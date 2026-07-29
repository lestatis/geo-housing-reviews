package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationTargetRef;
import java.util.Optional;

/**
 * Outbound port for asking the owning module about the content under moderation.
 *
 * <p>Declared here with no adapter yet, exactly as the reviews module declared {@code
 * PropertyLookup} before the properties contract existed: the port states what this module needs,
 * and chunk 4 satisfies it through {@code reviews.api}. Keeping the dependency inverted is what
 * stops moderation reaching into another module's tables.
 */
public interface ModerationTargetLookup {

  /** The target, or empty when it does not exist or is not moderatable. */
  Optional<ModeratableTarget> find(ModerationTargetRef ref);
}
