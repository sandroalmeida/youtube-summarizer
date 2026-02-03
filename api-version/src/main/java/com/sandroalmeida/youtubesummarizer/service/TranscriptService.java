package com.sandroalmeida.youtubesummarizer.service;

import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import io.github.thoroldvix.internal.TranscriptApiFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TranscriptService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptService.class);

    private final VideoCacheService cacheService;
    private final YoutubeTranscriptApi transcriptApi;

    public TranscriptService(VideoCacheService cacheService) {
        this.cacheService = cacheService;
        this.transcriptApi = TranscriptApiFactory.createDefault();
    }

    /**
     * Get transcript for a video URL.
     * Checks cache first, then fetches via youtube-transcript-api library.
     */
    public String getTranscript(String videoUrl) {
        // Check cache first
        var cached = cacheService.getTranscript(videoUrl);
        if (cached.isPresent()) {
            return cached.get();
        }

        String videoId = extractVideoId(videoUrl);
        if (videoId == null) {
            logger.error("Could not extract video ID from URL: {}", videoUrl);
            return null;
        }

        try {
            logger.info("Fetching transcript for video: {} (ID: {})", videoUrl, videoId);

            TranscriptList transcriptList = transcriptApi.listTranscripts(videoId);

            // Try to get English transcript
            TranscriptContent transcript = null;
            try {
                // Try manual captions first
                transcript = transcriptList.findTranscript("en").fetch();
                logger.info("Found manual English transcript");
            } catch (TranscriptRetrievalException e) {
                try {
                    // Fall back to auto-generated
                    transcript = transcriptList.findGeneratedTranscript("en").fetch();
                    logger.info("Found auto-generated English transcript");
                } catch (TranscriptRetrievalException e2) {
                    // Try any available transcript and translate
                    try {
                        var anyTranscript = transcriptList.findGeneratedTranscript(
                                transcriptList.iterator().next().getLanguageCode());
                        transcript = anyTranscript.translate("en").fetch();
                        logger.info("Translated transcript to English");
                    } catch (Exception e3) {
                        logger.warn("No transcript available for video {}: {}", videoId, e3.getMessage());
                        return null;
                    }
                }
            }

            if (transcript == null || transcript.getContent() == null || transcript.getContent().isEmpty()) {
                logger.warn("Empty transcript for video: {}", videoId);
                return null;
            }

            // Join transcript segments
            String fullTranscript = transcript.getContent().stream()
                    .map(fragment -> fragment.getText())
                    .collect(Collectors.joining(" "));

            // Cache the transcript
            cacheService.cacheTranscript(videoUrl, fullTranscript);

            logger.info("Transcript fetched: {} characters for video {}", fullTranscript.length(), videoId);
            return fullTranscript;

        } catch (TranscriptRetrievalException e) {
            logger.warn("Failed to fetch transcript for video {}: {}", videoId, e.getMessage());
            return null;
        }
    }

    private String extractVideoId(String videoUrl) {
        if (videoUrl == null) return null;

        // Match youtube.com/watch?v=VIDEO_ID
        Pattern pattern = Pattern.compile("[?&]v=([^&]+)");
        Matcher matcher = pattern.matcher(videoUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Match youtu.be/VIDEO_ID
        Pattern shortPattern = Pattern.compile("youtu\\.be/([^?&]+)");
        Matcher shortMatcher = shortPattern.matcher(videoUrl);
        if (shortMatcher.find()) {
            return shortMatcher.group(1);
        }

        return null;
    }
}
