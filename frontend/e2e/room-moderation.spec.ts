import { test, expect, Browser, Page } from '@playwright/test';

const password = 'password123';
const BACKEND_URL = process.env.E2E_BACKEND_URL || 'http://localhost:8080';

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
): Promise<{ ctx: Awaited<ReturnType<Browser['newContext']>>; page: Page; token: string }> {
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
  const token = (await page.evaluate(() => localStorage.getItem('authToken'))) ?? '';
  return { ctx, page, token };
}

test.describe('Room moderation', () => {
  test('create private → invite → accept → kick → unban → delete lifecycle', async ({
    browser,
  }) => {
    const owner = uniqueUser('owner');
    const bob = uniqueUser('bob');

    // Both register and log in
    const bobSession = await registerAndLogin(browser, bob.email, bob.username, bob.password);
    const bobToken = bobSession.token;
    await bobSession.ctx.close();

    const { ctx: ownerCtx, page: ownerPage, token: ownerToken } = await registerAndLogin(
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
    await ownerPage.click('div.fixed button:has-text("Create"):not(:has-text("Cancel"))');
    await ownerPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    const roomId = ownerPage.url().split('/rooms/')[1];

    // Owner invites Bob via "Invite user" button in the right panel
    await expect(ownerPage.getByRole('button', { name: 'Invite user' })).toBeVisible();
    await ownerPage.click('button:has-text("Invite user")');
    await ownerPage.fill('input[placeholder="Username"]', bob.username);
    await ownerPage.click('button:has-text("Send invitation")');
    // Modal closes on successful invite — wait before tearing down the context.
    await expect(ownerPage.getByRole('heading', { name: 'Invite user to this room' })).toBeHidden({
      timeout: 10_000,
    });

    await ownerCtx.close();

    // Bob accepts the invitation via API (no accept UI exists in the refactored shell)
    const invRes = await fetch(`${BACKEND_URL}/api/invitations`, {
      headers: { Authorization: `Bearer ${bobToken}` },
    });
    expect(invRes.ok).toBeTruthy();
    const invitations: Array<{ id: string }> = await invRes.json();
    const inv = invitations.find((_) => true); // first (only) invitation
    expect(inv).toBeDefined();
    const acceptRes = await fetch(`${BACKEND_URL}/api/invitations/${inv!.id}/accept`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${bobToken}` },
    });
    expect(acceptRes.ok).toBeTruthy();

    // Bob logs back in and enters the room directly
    const bobCtx = await browser.newContext();
    const bobPage = await bobCtx.newPage();
    await bobPage.goto('/login');
    await bobPage.fill('#email', bob.email);
    await bobPage.fill('#password', bob.password);
    await bobPage.click('button[type="submit"]');
    await bobPage.waitForURL(/.*\/rooms$/);

    await bobPage.goto(`/rooms/${roomId}`);
    await bobPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await expect(bobPage.getByPlaceholder(/type a message/i)).toBeVisible();

    await bobCtx.close();

    // Owner logs back in and kicks Bob via ManageRoomModal > Members tab
    const ownerCtx2 = await browser.newContext();
    const ownerPage2 = await ownerCtx2.newPage();
    await ownerPage2.goto('/login');
    await ownerPage2.fill('#email', owner.email);
    await ownerPage2.fill('#password', owner.password);
    await ownerPage2.click('button[type="submit"]');
    await ownerPage2.waitForURL(/.*\/rooms$/);
    await ownerPage2.goto(`/rooms/${roomId}`);
    await ownerPage2.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    // Open ManageRoomModal — Members tab is default
    await ownerPage2.click('button:has-text("Manage room")');
    await expect(ownerPage2.getByRole('heading', { name: 'Manage room' })).toBeVisible();

    // Wait for Bob's row to be visible in the members list, then kick
    const bobKickBtn = ownerPage2
      .locator('div[class*="fixed"]')
      .locator('li')
      .filter({ hasText: bob.username })
      .locator('button:has-text("Kick")');
    await expect(bobKickBtn).toBeVisible({ timeout: 10_000 });
    await bobKickBtn.click();

    // Switch to Banned tab, confirm Bob is there, unban
    await ownerPage2.click('button:has-text("Banned")');
    await expect(ownerPage2.locator('body')).toContainText(bob.username, { timeout: 5_000 });
    await ownerPage2.click('button:has-text("Unban")');

    // Switch to Settings tab and delete the room
    await ownerPage2.click('button:has-text("Settings")');
    await ownerPage2.click('button:has-text("Delete room")');
    await ownerPage2.click('button:has-text("Confirm delete")');
    await ownerPage2.waitForURL(/.*\/rooms$/, { timeout: 5_000 });

    await ownerCtx2.close();
  });
});
