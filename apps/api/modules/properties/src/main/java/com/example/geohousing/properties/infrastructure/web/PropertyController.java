package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.application.CreatePropertyCommand;
import com.example.geohousing.properties.application.PropertyCreationResult;
import com.example.geohousing.properties.application.PropertyCreationService;
import com.example.geohousing.properties.application.PropertyQueryService;
import com.example.geohousing.properties.domain.Address;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public catalogue endpoints. Any authenticated user may create a property (it starts as a {@code
 * DRAFT}) and read the catalogue; merge/hide/status changes are admin-only and live under {@code
 * /api/admin/**}. Authorization for these routes is the app's filter chain ({@code
 * anyRequest().authenticated()}).
 */
@RestController
@RequestMapping("/api/properties")
class PropertyController {

  private final PropertyCreationService creationService;
  private final PropertyQueryService queryService;

  PropertyController(PropertyCreationService creationService, PropertyQueryService queryService) {
    this.creationService = creationService;
    this.queryService = queryService;
  }

  /**
   * Creates a property. When possible duplicates exist and the caller has not opted in, responds
   * 409 with the candidates instead of creating, so the client can offer an existing property.
   */
  @PostMapping
  ResponseEntity<?> create(Principal principal, @RequestBody CreatePropertyRequest request) {
    CreatePropertyCommand command =
        new CreatePropertyCommand(
            parseType(request.type()),
            request.canonicalName(),
            WebAuthentication.creatorId(principal),
            toAddress(request.address()),
            toCoordinates(request.latitude(), request.longitude()),
            request.allowDuplicate());

    PropertyCreationResult result = creationService.create(command);
    if (result instanceof PropertyCreationResult.DuplicatesFound duplicates) {
      return ResponseEntity.status(409)
          .body(DuplicateCandidatesProblem.of(duplicates.candidates()));
    }

    Property created = ((PropertyCreationResult.Created) result).property();
    return ResponseEntity.created(URI.create("/api/properties/" + created.id().value()))
        .body(PropertyResponse.from(created));
  }

  @GetMapping("/{propertyId}")
  PropertyResponse get(@PathVariable("propertyId") String propertyId) {
    // A malformed UUID throws IllegalArgumentException, mapped to 400 by the properties advice.
    return PropertyResponse.from(queryService.getById(PropertyId.of(UUID.fromString(propertyId))));
  }

  @GetMapping
  PropertyListResponse list(@RequestParam(name = "limit", required = false) Integer limit) {
    List<PropertyResponse> items =
        queryService.listRecent(limit).stream().map(PropertyResponse::from).toList();
    return new PropertyListResponse(items);
  }

  private static PropertyType parseType(String type) {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("type must not be blank");
    }
    try {
      return PropertyType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown property type: " + type);
    }
  }

  private static Address toAddress(CreatePropertyRequest.AddressPayload payload) {
    return payload == null
        ? null
        : new Address(
            payload.country(),
            payload.city(),
            payload.district(),
            payload.street(),
            payload.building(),
            payload.originalText());
  }

  private static Coordinates toCoordinates(Double latitude, Double longitude) {
    if (latitude == null && longitude == null) {
      return null;
    }
    if (latitude == null || longitude == null) {
      throw new IllegalArgumentException("latitude and longitude must be supplied together");
    }
    return Coordinates.of(latitude, longitude);
  }
}
