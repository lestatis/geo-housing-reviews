package com.example.geohousing.app.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.shared.audit.AuditCursor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditPageTokenTest {

  private static final Instant SINCE = Instant.parse("2026-08-01T00:00:00Z");
  private static final Instant UNTIL = Instant.parse("2026-08-05T00:00:00Z");
  private static final UUID MARI = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID TEKLA = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final AuditCursor POSITION =
      new AuditCursor(Instant.parse("2026-08-03T09:00:00Z"), "identity", UUID.randomUUID());

  @Test
  void aTokenSurvivesBeingWrittenDownAndReadBack() {
    String encoded = AuditPageToken.encode(POSITION, query(MARI));

    AuditPageToken read = AuditPageToken.decode(encoded);

    assertThat(read.position()).isEqualTo(POSITION);
    assertThat(read.since()).isEqualTo(SINCE);
    assertThat(read.until()).isEqualTo(UNTIL);
    assertThat(read.actorAccountId()).isEqualTo(MARI);
  }

  @Test
  void aTokenCarriesAnAbsentActorAsAbsentRatherThanAsSomebody() {
    assertThat(AuditPageToken.decode(AuditPageToken.encode(POSITION, query(null))).actorAccountId())
        .isNull();
  }

  @Test
  void anUnreadableTokenIsRefusedRatherThanGuessedAt() {
    // A cursor that silently reset to the newest page would show an administrator the same events
    // again and let them believe they had reached the end of a list they had not.
    assertThatThrownBy(() -> AuditPageToken.decode("not-a-cursor"))
        .isInstanceOf(InvalidAuditQueryException.class);
    assertThatThrownBy(() -> AuditPageToken.decode(""))
        .isInstanceOf(InvalidAuditQueryException.class);
    assertThatThrownBy(() -> AuditPageToken.decode("YWJjfGRlZg"))
        .isInstanceOf(InvalidAuditQueryException.class);
  }

  @Test
  void aTokenIsRefusedWhenTheRequestAsksSomethingElse() {
    // The defect this exists for: a continuation is a continuation *of one query*. Carried onto a
    // different actor or a different window it resumes partway through a timeline the caller has
    // never seen the start of — and, on reaching the end, reports no further pages. An
    // administrator is then told they have seen the whole history when they have seen a fragment.
    AuditPageToken token = AuditPageToken.decode(AuditPageToken.encode(POSITION, query(MARI)));

    assertThatThrownBy(() -> token.verifyContinues(SINCE, UNTIL, TEKLA))
        .isInstanceOf(InvalidAuditQueryException.class);
    assertThatThrownBy(() -> token.verifyContinues(SINCE, UNTIL, null))
        .isInstanceOf(InvalidAuditQueryException.class);
    assertThatThrownBy(() -> token.verifyContinues(SINCE.minusSeconds(1), UNTIL, MARI))
        .isInstanceOf(InvalidAuditQueryException.class);
    assertThatThrownBy(() -> token.verifyContinues(SINCE, UNTIL.plusSeconds(1), MARI))
        .isInstanceOf(InvalidAuditQueryException.class);
  }

  @Test
  void aTokenIsAcceptedWhenTheRequestRepeatsTheQueryItCameFrom() {
    AuditPageToken sameQuery = AuditPageToken.decode(AuditPageToken.encode(POSITION, query(MARI)));
    AuditPageToken everyone = AuditPageToken.decode(AuditPageToken.encode(POSITION, query(null)));

    sameQuery.verifyContinues(SINCE, UNTIL, MARI);
    everyone.verifyContinues(SINCE, UNTIL, null);
  }

  @Test
  void aRequestThatOmitsTheWindowInheritsItFromTheToken() {
    // Nothing to disagree with: the token is the statement of which query is being continued, so
    // repeating its bounds is optional rather than required.
    AuditPageToken token = AuditPageToken.decode(AuditPageToken.encode(POSITION, query(MARI)));

    token.verifyContinues(null, null, MARI);
  }

  private static AuditQuery query(UUID actor) {
    return new AuditQuery(SINCE, UNTIL, null, actor, 50);
  }
}
