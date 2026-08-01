/**
 * The moderation vocabulary, mirrored from the server's `DecisionAction`.
 *
 * <p>This is the one place in the app that restates something the API already knows, and it is here
 * under protest: the OpenAPI document types `action` as a bare string, so generation produces no
 * union to pick from. Fixing that properly is a backend change — it needs either a springdoc
 * dependency in a module that has none, or enum-typed request fields, which would change how an
 * unrecognised action is reported. Recorded as a follow-up in `docs/plans/012-admin-web-app.md`.
 *
 * <p>Until then the list stays in one file, and the rule that matters is tested rather than
 * assumed.
 */
export const DECISION_ACTIONS = [
  "APPROVE",
  "APPROVE_WITH_REDACTION",
  "REQUEST_CHANGES",
  "REJECT",
  "HIDE",
  "REMOVE",
  "RESTRICT_ACCOUNT",
  "ESCALATE",
] as const;

export type DecisionAction = (typeof DECISION_ACTIONS)[number];

/** Actions that take nothing away, and so owe nobody an explanation. */
const EXPLAINS_ITSELF: readonly string[] = ["APPROVE", "ESCALATE"];

/**
 * Whether this action must be explained to the person it affects.
 *
 * <p>The server decides this for real; the form asks so a moderator is told before they submit
 * rather than after. An action this list does not recognise is treated as owing an explanation —
 * the safe direction to be wrong in.
 */
export function requiresPublicExplanation(action: string): boolean {
  return !EXPLAINS_ITSELF.includes(action);
}

/** "APPROVE_WITH_REDACTION" as something to read. */
export function describeAction(action: string): string {
  return action.charAt(0) + action.slice(1).toLowerCase().replaceAll("_", " ");
}
