package com.sandroalmeida.youtubesummarizer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Data model representing a LinkedIn education entry
 * Contains all the education information extracted from the profile
 */
public class Education {

    @JsonProperty("school_name")
    private String schoolName;

    @JsonProperty("school_logo_url")
    private String schoolLogoUrl;

    @JsonProperty("degree_name")
    private String degreeName;

    @JsonProperty("field_of_study")
    private String fieldOfStudy;

    @JsonProperty("dates_attended")
    private String datesAttended;

    @JsonProperty("description")
    private String description;

    @JsonProperty("school_link")
    private String schoolLink;

    // Default constructor for Jackson
    public Education() {
    }

    // Constructor with basic fields
    public Education(String schoolName, String degreeName, String fieldOfStudy) {
        this.schoolName = schoolName;
        this.degreeName = degreeName;
        this.fieldOfStudy = fieldOfStudy;
    }

    // Constructor with all fields
    public Education(String schoolName, String schoolLogoUrl, String degreeName,
                    String fieldOfStudy, String datesAttended, String description,
                    String schoolLink) {
        this.schoolName = schoolName;
        this.schoolLogoUrl = schoolLogoUrl;
        this.degreeName = degreeName;
        this.fieldOfStudy = fieldOfStudy;
        this.datesAttended = datesAttended;
        this.description = description;
        this.schoolLink = schoolLink;
    }

    // Getters and setters
    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getSchoolLogoUrl() {
        return schoolLogoUrl;
    }

    public void setSchoolLogoUrl(String schoolLogoUrl) {
        this.schoolLogoUrl = schoolLogoUrl;
    }

    public String getDegreeName() {
        return degreeName;
    }

    public void setDegreeName(String degreeName) {
        this.degreeName = degreeName;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public void setFieldOfStudy(String fieldOfStudy) {
        this.fieldOfStudy = fieldOfStudy;
    }

    public String getDatesAttended() {
        return datesAttended;
    }

    public void setDatesAttended(String datesAttended) {
        this.datesAttended = datesAttended;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSchoolLink() {
        return schoolLink;
    }

    public void setSchoolLink(String schoolLink) {
        this.schoolLink = schoolLink;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Education education = (Education) o;
        return Objects.equals(schoolName, education.schoolName) &&
            Objects.equals(schoolLogoUrl, education.schoolLogoUrl) &&
            Objects.equals(degreeName, education.degreeName) &&
            Objects.equals(fieldOfStudy, education.fieldOfStudy) &&
            Objects.equals(datesAttended, education.datesAttended) &&
            Objects.equals(description, education.description) &&
            Objects.equals(schoolLink, education.schoolLink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schoolName, schoolLogoUrl, degreeName, fieldOfStudy,
            datesAttended, description, schoolLink);
    }

    @Override
    public String toString() {
        return "Education{" +
            "schoolName='" + schoolName + '\'' +
            ", schoolLogoUrl='" + schoolLogoUrl + '\'' +
            ", degreeName='" + degreeName + '\'' +
            ", fieldOfStudy='" + fieldOfStudy + '\'' +
            ", datesAttended='" + datesAttended + '\'' +
            ", description='" + description + '\'' +
            ", schoolLink='" + schoolLink + '\'' +
            '}';
    }
}