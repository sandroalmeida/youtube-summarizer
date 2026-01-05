package com.sandroalmeida.youtubesummarizer.utils;

import com.microsoft.playwright.Page;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Helper functions that introduce subtle randomness to Playwright interactions
 * to better emulate human navigation patterns.
 */
public final class HumanLikeInteractionUtils {
  private static final double DEFAULT_VIEWPORT_WIDTH = 1280;
  private static final double DEFAULT_VIEWPORT_HEIGHT = 720;

  private HumanLikeInteractionUtils() {
  }

  public static void shortPause(Page page) {
    randomPause(page, 180, 420);
  }

  public static void mediumPause(Page page) {
    randomPause(page, 650, 1400);
  }

  public static void randomPause(Page page, long minMillis, long maxMillis) {
    if (page == null || maxMillis < minMillis) {
      return;
    }

    long duration = ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
    if (duration > 0) {
      page.waitForTimeout(duration);
    }
  }

  public static void performMicroInteraction(Page page) {
    if (page == null) {
      return;
    }

    int iterations = ThreadLocalRandom.current().nextInt(1, 3);
    for (int i = 0; i < iterations; i++) {
      if (ThreadLocalRandom.current().nextBoolean()) {
        performMicroScroll(page);
      }
      if (ThreadLocalRandom.current().nextInt(100) < 45) {
        moveMouseSlightly(page);
      }
      shortPause(page);
    }
  }

  public static void performMicroScroll(Page page) {
    if (page == null) {
      return;
    }

    int delta = ThreadLocalRandom.current().nextInt(-220, 360);
    if (delta == 0) {
      delta = 120;
    }

    try {
      page.evaluate("delta => { window.scrollBy({ top: delta, behavior: 'instant' }); }", delta);
    } catch (RuntimeException ignored) {
      // Continue even if evaluation fails; we only care about side effects.
    }
  }

  public static void moveMouseSlightly(Page page) {
    if (page == null) {
      return;
    }

    var viewport = page.viewportSize();
    double width = viewport != null ? viewport.width : DEFAULT_VIEWPORT_WIDTH;
    double height = viewport != null ? viewport.height : DEFAULT_VIEWPORT_HEIGHT;

    double x = ThreadLocalRandom.current().nextDouble(width * 0.2, width * 0.8);
    double y = ThreadLocalRandom.current().nextDouble(height * 0.3, height * 0.85);

    try {
      page.mouse().move(x, y);
    } catch (RuntimeException ignored) {
    }
  }

  public static void simulateContentEngagement(Page page) {
    if (page == null) {
      return;
    }

    mediumPause(page);
    performMicroInteraction(page);
    if (ThreadLocalRandom.current().nextBoolean()) {
      mediumPause(page);
    }
  }
}
