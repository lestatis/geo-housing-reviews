package com.example.geohousing.identity.domain;

import java.util.regex.Pattern;

/**
 * Public display name for an account. Never reveals the account's email or legal identity (see
 * {@code docs/DOMAIN_MODEL.md} PublicProfile). Length and charset match the {@code
 * identity.public_profile.pseudonym VARCHAR(32)} column.
 */
public record Pseudonym(String value) {

  public static final int MIN_LENGTH = 3;
  public static final int MAX_LENGTH = 32;

  private static final Pattern ALLOWED =
      Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9 _-]*[A-Za-z0-9])?$");

  public Pseudonym {
    if (value == null) {
      throw new InvalidPseudonymException("pseudonym must not be null");
    }
    if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
      throw new InvalidPseudonymException(
          "pseudonym length must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
    }
    if (!ALLOWED.matcher(value).matches()) {
      throw new InvalidPseudonymException(
          "pseudonym may contain only letters, digits, spaces, hyphens and underscores,"
              + " and must start and end with a letter or digit");
    }
  }

  public static Pseudonym of(String value) {
    return new Pseudonym(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
