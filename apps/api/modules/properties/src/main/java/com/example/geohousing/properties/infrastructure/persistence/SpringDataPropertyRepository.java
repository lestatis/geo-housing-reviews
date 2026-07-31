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

  /**
   * Ranked search over the catalogue.
   *
   * <p>Uses {@code word_similarity} (the {@code <%} operator), not plain {@code similarity}.
   * Similarity compares whole strings, so "orbi" against "Orbi Sea Towers Residence" scores 0.19 —
   * below the 0.3 default threshold — and the building a resident is looking for simply does not
   * come back. Word similarity asks whether the query appears as an extent within the text, which
   * is what someone typing a fragment means, and scores that same pair 1.0.
   *
   * <p>The query is on the left of {@code <%} on purpose: that is the operand order the GIN trigram
   * indexes from V3.4 can serve. Verified with EXPLAIN — the reverse order plans a sequential scan.
   *
   * <p>Only ACTIVE rows are searchable. DRAFT is awaiting an administrator, HIDDEN was withdrawn,
   * and MERGED already points elsewhere; returning any of them would either leak a queue or send
   * someone to a dead record.
   *
   * <p>Native SQL for the same reason as the duplicate finder (ADR-0007): PostGIS distance without
   * a hibernate-spatial dependency.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT p.id AS id,
                 p.canonical_name AS canonicalName,
                 GREATEST(
                   word_similarity(lower(:text), lower(p.canonical_name)),
                   COALESCE((SELECT MAX(word_similarity(lower(:text), lower(a.name)))
                             FROM properties.property_alias a
                             WHERE a.property_id = p.id), 0),
                   COALESCE(word_similarity(lower(:text), lower(addr.street)), 0),
                   COALESCE(word_similarity(lower(:text), lower(addr.city)), 0)
                 ) AS score,
                 CASE WHEN :hasPoint AND p.geo IS NOT NULL
                      THEN ST_Distance(p.geo,
                             ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
                 END AS distanceMeters
          FROM properties.property p
          LEFT JOIN properties.address addr ON addr.id = p.address_id
          WHERE p.status = 'ACTIVE'
            AND (NOT :hasText
                 OR lower(:text) <% lower(p.canonical_name)
                 OR lower(:text) <% lower(addr.street)
                 OR lower(:text) <% lower(addr.city)
                 OR EXISTS (SELECT 1 FROM properties.property_alias a
                            WHERE a.property_id = p.id
                              AND lower(:text) <% lower(a.name)))
            AND (NOT :hasPoint
                 OR (p.geo IS NOT NULL
                     AND ST_DWithin(p.geo,
                           ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                           :radiusMeters)))
          ORDER BY score DESC, distanceMeters ASC NULLS LAST, p.canonical_name
          LIMIT :maxResults
          """)
  List<PropertySearchProjection> search(
      @Param("hasText") boolean hasText,
      @Param("text") String text,
      @Param("hasPoint") boolean hasPoint,
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("radiusMeters") double radiusMeters,
      @Param("maxResults") int maxResults);
}
