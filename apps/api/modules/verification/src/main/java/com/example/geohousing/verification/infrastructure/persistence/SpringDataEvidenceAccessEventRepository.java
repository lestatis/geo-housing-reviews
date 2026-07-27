package com.example.geohousing.verification.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only evidence access rows; the application only ever inserts. */
interface SpringDataEvidenceAccessEventRepository
    extends JpaRepository<EvidenceAccessEventJpaEntity, UUID> {}
