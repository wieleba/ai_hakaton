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

async function createRoomAs(page: Page, name: string): Promise<string> {
  return page.evaluate(async (n) => {
    const r = await fetch('/api/rooms', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('authToken')}`,
      },
      body: JSON.stringify({ name: n, description: 'unread e2e', visibility: 'public' }),
    });
    return (await r.json()).id as string;
  }, name);
}

async function joinRoomAs(page: Page, roomId: string) {
  await page.evaluate(async (id) => {
    await fetch(`/api/rooms/${id}/join`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('authToken')}` },
    });
  }, roomId);
}

async function postMessage(page: Page, roomId: string, text: string) {
  await page.evaluate(
    async ({ id, t }) => {
      await fetch(`/api/rooms/${id}/messages`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('authToken')}`,
        },
        body: JSON.stringify({ text: t }),
      });
    },
    { id: roomId, t: text },
  );
}

test.describe('Unread indicators', () => {
  test('sidebar badge appears for messages sent while away and clears on open', async ({ browser }) => {
    const alice = uniqueUser('una');
    const bob = uniqueUser('unb');

    const aliceSession = await registerAndLogin(browser, alice.email, alice.username, alice.password);
    const bobSession = await registerAndLogin(browser, bob.email, bob.username, bob.password);

    // Alice creates + both users join. Created via REST to avoid driving the modal UI.
    const roomName = `unroom-${Date.now().toString().slice(-7)}`;
    const roomId = await createRoomAs(aliceSession.page, roomName);
    await joinRoomAs(bobSession.page, roomId);

    // Bob refreshes so the sidebar picks up the new membership.
    await bobSession.page.goto('/rooms');
    const sidebarLink = bobSession.page.locator(`aside a:has-text("${roomName}")`);
    await expect(sidebarLink).toBeVisible({ timeout: 5_000 });

    // Alice posts two messages while Bob is on /rooms (not inside the room).
    await postMessage(aliceSession.page, roomId, 'hello bob');
    await postMessage(aliceSession.page, roomId, 'still there?');

    // Bob has no live WebSocket yet (he hasn't entered a chat), so the hook's
    // live bumps won't fire. Reload so useUnread's on-mount REST fetch picks up
    // the new unread count.
    await bobSession.page.reload();
    const sidebarLinkAfter = bobSession.page.locator(`aside a:has-text("${roomName}")`);
    await expect(sidebarLinkAfter.locator('span[aria-label$="unread"]')).toContainText('2', {
      timeout: 15_000,
    });

    // Bob clicks the room — ChatPage mount calls markRoomRead and the badge clears.
    await sidebarLinkAfter.click();
    await bobSession.page.waitForURL(/.*\/rooms\/[^/]+$/);
    await expect
      .poll(
        async () =>
          bobSession.page
            .locator(`aside a:has-text("${roomName}") span[aria-label$="unread"]`)
            .count(),
        { timeout: 10_000 },
      )
      .toBe(0);

    await aliceSession.ctx.close();
    await bobSession.ctx.close();
  });
});
