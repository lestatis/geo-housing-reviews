package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.application.EvidenceNotFoundException;
import com.example.geohousing.verification.application.EvidenceService;
import com.example.geohousing.verification.application.VerificationDecisionService;
import com.example.geohousing.verification.application.VerificationQueryService;
import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moderation of verification cases. The {@code ROLE_ADMIN} gate is enforced by the security filter
 * chain ({@code /api/admin/**}), so reaching this controller already implies an authenticated
 * moderator; every decision is audited by {@link VerificationDecisionService}, including one
 * against a case that does not exist.
 */
@RestController
@RequestMapping("/api/admin/verifications")
class AdminVerificationController {

  private final VerificationDecisionService decisionService;
  private final VerificationQueryService queryService;
  private final EvidenceService evidenceService;

  AdminVerificationController(
      VerificationDecisionService decisionService,
      VerificationQueryService queryService,
      EvidenceService evidenceService) {
    this.decisionService = decisionService;
    this.queryService = queryService;
    this.evidenceService = evidenceService;
  }

  /** The pending queue, oldest first. */
  @GetMapping
  VerificationCaseListResponse queue(
      Principal principal, @RequestParam(name = "limit", required = false) Integer limit) {
    return VerificationCaseListResponse.from(
        queryService.pendingQueue(WebAuthentication.moderatorViewer(principal), limit));
  }

  /** Any case, as a moderator sees it. */
  @GetMapping("/{caseId}")
  VerificationCaseResponse get(Principal principal, @PathVariable("caseId") String caseId) {
    return VerificationCaseResponse.from(
        queryService.getById(
            VerificationCaseId.of(parseUuid(caseId)),
            WebAuthentication.moderatorViewer(principal)));
  }

  /**
   * Metadata only. The object key, checksum, and document bytes never leave the verification
   * module.
   */
  @GetMapping("/{caseId}/evidence")
  VerificationEvidenceListResponse evidence(
      Principal principal, @PathVariable("caseId") String caseId) {
    return VerificationEvidenceListResponse.from(
        evidenceService.listForCase(
            VerificationCaseId.of(parseUuid(caseId)),
            WebAuthentication.moderatorViewer(principal)));
  }

  /**
   * Opens one moderator-authorized, audited read of a document. This is deliberately a proxied
   * response, rather than a reusable storage URL, and is explicitly non-cacheable.
   */
  @GetMapping("/{caseId}/evidence/{evidenceId}")
  ResponseEntity<InputStreamResource> readEvidence(
      Principal principal,
      @PathVariable("caseId") String caseId,
      @PathVariable("evidenceId") String evidenceId) {
    VerificationCaseId verificationCaseId = VerificationCaseId.of(parseUuid(caseId));
    EvidenceId id = EvidenceId.of(parseUuid(evidenceId));
    var viewer = WebAuthentication.moderatorViewer(principal);
    var metadata =
        evidenceService.listForCase(verificationCaseId, viewer).stream()
            .filter(evidence -> evidence.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new EvidenceNotFoundException(id));
    InputStream content = evidenceService.read(id, viewer);

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("verification-evidence").build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(metadata.contentType()))
        .contentLength(metadata.sizeBytes())
        .body(new InputStreamResource(content));
  }

  @PostMapping("/{caseId}/approve")
  VerificationCaseResponse approve(
      Principal principal,
      @PathVariable("caseId") String caseId,
      @RequestBody VerificationDecisionRequest request) {
    VerificationCaseId id = VerificationCaseId.of(parseUuid(caseId));
    return respond(
        () ->
            decisionService.approve(
                moderator(principal),
                id,
                requiredVersion(request),
                request.reasonCode(),
                request.validThrough()),
        id);
  }

  @PostMapping("/{caseId}/reject")
  VerificationCaseResponse reject(
      Principal principal,
      @PathVariable("caseId") String caseId,
      @RequestBody VerificationDecisionRequest request) {
    VerificationCaseId id = VerificationCaseId.of(parseUuid(caseId));
    return respond(
        () ->
            decisionService.reject(
                moderator(principal), id, requiredVersion(request), request.reasonCode()),
        id);
  }

  /**
   * Revokes an approved badge. The case becomes terminal and the account's review reverts to
   * unverified — the review itself is never removed by this action.
   */
  @PostMapping("/{caseId}/revoke")
  VerificationCaseResponse revoke(
      Principal principal,
      @PathVariable("caseId") String caseId,
      @RequestBody VerificationDecisionRequest request) {
    VerificationCaseId id = VerificationCaseId.of(parseUuid(caseId));
    return respond(
        () ->
            decisionService.revoke(
                moderator(principal), id, requiredVersion(request), request.reasonCode()),
        id);
  }

  private static ModeratorId moderator(Principal principal) {
    return WebAuthentication.moderatorId(principal);
  }

  private static UUID parseUuid(String value) {
    return UUID.fromString(value);
  }

  private static long requiredVersion(VerificationDecisionRequest request) {
    if (request.version() == null) {
      throw new IllegalArgumentException("version is required: it is the case the moderator saw");
    }
    return request.version();
  }

  private static VerificationCaseResponse respond(
      Supplier<Optional<VerificationCase>> action, VerificationCaseId requestedId) {
    // "Not found" is honest in the admin context, and the attempt has already been audited.
    return action
        .get()
        .map(VerificationCaseResponse::from)
        .orElseThrow(() -> new VerificationCaseNotFoundException(requestedId));
  }
}
