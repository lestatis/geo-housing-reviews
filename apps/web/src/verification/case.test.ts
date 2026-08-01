import { describe, expect, it } from "vitest";
import {
  type EvidenceItem,
  type VerificationCase,
  needsEvidence,
  toEvidenceRow,
  toVerificationRow,
} from "./case";

const CASE: VerificationCase = {
  caseId: "d1e2f300-0000-4000-8000-000000000001",
  accountId: "aa110000-0000-4000-8000-000000000002",
  propertyId: "bb220000-0000-4000-8000-000000000003",
  method: "DOCUMENT",
  status: "PENDING",
  tier: "UNVERIFIED",
  relationshipClaim: "CURRENT_RESIDENT",
  createdAt: "2026-07-30T09:00:00Z",
  updatedAt: "2026-07-30T09:00:00Z",
  version: 0,
};

describe("a verification case in the queue", () => {
  it("says who claims what about which property", () => {
    const row = toVerificationRow(CASE);

    expect(row.method).toBe("Document");
    expect(row.claim).toBe("Current resident");
    expect(row.status).toBe("Pending");
    expect(row.account).toBe("aa110000");
    expect(row.property).toBe("bb220000");
    expect(row.opened).toBe("30 Jul 2026, 09:00 UTC");
  });

  it("carries the version, because a decision must name what it was made against", () => {
    // Two moderators reading the same case is the ordinary situation; without the version the
    // second one silently overwrites the first.
    expect(toVerificationRow(CASE).version).toBe(0);
    expect(toVerificationRow({ ...CASE, version: 7 }).version).toBe(7);
  });

  it("says what a decision granted once one was made", () => {
    const approved = toVerificationRow({
      ...CASE,
      status: "APPROVED",
      tier: "DOCUMENT_VERIFIED",
      decisionReasonCode: "LEASE_MATCHES",
      validThrough: "2027-07-30",
    });

    expect(approved.status).toBe("Approved");
    expect(approved.tier).toBe("Document verified");
    expect(approved.reasonCode).toBe("LEASE_MATCHES");
    expect(approved.validThrough).toBe("2027-07-30");
  });

  it("copes with a case the API described only partially", () => {
    expect(() => toVerificationRow({})).not.toThrow();
    expect(toVerificationRow({}).method).toBe("—");
    expect(toVerificationRow({}).version).toBe(0);
  });
});

describe("which methods are decided on documents", () => {
  it("expects evidence only where a document is the method", () => {
    // A relationship signal is checked by the system, not read by a moderator; showing an empty
    // evidence list against one would read as "the document is missing".
    expect(needsEvidence("DOCUMENT")).toBe(true);
    expect(needsEvidence("INVITATION")).toBe(false);
    expect(needsEvidence("BUILDING_CODE")).toBe(false);
    expect(needsEvidence("LOCATION_SIGNAL")).toBe(false);
    expect(needsEvidence(undefined)).toBe(false);
  });
});

describe("a piece of evidence", () => {
  const EVIDENCE: EvidenceItem = {
    evidenceId: "ee330000-0000-4000-8000-000000000004",
    contentType: "image/jpeg",
    sizeBytes: 1_258_291,
    uploadedAt: "2026-07-30T09:15:00Z",
  };

  it("describes the document without naming a file anyone chose", () => {
    // An uploaded filename is user-supplied text that would be rendered to a moderator; the API
    // does not return one, and the row does not invent one.
    const row = toEvidenceRow(EVIDENCE);

    expect(row.contentType).toBe("image/jpeg");
    expect(row.size).toBe("1.2 MB");
    expect(row.uploaded).toBe("30 Jul 2026, 09:15 UTC");
  });

  it("says plainly when evidence has already been deleted by retention", () => {
    // Evidence is deleted on a schedule after a decision. A row that still offered to open it
    // would send a moderator to a 404 and read as a fault.
    expect(toEvidenceRow(EVIDENCE).available).toBe(true);
    expect(toEvidenceRow({ ...EVIDENCE, deletedAt: "2026-08-06T00:00:00Z" }).available).toBe(false);
    expect(toEvidenceRow({ ...EVIDENCE, deletedAt: "2026-08-06T00:00:00Z" }).state).toBe(
      "Deleted 6 Aug 2026, 00:00 UTC",
    );
  });

  it("scales a size to something a person reads", () => {
    expect(toEvidenceRow({ ...EVIDENCE, sizeBytes: 512 }).size).toBe("512 B");
    expect(toEvidenceRow({ ...EVIDENCE, sizeBytes: 2048 }).size).toBe("2.0 KB");
    expect(toEvidenceRow({ ...EVIDENCE, sizeBytes: undefined }).size).toBe("—");
  });
});
