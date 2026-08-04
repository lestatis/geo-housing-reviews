import { expect, test } from "@playwright/test";
import { MODERATOR, fillAuditTimeline, seedAccounts } from "./seed";
import { signIn } from "./sign-in";

/**
 * The audit timeline, which is the point of having recorded any of it.
 *
 * <p>Requires the stack from apps/web/README.md.
 */

test("an administrator grants a role and then finds that grant in the timeline", async ({
  page,
}) => {
  // The whole feature in one journey: an action taken in one screen, found in another, because the
  // audit row that was always written can finally be read.
  const seeded = await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto(`/accounts/${seeded.residentAccountId}`);
  await page.waitForLoadState("networkidle");
  await page.getByRole("button", { name: "Make administrator" }).click();
  await expect(page.getByTestId("role")).toHaveText("Admin");

  await page.goto("/audit");

  const grant = page
    .getByTestId("audit-row")
    .filter({ hasText: "Grant admin" })
    .filter({ hasText: seeded.residentAccountId.slice(0, 8) });
  await expect(grant).toHaveCount(1);
  await expect(grant).toContainText("Identity");
  await expect(grant).toContainText(seeded.moderatorAccountId.slice(0, 8));

  // Put the role back. Scenarios share a database across runs, and an administrator left behind
  // means a later test asserting "this is the last one" is quietly testing something else.
  await page.goto(`/accounts/${seeded.residentAccountId}`);
  await page.waitForLoadState("networkidle");
  await page.getByRole("button", { name: "Remove administrative access" }).click();
  await expect(page.getByTestId("role")).toHaveText("User");
});

test("the timeline says which window it is showing", async ({ page }) => {
  // A filtered view that looked unfiltered would let somebody conclude nothing happened when they
  // were looking at the wrong week — the failure that matters for an audit log.
  await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto("/audit");

  await expect(page.getByTestId("window")).toContainText("everyone");

  await page.goto("/audit?since=2020-01-01&until=2020-01-02");
  await expect(page.getByTestId("window")).toContainText("2020-01-01 to 2020-01-02");
  await expect(page.getByTestId("empty")).toBeVisible();
});

test("the timeline can follow one account", async ({ page }) => {
  const seeded = await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto(`/accounts/${seeded.residentAccountId}`);
  await page.waitForLoadState("networkidle");
  await page.getByLabel("Why (the account can be told this)").fill("timeline check");
  await page.getByRole("button", { name: "Restrict this account" }).click();
  await expect(page.getByTestId("standing")).toHaveText("Restricted");

  await page.goto(`/audit?actor=${seeded.moderatorAccountId}`);

  await expect(page.getByTestId("window")).toContainText("only");
  const rows = page.getByTestId("audit-row");
  await expect(rows.first()).toBeVisible();
  // Every line is that administrator's — the filter reaches each module rather than being applied
  // after the merge.
  for (const text of await rows.allInnerTexts()) {
    expect(text).toContain(seeded.moderatorAccountId.slice(0, 8));
  }
});

test("reading the timeline appears in the timeline", async ({ page }) => {
  const seeded = await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto("/audit");
  await page.reload();

  const read = page
    .getByTestId("audit-row")
    .filter({ hasText: "View audit" })
    .filter({ hasText: seeded.moderatorAccountId.slice(0, 8) });
  await expect(read.first()).toBeVisible();
});

test("an ordinary account gets no timeline", async ({ page }) => {
  await seedAccounts();
  await signIn(page, "mariam-resident");
  await page.goto("/audit");

  await expect(page.locator("main").getByRole("alert")).toHaveText(
    "This account is not a moderator.",
  );
  await expect(page.getByTestId("audit-row")).toHaveCount(0);
});

test("a timeline longer than a page says so, and can be read further", async ({ page }) => {
  // A page that stopped at the limit and said "50 entries" would read as a complete answer. For an
  // audit log that is the failure that matters: somebody concludes nothing else happened.
  await seedAccounts();
  const looked = await fillAuditTimeline(55);
  await signIn(page, MODERATOR);
  await page.goto("/audit");

  await expect(page.getByTestId("page-summary")).toContainText("more remain");
  const firstPage = await page.getByTestId("audit-row").allInnerTexts();

  await page.getByTestId("older").click();
  await expect(page.getByTestId("window")).toContainText("continued");

  const secondPage = await page.getByTestId("audit-row").allInnerTexts();
  expect(secondPage.length).toBeGreaterThan(0);
  // No entry appears on both pages: the cursor resumes after the boundary rather than at it.
  expect(secondPage.filter((row) => firstPage.includes(row))).toEqual([]);

  // And nothing fell into the gap between them: every lookup is on one page or the other.
  const both = [...firstPage, ...secondPage].join("\n");
  for (const missing of looked) {
    expect(both).toContain(missing.slice(0, 8));
  }
});

test("a mistyped account id is answered as a typo, not as a broken timeline", async ({ page }) => {
  // This used to reach the API as an unhandled failure and come back a 500, which the screen
  // reported as "the timeline could not be loaded" — telling an administrator the audit log was
  // broken when they had fumbled a paste.
  await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto("/audit?actor=not-an-account-id");

  await expect(page.getByTestId("audit-error")).toContainText("not an id");
  await expect(page.getByTestId("audit-row")).toHaveCount(0);
});

test("a window that ends before it starts is answered as such", async ({ page }) => {
  await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto("/audit?since=2026-08-04&until=2026-08-01");

  await expect(page.getByTestId("audit-error")).toContainText("before its start");
});
