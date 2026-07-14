package com.example.geohousing.identity.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPublicProfileRepository
    extends JpaRepository<PublicProfileJpaEntity, UUID> {

  boolean existsByPseudonym(String pseudonym);
}
