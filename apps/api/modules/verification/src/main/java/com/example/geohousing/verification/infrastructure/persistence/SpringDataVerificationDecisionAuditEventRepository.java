package com.example.geohousing.verification.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only verification-decision audit rows; the application only ever inserts. */
interface SpringDataVerificationDecisionAuditEventRepository
    extends JpaRepository<VerificationDecisionAuditEventJpaEntity, UUID> {}
