package com.example.geohousing.reviews.infrastructure;

import com.example.geohousing.reviews.api.ReviewVerificationUpdater;
import com.example.geohousing.reviews.application.HelpfulSignalQueryService;
import com.example.geohousing.reviews.application.HelpfulSignalRepository;
import com.example.geohousing.reviews.application.HelpfulSignalService;
import com.example.geohousing.reviews.application.PropertyLookup;
import com.example.geohousing.reviews.application.ReviewModerationRepository;
import com.example.geohousing.reviews.application.ReviewModerationService;
import com.example.geohousing.reviews.application.ReviewQueryService;
import com.example.geohousing.reviews.application.ReviewRankingInputService;
import com.example.geohousing.reviews.application.ReviewRepository;
import com.example.geohousing.reviews.application.ReviewSubmissionService;
import com.example.geohousing.reviews.application.ReviewVerificationApplier;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Constructs the reviews module's framework-free application services as Spring beans. The adapters
 * they depend on ({@code JpaReviewRepository}, {@code CatalogPropertyLookup}) are
 * component-scanned.
 *
 * <p>The clock is constructed inline rather than exposed as a bean, for the same reason as in the
 * properties module: the convention build does not compile with {@code -parameters}, so a second
 * {@code Clock} bean would make every {@code Clock} injection ambiguous.
 */
@Configuration
public class ReviewsBeanConfiguration {

  @Bean
  ReviewSubmissionService reviewSubmissionService(
      ReviewRepository reviewRepository, PropertyLookup propertyLookup) {
    return new ReviewSubmissionService(reviewRepository, propertyLookup, Clock.systemUTC());
  }

  @Bean
  ReviewQueryService reviewQueryService(ReviewRepository reviewRepository) {
    return new ReviewQueryService(reviewRepository);
  }

  @Bean
  HelpfulSignalService helpfulSignalService(
      ReviewRepository reviewRepository, HelpfulSignalRepository helpfulSignalRepository) {
    return new HelpfulSignalService(reviewRepository, helpfulSignalRepository, Clock.systemUTC());
  }

  @Bean
  HelpfulSignalQueryService helpfulSignalQueryService(
      ReviewRepository reviewRepository, HelpfulSignalRepository helpfulSignalRepository) {
    return new HelpfulSignalQueryService(reviewRepository, helpfulSignalRepository);
  }

  @Bean
  ReviewRankingInputService reviewRankingInputService(
      HelpfulSignalQueryService helpfulSignalQueryService) {
    return new ReviewRankingInputService(helpfulSignalQueryService);
  }

  @Bean
  ReviewModerationService reviewModerationService(
      ReviewRepository reviewRepository, ReviewModerationRepository moderationRepository) {
    return new ReviewModerationService(reviewRepository, moderationRepository, Clock.systemUTC());
  }

  @Bean
  ReviewVerificationUpdater reviewVerificationUpdater(ReviewRepository reviewRepository) {
    return new ReviewVerificationApplier(reviewRepository, Clock.systemUTC());
  }
}
