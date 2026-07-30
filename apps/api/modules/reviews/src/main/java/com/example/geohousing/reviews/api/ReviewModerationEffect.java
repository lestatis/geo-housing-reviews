package com.example.geohousing.reviews.api;

/**
 * A change another module may ask this one to make to a review's publication state. A
 * published-contract enum, deliberately separate from the internal {@code ReviewModerationAction}
 * so the contract does not expose a domain type.
 *
 * <p>This is the whole of what an outside module may do to a review. Editing content, changing a
 * verification tier, or touching anything else is not reachable from here.
 */
public enum ReviewModerationEffect {
  PUBLISH,
  REJECT,
  HIDE,
  RESTORE,
  REMOVE,
  /**
   * Undoes a terminal decision after an appeal overturned it. The only way back from REJECT or
   * REMOVE, and reachable only through the appeal path.
   */
  REINSTATE
}
