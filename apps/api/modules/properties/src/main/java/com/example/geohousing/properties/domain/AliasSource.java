package com.example.geohousing.properties.domain;

/** Where a {@link PropertyAlias} came from — it does not by itself make the alias authoritative. */
public enum AliasSource {
  USER_SUBMITTED,
  IMPORTED,
  OFFICIAL
}
