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

test.describe('Room moderation', () => {
  test('create private → invite → accept → kick → unban → delete lifecycle', async ({
    browser,
  }) => {
    const owner = uniqueUser('owner');
    const bob = uniqueUser('bob');

    // Both register and log in
    const bobSession = await registerAndLogin(browser, bob.email, bob.username, bob.password);
    await bobSession.ctx.close();

    const { ctx: ownerCtx, page: ownerPage } = await registerAndLogin(
      browser,
      owner.email,
      owner.username,
      owner.password,
    );

    // Owner creates a private room
    const roomName = `priv-${Date.now().toString().slice(-7)}`;
    await ownerPage.click('button:has-text("New Room")');
    await ownerPage.fill('input[placeholder="Enter room name"]', roomName);
    await ownerPage.check('input[value="private"]');
    await ownerPage.click('button:has-text("Create"):not(:has-text("Cancel"))');
    await ownerPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    // Owner invites Bob
    await ownerPage.click('button:has-text("Invite")');
    await ownerPage.fill('input[placeholder="Username"]', bob.username);
    await ownerPage.click('button:has-text("Send invitation")');

    await ownerCtx.close();

    // Bob logs back in and sees the invitation
    const bobCtx = await browser.newContext();
    const bobPage = await bobCtx.newPage();
    await bobPage.goto('/login');
    await bobPage.fill('#email', bob.email);
    await bobPage.fill('#password', bob.password);
    await bobPage.click('button[type="submit"]');
    await bobPage.waitForURL(/.*\/rooms$/);

    await bobPage.click('button:has-text("My rooms")');
    await expect(bobPage.locator('body')).toContainText(roomName, { timeout: 5_000 });
    await bobPage.click('button:has-text("Accept")');
    await expect(bobPage.locator('body')).toContainText(roomName);

    // Bob enters the room
    await bobPage.click(`text=${roomName}`);
    await bobPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await expect(bobPage.getByPlaceholder(/type a message/i)).toBeVisible();

    await bobCtx.close();

    // Owner kicks Bob
    const ownerCtx2 = await browser.newContext();
    const ownerPage2 = await ownerCtx2.newPage();
    await ownerPage2.goto('/login');
    await ownerPage2.fill('#email', owner.email);
    await ownerPage2.fill('#password', owner.password);
    await ownerPage2.click('button[type="submit"]');
    await ownerPage2.waitForURL(/.*\/rooms$/);
    await ownerPage2.click('button:has-text("My rooms")');
    await ownerPage2.click(`text=${roomName}`);
    await ownerPage2.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    // Click Kick on Bob's row
    const bobRow = ownerPage2.locator('li').filter({ hasText: bob.username }).first();
    await bobRow.locator('button:has-text("Kick")').click();

    // Open bans, confirm Bob is there, unban
    await ownerPage2.click('button:has-text("Bans")');
    await expect(ownerPage2.locator('body')).toContainText(bob.username, { timeout: 5_000 });
    await ownerPage2.click('button:has-text("Unban")');

    // Close bans panel
    await ownerPage2.click('button:has-text("×")');

    // Delete the room
    await ownerPage2.click('button:has-text("Delete Room")');
    await ownerPage2.click('div.fixed button:has-text("Delete room"):not(:has-text("?"))');
    await ownerPage2.waitForURL(/.*\/rooms$/, { timeout: 5_000 });

    await ownerCtx2.close();
  });
});
