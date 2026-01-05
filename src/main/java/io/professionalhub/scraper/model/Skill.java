package io.professionalhub.scraper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Data model representing a LinkedIn skill entry
 * Contains skill information including name, endorsements, and associated positions
 */
public class Skill {

    @JsonProperty("skill_name")
    private String skillName;

    @JsonProperty("endorsement_count")
    private Integer endorsementCount;

    @JsonProperty("associated_positions")
    private String associatedPositions;

    // Default constructor for Jackson
    public Skill() {
    }

    // Constructor with basic fields
    public Skill(String skillName) {
        this.skillName = skillName;
    }

    // Constructor with all fields
    public Skill(String skillName, Integer endorsementCount, String associatedPositions) {
        this.skillName = skillName;
        this.endorsementCount = endorsementCount;
        this.associatedPositions = associatedPositions;
    }

    // Getters and setters
    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public Integer getEndorsementCount() {
        return endorsementCount;
    }

    public void setEndorsementCount(Integer endorsementCount) {
        this.endorsementCount = endorsementCount;
    }

    public String getAssociatedPositions() {
        return associatedPositions;
    }

    public void setAssociatedPositions(String associatedPositions) {
        this.associatedPositions = associatedPositions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Skill skill = (Skill) o;
        return Objects.equals(skillName, skill.skillName) &&
            Objects.equals(endorsementCount, skill.endorsementCount) &&
            Objects.equals(associatedPositions, skill.associatedPositions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillName, endorsementCount, associatedPositions);
    }

    @Override
    public String toString() {
        return "Skill{" +
            "skillName='" + skillName + '\'' +
            ", endorsementCount=" + endorsementCount +
            ", associatedPositions='" + associatedPositions + '\'' +
            '}';
    }
}