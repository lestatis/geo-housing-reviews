package com.example.geohousing.moderation.domain;

/**
 * What caused a case to be opened.
 *
 * <p>{@code LEGAL_REQUEST} exists so an owner's or developer's takedown demand travels the same
 * audited workflow as any other report. MODERATION.md's anti-capture rules require exactly that:
 * the alternative is a private channel where commercial pressure decides outcomes unrecorded.
 */
public enum CaseTrigger {
  REPORT,
  PRE_MODERATION,
  AUTOMATED,
  LEGAL_REQUEST
}
