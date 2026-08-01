package com.example.geohousing.identity.domain;

/** The kind of admin action recorded in the audit trail. Only account lookup exists for the MVP. */
public enum AdminAuditAction {
  VIEW_ACCOUNT,
  /**
   * Administrative access granted. The audit log is the only record of how someone became
   * privileged.
   */
  GRANT_ADMIN,
  /** Administrative access removed. */
  REVOKE_ADMIN
}
