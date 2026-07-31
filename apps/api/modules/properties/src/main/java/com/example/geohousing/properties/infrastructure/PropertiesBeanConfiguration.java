package com.example.geohousing.properties.infrastructure;

import com.example.geohousing.properties.api.PropertyCatalog;
import com.example.geohousing.properties.application.AdminPropertyService;
import com.example.geohousing.properties.application.DuplicateCandidateFinder;
import com.example.geohousing.properties.application.PropertyAdminRepository;
import com.example.geohousing.properties.application.PropertyCatalogService;
import com.example.geohousing.properties.application.PropertyCreationService;
import com.example.geohousing.properties.application.PropertyQueryService;
import com.example.geohousing.properties.application.PropertyRepository;
import com.example.geohousing.properties.application.PropertySearchService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Constructs the properties module's framework-free application services as Spring beans. The
 * persistence adapters ({@code JpaPropertyRepository}, {@code JpaDuplicateCandidateFinder}) are
 * component-scanned {@code @Repository}s; this wires the services that depend on them.
 *
 * <p>The clock is constructed inline rather than exposed as a bean on purpose: the app already
 * defines an {@code identityClock} bean, and the convention build does not compile with {@code
 * -parameters}, so a second {@code Clock} bean would make every {@code Clock} injection ambiguous
 * (no by-name fallback). Enabling {@code -parameters} project-wide would let each module keep a
 * named clock bean — a small infra follow-up left out of this chunk.
 */
@Configuration
public class PropertiesBeanConfiguration {

  @Bean
  PropertyCreationService propertyCreationService(
      PropertyRepository propertyRepository, DuplicateCandidateFinder duplicateCandidateFinder) {
    return new PropertyCreationService(
        propertyRepository, duplicateCandidateFinder, Clock.systemUTC());
  }

  @Bean
  PropertyQueryService propertyQueryService(PropertyRepository propertyRepository) {
    return new PropertyQueryService(propertyRepository);
  }

  @Bean
  PropertyCatalog propertyCatalog(PropertyRepository propertyRepository) {
    return new PropertyCatalogService(propertyRepository);
  }

  @Bean
  AdminPropertyService adminPropertyService(
      PropertyRepository propertyRepository, PropertyAdminRepository propertyAdminRepository) {
    return new AdminPropertyService(propertyRepository, propertyAdminRepository, Clock.systemUTC());
  }

  @Bean
  PropertySearchService propertySearchService(PropertyRepository propertyRepository) {
    return new PropertySearchService(propertyRepository);
  }
}
