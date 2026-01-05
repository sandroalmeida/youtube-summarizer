package io.professionalhub.scraper;

import io.professionalhub.scraper.config.ConfigManager;
import io.professionalhub.scraper.config.JpaConfig;
import io.professionalhub.scraper.repository.CompanyUrlRepository;
import io.professionalhub.scraper.repository.ProfileUrlRepository;
import io.professionalhub.scraper.service.LoginService;
import io.professionalhub.scraper.service.RecruiterService;
import io.professionalhub.scraper.utils.BrowserUtils;
import io.professionalhub.scraper.utils.TimestampedPrintStream;
import com.microsoft.playwright.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Main class for LinkedIn Profile Automation Tool
 * Orchestrates the automation workflow using service classes and utilities
 */
public class Main {
  static {
    System.setOut(new TimestampedPrintStream(System.out));
  }

  private static final ConfigManager config = ConfigManager.getInstance();

  public static void main(String[] args) {

    if (config.isDebugMode()) {
      config.printConfiguration();
    }

    // Initialize Spring ApplicationContext for database access
    AnnotationConfigApplicationContext applicationContext = null;
    try {
      applicationContext = new AnnotationConfigApplicationContext(JpaConfig.class);
      ProfileUrlRepository profileUrlRepository = applicationContext.getBean(ProfileUrlRepository.class);
      CompanyUrlRepository companyUrlRepository = applicationContext.getBean(CompanyUrlRepository.class);
      
      LoginService loginService = new LoginService();
      RecruiterService recruiterService = new RecruiterService(profileUrlRepository, companyUrlRepository);

      try (Playwright playwright = Playwright.create()) {

        // Initialize browser and context
        BrowserContext context = BrowserUtils.initializeBrowser(playwright);

        // Phase 1: Handle login process
        if (config.isSkipLogin()) {
          System.out.println("Skipping login process as per configuration.");
        } else {
          loginService.performLogin(context);
        }

        // Phase 2: Use RecruiterService to handle Recruiter navigation
        recruiterService.executeRecruiterNavigation(context);

        // Phase 4: Keep browser open for user inspection
        BrowserUtils.waitForUserInput();

        // Cleanup
        context.browser().close();
        System.out.println("=== Automation completed successfully! ===");

      } catch (Exception e) {
        System.err.println("=== AUTOMATION FAILED ===");
        System.err.println("Error: " + e.getMessage());
        if (config.isDebugMode()) {
          e.printStackTrace();
        }
        System.exit(1);
      }
    } finally {
      // Close Spring ApplicationContext
      if (applicationContext != null) {
        applicationContext.close();
      }
    }
  }
}
