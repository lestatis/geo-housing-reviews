package com.example.geohousing.app.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.moderation.application.DuplicateReportException;
import com.example.geohousing.moderation.application.ModerationCaseRepository;
import com.example.geohousing.moderation.application.ModerationCaseService;
import com.example.geohousing.moderation.application.ModerationDecisionRepository;
import com.example.geohousing.moderation.application.ModerationTargetNotFoundException;
import com.example.geohousing.moderation.application.ReportIntakeService;
import com.example.geohousing.moderation.application.ReportRepository;
import com.example.geohousing.moderation.application.SelfReportNotAllowedException;
import com.example.geohousing.moderation.domain.DecisionAction;
import com.example.geohousing.moderation.domain.ModerationCase;
import com.example.geohousing.moderation.domain.ModerationCaseStatus;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModeratorId;
import com.example.geohousing.moderation.domain.ReasonCode;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReportStatus;
import com.example.geohousing.moderation.domain.ReporterId;
import com.example.geohousing.reviews.api.ModeratableReview;
import com.example.geohousing.reviews.api.ReviewModerationGateway;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The whole of MVP loop 4 running for the first time: a report opens a case, a moderator works it,
 * and the decision actually removes the review.
 *
 * <p>Chunk 4 could only drive the moderation ports because the module had no repositories. This is
 * the proof that the report → case → decision → effect chain holds with real persistence and a real
 * cross-module call.
 */
@Testcontainers
@SpringBootTest
class ModerationFlowIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private ReportIntakeService intake;
  @Autowired private ModerationCaseService caseService;
  @Autowired private ModerationCaseRepository cases;
  @Autowired private ReportRepository reports;
  @Autowired private ModerationDecisionRepository decisions;
  @Autowired private ReviewModerationGateway reviews;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void aReportedReviewIsRemovedAndDisappearsFromWhatReadersSee() {
    ModerationTargetRef target = publishedReview();
    ModeratorId moderator = ModeratorId.of(UUID.randomUUID());

    Report report =
        intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, "It names my neighbour.");
    ModerationCase opened = cases.findById(report.caseId().orElseThrow()).orElseThrow();
    caseService.assign(opened.id(), moderator);
    caseService.decide(
        opened.id(),
        moderator,
        DecisionAction.REMOVE,
        ReasonCode.of("DOXXING"),
        "Your review identified a neighbour.",
        "reporter unrelated to the author");

    // The review is genuinely gone from what a reader can see, not merely marked in moderation.
    assertThat(reviews.find(target.id()).map(ModeratableReview::published)).contains(false);
    assertThat(reviewStatus(target.id())).isEqualTo("REMOVED");

    // And every part of the trail exists: the case is decided, the report closed out, the decision
    // recorded with its explanation, and reviews wrote its own audit row for the effect.
    assertThat(cases.findById(opened.id()).orElseThrow().status())
        .isEqualTo(ModerationCaseStatus.DECIDED);
    assertThat(reports.findByCase(opened.id()))
        .singleElement()
        .satisfies(r -> assertThat(r.status()).isEqualTo(ReportStatus.RESOLVED));
    assertThat(decisions.findByCase(opened.id()))
        .singleElement()
        .satisfies(
            d -> assertThat(d.publicExplanation()).contains("Your review identified a neighbour."));
    assertThat(reviewAuditRows(target.id())).isPositive();
  }

  @Test
  void severalReportersConvergeOnOneCaseAndAllAreClosedOutTogether() {
    ModerationTargetRef target = publishedReview();
    ModeratorId moderator = ModeratorId.of(UUID.randomUUID());

    List<Report> filed =
        List.of(
            intake.file(reporter(), target, ReportCategory.PERSONAL_DATA, null),
            intake.file(reporter(), target, ReportCategory.HARASSMENT_OR_THREAT, null),
            intake.file(reporter(), target, ReportCategory.DUPLICATE_OR_SPAM, null));

    assertThat(filed)
        .extracting(r -> r.caseId().orElseThrow())
        .containsOnly(filed.getFirst().caseId().orElseThrow());

    caseService.assign(filed.getFirst().caseId().orElseThrow(), moderator);
    caseService.decide(
        filed.getFirst().caseId().orElseThrow(),
        moderator,
        DecisionAction.APPROVE,
        ReasonCode.of("CLEAN"),
        null,
        null);

    // Heard and not upheld: the review stays up and every reporter's concern is dismissed, not
    // recorded as resolved.
    assertThat(reviewStatus(target.id())).isEqualTo("PUBLISHED");
    assertThat(reports.findByCase(filed.getFirst().caseId().orElseThrow()))
        .hasSize(3)
        .allSatisfy(r -> assertThat(r.status()).isEqualTo(ReportStatus.DISMISSED));
  }

  @Test
  void anAuthorCannotReportTheirOwnReviewAndLeavesNoCaseBehind() {
    ModerationTargetRef target = publishedReview();
    UUID author = reviews.find(target.id()).orElseThrow().authorAccountId();

    assertThatThrownBy(
            () ->
                intake.file(
                    ReporterId.of(author), target, ReportCategory.FALSE_OR_MISLEADING, null))
        .isInstanceOf(SelfReportNotAllowedException.class);

    assertThat(cases.findLiveByTarget(target)).isEmpty();
  }

  @Test
  void oneAccountCannotReportTheSameReviewTwice() {
    ModerationTargetRef target = publishedReview();
    ReporterId reporter = reporter();
    intake.file(reporter, target, ReportCategory.PERSONAL_DATA, null);

    assertThatThrownBy(
            () -> intake.file(reporter, target, ReportCategory.HARASSMENT_OR_THREAT, null))
        .isInstanceOf(DuplicateReportException.class);
  }

  @Test
  void aReportAboutAReviewThatDoesNotExistIsRefused() {
    assertThatThrownBy(
            () ->
                intake.file(
                    reporter(),
                    ModerationTargetRef.review(UUID.randomUUID()),
                    ReportCategory.PERSONAL_DATA,
                    null))
        .isInstanceOf(ModerationTargetNotFoundException.class);
  }

  /**
   * Creates a published review directly, since this test is about moderation rather than authoring.
   * The content row is not optional: a published review without one fails the aggregate's own
   * invariant when it is loaded back.
   */
  private ModerationTargetRef publishedReview() {
    UUID reviewId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into reviews.review"
            + " (id, property_id, author_account_id, relationship_type, status, published_at,"
            + "  created_at, updated_at, version)"
            + " values (?::uuid, ?::uuid, ?::uuid, 'CURRENT_RESIDENT', 'PUBLISHED', now(),"
            + "  now(), now(), 0)",
        reviewId,
        UUID.randomUUID(),
        UUID.randomUUID());
    jdbcTemplate.update(
        "insert into reviews.review_version"
            + " (id, review_id, version_number, locale, body, recommendation, created_at)"
            + " values (?::uuid, ?::uuid, 1, 'ka', 'მეზობლის ბინის ნომერი', 'NEUTRAL', now())",
        versionId,
        reviewId);
    jdbcTemplate.update(
        "update reviews.review set current_version_id = ?::uuid where id = ?::uuid",
        versionId,
        reviewId);
    return ModerationTargetRef.review(reviewId);
  }

  private String reviewStatus(UUID reviewId) {
    return jdbcTemplate.queryForObject(
        "select status from reviews.review where id = ?::uuid", String.class, reviewId);
  }

  private int reviewAuditRows(UUID reviewId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from reviews.review_moderation_audit_event where review_id = ?::uuid",
            Integer.class,
            reviewId);
    return count == null ? 0 : count;
  }

  private static ReporterId reporter() {
    return ReporterId.of(UUID.randomUUID());
  }
}
