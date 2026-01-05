package com.sandroalmeida.youtubesummarizer.service.profile;

import com.sandroalmeida.youtubesummarizer.config.ConfigManager;
import com.sandroalmeida.youtubesummarizer.model.Experience;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Service class responsible for extracting work experience data from LinkedIn profiles
 * This class handles all aspects of experience extraction including position details,
 * company information, dates, locations, summaries, and skills
 */
public class ExperienceExtractionService {
  private static final ConfigManager config = ConfigManager.getInstance();

  /**
   * Extract all work experiences from the profile page
   *
   * @param page the page containing the profile
   * @return List of Experience objects, or null if not found
   */
  public List<Experience> extractExperiences(Page page) {
    try {
      // Look for the experience section
      String experienceSectionSelector = ".experience-card, section[data-test-experience-card], .experience__resume-title--with-resume-enhancement";

      Locator experienceSection = page.locator(experienceSectionSelector).first();
      if (experienceSection.count() == 0) {
        return null;
      }

      // Look for all position items within the experience section
      String positionItemsSelector = ".position-item[data-test-position-list-container]";
      Locator positionItems = page.locator(positionItemsSelector);

      int itemCount = positionItems.count();
      if (itemCount == 0) {
        return null;
      }

      List<Experience> experiences = new ArrayList<>();

      for (int i = 0; i < itemCount; i++) {
        try {
          Locator positionItem = positionItems.nth(i);
          Experience experience = extractSingleExperience(positionItem, i + 1);

          if (experience != null) {
            experiences.add(experience);
          }

        } catch (Exception e) {
          System.out.println("⚠ Warning: Error extracting experience " + (i + 1) + ": " + e.getMessage());
          if (config.isDebugMode()) {
            e.printStackTrace();
          }
        }
      }
      return experiences.isEmpty() ? null : experiences;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting experiences: " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
      return null;
    }
  }

  /**
   * Extract a single experience from a position item element
   *
   * @param positionItem the DOM element containing the experience data
   * @param index        the position index for logging purposes
   * @return Experience object or null if extraction fails
   */
  private Experience extractSingleExperience(Locator positionItem, int index) {
    try {
      // Extract position title
      String position = extractExperiencePosition(positionItem);

      // Extract company name and employment status
      String[] companyInfo = extractExperienceCompanyInfo(positionItem);
      String companyName = companyInfo[0];
      String employmentStatus = companyInfo[1];

      // Extract date range and duration
      String[] dateInfo = extractExperienceDateInfo(positionItem);
      String dateRange = dateInfo[0];
      String duration = dateInfo[1];

      // Extract location
      String location = extractExperienceLocation(positionItem);

      // Extract summary
      String summary = extractExperienceSummary(positionItem);

      // Extract skills
      List<String> skills = extractExperienceSkills(positionItem);

      // Extract company URL
      String companyUrl = extractExperienceCompanyUrl(positionItem);

      // Create Experience object

      return new Experience(position, companyName, employmentStatus,
          dateRange, duration, location, summary, skills, companyUrl);

    } catch (Exception e) {
      System.out.println("    ❌ Error extracting experience " + index + ": " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
      return null;
    }
  }

  /**
   * Extract position title from experience element
   *
   * @param positionItem the DOM element containing the experience data
   * @return position title or null if not found
   */
  private String extractExperiencePosition(Locator positionItem) {
    try {
      // Target the position title link
      String positionSelector = "h3.background-entity__summary-definition--title a[data-test-position-entity-title]";
      Locator positionElement = positionItem.locator(positionSelector);

      if (positionElement.count() > 0) {
        String position = positionElement.textContent();
        if (position != null && !position.trim().isEmpty()) {
          return cleanHtmlEntities(position.trim());
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting position: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract company name and employment status from experience element
   *
   * @param positionItem the DOM element containing the experience data
   * @return array containing [companyName, employmentStatus]
   */
  private String[] extractExperienceCompanyInfo(Locator positionItem) {
    try {
      String companySelector = "dd[data-test-position-entity-company-name]";
      Locator companyElement = positionItem.locator(companySelector);

      if (companyElement.count() > 0) {
        // Try to get company name from link first
        Locator companyLink = companyElement.locator("a[data-test-position-entity-company-link]");
        String companyName = null;

        if (companyLink.count() > 0) {
          companyName = companyLink.textContent();
        } else {
          // Try to get from span without link
          Locator companySpan = companyElement.locator("span[data-test-position-entity-company-without-link]");
          if (companySpan.count() > 0) {
            companyName = companySpan.textContent();
          }
        }

        // Extract employment status
        String employmentStatus = null;
        Locator statusElement = companyElement.locator("span[data-test-position-entity-employment-status]");
        if (statusElement.count() > 0) {
          employmentStatus = statusElement.textContent();
        }

        // Clean the extracted values
        if (companyName != null) {
          companyName = cleanHtmlEntities(companyName.trim());
        }
        if (employmentStatus != null) {
          employmentStatus = cleanHtmlEntities(employmentStatus.trim());
        }

        return new String[]{companyName, employmentStatus};
      }

      return new String[]{null, null};

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting company info: " + e.getMessage());
      }
      return new String[]{null, null};
    }
  }

  /**
   * Extract date range and duration from experience element
   *
   * @param positionItem the DOM element containing the experience data
   * @return array containing [dateRange, duration]
   */
  private String[] extractExperienceDateInfo(Locator positionItem) {
    try {
      String dateSelector = "dd[data-test-position-entity-date-range-container]";
      Locator dateElement = positionItem.locator(dateSelector);

      if (dateElement.count() > 0) {
        // Extract date range
        String dateRange = null;
        Locator dateRangeElement = dateElement.locator("span[data-test-position-entity-date-range]");
        if (dateRangeElement.count() > 0) {
          dateRange = dateRangeElement.textContent();
        }

        // Extract duration
        String duration = null;
        Locator durationElement = dateElement.locator("span[data-test-position-entity-duration]");
        if (durationElement.count() > 0) {
          duration = durationElement.textContent();
        }

        // Clean the extracted values
        if (dateRange != null) {
          dateRange = cleanHtmlEntities(dateRange.trim());
        }
        if (duration != null) {
          duration = cleanHtmlEntities(duration.trim());
        }

        return new String[]{dateRange, duration};
      }

      return new String[]{null, null};

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting date info: " + e.getMessage());
      }
      return new String[]{null, null};
    }
  }

  /**
   * Extract location from experience element
   *
   * @param positionItem the DOM element containing the experience data
   * @return location string or null if not found
   */
  private String extractExperienceLocation(Locator positionItem) {
    try {
      String locationSelector = "dd[data-test-position-entity-location]";
      Locator locationElement = positionItem.locator(locationSelector);

      if (locationElement.count() > 0) {
        String location = locationElement.textContent();
        if (location != null && !location.trim().isEmpty()) {
          return cleanHtmlEntities(location.trim());
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting location: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract summary/description from experience element
   *
   * @param positionItem the DOM element containing the experience data
   * @return summary string or null if not found
   */
  private String extractExperienceSummary(Locator positionItem) {
    try {
      String summarySelector = "dd[data-test-position-entity-description]";
      Locator summaryElement = positionItem.locator(summarySelector);

      if (summaryElement.count() > 0) {
        // Get all text content including from nested elements
        String summary = summaryElement.textContent();

        if (summary != null && !summary.trim().isEmpty()) {
          // Clean the summary text
          return cleanExperienceSummary(summary.trim());
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting summary: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract company URL from experience element
   * Handles both grouped and non-grouped position entities
   *
   * @param positionItem the DOM element containing the experience data
   * @return company URL or null if not found
   */
  private String extractExperienceCompanyUrl(Locator positionItem) {
    try {
      // First, check if this is a grouped position by looking for parent grouped container
      // Try to find grouped position company link in parent containers
      try {
        // Look for grouped position company link in the position item itself or its ancestors
        String groupedCompanyLinkSelector = "a[data-test-grouped-position-entity-company-link]";
        Locator groupedCompanyLink = positionItem.locator(groupedCompanyLinkSelector);
        
        if (groupedCompanyLink.count() > 0) {
          String url = groupedCompanyLink.first().getAttribute("href");
          if (url != null && !url.trim().isEmpty()) {
            return cleanCompanyUrl(url.trim());
          }
        }

        // If not found, try grouped position company image link
        String groupedCompanyImageLinkSelector = "a[data-test-grouped-position-entity-company-image-link]";
        Locator groupedCompanyImageLink = positionItem.locator(groupedCompanyImageLinkSelector);
        
        if (groupedCompanyImageLink.count() > 0) {
          String url = groupedCompanyImageLink.first().getAttribute("href");
          if (url != null && !url.trim().isEmpty()) {
            return cleanCompanyUrl(url.trim());
          }
        }
      } catch (Exception e) {
        // Continue to try other methods if grouped position lookup fails
        if (config.isDebugMode()) {
          System.out.println("Debug: Error checking grouped position company link: " + e.getMessage());
        }
      }

      // Try to find non-grouped position company link
      // Check company name element for a link
      Locator companyElement = positionItem.locator("dd[data-test-position-entity-company-name]");
      if (companyElement.count() > 0) {
        Locator companyLink = companyElement.locator("a");
        if (companyLink.count() > 0) {
          String url = companyLink.first().getAttribute("href");
          if (url != null && !url.trim().isEmpty() && url.contains("/company/")) {
            return cleanCompanyUrl(url.trim());
          }
        }
      }

      // Also check for grouped position company link at page level (for grouped positions)
      // This handles cases where the company link is in a parent container
      try {
        // Look for the closest grouped position container
        Locator groupedContainer = positionItem.locator("xpath=ancestor::div[@data-test-group-position-list-container]");
        if (groupedContainer.count() > 0) {
          Locator companyLink = groupedContainer.locator("a[data-test-grouped-position-entity-company-link]");
          if (companyLink.count() == 0) {
            companyLink = groupedContainer.locator("a[data-test-grouped-position-entity-company-image-link]");
          }
          if (companyLink.count() > 0) {
            String url = companyLink.first().getAttribute("href");
            if (url != null && !url.trim().isEmpty()) {
              return cleanCompanyUrl(url.trim());
            }
          }
        }
      } catch (Exception e) {
        // XPath might not be supported, continue without it
        if (config.isDebugMode()) {
          System.out.println("Debug: Error checking ancestor grouped container: " + e.getMessage());
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting company URL: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Clean company URL by removing query parameters and normalizing
   *
   * @param url the raw company URL
   * @return cleaned company URL
   */
  private String cleanCompanyUrl(String url) {
    if (url == null || url.trim().isEmpty()) {
      return url;
    }

    try {
      String cleanUrl = url.trim();
      
      // Remove query parameters
      int queryIndex = cleanUrl.indexOf('?');
      if (queryIndex != -1) {
        cleanUrl = cleanUrl.substring(0, queryIndex);
      }
      
      // Remove hash fragments
      int hashIndex = cleanUrl.indexOf('#');
      if (hashIndex != -1) {
        cleanUrl = cleanUrl.substring(0, hashIndex);
      }
      
      return cleanUrl;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning company URL, returning original: " + e.getMessage());
      }
      return url;
    }
  }

  /**
   * Extract skills from experience element
   *
   * @param positionItem the DOM element containing the experience data
   * @return list of skills or null if not found
   */
  private List<String> extractExperienceSkills(Locator positionItem) {
    try {
      String skillsContainerSelector = ".background-entity__skills-container";
      Locator skillsContainer = positionItem.locator(skillsContainerSelector);

      if (skillsContainer.count() > 0) {
        String skillItemSelector = "span[data-test-position-skill-item]";
        Locator skillElements = skillsContainer.locator(skillItemSelector);

        int skillCount = skillElements.count();
        if (skillCount > 0) {
          List<String> skills = new ArrayList<>();

          for (int i = 0; i < skillCount; i++) {
            String skill = skillElements.nth(i).textContent();
            if (skill != null && !skill.trim().isEmpty()) {
              skills.add(cleanHtmlEntities(skill.trim()));
            }
          }

          return skills.isEmpty() ? null : skills;
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting skills: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Clean experience summary text by removing unwanted elements and normalizing whitespace
   *
   * @param summaryText the raw summary text
   * @return cleaned summary text or null if too short after cleaning
   */
  private String cleanExperienceSummary(String summaryText) {
    if (summaryText == null || summaryText.trim().isEmpty()) {
      return summaryText;
    }

    try {
      String cleanSummary = summaryText.trim();

      // Remove excessive whitespace and normalize line breaks
      cleanSummary = cleanSummary
          .replaceAll("\\s+", " ") // Replace multiple spaces with single space
          .replaceAll("\\n\\s*\\n", "\n") // Remove empty lines
          .trim();

      // Clean HTML entities
      cleanSummary = cleanHtmlEntities(cleanSummary);

      // Return null if the summary becomes too short after cleaning
      if (cleanSummary.length() < 3) {
        return null;
      }

      return cleanSummary;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning experience summary, returning original: " + e.getMessage());
      }
      return summaryText;
    }
  }

  /**
   * Clean HTML entities from text content
   *
   * @param text the text containing potential HTML entities
   * @return cleaned text with HTML entities decoded
   */
  private String cleanHtmlEntities(String text) {
    if (text == null || text.trim().isEmpty()) {
      return text;
    }

    try {
      return text.trim()
          .replace("&amp;", "&")
          .replace("&lt;", "<")
          .replace("&gt;", ">")
          .replace("&quot;", "\"")
          .replace("&#39;", "'")
          .replace("&nbsp;", " ");

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning HTML entities, returning original: " + e.getMessage());
      }
      return text;
    }
  }
}