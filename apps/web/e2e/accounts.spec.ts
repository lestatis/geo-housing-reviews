import { expect, test } from "@playwright/test";
import { MODERATOR, SECOND_MODERATOR, seedAccounts } from "./seed";
import { signIn } from "./sign-in";

/**
 * The account screen: find somebody by the pseudonym on their review, see where they stand, and
 * change it.
 *
 * <p>Requires the stack from apps/web/README.md.
 */

test("an administrator finds an account by the pseudonym on a review", async ({ page }) => {
  const seeded = await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto("/accounts");

  await page.getByLabel("Find an account by pseudonym").fill(seeded.residentPseudonym);
  await page.getByRole("button", { name: "Find" }).click();

  await page.getByTestId("account-hit").click();
  await expect(page.getByTestId("role")).toHaveText("User");
  await expect(page.getByTestId("standing")).toHaveText("Free to contribute");
});

test("a pseudonym nobody uses finds nothing", async ({ page }) => {
  await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto("/accounts?pseudonym=Nobody-At-All");

  await expect(page.locator("main").getByRole("alert")).toContainText("No account uses");
  await expect(page.getByTestId("account-hit")).toHaveCount(0);
});

test("an administrator restricts an account and then lifts it", async ({ page }) => {
  const seeded = await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto(`/accounts/${seeded.residentAccountId}`);
  await page.waitForLoadState("networkidle");

  await page.getByLabel("Why (the account can be told this)").fill("posted a neighbour's number");
  await page.getByRole("button", { name: "Restrict this account" }).click();

  await expect(page.getByTestId("standing")).toHaveText("Restricted");
  await expect(page.getByTestId("restriction-state")).toHaveText("In force");

  await page.getByRole("button", { name: "Lift" }).click();

  await expect(page.getByTestId("standing")).toHaveText("Free to contribute");
  // The record survives the lift — an appeal needs to see that it happened.
  await expect(page.getByTestId("restriction")).toHaveCount(1);
  await expect(page.getByTestId("restriction-state")).toHaveText("Ended");
});

test("a restriction will not be placed without a reason", async ({ page }) => {
  const seeded = await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto(`/accounts/${seeded.residentAccountId}`);
  await page.waitForLoadState("networkidle");

  await page.getByRole("button", { name: "Restrict this account" }).click();

  await expect(page.locator("form").getByRole("alert")).toHaveText(
    "Say why this account is being restricted.",
  );
  await expect(page.getByTestId("standing")).toHaveText("Free to contribute");
});

test("an administrator grants and removes another account's access", async ({ page }) => {
  const seeded = await seedAccounts();
  await signIn(page, MODERATOR);
  await page.goto(`/accounts/${seeded.residentAccountId}`);
  await page.waitForLoadState("networkidle");

  await page.getByRole("button", { name: "Make administrator" }).click();
  await expect(page.getByTestId("role")).toHaveText("Admin");

  // Somebody else's demotion is not "stepping down", and is not labelled as though it were.
  await expect(page.getByRole("button", { name: "Remove administrative access" })).toBeVisible();
  await page.getByRole("button", { name: "Remove administrative access" }).click();
  await expect(page.getByTestId("role")).toHaveText("User");
});

test("the last administrator is told why they cannot step down", async ({ page }) => {
  const seeded = await seedAccounts();
  await signIn(page, MODERATOR);
  // Every other administrator gives up the role first, leaving exactly one.
  await page.goto(`/accounts/${seeded.secondModeratorAccountId}`);
  await page.waitForLoadState("networkidle");
  await page.getByRole("button", { name: "Remove administrative access" }).click();
  await expect(page.getByTestId("role")).toHaveText("User");

  await page.goto(`/accounts/${seeded.moderatorAccountId}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("button", { name: "Step down" })).toBeVisible();
  await page.getByRole("button", { name: "Step down" }).click();

  await expect(page.locator("form").getByRole("alert")).toContainText("only administrator");
  await expect(page.getByTestId("role")).toHaveText("Admin");

  // Put it back, because scenarios share a database and the next one needs two administrators.
  await page.goto(`/accounts/${seeded.secondModeratorAccountId}`);
  await page.waitForLoadState("networkidle");
  await page.getByRole("button", { name: "Make administrator" }).click();
  await expect(page.getByTestId("role")).toHaveText("Admin");
});

test("a restricted account cannot be restricted twice over", async ({ page }) => {
  const seeded = await seedAccounts();
  await signIn(page, SECOND_MODERATOR);
  await page.goto(`/accounts/${seeded.residentAccountId}`);
  await page.waitForLoadState("networkidle");

  await page.getByLabel("Why (the account can be told this)").fill("first");
  await page.getByRole("button", { name: "Restrict this account" }).click();
  await expect(page.getByTestId("standing")).toHaveText("Restricted");

  // The form is gone rather than offering an action the API would refuse.
  await expect(page.getByRole("button", { name: "Restrict this account" })).toHaveCount(0);
});
