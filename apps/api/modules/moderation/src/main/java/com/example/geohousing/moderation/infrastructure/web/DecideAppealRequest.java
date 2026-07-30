package com.example.geohousing.moderation.infrastructure.web;

/** A moderator's ruling on an appeal. The decider comes from the token, never the body. */
public record DecideAppealRequest(String outcome, String explanation) {}
