package com.example.geohousing.properties.domain;

import java.util.Objects;

/**
 * A multilingual name for a property, with its locale, provenance, and an optional confidence in
 * {@code [0, 1]} ({@code null} when unknown). Aliases are how the same building is found under
 * different names/scripts.
 */
public record PropertyAlias(String locale, String name, AliasSource source, Double confidence) {

  public PropertyAlias {
    locale = requireText(locale, "locale");
    name = requireText(name, "name");
    Objects.requireNonNull(source, "source");
    if (confidence != null && (confidence < 0 || confidence > 1)) {
      throw new IllegalArgumentException("confidence must be between 0 and 1");
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
