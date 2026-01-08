package com.sandroalmeida.youtubesummarizer.controller;

import com.sandroalmeida.youtubesummarizer.model.AccountInfo;
import com.sandroalmeida.youtubesummarizer.model.SummaryRequest;
import com.sandroalmeida.youtubesummarizer.model.VideoInfo;
import com.sandroalmeida.youtubesummarizer.service.SummaryQueueService;
import com.sandroalmeida.youtubesummarizer.service.VideoActionService;
import com.sandroalmeida.youtubesummarizer.service.VideoCacheService;
import com.sandroalmeida.youtubesummarizer.service.YouTubeScraperService;
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

    private final YouTubeScraperService scraperService;
    private final VideoActionService actionService;
    private final VideoCacheService cacheService;
    private final SummaryQueueService summaryQueueService;

    public YouTubeController(YouTubeScraperService scraperService,
                            VideoActionService actionService,
                            VideoCacheService cacheService,
                            SummaryQueueService summaryQueueService) {
        this.scraperService = scraperService;
        this.actionService = actionService;
        this.cacheService = cacheService;
        this.summaryQueueService = summaryQueueService;
    }

    @GetMapping("/videos")
    public ResponseEntity<List<VideoInfo>> getVideos(
            @RequestParam(defaultValue = "subscriptions") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "false") boolean forceRefresh) {

        logger.info("GET /api/videos?tab={}&page={}&forceRefresh={}", tab, page, forceRefresh);

        try {
            List<VideoInfo> videos = scraperService.getVideos(tab, page, forceRefresh);
            return ResponseEntity.ok(videos);
        } catch (Exception e) {
            logger.error("Error fetching videos: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== Cache Management Endpoints ====================

    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, String>> invalidateCache(
            @RequestParam(required = false) String tab) {

        logger.info("POST /api/cache/invalidate?tab={}", tab);

        try {
            if (tab != null && !tab.isEmpty()) {
                cacheService.invalidateTab(tab);
                return ResponseEntity.ok(Map.of("message", "Cache invalidated for tab: " + tab));
            } else {
                cacheService.invalidateAll();
                return ResponseEntity.ok(Map.of("message", "All caches invalidated"));
            }
        } catch (Exception e) {
            logger.error("Error invalidating cache: {}", e.getMessage());
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
            AccountInfo accountInfo = scraperService.getAccountInfo();
            return ResponseEntity.ok(accountInfo);
        } catch (Exception e) {
            logger.error("Error fetching account info: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== Async Summary Queue Endpoints ====================

    /**
     * Submit a summary request to the queue.
     * Returns immediately with request ID and status.
     */
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

            // If already completed (from cache), include the summary
            if (summaryRequest.getStatus() == SummaryRequest.Status.COMPLETED) {
                response.put("summary", summaryRequest.getSummary());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error submitting summary request: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get the status of a summary request.
     */
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

    /**
     * Get queue statistics.
     */
    @GetMapping("/summary/queue/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        logger.debug("GET /api/summary/queue/stats");
        return ResponseEntity.ok(summaryQueueService.getQueueStats());
    }

    /**
     * Legacy sync endpoint - kept for backwards compatibility.
     * Consider using /api/summary/request for better UX.
     */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, String>> getAiSummary(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        String videoTitle = request.get("videoTitle");
        logger.info("POST /api/summary (sync) for: {}", videoUrl);

        if (videoUrl == null || videoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "videoUrl is required"));
        }

        try {
            String summary = actionService.getAiSummary(videoUrl, videoTitle);
            Map<String, String> response = new HashMap<>();
            response.put("summary", summary);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting AI summary: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveToWatchLater(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        logger.info("POST /api/save for: {}", videoUrl);

        if (videoUrl == null || videoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "videoUrl is required"));
        }

        try {
            boolean success = actionService.saveToWatchLater(videoUrl);
            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "Saved to Watch Later" : "Failed to save");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error saving to Watch Later: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
