package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationDecision;
import com.example.geohousing.moderation.domain.Report;
import java.util.List;
import java.util.Objects;

/**
 * Everything a moderator needs to judge one case: the case, the substance of each concern, and the
 * decisions already recorded against it.
 *
 * <p>The reports are carried whole because their category and description are the concern itself.
 * Who wrote them is not part of this view — see {@link ModerationCaseSummary}.
 */
public record ModerationCaseDetail(
    ModerationCase moderationCase, List<Report> reports, List<ModerationDecision> decisions) {

  public ModerationCaseDetail {
    Objects.requireNonNull(moderationCase, "moderationCase");
    reports = List.copyOf(reports);
    decisions = List.copyOf(decisions);
  }
}
