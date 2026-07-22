package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.application.ReviewModerationRepository;
import com.example.geohousing.reviews.application.ReviewRepository;
import com.example.geohousing.reviews.domain.Review;
import com.example.geohousing.reviews.domain.ReviewModerationAuditEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a moderation decision and its audit row in one transaction, so a review's publication
 * state can never be changed by a moderator without the action being recorded.
 *
 * <p>The review itself is saved through the {@link ReviewRepository} port rather than a second
 * write path: its adapter owns the optimistic-version check and the append-only trail guards, and a
 * moderation write must not become a way around them. Its {@code @Transactional} joins this one, so
 * the mutation and the audit row still commit or roll back together.
 */
@Repository
public class JpaReviewModerationRepository implements ReviewModerationRepository {

  private final ReviewRepository reviewRepository;
  private final SpringDataReviewModerationAuditEventRepository auditEvents;

  public JpaReviewModerationRepository(
      ReviewRepository reviewRepository,
      SpringDataReviewModerationAuditEventRepository auditEvents) {
    this.reviewRepository = reviewRepository;
    this.auditEvents = auditEvents;
  }

  @Override
  @Transactional
  public void applyDecision(Review review, ReviewModerationAuditEvent event) {
    reviewRepository.save(review);
    auditEvents.saveAndFlush(ReviewModerationAuditEventJpaMapper.toEntity(event));
  }

  @Override
  @Transactional
  public void recordAttempt(ReviewModerationAuditEvent event) {
    auditEvents.saveAndFlush(ReviewModerationAuditEventJpaMapper.toEntity(event));
  }
}
