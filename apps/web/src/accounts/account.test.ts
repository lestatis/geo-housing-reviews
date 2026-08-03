import { describe, expect, it } from "vitest";
import {
  type AdminAccount,
  type Restriction,
  canStepDown,
  toAccountRow,
  toRestrictionRow,
} from "./account";

const ACCOUNT: AdminAccount = {
  accountId: "aa110000-0000-4000-8000-000000000001",
  role: "USER",
  status: "ACTIVE",
  createdAt: "2026-06-01T09:00:00Z",
  version: 3,
};

const RESTRICTION: Restriction = {
  restrictionId: "bb220000-0000-4000-8000-000000000002",
  accountId: ACCOUNT.accountId,
  scope: "ACCOUNT_WIDE",
  reason: "posted a neighbour's flat number",
  startAt: "2026-07-30T09:00:00Z",
  moderatorAccountId: "cc330000-0000-4000-8000-000000000003",
  appealStatus: "NONE",
  active: true,
};

describe("an account an administrator is looking at", () => {
  it("shows what it is and carries the version a role change needs", () => {
    const row = toAccountRow(ACCOUNT);

    expect(row.role).toBe("User");
    expect(row.status).toBe("Active");
    expect(row.created).toBe("1 Jun 2026, 09:00 UTC");
    expect(row.version).toBe(3);
  });

  it("never shows an email or anything derived from a credential", () => {
    // AdminAccountView withholds both by construction. If it ever stopped doing so, this screen
    // must not start showing them: an administrator moderating content has no need for either.
    const leaky = {
      ...ACCOUNT,
      email: "nino@example.com",
      authSubjectHash: "9f2b8c1d4e5a6b7c",
    } as AdminAccount;

    const rendered = Object.values(toAccountRow(leaky)).join(" ");

    expect(rendered).not.toContain("nino@example.com");
    expect(rendered).not.toContain("9f2b8c1d");
  });

  it("says plainly when an account is closed", () => {
    const closed = toAccountRow({ ...ACCOUNT, status: "CLOSED", closedAt: "2026-07-15T12:00:00Z" });

    expect(closed.status).toBe("Closed");
    expect(closed.closed).toBe("15 Jul 2026, 12:00 UTC");
  });

  it("copes with an account the API described only partially", () => {
    expect(() => toAccountRow({})).not.toThrow();
    expect(toAccountRow({}).role).toBe("—");
    expect(toAccountRow({}).version).toBe(0);
  });
});

describe("whether stepping down is offered", () => {
  it("is offered to an administrator acting on themselves", () => {
    expect(canStepDown("ADMIN", true)).toBe(true);
  });

  it("is not offered on somebody else's account", () => {
    // Removing another administrator's access is a different button with a different meaning.
    expect(canStepDown("ADMIN", false)).toBe(false);
  });

  it("is not offered to an account that holds no role to give up", () => {
    expect(canStepDown("USER", true)).toBe(false);
  });
});

describe("a restriction on the account", () => {
  it("shows what was decided, by whom, and whether it still binds", () => {
    const row = toRestrictionRow(RESTRICTION);

    expect(row.reason).toBe("posted a neighbour's flat number");
    expect(row.scope).toBe("Account wide");
    expect(row.placedBy).toBe("cc330000");
    expect(row.started).toBe("30 Jul 2026, 09:00 UTC");
    expect(row.state).toBe("In force");
  });

  it("says indefinite rather than leaving the end blank", () => {
    // An empty cell reads as missing data. "Until lifted" is the actual policy: a restriction
    // placed by a moderation decision carries no end date and waits for an administrator.
    expect(toRestrictionRow(RESTRICTION).ends).toBe("Until lifted");
    expect(toRestrictionRow({ ...RESTRICTION, endAt: "2026-08-06T00:00:00Z" }).ends).toBe(
      "6 Aug 2026, 00:00 UTC",
    );
  });

  it("distinguishes one that has ended from one still in force", () => {
    const ended = toRestrictionRow({ ...RESTRICTION, active: false, endAt: "2026-08-01T10:00:00Z" });

    expect(ended.state).toBe("Ended");
    expect(ended.liftable).toBe(false);
    expect(toRestrictionRow(RESTRICTION).liftable).toBe(true);
  });

  it("copes with a restriction the API described only partially", () => {
    expect(() => toRestrictionRow({})).not.toThrow();
    expect(toRestrictionRow({}).reason).toBe("—");
  });
});
