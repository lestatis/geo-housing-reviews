/**
 * The properties module's published contract — the only part of it other modules may depend on
 * (enforced by {@code ModuleBoundaryArchitectureTest}).
 *
 * <p>Keep it small. Every type here is one other modules can be broken by, so it carries
 * identifiers and coarse availability rather than the domain aggregate, and it hides merges by
 * resolving them.
 */
package com.example.geohousing.properties.api;
