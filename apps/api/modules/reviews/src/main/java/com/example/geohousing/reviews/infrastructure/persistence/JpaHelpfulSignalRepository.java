package com.example.geohousing.reviews.infrastructure.persistence;

import com.example.geohousing.reviews.application.HelpfulSignalAlreadyActiveException;
import com.example.geohousing.reviews.application.HelpfulSignalRepository;
import com.example.geohousing.reviews.domain.HelpfulSignal;
import com.example.geohousing.reviews.domain.HelpfulSignalVoterId;
import com.example.geohousing.reviews.domain.ReviewId;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the private helpful-signal port. */
@Repository
public class JpaHelpfulSignalRepository implements HelpfulSignalRepository {

  private static final String ACTIVE_VOTER_UNIQUE_CONSTRAINT =
      "review_helpful_signal_one_active_voter_idx";

  private final SpringDataHelpfulSignalRepository signals;

  public JpaHelpfulSignalRepository(SpringDataHelpfulSignalRepository signals) {
    this.signals = signals;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<HelpfulSignal> findActive(ReviewId reviewId, HelpfulSignalVoterId voterId) {
    return signals
        .findByReviewIdAndVoterAccountIdAndWithdrawnAtIsNull(reviewId.value(), voterId.value())
        .map(HelpfulSignalJpaMapper::toDomain);
  }

  @Override
  @Transactional
  public void create(HelpfulSignal signal) {
    try {
      // Force the partial-unique-index race to surface while it can be translated at the port.
      signals.saveAndFlush(HelpfulSignalJpaMapper.toEntity(signal));
    } catch (DataIntegrityViolationException exception) {
      if (isActiveVoterUniqueViolation(exception)) {
        throw new HelpfulSignalAlreadyActiveException(signal.reviewId());
      }
      throw exception;
    }
  }

  @Override
  @Transactional
  public void withdraw(HelpfulSignal signal) {
    HelpfulSignalJpaEntity entity = signals.findById(signal.id().value()).orElseThrow();
    if (!entity.reviewId().equals(signal.reviewId().value())
        || !entity.voterAccountId().equals(signal.voterId().value())) {
      throw new IllegalArgumentException("helpful signal identity does not match its stored row");
    }
    entity.withdraw(signal.withdrawnAt());
    signals.saveAndFlush(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public long countActive(ReviewId reviewId) {
    return signals.countByReviewIdAndWithdrawnAtIsNull(reviewId.value());
  }

  private static boolean isActiveVoterUniqueViolation(DataIntegrityViolationException exception) {
    return exception.getCause() instanceof ConstraintViolationException violation
        && ACTIVE_VOTER_UNIQUE_CONSTRAINT.equals(violation.getConstraintName());
  }
}
