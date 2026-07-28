package com.example.geohousing.moderation.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The account of a moderator deciding a case or an appeal.
 *
 * <p>A distinct type from {@link ReporterId} and {@link AppellantId} even though all three are the
 * same account id, so a reporter can never be passed where a decision-maker is expected. The
 * different-decider rule on appeals compares two of these, which is only meaningful if the type
 * says what the id is doing.
 */
public record ModeratorId(UUID value) {

  public ModeratorId {
    Objects.requireNonNull(value, "moderator id value must not be null");
  }

  public static ModeratorId of(UUID value) {
    return new ModeratorId(value);
  }
}
