package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewModerationAuditEvent;

/**
 * Persistence boundary for moderation decisions. The state change and its audit row are written
 * together so a review's publication state can never be changed by a moderator without the action
 * being recorded — the application layer stays framework-free, so the atomicity lives in the
 * adapter rather than a transactional service.
 */
public interface ReviewModerationRepository {

  /**
   * Persists the moderated review and the audit event in one transaction.
   *
   * @throws com.example.geohousing.reviews.domain.ReviewVersionConflictException if the stored
   *     version no longer matches the one the review was loaded at
   */
  void applyDecision(Review review, ReviewModerationAuditEvent event);

  /** Records an action attempted against a review that does not exist. */
  void recordAttempt(ReviewModerationAuditEvent event);
}
