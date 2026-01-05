package com.sandroalmeida.youtubesummarizer.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for YouTube Summarizer.
 * Handles loading and accessing configuration properties.
 */
public class ConfigManager {
    private static final String CONFIG_FILE = "config.properties";
    private static ConfigManager instance;
    private Properties properties;

    private ConfigManager() {
        loadProperties();
    }

    /**
     * Get singleton instance of ConfigManager
     */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Load properties from config file
     */
    private void loadProperties() {
        properties = new Properties();

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new RuntimeException("Configuration file '" + CONFIG_FILE + "' not found in classpath");
            }

            properties.load(inputStream);
            System.out.println("Configuration loaded successfully from " + CONFIG_FILE);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration file: " + e.getMessage(), e);
        }
    }

    /**
     * Get string property value
     */
    public String getString(String key) {
        return getString(key, null);
    }

    /**
     * Get string property value with default
     */
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Get integer property value
     */
    public int getInt(String key) {
        return getInt(key, 0);
    }

    /**
     * Get integer property value with default
     */
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid integer value for key '" + key + "': " + value +
                ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get boolean property value
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    /**
     * Get boolean property value with default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Get long property value
     */
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    /**
     * Get long property value with default
     */
    public long getLong(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid long value for key '" + key + "': " + value +
                ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Check if a property exists
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    /**
     * Get all property keys
     */
    public java.util.Set<String> getPropertyNames() {
        return properties.stringPropertyNames();
    }

    /**
     * Print all configuration properties (for debugging)
     */
    public void printConfiguration() {
        System.out.println("=== Configuration Properties ===");
        properties.entrySet().stream()
            .sorted((e1, e2) -> e1.getKey().toString().compareTo(e2.getKey().toString()))
            .forEach(entry -> System.out.println(entry.getKey() + " = " + entry.getValue()));
        System.out.println("================================");
    }

    // Convenience methods for commonly used configurations

    public String getLinkedinLoginUrl() {
        return getString("linkedin.login.url");
    }

    public String getLinkedinRecruiterUrl() {
        return getString("linkedin.recruiter.url");
    }

    public String getLinkedinProject() {
        String value = getString("linkedin.project", null);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        return "";
    }

    public boolean isBrowserHeadless() {
        return getBoolean("browser.headless");
    }

    public int getBrowserSlowMotion() {
        return getInt("browser.slow.motion.ms");
    }

    public int getViewportWidth() {
        return getInt("browser.viewport.width");
    }

    public int getViewportHeight() {
        return getInt("browser.viewport.height");
    }

    /**
     * Get Chrome user data directory path for using existing profile
     * This is the parent directory that contains Default, Profile 1, etc.
     */
    public String getChromeUserDataDir() {
        return getString("browser.chrome.user.data.dir");
    }

    /**
     * Get specific Chrome profile directory name (e.g., "Default", "Profile 1")
     */
    public String getChromeProfileDir() {
        return getString("browser.chrome.profile.dir", "Default");
    }

    /**
     * Check if we should use existing Chrome profile instead of launching new browser
     */
    public boolean useExistingChromeProfile() {
        return getBoolean("browser.use.existing.profile", false);
    }

    /**
     * Check if we should connect to already running Chrome browser via CDP
     */
    public boolean useRunningBrowser() {
        return getBoolean("browser.connect.to.running", false);
    }

    /**
     * Get CDP endpoint URL for connecting to running browser
     */
    public String getCdpEndpoint() {
        return getString("browser.cdp.endpoint", "http://localhost:9222");
    }

    /**
     * Get CDP port for connecting to running browser
     */
    public int getCdpPort() {
        return getInt("browser.cdp.port", 9222);
    }

    public long getLoginTimeout() {
        return getLong("timeout.login.ms");
    }

    public long getProfileLoadTimeout() {
        return getLong("timeout.profile.load.ms");
    }

    public long getElementWaitTimeout() {
        return getLong("timeout.element.wait.ms");
    }

    public long getImageWaitTimeout() {
        return getLong("timeout.image.wait.ms");
    }

    public long getSectionsWaitTimeout() {
        return getLong("timeout.sections.wait.ms");
    }

    public long getDynamicContentWaitTime() {
        return getLong("wait.dynamic.content.ms");
    }

    public boolean isDebugMode() {
        return getBoolean("debug.mode");
    }

    public String getLogLevel() {
        return getString("log.level", "INFO");
    }

    public boolean isSkipLogin() {
        return getBoolean("browser.skip.login");
    }


    /**
     * Get waiting delay in minutes for LinkedIn throttling backoff
     */
    public int getLinkedinWaitingDelayMinutes() {
        return getInt("linkedin.waiting.delay", 10);
    }

    /**
     * Get retry attempts for waiting delay logic
     */
    public int getLinkedinRetryCount() {
        return getInt("linkedin.retry", 3);
    }

    /**
     * Get immediate retry attempts before triggering waiting delay
     */
    public int getLinkedinImmediateRetryCount() {
        return getInt("linkedin.immediate.retry", 2);
    }

    /**
     * Get minimum consecutive failures threshold before considering rate limiting
     */
    public int getLinkedinConsecutiveFailuresThreshold() {
        return getInt("linkedin.consecutive.failures.threshold", 3);
    }

    /**
     * Get enhanced timeout for profile loading detection
     */
    public long getLinkedinEnhancedProfileTimeout() {
        return getLong("linkedin.enhanced.profile.timeout", 30000);
    }

    /**
     * Get pause duration in minutes when no records with status=0 are found
     */
    public int getProfileQueueEmptyPauseMinutes() {
        return getInt("profile.queue.empty.pause.minutes", 5);
    }
}