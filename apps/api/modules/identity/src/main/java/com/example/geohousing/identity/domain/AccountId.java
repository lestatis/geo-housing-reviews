package com.example.geohousing.identity.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque, stable identifier for an {@link Account}. */
public record AccountId(UUID value) {

  public AccountId {
    Objects.requireNonNull(value, "account id value must not be null");
  }

  public static AccountId of(UUID value) {
    return new AccountId(value);
  }
}
