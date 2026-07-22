package com.example.geohousing.properties.infrastructure.web;

import java.util.List;

/**
 * A bounded, newest-first page of the catalogue. Deliberately not cursor-paginated: rich listing
 * and search belong to the search module; a cursor is introduced when a real feed needs one.
 */
public record PropertyListResponse(List<PropertyResponse> items) {}
