package com.sandroalmeida.youtubesummarizer.repository;

import com.sandroalmeida.youtubesummarizer.entity.SavedVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedVideoRepository extends JpaRepository<SavedVideo, Long> {

    Optional<SavedVideo> findByVideoUrl(String videoUrl);

    boolean existsByVideoUrl(String videoUrl);

    List<SavedVideo> findAllByOrderBySavedAtDesc();
}
