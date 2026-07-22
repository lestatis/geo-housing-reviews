package com.example.geohousing.reviews.infrastructure;

import com.example.geohousing.reviews.application.PropertyLookup;
import com.example.geohousing.reviews.application.ReviewQueryService;
import com.example.geohousing.reviews.application.ReviewRepository;
import com.example.geohousing.reviews.application.ReviewSubmissionService;
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
}
