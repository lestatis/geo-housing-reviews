package com.example.geohousing.identity.domain;

/**
 * Authorization role for an account. Resolved server-side from our own store, never from a token
 * claim (see ADR-0005). Mirrors the {@code role in ('USER', 'ADMIN')} check constraint on {@code
 * identity.account}.
 */
public enum AccountRole {
  USER,
  ADMIN
}
