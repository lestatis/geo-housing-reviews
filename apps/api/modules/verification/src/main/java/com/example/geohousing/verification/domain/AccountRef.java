package com.example.geohousing.verification.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The account being verified, held as an opaque identifier. The account belongs to the identity
 * module; this module never reads its tables.
 */
public record AccountRef(UUID value) {

  public AccountRef {
    Objects.requireNonNull(value, "account reference must not be null");
  }

  public static AccountRef of(UUID value) {
    return new AccountRef(value);
  }
}
