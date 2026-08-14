package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.api.AppealUseCase;
import com.example.geohousing.moderation.domain.Appeal;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.ModeratorId;
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
 * The appeals queue.
 *
 * <p>The moderator being appealed against cannot hear the appeal — enforced by the aggregate, by a
 * row-level CHECK, and refused here as forbidden rather than as a validation error, because it is a
 * due-process rule and not a malformed request.
 */
@RestController
@RequestMapping("/api/admin/moderation/appeals")
class AdminAppealController {

  private final AppealUseCase appeals;

  AdminAppealController(AppealUseCase appeals) {
    this.appeals = Objects.requireNonNull(appeals, "appeals");
  }

  @GetMapping
  AdminAppealQueueResponse pending() {
    return new AdminAppealQueueResponse(
        appeals.pending().stream().map(AdminAppealQueueEntryResponse::from).toList());
  }

  @PostMapping("/{appealId}/decide")
  AdminAppealResponse decide(
      Principal principal,
      @PathVariable("appealId") String appealId,
      @RequestBody DecideAppealRequest request) {
    AppealId id = AppealId.of(parseUuid(appealId));
    ModeratorId moderator = ModeratorId.of(UUID.fromString(principal.getName()));
    Appeal decided =
        switch (outcomeOf(request.outcome())) {
          case OVERTURN -> appeals.overturn(id, moderator, request.explanation());
          case UPHOLD -> appeals.uphold(id, moderator, request.explanation());
        };
    return AdminAppealResponse.from(decided);
  }

  private enum Outcome {
    UPHOLD,
    OVERTURN
  }

  private static Outcome outcomeOf(String outcome) {
    if (outcome == null || outcome.isBlank()) {
      throw new IllegalArgumentException("outcome must not be blank");
    }
    try {
      return Outcome.valueOf(outcome.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown outcome: " + outcome);
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
