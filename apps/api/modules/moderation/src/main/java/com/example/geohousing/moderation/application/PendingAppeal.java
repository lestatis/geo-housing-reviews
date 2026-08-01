package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import java.util.Objects;

/**
 * An appeal as the moderator who will hear it needs to see it: the challenge, the decision being
 * challenged, and the content both are about.
 *
 * <p>The appeal alone is only one side of the argument. MODERATION.md requires an appeal to be
 * heard by someone other than the original decider, and someone hearing it for the first time has
 * no memory of the case — so a queue that showed only the appellant's text would be asking them to
 * decide against a decision they cannot read.
 *
 * <p>The decision's {@code internalNote} travels with it and is admin-only by construction; nothing
 * author-facing is built from this record.
 */
public record PendingAppeal(
    Appeal appeal, ModerationDecision contestedDecision, ModerationTargetRef target) {

  public PendingAppeal {
    Objects.requireNonNull(appeal, "appeal");
    Objects.requireNonNull(contestedDecision, "contestedDecision");
    Objects.requireNonNull(target, "target");
  }
}
