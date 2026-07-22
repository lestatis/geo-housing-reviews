package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.application.DuplicateCandidate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The 409 body returned when a create attempt matched possible duplicates. It carries the
 * candidates so a client can offer "use this existing property instead", or resubmit with {@code
 * allowDuplicate=true}. Duplicates are an expected outcome, not a failure — the status simply tells
 * the client the create did not happen.
 */
final class DuplicateCandidatesProblem {

  private DuplicateCandidatesProblem() {}

  static ProblemDetail of(List<DuplicateCandidate> candidates) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "A property with a very similar name or location already exists."
                + " Use one of the candidates, or resubmit with allowDuplicate=true.");
    problem.setTitle("Possible duplicate property");
    problem.setProperty("code", "PROPERTY_DUPLICATE_CANDIDATES");
    problem.setProperty(
        "candidates",
        candidates.stream()
            .map(
                candidate ->
                    Map.of(
                        "propertyId", candidate.propertyId().value().toString(),
                        "canonicalName", candidate.canonicalName()))
            .toList());
    return problem;
  }
}
