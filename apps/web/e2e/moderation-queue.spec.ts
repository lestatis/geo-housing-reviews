import { expect, test } from "@playwright/test";
import { MODERATOR, seed } from "./seed";

/**
 * The whole of loop 5's first step, done the way a moderator would: sign in at the identity
 * provider, land on the queue, and see the case somebody reported.
 *
 * <p>Requires the stack from apps/web/README.md. These are the assertions unit tests cannot make:
 * that the authorization-code flow completes, that the session survives the redirect, and that the
 * page a moderator actually looks at does not name the person who complained.
 */

let seeded: { reviewId: string };

test.beforeAll(async () => {
  seeded = await seed();
});

async function signIn(page: import("@playwright/test").Page, username: string) {
  await page.goto("/");
  await page.getByRole("link", { name: "Sign in" }).click();
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[type="submit"]').click();
}

test("a moderator signs in and finds the reported review waiting", async ({ page }) => {
  await signIn(page, MODERATOR);

  await expect(page).toHaveURL(/\/moderation$/);
  await expect(page.getByRole("heading", { name: "Moderation queue" })).toBeVisible();

  const row = page.getByTestId("case-row").filter({ hasText: seeded.reviewId.slice(0, 8) });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText("Report");
  await expect(row).toContainText("1 concern");
});

test("the queue never says who complained", async ({ page }) => {
  await signIn(page, MODERATOR);

  const page_text = await page.locator("main").innerText();

  // A moderator decides on the content. Knowing which neighbour reported it is how a queue becomes
  // a way to retaliate, so the API counts concerns instead of listing reporters — and the screen
  // must not undo that.
  expect(page_text).not.toContain("giorgi");
  expect(page_text).not.toContain("neighbour");
  expect(page_text).toContain("concern");
});

test("someone who is not a moderator gets no queue", async ({ page }) => {
  await signIn(page, "mariam-resident");

  await expect(page.locator("main").getByRole("alert")).toHaveText(
    "This account is not a moderator.",
  );
  await expect(page.getByTestId("case-row")).toHaveCount(0);
});

test("a session the API rejects is sent back to sign in, not shown an empty queue", async ({
  page,
  context,
}) => {
  await signIn(page, MODERATOR);
  const [session] = await context.cookies();
  await context.clearCookies();
  await context.addCookies([{ ...session, value: `${session.value}tampered` }]);

  await page.goto("/moderation");

  // An empty queue would read as "nothing to moderate", which is the one wrong thing to say when
  // the truth is "we could not ask". The dead cookie has to be cleared on the way out, or "/" sends
  // the visitor straight back and the two pages redirect at each other forever.
  await expect(page).toHaveURL("/?error=session_expired");
  await expect(page.locator("main").getByRole("alert")).toHaveText(
    "That session has ended. Please sign in again.",
  );
  await expect(page.getByRole("link", { name: "Sign in" })).toBeVisible();
  expect(await context.cookies()).toHaveLength(0);
});

test("signing out ends the session", async ({ page }) => {
  await signIn(page, MODERATOR);
  await page.getByRole("button", { name: "Sign out" }).click();

  await expect(page).toHaveURL("/");
  await page.goto("/moderation");
  await expect(page).toHaveURL("/");
});
