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
    private final TranscriptService transcriptService;
    private final GeminiService geminiService;

    @Value("${timeout.element.wait.ms:10000}")
    private int elementWaitTimeout;

    public VideoActionService(BrowserContext browserContext,
                              TranscriptService transcriptService,
                              GeminiService geminiService) {
        this.browserContext = browserContext;
        this.transcriptService = transcriptService;
        this.geminiService = geminiService;
    }

    private Page getPage() {
        var pages = browserContext.pages();
        if (!pages.isEmpty()) {
            return pages.get(0);
        }
        return browserContext.newPage();
    }

    public String getAiSummary(String videoUrl, String videoTitle) {
        logger.info("Getting AI summary for: {}", videoUrl);

        // Step 1: Fetch the video transcript
        String transcript = transcriptService.getTranscript(videoUrl);

        if (transcript == null || transcript.isEmpty()) {
            logger.warn("No transcript available for video: {}", videoUrl);
            return "No transcript available for this video. The video might not have captions enabled.";
        }

        logger.info("Transcript fetched: {} characters", transcript.length());

        // Step 2: Send transcript to Gemini for summarization
        String summary = geminiService.summarize(transcript, videoTitle);

        logger.info("Summary generated: {} characters", summary.length());
        return summary;
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
