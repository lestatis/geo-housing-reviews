package com.example.geohousing.moderation.domain;

/**
 * What a reporter says is wrong with the content. Mirrors the categories listed in MODERATION.md
 * and the {@code category} check constraint on {@code moderation.report}.
 *
 * <p>A category is a claim, never a verdict. MODERATION.md is explicit that a "false claim" report
 * does not automatically remove anything: a moderator still has to judge whether the content is
 * opinion, first-hand experience, a verifiable allegation, or fabrication.
 */
public enum ReportCategory {
  PERSONAL_DATA,
  FALSE_OR_MISLEADING,
  HARASSMENT_OR_THREAT,
  CONFLICT_OF_INTEREST,
  NOT_ABOUT_THIS_PROPERTY,
  DUPLICATE_OR_SPAM,
  COPYRIGHT_OR_MEDIA,
  OUTDATED_OR_RESOLVED,
  OTHER;

  /**
   * Whether a reporter choosing this category must say what they mean. "Other" with nothing written
   * gives a moderator nothing to act on; every other category already states the problem.
   */
  public boolean requiresDescription() {
    return this == OTHER;
  }
}
