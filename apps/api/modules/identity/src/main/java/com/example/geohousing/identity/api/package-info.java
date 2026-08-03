/**
 * The only part of identity other modules may reach.
 *
 * <p>Enforced by {@code ModuleBoundaryArchitectureTest}: anything outside this package is internal
 * to identity, and a module reaching it fails the build.
 */
package com.example.geohousing.identity.api;
