package com.example.geohousing.reviews.infrastructure.web;

import java.util.List;

/**
 * Replacement content for an existing review. An edit carries a complete content set rather than a
 * patch, because each stored version has to be readable on its own during moderation.
 *
 * <p>{@code editReason} is required — it is what a moderator reads when the review comes back.
 */
public record EditReviewRequest(
    String locale,
    String body,
    String pros,
    String cons,
    String recommendation,
    List<SubmitReviewRequest.CategoryRatingRequest> ratings,
    String editReason) {}
