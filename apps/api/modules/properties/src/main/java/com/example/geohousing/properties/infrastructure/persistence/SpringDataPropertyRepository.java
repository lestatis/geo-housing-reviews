package com.example.geohousing.properties.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPropertyRepository extends JpaRepository<PropertyJpaEntity, UUID> {

  /** Newest-first page of properties. {@code Limit} keeps the bound in the query, not in memory. */
  @Query("select p from PropertyJpaEntity p order by p.createdAt desc, p.id desc")
  List<PropertyJpaEntity> findRecent(Limit limit);

  /**
   * Deterministic duplicate candidates: a property whose normalized canonical name equals the given
   * name, or (when a point is supplied) whose generated {@code geo} point is within {@code
   * radiusMeters}. Merged properties are excluded (they already point elsewhere). Native SQL by
   * design (ADR-0007) — PostGIS proximity without a hibernate-spatial dependency.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT p.id AS id, p.canonical_name AS canonicalName
          FROM properties.property p
          WHERE p.status <> 'MERGED'
            AND (
              lower(btrim(p.canonical_name)) = lower(btrim(:name))
              OR (
                :hasPoint
                AND p.geo IS NOT NULL
                AND ST_DWithin(
                      p.geo,
                      ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                      :radiusMeters)
              )
            )
          ORDER BY p.canonical_name
          LIMIT :maxResults
          """)
  List<PropertyCandidateProjection> findDuplicateCandidates(
      @Param("name") String name,
      @Param("hasPoint") boolean hasPoint,
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("radiusMeters") double radiusMeters,
      @Param("maxResults") int maxResults);
}
