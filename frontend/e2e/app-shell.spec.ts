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

test.describe('App shell', () => {
  test('top menu navigates to each primary route', async ({ browser }) => {
    const alice = uniqueUser('alice');
    const { ctx, page } = await registerAndLogin(
      browser,
      alice.email,
      alice.username,
      alice.password,
    );

    await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible();
    await expect(page.getByRole('complementary', { name: 'Workspace' })).toBeVisible();

    await page.getByRole('link', { name: 'Contacts' }).click();
    await page.waitForURL(/.*\/friends$/);
    await expect(page.locator('body')).toContainText(/friends/i);

    await page.getByRole('link', { name: 'Sessions' }).click();
    await page.waitForURL(/.*\/sessions$/);
    await expect(page.locator('body')).toContainText(/active sessions/i);

    await page.getByRole('link', { name: 'Public Rooms' }).click();
    await page.waitForURL(/.*\/rooms$/);
    await expect(page.locator('body')).toContainText(/public rooms/i);

    await ctx.close();
  });

  test('search finds a public room and clicking joins + navigates', async ({ browser }) => {
    const bob = uniqueUser('owner');
    const { ctx: ownerCtx, page: ownerPage } = await registerAndLogin(
      browser,
      bob.email,
      bob.username,
      bob.password,
    );
    const roomName = `searchable-${Date.now().toString().slice(-7)}`;
    // Use the RoomListPage New Room button (navigates after creation)
    await ownerPage.click('button:has-text("New Room")');
    await ownerPage.fill('input[placeholder="Enter room name"]', roomName);
    await ownerPage.click('div.fixed button:has-text("Create"):not(:has-text("Cancel"))');
    await ownerPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);
    await ownerCtx.close();

    const carol = uniqueUser('searcher');
    const { ctx: searcherCtx, page: searcherPage } = await registerAndLogin(
      browser,
      carol.email,
      carol.username,
      carol.password,
    );

    const searchBox = searcherPage.getByPlaceholder('Search rooms or users…');
    await searchBox.click();
    // Use the full room name — the DB accumulates rooms across runs, and the
    // SearchService returns only 5 rows ordered by name, so a short prefix
    // may push the fresh room out of the result page.
    await searchBox.fill(roomName);
    await expect(searcherPage.locator('body')).toContainText(roomName, { timeout: 5_000 });
    await searcherPage.getByRole('button', { name: new RegExp(`# ${roomName}`) }).click();
    await searcherPage.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    await searcherCtx.close();
  });

  test('ManageRoomModal tabs open from the right panel for the room owner', async ({
    browser,
  }) => {
    const dave = uniqueUser('owner');
    const { ctx, page } = await registerAndLogin(
      browser,
      dave.email,
      dave.username,
      dave.password,
    );
    const roomName = `mgrtest-${Date.now().toString().slice(-7)}`;
    // Use the RoomListPage New Room button (navigates after creation)
    await page.click('button:has-text("New Room")');
    await page.fill('input[placeholder="Enter room name"]', roomName);
    await page.click('div.fixed button:has-text("Create"):not(:has-text("Cancel"))');
    await page.waitForURL(/.*\/rooms\/[0-9a-f-]{36}$/);

    await page.getByRole('button', { name: 'Manage room' }).click();
    await expect(page.getByRole('heading', { name: 'Manage room' })).toBeVisible();

    await page.getByRole('button', { name: 'Invitations' }).click();
    await expect(page.locator('body')).toContainText(/no pending invitations|invitation/i);

    await page.getByRole('button', { name: 'Banned' }).click();
    await expect(page.locator('body')).toContainText(/no banned users|banned by/i);

    await page.getByRole('button', { name: 'Settings' }).click();
    await expect(page.locator('body')).toContainText(/delete room/i);

    await ctx.close();
  });
});
