package com.sandroalmeida.youtubesummarizer.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TranscriptService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptService.class);

    private final BrowserContext browserContext;

    @Value("${timeout.element.wait.ms:10000}")
    private int elementWaitTimeout;

    public TranscriptService(BrowserContext browserContext) {
        this.browserContext = browserContext;
    }

    private Page getPage() {
        var pages = browserContext.pages();
        if (!pages.isEmpty()) {
            return pages.get(0);
        }
        return browserContext.newPage();
    }

    public String getTranscript(String videoUrl) {
        Page page = getPage();
        String originalUrl = page.url();

        try {
            logger.info("Fetching transcript for: {}", videoUrl);

            // Navigate to video page
            page.navigate(videoUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(3000); // Wait for page to fully load

            // Step 1: Scroll down a bit to make sure description is visible
            page.evaluate("window.scrollBy(0, 300)");
            page.waitForTimeout(500);

            // Step 1b: Click "...more" to expand description
            try {
                Locator moreButton = page.locator(
                    "ytd-text-inline-expander #expand, " +
                    "#description-inline-expander #expand, " +
                    "tp-yt-paper-button#expand, " +
                    "#expand"
                ).first();
                if (moreButton.count() > 0) {
                    moreButton.scrollIntoViewIfNeeded();
                    if (moreButton.isVisible()) {
                        logger.info("Clicking expand button to show full description...");
                        moreButton.click();
                        page.waitForTimeout(1500);
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not click expand button: {}", e.getMessage());
            }

            // Step 2: Look for "Show transcript" button in the expanded description
            boolean transcriptOpened = false;

            // Try finding "Show transcript" button - specific selector from YouTube's structure
            Locator showTranscriptBtn = page.locator(
                "ytd-video-description-transcript-section-renderer button[aria-label='Show transcript'], " +
                "ytd-video-description-transcript-section-renderer #primary-button button, " +
                "button[aria-label='Show transcript']"
            ).first();

            if (showTranscriptBtn.count() > 0) {
                try {
                    showTranscriptBtn.scrollIntoViewIfNeeded();
                    if (showTranscriptBtn.isVisible()) {
                        logger.info("Found 'Show transcript' button, clicking...");
                        showTranscriptBtn.click();
                        page.waitForTimeout(2000);
                        transcriptOpened = true;
                    }
                } catch (Exception e) {
                    logger.debug("Could not click transcript button: {}", e.getMessage());
                }
            }

            // Step 3: If button not found, try the three-dot menu approach
            if (!transcriptOpened) {
                logger.info("Trying menu approach for transcript...");
                transcriptOpened = openTranscriptViaMenu(page);
            }

            if (!transcriptOpened) {
                logger.warn("Could not open transcript panel");
                return null;
            }

            // Step 4: Extract transcript text
            return extractTranscriptText(page);

        } catch (Exception e) {
            logger.error("Failed to get transcript: {}", e.getMessage(), e);
            return null;
        } finally {
            // Close transcript panel if open
            try {
                Locator closeBtn = page.locator(
                    "ytd-engagement-panel-section-list-renderer[target-id='engagement-panel-searchable-transcript'] button[aria-label='Close']"
                ).first();
                if (closeBtn.count() > 0 && closeBtn.isVisible()) {
                    closeBtn.click();
                }
            } catch (Exception e) {
                logger.debug("Could not close transcript panel: {}", e.getMessage());
            }

            // Navigate back if needed
            if (!originalUrl.contains("/watch") && !originalUrl.equals(videoUrl)) {
                try {
                    page.navigate(originalUrl);
                } catch (Exception e) {
                    logger.debug("Could not navigate back: {}", e.getMessage());
                }
            }
        }
    }

    private boolean openTranscriptViaMenu(Page page) {
        try {
            // Click the more actions button under the video (three dots)
            Locator menuButton = page.locator(
                "#top-level-buttons-computed ytd-menu-renderer #button, " +
                "ytd-watch-metadata ytd-menu-renderer #button, " +
                "#actions ytd-menu-renderer button"
            ).first();

            if (menuButton.count() > 0 && menuButton.isVisible()) {
                logger.info("Clicking menu button...");
                menuButton.click();
                page.waitForTimeout(500);

                // Look for "Show transcript" in the menu
                Locator transcriptOption = page.locator(
                    "ytd-menu-service-item-renderer:has-text('transcript'), " +
                    "tp-yt-paper-item:has-text('transcript'), " +
                    "yt-formatted-string:has-text('Show transcript')"
                ).first();

                if (transcriptOption.count() > 0 && transcriptOption.isVisible()) {
                    logger.info("Found transcript option in menu, clicking...");
                    transcriptOption.click();
                    page.waitForTimeout(2000);
                    return true;
                }

                // Close menu if transcript not found
                page.keyboard().press("Escape");
            }

            logger.warn("Transcript option not found in menu");
            return false;

        } catch (Exception e) {
            logger.error("Failed to open transcript via menu: {}", e.getMessage());
            return false;
        }
    }

    private String extractTranscriptText(Page page) {
        // Wait for transcript panel to load
        // The panel has target-id="engagement-panel-searchable-transcript"
        Locator transcriptPanel = page.locator(
            "ytd-engagement-panel-section-list-renderer[target-id='engagement-panel-searchable-transcript'], " +
            "ytd-transcript-renderer"
        );

        try {
            transcriptPanel.first().waitFor(new Locator.WaitForOptions().setTimeout(elementWaitTimeout));
            logger.info("Transcript panel found");
        } catch (Exception e) {
            logger.warn("Transcript panel did not appear: {}", e.getMessage());
            return null;
        }

        // Give it a moment to load all segments
        page.waitForTimeout(1500);

        // Extract transcript segments - the text is in yt-formatted-string.segment-text
        // Inside ytd-transcript-segment-renderer elements
        Locator segments = page.locator(
            "ytd-transcript-segment-renderer yt-formatted-string.segment-text"
        );

        List<String> transcriptLines = new ArrayList<>();
        int count = segments.count();

        logger.info("Found {} transcript segments", count);

        for (int i = 0; i < count; i++) {
            try {
                String text = segments.nth(i).textContent();
                if (text != null && !text.trim().isEmpty()) {
                    transcriptLines.add(text.trim());
                }
            } catch (Exception e) {
                logger.debug("Could not extract segment {}: {}", i, e.getMessage());
            }
        }

        // If still empty, try alternative selectors
        if (transcriptLines.isEmpty()) {
            logger.info("Trying alternative selectors...");

            // Try getting text from segment div aria-label which contains the text
            Locator altSegments = page.locator("ytd-transcript-segment-renderer .segment");
            count = altSegments.count();
            logger.info("Found {} alternative segments", count);

            for (int i = 0; i < count; i++) {
                try {
                    String ariaLabel = altSegments.nth(i).getAttribute("aria-label");
                    if (ariaLabel != null && !ariaLabel.trim().isEmpty()) {
                        // aria-label format: "12 seconds exploded in a fire, filling out the"
                        // Remove the time prefix
                        String text = ariaLabel.replaceFirst("^\\d+\\s*seconds?\\s*", "");
                        if (!text.isEmpty()) {
                            transcriptLines.add(text.trim());
                        }
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }

        String transcript = String.join(" ", transcriptLines);
        logger.info("Extracted transcript: {} characters", transcript.length());

        return transcript.isEmpty() ? null : transcript;
    }
}
