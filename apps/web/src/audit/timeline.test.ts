import { describe, expect, it } from "vitest";
import { type AuditEntry, describeWindow, toTimelineRow, windowBounds } from "./timeline";

const ENTRY: AuditEntry = {
  at: "2026-08-04T10:15:00Z",
  actorAccountId: "aa110000-0000-4000-8000-000000000001",
  module: "identity",
  action: "GRANT_ADMIN",
  subjectType: "ACCOUNT",
  subjectId: "bb220000-0000-4000-8000-000000000002",
  outcome: "APPLIED",
  reason: null as unknown as string,
};

describe("a line of the audit timeline", () => {
  it("says who did what to what, and when", () => {
    const row = toTimelineRow(ENTRY);

    expect(row.when).toBe("4 Aug 2026, 10:15 UTC");
    expect(row.actor).toBe("aa110000");
    expect(row.action).toBe("Grant admin");
    expect(row.subject).toBe("Account bb220000");
    expect(row.where).toBe("Identity");
    expect(row.outcome).toBe("Applied");
  });

  it("names the system when nobody decided", () => {
    // Verification expires a lapsed badge on a schedule. "—" would read as missing data; the truth
    // is that no person did this.
    const expiry = toTimelineRow({ ...ENTRY, actorAccountId: undefined, action: "EXPIRE" });

    expect(expiry.actor).toBe("System");
  });

  it("shows a reason where the action required one, and says so where it did not", () => {
    expect(toTimelineRow({ ...ENTRY, reason: "DOXXING" }).reason).toBe("DOXXING");
    // A view of an account requires no reason code; an empty cell would read as one going missing.
    expect(toTimelineRow(ENTRY).reason).toBe("—");
  });

  it("never renders anything a moderator wrote", () => {
    // The API does not send an internal note or a public explanation, and the row must not start
    // showing them if it ever does — the timeline says an action happened, the case says what it
    // contained.
    const leaky = {
      ...ENTRY,
      internalNote: "third from this account",
      publicExplanation: "Your review named a neighbour.",
    } as AuditEntry;

    const rendered = Object.values(toTimelineRow(leaky)).join(" ");

    expect(rendered).not.toContain("third from this account");
    expect(rendered).not.toContain("named a neighbour");
  });

  it("copes with an entry the API described only partially", () => {
    expect(() => toTimelineRow({})).not.toThrow();
    expect(toTimelineRow({}).action).toBe("—");
    expect(toTimelineRow({}).subject).toBe("—");
  });
});

describe("the window a timeline covers", () => {
  it("defaults to seven inclusive days, which is what the screen says it does", () => {
    // Today and the six before it. Subtracting seven would show eight dated days under a label
    // saying seven — a screen and a window disagreeing about what is being looked at.
    const window = describeWindow(undefined, undefined, new Date("2026-08-04T12:00:00Z"));

    expect(window.since).toBe("2026-07-29");
    expect(window.until).toBe("2026-08-04");
  });

  it("keeps what was asked for", () => {
    const window = describeWindow("2026-07-01", "2026-07-15", new Date("2026-08-04T12:00:00Z"));

    expect(window.since).toBe("2026-07-01");
    expect(window.until).toBe("2026-07-15");
  });

  it("falls back to the default rather than passing on something that is not a date", () => {
    // A rejected query tells the reader nothing; a mangled one tells them something false.
    const window = describeWindow("last tuesday", "2026-13-45", new Date("2026-08-04T12:00:00Z"));

    expect(window.since).toBe("2026-07-29");
    expect(window.until).toBe("2026-08-04");
  });
});

describe("translating that window for the API", () => {
  it("ends at the next midnight, so the last second of the chosen day is inside it", () => {
    // The regression this exists for: the screen sent 23:59:59Z against SQL asking for
    // created_at < :until, so anything recorded in the final fraction of the day vanished from an
    // audit log — the one place a missing entry matters most.
    const bounds = windowBounds({ since: "2026-08-01", until: "2026-08-04" });

    expect(bounds.since).toBe("2026-08-01T00:00:00Z");
    expect(new Date("2026-08-04T23:59:59.500Z") < new Date(bounds.until)).toBe(true);
    expect(new Date("2026-08-05T00:00:00.000Z") < new Date(bounds.until)).toBe(false);
  });

  it("crosses a month and a year boundary", () => {
    expect(windowBounds({ since: "2026-01-01", until: "2026-01-31" }).until).toBe(
      "2026-02-01T00:00:00.000Z",
    );
    expect(windowBounds({ since: "2026-01-01", until: "2026-12-31" }).until).toBe(
      "2027-01-01T00:00:00.000Z",
    );
  });

  it("clamps at the end of representable time rather than emitting a five-digit year", () => {
    // Nothing can be recorded after it, so the clamp loses nothing — and an expanded ISO year is
    // something the API would refuse to parse.
    expect(windowBounds({ since: "9999-12-31", until: "9999-12-31" }).until).toBe(
      "9999-12-31T23:59:59.999Z",
    );
  });
});
