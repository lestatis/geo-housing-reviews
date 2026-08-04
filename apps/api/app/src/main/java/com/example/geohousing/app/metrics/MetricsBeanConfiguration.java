package com.example.geohousing.app.metrics;

import com.example.geohousing.moderation.api.ModerationMetrics;
import com.example.geohousing.reviews.api.ReviewMetrics;
import com.example.geohousing.verification.api.VerificationMetrics;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the three modules' counts into one service. */
@Configuration
class MetricsBeanConfiguration {

  @Bean
  AdminMetricsService adminMetricsService(
      ModerationMetrics moderation, ReviewMetrics reviews, VerificationMetrics verification) {
    return new AdminMetricsService(moderation, reviews, verification, Clock.systemUTC());
  }
}
