package io.professionalhub.scraper.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * Entity representing a LinkedIn profile URL.
 * Maps to the profile_url table in the database.
 */
@Entity
@Table(name = "profile_url", uniqueConstraints = {
    @UniqueConstraint(name = "url_UNIQUE", columnNames = "url")
})
public class ProfileUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_profile_url")
    private Long idProfileUrl;

    @Column(name = "url", nullable = false, length = 255)
    private String url;

    @Column(name = "status", nullable = false)
    private Boolean status = false;

    public ProfileUrl() {
    }

    public ProfileUrl(String url) {
        this.url = url;
        this.status = false;
    }

    public ProfileUrl(String url, Boolean status) {
        this.url = url;
        this.status = status;
    }

    public Long getIdProfileUrl() {
        return idProfileUrl;
    }

    public void setIdProfileUrl(Long idProfileUrl) {
        this.idProfileUrl = idProfileUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileUrl that = (ProfileUrl) o;
        return Objects.equals(idProfileUrl, that.idProfileUrl) &&
            Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idProfileUrl, url);
    }

    @Override
    public String toString() {
        return "ProfileUrl{" +
            "idProfileUrl=" + idProfileUrl +
            ", url='" + url + '\'' +
            ", status=" + status +
            '}';
    }
}

