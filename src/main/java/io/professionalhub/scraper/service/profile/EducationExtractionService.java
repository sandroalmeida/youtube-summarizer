package io.professionalhub.scraper.service.profile;

import io.professionalhub.scraper.config.ConfigManager;
import io.professionalhub.scraper.model.Education;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Service class responsible for extracting education data from LinkedIn profiles
 * This class handles all aspects of education extraction including school details,
 * degree information, fields of study, dates, and descriptions
 */
public class EducationExtractionService {
  private static final ConfigManager config = ConfigManager.getInstance();

  /**
   * Extract all education entries from the profile page
   *
   * @param page the page containing the profile
   * @return List of Education objects, or null if not found
   */
  public List<Education> extractEducation(Page page) {
    try {
      // Look for the education section using multiple selectors
      String[] educationSectionSelectors = {
          "div[data-test-education-card][data-live-test-education-card]",
          ".education-card",
          "section[data-test-education-card]",
          ".expandable-list[data-test-education-card]"
      };

      Locator educationSection = null;

      // Try each selector until we find the education section
      for (String selector : educationSectionSelectors) {
        educationSection = page.locator(selector).first();
        if (educationSection.count() > 0) {
          break;
        }
      }

      if (educationSection.count() == 0) {
        return null;
      }

      // Look for all education items within the section
      String educationItemsSelector = "li[data-test-education-item][data-live-test-education-item]";
      Locator educationItems = educationSection.locator(educationItemsSelector);

      int itemCount = educationItems.count();
      if (itemCount == 0) {
        return null;
      }

      List<Education> educationList = new ArrayList<>();

      for (int i = 0; i < itemCount; i++) {
        try {
          Locator educationItem = educationItems.nth(i);
          Education education = extractSingleEducation(educationItem, i + 1);

          if (education != null) {
            educationList.add(education);
          }

        } catch (Exception e) {
          System.out.println("⚠ Warning: Error extracting education " + (i + 1) + ": " + e.getMessage());
          if (config.isDebugMode()) {
            e.printStackTrace();
          }
        }
      }
      return educationList.isEmpty() ? null : educationList;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting education: " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
      return null;
    }
  }

  /**
   * Extract a single education entry from an education item element
   *
   * @param educationItem the DOM element containing the education data
   * @param index         the education index for logging purposes
   * @return Education object or null if extraction fails
   */
  private Education extractSingleEducation(Locator educationItem, int index) {
    try {
      // Extract school name
      String schoolName = extractSchoolName(educationItem);

      // Extract school logo URL
      String schoolLogoUrl = extractSchoolLogoUrl(educationItem);

      // Extract school link
      String schoolLink = extractSchoolLink(educationItem);

      // Extract degree name
      String degreeName = extractDegreeName(educationItem);

      // Extract field of study
      String fieldOfStudy = extractFieldOfStudy(educationItem);

      // Extract dates attended
      String datesAttended = extractDatesAttended(educationItem);

      // Extract description
      String description = extractEducationDescription(educationItem);

      // Create Education object

      return new Education(schoolName, schoolLogoUrl, degreeName,
          fieldOfStudy, datesAttended, description, schoolLink);

    } catch (Exception e) {
      System.out.println("    ❌ Error extracting education " + index + ": " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
      return null;
    }
  }

  /**
   * Extract school name from education element
   *
   * @param educationItem the DOM element containing the education data
   * @return school name or null if not found
   */
  private String extractSchoolName(Locator educationItem) {
    try {
      // Target the school name element based on the HTML structure
      String[] schoolNameSelectors = {
          "h3[data-test-education-entity-school-name] a[data-test-education-entity-school-link]",
          "h3[data-test-education-entity-school-name]",
          ".background-entity__summary-definition--title a",
          ".background-entity__summary-definition--title"
      };

      for (String selector : schoolNameSelectors) {
        Locator schoolElement = educationItem.locator(selector);
        if (schoolElement.count() > 0) {
          String schoolName = schoolElement.first().textContent();
          if (schoolName != null && !schoolName.trim().isEmpty()) {
            return cleanHtmlEntities(schoolName.trim());
          }
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting school name: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract school logo URL from education element
   *
   * @param educationItem the DOM element containing the education data
   * @return school logo URL or null if not found
   */
  private String extractSchoolLogoUrl(Locator educationItem) {
    try {
      String[] logoSelectors = {
          "figure[data-test-education-entity-figure] img.logo-container__img",
          ".logo-container img",
          "figure img"
      };

      for (String selector : logoSelectors) {
        Locator logoElement = educationItem.locator(selector);
        if (logoElement.count() > 0) {
          String logoUrl = logoElement.first().getAttribute("src");
          if (logoUrl != null && !logoUrl.trim().isEmpty() && !logoUrl.contains("data:image/gif")) {
            // Clean the logo URL (remove LinkedIn's image parameters)
            return cleanLinkedInImageUrl(logoUrl);
          }
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting school logo: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract school link from education element
   *
   * @param educationItem the DOM element containing the education data
   * @return school link URL or null if not found
   */
  private String extractSchoolLink(Locator educationItem) {
    try {
      String[] linkSelectors = {
          "a[data-test-education-entity-school-link]",
          "a[data-test-education-entity-school-image-link]",
          ".background-entity__school-link"
      };

      for (String selector : linkSelectors) {
        Locator linkElement = educationItem.locator(selector);
        if (linkElement.count() > 0) {
          String href = linkElement.first().getAttribute("href");
          if (href != null && !href.trim().isEmpty()) {
            return href.trim();
          }
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting school link: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract degree name from education element
   *
   * @param educationItem the DOM element containing the education data
   * @return degree name or null if not found
   */
  private String extractDegreeName(Locator educationItem) {
    try {
      String degreeSelector = "span[data-test-education-entity-degree-name]";
      Locator degreeElement = educationItem.locator(degreeSelector);

      if (degreeElement.count() > 0) {
        String degreeName = degreeElement.first().textContent();
        if (degreeName != null && !degreeName.trim().isEmpty()) {
          return cleanHtmlEntities(degreeName.trim());
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting degree name: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract field of study from education element
   *
   * @param educationItem the DOM element containing the education data
   * @return field of study or null if not found
   */
  private String extractFieldOfStudy(Locator educationItem) {
    try {
      String fieldSelector = "span[data-test-education-entity-field-of-study]";
      Locator fieldElement = educationItem.locator(fieldSelector);

      if (fieldElement.count() > 0) {
        String fieldOfStudy = fieldElement.first().textContent();
        if (fieldOfStudy != null && !fieldOfStudy.trim().isEmpty()) {
          return cleanHtmlEntities(fieldOfStudy.trim());
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting field of study: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract dates attended from education element
   *
   * @param educationItem the DOM element containing the education data
   * @return dates attended or null if not found
   */
  private String extractDatesAttended(Locator educationItem) {
    try {
      String datesSelector = "dd[data-test-education-entity-dates]";
      Locator datesElement = educationItem.locator(datesSelector);

      if (datesElement.count() > 0) {
        String dates = datesElement.first().textContent();
        if (dates != null && !dates.trim().isEmpty()) {
          String cleanDates = cleanHtmlEntities(dates.trim());
          // Return null if the dates field is essentially empty
          if (cleanDates.length() > 2) {
            return cleanDates;
          }
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting dates attended: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Extract description from education element
   *
   * @param educationItem the DOM element containing the education data
   * @return description or null if not found
   */
  private String extractEducationDescription(Locator educationItem) {
    try {
      // Look for description in the background-entity__description-container
      String descriptionSelector = ".background-entity__description-container";
      Locator descriptionElement = educationItem.locator(descriptionSelector);

      if (descriptionElement.count() > 0) {
        String description = descriptionElement.first().textContent();
        if (description != null && !description.trim().isEmpty()) {
          String cleanDescription = cleanEducationDescription(description.trim());
          // Return null if description is too short after cleaning
          if (cleanDescription != null && cleanDescription.length() > 5) {
            return cleanDescription;
          }
        }
      }

      return null;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error extracting education description: " + e.getMessage());
      }
      return null;
    }
  }

  /**
   * Clean education description text by removing unwanted elements and normalizing whitespace
   *
   * @param descriptionText the raw description text
   * @return cleaned description text or null if too short after cleaning
   */
  private String cleanEducationDescription(String descriptionText) {
    if (descriptionText == null || descriptionText.trim().isEmpty()) {
      return descriptionText;
    }

    try {
      String cleanDescription = descriptionText.trim();

      // Remove excessive whitespace and normalize line breaks
      cleanDescription = cleanDescription
          .replaceAll("\\s+", " ") // Replace multiple spaces with single space
          .replaceAll("\\n\\s*\\n", "\n") // Remove empty lines
          .trim();

      // Clean HTML entities
      cleanDescription = cleanHtmlEntities(cleanDescription);

      // Return null if the description becomes too short after cleaning
      if (cleanDescription.length() < 5) {
        return null;
      }

      return cleanDescription;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning education description, returning original: " + e.getMessage());
      }
      return descriptionText;
    }
  }

  /**
   * Clean LinkedIn image URLs by removing size and cache parameters
   *
   * @param imageUrl the raw image URL
   * @return cleaned image URL
   */
  private String cleanLinkedInImageUrl(String imageUrl) {
    if (imageUrl == null || imageUrl.trim().isEmpty()) {
      return imageUrl;
    }

    try {
      String cleanUrl = imageUrl.trim();

      // Remove query parameters (everything after ?)
      int queryIndex = cleanUrl.indexOf('?');
      if (queryIndex != -1) {
        cleanUrl = cleanUrl.substring(0, queryIndex);
      }

      return cleanUrl;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning logo URL, returning original: " + e.getMessage());
      }
      return imageUrl;
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