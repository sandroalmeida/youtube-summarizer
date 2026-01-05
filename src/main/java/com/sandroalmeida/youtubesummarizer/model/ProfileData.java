package com.sandroalmeida.youtubesummarizer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Data model representing a LinkedIn profile
 * Contains all the profile information extracted from the page
 */
public class ProfileData {

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("profile_public_url")
    private String profilePublicUrl;

    @JsonProperty("profile_url")
    private String profileUrl;

    @JsonProperty("title")
    private String title;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("latest_education")
    private String latestEducation;

    @JsonProperty("location")
    private String location;

    @JsonProperty("current_industry")
    private String currentIndustry;

    @JsonProperty("experiences")
    private List<Experience> experiences;

    @JsonProperty("education")
    private List<Education> education;

    @JsonProperty("skills")
    private List<Skill> skills;

    @JsonProperty("extraction_timestamp")
    private long extractionTimestamp;

    // Default constructor for Jackson
    public ProfileData() {
        this.extractionTimestamp = System.currentTimeMillis();
    }

    // Constructor with basic fields
    public ProfileData(String fullName, String imageUrl, String profileUrl, String profilePublicUrl) {
        this.fullName = fullName;
        this.imageUrl = imageUrl;
        this.profileUrl = profileUrl;
        this.profilePublicUrl = profilePublicUrl;
        this.extractionTimestamp = System.currentTimeMillis();
    }

    public ProfileData(String fullName, String imageUrl, String profileUrl, String profilePublicUrl,
            String title, String summary, String latestEducation,
            String location, String currentIndustry, List<Experience> experiences,
            List<Education> education, List<Skill> skills) {
        this.fullName = fullName;
        this.imageUrl = imageUrl;
        this.profileUrl = profileUrl;
        this.profilePublicUrl = profilePublicUrl;
        this.title = title;
        this.summary = summary;
        this.latestEducation = latestEducation;
        this.location = location;
        this.currentIndustry = currentIndustry;
        this.experiences = experiences;
        this.education = education;
        this.skills = skills;
        this.extractionTimestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public String getProfilePublicUrl() {
        return profilePublicUrl;
    }

    public void setProfilePublicUrl(String profilePublicUrl) {
        this.profilePublicUrl = profilePublicUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLatestEducation() {
        return latestEducation;
    }

    public void setLatestEducation(String latestEducation) {
        this.latestEducation = latestEducation;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCurrentIndustry() {
        return currentIndustry;
    }

    public void setCurrentIndustry(String currentIndustry) {
        this.currentIndustry = currentIndustry;
    }

    public List<Experience> getExperiences() {
        return experiences;
    }

    public void setExperiences(List<Experience> experiences) {
        this.experiences = experiences;
    }

    public List<Education> getEducation() {
        return education;
    }

    public void setEducation(List<Education> education) {
        this.education = education;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills;
    }

    public long getExtractionTimestamp() {
        return extractionTimestamp;
    }

    public void setExtractionTimestamp(long extractionTimestamp) {
        this.extractionTimestamp = extractionTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ProfileData that = (ProfileData) o;
        return Objects.equals(fullName, that.fullName) &&
                Objects.equals(imageUrl, that.imageUrl) &&
                Objects.equals(profileUrl, that.profileUrl) &&
                Objects.equals(profilePublicUrl, that.profilePublicUrl) &&
                Objects.equals(title, that.title) &&
                Objects.equals(summary, that.summary) &&
                Objects.equals(latestEducation, that.latestEducation) &&
                Objects.equals(location, that.location) &&
                Objects.equals(currentIndustry, that.currentIndustry) &&
                Objects.equals(experiences, that.experiences) &&
                Objects.equals(education, that.education);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName, imageUrl, profileUrl, profilePublicUrl, title, summary,
                latestEducation, location, currentIndustry, experiences, education);
    }

    @Override
    public String toString() {
        return "ProfileData{" +
                "fullName='" + fullName + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", profileUrl='" + profileUrl + '\'' +
                ", profilePublicUrl='" + profilePublicUrl + '\'' +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", latestEducation='" + latestEducation + '\'' +
                ", location='" + location + '\'' +
                ", currentIndustry='" + currentIndustry + '\'' +
                ", experiences=" + experiences +
                ", education=" + education +
                ", extractionTimestamp=" + extractionTimestamp +
                '}';
    }
}