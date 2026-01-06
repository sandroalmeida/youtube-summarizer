package com.sandroalmeida.youtubesummarizer.controller;

import com.sandroalmeida.youtubesummarizer.model.AccountInfo;
import com.sandroalmeida.youtubesummarizer.model.VideoInfo;
import com.sandroalmeida.youtubesummarizer.service.VideoActionService;
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

    public YouTubeController(YouTubeScraperService scraperService, VideoActionService actionService) {
        this.scraperService = scraperService;
        this.actionService = actionService;
    }

    @GetMapping("/videos")
    public ResponseEntity<List<VideoInfo>> getVideos(
            @RequestParam(defaultValue = "subscriptions") String tab,
            @RequestParam(defaultValue = "0") int page) {

        logger.info("GET /api/videos?tab={}&page={}", tab, page);

        try {
            List<VideoInfo> videos = scraperService.scrapeVideos(tab, page);
            return ResponseEntity.ok(videos);
        } catch (Exception e) {
            logger.error("Error fetching videos: {}", e.getMessage());
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

    @PostMapping("/summary")
    public ResponseEntity<Map<String, String>> getAiSummary(@RequestBody Map<String, String> request) {
        String videoUrl = request.get("videoUrl");
        logger.info("POST /api/summary for: {}", videoUrl);

        if (videoUrl == null || videoUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "videoUrl is required"));
        }

        try {
            String summary = actionService.getAiSummary(videoUrl);
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
