package com.example.geohousing.moderation.domain;

/**
 * What kind of thing a report or case is about. Mirrors the {@code target_type} check constraint on
 * the moderation tables.
 *
 * <p>Only reviews are moderatable today. Representative replies, properties and accounts arrive
 * with later plans; keeping the vocabulary explicit makes each widening a deliberate change here
 * and in a migration, rather than a silent one.
 */
public enum ModerationTargetType {
  REVIEW
}
