package com.sandroalmeida.youtubesummarizer;

import com.sandroalmeida.youtubesummarizer.service.YouTubeScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final YouTubeScraperService scraperService;

    public Application(YouTubeScraperService scraperService) {
        this.scraperService = scraperService;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application ready, preloading YouTube...");
        scraperService.preloadYouTube();
        logger.info("YouTube preload complete. Application is ready at http://localhost:8080");
    }
}
