import { expect, test } from "@playwright/test";
import { MODERATOR, SECOND_MODERATOR, seed, seedAppeal } from "./seed";
import { signIn } from "./sign-in";

/**
 * Loop 5, done through the screen instead of curl: a moderator opens a reported case, reads the
 * concern, records a decision with a reason the author can act on — and a second moderator hears
 * the appeal that follows.
 *
 * <p>Requires the stack from apps/web/README.md.
 */

test("a moderator opens a reported case and reads the concern without learning who raised it", async ({
  page,
}) => {
  const seeded = await seed();
  await signIn(page, MODERATOR);

  await page.getByRole("link", { name: new RegExp(seeded.reviewId.slice(0, 8)) }).click();

  await expect(page).toHaveURL(/\/moderation\/[0-9a-f-]{36}$/);
  const concern = page.getByTestId("concern");
  await expect(concern).toHaveCount(1);
  await expect(concern).toContainText("Personal data");
  await expect(concern).toContainText("Mentions a neighbour by flat number.");

  // The concern's substance, never its author.
  const shown = await page.locator("main").innerText();
  expect(shown).not.toContain("giorgi");
  expect(shown).not.toContain("neighbour-");
});

test("a takedown will not be recorded without telling the author why", async ({ page }) => {
  const seeded = await seed();
  await signIn(page, MODERATOR);
  await page.goto(`/moderation/${seeded.caseId}`);

  await page.getByLabel("What to do").selectOption("REMOVE");
  await page.getByLabel("Reason code").fill("DOXXING");
  await page.getByRole("button", { name: "Record decision" }).click();

  await expect(page.locator("form").getByRole("alert")).toHaveText(
    "This action takes something away, so the author must be told why.",
  );
  // Still on the case, and nothing recorded.
  await expect(page).toHaveURL(`/moderation/${seeded.caseId}`);
  await expect(page.getByTestId("decision")).toHaveCount(0);
});

test("a moderator records a decision and it appears in the case history", async ({ page }) => {
  const seeded = await seed();
  await signIn(page, MODERATOR);
  await page.goto(`/moderation/${seeded.caseId}`);

  await page.getByLabel("What to do").selectOption("REMOVE");
  await page.getByLabel("Reason code").fill("DOXXING");
  await page.getByLabel("What the author is told").fill("Your review named a neighbour.");
  await page.getByLabel("Internal note (moderators only)").fill("Third from this account.");
  await page.getByRole("button", { name: "Record decision" }).click();

  await expect(page).toHaveURL("/moderation");

  await page.goto(`/moderation/${seeded.caseId}`);
  const decision = page.getByTestId("decision");
  await expect(decision).toHaveCount(1);
  await expect(decision).toContainText("Remove");
  await expect(decision).toContainText("DOXXING");
  await expect(page.getByTestId("public-explanation")).toContainText(
    "Your review named a neighbour.",
  );
  // The note is a moderator's, kept in its own field rather than folded into what the author was
  // told — the two must never become one string.
  await expect(page.getByTestId("internal-note")).toContainText("Third from this account.");
  await expect(page.getByTestId("public-explanation")).not.toContainText("Third from this account.");
});

test("a second moderator hears an appeal and can read the decision it challenges", async ({
  page,
}) => {
  const appealed = await seedAppeal();
  await signIn(page, SECOND_MODERATOR);
  await page.goto("/moderation/appeals");

  const appeal = page.getByTestId("appeal").filter({ hasText: appealed.reviewId.slice(0, 8) });
  await expect(appeal).toHaveCount(1);
  await expect(appeal.getByTestId("contested")).toHaveText("Remove · DOXXING");
  await expect(appeal.getByTestId("appeal-text")).toContainText("I never named anyone.");
  // Whoever picks this up needs to know the decision was not theirs.
  await expect(appeal.getByTestId("original-decider")).not.toHaveText("—");

  await appeal.getByLabel("Why").fill("The review named nobody.");
  await appeal.getByRole("button", { name: "Overturn and restore" }).click();

  await expect(page).toHaveURL("/moderation/appeals");
  await expect(
    page.getByTestId("appeal").filter({ hasText: appealed.reviewId.slice(0, 8) }),
  ).toHaveCount(0);
});

test("an appeal cannot be heard without explaining the outcome", async ({ page }) => {
  const appealed = await seedAppeal();
  await signIn(page, SECOND_MODERATOR);
  await page.goto("/moderation/appeals");

  const appeal = page.getByTestId("appeal").filter({ hasText: appealed.reviewId.slice(0, 8) });
  await appeal.getByRole("button", { name: "Uphold the decision" }).click();

  await expect(appeal.getByRole("alert")).toHaveText(
    "An appeal outcome has to be explained to the person who filed it.",
  );
});

test("the moderator being appealed against is refused, and told why", async ({ page }) => {
  const appealed = await seedAppeal();
  // MODERATOR made the decision under appeal; the rule is enforced by the API, and the screen has
  // to say what happened rather than failing silently.
  await signIn(page, MODERATOR);
  await page.goto("/moderation/appeals");

  const appeal = page.getByTestId("appeal").filter({ hasText: appealed.reviewId.slice(0, 8) });
  await appeal.getByLabel("Why").fill("On reflection, I was wrong.");
  await appeal.getByRole("button", { name: "Overturn and restore" }).click();

  await expect(appeal.getByRole("alert")).toHaveText(
    "An appeal cannot be heard by the moderator whose decision it challenges.",
  );
});
