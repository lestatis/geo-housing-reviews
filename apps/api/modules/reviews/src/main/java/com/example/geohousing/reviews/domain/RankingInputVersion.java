package com.example.geohousing.reviews.domain;

/**
 * The version of the policy that produced a ranking input (PRD_MVP.md §6: "ranking version must be
 * stored for audit/experiments").
 *
 * <p>A version exists so the policy's parameters can change without making values recorded under an
 * earlier policy uninterpretable. Changing how a version scores means adding a new constant here,
 * never editing an existing one: an audit asking "why did this review score what it scored last
 * March" is answerable only if {@code V1} still means in code what it meant then.
 */
public enum RankingInputVersion {
  V1;

  /** The version new inputs are produced under. */
  public static RankingInputVersion current() {
    return V1;
  }
}
