package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.application.AppealService;
import com.example.geohousing.moderation.domain.AppealId;
import com.example.geohousing.moderation.domain.AppellantId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModerationTargetType;
import java.net.URI;
import java.security.Principal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets an author challenge a decision that took their content down, and follow the outcome. */
@RestController
@RequestMapping("/api/appeals")
class AppealController {

  private final AppealService appeals;

  AppealController(AppealService appeals) {
    this.appeals = Objects.requireNonNull(appeals, "appeals");
  }

  @PostMapping
  ResponseEntity<AppealResponse> submit(
      Principal principal, @RequestBody SubmitAppealRequest request) {
    AppealResponse filed =
        AppealResponse.from(
            appeals.file(
                AppellantId.of(UUID.fromString(principal.getName())),
                targetOf(request),
                request.appealText()));
    return ResponseEntity.created(URI.create("/api/appeals/" + filed.appealId())).body(filed);
  }

  @GetMapping("/{appealId}")
  AppealResponse get(Principal principal, @PathVariable("appealId") String appealId) {
    return AppealResponse.from(
        appeals.findOwn(
            AppealId.of(parseUuid(appealId)),
            AppellantId.of(UUID.fromString(principal.getName()))));
  }

  private static ModerationTargetRef targetOf(SubmitAppealRequest request) {
    String type = request.targetType();
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("targetType must not be blank");
    }
    try {
      return new ModerationTargetRef(
          ModerationTargetType.valueOf(type.trim().toUpperCase(Locale.ROOT)),
          parseUuid(request.targetId()));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown targetType: " + type);
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
