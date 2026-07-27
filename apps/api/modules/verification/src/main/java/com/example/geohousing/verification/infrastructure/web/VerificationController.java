package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.application.EvidenceService;
import com.example.geohousing.verification.application.OpenVerificationCommand;
import com.example.geohousing.verification.application.VerificationQueryService;
import com.example.geohousing.verification.application.VerificationSubmissionService;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import com.example.geohousing.verification.domain.VerificationEvidence;
import com.example.geohousing.verification.domain.VerificationMethod;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A user's own verification cases: opening one, reading it, cancelling it, and checking whether
 * they are verified for a property. Every endpoint is authenticated — a case is private, so nothing
 * here is a public read.
 */
@RestController
class VerificationController {

  private final VerificationSubmissionService submissionService;
  private final VerificationQueryService queryService;
  private final EvidenceService evidenceService;

  VerificationController(
      VerificationSubmissionService submissionService,
      VerificationQueryService queryService,
      EvidenceService evidenceService) {
    this.submissionService = submissionService;
    this.queryService = queryService;
    this.evidenceService = evidenceService;
  }

  /**
   * Opens a verification case for the authenticated account. Responds 201 with the pending case.
   *
   * <p>A replay is safe: one live case per account+property, so a repeat is refused with 409 {@code
   * VERIFICATION_CASE_ALREADY_EXISTS} carrying the existing case's id and a {@code Location}
   * header.
   */
  @PostMapping("/api/verifications")
  ResponseEntity<VerificationCaseResponse> open(
      Principal principal, @RequestBody OpenVerificationRequest request) {
    OpenVerificationCommand command =
        new OpenVerificationCommand(
            WebAuthentication.accountRef(principal),
            PropertyRef.of(parseUuid(request.propertyId())),
            parseClaim(request.relationshipClaim()),
            parseMethod(request.method()));

    VerificationCase opened = submissionService.open(command);
    return ResponseEntity.created(location(opened.id()))
        .body(VerificationCaseResponse.from(opened));
  }

  @GetMapping("/api/verifications/{caseId}")
  VerificationCaseResponse get(Principal principal, @PathVariable("caseId") String caseId) {
    return VerificationCaseResponse.from(
        queryService.getById(
            VerificationCaseId.of(parseUuid(caseId)), WebAuthentication.ownerViewer(principal)));
  }

  @PostMapping("/api/verifications/{caseId}/cancel")
  VerificationCaseResponse cancel(Principal principal, @PathVariable("caseId") String caseId) {
    return VerificationCaseResponse.from(
        submissionService.cancel(
            VerificationCaseId.of(parseUuid(caseId)), WebAuthentication.ownerViewer(principal)));
  }

  /**
   * Attaches one document to the caller's pending Tier 2 case. The original filename is not kept or
   * returned: document names often contain personal information and are unnecessary to verify a
   * relationship.
   */
  @PostMapping(value = "/api/verifications/{caseId}/evidence", consumes = "multipart/form-data")
  ResponseEntity<VerificationEvidenceResponse> attachEvidence(
      Principal principal,
      @PathVariable("caseId") String caseId,
      @RequestPart("document") MultipartFile document) {
    VerificationEvidence evidence;
    try (InputStream content = document.getInputStream()) {
      evidence =
          evidenceService.attach(
              VerificationCaseId.of(parseUuid(caseId)),
              WebAuthentication.ownerViewer(principal),
              document.getContentType(),
              content);
    } catch (IOException exception) {
      throw new IllegalArgumentException("could not read uploaded evidence", exception);
    }
    return ResponseEntity.status(201).body(VerificationEvidenceResponse.from(evidence));
  }

  /**
   * The caller's latest case for a property — how a client learns whether the account is verified
   * there. Not under {@code /api/properties} on purpose: that would make it a public read, but a
   * case is private to its account.
   */
  @GetMapping("/api/verifications")
  VerificationCaseResponse myCaseForProperty(
      Principal principal, @RequestParam("propertyId") String propertyId) {
    return queryService
        .findMine(WebAuthentication.accountRef(principal), PropertyRef.of(parseUuid(propertyId)))
        .map(VerificationCaseResponse::from)
        .orElseThrow(
            () -> new VerificationCaseNotFoundException(VerificationCaseId.of(new UUID(0L, 0L))));
  }

  private static URI location(VerificationCaseId caseId) {
    return URI.create("/api/verifications/" + caseId.value());
  }

  private static UUID parseUuid(String value) {
    return UUID.fromString(value);
  }

  private static RelationshipClaim parseClaim(String value) {
    if (value == null) {
      throw new IllegalArgumentException("relationshipClaim is required");
    }
    try {
      return RelationshipClaim.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown relationshipClaim: " + value);
    }
  }

  private static VerificationMethod parseMethod(String value) {
    if (value == null) {
      throw new IllegalArgumentException("method is required");
    }
    try {
      return VerificationMethod.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown method: " + value);
    }
  }
}
