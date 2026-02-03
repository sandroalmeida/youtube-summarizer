package com.sandroalmeida.youtubesummarizer.controller;

import com.sandroalmeida.youtubesummarizer.entity.SavedVideo;
import com.sandroalmeida.youtubesummarizer.model.AccountInfo;
import com.sandroalmeida.youtubesummarizer.model.SummaryRequest;
import com.sandroalmeida.youtubesummarizer.model.VideoInfo;
import com.sandroalmeida.youtubesummarizer.service.SavedVideoService;
import com.sandroalmeida.youtubesummarizer.service.SummaryQueueService;
import com.sandroalmeida.youtubesummarizer.service.VideoActionService;
import com.sandroalmeida.youtubesummarizer.service.VideoCacheService;
import com.sandroalmeida.youtubesummarizer.service.YouTubeApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class YouTubeController {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeController.class);

    private final YouTubeApiService youTubeApiService;
    private final VideoActionService actionService;
    private final VideoCacheService cacheService;
    private final SummaryQueueService summaryQueueService;
    private final SavedVideoService savedVideoService;

    public YouTubeController(YouTubeApiService youTubeApiService,
                            VideoActionService actionService,
                            VideoCacheService cacheService,
                            SummaryQueueService summaryQueueService,
                            SavedVideoService savedVideoService) {
        this.youTubeApiService = youTubeApiService;
        this.actionService = actionService;
        this.cacheService = cacheService;
        this.summaryQueueService = summaryQueueService;
        this.savedVideoService = savedVideoService;
    }

    @GetMapping("/videos")
    public ResponseEntity<List<VideoInfo>> getVideos(
            @RequestParam(defaultValue = "subscriptions") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        logger.info("GET /api/videos?tab={}&page={}&categoryId={}&forceRefresh={}", tab, page, categoryId, forceRefresh);

        try {
            List<VideoInfo> videos;
            if ("discover".equals(tab)) {
                videos = youTubeApiService.getDiscoverVideos(page, categoryId, forceRefresh);
            } else {
                videos = youTubeApiService.getSubscriptionVideos(page, forceRefresh);
            }
            return ResponseEntity.ok(videos);
        } catch (Exception e) {
            logger.error("Error fetching videos: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, String>>> getCategories(
            @RequestParam(defaultValue = "US") String regionCode) {

        logger.info("GET /api/categories?regionCode={}", regionCode);

        try {
            List<Map<String, String>> categories = youTubeApiService.getVideoCategories(regionCode);
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            logger.error("Error fetching categories: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== Cache Management Endpoints ====================

    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache(
            @RequestParam(required = false) String tab) {

        logger.info("POST /api/cache/clear?tab={}", tab);

        try {
            if (tab != null && !tab.isEmpty()) {
                cacheService.invalidateTab(tab);
                return ResponseEntity.ok(Map.of("message", "Cache cleared for tab: " + tab));
            } else {
                cacheService.invalidateAll();
                return ResponseEntity.ok(Map.of("message", "All caches cleared"));
            }
        } catch (Exception e) {
            logger.error("Error clearing cache: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        logger.info("GET /api/cache/stats");

        try {
            Map<String, Object> stats = cacheService.getCacheStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting cache stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/account")
    public ResponseEntity<AccountInfo> getAccountInfo() {
        logger.info("GET /api/account");

        try {
            AccountInfo accountInfo = youTubeApiService.getAccountInfo();
            return ResponseEntity.ok(accountInfo);
        } catch (Exception e) {
            logger.error("Error fetching account info: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== Async Summary Queue Endpoints ====================

    @PostMapping("/summary/request")
    public ResponseEntity<Map<String, Object>> submitSummaryRequest(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        String videoTitle = request.get("videoTitle");
        logger.info("POST /api/summary/request for: {}", videoUrl);

        if (videoUrl == null || videoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "videoUrl is required"));
        }

        try {
            SummaryRequest summaryRequest = summaryQueueService.submitRequest(videoUrl, videoTitle);

            Map<String, Object> response = new HashMap<>();
            response.put("requestId", summaryRequest.getRequestId());
            response.put("status", summaryRequest.getStatus().name());
            response.put("queuePosition", summaryRequest.getQueuePosition());

            if (summaryRequest.getStatus() == SummaryRequest.Status.COMPLETED) {
                response.put("summary", summaryRequest.getSummary());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting summary request: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/summary/status/{requestId}")
    public ResponseEntity<Map<String, Object>> getSummaryStatus(@PathVariable String requestId) {
        logger.debug("GET /api/summary/status/{}", requestId);

        SummaryRequest summaryRequest = summaryQueueService.getRequestStatus(requestId);

        if (summaryRequest == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("requestId", summaryRequest.getRequestId());
        response.put("status", summaryRequest.getStatus().name());
        response.put("queuePosition", summaryRequest.getQueuePosition());

        if (summaryRequest.getStatus() == SummaryRequest.Status.COMPLETED) {
            response.put("summary", summaryRequest.getSummary());
        } else if (summaryRequest.getStatus() == SummaryRequest.Status.FAILED) {
            response.put("error", summaryRequest.getErrorMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/summary/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateSummary(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        String videoTitle = request.get("videoTitle");
        logger.info("POST /api/summary/regenerate for: {}", videoUrl);

        if (videoUrl == null || videoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "videoUrl is required"));
        }

        try {
            SummaryRequest summaryRequest = summaryQueueService.submitRequest(videoUrl, videoTitle, true);

            Map<String, Object> response = new HashMap<>();
            response.put("requestId", summaryRequest.getRequestId());
            response.put("status", summaryRequest.getStatus().name());
            response.put("queuePosition", summaryRequest.getQueuePosition());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error regenerating summary: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== Saved Videos Endpoints ====================

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveVideo(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        String title = request.get("videoTitle");
        String channelName = request.get("channelName");
        String thumbnailUrl = request.get("thumbnailUrl");
        String duration = request.get("duration");
        String videoId = request.get("videoId");
        String aiSummary = request.get("aiSummary");

        logger.info("POST /api/save for: {}", videoUrl);

        if (videoUrl == null || videoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "videoUrl is required"));
        }

        try {
            SavedVideo savedVideo = savedVideoService.saveVideo(
                    videoUrl, title, channelName, thumbnailUrl, duration, videoId, aiSummary);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Video saved to database");
            response.put("savedVideo", Map.of(
                    "id", savedVideo.getId(),
                    "videoUrl", savedVideo.getVideoUrl(),
                    "title", savedVideo.getTitle(),
                    "savedAt", savedVideo.getSavedAt().toString()
            ));
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            logger.warn("Cannot save video: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error saving video: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    private static final int SAVED_VIDEOS_PER_PAGE = 16;

    @GetMapping("/videos/saved")
    public ResponseEntity<List<SavedVideo>> getSavedVideos(
            @RequestParam(defaultValue = "0") int page) {
        logger.info("GET /api/videos/saved?page={}", page);
        try {
            List<SavedVideo> savedVideos = savedVideoService.getSavedVideosPaginated(page, SAVED_VIDEOS_PER_PAGE);
            return ResponseEntity.ok(savedVideos);
        } catch (Exception e) {
            logger.error("Error getting saved videos: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/videos/saved")
    public ResponseEntity<Map<String, Object>> deleteSavedVideo(@RequestParam String videoUrl) {
        logger.info("DELETE /api/videos/saved?videoUrl={}", videoUrl);

        try {
            boolean deleted = savedVideoService.deleteSavedVideo(videoUrl);
            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("message", deleted ? "Video removed from saved" : "Video not found");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting saved video: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/videos/saved/check")
    public ResponseEntity<Map<String, Object>> checkIfSaved(@RequestParam String videoUrl) {
        logger.debug("GET /api/videos/saved/check?videoUrl={}", videoUrl);

        try {
            boolean isSaved = savedVideoService.isSaved(videoUrl);
            return ResponseEntity.ok(Map.of("isSaved", isSaved));
        } catch (Exception e) {
            logger.error("Error checking if saved: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/videos/saved/urls")
    public ResponseEntity<List<String>> getSavedVideoUrls() {
        logger.debug("GET /api/videos/saved/urls");

        try {
            List<String> urls = savedVideoService.getSavedVideoUrls();
            return ResponseEntity.ok(urls);
        } catch (Exception e) {
            logger.error("Error getting saved video URLs: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
