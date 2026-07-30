package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.application.ModerationCaseService;
import com.example.geohousing.moderation.application.ModerationQueueService;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;
import java.security.Principal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The moderator's queue: see what is waiting, read one case, and decide it.
 *
 * <p>The security chain restricts {@code /api/admin/**} to administrators, so reaching this
 * controller already implies one. Every decision records the moderator taken from the token, and
 * the decision rows are themselves the audit trail — there is no way to act here without leaving
 * one.
 */
@RestController
@RequestMapping("/api/admin/moderation/cases")
class AdminModerationController {

  private final ModerationQueueService queue;
  private final ModerationCaseService cases;

  AdminModerationController(ModerationQueueService queue, ModerationCaseService cases) {
    this.queue = Objects.requireNonNull(queue, "queue");
    this.cases = Objects.requireNonNull(cases, "cases");
  }

  @GetMapping
  ModerationQueueResponse queue() {
    return new ModerationQueueResponse(
        queue.queue().stream().map(ModerationCaseResponse::from).toList());
  }

  @GetMapping("/{caseId}")
  ModerationCaseDetailResponse get(@PathVariable("caseId") String caseId) {
    return ModerationCaseDetailResponse.from(queue.detail(ModerationCaseId.of(parseUuid(caseId))));
  }

  @PostMapping("/{caseId}/assign")
  ModerationCaseDetailResponse assign(Principal principal, @PathVariable("caseId") String caseId) {
    ModerationCaseId id = ModerationCaseId.of(parseUuid(caseId));
    cases.assign(id, moderator(principal));
    // Read back rather than building a response from the write: the concern count belongs to the
    // case, and inventing one here would be a number nobody checked.
    return ModerationCaseDetailResponse.from(queue.detail(id));
  }

  /**
   * Records the decision and applies it to the content.
   *
   * <p>Takes the case in the same call if nobody holds it. The accountability rule is that a
   * decision names a moderator, not that they clicked twice to get there — and forcing an
   * assign-then-decide round trip would only tempt a queue tool to paper over it.
   */
  @PostMapping("/{caseId}/decide")
  ModerationCaseDetailResponse decide(
      Principal principal,
      @PathVariable("caseId") String caseId,
      @RequestBody DecideCaseRequest request) {
    ModerationCaseId id = ModerationCaseId.of(parseUuid(caseId));
    ModeratorId moderator = moderator(principal);
    cases.claim(id, moderator);
    cases.decide(
        id,
        moderator,
        parseAction(request.action()),
        ReasonCode.of(request.reasonCode()),
        request.publicExplanation(),
        request.internalNote());
    return ModerationCaseDetailResponse.from(queue.detail(id));
  }

  private static ModeratorId moderator(Principal principal) {
    return ModeratorId.of(UUID.fromString(principal.getName()));
  }

  private static DecisionAction parseAction(String action) {
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("action must not be blank");
    }
    try {
      return DecisionAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown action: " + action);
    }
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new IllegalArgumentException("not a valid identifier: " + value);
    }
  }
}
