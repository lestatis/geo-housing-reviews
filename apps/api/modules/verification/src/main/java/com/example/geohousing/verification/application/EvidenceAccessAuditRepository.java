package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.EvidenceAccessEvent;

/**
 * Append-only sink for the evidence access audit. Separate from {@link EvidenceRepository} because
 * it is written on a <em>read</em> path too: opening evidence changes no metadata but must still
 * leave a record.
 */
public interface EvidenceAccessAuditRepository {

  void record(EvidenceAccessEvent event);
}
