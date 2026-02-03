package com.sandroalmeida.youtubesummarizer;

import com.sandroalmeida.youtubesummarizer.service.SavedVideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final SavedVideoService savedVideoService;

    public Application(SavedVideoService savedVideoService) {
        this.savedVideoService = savedVideoService;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application ready, loading saved videos into cache...");
        savedVideoService.loadSavedVideosIntoCache();
        logger.info("Application is ready at http://localhost:8080");
    }
}
