package io.professionalhub.scraper.service.profile;

import io.professionalhub.scraper.config.ConfigManager;
import io.professionalhub.scraper.model.Skill;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Service class responsible for extracting skills data from LinkedIn profiles
 * This class handles all aspects of skills extraction including skill names,
 * endorsement counts, and associated positions
 */
public class SkillExtractionService {
  private static final ConfigManager config = ConfigManager.getInstance();

  /**
   * Extract all skills from the profile page
   *
   * @param page the page containing the profile
   * @return List of Skill objects, or null if not found
   */
  public List<Skill> extractSkills(Page page) {
    try {
      // Look for the skills section using multiple selectors
      String[] skillsSectionSelectors = {
          "div[data-test-expandable-stepper][data-live-test-profile-skills-card]",
          ".skills-card-expandable",
          "div[data-live-test-profile-skills-card]",
          "section[data-test-profile-skills-card]"
      };

      Locator skillsSection = null;

      // Try each selector until we find the skills section
      for (String selector : skillsSectionSelectors) {
        skillsSection = page.locator(selector).first();
        if (skillsSection.count() > 0) {
          break;
        }
      }

      if (skillsSection.count() == 0) {
        return null;
      }

      // Look for all skill items within the section
      String skillItemsSelector = "li.skill-entity__wrapper";
      Locator skillItems = skillsSection.locator(skillItemsSelector);

      int itemCount = skillItems.count();
      if (itemCount == 0) {
        return null;
      }

      List<Skill> skillsList = new ArrayList<>();

      for (int i = 0; i < itemCount; i++) {
        try {
          Locator skillItem = skillItems.nth(i);
          Skill skill = extractSingleSkill(skillItem, i + 1);

          if (skill != null) {
            skillsList.add(skill);
          }

        } catch (Exception e) {
          System.out.println("⚠ Warning: Error extracting skill " + (i + 1) + ": " + e.getMessage());
          if (config.isDebugMode()) {
            e.printStackTrace();
          }
        }
      }
      return skillsList.isEmpty() ? null : skillsList;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting skills: " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
      return null;
    }
  }

  /**
   * Extract a single skill from a skill item element
   *
   * @param skillItem the DOM element containing the skill data
   * @param index     the skill index for logging purposes
   * @return Skill object or null if extraction fails
   */
  private Skill extractSingleSkill(Locator skillItem, int index) {
    try {
      // Extract skill name
      String skillName = extractSkillName(skillItem);

      // Extract endorsement count
      Integer endorsementCount = extractEndorsementCount(skillItem);

      // Extract associated positions
      String associatedPositions = extractAssociatedPositions(skillItem);

      // Create Skill object

      return new Skill(skillName, endorsementCount, associatedPositions);

    } catch (Exception e) {
      System.out.println("    ❌ Error extracting skill " + index + ": " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
      return null;
    }
  }

  /**
   * Extract skill name from skill element
   *
   * @param skillItem the DOM element containing the skill data
   * @return skill name or null if not found
   */
  private String extractSkillName(Locator skillItem) {
    try {
      String[] skillNameSelectors = {
          "dt[data-test-skill-entity-skill-name]",
          "dt[data-live-test-skill-entity-skill-name]",
          ".skill-entity__skill-name"
      };

      for (String selector : skillNameSelectors) {
        Locator skillNameElement = skillItem.locator(selector);
        if (skillNameElement.count() > 0) {
          String skillName = skillNameElement.first().textContent();
          if (skillName != null && !skillName.trim().isEmpty()) {
            return cleanHtmlEntities(skillName.trim());
          }
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting skill name: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract endorsement count from skill element
   *
   * @param skillItem the DOM element containing the skill data
   * @return endorsement count or null if not found
   */
  private Integer extractEndorsementCount(Locator skillItem) {
    try {
      // Look for endorsement information in skill insights
      String endorsementSelector = "article[data-test-skill-entity-skill-endorsements] " +
          ".skill-insight__title-content";

      Locator endorsementElement = skillItem.locator(endorsementSelector);

      if (endorsementElement.count() > 0) {
        String endorsementText = endorsementElement.first().textContent();
        if (endorsementText != null && !endorsementText.trim().isEmpty()) {
          return parseEndorsementCount(endorsementText.trim());
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting endorsement count: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract associated positions from skill element
   *
   * @param skillItem the DOM element containing the skill data
   * @return concatenated associated positions or null if not found
   */
  private String extractAssociatedPositions(Locator skillItem) {
    try {
      // Look for skill associations (positions where this skill was used)
      String associationSelector = "article[data-test-skill-association-entity] " +
          ".skill-insight__title-content";

      Locator associationElements = skillItem.locator(associationSelector);

      if (associationElements.count() > 0) {
        List<String> positions = new ArrayList<>();

        for (int i = 0; i < associationElements.count(); i++) {
          String positionText = associationElements.nth(i).textContent();
          if (positionText != null && !positionText.trim().isEmpty()) {
            String cleanPosition = cleanHtmlEntities(positionText.trim());
            if (!cleanPosition.isEmpty()) {
              positions.add(cleanPosition);
            }
          }
        }

        if (!positions.isEmpty()) {
          return String.join("; ", positions);
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting associated positions: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Parse endorsement count from text like "25 endorsements" or "1 endorsement"
   *
   * @param endorsementText the text containing endorsement information
   * @return parsed endorsement count or null if parsing fails
   */
  private Integer parseEndorsementCount(String endorsementText) {
    try {
      if (endorsementText == null || endorsementText.trim().isEmpty()) {
        return null;
      }

      // Remove common words and extract number
      String cleanText = endorsementText.toLowerCase()
          .replace("endorsements", "")
          .replace("endorsement", "")
          .trim();

      if (cleanText.isEmpty()) {
        return null;
      }

      // Try to parse the number
      return Integer.parseInt(cleanText);

    } catch (NumberFormatException e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Could not parse endorsement count from: " + endorsementText);
      }
      return null;
    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error parsing endorsement count: " + e.getMessage());
      }
      return null;
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
      String cleanText = text.trim()
          .replace("&amp;", "&")
          .replace("&lt;", "<")
          .replace("&gt;", ">")
          .replace("&quot;", "\"")
          .replace("&#39;", "'")
          .replace("&nbsp;", " ");

      if (config.isDebugMode() && !text.equals(cleanText)) {
        System.out.println("Debug: Cleaned HTML entities from '" + text + "' to '" + cleanText + "'");
      }

      return cleanText;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning HTML entities, returning original: " + e.getMessage());
      }
      return text;
    }
  }
}