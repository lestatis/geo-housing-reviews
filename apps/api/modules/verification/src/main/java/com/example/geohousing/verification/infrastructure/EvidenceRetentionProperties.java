package com.example.geohousing.verification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long raw verification evidence may be kept, in days.
 *
 * <p>Configuration rather than constants on purpose: {@code docs/SECURITY_PRIVACY.md} §6 says exact
 * retention periods require legal and operational approval, which is still outstanding. The
 * defaults below are conservative placeholders so local development works — not a policy decision,
 * and a legal answer must change these settings rather than any code.
 *
 * @param uploadRetentionDays backstop for evidence on a case that is never decided
 * @param postDecisionRetentionDays how long evidence may outlive a decision, covering the appeal
 *     window
 */
@ConfigurationProperties(prefix = "verification.evidence.retention")
public record EvidenceRetentionProperties(int uploadRetentionDays, int postDecisionRetentionDays) {

  public EvidenceRetentionProperties {
    uploadRetentionDays = uploadRetentionDays <= 0 ? 30 : uploadRetentionDays;
    postDecisionRetentionDays = postDecisionRetentionDays <= 0 ? 7 : postDecisionRetentionDays;
  }
}
