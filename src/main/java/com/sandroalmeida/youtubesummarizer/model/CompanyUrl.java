package com.sandroalmeida.youtubesummarizer.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * Entity representing a company URL.
 * Maps to the company_url table in the database.
 */
@Entity
@Table(name = "company_url", uniqueConstraints = {
    @UniqueConstraint(name = "url_UNIQUE", columnNames = "url")
})
public class CompanyUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_company_url")
    private Long idCompanyUrl;

    @Column(name = "url", nullable = false, length = 255)
    private String url;

    @Column(name = "status", nullable = false, length = 45)
    private String status = "0";

    public CompanyUrl() {
    }

    public CompanyUrl(String url) {
        this.url = url;
        this.status = "0";
    }

    public CompanyUrl(String url, String status) {
        this.url = url;
        this.status = status;
    }

    public Long getIdCompanyUrl() {
        return idCompanyUrl;
    }

    public void setIdCompanyUrl(Long idCompanyUrl) {
        this.idCompanyUrl = idCompanyUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompanyUrl that = (CompanyUrl) o;
        return Objects.equals(idCompanyUrl, that.idCompanyUrl) &&
            Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idCompanyUrl, url);
    }

    @Override
    public String toString() {
        return "CompanyUrl{" +
            "idCompanyUrl=" + idCompanyUrl +
            ", url='" + url + '\'' +
            ", status='" + status + '\'' +
            '}';
    }
}

