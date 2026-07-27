package com.example.geohousing.verification.domain;

/**
 * What was done to an evidence object. Mirrors the {@code action} check on {@code
 * verification.verification_evidence_access_event}: a moderator {@code READ} always records who; a
 * {@code DELETE} by the retention sweep has no human actor.
 */
public enum EvidenceAccessAction {
  READ,
  DELETE
}
