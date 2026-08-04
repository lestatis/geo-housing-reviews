package com.example.geohousing.app.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.shared.audit.AuditEntry;
import com.example.geohousing.shared.audit.AuditTrail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditTimelineServiceTest {

  private static final Instant NOON = Instant.parse("2026-08-04T12:00:00Z");
  private static final UUID MARI = UUID.randomUUID();
  private static final UUID TEKLA = UUID.randomUUID();

  private static final List<UUID> reads = new java.util.ArrayList<>();

  @Test
  void aTimelineIsEveryModulesEntriesInOneOrder() {
    // The point of the whole feature: five append-only tables an administrator could not read
    // become one answer to "what has been done, and when".
    AuditTimelineService service =
        serviceOver(
            trail(entry(NOON.minusSeconds(30), MARI, "identity", "GRANT_ADMIN")),
            trail(entry(NOON, TEKLA, "properties", "HIDE")),
            trail(entry(NOON.minusSeconds(60), MARI, "reviews", "REMOVE")));

    List<AuditEntry> timeline = service.recorded(query(null, 10), MARI);

    assertThat(timeline)
        .extracting(AuditEntry::module)
        .containsExactly("properties", "identity", "reviews");
  }

  @Test
  void theNewestEntriesSurviveTheLimitRatherThanWhicheverModuleAnsweredFirst() {
    // Each trail is asked for `limit` and the merge trims again. Trimming before sorting would
    // return the newest of module one and drop something newer from module two.
    AuditTimelineService service =
        serviceOver(
            trail(
                entry(NOON.minusSeconds(10), MARI, "identity", "A"),
                entry(NOON.minusSeconds(20), MARI, "identity", "B")),
            trail(entry(NOON, TEKLA, "properties", "C")));

    assertThat(service.recorded(query(null, 2), MARI))
        .extracting(AuditEntry::action)
        .containsExactly("C", "A");
  }

  @Test
  void anActorFilterFollowsThatPersonAcrossModules() {
    // "What has this moderator been doing" is the access-review question, and it is only answerable
    // if the filter reaches every module rather than being applied after the merge.
    AuditTimelineService service =
        serviceOver(
            filteringTrail(entry(NOON, MARI, "identity", "GRANT_ADMIN")),
            filteringTrail(entry(NOON.minusSeconds(5), TEKLA, "properties", "HIDE")));

    assertThat(service.recorded(query(MARI, 10), MARI))
        .extracting(AuditEntry::action)
        .containsExactly("GRANT_ADMIN");
  }

  @Test
  void aModuleThatRecordedNothingContributesNothingRatherThanFailing() {
    AuditTimelineService service =
        serviceOver(trail(), trail(entry(NOON, MARI, "properties", "HIDE")), trail());

    assertThat(service.recorded(query(null, 10), MARI)).hasSize(1);
  }

  @Test
  void aLimitIsClampedSoNobodyPullsTheWholeHistory() {
    assertThat(query(null, 0).limit()).isEqualTo(AuditQuery.DEFAULT_LIMIT);
    assertThat(query(null, -1).limit()).isEqualTo(AuditQuery.DEFAULT_LIMIT);
    assertThat(query(null, 5).limit()).isEqualTo(5);
    assertThat(query(null, 10_000).limit()).isEqualTo(AuditQuery.MAX_LIMIT);
  }

  @Test
  void awindowThatEndsBeforeItStartsIsRefusedRatherThanReturningNothing() {
    // Silently returning an empty timeline would read as "nothing happened", which is the one
    // answer an audit log must never give by accident.
    assertThatThrownBy(() -> new AuditQuery(NOON, NOON.minusSeconds(1), null, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static AuditQuery query(UUID actor, int limit) {
    return new AuditQuery(NOON.minusSeconds(3600), NOON.plusSeconds(3600), actor, limit);
  }

  private static AuditEntry entry(Instant at, UUID actor, String module, String action) {
    return new AuditEntry(at, actor, module, action, "THING", "id", "APPLIED", null);
  }

  /**
   * Answers with everything it holds, ignoring the actor filter — most trails are asked to filter
   * in SQL, and this proves the merge does not depend on them having done so for ordering.
   */
  private static AuditTrail trail(AuditEntry... entries) {
    return (from, until, actor, limit) -> List.of(entries);
  }

  /** Filters like a real adapter's query does. */
  private static AuditTrail filteringTrail(AuditEntry... entries) {
    return (from, until, actor, limit) ->
        java.util.Arrays.stream(entries)
            .filter(e -> actor == null || e.actorAccountId().filter(actor::equals).isPresent())
            .toList();
  }

  private static AuditTimelineService serviceOver(AuditTrail... sources) {
    return new AuditTimelineService(List.of(sources), reads::add);
  }

  @Test
  void readingTheTimelineIsItselfRecorded() {
    // The log answers "who has been looking at what". An access review that cannot see its own
    // reviewers is half a control.
    reads.clear();
    AuditTimelineService service =
        serviceOver(trail(entry(NOON, MARI, "identity", "VIEW_ACCOUNT")));

    service.recorded(query(null, 10), TEKLA);

    assertThat(reads).containsExactly(TEKLA);
  }

  @Test
  void aTimelineThatCouldNotBeRecordedIsNotReturned() {
    AuditTimelineService failing =
        new AuditTimelineService(
            List.of(trail(entry(NOON, MARI, "identity", "VIEW_ACCOUNT"))),
            adminAccountId -> {
              throw new IllegalStateException("audit store unavailable");
            });

    assertThatThrownBy(() -> failing.recorded(query(null, 10), TEKLA))
        .isInstanceOf(IllegalStateException.class);
  }
}
