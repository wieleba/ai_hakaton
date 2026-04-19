import { test, expect, Browser, Page } from '@playwright/test';
import path from 'path';
import fs from 'fs';
import os from 'os';

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

// 1x1 transparent PNG
const tinyPngBase64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

function writeTempPng(): string {
  const file = path.join(os.tmpdir(), `tiny-${Date.now()}.png`);
  fs.writeFileSync(file, Buffer.from(tinyPngBase64, 'base64'));
  return file;
}

test.describe('Attachments', () => {
  test('image upload in a room is visible to both clients', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const bob = uniqueUser('bob');
    const { ctx: aCtx, page: aPage } = await registerAndLogin(
      browser, alice.email, alice.username, alice.password);

    // Alice creates a room
    const roomName = `att-${Date.now().toString().slice(-7)}`;
    await aPage.click('button:has-text("New Room")');
    await aPage.fill('input[placeholder="Enter room name"]', roomName);
    await aPage.click('div.fixed button:has-text("Create"):not(:has-text("Cancel"))');
    await aPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    const roomUrl = aPage.url();

    // Stage a tiny PNG via the hidden file input
    const tmpFile = writeTempPng();
    await aPage.locator('input[type="file"]').setInputFiles(tmpFile);

    // Preview chip appears — filename starts with "tiny-"
    await expect(aPage.locator('body')).toContainText('tiny-');

    await aPage.getByPlaceholder(/type a message/i).fill('here is a pic');
    await aPage.keyboard.press('Control+Enter');

    // Alice sees her own message with an <img>
    await expect(aPage.locator('img[alt*="tiny-"]').first()).toBeVisible({ timeout: 10_000 });

    // Bob joins the same room URL and sees the image
    const { ctx: bCtx, page: bPage } = await registerAndLogin(
      browser, bob.email, bob.username, bob.password);
    await bPage.goto(roomUrl);
    await bPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await expect(bPage.locator('img[alt*="tiny-"]').first()).toBeVisible({ timeout: 10_000 });

    fs.unlinkSync(tmpFile);
    await aCtx.close();
    await bCtx.close();
  });
});
