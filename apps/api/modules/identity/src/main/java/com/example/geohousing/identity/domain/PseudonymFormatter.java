package com.example.geohousing.identity.domain;

/**
 * Builds default pseudonyms of the form {@code Reviewer-<suffix>}. Pure: it does not check
 * uniqueness (that needs a repository and belongs in the application layer) and does not generate
 * randomness itself (the caller supplies the suffix), which keeps it deterministic and trivially
 * testable.
 */
public final class PseudonymFormatter {

  public static final String DEFAULT_PREFIX = "Reviewer-";

  private PseudonymFormatter() {}

  /**
   * Produces a default pseudonym from a caller-supplied suffix (e.g. a short random hex string).
   *
   * @throws InvalidPseudonymException if the resulting value violates {@link Pseudonym}'s rules
   */
  public static Pseudonym defaultFrom(String suffix) {
    if (suffix == null || suffix.isBlank()) {
      throw new InvalidPseudonymException("default pseudonym suffix must not be blank");
    }
    return new Pseudonym(DEFAULT_PREFIX + suffix);
  }
}
