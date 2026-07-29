package com.example.geohousing.moderation.application;

import com.example.geohousing.moderation.domain.ModerationTargetRef;

/**
 * The content someone tried to act on does not exist, or is not something they may act on.
 *
 * <p>Deliberately one exception for both cases: telling a caller apart "no such review" from "a
 * review you may not see" would turn the reporting endpoint into a way to discover unpublished
 * content.
 */
public class ModerationTargetNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ModerationTargetNotFoundException(ModerationTargetRef ref) {
    super("no moderatable " + ref.type() + " for this identifier");
  }
}
