import type { Page } from "@playwright/test";

/**
 * Signs in the way a person does: through the identity provider's login form, not by planting a
 * cookie. Every journey starts here, so every journey exercises the real authorization-code flow.
 */
export async function signIn(page: Page, username: string): Promise<void> {
  await page.goto("/");
  await page.getByRole("link", { name: "Sign in" }).click();
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[type="submit"]').click();
}
