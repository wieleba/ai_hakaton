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

test.describe('Message content — rooms', () => {
  test('reply + edit + delete flow visible to both clients', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const bob = uniqueUser('bob');

    // Alice registers and creates a room
    const { ctx: aliceCtx, page: alicePage } = await registerAndLogin(
      browser,
      alice.email,
      alice.username,
      alice.password,
    );
    const roomName = `msg-${Date.now().toString().slice(-7)}`;
    await alicePage.click('button:has-text("New Room")');
    await alicePage.fill('input[placeholder="Enter room name"]', roomName);
    await alicePage.click('div.fixed button:has-text("Create"):not(:has-text("Cancel"))');
    await alicePage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    const roomUrl = alicePage.url();

    // Alice sends the first message
    const aliceInput = alicePage.getByPlaceholder(/type a message/i);
    await aliceInput.fill('hello from alice');
    await alicePage.keyboard.press('Control+Enter');
    await expect(alicePage.locator('body')).toContainText('hello from alice');

    // Bob joins the same room directly
    const { ctx: bobCtx, page: bobPage } = await registerAndLogin(
      browser,
      bob.email,
      bob.username,
      bob.password,
    );
    await bobPage.goto(roomUrl);
    await bobPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await expect(bobPage.locator('body')).toContainText('hello from alice', { timeout: 5_000 });

    // Bob replies to Alice's message
    const aliceRowOnBob = bobPage.locator('div.group').filter({ hasText: 'hello from alice' }).first();
    await aliceRowOnBob.hover();
    await aliceRowOnBob.getByRole('button', { name: /Reply/ }).click();
    await expect(bobPage.locator('body')).toContainText(/Replying to/);
    const bobInput = bobPage.getByPlaceholder(/type a message/i);
    await bobInput.fill('hi alice');
    await bobPage.keyboard.press('Control+Enter');
    await expect(bobPage.locator('body')).toContainText('hi alice');
    await expect(alicePage.locator('body')).toContainText('hi alice', { timeout: 5_000 });

    // Alice edits her own message — target the row that has the <p> with her exact text
    const aliceOwnRowOnAlice = alicePage
      .locator('div.group')
      .filter({ has: alicePage.locator('p', { hasText: 'hello from alice' }) })
      .first();
    await aliceOwnRowOnAlice.hover();
    await expect(aliceOwnRowOnAlice.getByRole('button', { name: /Edit/ })).toBeVisible();
    await aliceOwnRowOnAlice.getByRole('button', { name: /Edit/ }).click();
    const editor = alicePage.locator('textarea').nth(0); // inline editor comes first in DOM (before MessageInput)
    await editor.fill('hello from alice (edited content)');
    await editor.press('Control+Enter');
    await expect(alicePage.locator('body')).toContainText('(edited)', { timeout: 5_000 });
    await expect(bobPage.locator('body')).toContainText('hello from alice (edited content)', {
      timeout: 5_000,
    });

    // Alice deletes her own message
    alicePage.once('dialog', (d) => d.accept());
    const aliceEditedRow = alicePage
      .locator('div.group')
      .filter({ has: alicePage.locator('p', { hasText: /edited content/ }) })
      .first();
    await aliceEditedRow.hover();
    await expect(aliceEditedRow.getByRole('button', { name: /Delete/ })).toBeVisible();
    await aliceEditedRow.getByRole('button', { name: /Delete/ }).click();
    await expect(alicePage.locator('body')).toContainText(/Message deleted/);
    await expect(bobPage.locator('body')).toContainText(/Message deleted/, { timeout: 5_000 });

    await aliceCtx.close();
    await bobCtx.close();
  });
});
