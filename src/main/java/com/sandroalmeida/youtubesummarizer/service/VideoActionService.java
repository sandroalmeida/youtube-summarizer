package com.sandroalmeida.youtubesummarizer.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VideoActionService {

    private static final Logger logger = LoggerFactory.getLogger(VideoActionService.class);

    private final BrowserContext browserContext;

    @Value("${timeout.element.wait.ms:10000}")
    private int elementWaitTimeout;

    @Value("${timeout.ai.response.ms:30000}")
    private int aiResponseTimeout;

    public VideoActionService(BrowserContext browserContext) {
        this.browserContext = browserContext;
    }

    private Page getPage() {
        var pages = browserContext.pages();
        if (!pages.isEmpty()) {
            return pages.get(0);
        }
        return browserContext.newPage();
    }

    public String getAiSummary(String videoUrl) {
        Page page = getPage();
        String originalUrl = page.url();

        try {
            logger.info("Getting AI summary for: {}", videoUrl);

            // Navigate to video page
            page.navigate(videoUrl);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2000); // Wait for video page to fully load

            // Click the more actions button (three dots menu)
            Locator moreActionsBtn = page.locator("button[aria-label='More actions'], ytd-menu-renderer button#button").first();
            moreActionsBtn.waitFor(new Locator.WaitForOptions().setTimeout(elementWaitTimeout));
            moreActionsBtn.click();
            page.waitForTimeout(500);

            // Click "Ask" option in the menu
            Locator askOption = page.locator("ytd-menu-service-item-renderer:has(yt-formatted-string:text('Ask'))").first();
            if (askOption.count() == 0) {
                // Try alternative selector
                askOption = page.locator("tp-yt-paper-item:has-text('Ask')").first();
            }
            askOption.waitFor(new Locator.WaitForOptions().setTimeout(elementWaitTimeout));
            askOption.click();
            page.waitForTimeout(1000);

            // Wait for AI panel to open
            Locator aiPanel = page.locator("ytd-engagement-panel-section-list-renderer[target-id='PAyouchat']");
            aiPanel.waitFor(new Locator.WaitForOptions().setTimeout(elementWaitTimeout));

            // Click "Summarize the video" chip
            Locator summarizeChip = page.locator("button.ytwYouChatChipsDataChip:has-text('Summarize')").first();
            if (summarizeChip.count() == 0) {
                summarizeChip = page.locator(".ytwYouChatChipsDataChip:has-text('Summarize')").first();
            }
            summarizeChip.waitFor(new Locator.WaitForOptions().setTimeout(elementWaitTimeout));
            summarizeChip.click();

            // Wait for AI response (this can take a while)
            logger.info("Waiting for AI summary response...");
            page.waitForTimeout(3000); // Initial wait

            // Wait for the summary response to appear
            Locator summaryResponse = page.locator("you-chat-item-view-model markdown-div p").last();
            summaryResponse.waitFor(new Locator.WaitForOptions().setTimeout(aiResponseTimeout));

            // Wait a bit more to ensure full response
            page.waitForTimeout(2000);

            // Extract all summary paragraphs
            Locator allParagraphs = page.locator("you-chat-item-view-model markdown-div");
            StringBuilder summary = new StringBuilder();

            int count = allParagraphs.count();
            for (int i = 0; i < count; i++) {
                String text = allParagraphs.nth(i).textContent();
                if (text != null && !text.isEmpty()
                    && !text.contains("Hello! Curious")
                    && !text.contains("Not sure what to ask")) {
                    summary.append(text.trim()).append("\n\n");
                }
            }

            String result = summary.toString().trim();
            logger.info("AI summary extracted: {} characters", result.length());

            // Close the AI panel
            Locator closeBtn = page.locator("ytd-engagement-panel-title-header-renderer button[aria-label='Close']").first();
            if (closeBtn.count() > 0) {
                closeBtn.click();
            }

            return result.isEmpty() ? "Summary not available for this video." : result;

        } catch (Exception e) {
            logger.error("Failed to get AI summary: {}", e.getMessage());
            return "Failed to get AI summary: " + e.getMessage();
        } finally {
            // Navigate back to original page
            if (!originalUrl.contains("/watch")) {
                page.navigate(originalUrl);
            }
        }
    }

    public boolean saveToWatchLater(String videoUrl) {
        Page page = getPage();
        String originalUrl = page.url();

        try {
            logger.info("Saving to Watch Later: {}", videoUrl);

            // Navigate to video page if not already there
            if (!page.url().equals(videoUrl)) {
                page.navigate(videoUrl);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(2000);
            }

            // Click the more actions button (three dots menu)
            Locator moreActionsBtn = page.locator("button[aria-label='More actions'], ytd-menu-renderer button#button").first();
            moreActionsBtn.waitFor(new Locator.WaitForOptions().setTimeout(elementWaitTimeout));
            moreActionsBtn.click();
            page.waitForTimeout(500);

            // Click "Save" option in the menu
            Locator saveOption = page.locator("ytd-menu-service-item-renderer:has(yt-formatted-string:text('Save'))").first();
            if (saveOption.count() == 0) {
                saveOption = page.locator("tp-yt-paper-item:has-text('Save')").first();
            }
            saveOption.waitFor(new Locator.WaitForOptions().setTimeout(elementWaitTimeout));
            saveOption.click();
            page.waitForTimeout(1000);

            // Wait for save dialog/playlist selection to appear
            Locator watchLaterOption = page.locator("yt-list-item-view-model[aria-label*='Watch later']").first();
            if (watchLaterOption.count() == 0) {
                watchLaterOption = page.locator("tp-yt-paper-checkbox:has-text('Watch later')").first();
            }
            watchLaterOption.waitFor(new Locator.WaitForOptions().setTimeout(elementWaitTimeout));
            watchLaterOption.click();
            page.waitForTimeout(500);

            // Close dialog if there's a close button
            Locator closeBtn = page.locator("yt-icon-button[aria-label='Close'], button[aria-label='Close']").first();
            if (closeBtn.count() > 0) {
                closeBtn.click();
            } else {
                // Press Escape to close
                page.keyboard().press("Escape");
            }

            logger.info("Video saved to Watch Later successfully");
            return true;

        } catch (Exception e) {
            logger.error("Failed to save to Watch Later: {}", e.getMessage());
            return false;
        } finally {
            // Navigate back if needed
            if (!originalUrl.contains("/watch")) {
                page.navigate(originalUrl);
            }
        }
    }
}
