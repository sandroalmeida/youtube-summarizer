package io.professionalhub.scraper.service.profile;

import io.professionalhub.scraper.config.ConfigManager;
import io.professionalhub.scraper.model.CompanyUrl;
import io.professionalhub.scraper.model.Education;
import io.professionalhub.scraper.model.Experience;
import io.professionalhub.scraper.model.ProfileData;
import io.professionalhub.scraper.model.Skill;
import io.professionalhub.scraper.repository.CompanyUrlRepository;
import io.professionalhub.scraper.utils.JsonUtils;
import io.professionalhub.scraper.utils.TextSanitizer;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Service class responsible for extracting profile data from LinkedIn pages
 * and saving it as JSON files
 */
public class ProfileExtractionService {
  private static final ConfigManager config = ConfigManager.getInstance();
  private static final String JSON_FILES_DIR = "../resources/json-profile-files";

  private final ExperienceExtractionService experienceExtractionService;
  private final EducationExtractionService educationExtractionService;
  private final SkillExtractionService skillExtractionService;
  private final CompanyUrlRepository companyUrlRepository;

  public ProfileExtractionService() {
    this.educationExtractionService = new EducationExtractionService();
    this.experienceExtractionService = new ExperienceExtractionService();
    this.skillExtractionService = new SkillExtractionService();
    this.companyUrlRepository = null; // Will be set via setter if repository is needed
  }

  public ProfileExtractionService(CompanyUrlRepository companyUrlRepository) {
    this.educationExtractionService = new EducationExtractionService();
    this.experienceExtractionService = new ExperienceExtractionService();
    this.skillExtractionService = new SkillExtractionService();
    this.companyUrlRepository = companyUrlRepository;
  }

  /**
   * Extract profile data from the current page and save it as JSON
   *
   * @param page the page containing the profile to extract
   */
  public void extractAndSaveProfile(Page page) {
    System.out.println("Extracting and saving profile data as JSON...");

    try {
      // Extract profile data
      ProfileData profileData = extractProfileData(page);

      // Sanitize all text fields before saving
      TextSanitizer.sanitizeProfileData(profileData);

      // Generate filename from profile URL
      String fileName = JsonUtils.convertUrlToFileName(profileData.getProfileUrl());
      String jsonFilePath = Paths.get(JSON_FILES_DIR, fileName).toString();

      // Save profile data as JSON
      JsonUtils.writeObjectToJson(jsonFilePath, profileData);

      System.out.println("✓ Profile data saved successfully:");

    } catch (Exception e) {
      System.err.println("❌ Error saving profile data as JSON: " + e.getMessage());
      throw new RuntimeException("Failed to extract and save profile data", e);
    }
  }

  /**
   * Extract profile data from the current page
   *
   * @param page the page containing the profile
   * @return ProfileData object with extracted information
   */
  private ProfileData extractProfileData(Page page) {
    try {
      // Get current URL and clean it
      String rawUrl = page.url();
      String profileUrl = cleanProfileUrl(rawUrl);

      // Extract all fields
      String fullName = extractFullName(page);
      String imageUrl = extractImageUrl(page);
      String profilePublicUrl = extractProfilePublicUrl(page);
      String title = extractTitle(page);
      String summary = extractSummary(page);
      String latestEducation = extractLatestEducation(page);
      String location = extractLocation(page);
      String currentIndustry = extractCurrentIndustry(page);
      List<Experience> experiences = experienceExtractionService.extractExperiences(page);
      List<Education> education = educationExtractionService.extractEducation(page);
      List<Skill> skills = skillExtractionService.extractSkills(page);

      // Save company URLs to database if repository is available
      if (companyUrlRepository != null && experiences != null) {
        saveCompanyUrls(experiences);
      }

      // Create and populate ProfileData object
      ProfileData profileData = new ProfileData(fullName, imageUrl, profileUrl, profilePublicUrl,
          title, summary, latestEducation, location, currentIndustry, experiences, education, skills);

      System.out.println("✓ Profile data extracted successfully:");

      return profileData;

    } catch (Exception e) {
      System.err.println("❌ Error extracting profile data: " + e.getMessage());
      throw new RuntimeException("Failed to extract profile data from page", e);
    }
  }

  /**
   * Extract the summary from the profile page
   */
  private String extractSummary(Page page) {
    try {
      String[] summarySelectors = {
          "section[data-test-profile-summary-card] span.lt-line-clamp__raw-line",
          "section[data-live-test-profile-summary-card] span.lt-line-clamp__raw-line",
          "section[data-test-profile-summary-card] blockquote[data-test-summary-card-text]",
          "section[data-live-test-profile-summary-card] blockquote[data-live-test-summary-card-text]",
          ".summary-card span.lt-line-clamp__raw-line",
          ".summary-card blockquote",
          "section[data-test-profile-summary-card] .lt-line-clamp__raw-line",
          "section[data-test-profile-summary-card] [data-test-decorated-line-clamp] span",
          ".pv-about-section .pv-about__summary-text",
          ".about-section .about-section__text"
      };

      for (String selector : summarySelectors) {
        try {
          Locator summaryElement = page.locator(selector);

          if (summaryElement.count() > 0) {
            if (selector.contains("lt-line-clamp__raw-line")) {
              Locator allSummarySpans = page.locator(
                  "section[data-test-profile-summary-card] span.lt-line-clamp__raw-line, section[data-live-test-profile-summary-card] span.lt-line-clamp__raw-line");

              if (allSummarySpans.count() > 0) {
                StringBuilder fullSummary = new StringBuilder();

                for (int i = 0; i < allSummarySpans.count(); i++) {
                  String spanText = allSummarySpans.nth(i).textContent();
                  if (spanText != null && !spanText.trim().isEmpty()) {
                    if (fullSummary.length() > 0) {
                      fullSummary.append(" ");
                    }
                    fullSummary.append(spanText.trim());
                  }
                }

                String summary = fullSummary.toString().trim();
                if (!summary.isEmpty()) {
                  return cleanSummaryText(summary);
                }
              }
            } else {
              String summary = summaryElement.first().textContent();

              if (summary != null && !summary.trim().isEmpty()) {
                String cleanSummary = cleanSummaryText(summary.trim());

                if (cleanSummary.length() > 10 && !cleanSummary.equalsIgnoreCase("summary")) {
                  return cleanSummary;
                }
              }
            }
          }

        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Summary selector '" + selector + "' failed: " + e.getMessage());
          }
        }
      }
      return null;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting summary: " + e.getMessage());
      return null;
    }
  }

  /**
   * Clean summary text by removing unwanted elements and normalizing whitespace
   */
  private String cleanSummaryText(String summaryText) {
    if (summaryText == null || summaryText.trim().isEmpty()) {
      return summaryText;
    }

    try {
      String cleanSummary = summaryText.trim();

      cleanSummary = cleanSummary
          .replaceAll("(?i)^summary\\s*", "")
          .replaceAll("\\s*see\\s+more\\s*$", "")
          .replaceAll("\\s*see\\s+less\\s*$", "")
          .replaceAll("\\s*\\.\\.\\.\\s*$", "")
          .replaceAll("\\s+", " ")
          .trim();

      cleanSummary = cleanHtmlEntities(cleanSummary);

      if (cleanSummary.length() < 3) {
        return null;
      }

      return cleanSummary;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning summary text, returning original: " + e.getMessage());
      }
      return summaryText;
    }
  }

  /**
   * Extract the full name from the profile page
   */
  private String extractFullName(Page page) {
    try {
      String[] nameSelectors = {
          "span[data-test-row-lockup-full-name] .artdeco-entity-lockup__title",
          "span[data-live-test-row-lockup-full-name] .artdeco-entity-lockup__title",
          "span[data-test-row-lockup-full-name] div",
          ".artdeco-entity-lockup__title",
          "h1[data-test-view-name-page-title]",
          ".pv-text-details__left-panel h1"
      };

      for (String selector : nameSelectors) {
        try {
          Locator nameElement = page.locator(selector);

          if (nameElement.count() > 0) {
            String name = nameElement.first().textContent();

            if (name != null && !name.trim().isEmpty()) {
              return name.trim();
            }
          }

        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Selector '" + selector + "' failed: " + e.getMessage());
          }
        }
      }
      return null;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting full name: " + e.getMessage());
      return null;
    }
  }

  /**
   * Extract the profile image URL from the profile page
   */
  private String extractImageUrl(Page page) {
    try {
      String[] imageSelectors = {
          "span[data-test-row-lockup-figure] img.lockup__image",
          ".artdeco-entity-lockup__image img",
          ".lockup__image-container img",
          ".pv-top-card-profile-picture img",
          ".presence-entity__image img"
      };

      for (String selector : imageSelectors) {
        try {
          Locator imageElement = page.locator(selector);

          if (imageElement.count() > 0) {
            String imageUrl = imageElement.first().getAttribute("src");

            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
              return cleanLinkedInImageUrl(imageUrl);
            }
          }

        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Image selector '" + selector + "' failed: " + e.getMessage());
          }
        }
      }
      return null;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting image URL: " + e.getMessage());
      return null;
    }
  }

  /**
   * Extract the public profile URL from the personal info section
   */
  private String extractProfilePublicUrl(Page page) {
    try {
      String selector = "a[data-test-personal-info-profile-link]";
      try {
        Locator linkElement = page.locator(selector);

        if (linkElement.count() > 0) {
          String publicUrl = linkElement.first().getAttribute("href");

          if (publicUrl != null && !publicUrl.trim().isEmpty()) {
            return publicUrl.trim();
          }
        }
      } catch (Exception e) {
        if (config.isDebugMode()) {
          System.out.println("Debug: Profile public URL selector '" + selector + "' failed: " + e.getMessage());
        }
      }
      return null;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting profile public URL: " + e.getMessage());
      return null;
    }
  }

  /**
   * Extract the title/headline from the profile page
   */
  private String extractTitle(Page page) {
    try {
      String[] titleSelectors = {
          "span[data-test-row-lockup-headline]",
          "span[data-live-test-row-lockup-headline]",
          ".artdeco-entity-lockup__subtitle span",
          ".pv-text-details__left-panel .text-body-medium",
          ".pv-top-card--list-bullet .pv-entity__summary-info h2"
      };

      for (String selector : titleSelectors) {
        try {
          Locator titleElement = page.locator(selector);

          if (titleElement.count() > 0) {
            String title = titleElement.first().textContent();

            if (title != null && !title.trim().isEmpty()) {
              return cleanHtmlEntities(title.trim());
            }
          }

        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Title selector '" + selector + "' failed: " + e.getMessage());
          }
        }
      }
      return null;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting title: " + e.getMessage());
      return null;
    }
  }

  /**
   * Extract the latest education from the profile page
   */
  private String extractLatestEducation(Page page) {
    try {
      String[] educationSelectors = {
          "span[data-test-latest-education]",
          "span[data-live-test-latest-education]",
          ".artdeco-entity-lockup__caption span",
          ".pv-entity__secondary-title",
          ".education .pv-entity__school-name"
      };

      for (String selector : educationSelectors) {
        try {
          Locator educationElement = page.locator(selector);

          if (educationElement.count() > 0) {
            String education = educationElement.first().textContent();

            if (education != null && !education.trim().isEmpty()) {
              return education.trim();
            }
          }

        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Education selector '" + selector + "' failed: " + e.getMessage());
          }
        }
      }
      return null;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting latest education: " + e.getMessage());
      return null;
    }
  }

  /**
   * Extract the location from the profile page
   */
  private String extractLocation(Page page) {
    try {
      String[] locationSelectors = {
          "div[data-test-row-lockup-location]",
          "div[data-live-test-row-lockup-location]",
          ".artdeco-entity-lockup__metadata div",
          ".pv-top-card--list-bullet .pv-top-card--list-bullet__first-item",
          ".pv-top-card__location"
      };

      for (String selector : locationSelectors) {
        try {
          Locator locationElement = page.locator(selector);

          if (locationElement.count() > 0) {
            String location = locationElement.first().textContent();

            if (location != null && !location.trim().isEmpty()) {
              String cleanLocation = cleanLocationText(location.trim());
              if (cleanLocation != null && !cleanLocation.isEmpty()) {
                return cleanLocation;
              }
            }
          }

        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Location selector '" + selector + "' failed: " + e.getMessage());
          }
        }
      }
      return null;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting location: " + e.getMessage());
      return null;
    }
  }

  /**
   * Extract the current industry from the profile page
   */
  private String extractCurrentIndustry(Page page) {
    try {
      String[] industrySelectors = {
          "span[data-test-current-employer-industry]",
          "span[data-live-test-current-employer-industry]",
          ".artdeco-entity-lockup__metadata span",
          ".pv-top-card--list-bullet .industry",
          ".pv-entity__summary-info .pv-entity__industry"
      };

      for (String selector : industrySelectors) {
        try {
          Locator industryElement = page.locator(selector);

          if (industryElement.count() > 0) {
            String industry = industryElement.first().textContent();

            if (industry != null && !industry.trim().isEmpty()) {
              String cleanIndustry = cleanLocationText(industry.trim());
              if (cleanIndustry != null && !cleanIndustry.isEmpty()) {
                return cleanIndustry;
              }
            }
          }

        } catch (Exception e) {
          if (config.isDebugMode()) {
            System.out.println("Debug: Industry selector '" + selector + "' failed: " + e.getMessage());
          }
        }
      }
      return null;

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error extracting current industry: " + e.getMessage());
      return null;
    }
  }

  /**
   * Clean LinkedIn image URLs by removing size and cache parameters
   */
  private String cleanLinkedInImageUrl(String imageUrl) {
    if (imageUrl == null || imageUrl.trim().isEmpty()) {
      return imageUrl;
    }

    try {
      String cleanUrl = imageUrl.trim();

      int queryIndex = cleanUrl.indexOf('?');
      if (queryIndex != -1) {
        cleanUrl = cleanUrl.substring(0, queryIndex);
      }
      return cleanUrl;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning image URL, returning original: " + e.getMessage());
      }
      return imageUrl;
    }
  }

  /**
   * Clean HTML entities from text content
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

  /**
   * Clean LinkedIn profile URL by removing unnecessary parameters
   */
  private String cleanProfileUrl(String rawUrl) {
    if (rawUrl == null || rawUrl.trim().isEmpty()) {
      return rawUrl;
    }

    try {
      String cleanUrl = rawUrl.trim();

      String[] urlParts = cleanUrl.split("\\?", 2);
      if (urlParts.length < 2) {
        return cleanUrl;
      }

      String baseUrl = urlParts[0];
      String queryString = urlParts[1];

      String[] keepParams = {
          "highlightedPatternSource",
          "project",
          "searchContextId",
          "searchHistoryId"
      };

      String[] removeParams = {
          "searchRequestId",
          "start",
          "trk"
      };

      StringBuilder cleanQuery = new StringBuilder();
      String[] params = queryString.split("&");

      for (String param : params) {
        String[] keyValue = param.split("=", 2);
        if (keyValue.length == 2) {
          String key = keyValue[0];
          String value = keyValue[1];

          boolean shouldKeep = false;
          for (String keepParam : keepParams) {
            if (key.equals(keepParam)) {
              shouldKeep = true;
              break;
            }
          }

          boolean shouldRemove = false;
          for (String removeParam : removeParams) {
            if (key.equals(removeParam)) {
              shouldRemove = true;
              break;
            }
          }

          if (shouldKeep && !shouldRemove) {
            if (cleanQuery.length() > 0) {
              cleanQuery.append("&");
            }
            cleanQuery.append(key).append("=").append(value);
          }
        }
      }

      String finalUrl = baseUrl;
      if (cleanQuery.length() > 0) {
        finalUrl += "?" + cleanQuery;
      }

      return finalUrl;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning URL, returning original: " + e.getMessage());
      }
      return rawUrl;
    }
  }

  /**
   * Save company URLs from experiences to the database
   * Only saves URLs that don't already exist in the database
   *
   * @param experiences list of experiences containing company URLs
   */
  private void saveCompanyUrls(List<Experience> experiences) {
    if (experiences == null || experiences.isEmpty()) {
      return;
    }

    try {
      int savedCount = 0;
      int skippedCount = 0;

      for (Experience experience : experiences) {
        String companyUrl = experience.getCompanyUrl();

        if (companyUrl != null && !companyUrl.trim().isEmpty()) {
          String cleanUrl = companyUrl.trim();

          // Check if URL already exists
          Optional<CompanyUrl> existingUrl = companyUrlRepository.findByUrl(cleanUrl);

          if (!existingUrl.isPresent()) {
            // Create new CompanyUrl entity with default status "0"
            CompanyUrl newCompanyUrl = new CompanyUrl(cleanUrl);
            companyUrlRepository.save(newCompanyUrl);
            savedCount++;
          } else {
            skippedCount++;
          }
        }
      }

      if (savedCount > 0 || skippedCount > 0) {
        System.out.println("  - Company URLs: " + savedCount + " saved, " + skippedCount + " already exist");
      }

    } catch (Exception e) {
      System.out.println("⚠ Warning: Error saving company URLs to database: " + e.getMessage());
      if (config.isDebugMode()) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Clean location and industry text by removing leading dots and extra
   * whitespace
   */
  private String cleanLocationText(String text) {
    if (text == null || text.trim().isEmpty()) {
      return text;
    }

    try {
      String cleanText = text.trim()
          .replaceAll("^[·•]\\s*", "")
          .replaceAll("\\s+", " ")
          .trim();

      if (cleanText.isEmpty()) {
        return null;
      }

      return cleanText;

    } catch (Exception e) {
      if (config.isDebugMode()) {
        System.out.println("Debug: Error cleaning location/industry text, returning original: " + e.getMessage());
      }
      return text;
    }
  }
}