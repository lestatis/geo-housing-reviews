package com.example.geohousing.app.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.shared.audit.AuditCursor;
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

    List<AuditEntry> timeline = service.recorded(query(null, 10), MARI).entries();

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

    assertThat(service.recorded(query(null, 2), MARI).entries())
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

    assertThat(service.recorded(query(MARI, 10), MARI).entries())
        .extracting(AuditEntry::action)
        .containsExactly("GRANT_ADMIN");
  }

  @Test
  void aModuleThatRecordedNothingContributesNothingRatherThanFailing() {
    AuditTimelineService service =
        serviceOver(trail(), trail(entry(NOON, MARI, "properties", "HIDE")), trail());

    assertThat(service.recorded(query(null, 10), MARI).entries()).hasSize(1);
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
    assertThatThrownBy(() -> new AuditQuery(NOON, NOON.minusSeconds(1), null, null, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static AuditQuery query(UUID actor, int limit) {
    return query(actor, limit, null);
  }

  private static AuditQuery query(UUID actor, int limit, AuditCursor from) {
    return new AuditQuery(NOON.minusSeconds(3600), NOON.plusSeconds(3600), from, actor, limit);
  }

  private static AuditEntry entry(Instant at, UUID actor, String module, String action) {
    return entry(UUID.randomUUID(), at, actor, module, action);
  }

  private static AuditEntry entry(UUID id, Instant at, UUID actor, String module, String action) {
    return new AuditEntry(id, at, actor, module, action, "THING", "id", "APPLIED", null);
  }

  /**
   * Answers like an adapter's SQL does: everything strictly after the cursor's position in this
   * module, newest first, no more than the limit. Ignores the actor filter, which most trails apply
   * in SQL — that this one does not is what proves the merge does not depend on it for ordering.
   */
  private static AuditTrail trail(AuditEntry... entries) {
    return (from, before, actor, limit) -> page(List.of(entries), before, limit);
  }

  /** Filters like a real adapter's query does. */
  private static AuditTrail filteringTrail(AuditEntry... entries) {
    return (from, before, actor, limit) ->
        page(
            java.util.Arrays.stream(entries)
                .filter(e -> actor == null || e.actorAccountId().filter(actor::equals).isPresent())
                .toList(),
            before,
            limit);
  }

  /** The keyset predicate every adapter pushes into SQL, in Java, over a held list. */
  private static List<AuditEntry> page(List<AuditEntry> held, AuditCursor before, int limit) {
    return held.stream()
        .filter(
            e -> {
              UUID bound = before.idBoundFor(e.module());
              return e.at().isBefore(before.at())
                  || (e.at().equals(before.at())
                      && AuditCursor.ID_ORDER.compare(e.id(), bound) < 0);
            })
        .sorted(AuditEntry.NEWEST_FIRST)
        .limit(limit)
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

  @Test
  void aFullPageSaysWhereToContinueFrom() {
    // API_GUIDELINES.md requires cursor pagination and stable sort semantics for audit events. A
    // page that fills up and says nothing about the rest is a timeline that ends wherever the limit
    // happened to fall, which reads as "that is all that happened".
    AuditTimelineService service =
        serviceOver(
            trail(
                entry(NOON, MARI, "identity", "A"),
                entry(NOON.minusSeconds(10), MARI, "identity", "B")),
            trail(entry(NOON.minusSeconds(20), TEKLA, "properties", "C")));

    AuditPage page = service.recorded(query(null, 2), MARI);

    assertThat(page.entries()).extracting(AuditEntry::action).containsExactly("A", "B");
    assertThat(page.nextCursor()).isNotNull();
    assertThat(page.nextCursor().at()).isEqualTo(NOON.minusSeconds(10));
    assertThat(page.nextCursor().module()).isEqualTo("identity");
  }

  @Test
  void theLastPageOffersNoCursorEvenWhenItFillsExactly() {
    // Exactly `limit` entries and nothing behind them. Offering a cursor here would send a reader
    // to an empty page and leave them unsure whether the timeline had ended or the query had
    // failed.
    AuditTimelineService service =
        serviceOver(
            trail(entry(NOON, MARI, "identity", "A")),
            trail(entry(NOON.minusSeconds(10), TEKLA, "properties", "B")));

    assertThat(service.recorded(query(null, 2), MARI).nextCursor()).isNull();
  }

  @Test
  void continuingFromACursorResumesAfterItRatherThanRepeatingIt() {
    AuditTimelineService service =
        serviceOver(
            trail(
                entry(NOON, MARI, "identity", "A"),
                entry(NOON.minusSeconds(10), MARI, "identity", "B")),
            trail(entry(NOON.minusSeconds(20), TEKLA, "properties", "C")));

    AuditPage first = service.recorded(query(null, 2), MARI);
    AuditPage second = service.recorded(query(null, 2, first.nextCursor()), MARI);

    assertThat(second.entries()).extracting(AuditEntry::action).containsExactly("C");
    assertThat(second.nextCursor()).isNull();
  }

  @Test
  void twoModulesActingInTheSameMillisecondAreEachShownExactlyOnce() {
    // The reason a cursor carries a module and an id rather than only an instant. With a timestamp
    // alone, a page ending mid-millisecond either repeats that instant or skips the rest of it —
    // and skipping is how an audit log loses the row somebody is looking for.
    UUID earlierId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID laterId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    AuditTimelineService service =
        serviceOver(
            trail(entry(laterId, NOON, MARI, "identity", "GRANT_ADMIN")),
            trail(entry(earlierId, NOON, TEKLA, "properties", "HIDE")));

    List<String> walked = new java.util.ArrayList<>();
    AuditCursor cursor = null;
    for (int page = 0; page < 5; page++) {
      AuditPage read = service.recorded(query(null, 1, cursor), MARI);
      read.entries().forEach(e -> walked.add(e.action()));
      cursor = read.nextCursor();
      if (cursor == null) {
        break;
      }
    }

    assertThat(walked).containsExactly("GRANT_ADMIN", "HIDE");
  }

  @Test
  void aCursorFromOutsideTheWindowIsRefused() {
    // A cursor names a position, not a window. Pairing one with a window it never came from would
    // answer a question nobody asked, so it is refused rather than quietly reinterpreted.
    assertThatThrownBy(
            () -> query(null, 10, new AuditCursor(NOON.plusSeconds(7200), "identity", MARI)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
