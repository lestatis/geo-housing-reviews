import { formatInstant, humanise } from "@/src/format";
import type { components } from "@/src/api/generated/schema";

export type AdminAccount = components["schemas"]["AdminAccountView"];
export type Restriction = components["schemas"]["RestrictionView"];

/**
 * What the account screen may show.
 *
 * <p>The same allowlist idea as the moderation queue, and here it guards something narrower but
 * sharper: `AdminAccountView` deliberately withholds the email and the auth-subject hash, and this
 * row is what stops the screen showing them if that ever changes. An administrator moderating
 * content needs an account's role and standing, not the credentials behind it.
 */
export type AccountRow = {
  accountId: string;
  role: string;
  status: string;
  created: string;
  closed: string;
  version: number;
};

export function toAccountRow(account: AdminAccount): AccountRow {
  return {
    accountId: account.accountId ?? "",
    role: humanise(account.role),
    status: humanise(account.status),
    created: formatInstant(account.createdAt),
    closed: formatInstant(account.closedAt),
    // Carried, not displayed: a role change has to name the version the administrator saw.
    version: account.version ?? 0,
  };
}

/**
 * Whether to offer "step down" rather than "remove access".
 *
 * <p>They hit the same endpoint but mean different things, and the API refuses the last
 * administrator either way. Labelling somebody else's demotion "step down" would misdescribe it.
 */
export function canStepDown(role: string | undefined, isSelf: boolean): boolean {
  return isSelf && role === "ADMIN";
}

export type RestrictionRow = {
  restrictionId: string;
  scope: string;
  reason: string;
  started: string;
  ends: string;
  placedBy: string;
  state: string;
  liftable: boolean;
};

export function toRestrictionRow(restriction: Restriction): RestrictionRow {
  const active = restriction.active ?? false;
  return {
    restrictionId: restriction.restrictionId ?? "",
    scope: humanise(restriction.scope),
    reason: restriction.reason?.trim() || "—",
    started: formatInstant(restriction.startAt),
    // "Until lifted" rather than a blank cell: an empty end reads as missing data, when it is
    // actually the policy — a restriction from a moderation decision waits for an administrator.
    ends: restriction.endAt ? formatInstant(restriction.endAt) : "Until lifted",
    placedBy: restriction.moderatorAccountId
      ? restriction.moderatorAccountId.slice(0, 8)
      : "—",
    state: active ? "In force" : "Ended",
    liftable: active,
  };
}
