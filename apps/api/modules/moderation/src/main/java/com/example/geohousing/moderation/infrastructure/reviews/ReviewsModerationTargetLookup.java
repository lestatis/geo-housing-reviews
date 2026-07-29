package com.example.geohousing.moderation.infrastructure.reviews;

import com.example.geohousing.moderation.application.ModeratableTarget;
import com.example.geohousing.moderation.application.ModerationTargetLookup;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModerationTargetType;
import com.example.geohousing.reviews.api.ReviewModerationGateway;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers this module's {@link ModerationTargetLookup} from the reviews module's published gateway.
 *
 * <p>This adapter and its sibling are the whole of the dependency: the application layer keeps
 * talking to its own ports, and only these two classes know another module exists.
 */
@Component
public class ReviewsModerationTargetLookup implements ModerationTargetLookup {

  private final ReviewModerationGateway gateway;

  public ReviewsModerationTargetLookup(ReviewModerationGateway gateway) {
    this.gateway = Objects.requireNonNull(gateway, "gateway");
  }

  @Override
  public Optional<ModeratableTarget> find(ModerationTargetRef ref) {
    Objects.requireNonNull(ref, "ref");
    if (ref.type() != ModerationTargetType.REVIEW) {
      // Reviews are the only moderatable content today. A silent empty here would look like
      // "content does not exist" and quietly drop reports about a type nobody wired up yet.
      throw new IllegalArgumentException("no lookup is wired for target type " + ref.type());
    }
    return gateway
        .find(ref.id())
        .map(review -> new ModeratableTarget(ref, review.authorAccountId(), review.version()));
  }
}
