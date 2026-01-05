package com.sandroalmeida.youtubesummarizer.repository;

import com.sandroalmeida.youtubesummarizer.model.CompanyUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for CompanyUrl entity.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface CompanyUrlRepository extends JpaRepository<CompanyUrl, Long> {

    /**
     * Find a CompanyUrl by its URL.
     *
     * @param url the company URL to search for
     * @return Optional containing the CompanyUrl if found, empty otherwise
     */
    Optional<CompanyUrl> findByUrl(String url);

    /**
     * Check if a CompanyUrl exists with the given URL.
     *
     * @param url the company URL to check
     * @return true if a CompanyUrl with the given URL exists, false otherwise
     */
    boolean existsByUrl(String url);

    /**
     * Find the first CompanyUrl with the given status, ordered by idCompanyUrl ASC.
     *
     * @param status the status to search for
     * @return Optional containing the CompanyUrl if found, empty otherwise
     */
    Optional<CompanyUrl> findFirstByStatusOrderByIdCompanyUrlAsc(String status);
}

