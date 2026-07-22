package com.example.geohousing.reviews.domain;

import java.time.LocalDate;

/**
 * When the author lived in / owned the property. Either bound may be absent (an ongoing stay has no
 * end; some authors only remember roughly), but a period can never end before it starts — mirroring
 * the check constraint on {@code reviews.review}.
 */
public record ResidencePeriod(LocalDate from, LocalDate to) {

  public ResidencePeriod {
    if (from != null && to != null && to.isBefore(from)) {
      throw new IllegalArgumentException("a residence period cannot end before it starts");
    }
  }

  public static ResidencePeriod of(LocalDate from, LocalDate to) {
    return new ResidencePeriod(from, to);
  }
}
