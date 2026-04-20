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

async function loginIn(ctx: Awaited<ReturnType<Browser['newContext']>>, email: string, pw: string): Promise<Page> {
  const page = await ctx.newPage();
  await page.goto('/login');
  await page.fill('#email', email);
  await page.fill('#password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/rooms$/);
  return page;
}

/**
 * Create a room via REST using the browser's stored JWT, then navigate into it.
 * The backend only records a WS session in the presence registry when a user
 * enters a chat page (useWebSocket mounts there), so we need to actually land on
 * /rooms/:id for /api/sessions to see a session row.
 */
async function openRoomAsChat(page: Page, roomName: string): Promise<string> {
  const roomId = await page.evaluate(async (name) => {
    const r = await fetch('/api/rooms', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('authToken')}`,
      },
      body: JSON.stringify({ name, description: 'sessions e2e', visibility: 'public' }),
    });
    return (await r.json()).id as string;
  }, roomName);
  await page.goto(`/rooms/${roomId}`);
  return roomId;
}

async function joinAndOpenRoom(page: Page, roomId: string) {
  await page.evaluate(async (id) => {
    await fetch(`/api/rooms/${id}/join`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('authToken')}` },
    });
  }, roomId);
  await page.goto(`/rooms/${roomId}`);
}

test.describe('Sessions management', () => {
  test('two tabs see each other; logout-others kicks the other', async ({ browser }) => {
    const u = uniqueUser('sess');
    const { ctx: ctxA, page: pageA } = await registerAndLogin(browser, u.email, u.username, u.password);
    const ctxB = await browser.newContext();
    const pageB = await loginIn(ctxB, u.email, u.password);

    // Enter a chat room on both tabs so each opens a WebSocket.
    const roomName = `sessroom-${Date.now().toString().slice(-7)}`;
    const roomId = await openRoomAsChat(pageA, roomName);
    // Wait for tab A's MessageInput to render (requires WS connected + room loaded).
    await expect(pageA.locator('textarea')).toBeVisible({ timeout: 15_000 });
    await joinAndOpenRoom(pageB, roomId);
    await expect(pageB.locator('textarea')).toBeVisible({ timeout: 15_000 });

    // Poll /api/sessions until both WS sessions are registered.
    await expect.poll(
      async () =>
        pageA.evaluate(async () => {
          const r = await fetch('/api/sessions', {
            headers: { Authorization: `Bearer ${localStorage.getItem('authToken')}` },
          });
          return r.ok ? ((await r.json()) as Array<unknown>).length : -1;
        }),
      { timeout: 30_000, intervals: [500, 1_000, 2_000] },
    ).toBeGreaterThanOrEqual(2);

    // SPA-navigate (not goto) so the WebSocket stays open; a full reload
    // would disconnect Tab A's session right before we assert it's visible.
    await pageA.click('nav >> a:has-text("Sessions")');
    await pageA.waitForURL(/.*\/sessions$/);
    await expect(pageA.locator('body')).toContainText('Active sessions');
    await expect.poll(async () => pageA.locator('table tbody tr').count(), { timeout: 15_000 }).toBeGreaterThanOrEqual(2);
    await expect(pageA.locator('text=This device')).toHaveCount(1);

    // Accept any confirm() dialogs the UI triggers.
    pageA.on('dialog', (d) => d.accept());

    await pageA.click('button:has-text("Log out everywhere else")');

    // List shrinks to the current session on tab A.
    await expect.poll(async () => pageA.locator('table tbody tr').count(), { timeout: 15_000 }).toBe(1);
    await expect(pageA.locator('text=This device')).toHaveCount(1);

    // Tab B gets redirected to /login by the eviction watcher (poll interval 3s).
    await pageB.waitForURL(/.*\/login$/, { timeout: 20_000 });

    await ctxA.close();
    await ctxB.close();
  });
});
