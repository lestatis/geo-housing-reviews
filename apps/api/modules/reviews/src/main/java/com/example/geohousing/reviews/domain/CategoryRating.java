package com.example.geohousing.reviews.domain;

/**
 * A rating for one category of the living experience: either a 1–5 value or explicitly
 * not-applicable — never both, never neither (mirrors the check constraint on {@code
 * reviews.category_rating}). The category set is versioned so historical ratings stay interpretable
 * when definitions evolve (see {@code docs/DOMAIN_MODEL.md}).
 */
public record CategoryRating(
    String category, Integer value, boolean notApplicable, String note, int categorySetVersion) {

  public CategoryRating {
    if (category == null || category.isBlank()) {
      throw new IllegalArgumentException("category must not be blank");
    }
    category = category.trim();
    if (notApplicable == (value != null)) {
      throw new IllegalArgumentException(
          "a category rating is either not applicable or carries a value, never both or neither");
    }
    if (value != null && (value < 1 || value > 5)) {
      throw new IllegalArgumentException("rating value must be between 1 and 5");
    }
    if (categorySetVersion < 1) {
      throw new IllegalArgumentException("categorySetVersion must be positive");
    }
  }

  public static CategoryRating rated(String category, int value, String note) {
    return new CategoryRating(category, value, false, note, 1);
  }

  public static CategoryRating notApplicable(String category, String note) {
    return new CategoryRating(category, null, true, note, 1);
  }
}
