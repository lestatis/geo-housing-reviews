package com.example.geohousing.identity.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAdminAuditEventRepository
    extends JpaRepository<AdminAuditEventJpaEntity, UUID> {}
