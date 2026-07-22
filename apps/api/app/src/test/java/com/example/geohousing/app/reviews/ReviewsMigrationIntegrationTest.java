package com.example.geohousing.app.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class ReviewsMigrationIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void reviewMigrationApplied() {
    Integer applied =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '4.1' and success = true",
            Integer.class);
    assertThat(applied).isEqualTo(1);
  }

  @Test
  void reviewTablesExist() {
    for (String table : new String[] {"review", "review_version", "category_rating"}) {
      Integer count =
          jdbcTemplate.queryForObject(
              "select count(*) from information_schema.tables"
                  + " where table_schema = 'reviews' and table_name = ?",
              Integer.class,
              table);
      assertThat(count).as("table reviews.%s exists", table).isEqualTo(1);
    }
  }

  @Test
  void relationshipTypeAndStatusRejectUnknownValues() {
    assertThatThrownBy(
            () -> insertReview(UUID.randomUUID(), UUID.randomUUID(), "LANDLORD", "DRAFT"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> insertReview(UUID.randomUUID(), UUID.randomUUID(), "OWNER", "ARCHIVED"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aResidencePeriodCannotEndBeforeItStarts() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into reviews.review"
                        + " (property_id, author_account_id, relationship_type,"
                        + " residence_from, residence_to)"
                        + " values (?, ?, 'OWNER', date '2026-05-01', date '2026-01-01')",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aPublishedReviewMustRecordWhenItWasPublished() {
    assertThatThrownBy(
            () -> insertReview(UUID.randomUUID(), UUID.randomUUID(), "OWNER", "PUBLISHED"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anAuthorHasOnlyOneLiveReviewPerPropertyButMayStartAgainAfterRemoval() {
    UUID property = UUID.randomUUID();
    UUID author = UUID.randomUUID();
    insertReview(property, author, "CURRENT_RESIDENT", "DRAFT");

    assertThatThrownBy(() -> insertReview(property, author, "OWNER", "DRAFT"))
        .isInstanceOf(DataIntegrityViolationException.class);

    jdbcTemplate.update(
        "update reviews.review set status = 'REMOVED'"
            + " where author_account_id = ? and property_id = ?",
        author,
        property);
    assertThatCode(() -> insertReview(property, author, "OWNER", "DRAFT"))
        .doesNotThrowAnyException();
  }

  @Test
  void aCategoryRatingIsEitherNotApplicableOrHasAValueInRange() {
    UUID versionId = seedReviewVersion();

    assertThatCode(() -> insertRating(versionId, "NOISE", 4, false)).doesNotThrowAnyException();
    assertThatCode(() -> insertRating(versionId, "PARKING", null, true)).doesNotThrowAnyException();

    // not applicable *and* a value, neither of them, and an out-of-range value are all rejected.
    assertThatThrownBy(() -> insertRating(versionId, "LIFT", 3, true))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRating(versionId, "HEATING", null, false))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRating(versionId, "SECURITY", 9, false))
        .isInstanceOf(DataIntegrityViolationException.class);
    // one rating per category per version
    assertThatThrownBy(() -> insertRating(versionId, "NOISE", 2, false))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void versionNumbersAreUniquePerReviewAndBodyMustNotBeBlank() {
    UUID reviewId = seedReview();
    insertVersion(reviewId, 1, "A perfectly fine building", "RECOMMEND");

    assertThatThrownBy(() -> insertVersion(reviewId, 1, "duplicate number", "NEUTRAL"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertVersion(reviewId, 2, "   ", "NEUTRAL"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertVersion(reviewId, 3, "fine", "MAYBE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertReview(UUID property, UUID author, String relationship, String status) {
    jdbcTemplate.update(
        "insert into reviews.review (property_id, author_account_id, relationship_type, status)"
            + " values (?, ?, ?, ?)",
        property,
        author,
        relationship,
        status);
  }

  private UUID seedReview() {
    return jdbcTemplate.queryForObject(
        "insert into reviews.review (property_id, author_account_id, relationship_type)"
            + " values (?, ?, 'OWNER') returning id",
        UUID.class,
        UUID.randomUUID(),
        UUID.randomUUID());
  }

  private UUID seedReviewVersion() {
    UUID reviewId = seedReview();
    return jdbcTemplate.queryForObject(
        "insert into reviews.review_version"
            + " (review_id, version_number, locale, body, recommendation)"
            + " values (?, 1, 'ka', 'კარგი შენობა', 'RECOMMEND') returning id",
        UUID.class,
        reviewId);
  }

  private void insertVersion(UUID reviewId, int number, String body, String recommendation) {
    jdbcTemplate.update(
        "insert into reviews.review_version"
            + " (review_id, version_number, locale, body, recommendation)"
            + " values (?, ?, 'en', ?, ?)",
        reviewId,
        number,
        body,
        recommendation);
  }

  private void insertRating(UUID versionId, String category, Integer value, boolean notApplicable) {
    jdbcTemplate.update(
        "insert into reviews.category_rating"
            + " (review_version_id, category, value, not_applicable) values (?, ?, ?, ?)",
        versionId,
        category,
        value,
        notApplicable);
  }
}
