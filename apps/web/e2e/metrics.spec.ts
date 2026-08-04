import { expect, test } from "@playwright/test";
import { MODERATOR, SECOND_MODERATOR, seedAppeal } from "./seed";
import { signIn } from "./sign-in";

/**
 * The metrics screen: whether the queues are being served, and whether appeals overturn decisions.
 *
 * <p>Requires the stack from apps/web/README.md.
 */

test("overturning an appeal moves the number the documents ask for", async ({ page }) => {
  // The whole feature in one journey, and the only measurement docs/MODERATION.md names by name.
  // Signed in as the second moderator throughout: the original decider may not hear the appeal,
  // which is the rule the appeals queue enforces.
  const appealed = await seedAppeal();
  await signIn(page, SECOND_MODERATOR);

  await page.goto("/metrics");
  const before = countsFrom(await page.getByTestId("overturn-line").innerText());

  await page.goto("/moderation/appeals");
  const appeal = page.getByTestId("appeal").filter({ hasText: appealed.reviewId.slice(0, 8) });
  await appeal.getByLabel("Why").fill("The review named nobody.");
  await appeal.getByRole("button", { name: "Overturn and restore" }).click();
  await expect(
    page.getByTestId("appeal").filter({ hasText: appealed.reviewId.slice(0, 8) }),
  ).toHaveCount(0);

  await page.goto("/metrics");
  const after = countsFrom(await page.getByTestId("overturn-line").innerText());

  expect(after.overturned).toBe(before.overturned + 1);
  // Heard rises with it: an overturn is an appeal that was heard, and a screen where the two could
  // drift apart could show a rate above 100%.
  expect(after.heard).toBe(before.heard + 1);
});

/** Reads "3 of 14 overturned (21%)", or the empty-window sentence, back into numbers. */
function countsFrom(line: string): { overturned: number; heard: number } {
  const matched = /^(\d+) of (\d+) overturned/.exec(line);
  return matched
    ? { overturned: Number(matched[1]), heard: Number(matched[2]) }
    : { overturned: 0, heard: 0 };
}

test("the queue block says how long the oldest case has been waiting", async ({ page }) => {
  await seedAppeal();
  await signIn(page, MODERATOR);
  await page.goto("/metrics");

  const rows = page.getByTestId("metric-row");
  await expect(rows.filter({ hasText: "Open moderation cases" })).toHaveCount(1);
  // Freshly seeded, so the oldest open case arrived today rather than days ago.
  await expect(rows.filter({ hasText: "Oldest open case" })).toContainText(/today|day/);
});

test("the metrics name nobody, and say where names live instead", async ({ page }) => {
  // Deliberate product decision, stated on the screen rather than left to be discovered: per
  // moderator numbers would turn an access-review tool into a productivity leaderboard.
  await seedAppeal();
  await signIn(page, MODERATOR);
  await page.goto("/metrics");

  await expect(page.getByTestId("no-names")).toContainText("platform totals");
  await expect(page.getByTestId("no-names").getByRole("link", { name: "audit timeline" })).toBeVisible();
});

test("the window is stated, both days included", async ({ page }) => {
  await seedAppeal();
  await signIn(page, MODERATOR);

  await page.goto("/metrics?since=2020-01-01&until=2020-01-02");

  await expect(page.getByRole("heading", { name: /2020-01-01 to 2020-01-02/ })).toBeVisible();
  // A window from before the platform existed: nothing was decided in it, and the screen says that
  // rather than reporting a rate over nothing.
  await expect(page.getByTestId("overturn-line")).toHaveText("No appeals heard in this window");
});

test("an ordinary account gets no metrics", async ({ page }) => {
  await seedAppeal();
  await signIn(page, "mariam-resident");
  await page.goto("/metrics");

  await expect(page.getByTestId("metrics-error")).toHaveText("This account is not a moderator.");
  await expect(page.getByTestId("metric-row")).toHaveCount(0);
});
