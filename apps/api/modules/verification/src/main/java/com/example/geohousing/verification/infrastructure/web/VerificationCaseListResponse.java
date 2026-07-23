package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.domain.VerificationCase;
import java.util.List;

/** A page of verification cases — the moderator queue. */
public record VerificationCaseListResponse(List<VerificationCaseResponse> items) {

  static VerificationCaseListResponse from(List<VerificationCase> cases) {
    return new VerificationCaseListResponse(
        cases.stream().map(VerificationCaseResponse::from).toList());
  }
}
