package com.sandroalmeida.youtubesummarizer.service;

import com.sandroalmeida.youtubesummarizer.model.SummaryRequest;
import com.sandroalmeida.youtubesummarizer.model.SummaryRequest.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SummaryQueueService {

    private static final Logger logger = LoggerFactory.getLogger(SummaryQueueService.class);

    private final VideoActionService actionService;
    private final VideoCacheService cacheService;

    private final ConcurrentLinkedQueue<SummaryRequest> queue = new ConcurrentLinkedQueue<>();
    private final Map<String, SummaryRequest> requests = new ConcurrentHashMap<>();
    private final AtomicBoolean processorRunning = new AtomicBoolean(false);
    private static final int MAX_COMPLETED_REQUESTS = 100;

    public SummaryQueueService(VideoActionService actionService, VideoCacheService cacheService) {
        this.actionService = actionService;
        this.cacheService = cacheService;
    }

    public SummaryRequest submitRequest(String videoUrl, String videoTitle) {
        return submitRequest(videoUrl, videoTitle, false);
    }

    public SummaryRequest submitRequest(String videoUrl, String videoTitle, boolean forceRegenerate) {
        if (forceRegenerate) {
            logger.info("Force regenerate requested for: {}", videoUrl);
            cacheService.invalidateVideoCache(videoUrl);
        }

        if (!forceRegenerate) {
            var cachedSummary = cacheService.getSummary(videoUrl);
            if (cachedSummary.isPresent()) {
                logger.info("Summary already cached for: {}", videoUrl);
                SummaryRequest request = new SummaryRequest(UUID.randomUUID().toString(), videoUrl, videoTitle);
                request.setStatus(Status.COMPLETED);
                request.setSummary(cachedSummary.get());
                request.setCompletedAt(LocalDateTime.now());
                request.setQueuePosition(0);
                return request;
            }
        }

        if (!forceRegenerate) {
            for (SummaryRequest existing : requests.values()) {
                if (existing.getVideoUrl().equals(videoUrl) &&
                    (existing.getStatus() == Status.QUEUED || existing.getStatus() == Status.PROCESSING)) {
                    logger.info("Request already pending for: {}", videoUrl);
                    updateQueuePositions();
                    return existing;
                }
            }
        }

        String requestId = UUID.randomUUID().toString();
        SummaryRequest request = new SummaryRequest(requestId, videoUrl, videoTitle);

        queue.offer(request);
        requests.put(requestId, request);
        updateQueuePositions();

        logger.info("New summary request queued: {} (position {}){}",
                    requestId, request.getQueuePosition(), forceRegenerate ? " [FORCE REGENERATE]" : "");

        triggerProcessing();
        return request;
    }

    public SummaryRequest getRequestStatus(String requestId) {
        SummaryRequest request = requests.get(requestId);
        if (request != null) {
            updateQueuePositions();
        }
        return request;
    }

    public Map<String, Object> getQueueStats() {
        long queued = requests.values().stream().filter(r -> r.getStatus() == Status.QUEUED).count();
        long processing = requests.values().stream().filter(r -> r.getStatus() == Status.PROCESSING).count();
        long completed = requests.values().stream().filter(r -> r.getStatus() == Status.COMPLETED).count();
        long failed = requests.values().stream().filter(r -> r.getStatus() == Status.FAILED).count();

        return Map.of(
            "queued", queued,
            "processing", processing,
            "completed", completed,
            "failed", failed,
            "total", requests.size()
        );
    }

    private void triggerProcessing() {
        if (processorRunning.compareAndSet(false, true)) {
            processQueueAsync();
        }
    }

    @Async
    public void processQueueAsync() {
        logger.info("Queue processor started");

        try {
            while (!queue.isEmpty()) {
                SummaryRequest request = queue.poll();
                if (request == null) break;

                if (request.getStatus() == Status.COMPLETED) {
                    continue;
                }

                processRequest(request);
            }
        } finally {
            processorRunning.set(false);
            logger.info("Queue processor stopped");

            if (!queue.isEmpty()) {
                triggerProcessing();
            }
        }

        cleanupOldRequests();
    }

    private void processRequest(SummaryRequest request) {
        logger.info("Processing request: {} for {}", request.getRequestId(), request.getVideoUrl());

        request.setStatus(Status.PROCESSING);
        updateQueuePositions();

        try {
            String summary = actionService.getAiSummary(request.getVideoUrl(), request.getVideoTitle());

            request.setSummary(summary);
            request.setStatus(Status.COMPLETED);
            request.setCompletedAt(LocalDateTime.now());

            logger.info("Request completed: {}", request.getRequestId());

        } catch (Exception e) {
            logger.error("Request failed: {} - {}", request.getRequestId(), e.getMessage());
            request.setStatus(Status.FAILED);
            request.setErrorMessage(e.getMessage());
            request.setCompletedAt(LocalDateTime.now());
        }
    }

    private void updateQueuePositions() {
        int position = 1;
        for (SummaryRequest request : queue) {
            if (request.getStatus() == Status.QUEUED) {
                request.setQueuePosition(position++);
            }
        }

        for (SummaryRequest request : requests.values()) {
            if (request.getStatus() != Status.QUEUED) {
                request.setQueuePosition(0);
            }
        }
    }

    private void cleanupOldRequests() {
        long completedCount = requests.values().stream()
            .filter(r -> r.getStatus() == Status.COMPLETED || r.getStatus() == Status.FAILED)
            .count();

        if (completedCount > MAX_COMPLETED_REQUESTS) {
            requests.values().stream()
                .filter(r -> r.getStatus() == Status.COMPLETED || r.getStatus() == Status.FAILED)
                .sorted((a, b) -> a.getCompletedAt().compareTo(b.getCompletedAt()))
                .limit(completedCount - MAX_COMPLETED_REQUESTS)
                .forEach(r -> requests.remove(r.getRequestId()));

            logger.info("Cleaned up old completed requests");
        }
    }
}
