package com.example.geohousing.verification.infrastructure.reviews;

import com.example.geohousing.reviews.api.ReviewVerificationTier;
import com.example.geohousing.reviews.api.ReviewVerificationUpdater;
import com.example.geohousing.verification.application.ReviewProjection;
import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationTier;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Projects a verification decision onto the account's review through the reviews module's inbound
 * published api. Only this class knows the reviews module exists; the application layer keeps
 * talking to its own {@link ReviewProjection} port.
 *
 * <p>Translating the verification tier to the reviews api tier here is the point of the boundary —
 * each module keeps its own vocabulary, and this adapter maps between them.
 */
@Component
public class ReviewProjectionAdapter implements ReviewProjection {

  private final ReviewVerificationUpdater reviewVerificationUpdater;

  public ReviewProjectionAdapter(ReviewVerificationUpdater reviewVerificationUpdater) {
    this.reviewVerificationUpdater =
        Objects.requireNonNull(reviewVerificationUpdater, "reviewVerificationUpdater");
  }

  @Override
  public void applyTier(AccountRef accountRef, PropertyRef propertyRef, VerificationTier tier) {
    Objects.requireNonNull(accountRef, "accountRef");
    Objects.requireNonNull(propertyRef, "propertyRef");
    Objects.requireNonNull(tier, "tier");
    reviewVerificationUpdater.applyTier(accountRef.value(), propertyRef.value(), toApi(tier));
  }

  private static ReviewVerificationTier toApi(VerificationTier tier) {
    return switch (tier) {
      case UNVERIFIED -> ReviewVerificationTier.UNVERIFIED;
      case RELATIONSHIP_SIGNAL -> ReviewVerificationTier.RELATIONSHIP_SIGNAL;
      case DOCUMENT_VERIFIED -> ReviewVerificationTier.DOCUMENT_VERIFIED;
    };
  }
}
