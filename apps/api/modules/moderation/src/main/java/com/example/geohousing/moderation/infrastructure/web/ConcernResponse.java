package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.domain.Report;
import java.time.Instant;

/**
 * One concern raised about the content, as a moderator sees it.
 *
 * <p>The category and description are the substance a moderator judges against. The reporter is
 * deliberately absent: knowing who complained cannot make the content more or less acceptable, and
 * carrying the identity here would put it one serialisation mistake away from the reported author.
 */
public record ConcernResponse(String category, String description, Instant raisedAt) {

  static ConcernResponse from(Report report) {
    return new ConcernResponse(
        report.category().name(), report.description().orElse(null), report.createdAt());
  }
}
