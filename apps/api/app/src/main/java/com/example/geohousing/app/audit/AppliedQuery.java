package com.example.geohousing.app.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The query this page actually answered.
 *
 * <p>A continuation inherits its window and filter from the cursor, so a caller that sent only a
 * cursor never stated either. Without this they would have to guess, and a screen that guessed
 * would label the page with the question it asked rather than the one that was answered — which,
 * for an audit log, is how somebody concludes nothing happened.
 *
 * <p>Grouped rather than three more fields beside {@code items}: the response was already at three,
 * and "what came back" and "what was asked" are different things that should not sit in one flat
 * list.
 *
 * @param since inclusive
 * @param until exclusive
 * @param actorAccountId whose actions, or null for everyone's
 */
public record AppliedQuery(
    Instant since, Instant until, @Schema(nullable = true) String actorAccountId) {}
