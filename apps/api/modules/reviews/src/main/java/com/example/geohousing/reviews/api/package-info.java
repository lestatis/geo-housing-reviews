/**
 * The reviews module's published contract — the only part of it other modules may depend on
 * (enforced by {@code ModuleBoundaryArchitectureTest}).
 *
 * <p>Currently one inbound port: {@link
 * com.example.geohousing.reviews.api.ReviewVerificationUpdater}, through which the verification
 * module projects a trust tier onto a review. The projection is one-way — reviews never calls
 * verification — so the two modules do not form a cycle.
 */
package com.example.geohousing.reviews.api;
