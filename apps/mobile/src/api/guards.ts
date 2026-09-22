/**
 * Runtime guards.
 *
 * Generated OpenAPI types describe the contract at compile time; they validate nothing at runtime.
 * A response that is 200 but does not match the generated type is `malformed`, and the only way to
 * know that is to check the fields the caller actually reads. These helpers are the smallest set
 * needed to write those checks; they are deliberately not a schema-validation framework (plan 023,
 * "Runtime validation rule").
 */

export type RuntimeGuard<T> = (value: unknown) => value is T;

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function hasString(record: Record<string, unknown>, key: string): boolean {
  return typeof record[key] === "string";
}

export function hasNumber(record: Record<string, unknown>, key: string): boolean {
  return typeof record[key] === "number";
}
