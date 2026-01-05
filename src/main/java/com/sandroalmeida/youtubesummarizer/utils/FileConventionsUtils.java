package com.sandroalmeida.youtubesummarizer.utils;

public class FileConventionsUtils {

  /**
   * Convert project name to filename format
   * - Convert to lowercase
   * - Replace spaces with hyphens
   * - Add .txt extension
   */
  public static String convertProjectNameToFileName(String projectName) {
    return projectName.toLowerCase()
        .trim()
        .replaceAll("\\s+", "-") // Replace one or more spaces with single hyphen
        .replaceAll("[^a-z0-9\\-]", "") // Remove any non-alphanumeric characters except hyphens
        + ".txt";
  }
}
