import { formatInstant, humanise } from "@/src/format";
import type { components } from "@/src/api/generated/schema";

export type VerificationCase = components["schemas"]["VerificationCaseResponse"];
export type EvidenceItem = components["schemas"]["VerificationEvidenceResponse"];

/**
 * A verification case as a moderator judges it.
 *
 * <p>The same allowlist idea as the moderation queue. There is less to leak here — the API already
 * withholds the object key, the checksum and the document bytes — but the rule that matters is
 * that "verified" means the relationship was checked, not that the claim is certified true, so the
 * screen shows the claim and the evidence and never a conclusion of its own.
 */
export type VerificationRow = {
  caseId: string;
  account: string;
  property: string;
  method: string;
  claim: string;
  status: string;
  tier: string;
  reasonCode: string;
  validThrough: string;
  opened: string;
  version: number;
};

export function toVerificationRow(verificationCase: VerificationCase): VerificationRow {
  return {
    caseId: verificationCase.caseId ?? "",
    account: shorten(verificationCase.accountId),
    property: shorten(verificationCase.propertyId),
    method: humanise(verificationCase.method),
    claim: humanise(verificationCase.relationshipClaim),
    status: humanise(verificationCase.status),
    tier: humanise(verificationCase.tier),
    reasonCode: verificationCase.decisionReasonCode ?? "—",
    validThrough: verificationCase.validThrough ?? "—",
    opened: formatInstant(verificationCase.createdAt),
    // Carried, not displayed: a decision has to name the version it was made against, or the
    // second of two moderators reading the same case silently overwrites the first.
    version: verificationCase.version ?? 0,
  };
}

/**
 * Whether this method is decided by reading a document.
 *
 * <p>Mirrors `VerificationMethod.requiresEvidence`. A relationship signal is checked by the system,
 * so an empty evidence list against one is normal rather than a missing document.
 */
export function needsEvidence(method: string | undefined): boolean {
  return method === "DOCUMENT";
}

export type EvidenceRow = {
  evidenceId: string;
  contentType: string;
  size: string;
  uploaded: string;
  available: boolean;
  state: string;
};

export function toEvidenceRow(evidence: EvidenceItem): EvidenceRow {
  const deleted = evidence.deletedAt;
  return {
    evidenceId: evidence.evidenceId ?? "",
    contentType: evidence.contentType ?? "—",
    size: formatSize(evidence.sizeBytes),
    uploaded: formatInstant(evidence.uploadedAt),
    // Evidence is deleted on a retention schedule after a decision (ADR-0008). Offering to open a
    // document that retention has already removed sends a moderator to a 404 and reads as a fault.
    available: !deleted,
    state: deleted ? `Deleted ${formatInstant(deleted)}` : "Available",
  };
}

function formatSize(bytes: number | undefined): string {
  if (bytes === undefined) {
    return "—";
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function shorten(id: string | undefined): string {
  return id ? id.slice(0, 8) : "—";
}
