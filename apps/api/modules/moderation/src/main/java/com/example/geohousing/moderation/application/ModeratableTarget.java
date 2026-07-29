package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationTargetRef;
import java.util.Objects;
import java.util.UUID;

/**
 * What moderation needs to know about a piece of content owned by another module: who wrote it and
 * which version is current.
 *
 * <p>The author is needed to refuse a self-report and to know who an adverse decision affects; the
 * version is stamped onto a decision so an edit afterwards is visibly different from what the
 * moderator read. Nothing about the content itself crosses the boundary — moderation does not need
 * the text to run a case workflow.
 */
public record ModeratableTarget(ModerationTargetRef ref, UUID authorAccountId, long version) {

  public ModeratableTarget {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(authorAccountId, "authorAccountId");
  }
}
