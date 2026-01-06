package com.sandroalmeida.youtubesummarizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String model;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiBaseUrl;

    public GeminiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String summarize(String transcript, String videoTitle) {
        if (transcript == null || transcript.isEmpty()) {
            return "No transcript available for this video.";
        }

        String prompt = buildSummarizationPrompt(transcript, videoTitle);

        try {
            logger.info("Calling Gemini API for summarization...");

            String url = String.format("%s/%s:generateContent?key=%s", apiBaseUrl, model, apiKey);

            // Build request body
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of(
                        "parts", List.of(
                            Map.of("text", prompt)
                        )
                    )
                ),
                "generationConfig", Map.of(
                    "temperature", 0.7,
                    "maxOutputTokens", 1024,
                    "topP", 0.9
                )
            );

            String response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseGeminiResponse(response);

        } catch (Exception e) {
            logger.error("Failed to call Gemini API: {}", e.getMessage());
            return "Failed to generate summary: " + e.getMessage();
        }
    }

    private String buildSummarizationPrompt(String transcript, String videoTitle) {
        String titleContext = (videoTitle != null && !videoTitle.isEmpty())
            ? "Video Title: " + videoTitle + "\n\n"
            : "";

        return String.format("""
            %sPlease provide a clear and concise summary of the following video transcript.
            Focus on the main points, key takeaways, and important information.
            Format the summary in a readable way with bullet points for key points if appropriate.

            Transcript:
            %s

            Summary:""",
            titleContext,
            truncateTranscript(transcript)
        );
    }

    private String truncateTranscript(String transcript) {
        // Gemini has token limits, so truncate very long transcripts
        // Approximately 4 chars per token, limit to ~30k tokens for safety
        int maxChars = 100000;
        if (transcript.length() > maxChars) {
            logger.warn("Transcript truncated from {} to {} characters", transcript.length(), maxChars);
            return transcript.substring(0, maxChars) + "\n\n[Transcript truncated due to length...]";
        }
        return transcript;
    }

    private String parseGeminiResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // Check for errors
            if (root.has("error")) {
                String errorMessage = root.path("error").path("message").asText("Unknown error");
                logger.error("Gemini API error: {}", errorMessage);
                return "API Error: " + errorMessage;
            }

            // Extract the generated text
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    String text = parts.get(0).path("text").asText("");
                    logger.info("Summary generated: {} characters", text.length());
                    return text.trim();
                }
            }

            logger.warn("Unexpected Gemini response format");
            return "Failed to parse summary from API response.";

        } catch (Exception e) {
            logger.error("Failed to parse Gemini response: {}", e.getMessage());
            return "Failed to parse API response: " + e.getMessage();
        }
    }
}
