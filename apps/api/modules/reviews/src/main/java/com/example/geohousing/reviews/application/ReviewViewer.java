package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.AuthorId;
import java.util.Objects;
import java.util.Optional;

/**
 * Who is asking to see a review. Reviews are largely public, so the anonymous viewer is a normal
 * case rather than an error; the account and the moderator flag only widen what is visible.
 *
 * <p>The moderator flag is decided by the web layer from the caller's role — the application layer
 * is told the answer rather than resolving roles itself.
 */
public record ReviewViewer(AuthorId accountId, boolean moderator) {

  private static final ReviewViewer ANONYMOUS = new ReviewViewer(null, false);

  public static ReviewViewer anonymous() {
    return ANONYMOUS;
  }

  public static ReviewViewer user(AuthorId accountId) {
    return new ReviewViewer(Objects.requireNonNull(accountId, "accountId"), false);
  }

  public static ReviewViewer moderator(AuthorId accountId) {
    return new ReviewViewer(Objects.requireNonNull(accountId, "accountId"), true);
  }

  public Optional<AuthorId> account() {
    return Optional.ofNullable(accountId);
  }

  public boolean is(AuthorId authorId) {
    return accountId != null && accountId.equals(authorId);
  }
}
