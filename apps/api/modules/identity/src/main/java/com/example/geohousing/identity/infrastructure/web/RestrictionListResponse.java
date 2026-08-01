package com.example.geohousing.identity.infrastructure.web;

import java.util.List;

/** Every restriction ever placed on an account, newest first. */
public record RestrictionListResponse(List<RestrictionView> items) {}
