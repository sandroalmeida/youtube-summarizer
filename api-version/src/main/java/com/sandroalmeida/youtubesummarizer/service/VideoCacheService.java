package com.sandroalmeida.youtubesummarizer.service;

import com.sandroalmeida.youtubesummarizer.model.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoCacheService {

    private static final Logger logger = LoggerFactory.getLogger(VideoCacheService.class);

    // Video list cache by tab
    private final Map<String, TabCache> tabCaches = new ConcurrentHashMap<>();

    // AI Summary cache: videoUrl -> CachedSummary
    private final Map<String, CachedSummary> summaryCache = new ConcurrentHashMap<>();

    // Transcript cache: videoUrl -> CachedTranscript
    private final Map<String, CachedTranscript> transcriptCache = new ConcurrentHashMap<>();

    // Cache configuration
    private static final int VIDEOS_PER_PAGE = 16;
    private static final long SUMMARY_TTL_DAYS = 7;
    private static final long TRANSCRIPT_TTL_DAYS = 30;

    static class TabCache {
        List<VideoInfo> videos = new ArrayList<>();
        LocalDateTime lastUpdated;
        int totalPagesLoaded = 0;

        TabCache() {
            this.lastUpdated = LocalDateTime.now();
        }
    }

    static class CachedSummary {
        String summary;
        LocalDateTime cachedAt;

        CachedSummary(String summary) {
            this.summary = summary;
            this.cachedAt = LocalDateTime.now();
        }

        boolean isExpired() {
            return ChronoUnit.DAYS.between(cachedAt, LocalDateTime.now()) >= SUMMARY_TTL_DAYS;
        }
    }

    static class CachedTranscript {
        String transcript;
        LocalDateTime cachedAt;

        CachedTranscript(String transcript) {
            this.transcript = transcript;
            this.cachedAt = LocalDateTime.now();
        }

        boolean isExpired() {
            return ChronoUnit.DAYS.between(cachedAt, LocalDateTime.now()) >= TRANSCRIPT_TTL_DAYS;
        }
    }

    // ==================== Video List Cache Methods ====================

    public Optional<List<VideoInfo>> getVideos(String tab, int page) {
        TabCache cache = tabCaches.get(tab.toLowerCase());
        if (cache == null) {
            return Optional.empty();
        }

        int startIndex = page * VIDEOS_PER_PAGE;
        int endIndex = Math.min(startIndex + VIDEOS_PER_PAGE, cache.videos.size());

        if (startIndex >= cache.videos.size()) {
            return Optional.empty();
        }

        List<VideoInfo> pageVideos = new ArrayList<>(cache.videos.subList(startIndex, endIndex));
        logger.info("Cache HIT for tab '{}' page {} - returning {} videos", tab, page, pageVideos.size());
        return Optional.of(pageVideos);
    }

    public boolean hasPage(String tab, int page) {
        TabCache cache = tabCaches.get(tab.toLowerCase());
        if (cache == null) {
            return false;
        }
        int startIndex = page * VIDEOS_PER_PAGE;
        return startIndex < cache.videos.size();
    }

    public void cacheVideos(String tab, int page, List<VideoInfo> videos) {
        String tabKey = tab.toLowerCase();
        TabCache cache = tabCaches.computeIfAbsent(tabKey, k -> new TabCache());

        int expectedStartIndex = page * VIDEOS_PER_PAGE;

        if (page == 0) {
            cache.videos.clear();
            cache.videos.addAll(videos);
            cache.totalPagesLoaded = 1;
        } else {
            while (cache.videos.size() < expectedStartIndex) {
                logger.warn("Gap detected in cache for tab '{}' at page {}", tab, page);
                break;
            }

            if (cache.videos.size() == expectedStartIndex) {
                cache.videos.addAll(videos);
                cache.totalPagesLoaded = page + 1;
            }
        }

        cache.lastUpdated = LocalDateTime.now();
        logger.info("Cached {} videos for tab '{}' page {}. Total cached: {}",
                    videos.size(), tab, page, cache.videos.size());
    }

    public void invalidateTab(String tab) {
        TabCache removed = tabCaches.remove(tab.toLowerCase());
        if (removed != null) {
            logger.info("Invalidated cache for tab '{}' ({} videos removed)", tab, removed.videos.size());
        }
    }

    public void invalidateAllVideos() {
        tabCaches.clear();
        logger.info("Invalidated all video caches");
    }

    // ==================== AI Summary Cache Methods ====================

    public Optional<String> getSummary(String videoUrl) {
        CachedSummary cached = summaryCache.get(videoUrl);
        if (cached == null) {
            return Optional.empty();
        }

        if (cached.isExpired()) {
            summaryCache.remove(videoUrl);
            return Optional.empty();
        }

        logger.info("Summary cache HIT for: {}", videoUrl);
        return Optional.of(cached.summary);
    }

    public void cacheSummary(String videoUrl, String summary) {
        summaryCache.put(videoUrl, new CachedSummary(summary));
        logger.info("Cached summary for: {} ({} chars)", videoUrl, summary.length());
    }

    // ==================== Transcript Cache Methods ====================

    public Optional<String> getTranscript(String videoUrl) {
        CachedTranscript cached = transcriptCache.get(videoUrl);
        if (cached == null) {
            return Optional.empty();
        }

        if (cached.isExpired()) {
            transcriptCache.remove(videoUrl);
            return Optional.empty();
        }

        logger.info("Transcript cache HIT for: {}", videoUrl);
        return Optional.of(cached.transcript);
    }

    public void cacheTranscript(String videoUrl, String transcript) {
        transcriptCache.put(videoUrl, new CachedTranscript(transcript));
        logger.info("Cached transcript for: {} ({} chars)", videoUrl, transcript.length());
    }

    // ==================== Cache Management ====================

    public void invalidateAll() {
        invalidateAllVideos();
        summaryCache.clear();
        transcriptCache.clear();
        logger.info("Invalidated ALL caches");
    }

    public void invalidateVideoCache(String videoUrl) {
        boolean summaryRemoved = summaryCache.remove(videoUrl) != null;
        boolean transcriptRemoved = transcriptCache.remove(videoUrl) != null;
        logger.info("Invalidated cache for video: {} (summary: {}, transcript: {})",
                    videoUrl, summaryRemoved, transcriptRemoved);
    }

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        Map<String, Object> videoStats = new LinkedHashMap<>();
        for (Map.Entry<String, TabCache> entry : tabCaches.entrySet()) {
            TabCache cache = entry.getValue();
            Map<String, Object> tabStats = new LinkedHashMap<>();
            tabStats.put("videoCount", cache.videos.size());
            tabStats.put("pagesLoaded", cache.totalPagesLoaded);
            tabStats.put("lastUpdated", cache.lastUpdated.toString());
            videoStats.put(entry.getKey(), tabStats);
        }
        stats.put("videoCaches", videoStats);

        Map<String, Object> summaryStats = new LinkedHashMap<>();
        summaryStats.put("totalEntries", summaryCache.size());
        long expiredSummaries = summaryCache.values().stream()
                .filter(CachedSummary::isExpired)
                .count();
        summaryStats.put("expiredEntries", expiredSummaries);
        stats.put("summaryCache", summaryStats);

        Map<String, Object> transcriptStats = new LinkedHashMap<>();
        transcriptStats.put("totalEntries", transcriptCache.size());
        long expiredTranscripts = transcriptCache.values().stream()
                .filter(CachedTranscript::isExpired)
                .count();
        transcriptStats.put("expiredEntries", expiredTranscripts);
        stats.put("transcriptCache", transcriptStats);

        return stats;
    }

    public void cleanupExpired() {
        int removedSummaries = 0;
        int removedTranscripts = 0;

        Iterator<Map.Entry<String, CachedSummary>> summaryIterator = summaryCache.entrySet().iterator();
        while (summaryIterator.hasNext()) {
            if (summaryIterator.next().getValue().isExpired()) {
                summaryIterator.remove();
                removedSummaries++;
            }
        }

        Iterator<Map.Entry<String, CachedTranscript>> transcriptIterator = transcriptCache.entrySet().iterator();
        while (transcriptIterator.hasNext()) {
            if (transcriptIterator.next().getValue().isExpired()) {
                transcriptIterator.remove();
                removedTranscripts++;
            }
        }

        if (removedSummaries > 0 || removedTranscripts > 0) {
            logger.info("Cleanup: removed {} expired summaries, {} expired transcripts",
                       removedSummaries, removedTranscripts);
        }
    }
}
