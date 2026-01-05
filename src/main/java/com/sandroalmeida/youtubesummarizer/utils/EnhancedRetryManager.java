package com.sandroalmeida.youtubesummarizer.utils;

import com.sandroalmeida.youtubesummarizer.config.ConfigManager;
import com.microsoft.playwright.Page;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;

/**
 * Enhanced retry manager that provides intelligent failure detection,
 * immediate retries for temporary issues, and console input capability
 * during waiting periods.
 */
public class EnhancedRetryManager {
    private final ConfigManager config;
    private int consecutiveFailures = 0;

    public EnhancedRetryManager(ConfigManager config) {
        this.config = config;
    }

    /**
     * Reset the consecutive failures counter (call this after successful
     * operations)
     */
    public void resetFailureCount() {
        this.consecutiveFailures = 0;
    }

    /**
     * Increment consecutive failures counter
     */
    public void recordFailure() {
        this.consecutiveFailures++;
    }

    /**
     * Check if we should trigger the waiting delay based on failure patterns
     */
    public boolean shouldTriggerWaitingDelay() {
        return consecutiveFailures >= config.getLinkedinConsecutiveFailuresThreshold();
    }

    /**
     * Perform enhanced navigation retry with intelligent failure detection
     */
    public boolean performNavigationWithRetry(Page page, String profileUrl, NavigationFunction navigationFunction) {
        // First, try immediate retries for temporary issues
        for (int attempt = 1; attempt <= config.getLinkedinImmediateRetryCount(); attempt++) {
            System.out.println("Attempting navigation (immediate retry " + attempt + "/"
                    + config.getLinkedinImmediateRetryCount() + ")...");

            if (navigationFunction.navigate(page, profileUrl)) {
                resetFailureCount();
                return true;
            }

            // Short delay between immediate retries
            if (attempt < config.getLinkedinImmediateRetryCount()) {
                System.out.println("  - Short delay before next immediate retry...");
                page.waitForTimeout(2000);
            }
        }

        // Record this failure
        recordFailure();

        // Check if we should trigger waiting delay
        if (!shouldTriggerWaitingDelay()) {
            System.out
                    .println("❌ Navigation failed after immediate retries, but not enough consecutive failures yet (" +
                            consecutiveFailures + "/" + config.getLinkedinConsecutiveFailuresThreshold()
                            + ") to trigger waiting delay.");
            System.out.println("📝 Recording failure and continuing with next profile...");
            return false; // This failure should be handled by the caller, not cause termination
        }

        // Trigger waiting delay with console input capability
        System.out.println("❌ Navigation failed after immediate retries. Consecutive failures: " + consecutiveFailures +
                ". Triggering waiting delay mechanism...");

        return performWaitingDelayRetry(page, profileUrl, navigationFunction);
    }

    /**
     * Perform extraction retry with intelligent failure detection
     */
    public boolean performExtractionWithRetry(Page page, String profileUrl, ExtractionFunction extractionFunction,
            NavigationFunction navigationFunction) {
        // First, try immediate retries for temporary issues
        for (int attempt = 1; attempt <= config.getLinkedinImmediateRetryCount(); attempt++) {
            System.out.println("Attempting extraction (immediate retry " + attempt + "/"
                    + config.getLinkedinImmediateRetryCount() + ")...");

            if (extractionFunction.extract(page)) {
                resetFailureCount();
                return true;
            }

            // Short delay between immediate retries
            if (attempt < config.getLinkedinImmediateRetryCount()) {
                System.out.println("  - Short delay before next immediate retry...");
                page.waitForTimeout(2000);
            }
        }

        // Record this failure
        recordFailure();

        // Check if we should trigger waiting delay
        if (!shouldTriggerWaitingDelay()) {
            System.out
                    .println("❌ Extraction failed after immediate retries, but not enough consecutive failures yet (" +
                            consecutiveFailures + "/" + config.getLinkedinConsecutiveFailuresThreshold()
                            + ") to trigger waiting delay.");
            return false;
        }

        // Trigger waiting delay with console input capability
        System.out.println("❌ Extraction failed after immediate retries. Consecutive failures: " + consecutiveFailures +
                ". Triggering waiting delay mechanism...");

        return performWaitingDelayRetryWithExtraction(page, profileUrl, extractionFunction, navigationFunction);
    }

    /**
     * Perform waiting delay retry for navigation failures
     */
    private boolean performWaitingDelayRetry(Page page, String profileUrl, NavigationFunction navigationFunction) {
        int retryCount = config.getLinkedinRetryCount();
        int waitMinutes = config.getLinkedinWaitingDelayMinutes();

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            System.out.println(
                    "⏳ Initiating waiting period " + attempt + "/" + retryCount + " (" + waitMinutes + " minutes)...");
            System.out.println(
                    "💡 You can press ENTER at any time during the wait to skip and retry immediately if LinkedIn is working again.");

            boolean waitCompleted = performInterruptibleWait(waitMinutes);

            System.out.println("Retrying navigation (attempt " + attempt + ")...");

            if (navigationFunction.navigate(page, profileUrl)) {
                resetFailureCount();
                return true;
            }
        }

        return false;
    }

    /**
     * Perform waiting delay retry for extraction failures
     */
    private boolean performWaitingDelayRetryWithExtraction(Page page, String profileUrl,
            ExtractionFunction extractionFunction, NavigationFunction navigationFunction) {
        int retryCount = config.getLinkedinRetryCount();
        int waitMinutes = config.getLinkedinWaitingDelayMinutes();

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            System.out.println(
                    "⏳ Initiating waiting period " + attempt + "/" + retryCount + " (" + waitMinutes + " minutes)...");
            System.out.println(
                    "💡 You can press ENTER at any time during the wait to skip and retry immediately if LinkedIn is working again.");

            boolean waitCompleted = performInterruptibleWait(waitMinutes);

            // Re-navigate to ensure fresh page load, then retry extraction
            if (navigationFunction.navigate(page, profileUrl)) {
                if (extractionFunction.extract(page)) {
                    resetFailureCount();
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Perform interruptible wait that can be cancelled by user input
     * 
     * @param waitMinutes Number of minutes to wait
     * @return true if wait completed normally, false if interrupted by user
     */
    private boolean performInterruptibleWait(int waitMinutes) {
        long waitMillis = waitMinutes * 60L * 1000L;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + waitMillis;

        // Create a thread to monitor console input
        CompletableFuture<Void> userInput = CompletableFuture.runAsync(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                reader.readLine(); // Wait for user to press ENTER
            } catch (IOException e) {
                // Ignore - likely interrupted
            }
        });

        // Wait with periodic status updates
        while (System.currentTimeMillis() < endTime) {
            // Check if user pressed ENTER
            if (userInput.isDone()) {
                System.out.println("✋ Wait interrupted by user input. Proceeding with retry...");
                return false;
            }

            // Wait for 10 seconds before next check and show progress
            try {
                Thread.sleep(10000);
                long remaining = endTime - System.currentTimeMillis();
                if (remaining > 0) {
                    long remainingMinutes = remaining / (60 * 1000);
                    long remainingSeconds = (remaining % (60 * 1000)) / 1000;
                    System.out.printf("⏳ Remaining wait time: %d:%02d (press ENTER to skip)%n", remainingMinutes,
                            remainingSeconds);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        // Cancel the input monitoring task
        userInput.cancel(true);

        System.out.println("⏰ Wait period completed.");
        return true;
    }

    /**
     * Enhanced profile loading validation with multiple detection methods
     */
    public boolean isProfileLoadedRobustly(Page page) {
        System.out.println("Performing enhanced profile loading validation...");

        try {
            // Method 1: Primary - Save to project button
            String saveToProjectSelector = "button[data-live-test-action='save-to-project']";
            try {
                page.waitForSelector(saveToProjectSelector, new Page.WaitForSelectorOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                        .setTimeout(config.getLinkedinEnhancedProfileTimeout()));

                System.out.println("✓ Profile loaded - 'Save to project' button detected");
                return true;

            } catch (Exception e) {
                System.out.println("⚠ Primary validation failed, trying fallback methods...");
            }

            // Method 2: Fallback - Profile indicators
            String[] fallbackSelectors = {
                    "h1[data-test-view-name-page-title]",
                    "section[data-test-module='summary']",
                    ".profile-summary",
                    ".profile-card",
                    "[data-test-component='profile-header']",
                    ".profile-photo",
                    ".profile-info"
            };

            for (String selector : fallbackSelectors) {
                try {
                    page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                            .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                            .setTimeout(5000));
                    System.out.println("✓ Profile loaded - fallback selector detected: " + selector);
                    return true;
                } catch (Exception ignored) {
                    // Continue to next selector
                }
            }

            // Method 3: URL validation - check if we're still on a profile page
            String currentUrl = page.url();
            if (currentUrl != null
                    && (currentUrl.contains("/talent/profile/") || currentUrl.contains("/talent/hire/"))) {
                System.out.println("✓ Profile loaded - URL validation successful");
                return true;
            }

            System.out.println("❌ Profile loading validation failed - no indicators found");
            return false;

        } catch (Exception e) {
            System.out.println("❌ Profile loading validation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Functional interface for navigation operations
     */
    @FunctionalInterface
    public interface NavigationFunction {
        boolean navigate(Page page, String profileUrl);
    }

    /**
     * Functional interface for extraction operations
     */
    @FunctionalInterface
    public interface ExtractionFunction {
        boolean extract(Page page);
    }
}
