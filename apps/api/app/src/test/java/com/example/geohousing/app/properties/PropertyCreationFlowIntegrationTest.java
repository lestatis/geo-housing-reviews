package com.example.geohousing.app.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.application.CreatePropertyCommand;
import com.example.geohousing.properties.application.PropertyCreationResult;
import com.example.geohousing.properties.application.PropertyCreationService;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.PropertyType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves chunks 3+4+5 compose: the wired {@link PropertyCreationService} uses the real PostGIS
 * finder, so a second same-named create is stopped with duplicates and only proceeds when allowed.
 */
@Testcontainers
@SpringBootTest
class PropertyCreationFlowIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private PropertyCreationService creationService;

  @Test
  void surfacesDuplicatesOnASecondCreateThenCreatesWhenAllowed() {
    String name = "Flow Tower " + UUID.randomUUID();
    CreatorId creator = CreatorId.of(UUID.randomUUID());

    PropertyCreationResult first =
        creationService.create(
            new CreatePropertyCommand(PropertyType.BUILDING, name, creator, null, null, false));
    assertThat(first).isInstanceOf(PropertyCreationResult.Created.class);

    PropertyCreationResult second =
        creationService.create(
            new CreatePropertyCommand(PropertyType.BUILDING, name, creator, null, null, false));
    assertThat(second).isInstanceOf(PropertyCreationResult.DuplicatesFound.class);
    assertThat(((PropertyCreationResult.DuplicatesFound) second).candidates()).isNotEmpty();

    PropertyCreationResult third =
        creationService.create(
            new CreatePropertyCommand(PropertyType.BUILDING, name, creator, null, null, true));
    assertThat(third).isInstanceOf(PropertyCreationResult.Created.class);
  }
}
