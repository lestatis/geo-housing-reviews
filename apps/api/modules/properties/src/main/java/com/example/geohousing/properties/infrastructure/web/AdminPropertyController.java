package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.application.AdminPropertyService;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyNotFoundException;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin lifecycle actions on properties. The {@code ROLE_ADMIN} gate is enforced by the security
 * filter chain ({@code /api/admin/**}), so reaching this controller already implies an
 * authenticated admin; every action is audited by {@link AdminPropertyService}, including attempts
 * against a property that does not exist.
 */
@RestController
@RequestMapping("/api/admin/properties")
class AdminPropertyController {

  private final AdminPropertyService adminPropertyService;

  AdminPropertyController(AdminPropertyService adminPropertyService) {
    this.adminPropertyService = adminPropertyService;
  }

  @PostMapping("/{propertyId}/activate")
  PropertyResponse activate(
      Principal principal,
      @PathVariable("propertyId") String propertyId,
      @RequestBody AdminLifecycleRequest request) {
    PropertyId id = parseId(propertyId);
    return respond(
        adminPropertyService.activate(WebAuthentication.adminId(principal), id, request.version()),
        id);
  }

  @PostMapping("/{propertyId}/hide")
  PropertyResponse hide(
      Principal principal,
      @PathVariable("propertyId") String propertyId,
      @RequestBody AdminLifecycleRequest request) {
    PropertyId id = parseId(propertyId);
    return respond(
        adminPropertyService.hide(WebAuthentication.adminId(principal), id, request.version()), id);
  }

  @PostMapping("/{propertyId}/merge")
  PropertyResponse merge(
      Principal principal,
      @PathVariable("propertyId") String propertyId,
      @RequestBody AdminLifecycleRequest request) {
    PropertyId id = parseId(propertyId);
    if (request.targetPropertyId() == null || request.targetPropertyId().isBlank()) {
      throw new IllegalArgumentException("targetPropertyId is required for a merge");
    }
    return respond(
        adminPropertyService.merge(
            WebAuthentication.adminId(principal),
            id,
            parseId(request.targetPropertyId()),
            request.version()),
        id);
  }

  private static PropertyId parseId(String value) {
    // A malformed UUID throws IllegalArgumentException, mapped to 400 by the properties advice.
    return PropertyId.of(UUID.fromString(value));
  }

  private static PropertyResponse respond(Optional<Property> property, PropertyId requestedId) {
    return property
        .map(PropertyResponse::from)
        .orElseThrow(() -> new PropertyNotFoundException(requestedId));
  }
}
