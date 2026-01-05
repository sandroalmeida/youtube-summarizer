package io.professionalhub.scraper.service.methods;

import io.professionalhub.scraper.config.ConfigManager;
import io.professionalhub.scraper.model.ProfileUrl;
import io.professionalhub.scraper.repository.CompanyUrlRepository;
import io.professionalhub.scraper.repository.ProfileUrlRepository;
import io.professionalhub.scraper.service.navigation.ProfileExpansionService;
import io.professionalhub.scraper.utils.EnhancedRetryManager;
import io.professionalhub.scraper.utils.HumanLikeInteractionUtils;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;

/**
 * Service class responsible for extracting profile data from the URLs
 * This service reads URLs from the database (profile_url table)
 * and extracts each profile using the existing extraction services
 * After successful extraction, URLs status are updated in the database
 */
public class ProfileExtractionService {
  private static final ConfigManager config = ConfigManager.getInstance();

  private final ProfileUrlRepository profileUrlRepository;
  private final CompanyUrlRepository companyUrlRepository;
  private final ProfileExpansionService profileExpansionService;
  private final io.professionalhub.scraper.service.profile.ProfileExtractionService profileExtractionService;
  private final EnhancedRetryManager retryManager;
  private final BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

  /**
   * Constructor that initializes all service dependencies
   */
  public ProfileExtractionService(ProfileUrlRepository profileUrlRepository,
      CompanyUrlRepository companyUrlRepository) {
    this.profileUrlRepository = profileUrlRepository;
    this.companyUrlRepository = companyUrlRepository;
    this.profileExpansionService = new ProfileExpansionService();
    this.profileExtractionService = new io.professionalhub.scraper.service.profile.ProfileExtractionService(
        companyUrlRepository);
    this.retryManager = new EnhancedRetryManager(config);
  }

  private void handleUnexpectedLinkedInMemberProfile(String originalProfileUrl, String currentPageUrl) {
    System.out.println("⚠ Unexpected 'LinkedIn Member' profile encountered during method 3 extraction.");
    System.out.println("Profiles in the list should already exclude this type.");

    if (originalProfileUrl != null && !originalProfileUrl.trim().isEmpty()) {
      System.out.println("  - Original list URL: " + originalProfileUrl);
    }

    if (currentPageUrl != null && !currentPageUrl.trim().isEmpty()) {
      System.out.println("  - Currently loaded URL: " + currentPageUrl);
    }

    boolean shouldContinue = confirmContinueAfterUnexpectedLinkedInMember();
    if (!shouldContinue) {
      System.out.println("Stopping extraction as requested after detecting a 'LinkedIn Member' profile.");
      System.exit(0);
    }

    System.out.println("Continuing extraction after user confirmation.");
  }

  private boolean confirmContinueAfterUnexpectedLinkedInMember() {
    System.out.println("Do you want to continue with the remaining profiles? (Y/N)");

    while (true) {
      System.out.print("> ");
      String input;
      try {
        input = consoleReader.readLine();
      } catch (Exception e) {
        System.err.println("Error reading response. Stopping extraction for safety.");
        return false;
      }

      if (input == null) {
        System.err.println("No input received. Stopping extraction for safety.");
        return false;
      }

      String normalized = input.trim().toLowerCase();
      if (normalized.equals("y") || normalized.equals("yes")) {
        return true;
      }
      if (normalized.equals("n") || normalized.equals("no")) {
        return false;
      }

      System.out.println("Please answer with 'Y' or 'N'.");
    }
  }

  /**
   * Wait for specified minutes with a countdown display
   * Updates the console every 30 seconds to show remaining time
   *
   * @param minutes Number of minutes to wait
   */
  private void waitWithCountdown(int minutes) {
    long totalMillis = minutes * 60L * 1000L;
    long endTime = System.currentTimeMillis() + totalMillis;

    // Update every 30 seconds
    long updateIntervalMillis = 30000L;

    while (System.currentTimeMillis() < endTime) {
      long remainingMillis = endTime - System.currentTimeMillis();

      if (remainingMillis <= 0) {
        break;
      }

      // Calculate remaining time
      long remainingMinutes = remainingMillis / 60000L;
      long remainingSeconds = (remainingMillis % 60000L) / 1000L;

      // Display countdown
      System.out.println(String.format("⏱ Waiting... Time remaining: %d minute(s) %d second(s)",
          remainingMinutes, remainingSeconds));

      // Wait for next update interval or until end time
      long waitTime = Math.min(updateIntervalMillis, remainingMillis);
      try {
        Thread.sleep(waitTime);
      } catch (InterruptedException e) {
        System.out.println("⚠ Wait interrupted: " + e.getMessage());
        Thread.currentThread().interrupt();
        break;
      }
    }

    System.out.println("✓ Pause completed");
  }

  /**
   * Execute the complete list profile extraction operations
   * Reads URLs from the database and extracts each profile
   * Updates status to 1 after successful extraction
   *
   * @param page The page to use for profile extraction
   */
  public void executeListProfileExtractionOperations(Page page) {
    System.out.println("=== ProfileExtractionService: Starting profile extraction from database ===");

    // Count pending profiles in database (status = false)
    long totalProfilesPending = profileUrlRepository.count();
    System.out.println("Total profiles in database: " + totalProfilesPending);

    processProfilesFromDatabase(page);
  }

  /**
   * Process profiles from the database
   * Fetches URLs with status = false, ordered by idProfileUrl ASC
   * Updates status to true after successful extraction
   * Continuously checks for new entries, pausing when queue is empty
   *
   * @param page The page to use for profile extraction
   */
  private void processProfilesFromDatabase(Page page) {
    System.out.println("\n" + "#".repeat(80));
    System.out.println("Starting extraction from database");
    System.out.println("System will continuously monitor for new entries");
    System.out.println("#".repeat(80));

    int successfulExtractions = 0;
    int failedExtractions = 0;
    int skippedExtractions = 0;
    int profileNumber = 1;

    // Process URLs continuously - system will pause when no entries are found
    while (true) {
      // Fetch the first URL with status = false, ordered by idProfileUrl ASC
      Optional<ProfileUrl> profileUrlOpt = profileUrlRepository.findFirstByStatusOrderByIdProfileUrlAsc(false);

      if (!profileUrlOpt.isPresent()) {
        System.out.println("\n" + "⏸".repeat(80));
        System.out.println("No URLs with status = 0 found in the database");
        System.out.println("Entering pause mode - will check again after the configured pause duration");
        System.out.println("⏸".repeat(80));

        // Display current statistics
        int totalProcessed = successfulExtractions + failedExtractions + skippedExtractions;
        System.out.println("\n📊 Current session statistics:");
        System.out.println("  Total URLs processed: " + totalProcessed);
        System.out.println("  Successful extractions: " + successfulExtractions);
        System.out.println("  Failed extractions: " + failedExtractions);
        System.out.println("  Skipped extractions (out of network): " + skippedExtractions);
        if (totalProcessed > 0) {
          System.out.println("  Success rate: " +
              String.format("%.1f%%", (double) successfulExtractions / totalProcessed * 100));
        }
        System.out.println();

        // Get pause duration from configuration (in minutes)
        int pauseMinutes = config.getProfileQueueEmptyPauseMinutes();
        System.out.println("Pause duration: " + pauseMinutes + " minute(s)");

        // Display countdown
        waitWithCountdown(pauseMinutes);

        // Continue to check for new entries
        System.out.println("\n" + "🔄".repeat(80));
        System.out.println("Resuming check for new entries...");
        System.out.println("🔄".repeat(80) + "\n");
        continue;
      }

      ProfileUrl profileUrlEntity = profileUrlOpt.get();
      String profileUrl = profileUrlEntity.getUrl();
      Long profileUrlId = profileUrlEntity.getIdProfileUrl();

      try {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Processing Profile " + profileNumber);
        System.out.println("ID: " + profileUrlId);
        System.out.println("URL: " + profileUrl);
        System.out.println("=".repeat(70));

        // Use enhanced retry mechanism for navigation
        boolean navigationSuccessful = retryManager.performNavigationWithRetry(page, profileUrl,
            this::navigateToProfile);
        if (!navigationSuccessful) {
          // Check if this should cause termination (threshold reached) or just continue
          if (retryManager.shouldTriggerWaitingDelay()) {
            System.out.println(
                "❌ Navigation failed after enhanced retry mechanism with waiting delay. Terminating execution.");
            // Finish execution as per requirement
            System.exit(1);
            return; // Safety return (unreachable after System.exit)
          } else {
            // Threshold not reached - mark as failed and continue with next profile
            System.out.println("❌ Navigation failed but threshold not reached. Marking as failed and continuing...");
            // Don't update status - leave it as 0 so it can be retried later
            failedExtractions++;
            profileNumber++;
            continue; // Continue with next profile
          }
        }

        if (isDailyProfileViewLimitReached(page)) {
          System.out.println(
              "❌ Daily profile view limit banner detected. Logging and terminating execution to avoid further requests.");
          System.exit(0);
          return; // Safety return after System.exit
        }

        boolean isAccessible = isProfileAccessible(page, profileUrl);
        if (!isAccessible) {
          System.out
              .println("⚠ Profile " + profileNumber + " is out of network. Marking as processed and continuing...");
          // Mark as processed (status = true) even though we skipped it
          profileUrlEntity.setStatus(true);
          profileUrlRepository.save(profileUrlEntity);
          skippedExtractions++;
          profileNumber++;
          continue;
        }

        // Use enhanced retry mechanism for extraction
        final int currentProfileNumber = profileNumber;
        boolean extractionSuccessful = retryManager.performExtractionWithRetry(
            page, profileUrl,
            (p) -> extractCurrentProfile(p, currentProfileNumber),
            this::navigateToProfile);

        if (extractionSuccessful) {
          System.out.println("✓ Profile " + profileNumber + " extracted successfully");
          // Update status to 1 (true) after successful extraction
          profileUrlEntity.setStatus(true);
          profileUrlRepository.save(profileUrlEntity);
          successfulExtractions++;
        } else {
          // Check if this should cause termination (threshold reached) or just continue
          if (retryManager.shouldTriggerWaitingDelay()) {
            System.out.println("❌ Profile " + profileNumber
                + " extraction failed after enhanced retry mechanism with waiting delay. Terminating execution.");
            // Finish execution as per requirement
            System.exit(1);
            return; // Safety return (unreachable after System.exit)
          } else {
            // Threshold not reached - mark as failed and continue with next profile
            System.out.println("❌ Profile " + profileNumber
                + " extraction failed but threshold not reached. Marking as failed and continuing...");
            // Don't update status - leave it as 0 so it can be retried later
            failedExtractions++;
            profileNumber++;
            continue; // Continue with next profile
          }
        }

        profileNumber++;
        long processingDelay = config.getLong("profile.processing.delay.ms", 2000);
        page.waitForTimeout(processingDelay);

      } catch (Exception e) {
        System.err.println("❌ Unexpected error processing profile " + profileNumber + ": " + e.getMessage());
        if (config.isDebugMode()) {
          e.printStackTrace();
        }

        // Record this as a failure for the enhanced retry mechanism
        retryManager.recordFailure();

        // Use enhanced retry mechanism for unexpected errors
        System.out.println("⏱ Applying enhanced retry mechanism due to unexpected error...");
        final int currentProfileNumber = profileNumber;

        boolean recovered = retryManager.performExtractionWithRetry(
            page, profileUrl,
            (p) -> extractCurrentProfile(p, currentProfileNumber),
            this::navigateToProfile);

        if (!recovered) {
          // Check if this should cause termination (threshold reached) or just continue
          if (retryManager.shouldTriggerWaitingDelay()) {
            System.out.println(
                "❌ Unexpected error persisted after enhanced retry mechanism with waiting delay. Terminating execution.");
            System.exit(1);
            return; // Safety return
          } else {
            // Threshold not reached - mark as failed and continue with next profile
            System.out
                .println("❌ Unexpected error persisted but threshold not reached. Marking as failed and continuing...");
            // Don't update status - leave it as 0 so it can be retried later
            failedExtractions++;
            profileNumber++;
            // Continue with next profile (don't return)
          }
        } else {
          System.out.println("✓ Profile " + profileNumber + " recovered successfully after unexpected error");
          // Update status to 1 (true) after successful recovery
          profileUrlEntity.setStatus(true);
          profileUrlRepository.save(profileUrlEntity);
          successfulExtractions++;
        }

        profileNumber++;
      }
    }

    // Note: This method runs continuously and will not exit normally.
    // The loop will keep checking for new entries and pause when queue is empty.
  }

  private boolean isDailyProfileViewLimitReached(Page page) {
    try {
      Locator bannerBody = page.locator("div.profile-limit-block-banner__body");
      if (bannerBody.count() == 0) {
        return false;
      }

      Locator firstBanner = bannerBody.first();
      boolean bannerVisible = false;
      try {
        bannerVisible = firstBanner.isVisible();
      } catch (Exception visibilityError) {
        if (config.isDebugMode()) {
          System.out.println("Debug: Error checking banner visibility: " + visibilityError.getMessage());
        }
      }

      String bannerText = "";
      try {
        bannerText = firstBanner.innerText();
      } catch (Exception innerTextError) {
        if (config.isDebugMode()) {
          System.out.println("Debug: Error reading banner inner text: " + innerTextError.getMessage());
        }
        try {
          bannerText = firstBanner.textContent();
        } catch (Exception textContentError) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Error reading banner text content: " + textContentError.getMessage());
          }
        }
      }

      String normalizedText = bannerText == null ? "" : bannerText.toLowerCase();

      Locator headline = firstBanner.locator("h1.profile-limit-block-banner__headline");
      String headlineText = "";
      if (headline.count() > 0) {
        try {
          headlineText = headline.first().innerText();
        } catch (Exception headlineError) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Error reading banner headline: " + headlineError.getMessage());
          }
        }
      }

      String normalizedHeadline = headlineText == null ? "" : headlineText.toLowerCase();

      boolean headlineIndicatesLimit = normalizedHeadline.contains("profile view limit");
      boolean bodyIndicatesLimit = normalizedText.contains("profile view limit");

      if (bannerVisible || headlineIndicatesLimit || bodyIndicatesLimit) {
        if (config.isDebugMode()) {
          System.out.println("Debug: Daily profile limit banner detected. Visible=" + bannerVisible +
              ", headline='" + headlineText + "', content='" + bannerText + "'");
        }
        return true;
      }

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error while detecting daily profile limit banner: " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
    }

    return false;
  }

  /**
   * Navigate to a specific profile URL
   *
   * @param page       The page to navigate
   * @param profileUrl The profile URL to navigate to
   * @return true if navigation was successful, false otherwise
   */
  private boolean navigateToProfile(Page page, String profileUrl) {
    System.out.println("Navigating to profile...");

    try {
      // Navigate to the profile URL
      page.navigate(profileUrl);
      HumanLikeInteractionUtils.shortPause(page);

      // Wait for page to load
      page.waitForLoadState();
      HumanLikeInteractionUtils.performMicroInteraction(page);

      // Wait for profile to fully load using enhanced validation
      if (!retryManager.isProfileLoadedRobustly(page)) {
        throw new RuntimeException("Profile failed to load properly");
      }

      HumanLikeInteractionUtils.simulateContentEngagement(page);

      System.out.println("✓ Successfully navigated to profile");

      if (config.isDebugMode()) {
        System.out.println("Debug: Current URL after navigation: " + page.url());
      }

      return true;

    } catch (Exception e) {
      System.err.println("❌ Error navigating to profile: " + e.getMessage());

      if (config.isDebugMode()) {
        e.printStackTrace();
      }

      return false;
    }
  }

  /**
   * Validate if the current profile is accessible (not out of network)
   * Checks for "LinkedIn Member" name and "Out of network" indicators
   *
   * @param page               The current page
   * @param originalProfileUrl The URL read from the list file for context
   * @return true if profile is accessible, false if out of network
   */
  private boolean isProfileAccessible(Page page, String originalProfileUrl) {
    System.out.println("Validating profile accessibility...");

    try {
      // Check 1: Look for "LinkedIn Member" text in the name field
      String[] linkedinMemberSelectors = {
          "span[data-test-row-lockup-full-name] .artdeco-entity-lockup__title",
          "span[data-live-test-row-lockup-full-name] .artdeco-entity-lockup__title",
          ".artdeco-entity-lockup__title"
      };

      boolean hasLinkedInMemberText = false;
      for (String selector : linkedinMemberSelectors) {
        try {
          var nameElement = page.locator(selector);
          if (nameElement.count() > 0) {
            String nameText = nameElement.first().textContent();
            if (nameText != null && nameText.trim().equalsIgnoreCase("LinkedIn Member")) {
              hasLinkedInMemberText = true;
              System.out.println("  - Found 'LinkedIn Member' text in name field");
              handleUnexpectedLinkedInMemberProfile(originalProfileUrl, page.url());
              break;
            }
          }
        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Error checking selector '" + selector + "': " + e.getMessage());
          }
        }
      }

      // Check 2: Look for "Out of network" button/label
      String[] outOfNetworkSelectors = {
          "button[data-test-out-of-network-label]",
          "button[data-live-test-out-of-network-label]",
          ".locked-reason__label",
          "button:has-text('Out of network')"
      };

      boolean hasOutOfNetworkLabel = false;
      for (String selector : outOfNetworkSelectors) {
        try {
          var outOfNetworkElement = page.locator(selector);
          if (outOfNetworkElement.count() > 0) {
            String labelText = outOfNetworkElement.first().textContent();
            if (labelText != null && labelText.toLowerCase().contains("out of network")) {
              hasOutOfNetworkLabel = true;
              System.out.println("  - Found 'Out of network' label");
              break;
            }
          }
        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Error checking selector '" + selector + "': " + e.getMessage());
          }
        }
      }

      // Check 3: Look for locked reason wrapper (additional validation)
      boolean hasLockedReasonWrapper = false;
      try {
        var lockedReasonWrapper = page.locator(".lockup__lockedReason[data-test-locked-reason-wrapper]");
        if (lockedReasonWrapper.count() > 0) {
          hasLockedReasonWrapper = true;
          System.out.println("  - Found locked reason wrapper");
        }
      } catch (Exception e) {
        if (config.isDebugMode()) {
          System.out.println("Debug: Error checking locked reason wrapper: " + e.getMessage());
        }
      }

      // Profile is considered out of network if any of these conditions are met
      boolean isOutOfNetwork = hasLinkedInMemberText || hasOutOfNetworkLabel || hasLockedReasonWrapper;

      if (isOutOfNetwork) {
        System.out.println("❌ Profile is out of network - skipping extraction");
        System.out.println("  - LinkedIn Member text: " + hasLinkedInMemberText);
        System.out.println("  - Out of network label: " + hasOutOfNetworkLabel);
        System.out.println("  - Locked reason wrapper: " + hasLockedReasonWrapper);
        return false;
      } else {
        System.out.println("✓ Profile is accessible - proceeding with extraction");
        return true;
      }

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error validating profile accessibility: " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
      // In case of error, assume profile is accessible to avoid false negatives
      System.out.println("  - Assuming profile is accessible due to validation error");
      return true;
    }
  }

  /**
   * Extract the currently loaded profile
   * This method orchestrates the profile extraction workflow
   *
   * @param page          The current page
   * @param profileNumber The profile number for logging purposes
   * @return true if extraction was successful, false otherwise
   */
  private boolean extractCurrentProfile(Page page, int profileNumber) {
    System.out.println("=== Extracting current profile ===");

    try {
      // Step 1: Expose entire profile by clicking expand buttons
      profileExpansionService.exposeEntireProfile(page);

      // Step 2: Extract and save profile as JSON using ProfileExtractionService
      profileExtractionService.extractAndSaveProfile(page);

      System.out.println("✓ Profile " + profileNumber + " extraction completed successfully");
      return true;

    } catch (Exception e) {
      System.err.println("❌ Error extracting profile " + profileNumber + ": " + e.getMessage());

      if (config.isDebugMode()) {
        e.printStackTrace();
      }

      return false;
    }
  }
}
