package com.example.geohousing.app.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.app.support.SharedPostgis;
import com.example.geohousing.moderation.application.ModerationCaseRepository;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseId;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModerationTargetType;
import com.example.geohousing.moderation.domain.StaleModerationWriteException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A decision belongs to the state of the case it was made about.
 *
 * <p>Two moderators opening the same case both saw it awaiting a decision, and the second one's
 * write landed on top of the first's without complaint: a second decision row on a case that
 * already had one. The repository re-read the row before writing, so Hibernate's {@code @Version}
 * was checking what that transaction had just loaded rather than what the moderator read — a
 * protection that looked present and covered a different window.
 *
 * <p>Written through the repository port rather than over HTTP, because the point is a caller
 * holding a version the row has since left behind, which is a state two sequential requests cannot
 * produce.
 */
@ExtendWith(SharedPostgis.ResetBeforeClass.class)
@SpringBootTest
class StaleModerationWriteIntegrationTest {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    SharedPostgis.register(registry);
  }

  @Autowired private ModerationCaseRepository cases;

  @Test
  void aCaseDecidedOnAStaleReadIsRefused() {
    ModerationCase opened = openCase();
    cases.create(opened);

    ModerationCase asOneModeratorReadIt = cases.findById(opened.id()).orElseThrow();
    ModerationCase asAnotherReadIt = cases.findById(opened.id()).orElseThrow();

    // The first moderator assigns it, moving the row on.
    asOneModeratorReadIt.assignTo(moderator(), CLOCK);
    cases.save(asOneModeratorReadIt);

    // The second is still holding the version they read before that happened.
    asAnotherReadIt.assignTo(moderator(), CLOCK);
    assertThatThrownBy(() -> cases.save(asAnotherReadIt))
        .as("the second write landed on a case that had already moved on")
        .isInstanceOf(StaleModerationWriteException.class);
  }

  @Test
  void afreshReadCanStillBeSaved() {
    // The guard must refuse staleness, not writing. A moderator who reads and then acts is the
    // ordinary path and has to keep working.
    ModerationCase opened = openCase();
    cases.create(opened);

    ModerationCase fresh = cases.findById(opened.id()).orElseThrow();
    fresh.assignTo(moderator(), CLOCK);
    cases.save(fresh);

    assertThat(cases.findById(opened.id()).orElseThrow().status())
        .isEqualTo(com.example.geohousing.moderation.domain.ModerationCaseStatus.IN_REVIEW);
  }

  private static final java.time.Clock CLOCK = java.time.Clock.systemUTC();

  private static ModerationCase openCase() {
    return ModerationCase.open(
        ModerationCaseId.of(UUID.randomUUID()),
        new ModerationTargetRef(ModerationTargetType.REVIEW, UUID.randomUUID()),
        com.example.geohousing.moderation.domain.CaseTrigger.REPORT,
        com.example.geohousing.moderation.domain.RiskLevel.STANDARD,
        CLOCK);
  }

  private static com.example.geohousing.moderation.domain.ModeratorId moderator() {
    return com.example.geohousing.moderation.domain.ModeratorId.of(UUID.randomUUID());
  }
}
