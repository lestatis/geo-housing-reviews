package com.example.geohousing.moderation.domain;

/** The outcome of an appeal, or {@code PENDING} until a different moderator has heard it. */
public enum AppealStatus {
  PENDING,
  UPHELD,
  OVERTURNED;

  public boolean isDecided() {
    return this != PENDING;
  }
}
