package io.professionalhub.scraper.repository;

import io.professionalhub.scraper.model.ProfileUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for ProfileUrl entity.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface ProfileUrlRepository extends JpaRepository<ProfileUrl, Long> {

    /**
     * Find a ProfileUrl by its URL.
     *
     * @param url the profile URL to search for
     * @return Optional containing the ProfileUrl if found, empty otherwise
     */
    Optional<ProfileUrl> findByUrl(String url);

    /**
     * Check if a ProfileUrl exists with the given URL.
     *
     * @param url the profile URL to check
     * @return true if a ProfileUrl with the given URL exists, false otherwise
     */
    boolean existsByUrl(String url);

    /**
     * Find the first ProfileUrl with status = false (0), ordered by idProfileUrl ASC.
     * This is used to fetch the next URL to process.
     *
     * @return Optional containing the ProfileUrl if found, empty otherwise
     */
    Optional<ProfileUrl> findFirstByStatusOrderByIdProfileUrlAsc(Boolean status);
}

