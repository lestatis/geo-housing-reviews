import { expect, test } from "@playwright/test";
import { MODERATOR, seedProperty, seedVerification } from "./seed";
import { signIn } from "./sign-in";

/**
 * The remaining admin queues: deciding a Tier 2 verification with the document in front of you, and
 * moving a property through its lifecycle.
 *
 * <p>Requires the stack from apps/web/README.md, including MinIO and its bucket.
 */

test("a moderator reads the evidence and approves the relationship", async ({ page }) => {
  const seeded = await seedVerification();
  await signIn(page, MODERATOR);
  await page.goto(`/verification/${seeded.caseId}`);

  await expect(page.getByTestId("status")).toHaveText("Pending");
  const evidence = page.getByTestId("evidence");
  await expect(evidence).toHaveCount(1);
  await expect(evidence).toContainText("image/png");
  await expect(page.getByTestId("evidence-state")).toHaveText("Available");

  // The document is served through this app, never linked to storage — the browser has no token
  // for the API, and every read has to land in the API's access audit.
  const link = await page.getByTestId("evidence-link").getAttribute("href");
  expect(link).toBe(`/api/evidence/${seeded.caseId}/${await evidenceIdOf(page)}`);

  const document = await page.request.get(link!);
  expect(document.status()).toBe(200);
  expect(document.headers()["content-type"]).toContain("image/png");
  // Evidence must not be cached anywhere, and must not render inline on this origin.
  expect(document.headers()["cache-control"]).toContain("no-store");
  expect(document.headers()["content-disposition"]).toContain("attachment");
  expect(document.headers()["x-content-type-options"]).toBe("nosniff");

  await page.getByLabel("Reason code").fill("LEASE_MATCHES");
  await page.getByLabel("Valid through (approval only)").fill("2027-07-30");
  await page.getByRole("button", { name: "Approve" }).click();

  await expect(page).toHaveURL("/verification");
  await page.goto(`/verification/${seeded.caseId}`);
  await expect(page.getByTestId("status")).toHaveText("Approved");
});

test("a verification decision will not be recorded without a reason", async ({ page }) => {
  const seeded = await seedVerification();
  await signIn(page, MODERATOR);
  await page.goto(`/verification/${seeded.caseId}`);

  await page.getByRole("button", { name: "Reject" }).click();

  await expect(page.locator("form").getByRole("alert")).toHaveText(
    "Every verification decision needs a reason code.",
  );
  await expect(page.getByTestId("status")).toHaveText("Pending");
});

test("evidence is not served to a browser without a session", async ({ page, context }) => {
  const seeded = await seedVerification();
  await signIn(page, MODERATOR);
  await page.goto(`/verification/${seeded.caseId}`);
  const link = (await page.getByTestId("evidence-link").getAttribute("href"))!;

  await context.clearCookies();
  const anonymous = await page.request.get(link, { maxRedirects: 0 });

  // Guessable is not the same as reachable: the URL is stable, and useless without the session.
  expect(anonymous.status()).toBe(307);
});

test("an administrator withdraws a property they found by searching", async ({ page }) => {
  const seeded = await seedProperty();
  await signIn(page, MODERATOR);
  await page.goto("/properties");

  await page.getByLabel("Find a property").fill(seeded.name);
  await page.getByRole("button", { name: "Search" }).click();

  await page.getByRole("link", { name: seeded.name }).click();
  await expect(page.getByTestId("property-status")).toHaveText("Draft");
  // Playwright can press a button faster than a dev build hydrates the route it just navigated to,
  // and a submit that lands first is dropped. That is a harness artefact rather than something a
  // person can do — see the plan's note on pre-hydration submits.
  await page.waitForLoadState("networkidle");

  await page.getByRole("button", { name: "Withdraw" }).click();
  await expect(page.getByTestId("property-status")).toHaveText("Hidden");

  // Withdrawn is not a dead end — it can be brought back, and hiding it again is not offered.
  await expect(page.getByRole("button", { name: "Activate" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Withdraw" })).toHaveCount(0);
});

test("a property cannot be merged into itself", async ({ page }) => {
  const seeded = await seedProperty();
  await signIn(page, MODERATOR);
  await page.goto(`/properties/${seeded.propertyId}`);

  await page.getByLabel("Merge into (property id)").fill(seeded.propertyId);
  await page.getByRole("button", { name: "Merge" }).click();

  await expect(page.locator("form").getByRole("alert")).toHaveText(
    "A property cannot be merged into itself.",
  );
  await expect(page.getByTestId("property-status")).toHaveText("Draft");
});

async function evidenceIdOf(page: import("@playwright/test").Page): Promise<string> {
  const href = (await page.getByTestId("evidence-link").getAttribute("href"))!;
  return href.split("/").pop()!;
}
