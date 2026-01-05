package io.professionalhub.scraper.service;

import io.professionalhub.scraper.config.ConfigManager;
import io.professionalhub.scraper.repository.CompanyUrlRepository;
import io.professionalhub.scraper.repository.ProfileUrlRepository;
import io.professionalhub.scraper.service.methods.ProfileExtractionService;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Service class responsible for handling LinkedIn Recruiter operations
 * This includes navigation to Recruiter home page, project navigation, and
 * validation
 */
public class RecruiterService {
  private static final ConfigManager config = ConfigManager.getInstance();
  private final ProfileExtractionService profileExtractionService;

  public RecruiterService(ProfileUrlRepository profileUrlRepository, CompanyUrlRepository companyUrlRepository) {
    this.profileExtractionService = new ProfileExtractionService(profileUrlRepository, companyUrlRepository);
  }

  /**
   * Execute the complete Recruiter navigation workflow
   * 1. Navigate to LinkedIn Recruiter URL
   * 2. Validate Recruiter home page
   * 3. Navigate to specific project
   * 4. Validate project page
   *
   * @param context The browser context to use
   */
  public void executeRecruiterNavigation(BrowserContext context) {
    System.out.println("=== RecruiterService: Starting Recruiter navigation ===");

    Page page = context.newPage();

    // Step 1: Navigate to LinkedIn Recruiter
    page.navigate(config.getLinkedinRecruiterUrl());

    // Step 2: Validate Recruiter Home Page
    validateRecruiterHomePage(page);

    // Extract profiles from existing URL list (no project navigation required)
    profileExtractionService.executeListProfileExtractionOperations(page);
  }

  /**
   * Validate that the LinkedIn Recruiter Home Page is properly loaded
   * Checks for key elements that should be present on the home page
   *
   * @param page The page to validate
   */
  private void validateRecruiterHomePage(Page page) {
    System.out.println("Validating Recruiter Home Page...");

    // 1.1 - Recruiter lite logo should be at the page
    page.waitForSelector("a.product-name__product-bug", new Page.WaitForSelectorOptions()
        .setState(WaitForSelectorState.VISIBLE)
        .setTimeout(config.getElementWaitTimeout()));
    System.out.println("  - Recruiter Lite logo found.");

    // 1.2 - Vertical Lateral Menu with the Option Home should be present
    page.waitForSelector("a[data-live-test-home-link='recent-projects']", new Page.WaitForSelectorOptions()
        .setState(WaitForSelectorState.VISIBLE)
        .setTimeout(config.getElementWaitTimeout()));
    System.out.println("  - Vertical Lateral Menu with Home option found.");

    // 1.3 - Recent Work title should be present in the main content area.
    page.waitForSelector("//h1[text()='Recent work']", new Page.WaitForSelectorOptions()
        .setState(WaitForSelectorState.VISIBLE)
        .setTimeout(config.getElementWaitTimeout()));
    System.out.println("  - 'Recent work' title found.");

    System.out.println("✓ Recruiter Home Page validated successfully.");
  }

  /**
   * Navigate to the specific project configured in settings
   * Uses multiple approaches to find and click the project link
   *
   * @param page        The page to navigate from
   * @param projectName The name of the project to navigate to
   */
  private void navigateToProject(Page page, String projectName) {
    openProjectsPage(page);

    String sanitizedProjectName = projectName.replace("\"", "");
    System.out.println("Navigating to project: " + sanitizedProjectName);

    ensureProjectsListOnFirstPage(page);

    int maxPaginationPages = Math.max(1, config.getInt("project.list.max.pagination.pages", 25));
    long paginationWaitMs = Math.max(0L, config.getLong("project.list.pagination.wait.ms", 1000));

    for (int pageIndex = 0; pageIndex < maxPaginationPages; pageIndex++) {
      if (tryNavigateToProjectOnCurrentPage(page, sanitizedProjectName)) {
        return;
      }

      if (!goToNextProjectsPage(page, pageIndex + 1, maxPaginationPages, paginationWaitMs)) {
        break;
      }
    }

    throw new RuntimeException("Project not found after checking all available pages: " + sanitizedProjectName);
  }

  /**
   * Navigate from the Recruiter home page to the Projects list page via the top
   * navigation
   */
  private void openProjectsPage(Page page) {
    if (page.url().contains("/talent/projects")) {
      waitForProjectsListToLoad(page);
      return;
    }

    System.out.println("Opening Projects page from navigation bar...");

    String projectsNavSelector = "nav[data-live-test-nav-items] a[href='/talent/projects']";

    try {
      page.waitForSelector(projectsNavSelector, new Page.WaitForSelectorOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(config.getElementWaitTimeout()));

      Locator projectsNavLink = page.locator(projectsNavSelector).first();
      projectsNavLink.click();

      page.waitForLoadState();
      waitForProjectsListToLoad(page);
      System.out.println("✓ Projects page loaded successfully.");

    } catch (Exception e) {
      throw new RuntimeException("Failed to open Projects page", e);
    }
  }

  private boolean tryNavigateToProjectOnCurrentPage(Page page, String projectName) {
    System.out.println("  - Searching current projects page for: " + projectName);

    try {
      if (clickProjectCardWithScrolling(page, projectName)) {
        System.out.println("✓ Successfully navigated to project: " + projectName);
        return true;
      }
    } catch (Exception e) {
      System.err.println("  - Error while attempting scroll-based search: " + e.getMessage());
    }

    if (clickProjectUsingXPath(page, projectName)) {
      System.out.println("✓ Successfully navigated to project using XPath: " + projectName);
      return true;
    }

    if (clickProjectViaManualSearch(page, projectName)) {
      System.out.println("✓ Successfully navigated to project using manual search: " + projectName);
      return true;
    }

    System.out.println("  - Project not present on this page.");
    return false;
  }

  private boolean clickProjectUsingXPath(Page page, String projectName) {
    System.out.println("  - Trying XPath approach as fallback...");

    try {
      String escapedProjectName = projectName.replace("'", "\\'");
      String selector = String.format(
          "//a[contains(@class, \"project-list-item__project-card-link\") and .//div[@class=\"project-lockup-title__text\" and text()=\"%s\"]]",
          escapedProjectName);

      page.click(selector);
      return true;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.err.println("  - XPath approach failed: " + e.getMessage());
      }
      return false;
    }
  }

  private void ensureProjectsListOnFirstPage(Page page) {
    Locator previousButton = page.locator("a[data-test-pagination-previous]");
    int maxAttempts = Math.max(1, config.getInt("project.list.max.pagination.pages", 25));

    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      if (previousButton.count() == 0) {
        if (attempt > 0) {
          waitForProjectsListToLoad(page);
        }
        System.out.println("  - Confirmed project list is on the first page.");
        return;
      }

      System.out.println(
          "  - Moving back to previous page to reach the start of the project list (step " + (attempt + 1) + ")");

      previousButton.first().click();
      waitForProjectsPageChange(page);
    }

    throw new RuntimeException(
        "Unable to reach the first page of the projects list after " + maxAttempts + " attempts.");
  }

  private boolean goToNextProjectsPage(Page page, int nextPageIndex, int maxPaginationPages, long paginationWaitMs) {
    Locator nextButton = page.locator("a[data-test-pagination-next]");

    if (nextButton.count() == 0) {
      System.out.println("  - No next page available; reached end of project list.");
      return false;
    }

    if (nextPageIndex >= maxPaginationPages) {
      System.out.println("  - Reached configured pagination limit of " + maxPaginationPages + " pages.");
      return false;
    }

    System.out
        .println("  - Project not found yet. Navigating to next projects page (page " + (nextPageIndex + 1) + ")");

    nextButton.first().click();
    waitForProjectsPageChange(page);

    if (paginationWaitMs > 0) {
      try {
        page.waitForTimeout(paginationWaitMs);
      } catch (Exception e) {
        System.out.println("⚠ Warning: Error while waiting after pagination: " + e.getMessage());
      }
    }

    return true;
  }

  private void waitForProjectsPageChange(Page page) {
    page.waitForLoadState();
    waitForProjectsListToLoad(page);
  }

  private void waitForProjectsListToLoad(Page page) {
    page.waitForSelector("a.project-list-item__project-card-link", new Page.WaitForSelectorOptions()
        .setTimeout(config.getElementWaitTimeout()));
  }

  /**
   * Manual search approach as final fallback
   *
   * @param page        The page to navigate from
   * @param projectName The name of the project to navigate to
   */
  private boolean clickProjectViaManualSearch(Page page, String projectName) {
    System.out.println("  - Trying manual search approach...");

    try {
      ensureProjectListExpanded(page);

      // Get all project cards
      Locator projectCards = page.locator("a.project-list-item__project-card-link");
      int count = projectCards.count();

      System.out.println("  - Manual search inspecting " + count + " project cards.");

      for (int i = 0; i < count; i++) {
        Locator card = projectCards.nth(i);
        Locator titleElement = card.locator(".project-lockup-title__text");

        if (titleElement.count() > 0) {
          String cardTitle = titleElement.textContent().trim();
          if (projectName.equals(cardTitle)) {
            card.click();
            return true;
          }
        }
      }

      System.out.println("  - Manual search did not find the project on this page.");

    } catch (Exception e) {
      System.err.println("  - Manual search encountered an error: " + e.getMessage());
    }

    return false;
  }

  /**
   * Attempt to click a project card, scrolling the project list between attempts
   */
  private boolean clickProjectCardWithScrolling(Page page, String projectName) {
    Locator projectLinks = page.locator("a.project-list-item__project-card-link");
    int maxScrollAttempts = Math.max(0, config.getInt("project.list.max.scroll.attempts", 10));
    long scrollWaitMs = Math.max(0L, config.getLong("project.list.scroll.wait.ms", 1500));

    for (int attempt = 0; attempt <= maxScrollAttempts; attempt++) {
      Locator targetProject = projectLinks.filter(new Locator.FilterOptions().setHasText(projectName));
      int candidateCount = targetProject.count();

      if (candidateCount > 0) {
        for (int index = 0; index < candidateCount; index++) {
          Locator candidate = targetProject.nth(index);
          Locator titleElement = candidate.locator(".project-lockup-title__text");

          if (titleElement.count() == 0) {
            continue;
          }

          String titleText = titleElement.textContent().trim();
          if (!projectName.equals(titleText)) {
            continue;
          }

          try {
            candidate.scrollIntoViewIfNeeded();
          } catch (Exception e) {
            System.out.println("  - Warning: Unable to scroll project into view: " + e.getMessage());
          }

          candidate.click();
          return true;
        }
      }

      if (attempt < maxScrollAttempts) {
        performProjectListScroll(page, attempt + 1, maxScrollAttempts);

        if (scrollWaitMs > 0) {
          try {
            page.waitForTimeout(scrollWaitMs);
          } catch (Exception e) {
            System.out.println("⚠ Warning: Error while waiting after project list scroll: " + e.getMessage());
          }
        }
      }
    }

    return false;
  }

  /**
   * Ensure the project list is expanded by scrolling to load additional cards
   */
  private void ensureProjectListExpanded(Page page) {
    Locator projectCards = page.locator("a.project-list-item__project-card-link");
    int maxScrollAttempts = Math.max(0, config.getInt("project.list.max.scroll.attempts", 10));
    long scrollWaitMs = Math.max(0L, config.getLong("project.list.scroll.wait.ms", 1500));
    int previousCount = projectCards.count();

    for (int attempt = 1; attempt <= maxScrollAttempts; attempt++) {
      performProjectListScroll(page, attempt, maxScrollAttempts);

      if (scrollWaitMs > 0) {
        try {
          page.waitForTimeout(scrollWaitMs);
        } catch (Exception e) {
          System.out.println("⚠ Warning: Error while waiting after project list scroll: " + e.getMessage());
        }
      }

      int currentCount = projectCards.count();

      if (currentCount <= previousCount) {
        if (attempt > 1) {
          System.out.println("  - Project list size stabilized at " + currentCount + " cards.");
          break;
        }
      } else {
        System.out.println("  - Project list size after scroll attempt " + attempt + ": " + currentCount);
        previousCount = currentCount;
      }
    }
  }

  /**
   * Perform a scroll gesture in the project list to trigger lazy loading
   */
  private void performProjectListScroll(Page page, int attempt, int maxAttempts) {
    try {
      System.out.println("  - Project list scroll attempt " + attempt + " of " + maxAttempts);

      if (attempt % 3 == 1) {
        page.evaluate("window.scrollBy(0, window.innerHeight)");
      } else if (attempt % 3 == 2) {
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
      } else {
        page.evaluate("window.scrollTo(0, document.body.scrollHeight / 2)");
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
      }

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error while scrolling project list: " + e.getMessage());
    }
  }

  /**
   * Validate that the project page loaded correctly
   * Checks for key elements that should be present on a project page
   *
   * @param page The page to validate
   */
  private void validateProjectPage(Page page) {
    System.out.println("Validating project page...");

    // Check 1: Project title is loaded
    page.waitForSelector(".project-lockup-title__text", new Page.WaitForSelectorOptions()
        .setState(WaitForSelectorState.VISIBLE)
        .setTimeout(config.getProfileLoadTimeout()));
    System.out.println("  - Project title found.");

    // Check 2: Vertical lateral menu with "Talent pool" option
    page.waitForSelector("button[data-live-test-collapsible-menu-link-group='discover']",
        new Page.WaitForSelectorOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(config.getElementWaitTimeout()));

    // Verify the Talent pool text is present
    Locator talentPoolButton = page.locator("button[data-live-test-collapsible-menu-link-group='discover']");
    if (talentPoolButton.locator("text=Talent pool").count() == 0) {
      throw new RuntimeException("Talent pool option not found in lateral menu");
    }

    System.out.println("  - Vertical lateral menu with 'Talent pool' option found.");
    System.out.println("✓ Project page validated successfully.");
  }

  /**
   * Handle case when project file doesn't exist
   * Click on Talent Pool in the vertical lateral menu
   */
  private void handleMissingProjectFile(Page page) {
    System.out.println("Project file not found, navigating to Talent Pool...");

    try {
      // Wait for the Talent Pool button to be visible
      String talentPoolSelector = "button[data-live-test-collapsible-menu-link-group='discover']";

      page.waitForSelector(talentPoolSelector, new Page.WaitForSelectorOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(config.getElementWaitTimeout()));

      // Verify the button contains "Talent pool" text
      Locator talentPoolButton = page.locator(talentPoolSelector);

      if (talentPoolButton.locator("text=Talent pool").count() == 0) {
        throw new RuntimeException("Talent pool button found but doesn't contain expected text");
      }

      System.out.println("Clicking on Talent Pool...");
      talentPoolButton.click();

      // Wait for navigation or content change
      try {
        // Wait for URL change or new content to load
        page.waitForTimeout(config.getDynamicContentWaitTime());
        System.out.println("✓ Successfully clicked Talent Pool");

        if (config.isDebugMode()) {
          System.out.println("Debug: Current URL after Talent Pool click: " + page.url());
        }

      } catch (Exception e) {
        System.out.println("⚠ Warning: Could not detect page change after Talent Pool click");
        if (config.isDebugMode()) {
          System.out.println("Debug: " + e.getMessage());
        }
      }

    } catch (Exception e) {
      System.err.println("❌ Error clicking Talent Pool: " + e.getMessage());
      throw new RuntimeException("Failed to navigate to Talent Pool", e);
    }
  }

}
