package com.example.geohousing.reviews.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Content-version rows. Written on create and append, read by {@code current_version_id} for the
 * aggregate; the full per-review history is a moderation read path to be added when the moderation
 * module consumes it.
 */
interface SpringDataReviewVersionRepository extends JpaRepository<ReviewVersionJpaEntity, UUID> {}
