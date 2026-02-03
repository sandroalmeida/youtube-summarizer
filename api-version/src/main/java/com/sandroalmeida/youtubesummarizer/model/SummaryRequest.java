package com.sandroalmeida.youtubesummarizer.model;

import java.time.LocalDateTime;

public class SummaryRequest {

    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    private String requestId;
    private String videoUrl;
    private String videoTitle;
    private Status status;
    private String summary;
    private String errorMessage;
    private int queuePosition;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public SummaryRequest() {
    }

    public SummaryRequest(String requestId, String videoUrl, String videoTitle) {
        this.requestId = requestId;
        this.videoUrl = videoUrl;
        this.videoTitle = videoTitle;
        this.status = Status.QUEUED;
        this.createdAt = LocalDateTime.now();
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(int queuePosition) {
        this.queuePosition = queuePosition;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
