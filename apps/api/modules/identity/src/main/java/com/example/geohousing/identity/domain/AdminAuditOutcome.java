package com.example.geohousing.identity.domain;

/**
 * How the audited admin action ended.
 *
 * <p>{@code REFUSED} is recorded, not swallowed: an attempt to demote the last administrator, or to
 * change one's own role, is exactly the kind of thing worth being able to look back at.
 */
public enum AdminAuditOutcome {
  FOUND,
  NOT_FOUND,
  APPLIED,
  REFUSED
}
