package com.sandroalmeida.youtubesummarizer.config;

import com.microsoft.playwright.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Configuration
public class BrowserConfig {

    private static final Logger logger = LoggerFactory.getLogger(BrowserConfig.class);

    @Value("${browser.cdp.endpoint:http://localhost:9222}")
    private String cdpEndpoint;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext browserContext;

    @Bean
    public Playwright playwright() {
        logger.info("Initializing Playwright...");
        this.playwright = Playwright.create();
        return playwright;
    }

    @Bean
    public BrowserContext browserContext(Playwright playwright) {
        logger.info("Connecting to Chrome browser via CDP at: {}", cdpEndpoint);

        if (!testCdpConnection()) {
            throw new RuntimeException(
                "Cannot connect to Chrome browser at " + cdpEndpoint + ". " +
                "Please start Chrome with remote debugging enabled:\n" +
                "/Applications/Google\\ Chrome.app/Contents/MacOS/Google\\ Chrome " +
                "--remote-debugging-port=9222 --user-data-dir=\"$HOME/chrome-debug-profile\""
            );
        }

        try {
            this.browser = playwright.chromium().connectOverCDP(cdpEndpoint);
            logger.info("Connected to browser: {}", browser.version());

            var contexts = browser.contexts();
            if (!contexts.isEmpty()) {
                this.browserContext = contexts.get(0);
                logger.info("Reusing existing browser context with {} page(s)", browserContext.pages().size());
            } else {
                this.browserContext = browser.newContext();
                logger.info("Created new browser context");
            }

            return browserContext;
        } catch (Exception e) {
            logger.error("Failed to connect to browser: {}", e.getMessage());
            throw new RuntimeException("Failed to connect to Chrome browser", e);
        }
    }

    private boolean testCdpConnection() {
        try {
            URL url = new URL(cdpEndpoint + "/json/version");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == 200;
        } catch (IOException e) {
            logger.warn("CDP connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up browser resources...");
        // Note: We don't close the browser context as it's the user's session
        // We only close our Playwright connection
        if (playwright != null) {
            playwright.close();
        }
    }
}
