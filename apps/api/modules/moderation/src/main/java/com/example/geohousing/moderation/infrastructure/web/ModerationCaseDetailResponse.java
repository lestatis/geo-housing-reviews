package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.application.ModerationCaseDetail;
import com.example.geohousing.moderation.application.ModerationCaseSummary;
import java.util.List;

/** One case in full, for the moderator working it. */
public record ModerationCaseDetailResponse(
    ModerationCaseResponse summary,
    List<ConcernResponse> concerns,
    List<ModerationDecisionResponse> decisions) {

  static ModerationCaseDetailResponse from(ModerationCaseDetail detail) {
    return new ModerationCaseDetailResponse(
        ModerationCaseResponse.from(
            new ModerationCaseSummary(detail.moderationCase(), detail.reports().size())),
        detail.reports().stream().map(ConcernResponse::from).toList(),
        detail.decisions().stream().map(ModerationDecisionResponse::from).toList());
  }
}
