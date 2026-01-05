package io.professionalhub.scraper.utils;

import io.professionalhub.scraper.config.ConfigManager;
import com.microsoft.playwright.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

/**
 * Utility class for browser-related operations
 */
public class BrowserUtils {
    private static final ConfigManager config = ConfigManager.getInstance();

    /**
     * Initialize Playwright browser and context with configuration settings
     */
    public static BrowserContext initializeBrowser(Playwright playwright) {
        System.out.println("=== Initializing Browser ===");

        try {
            BrowserContext context;

            if (config.useRunningBrowser()) {
                context = connectToRunningBrowser(playwright);
            } else if (config.useExistingChromeProfile()) {
                context = launchWithExistingProfile(playwright);
            } else {
                context = launchNewBrowser(playwright);
            }

            System.out.println("✓ Browser context created");
            System.out.println("  - Viewport: " + config.getViewportWidth() + "x" + config.getViewportHeight());

            return context;

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize browser", e);
        }
    }

    /**
     * Connect to already running Chrome browser via CDP
     */
    private static BrowserContext connectToRunningBrowser(Playwright playwright) {
        System.out.println("Connecting to running Chrome browser...");

        String cdpEndpoint = config.getCdpEndpoint();

        try {
            // Test if the CDP endpoint is available
            testCdpConnection(cdpEndpoint);

            // Connect to the running browser
            Browser browser = playwright.chromium().connectOverCDP(cdpEndpoint);

            System.out.println("✓ Successfully connected to running Chrome browser");
            System.out.println("  - CDP Endpoint: " + cdpEndpoint);

            // Get existing contexts or create a new one
            BrowserContext context;
            var existingContexts = browser.contexts();

            if (!existingContexts.isEmpty()) {
                // Use the first existing context (your current browser session)
                context = existingContexts.get(0);
                System.out.println("✓ Using existing browser context with " + context.pages().size() + " pages");

                // Optionally set viewport on existing pages
                for (Page page : context.pages()) {
                    try {
                        page.setViewportSize(config.getViewportWidth(), config.getViewportHeight());
                    } catch (Exception e) {
                        if (config.isDebugMode()) {
                            System.out.println("Debug: Could not set viewport on existing page: " + e.getMessage());
                        }
                    }
                }

            } else {
                // Create new context if none exist (unlikely with running browser)
                context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(config.getViewportWidth(), config.getViewportHeight()));
                System.out.println("✓ Created new context in running browser");
            }

            if (config.isDebugMode()) {
                System.out.println("Debug: Connected browser info:");
                System.out.println("  - Version: " + browser.version());
                System.out.println("  - Contexts: " + browser.contexts().size());
                System.out.println("  - Total pages: " + context.pages().size());

                // List current pages
                var pages = context.pages();
                for (int i = 0; i < pages.size(); i++) {
                    System.out.println("  - Page " + (i+1) + ": " + pages.get(i).url());
                }
            }

            return context;

        } catch (Exception e) {
            System.err.println("❌ Failed to connect to running Chrome browser");
            System.err.println("Make sure Chrome is running with: chrome --remote-debugging-port=" + config.getCdpPort());
            throw new RuntimeException("Failed to connect to running browser at " + cdpEndpoint, e);
        }
    }

    /**
     * Test if CDP endpoint is available
     */
    private static void testCdpConnection(String cdpEndpoint) {
        try {
            // Simple HTTP request to test if CDP is available
            java.net.URL url = new java.net.URL(cdpEndpoint + "/json/version");
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                System.out.println("✓ Chrome CDP endpoint is available");
            } else {
                throw new RuntimeException("CDP endpoint returned status: " + responseCode);
            }

        } catch (Exception e) {
            throw new RuntimeException("CDP endpoint not available. Make sure Chrome is running with --remote-debugging-port=" + config.getCdpPort(), e);
        }
    }
    private static BrowserContext launchWithExistingProfile(Playwright playwright) {
        System.out.println("Using existing Chrome profile...");

        String userDataDir = config.getChromeUserDataDir();
        String profileDir = config.getChromeProfileDir();

        // Validate user data directory
        if (userDataDir == null || userDataDir.trim().isEmpty()) {
            throw new RuntimeException("Chrome user data directory not configured. Please set 'browser.chrome.user.data.dir' in config.properties");
        }

        Path userDataPath = Paths.get(userDataDir);
        if (!userDataPath.toFile().exists()) {
            throw new RuntimeException("Chrome user data directory does not exist: " + userDataDir);
        }

        // Validate profile directory
        Path profilePath = userDataPath.resolve(profileDir);
        if (!profilePath.toFile().exists()) {
            System.out.println("⚠ Warning: Profile directory '" + profileDir + "' not found in user data directory.");
            System.out.println("Available profiles:");
            listAvailableProfiles(userDataPath);
            throw new RuntimeException("Chrome profile directory does not exist: " + profilePath);
        }

        System.out.println("✓ Using Chrome profile: " + profilePath);

        // Launch persistent context with existing profile
        BrowserContext context = playwright.chromium().launchPersistentContext(
            profilePath,
            new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(config.isBrowserHeadless())
                .setSlowMo(config.getBrowserSlowMotion())
                .setViewportSize(config.getViewportWidth(), config.getViewportHeight())
                .setArgs(Arrays.asList(
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-blink-features=AutomationControlled",
                    "--exclude-switches=enable-automation"
                ))
        );

        System.out.println("✓ Browser context launched with existing profile");
        System.out.println("  - Profile Path: " + profilePath);
        System.out.println("  - Headless mode: " + config.isBrowserHeadless());
        System.out.println("  - Slow motion: " + config.getBrowserSlowMotion() + "ms");

        return context;
    }

    /**
     * Launch new browser context (original behavior)
     */
    private static BrowserContext launchNewBrowser(Playwright playwright) {
        System.out.println("Launching new browser instance...");

        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(config.isBrowserHeadless())
            .setSlowMo(config.getBrowserSlowMotion()));

        System.out.println("✓ New browser instance launched");
        System.out.println("  - Headless mode: " + config.isBrowserHeadless());
        System.out.println("  - Slow motion: " + config.getBrowserSlowMotion() + "ms");

        // Create browser context with configured viewport
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
            .setViewportSize(config.getViewportWidth(), config.getViewportHeight()));

        return context;
    }

    /**
     * List available Chrome profiles in the user data directory
     */
    private static void listAvailableProfiles(Path userDataDir) {
        try {
            File[] profiles = userDataDir.toFile().listFiles((dir, name) ->
                name.equals("Default") || name.startsWith("Profile "));

            if (profiles != null && profiles.length > 0) {
                for (File profile : profiles) {
                    System.out.println("  - " + profile.getName());
                }
            } else {
                System.out.println("  - No Chrome profiles found");
            }
        } catch (Exception e) {
            System.out.println("  - Could not list profiles: " + e.getMessage());
        }
    }

    /**
     * Validate browser configuration
     */
    public static boolean validateBrowserConfig() {
        if (config.useRunningBrowser()) {
            return validateRunningBrowserConfig();
        } else if (config.useExistingChromeProfile()) {
            return validateChromeProfileConfig();
        }
        return true; // No validation needed for new browser
    }

    /**
     * Validate running browser CDP configuration
     */
    public static boolean validateRunningBrowserConfig() {
        String cdpEndpoint = config.getCdpEndpoint();

        try {
            testCdpConnection(cdpEndpoint);
            System.out.println("✓ Running browser configuration validated");
            System.out.println("  - CDP Endpoint: " + cdpEndpoint);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Running browser validation failed: " + e.getMessage());
            System.err.println("💡 To fix this:");
            System.err.println("   1. Close your current Chrome browser");
            System.err.println("   2. Start Chrome with: chrome --remote-debugging-port=" + config.getCdpPort());
            System.err.println("   3. Navigate to your desired pages (like LinkedIn)");
            System.err.println("   4. Run this application");
            return false;
        }
    }
    public static boolean validateChromeProfileConfig() {
        if (!config.useExistingChromeProfile()) {
            return true; // No validation needed for new browser
        }

        String userDataDir = config.getChromeUserDataDir();
        String profileDir = config.getChromeProfileDir();

        if (userDataDir == null || userDataDir.trim().isEmpty()) {
            System.err.println("❌ Chrome user data directory not configured");
            return false;
        }

        Path userDataPath = Paths.get(userDataDir);
        if (!userDataPath.toFile().exists()) {
            System.err.println("❌ Chrome user data directory does not exist: " + userDataDir);
            return false;
        }

        Path profilePath = userDataPath.resolve(profileDir);
        if (!profilePath.toFile().exists()) {
            System.err.println("❌ Chrome profile directory does not exist: " + profilePath);
            System.err.println("Available profiles:");
            listAvailableProfiles(userDataPath);
            return false;
        }

        System.out.println("✓ Chrome profile configuration validated");
        System.out.println("  - User Data Dir: " + userDataDir);
        System.out.println("  - Profile: " + profileDir);

        return true;
    }

    /**
     * Wait for user input before closing the browser
     */
    public static void waitForUserInput() {
        System.out.println("=== INSPECTION PHASE ===");
        System.out.println("✓ All automation tasks completed successfully!");
        System.out.println("The browser will remain open for your inspection.");
        System.out.println("Press Enter to close the browser and exit...");

        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        } catch (Exception e) {
            System.out.println("Error reading user input, closing browser...");
        }
    }
}