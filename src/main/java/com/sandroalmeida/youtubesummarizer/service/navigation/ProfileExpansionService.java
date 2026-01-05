package com.sandroalmeida.youtubesummarizer.service.navigation;

import com.sandroalmeida.youtubesummarizer.config.ConfigManager;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Service class responsible for expanding all collapsible sections in LinkedIn profiles
 * This includes summary, experience positions, and skills sections
 */
public class ProfileExpansionService {
    private static final ConfigManager config = ConfigManager.getInstance();

    /**
     * Expose the entire profile by clicking all "show more" buttons
     * This includes summary, experience positions and skills
     *
     * @param page The page containing the profile to expand
     */
    public void exposeEntireProfile(Page page) {
        System.out.println("=== ProfileExpansionService: Exposing entire profile ===");

        // Expand summary section (first, as it's at the top)
        expandSummarySection(page);

        // Expand experience positions
        expandExperiencePositions(page);

        // Expand skills section
        expandSkillsSection(page);

        System.out.println("✓ ProfileExpansionService: Profile fully exposed");
    }

    /**
     * Click "See more of summary" button to expand summary section
     *
     * @param page The page containing the profile
     */
    private void expandSummarySection(Page page) {
        System.out.println("Expanding summary section...");

        try {
            // Look for the summary section container first
            String summaryContainerSelector = "section[data-test-profile-summary-card]";

            // Check if summary section exists
            Locator summaryContainer = page.locator(summaryContainerSelector);
            if (summaryContainer.count() == 0) {
                System.out.println("  - No summary section found");
                return;
            }

            // Look for the "See more of summary" button using various selectors
            String[] summaryButtonSelectors = {
                "section[data-test-profile-summary-card] button[data-test-decorated-line-clamp-see-more-button]",
                "section[data-test-profile-summary-card] button:has-text('See more of summary')",
                "button[data-test-decorated-line-clamp-see-more-button]",
                "button.decorated-line-clamp__see-more-button"
            };

            Locator summaryButton = null;
            String usedSelector = null;

            // Try each selector until we find the button
            for (String selector : summaryButtonSelectors) {
                summaryButton = page.locator(selector);
                if (summaryButton.count() > 0) {
                    usedSelector = selector;
                    break;
                }
            }

            if (summaryButton.count() == 0) {
                System.out.println("  - No summary expand button found - summary may already be fully displayed");
                return;
            }

            // Check if button is visible and clickable
            if (!summaryButton.first().isVisible()) {
                System.out.println("  - Summary expand button not visible - summary may already be expanded");
                return;
            }

            String buttonText = summaryButton.first().textContent();
            System.out.println("  - Found summary button: '" + buttonText + "' using selector: " + usedSelector);

            try {
                summaryButton.first().click();

                // Wait for summary content to expand
                page.waitForTimeout(config.getDynamicContentWaitTime());

                System.out.println("✓ Expanded summary section successfully");

                if (config.isDebugMode()) {
                    // Verify the button is no longer visible (indicating successful expansion)
                    boolean buttonStillVisible = summaryButton.first().isVisible();
                    System.out.println("Debug: Summary button still visible after click: " + buttonStillVisible);
                }

            } catch (Exception e) {
                System.out.println("  - Could not click summary button: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("⚠ Warning: Error expanding summary section: " + e.getMessage());
            if (config.isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Click "Show more experience positions" button until no longer present
     *
     * @param page The page containing the profile
     */
    private void expandExperiencePositions(Page page) {
        System.out.println("Expanding experience positions...");

        try {
            // First, try to locate the experience section container using multiple selectors
            String[] experienceSectionSelectors = {
                "div.experience-card",
                "section[data-test-profile-background-card] div.experience-card",
                "section[data-test-profile-experience-card]",
                "div[data-live-test-profile-experience-card]"
            };

            Locator experienceSection = null;
            String usedSectionSelector = null;

            // Try each selector until we find the experience section
            for (String selector : experienceSectionSelectors) {
                Locator candidateSection = page.locator(selector);
                if (candidateSection.count() > 0) {
                    experienceSection = candidateSection;
                    usedSectionSelector = selector;
                    break;
                }
            }

            if (experienceSection == null || experienceSection.count() == 0) {
                System.out.println("  - No experience section found");
                if (config.isDebugMode()) {
                    System.out.println("Debug: Tried selectors: " + String.join(", ", experienceSectionSelectors));
                }
                return;
            }

            if (config.isDebugMode()) {
                System.out.println("Debug: Found experience section using selector: " + usedSectionSelector);
            }

            int clickCount = 0;
            int maxClicks = 10; // Safety limit to prevent infinite loops

            while (clickCount < maxClicks) {
                // Look for the "Show more experience positions" button with multiple strategies
                String[] experienceButtonSelectors = {
                    "button[aria-label*='Show'][aria-label*='experience']",
                    "button[aria-label*='experience']",
                    "button[data-test-expand-more-lower-button]",
                    "button[data-live-test-expandable-button]",
                    "button.expandable-list__button"
                };

                Locator experienceButton = null;
                String usedSelector = null;

                // Try each selector until we find a visible button, scoped to the experience section
                for (String selector : experienceButtonSelectors) {
                    Locator candidateButton = experienceSection.locator(selector);
                    if (candidateButton.count() > 0 && candidateButton.first().isVisible()) {
                        experienceButton = candidateButton;
                        usedSelector = selector;
                        break;
                    }
                }

                if (experienceButton == null || experienceButton.count() == 0) {
                    if (clickCount == 0) {
                        System.out.println("  - No experience expand buttons found - all positions may already be visible");
                        if (config.isDebugMode()) {
                            System.out.println("Debug: Tried button selectors: " + String.join(", ", experienceButtonSelectors));
                        }
                    } else {
                        System.out.println("  - No more experience expand buttons found");
                    }
                    break;
                }

                // Check if button is visible and clickable (using .first() to avoid strict mode violation)
                if (!experienceButton.first().isVisible()) {
                    System.out.println("  - Experience expand button not visible");
                    break;
                }

                if (config.isDebugMode() && usedSelector != null) {
                    System.out.println("  - Using selector: " + usedSelector);
                }

                System.out.println("  - Clicking 'Show more experience positions' button (click " + (clickCount + 1) + ")");

                try {
                    experienceButton.first().click();
                    clickCount++;

                    // Wait for content to load after click
                    page.waitForTimeout(config.getDynamicContentWaitTime());

                } catch (Exception e) {
                    System.out.println("  - Could not click experience button: " + e.getMessage());
                    break;
                }
            }

            if (clickCount > 0) {
                System.out.println("✓ Expanded experience positions with " + clickCount + " clicks");
            } else {
                System.out.println("✓ No experience positions to expand");
            }

            if (clickCount >= maxClicks) {
                System.out.println("⚠ Warning: Reached maximum click limit for experience expansion");
            }

        } catch (Exception e) {
            System.out.println("⚠ Warning: Error expanding experience positions: " + e.getMessage());
            if (config.isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Click "Show all X skills" button to expand skills section
     *
     * @param page The page containing the profile
     */
    private void expandSkillsSection(Page page) {
        System.out.println("Expanding skills section...");

        try {
            // First, try to locate the skills section container using multiple selectors
            String[] skillsSectionSelectors = {
                "div[data-live-test-profile-skills-card]",
                "div[data-test-expandable-stepper][data-live-test-profile-skills-card]",
                "section[data-test-profile-skills-card]",
                ".skills-card-expandable"
            };

            Locator skillsSection = null;
            String usedSectionSelector = null;

            // Try each selector until we find the skills section
            for (String selector : skillsSectionSelectors) {
                Locator candidateSection = page.locator(selector);
                if (candidateSection.count() > 0) {
                    skillsSection = candidateSection;
                    usedSectionSelector = selector;
                    break;
                }
            }

            if (skillsSection == null || skillsSection.count() == 0) {
                System.out.println("  - No skills section found");
                if (config.isDebugMode()) {
                    System.out.println("Debug: Tried selectors: " + String.join(", ", skillsSectionSelectors));
                }
                return;
            }

            if (config.isDebugMode()) {
                System.out.println("Debug: Found skills section using selector: " + usedSectionSelector);
            }

            // Look for skills expand button using scoped and specific selectors
            String[] skillsSelectors = {
                // Most specific: button with aria-label containing "skills" within the found skills section
                "button[aria-label*='Show all'][aria-label*='skills']",
                "button[aria-label*='skill']",
                "button[data-test-expand-more-lower-button]",
                "button[data-live-test-expandable-button]",
                // Fallback: expandable list button
                "button.expandable-list__button"
            };

            Locator skillsButton = null;
            String usedSelector = null;

            // Try each selector until we find a visible button, scoped to the skills section
            for (String selector : skillsSelectors) {
                Locator candidateButton = skillsSection.locator(selector);
                if (candidateButton.count() > 0 && candidateButton.first().isVisible()) {
                    skillsButton = candidateButton;
                    usedSelector = selector;
                    break;
                }
            }

            if (skillsButton == null || skillsButton.count() == 0) {
                System.out.println("  - No skills expand button found - skills may already be fully displayed");
                if (config.isDebugMode()) {
                    System.out.println("Debug: Tried button selectors: " + String.join(", ", skillsSelectors));
                }
                return;
            }

            // Check if button is visible and clickable (using .first() to avoid strict mode violation)
            if (!skillsButton.first().isVisible()) {
                System.out.println("  - Skills expand button not visible");
                return;
            }

            String buttonText = skillsButton.first().textContent().trim();
            System.out.println("  - Found skills button: '" + buttonText + "'" + 
                (config.isDebugMode() && usedSelector != null ? " using selector: " + usedSelector : ""));

            try {
                skillsButton.first().click();

                // Wait for skills content to load
                page.waitForTimeout(config.getDynamicContentWaitTime());

                System.out.println("✓ Expanded skills section successfully");

            } catch (Exception e) {
                System.out.println("  - Could not click skills button: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("⚠ Warning: Error expanding skills section: " + e.getMessage());
            if (config.isDebugMode()) {
                e.printStackTrace();
            }
        }
    }
}