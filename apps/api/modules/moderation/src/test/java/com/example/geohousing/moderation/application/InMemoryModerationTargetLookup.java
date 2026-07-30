package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationTargetRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory stand-in for the owning module's content lookup. */
final class InMemoryModerationTargetLookup implements ModerationTargetLookup {

  private final Map<ModerationTargetRef, ModeratableTarget> targets = new LinkedHashMap<>();

  ModerationTargetRef givenReviewBy(UUID authorAccountId, long version) {
    return givenReview(authorAccountId, version, true);
  }

  ModerationTargetRef givenReview(UUID authorAccountId, long version, boolean visible) {
    ModerationTargetRef ref = ModerationTargetRef.review(UUID.randomUUID());
    targets.put(ref, new ModeratableTarget(ref, authorAccountId, version, visible));
    return ref;
  }

  @Override
  public Optional<ModeratableTarget> find(ModerationTargetRef ref) {
    return Optional.ofNullable(targets.get(ref));
  }
}
