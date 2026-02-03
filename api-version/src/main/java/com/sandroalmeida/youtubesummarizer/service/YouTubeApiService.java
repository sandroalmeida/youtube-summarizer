package com.sandroalmeida.youtubesummarizer.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.sandroalmeida.youtubesummarizer.model.AccountInfo;
import com.sandroalmeida.youtubesummarizer.model.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class YouTubeApiService {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeApiService.class);

    private final YouTube youtube;
    private final VideoCacheService cacheService;

    @Value("${videos.per.page:16}")
    private int videosPerPage;

    @Value("${youtube.api.default-region-code:US}")
    private String defaultRegionCode;

    // Channel metadata cache: channelId -> uploadsPlaylistId (24h TTL)
    private final Map<String, CachedChannelMeta> channelMetaCache = new ConcurrentHashMap<>();
    private static final long CHANNEL_META_TTL_HOURS = 24;

    // Cached account info
    private AccountInfo cachedAccountInfo;

    static class CachedChannelMeta {
        String uploadsPlaylistId;
        Instant cachedAt;

        CachedChannelMeta(String uploadsPlaylistId) {
            this.uploadsPlaylistId = uploadsPlaylistId;
            this.cachedAt = Instant.now();
        }

        boolean isExpired() {
            return java.time.Duration.between(cachedAt, Instant.now()).toHours() >= CHANNEL_META_TTL_HOURS;
        }
    }

    public YouTubeApiService(YouTube youtube, VideoCacheService cacheService) {
        this.youtube = youtube;
        this.cacheService = cacheService;
    }

    /**
     * Get videos from user's subscriptions, sorted by publish date.
     */
    public List<VideoInfo> getSubscriptionVideos(int page, boolean forceRefresh) throws IOException {
        String tab = "subscriptions";

        if (!forceRefresh) {
            var cached = cacheService.getVideos(tab, page);
            if (cached.isPresent()) {
                return cached.get();
            }
        } else if (page == 0) {
            cacheService.invalidateTab(tab);
        }

        // If we need more videos than what's cached, fetch them
        if (page == 0 || !cacheService.hasPage(tab, page)) {
            if (page == 0) {
                List<VideoInfo> allVideos = fetchSubscriptionVideos();
                // Cache all pages worth of videos
                for (int p = 0; p * videosPerPage < allVideos.size(); p++) {
                    int start = p * videosPerPage;
                    int end = Math.min(start + videosPerPage, allVideos.size());
                    cacheService.cacheVideos(tab, p, allVideos.subList(start, end));
                }
            }
        }

        return cacheService.getVideos(tab, page).orElse(Collections.emptyList());
    }

    /**
     * Get trending/discover videos by category.
     */
    public List<VideoInfo> getDiscoverVideos(int page, String categoryId, boolean forceRefresh) throws IOException {
        String tab = "discover" + (categoryId != null ? "_" + categoryId : "");

        if (!forceRefresh) {
            var cached = cacheService.getVideos(tab, page);
            if (cached.isPresent()) {
                return cached.get();
            }
        } else if (page == 0) {
            cacheService.invalidateTab(tab);
        }

        if (page == 0 || !cacheService.hasPage(tab, page)) {
            if (page == 0) {
                List<VideoInfo> videos = fetchDiscoverVideos(categoryId);
                for (int p = 0; p * videosPerPage < videos.size(); p++) {
                    int start = p * videosPerPage;
                    int end = Math.min(start + videosPerPage, videos.size());
                    cacheService.cacheVideos(tab, p, videos.subList(start, end));
                }
            }
        }

        return cacheService.getVideos(tab, page).orElse(Collections.emptyList());
    }

    /**
     * Get available video categories for a region.
     */
    public List<Map<String, String>> getVideoCategories(String regionCode) throws IOException {
        if (regionCode == null || regionCode.isEmpty()) {
            regionCode = defaultRegionCode;
        }

        logger.info("Fetching video categories for region: {}", regionCode);

        VideoCategoryListResponse response = youtube.videoCategories()
                .list(List.of("snippet"))
                .setRegionCode(regionCode)
                .execute();

        List<Map<String, String>> categories = new ArrayList<>();
        if (response.getItems() != null) {
            for (VideoCategory category : response.getItems()) {
                if (category.getSnippet().getAssignable()) {
                    categories.add(Map.of(
                            "id", category.getId(),
                            "title", category.getSnippet().getTitle()
                    ));
                }
            }
        }

        logger.info("Found {} assignable categories", categories.size());
        return categories;
    }

    /**
     * Get authenticated user's account info.
     */
    public AccountInfo getAccountInfo() throws IOException {
        if (cachedAccountInfo != null) {
            return cachedAccountInfo;
        }

        logger.info("Fetching account info...");

        ChannelListResponse response = youtube.channels()
                .list(List.of("snippet"))
                .setMine(true)
                .execute();

        if (response.getItems() != null && !response.getItems().isEmpty()) {
            Channel channel = response.getItems().get(0);
            ChannelSnippet snippet = channel.getSnippet();

            String name = snippet.getTitle();
            String handle = snippet.getCustomUrl() != null ? snippet.getCustomUrl() : "";
            String profileImageUrl = snippet.getThumbnails() != null
                    ? snippet.getThumbnails().getDefault().getUrl()
                    : null;

            cachedAccountInfo = new AccountInfo(name, handle, profileImageUrl);
            logger.info("Account info: {} ({})", name, handle);
            return cachedAccountInfo;
        }

        return new AccountInfo("YouTube User", "", null);
    }

    // ==================== Private Fetch Methods ====================

    private List<VideoInfo> fetchSubscriptionVideos() throws IOException {
        logger.info("Fetching subscription videos via YouTube Data API...");

        // Step 1: Get subscribed channel IDs
        List<String> channelIds = getSubscribedChannelIds();
        logger.info("Found {} subscribed channels", channelIds.size());

        // Step 2: Get uploads playlist IDs for each channel
        Map<String, String> uploadsPlaylists = getUploadsPlaylistIds(channelIds);

        // Step 3: Get recent video IDs from each playlist
        List<String> videoIds = new ArrayList<>();
        for (String playlistId : uploadsPlaylists.values()) {
            try {
                List<String> ids = getRecentVideoIds(playlistId, 5);
                videoIds.addAll(ids);
            } catch (IOException e) {
                logger.warn("Failed to fetch videos from playlist {}: {}", playlistId, e.getMessage());
            }
        }

        logger.info("Found {} recent video IDs from subscriptions", videoIds.size());

        // Step 4: Get full video details
        List<VideoInfo> videos = getVideoDetails(videoIds);

        // Sort by published date descending
        videos.sort((a, b) -> {
            if (a.getPublishedAt() == null) return 1;
            if (b.getPublishedAt() == null) return -1;
            return b.getPublishedAt().compareTo(a.getPublishedAt());
        });

        logger.info("Returning {} subscription videos", videos.size());
        return videos;
    }

    private List<VideoInfo> fetchDiscoverVideos(String categoryId) throws IOException {
        logger.info("Fetching discover/trending videos (category: {})", categoryId);

        YouTube.Videos.List request = youtube.videos()
                .list(List.of("snippet", "contentDetails"))
                .setChart("mostPopular")
                .setRegionCode(defaultRegionCode)
                .setMaxResults(50L);

        if (categoryId != null && !categoryId.isEmpty()) {
            request.setVideoCategoryId(categoryId);
        }

        VideoListResponse response = request.execute();
        List<VideoInfo> videos = new ArrayList<>();

        if (response.getItems() != null) {
            for (Video video : response.getItems()) {
                videos.add(mapVideoToVideoInfo(video));
            }
        }

        logger.info("Found {} discover videos", videos.size());
        return videos;
    }

    private List<String> getSubscribedChannelIds() throws IOException {
        List<String> channelIds = new ArrayList<>();
        String pageToken = null;

        do {
            YouTube.Subscriptions.List request = youtube.subscriptions()
                    .list(List.of("snippet"))
                    .setMine(true)
                    .setMaxResults(50L);

            if (pageToken != null) {
                request.setPageToken(pageToken);
            }

            SubscriptionListResponse response = request.execute();
            if (response.getItems() != null) {
                for (Subscription sub : response.getItems()) {
                    channelIds.add(sub.getSnippet().getResourceId().getChannelId());
                }
            }

            pageToken = response.getNextPageToken();
        } while (pageToken != null);

        return channelIds;
    }

    private Map<String, String> getUploadsPlaylistIds(List<String> channelIds) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();

        // Check cache first
        List<String> uncachedIds = new ArrayList<>();
        for (String channelId : channelIds) {
            CachedChannelMeta cached = channelMetaCache.get(channelId);
            if (cached != null && !cached.isExpired()) {
                result.put(channelId, cached.uploadsPlaylistId);
            } else {
                uncachedIds.add(channelId);
            }
        }

        // Fetch uncached in batches of 50
        for (int i = 0; i < uncachedIds.size(); i += 50) {
            List<String> batch = uncachedIds.subList(i, Math.min(i + 50, uncachedIds.size()));
            String ids = String.join(",", batch);

            ChannelListResponse response = youtube.channels()
                    .list(List.of("contentDetails"))
                    .setId(List.of(ids))
                    .execute();

            if (response.getItems() != null) {
                for (Channel channel : response.getItems()) {
                    String uploadsId = channel.getContentDetails()
                            .getRelatedPlaylists().getUploads();
                    result.put(channel.getId(), uploadsId);
                    channelMetaCache.put(channel.getId(), new CachedChannelMeta(uploadsId));
                }
            }
        }

        return result;
    }

    private List<String> getRecentVideoIds(String playlistId, int maxResults) throws IOException {
        PlaylistItemListResponse response = youtube.playlistItems()
                .list(List.of("contentDetails"))
                .setPlaylistId(playlistId)
                .setMaxResults((long) maxResults)
                .execute();

        List<String> videoIds = new ArrayList<>();
        if (response.getItems() != null) {
            for (PlaylistItem item : response.getItems()) {
                videoIds.add(item.getContentDetails().getVideoId());
            }
        }
        return videoIds;
    }

    private List<VideoInfo> getVideoDetails(List<String> videoIds) throws IOException {
        List<VideoInfo> videos = new ArrayList<>();

        // Fetch in batches of 50
        for (int i = 0; i < videoIds.size(); i += 50) {
            List<String> batch = videoIds.subList(i, Math.min(i + 50, videoIds.size()));
            String ids = String.join(",", batch);

            VideoListResponse response = youtube.videos()
                    .list(List.of("snippet", "contentDetails"))
                    .setId(List.of(ids))
                    .execute();

            if (response.getItems() != null) {
                for (Video video : response.getItems()) {
                    videos.add(mapVideoToVideoInfo(video));
                }
            }
        }

        return videos;
    }

    private VideoInfo mapVideoToVideoInfo(Video video) {
        VideoSnippet snippet = video.getSnippet();
        String videoId = video.getId();
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

        String thumbnailUrl = null;
        if (snippet.getThumbnails() != null) {
            if (snippet.getThumbnails().getMedium() != null) {
                thumbnailUrl = snippet.getThumbnails().getMedium().getUrl();
            } else if (snippet.getThumbnails().getDefault() != null) {
                thumbnailUrl = snippet.getThumbnails().getDefault().getUrl();
            }
        }

        String duration = formatDuration(video.getContentDetails().getDuration());

        VideoInfo info = new VideoInfo(
                videoId,
                snippet.getTitle(),
                thumbnailUrl,
                snippet.getChannelTitle(),
                videoUrl,
                duration
        );

        if (snippet.getPublishedAt() != null) {
            info.setPublishedAt(snippet.getPublishedAt().toStringRfc3339());
        }

        return info;
    }

    /**
     * Convert ISO 8601 duration (PT1H2M3S) to human-readable format (1:02:03).
     */
    static String formatDuration(String isoDuration) {
        if (isoDuration == null) return "";
        try {
            Duration duration = Duration.parse(isoDuration);
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();

            if (hours > 0) {
                return String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format("%d:%02d", minutes, seconds);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse duration: {}", isoDuration);
            return isoDuration;
        }
    }
}
