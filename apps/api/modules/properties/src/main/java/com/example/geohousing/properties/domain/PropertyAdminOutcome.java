package com.example.geohousing.properties.domain;

/**
 * Whether an audited admin action took effect. Rejected attempts (version conflict, illegal
 * transition) are not recorded yet — the mutation is rolled back and an error is returned instead.
 */
public enum PropertyAdminOutcome {
  APPLIED,
  NOT_FOUND
}
