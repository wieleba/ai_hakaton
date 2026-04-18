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

test.describe('Room re-entry', () => {
  test('after Leave + re-entry, sending a message still delivers in real time', async ({
    browser,
  }) => {
    const u = uniqueUser('reentry');
    const { ctx, page } = await registerAndLogin(browser, u.email, u.username, u.password);

    const roomName = `reentry-${Date.now().toString().slice(-7)}`;
    await page.goto('/rooms');
    await page.click('button:has-text("New Room")');
    await page.fill('input[placeholder="Enter room name"]', roomName);
    await page.click('button:has-text("Create"):not(:has-text("Cancel"))');
    // Navigates into the new room.
    await page.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    const textarea = page.getByPlaceholder(/type a message/i);
    await expect(textarea).toBeVisible();

    // First visit — send a message.
    await textarea.fill('first visit hello');
    await page.click('button:has-text("Send")');
    await expect(page.locator('body')).toContainText('first visit hello', { timeout: 5_000 });

    // Leave the room.
    await page.click('button:has-text("Leave Room")');
    await page.waitForURL(/.*\/rooms$/);

    // Re-enter via the room list (clicks the room card).
    await page.click(`text=${roomName}`);
    await page.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    const textarea2 = page.getByPlaceholder(/type a message/i);
    await expect(textarea2).toBeVisible();

    // Second visit — send another message.
    await textarea2.fill('second visit after re-entry');
    await page.click('button:has-text("Send")');

    // This is the regression check — before the fix, the message would NOT
    // appear in real time on this re-entry. Assert it shows up without
    // another page reload.
    await expect(page.locator('body')).toContainText('second visit after re-entry', {
      timeout: 5_000,
    });

    await ctx.close();
  });
});
