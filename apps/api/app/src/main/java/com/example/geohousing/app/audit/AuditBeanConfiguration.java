package com.example.geohousing.app.audit;

import com.example.geohousing.identity.api.AuditReadRecorder;
import com.example.geohousing.shared.audit.AuditTrail;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires every module's trail into one timeline.
 *
 * <p>Spring injects every {@link AuditTrail} bean, so a module that starts recording something new
 * appears in the timeline without this class or {@link AuditTimelineService} learning its name.
 */
@Configuration
class AuditBeanConfiguration {

  @Bean
  AuditTimelineService auditTimelineService(
      List<AuditTrail> trails, AuditReadRecorder auditReadRecorder) {
    return new AuditTimelineService(trails, auditReadRecorder);
  }
}
