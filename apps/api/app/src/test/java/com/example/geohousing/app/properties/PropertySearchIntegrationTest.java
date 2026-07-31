package com.example.geohousing.app.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.application.PropertyMatch;
import com.example.geohousing.properties.application.PropertyRepository;
import com.example.geohousing.properties.domain.Coordinates;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Proves the catalogue search against a real PostgreSQL, because everything that makes it work is
 * the database's: trigram scoring, the operand order the GIN indexes can serve, and PostGIS
 * distance.
 */
@Testcontainers
@SpringBootTest
class PropertySearchIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  private static final double BATUMI_LAT = 41.6412;
  private static final double BATUMI_LNG = 41.6300;

  @Autowired private PropertyRepository properties;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID orbi;

  @BeforeEach
  void seed() {
    jdbcTemplate.update("delete from properties.property_alias");
    jdbcTemplate.update("update properties.property set address_id = null");
    jdbcTemplate.update("delete from properties.address");
    jdbcTemplate.update("delete from properties.property");

    UUID chavchavadze = address("Batumi", "Chavchavadze Avenue");
    UUID rustaveli = address("Batumi", "Rustaveli Street");
    orbi = property("Orbi Sea Towers Residence", "ACTIVE", chavchavadze, BATUMI_LAT, BATUMI_LNG);
    alias(orbi, "ka", "ორბი რეზიდენსი");
    property("Alliance Palace", "ACTIVE", rustaveli, 41.6500, 41.6400);
    property("Orbi Draft Tower", "DRAFT", null, null, null);
    property("Orbi Withdrawn Tower", "HIDDEN", null, null, null);
    property("Orbi Merged Tower", "MERGED", null, null, null);
  }

  @Test
  void aFragmentOfTheNameFindsTheBuilding() {
    // The reason this uses word_similarity: plain similarity scores "orbi" against
    // "Orbi Sea Towers Residence" at 0.19, below the 0.3 threshold, and the building a resident is
    // looking for would simply not come back.
    assertThat(names(search("orbi"))).contains("Orbi Sea Towers Residence");
  }

  @Test
  void aTypoStillFindsTheBuilding() {
    assertThat(names(search("orbe"))).contains("Orbi Sea Towers Residence");
  }

  @Test
  void aGeorgianAliasFindsTheBuildingNamedInEnglish() {
    // Trigram is language-agnostic, which is why it was chosen over tsvector: PostgreSQL ships no
    // Georgian full-text configuration (P-002 keeps the Georgian data model ready).
    assertThat(names(search("ორბი"))).contains("Orbi Sea Towers Residence");
  }

  @Test
  void anAddressFragmentFindsTheBuilding() {
    assertThat(names(search("chavchav"))).containsExactly("Orbi Sea Towers Residence");
    assertThat(names(search("rustaveli"))).containsExactly("Alliance Palace");
  }

  @Test
  void aCityNameFindsEverythingInIt() {
    assertThat(names(search("batumi")))
        .containsExactlyInAnyOrder("Orbi Sea Towers Residence", "Alliance Palace");
  }

  @Test
  void aUserContributedPropertyIsFindableStraightAway() {
    // DRAFT is not a moderation queue: this module treats a user-contributed property as publicly
    // readable (PropertyCatalogService.visibilityOf). Excluding drafts would let a resident create
    // a property, review it, and never find it again — including their own.
    assertThat(names(search("orbi"))).contains("Orbi Draft Tower");
  }

  @Test
  void withdrawnAndSupersededPropertiesAreNotFindable() {
    // HIDDEN was taken out by an administrator; MERGED points at the record that survived.
    assertThat(names(search("orbi"))).doesNotContain("Orbi Withdrawn Tower", "Orbi Merged Tower");
  }

  @Test
  void nothingMatchesGibberish() {
    assertThat(search("zzzqqq")).isEmpty();
  }

  @Test
  void aSearchAroundAPointReturnsWhatIsNearbyWithItsDistance() {
    List<PropertyMatch> nearby =
        properties.search(null, Coordinates.of(BATUMI_LAT, BATUMI_LNG), 3000, 20);

    assertThat(names(nearby)).containsExactly("Orbi Sea Towers Residence", "Alliance Palace");
    assertThat(nearby.getFirst().distance()).hasValueSatisfying(d -> assertThat(d).isLessThan(1d));
    assertThat(nearby.getLast().distance())
        .hasValueSatisfying(d -> assertThat(d).isGreaterThan(1000d));
  }

  @Test
  void aTightRadiusExcludesWhatIsOutsideIt() {
    assertThat(properties.search(null, Coordinates.of(BATUMI_LAT, BATUMI_LNG), 500, 20)).hasSize(1);
  }

  @Test
  void aTextSearchWithoutAPointCarriesNoDistance() {
    assertThat(search("orbi").getFirst().distance()).isEmpty();
  }

  @Test
  void theLimitIsRespected() {
    assertThat(properties.search("batumi", null, 0, 1)).hasSize(1);
  }

  @Test
  void theTrigramIndexesCanServeTheQueryRatherThanScanningTheCatalogue() {
    // With four rows the planner will always choose a sequential scan, so the question is whether
    // the index *can* serve this predicate — the operand order of <% decides it, and getting it
    // backwards plans a scan even on a large table.
    // enable_seqscan and the EXPLAIN must share a connection, so both run in one callback.
    String plan =
        jdbcTemplate.execute(
            (java.sql.Connection connection) -> {
              try (java.sql.Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                StringBuilder lines = new StringBuilder();
                try (java.sql.ResultSet rows =
                    statement.executeQuery(
                        "explain (costs off) select id from properties.property"
                            + " where lower('orbi') <% lower(canonical_name)")) {
                  while (rows.next()) {
                    lines.append(rows.getString(1)).append(' ');
                  }
                }
                statement.execute("SET enable_seqscan = on");
                return lines.toString();
              }
            });

    assertThat(plan).contains("property_canonical_name_trgm_idx");
  }

  private List<PropertyMatch> search(String text) {
    return properties.search(text, null, 0, 20);
  }

  private static List<String> names(List<PropertyMatch> matches) {
    return matches.stream().map(PropertyMatch::canonicalName).toList();
  }

  private UUID address(String city, String street) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into properties.address (id, city, street) values (?::uuid, ?, ?)",
        id,
        city,
        street);
    return id;
  }

  private UUID property(String name, String status, UUID addressId, Double lat, Double lng) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into properties.property"
            + " (id, type, status, canonical_name, address_id, latitude, longitude, created_by)"
            + " values (?::uuid, 'BUILDING', ?, ?, ?::uuid, ?, ?, gen_random_uuid())",
        id,
        status,
        name,
        addressId,
        lat,
        lng);
    return id;
  }

  private void alias(UUID propertyId, String locale, String name) {
    jdbcTemplate.update(
        "insert into properties.property_alias (id, property_id, locale, name, source)"
            + " values (gen_random_uuid(), ?::uuid, ?, ?, 'USER')",
        propertyId,
        locale,
        name);
  }
}
