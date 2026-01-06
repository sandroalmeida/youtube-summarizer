package com.sandroalmeida.youtubesummarizer.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.sandroalmeida.youtubesummarizer.model.AccountInfo;
import com.sandroalmeida.youtubesummarizer.model.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YouTubeScraperService {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeScraperService.class);

    private final BrowserContext browserContext;

    @Value("${youtube.home.url:https://www.youtube.com/}")
    private String youtubeHomeUrl;

    @Value("${youtube.subscriptions.url:https://www.youtube.com/feed/subscriptions}")
    private String youtubeSubscriptionsUrl;

    @Value("${videos.per.page:16}")
    private int videosPerPage;

    @Value("${timeout.element.wait.ms:10000}")
    private int elementWaitTimeout;

    private Page currentPage;
    private AccountInfo cachedAccountInfo;

    public YouTubeScraperService(BrowserContext browserContext) {
        this.browserContext = browserContext;
    }

    private Page getOrCreatePage() {
        if (currentPage == null || currentPage.isClosed()) {
            var pages = browserContext.pages();
            if (!pages.isEmpty()) {
                currentPage = pages.get(0);
                logger.info("Reusing existing page: {}", currentPage.url());
            } else {
                currentPage = browserContext.newPage();
                logger.info("Created new page");
            }
        }
        return currentPage;
    }

    public List<VideoInfo> scrapeVideos(String tab, int pageNum) {
        Page page = getOrCreatePage();
        String targetUrl = "subscriptions".equalsIgnoreCase(tab) ? youtubeSubscriptionsUrl : youtubeHomeUrl;

        logger.info("Scraping {} videos from: {}", tab, targetUrl);

        // Navigate to the target page if not already there
        if (!page.url().startsWith(targetUrl.replace("/feed/subscriptions", ""))) {
            page.navigate(targetUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } else if (!page.url().equals(targetUrl)) {
            page.navigate(targetUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);
        }

        // Wait for video items to load
        page.waitForSelector("ytd-rich-item-renderer",
            new Page.WaitForSelectorOptions().setTimeout(elementWaitTimeout));

        // Scroll to load more videos if needed
        int totalVideosNeeded = (pageNum + 1) * videosPerPage;
        scrollToLoadVideos(page, totalVideosNeeded);

        // Extract videos
        List<VideoInfo> videos = extractVideos(page);

        // Return the requested page of videos
        int startIndex = pageNum * videosPerPage;
        int endIndex = Math.min(startIndex + videosPerPage, videos.size());

        if (startIndex >= videos.size()) {
            return new ArrayList<>();
        }

        return videos.subList(startIndex, endIndex);
    }

    private void scrollToLoadVideos(Page page, int targetCount) {
        int maxScrollAttempts = 10;
        int attempts = 0;

        while (attempts < maxScrollAttempts) {
            int currentCount = page.locator("ytd-rich-item-renderer").count();
            if (currentCount >= targetCount) {
                logger.info("Loaded {} videos (target: {})", currentCount, targetCount);
                break;
            }

            // Scroll down
            page.evaluate("window.scrollTo(0, document.documentElement.scrollHeight)");
            page.waitForTimeout(1500);
            attempts++;
        }
    }

    private List<VideoInfo> extractVideos(Page page) {
        List<VideoInfo> videos = new ArrayList<>();

        Locator videoItems = page.locator("ytd-rich-item-renderer");
        int count = videoItems.count();
        logger.info("Found {} video items on page", count);

        for (int i = 0; i < count; i++) {
            try {
                Locator item = videoItems.nth(i);

                // Skip live videos - they don't have transcripts
                if (isLiveVideo(item)) {
                    logger.debug("Skipping live video at index {}", i);
                    continue;
                }

                // Skip sponsored/ad videos
                if (isSponsoredVideo(item)) {
                    logger.debug("Skipping sponsored video at index {}", i);
                    continue;
                }

                VideoInfo video = extractVideoInfo(item);
                if (video != null && video.getVideoId() != null) {
                    videos.add(video);
                }
            } catch (Exception e) {
                logger.debug("Failed to extract video at index {}: {}", i, e.getMessage());
            }
        }

        logger.info("Successfully extracted {} videos", videos.size());
        return videos;
    }

    private boolean isLiveVideo(Locator item) {
        // Check for live video badge (class yt-badge-shape--thumbnail-live or text "LIVE")
        Locator liveBadge = item.locator("badge-shape.yt-badge-shape--thumbnail-live, .yt-thumbnail-overlay-badge-view-model badge-shape");
        if (liveBadge.count() > 0) {
            String badgeText = liveBadge.first().textContent();
            if (badgeText != null && badgeText.trim().equalsIgnoreCase("LIVE")) {
                return true;
            }
        }

        // Also check for the live avatar ring indicator
        Locator liveRing = item.locator(".yt-spec-avatar-shape--live-ring, .yt-spec-avatar-shape__live-badge");
        if (liveRing.count() > 0) {
            return true;
        }

        return false;
    }

    private boolean isSponsoredVideo(Locator item) {
        // Check for ad layout renderer
        Locator adLayout = item.locator("ytd-in-feed-ad-layout-renderer, ytd-ad-slot-renderer");
        if (adLayout.count() > 0) {
            return true;
        }

        // Check for "Sponsored" badge
        Locator sponsoredBadge = item.locator("ad-badge-view-model, .yt-badge-shape--ad");
        if (sponsoredBadge.count() > 0) {
            return true;
        }

        // Check for ad-related elements
        Locator adElements = item.locator("feed-ad-metadata-view-model, ad-button-view-model");
        if (adElements.count() > 0) {
            return true;
        }

        // Check for Google Ads links
        Locator adLinks = item.locator("a[href*='googleadservices.com'], a[href*='doubleclick.net']");
        if (adLinks.count() > 0) {
            return true;
        }

        return false;
    }

    private VideoInfo extractVideoInfo(Locator item) {
        VideoInfo video = new VideoInfo();

        // Extract video URL and ID
        Locator thumbnailLink = item.locator("a#thumbnail, a[href*='/watch']").first();
        if (thumbnailLink.count() > 0) {
            String href = thumbnailLink.getAttribute("href");
            if (href != null && href.contains("/watch")) {
                video.setVideoUrl("https://www.youtube.com" + href);
                video.setVideoId(extractVideoId(href));
            }
        }

        // Extract thumbnail
        Locator thumbnail = item.locator("yt-image img, ytd-thumbnail img, yt-thumbnail-view-model img").first();
        if (thumbnail.count() > 0) {
            String src = thumbnail.getAttribute("src");
            if (src != null && !src.isEmpty()) {
                video.setThumbnailUrl(src);
            }
        }

        // Extract title - try multiple selectors for different YouTube layouts
        String titleText = null;

        // Try the new lockup view model structure first (h3 with title attribute)
        Locator h3Title = item.locator("h3[title]").first();
        if (h3Title.count() > 0) {
            titleText = h3Title.getAttribute("title");
        }

        // Try the link with aria-label in the new structure
        if (titleText == null || titleText.isEmpty()) {
            Locator linkTitle = item.locator("a.yt-lockup-metadata-view-model__title").first();
            if (linkTitle.count() > 0) {
                titleText = linkTitle.getAttribute("aria-label");
            }
        }

        // Fall back to the classic selectors
        if (titleText == null || titleText.isEmpty()) {
            Locator title = item.locator("#video-title, #video-title-link").first();
            if (title.count() > 0) {
                titleText = title.getAttribute("title");
                if (titleText == null || titleText.isEmpty()) {
                    titleText = title.textContent();
                }
            }
        }

        video.setTitle(titleText != null ? titleText.trim() : "");

        // Extract channel name - try multiple selectors for different YouTube layouts
        String channelName = null;

        // Try the new lockup view model structure
        Locator channelLink = item.locator("yt-content-metadata-view-model a[href^='/@']").first();
        if (channelLink.count() > 0) {
            channelName = channelLink.textContent();
        }

        // Fall back to classic selectors
        if (channelName == null || channelName.isEmpty()) {
            Locator channel = item.locator("ytd-channel-name a, #text.ytd-channel-name").first();
            if (channel.count() > 0) {
                channelName = channel.textContent();
            }
        }

        video.setChannelName(channelName != null ? channelName.trim() : "");

        // Extract duration
        Locator duration = item.locator("badge-shape .yt-badge-shape, ytd-thumbnail-overlay-time-status-renderer span").first();
        if (duration.count() > 0) {
            String durationText = duration.textContent();
            if (durationText != null) {
                video.setDuration(durationText.trim());
            }
        }

        return video;
    }

    private String extractVideoId(String url) {
        Pattern pattern = Pattern.compile("[?&]v=([^&]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public AccountInfo getAccountInfo() {
        if (cachedAccountInfo != null) {
            return cachedAccountInfo;
        }

        Page page = getOrCreatePage();

        try {
            // Click on avatar to open account menu
            Locator avatar = page.locator("button#avatar-btn, img.ytd-topbar-menu-button-renderer").first();
            if (avatar.count() > 0) {
                avatar.click();
                page.waitForTimeout(500);

                // Extract account info
                Locator accountName = page.locator("#account-name").first();
                Locator accountHandle = page.locator("#channel-handle").first();

                AccountInfo info = new AccountInfo();
                if (accountName.count() > 0) {
                    info.setName(accountName.textContent().trim());
                }
                if (accountHandle.count() > 0) {
                    info.setHandle(accountHandle.textContent().trim());
                }

                // Close the menu by pressing Escape
                page.keyboard().press("Escape");

                cachedAccountInfo = info;
                logger.info("Account info: {} ({})", info.getName(), info.getHandle());
                return info;
            }
        } catch (Exception e) {
            logger.warn("Failed to get account info: {}", e.getMessage());
        }

        // Return default if we can't get the info
        return new AccountInfo("YouTube User", "@user", null);
    }
}
