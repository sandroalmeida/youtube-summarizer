package io.professionalhub.scraper.utils;

import io.professionalhub.scraper.model.Education;
import io.professionalhub.scraper.model.Experience;
import io.professionalhub.scraper.model.ProfileData;
import io.professionalhub.scraper.model.Skill;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for sanitizing text fields extracted from LinkedIn profiles.
 * Handles common data quality issues such as:
 * - Excess whitespace and newlines
 * - "Related to search terms in your query" artifacts
 * - Emoji characters
 */
public class TextSanitizer {

    // Pattern to match "Related to search terms in your query" and everything after it
    private static final Pattern RELATED_SEARCH_PATTERN = Pattern.compile(
        "\\s*Related to search terms in your query.*",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to match emoji characters (Unicode emoji ranges)
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
        "[\\x{1F600}-\\x{1F64F}]|" +  // Emoticons
        "[\\x{1F300}-\\x{1F5FF}]|" +  // Misc Symbols and Pictographs
        "[\\x{1F680}-\\x{1F6FF}]|" +  // Transport and Map
        "[\\x{1F1E0}-\\x{1F1FF}]|" +  // Flags
        "[\\x{2600}-\\x{26FF}]|" +    // Misc symbols
        "[\\x{2700}-\\x{27BF}]|" +    // Dingbats
        "[\\x{FE00}-\\x{FE0F}]|" +    // Variation Selectors
        "[\\x{1F900}-\\x{1F9FF}]|" +  // Supplemental Symbols and Pictographs
        "[\\x{1FA00}-\\x{1FA6F}]|" +  // Chess Symbols
        "[\\x{1FA70}-\\x{1FAFF}]|" +  // Symbols and Pictographs Extended-A
        "[\\x{231A}-\\x{231B}]|" +    // Watch, Hourglass
        "[\\x{23E9}-\\x{23F3}]|" +    // Media control symbols
        "[\\x{23F8}-\\x{23FA}]|" +    // More media controls
        "[\\x{25AA}-\\x{25AB}]|" +    // Squares
        "[\\x{25B6}]|" +              // Play button
        "[\\x{25C0}]|" +              // Reverse button
        "[\\x{25FB}-\\x{25FE}]|" +    // Squares
        "[\\x{2614}-\\x{2615}]|" +    // Umbrella, Hot beverage
        "[\\x{2648}-\\x{2653}]|" +    // Zodiac signs
        "[\\x{267F}]|" +              // Wheelchair
        "[\\x{2693}]|" +              // Anchor
        "[\\x{26A1}]|" +              // High voltage
        "[\\x{26AA}-\\x{26AB}]|" +    // Circles
        "[\\x{26BD}-\\x{26BE}]|" +    // Soccer, Baseball
        "[\\x{26C4}-\\x{26C5}]|" +    // Snowman, Sun
        "[\\x{26CE}]|" +              // Ophiuchus
        "[\\x{26D4}]|" +              // No entry
        "[\\x{26EA}]|" +              // Church
        "[\\x{26F2}-\\x{26F3}]|" +    // Fountain, Golf
        "[\\x{26F5}]|" +              // Sailboat
        "[\\x{26FA}]|" +              // Tent
        "[\\x{26FD}]|" +              // Fuel pump
        "[\\x{2702}]|" +              // Scissors
        "[\\x{2705}]|" +              // Check mark
        "[\\x{2708}-\\x{270D}]|" +    // Airplane to Writing hand
        "[\\x{270F}]|" +              // Pencil
        "[\\x{2712}]|" +              // Black nib
        "[\\x{2714}]|" +              // Check mark
        "[\\x{2716}]|" +              // X mark
        "[\\x{271D}]|" +              // Latin cross
        "[\\x{2721}]|" +              // Star of David
        "[\\x{2728}]|" +              // Sparkles
        "[\\x{2733}-\\x{2734}]|" +    // Eight spoked asterisks
        "[\\x{2744}]|" +              // Snowflake
        "[\\x{2747}]|" +              // Sparkle
        "[\\x{274C}]|" +              // Cross mark
        "[\\x{274E}]|" +              // Cross mark
        "[\\x{2753}-\\x{2755}]|" +    // Question marks
        "[\\x{2757}]|" +              // Exclamation mark
        "[\\x{2763}-\\x{2764}]|" +    // Heart exclamation, Heart
        "[\\x{2795}-\\x{2797}]|" +    // Plus, Minus, Division
        "[\\x{27A1}]|" +              // Right arrow
        "[\\x{27B0}]|" +              // Curly loop
        "[\\x{27BF}]|" +              // Double curly loop
        "[\\x{2934}-\\x{2935}]|" +    // Arrows
        "[\\x{2B05}-\\x{2B07}]|" +    // Arrows
        "[\\x{2B1B}-\\x{2B1C}]|" +    // Squares
        "[\\x{2B50}]|" +              // Star
        "[\\x{2B55}]|" +              // Circle
        "[\\x{3030}]|" +              // Wavy dash
        "[\\x{303D}]|" +              // Part alternation mark
        "[\\x{3297}]|" +              // Circled Ideograph Congratulation
        "[\\x{3299}]|" +              // Circled Ideograph Secret
        "[\\x{00A9}]|" +              // Copyright
        "[\\x{00AE}]|" +              // Registered
        "[\\x{203C}]|" +              // Double exclamation
        "[\\x{2049}]|" +              // Exclamation question mark
        "[\\x{200D}]|" +              // Zero width joiner
        "[\\x{20E3}]|" +              // Combining enclosing keycap
        "[\\x{FE0F}]|" +              // Variation selector
        "[\\x{E0020}-\\x{E007F}]",    // Tags
        Pattern.UNICODE_CHARACTER_CLASS
    );

    // Pattern to match multiple whitespace characters (spaces, tabs, newlines)
    private static final Pattern EXCESS_WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Main sanitization method that applies all cleaning rules to a text string.
     * 
     * @param text the text to sanitize
     * @return sanitized text, or null if input is null
     */
    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }

        String result = text;

        // Step 1: Remove "Related to search terms in your query" and everything after
        result = RELATED_SEARCH_PATTERN.matcher(result).replaceAll("");

        // Step 2: Remove emojis
        result = EMOJI_PATTERN.matcher(result).replaceAll("");

        // Step 3: Normalize whitespace - replace multiple spaces/newlines/tabs with single space
        result = EXCESS_WHITESPACE_PATTERN.matcher(result).replaceAll(" ");

        // Step 4: Trim leading and trailing whitespace
        result = result.trim();

        // Return null if result is empty after sanitization
        return result.isEmpty() ? null : result;
    }

    /**
     * Sanitizes all text fields in a ProfileData object.
     * Modifies the object in place and also returns it for method chaining.
     * 
     * @param profileData the profile data to sanitize
     * @return the sanitized ProfileData object
     */
    public static ProfileData sanitizeProfileData(ProfileData profileData) {
        if (profileData == null) {
            return null;
        }

        // Sanitize profile-level fields
        profileData.setTitle(sanitize(profileData.getTitle()));
        profileData.setLocation(sanitize(profileData.getLocation()));

        // Sanitize experiences
        List<Experience> experiences = profileData.getExperiences();
        if (experiences != null) {
            for (Experience experience : experiences) {
                sanitizeExperience(experience);
            }
        }

        // Sanitize education
        List<Education> educationList = profileData.getEducation();
        if (educationList != null) {
            for (Education education : educationList) {
                sanitizeEducation(education);
            }
        }

        // Sanitize skills
        List<Skill> skills = profileData.getSkills();
        if (skills != null) {
            for (Skill skill : skills) {
                sanitizeSkill(skill);
            }
        }

        return profileData;
    }

    /**
     * Sanitizes all text fields in an Experience object.
     * 
     * @param experience the experience to sanitize
     */
    public static void sanitizeExperience(Experience experience) {
        if (experience == null) {
            return;
        }

        experience.setCompanyName(sanitize(experience.getCompanyName()));
        experience.setPosition(sanitize(experience.getPosition()));
        experience.setLocation(sanitize(experience.getLocation()));
    }

    /**
     * Sanitizes all text fields in an Education object.
     * 
     * @param education the education to sanitize
     */
    public static void sanitizeEducation(Education education) {
        if (education == null) {
            return;
        }

        education.setSchoolName(sanitize(education.getSchoolName()));
        education.setDegreeName(sanitize(education.getDegreeName()));
        education.setFieldOfStudy(sanitize(education.getFieldOfStudy()));
    }

    /**
     * Sanitizes all text fields in a Skill object.
     * 
     * @param skill the skill to sanitize
     */
    public static void sanitizeSkill(Skill skill) {
        if (skill == null) {
            return;
        }

        skill.setSkillName(sanitize(skill.getSkillName()));
    }
}

