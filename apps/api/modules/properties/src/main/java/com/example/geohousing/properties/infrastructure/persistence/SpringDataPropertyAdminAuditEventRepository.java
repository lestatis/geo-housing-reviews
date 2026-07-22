package com.example.geohousing.properties.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPropertyAdminAuditEventRepository
    extends JpaRepository<PropertyAdminAuditEventJpaEntity, UUID> {}
