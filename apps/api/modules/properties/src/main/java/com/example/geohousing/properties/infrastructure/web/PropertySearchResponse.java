package com.example.geohousing.properties.infrastructure.web;

import java.util.List;

/** Search hits, best match first. */
public record PropertySearchResponse(List<PropertySearchHitResponse> items) {}
