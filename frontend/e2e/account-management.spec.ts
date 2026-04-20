import { test, expect, Browser, Page } from '@playwright/test';

const password = 'password12345';

function uniqueUser(prefix: string) {
  const stamp = Date.now().toString().slice(-7) + Math.floor(Math.random() * 1000);
  const username = `${prefix}${stamp}`;
  return { username, email: `${username}@example.com`, password };
}

async function registerAndLogin(
  browser: Browser,
  email: string,
  username: string,
  pw: string,
): Promise<{ ctx: Awaited<ReturnType<Browser['newContext']>>; page: Page }> {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto('/register');
  await page.fill('#email', email);
  await page.fill('#username', username);
  await page.fill('#password', pw);
  await page.fill('#confirm-password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/login$/);
  await page.fill('#email', email);
  await page.fill('#password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/rooms$/);
  return { ctx, page };
}

test.describe('Account management', () => {
  test('change password → log out → log in with new password', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const { ctx, page } = await registerAndLogin(
      browser,
      alice.email,
      alice.username,
      alice.password,
    );

    // Open profile menu and navigate to account settings, then click "Change password"
    await page.getByRole('button', { name: new RegExp(alice.username) }).click();
    await page.getByRole('menuitem', { name: 'Account settings' }).click();
    await page.waitForURL(/.*\/account$/);
    await page.getByRole('button', { name: 'Change password' }).click();

    await page.fill('input[placeholder="Current password"]', alice.password);
    const newPassword = 'newpassword123';
    await page.fill('input[placeholder^="New password"]', newPassword);
    await page.fill('input[placeholder="Confirm new password"]', newPassword);
    await page.click('div.fixed button:has-text("Change password"):not(:has-text("Cancel"))');

    // Modal closes on success; log out
    await page.getByRole('button', { name: new RegExp(alice.username) }).click();
    await page.getByRole('menuitem', { name: 'Sign out' }).click();
    await page.waitForURL(/.*\/login$/);

    // Log in with new password
    await page.fill('#email', alice.email);
    await page.fill('#password', newPassword);
    await page.click('button[type="submit"]');
    await page.waitForURL(/.*\/rooms$/);

    await ctx.close();
  });

  test('delete account → redirected to /login → same email re-registers', async ({ browser }) => {
    const bob = uniqueUser('bob');
    const { ctx, page } = await registerAndLogin(
      browser,
      bob.email,
      bob.username,
      bob.password,
    );

    await page.getByRole('button', { name: new RegExp(bob.username) }).click();
    await page.getByRole('menuitem', { name: 'Account settings' }).click();
    await page.waitForURL(/.*\/account$/);
    await page.getByRole('button', { name: 'Delete account' }).click();

    await page.fill('input[autocomplete="off"]', 'DELETE');
    await page.click('div.fixed button:has-text("Delete account"):not(:has-text("Cancel"))');
    await page.waitForURL(/.*\/login$/, { timeout: 10_000 });

    // Re-register with the same email to confirm the account is truly gone
    await page.goto('/register');
    await page.fill('#email', bob.email);
    await page.fill('#username', bob.username);
    await page.fill('#password', bob.password);
    await page.fill('#confirm-password', bob.password);
    await page.click('button[type="submit"]');
    await page.waitForURL(/.*\/login$/);

    await ctx.close();
  });
});
