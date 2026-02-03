package com.sandroalmeida.youtubesummarizer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VideoActionService {

    private static final Logger logger = LoggerFactory.getLogger(VideoActionService.class);

    private final TranscriptService transcriptService;
    private final GeminiService geminiService;
    private final VideoCacheService cacheService;

    public VideoActionService(TranscriptService transcriptService,
                              GeminiService geminiService,
                              VideoCacheService cacheService) {
        this.transcriptService = transcriptService;
        this.geminiService = geminiService;
        this.cacheService = cacheService;
    }

    public String getAiSummary(String videoUrl, String videoTitle) {
        logger.info("Getting AI summary for: {}", videoUrl);

        // Check cache first
        var cachedSummary = cacheService.getSummary(videoUrl);
        if (cachedSummary.isPresent()) {
            logger.info("Returning cached summary for: {}", videoUrl);
            return cachedSummary.get();
        }

        // Step 1: Fetch the video transcript (uses transcript cache internally)
        String transcript = transcriptService.getTranscript(videoUrl);

        if (transcript == null || transcript.isEmpty()) {
            logger.warn("No transcript available for video: {}", videoUrl);
            return "No transcript available for this video. The video might not have captions enabled.";
        }

        logger.info("Transcript fetched: {} characters", transcript.length());

        // Step 2: Send transcript to Gemini for summarization
        String summary = geminiService.summarize(transcript, videoTitle);

        // Cache the summary
        if (summary != null && !summary.isEmpty()) {
            cacheService.cacheSummary(videoUrl, summary);
        }

        logger.info("Summary generated: {} characters", summary.length());
        return summary;
    }
}
