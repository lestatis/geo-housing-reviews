package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.application.VerificationDecisionService;
import com.example.geohousing.verification.application.VerificationQueryService;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
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

  AdminVerificationController(
      VerificationDecisionService decisionService, VerificationQueryService queryService) {
    this.decisionService = decisionService;
    this.queryService = queryService;
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
