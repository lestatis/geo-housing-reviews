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
 *
 * <p>{@code visible} reports a fact, not a policy: whether the owning module currently shows this
 * to the public. Who may act on it given that fact is this module's decision — a moderator works
 * withdrawn content all the time, while a reporter must not even learn it exists.
 */
public record ModeratableTarget(
    ModerationTargetRef ref, UUID authorAccountId, long version, boolean visible) {

  public ModeratableTarget {
    Objects.requireNonNull(ref, "ref");
    Objects.requireNonNull(authorAccountId, "authorAccountId");
  }
}
