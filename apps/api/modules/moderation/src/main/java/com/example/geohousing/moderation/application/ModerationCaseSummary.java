package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCase;
import java.util.List;
import java.util.Objects;

/**
 * A case as a moderator working the queue sees it: the case itself, and how many distinct concerns
 * were raised about it.
 *
 * <p>The count, not the reporters. One account can raise at most one live report per target, so the
 * count already answers the question a moderator actually has — is this one person's complaint or
 * twenty? — without naming anyone. Identities would add nothing to that judgement and would invite
 * deciding by who complained rather than by what the content says.
 */
public record ModerationCaseSummary(ModerationCase moderationCase, int concernCount) {

  public ModerationCaseSummary {
    Objects.requireNonNull(moderationCase, "moderationCase");
  }

  static ModerationCaseSummary of(ModerationCase moderationCase, List<?> reports) {
    return new ModerationCaseSummary(moderationCase, reports.size());
  }
}
