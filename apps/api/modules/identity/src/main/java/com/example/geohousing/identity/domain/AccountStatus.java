package com.example.geohousing.identity.domain;

/**
 * Lifecycle status of an account. Intentionally only {@code ACTIVE}/{@code CLOSED}: a restriction
 * is a time-bounded fact modelled by {@link UserRestriction} and computed on demand, not a status
 * flag that could go stale when a restriction expires. Mirrors the {@code status in ('ACTIVE',
 * 'CLOSED')} check constraint on {@code identity.account}.
 */
public enum AccountStatus {
  ACTIVE,
  CLOSED
}
