package com.sandroalmeida.youtubesummarizer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Data model representing a LinkedIn work experience entry
 * Contains all the experience information extracted from the profile
 */
public class Experience {

    @JsonProperty("position")
    private String position;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("employment_status")
    private String employmentStatus;

    @JsonProperty("date_range")
    private String dateRange;

    @JsonProperty("duration")
    private String duration;

    @JsonProperty("location")
    private String location;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("skills")
    private List<String> skills;

    @JsonProperty("company_url")
    private String companyUrl;

    // Default constructor for Jackson
    public Experience() {
    }

    // Constructor with basic fields
    public Experience(String position, String companyName, String dateRange) {
        this.position = position;
        this.companyName = companyName;
        this.dateRange = dateRange;
    }

    // Constructor with all fields
    public Experience(String position, String companyName, String employmentStatus,
                     String dateRange, String duration, String location,
                     String summary, List<String> skills, String companyUrl) {
        this.position = position;
        this.companyName = companyName;
        this.employmentStatus = employmentStatus;
        this.dateRange = dateRange;
        this.duration = duration;
        this.location = location;
        this.summary = summary;
        this.skills = skills;
        this.companyUrl = companyUrl;
    }

    // Getters and setters
    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public String getDateRange() {
        return dateRange;
    }

    public void setDateRange(String dateRange) {
        this.dateRange = dateRange;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public String getCompanyUrl() {
        return companyUrl;
    }

    public void setCompanyUrl(String companyUrl) {
        this.companyUrl = companyUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Experience that = (Experience) o;
        return Objects.equals(position, that.position) &&
            Objects.equals(companyName, that.companyName) &&
            Objects.equals(employmentStatus, that.employmentStatus) &&
            Objects.equals(dateRange, that.dateRange) &&
            Objects.equals(duration, that.duration) &&
            Objects.equals(location, that.location) &&
            Objects.equals(summary, that.summary) &&
            Objects.equals(skills, that.skills) &&
            Objects.equals(companyUrl, that.companyUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, companyName, employmentStatus, dateRange,
            duration, location, summary, skills, companyUrl);
    }

    @Override
    public String toString() {
        return "Experience{" +
            "position='" + position + '\'' +
            ", companyName='" + companyName + '\'' +
            ", employmentStatus='" + employmentStatus + '\'' +
            ", dateRange='" + dateRange + '\'' +
            ", duration='" + duration + '\'' +
            ", location='" + location + '\'' +
            ", summary='" + summary + '\'' +
            ", skills=" + skills +
            ", companyUrl='" + companyUrl + '\'' +
            '}';
    }
}