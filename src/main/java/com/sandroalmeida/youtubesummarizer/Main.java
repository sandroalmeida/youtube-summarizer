package com.sandroalmeida.youtubesummarizer;

import com.sandroalmeida.youtubesummarizer.config.ConfigManager;
import com.sandroalmeida.youtubesummarizer.utils.BrowserUtils;
import com.sandroalmeida.youtubesummarizer.utils.TimestampedPrintStream;
import com.microsoft.playwright.*;

/**
 * Main class for YouTube Summarizer
 * Orchestrates the YouTube video summarization workflow
 */
public class Main {
  static {
    System.setOut(new TimestampedPrintStream(System.out));
  }

  private static final ConfigManager config = ConfigManager.getInstance();

  public static void main(String[] args) {

    if (config.isDebugMode()) {
      config.printConfiguration();
    }

    try (Playwright playwright = Playwright.create()) {

      // Initialize browser and context
      BrowserContext context = BrowserUtils.initializeBrowser(playwright);

      System.out.println("=== YouTube Summarizer Started ===");

      // TODO: Implement YouTube summarization logic here

      // Keep browser open for user inspection
      BrowserUtils.waitForUserInput();

      // Cleanup
      context.browser().close();
      System.out.println("=== YouTube Summarizer completed successfully! ===");

    } catch (Exception e) {
      System.err.println("=== SUMMARIZATION FAILED ===");
      System.err.println("Error: " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
      System.exit(1);
    }
  }
}
