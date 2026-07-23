package com.example.geohousing.verification.domain;

/**
 * Lifecycle status of a verification case. Mirrors the {@code status} check constraint on {@code
 * verification.verification_case}.
 *
 * <p>{@code PENDING} and {@code APPROVED} are "live" — they hold the one-case-per-account-property
 * slot. {@code REJECTED}, {@code EXPIRED} and {@code CANCELLED} are terminal and free it, so the
 * account can try again.
 */
public enum VerificationStatus {
  PENDING,
  APPROVED,
  REJECTED,
  EXPIRED,
  CANCELLED;

  public boolean isLive() {
    return this == PENDING || this == APPROVED;
  }

  public boolean isTerminal() {
    return !isLive();
  }
}
