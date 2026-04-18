import { test, expect, Browser, Page } from '@playwright/test';

const password = 'password123';

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

test.describe('Friends', () => {
  test('Alice sends a friend request → outgoing list updates', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const bob = uniqueUser('bob');

    // Register Bob first so Alice can address him by username
    const bobSession = await registerAndLogin(browser, bob.email, bob.username, bob.password);
    await bobSession.ctx.close();

    const { ctx: aliceCtx, page: alicePage } = await registerAndLogin(
      browser,
      alice.email,
      alice.username,
      alice.password,
    );

    await alicePage.goto('/friends');
    await alicePage.fill('input[placeholder="Username"]', bob.username);
    await alicePage.click('button:has-text("Send request")');

    // The outgoing requests section should now contain a "To user <prefix>"
    // line and a Cancel button.
    await expect(alicePage.locator('body')).toContainText('To user', { timeout: 5_000 });
    await expect(alicePage.locator('button:has-text("Cancel")')).toBeVisible();

    await aliceCtx.close();
  });

  test('Bob accepts → friendship visible on both sides', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const bob = uniqueUser('bob');

    const bobSession = await registerAndLogin(browser, bob.email, bob.username, bob.password);
    await bobSession.ctx.close();

    // Alice sends request
    const aliceSession = await registerAndLogin(
      browser,
      alice.email,
      alice.username,
      alice.password,
    );
    await aliceSession.page.goto('/friends');
    await aliceSession.page.fill('input[placeholder="Username"]', bob.username);
    await aliceSession.page.click('button:has-text("Send request")');
    await expect(aliceSession.page.locator('button:has-text("Cancel")')).toBeVisible();

    // Bob logs back in and sees the incoming request
    const bobCtx = await browser.newContext();
    const bobPage = await bobCtx.newPage();
    await bobPage.goto('/login');
    await bobPage.fill('#email', bob.email);
    await bobPage.fill('#password', bob.password);
    await bobPage.click('button[type="submit"]');
    await bobPage.waitForURL(/.*\/rooms$/);
    await bobPage.goto('/friends');

    await expect(bobPage.locator('body')).toContainText('Request from user', { timeout: 5_000 });
    await bobPage.click('button:has-text("Accept")');

    // Friendship now appears on both sides
    await expect(bobPage.locator('body')).toContainText(alice.username, { timeout: 5_000 });

    await aliceSession.page.reload();
    await expect(aliceSession.page.locator('body')).toContainText(bob.username, {
      timeout: 5_000,
    });

    await bobCtx.close();
    await aliceSession.ctx.close();
  });
});
