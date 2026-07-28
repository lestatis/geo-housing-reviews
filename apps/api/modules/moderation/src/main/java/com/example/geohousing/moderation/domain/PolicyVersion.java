package com.example.geohousing.moderation.domain;

/**
 * The version of the content policy a decision was made under.
 *
 * <p>Recorded on every decision because MODERATION.md requires an appeal to preserve "the original
 * decision and policy version": judging an old decision by today's rules is not an appeal, it is a
 * different question. It also makes "how many decisions did the policy change overturn?"
 * answerable.
 */
public record PolicyVersion(int value) {

  public PolicyVersion {
    if (value < 1) {
      throw new IllegalArgumentException("policy version must be positive, was " + value);
    }
  }

  public static PolicyVersion of(int value) {
    return new PolicyVersion(value);
  }
}
