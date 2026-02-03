package com.sandroalmeida.youtubesummarizer.service;

import com.sandroalmeida.youtubesummarizer.entity.SavedVideo;
import com.sandroalmeida.youtubesummarizer.repository.SavedVideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SavedVideoService {

    private static final Logger logger = LoggerFactory.getLogger(SavedVideoService.class);

    private final SavedVideoRepository repository;
    private final VideoCacheService cacheService;

    public SavedVideoService(SavedVideoRepository repository, VideoCacheService cacheService) {
        this.repository = repository;
        this.cacheService = cacheService;
    }

    @Transactional
    public SavedVideo saveVideo(String videoUrl, String title, String channelName,
                                 String thumbnailUrl, String duration, String videoId,
                                 String providedSummary) {
        logger.info("Saving video to database: {}", videoUrl);

        String summary = providedSummary;
        if (summary == null || summary.isEmpty()) {
            Optional<String> cachedSummary = cacheService.getSummary(videoUrl);
            if (cachedSummary.isEmpty()) {
                throw new IllegalStateException("Cannot save video without AI summary. Generate summary first.");
            }
            summary = cachedSummary.get();
        }

        Optional<String> cachedTranscript = cacheService.getTranscript(videoUrl);

        Optional<SavedVideo> existing = repository.findByVideoUrl(videoUrl);
        SavedVideo savedVideo;

        if (existing.isPresent()) {
            savedVideo = existing.get();
            logger.info("Updating existing saved video: {}", videoUrl);
        } else {
            savedVideo = new SavedVideo(videoUrl, title);
            logger.info("Creating new saved video: {}", videoUrl);
        }

        savedVideo.setTitle(title);
        savedVideo.setChannelName(channelName);
        savedVideo.setThumbnailUrl(thumbnailUrl);
        savedVideo.setDuration(duration);
        savedVideo.setVideoId(videoId);
        savedVideo.setAiSummary(summary);
        savedVideo.setTranscript(cachedTranscript.orElse(null));

        savedVideo = repository.save(savedVideo);
        logger.info("Video saved successfully: {} (id={})", videoUrl, savedVideo.getId());

        return savedVideo;
    }

    public Optional<SavedVideo> getByVideoUrl(String videoUrl) {
        return repository.findByVideoUrl(videoUrl);
    }

    public boolean isSaved(String videoUrl) {
        return repository.existsByVideoUrl(videoUrl);
    }

    public List<SavedVideo> getAllSavedVideos() {
        return repository.findAllByOrderBySavedAtDesc();
    }

    public List<SavedVideo> getSavedVideosPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAllByOrderBySavedAtDesc(pageable).getContent();
    }

    @Transactional
    public boolean deleteSavedVideo(String videoUrl) {
        Optional<SavedVideo> video = repository.findByVideoUrl(videoUrl);
        if (video.isPresent()) {
            repository.delete(video.get());
            logger.info("Deleted saved video: {}", videoUrl);
            return true;
        }
        logger.warn("Video not found for deletion: {}", videoUrl);
        return false;
    }

    public void loadSavedVideosIntoCache() {
        logger.info("Loading saved videos into cache...");
        List<SavedVideo> savedVideos = repository.findAll();

        int summariesLoaded = 0;
        int transcriptsLoaded = 0;

        for (SavedVideo video : savedVideos) {
            if (video.getAiSummary() != null && !video.getAiSummary().isEmpty()) {
                cacheService.cacheSummary(video.getVideoUrl(), video.getAiSummary());
                summariesLoaded++;
            }

            if (video.getTranscript() != null && !video.getTranscript().isEmpty()) {
                cacheService.cacheTranscript(video.getVideoUrl(), video.getTranscript());
                transcriptsLoaded++;
            }
        }

        logger.info("Loaded {} saved videos into cache ({} summaries, {} transcripts)",
                    savedVideos.size(), summariesLoaded, transcriptsLoaded);
    }

    public List<String> getSavedVideoUrls() {
        return repository.findAll().stream()
                .map(SavedVideo::getVideoUrl)
                .toList();
    }
}
