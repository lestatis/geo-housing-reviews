package com.example.geohousing.properties.api;

/**
 * Whether a property is publicly available.
 *
 * <p>This is deliberately coarser than the module's internal lifecycle: consumers care whether they
 * may act on a property, not which of several administrative states it is in, and the internal
 * states are free to change without breaking them. A merged property never reaches consumers — it
 * resolves to the property that survived the merge.
 */
public enum PropertyVisibility {

  /** Listed in the catalogue and open to whatever the consumer wants to do with it. */
  PUBLIC,

  /** Withheld by an administrator: still referenced by existing content, but not open to new. */
  WITHHELD
}
