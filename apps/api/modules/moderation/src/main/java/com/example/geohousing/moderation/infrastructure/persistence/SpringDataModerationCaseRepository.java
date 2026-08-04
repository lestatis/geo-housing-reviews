package com.example.geohousing.moderation.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataModerationCaseRepository extends JpaRepository<ModerationCaseJpaEntity, UUID> {

  /** Mirrors the partial unique index: CLOSED is the only status that frees the target's slot. */
  Optional<ModerationCaseJpaEntity> findByTargetTypeAndTargetIdAndStatusNot(
      String targetType, UUID targetId, String closedStatus);

  List<ModerationCaseJpaEntity> findByStatusNotOrderByOpenedAtAsc(String closedStatus);

  /**
   * How many cases are still open. A count in the database rather than a list counted here: a
   * backlog worth measuring is a backlog too big to load.
   */
  long countByStatusIn(List<String> statuses);

  /**
   * When the oldest still-open case was opened, or null if none is open.
   *
   * <p>The number that says whether the queue is being *served* rather than merely worked — ten
   * waiting is fine if none has been waiting a fortnight.
   */
  @Query("select min(c.openedAt) from ModerationCaseJpaEntity c where c.status in :statuses")
  Instant earliestOpenedAtAmong(@Param("statuses") List<String> statuses);
}
