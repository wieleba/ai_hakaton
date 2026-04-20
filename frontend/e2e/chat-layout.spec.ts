import { test, expect, Browser, Page, APIRequestContext } from '@playwright/test';

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
  const token = await page.evaluate(() => localStorage.getItem('authToken'));
  if (!token) throw new Error('no auth token after login');
  return { ctx, page, token };
}

async function seedRoomWithMessages(
  request: APIRequestContext,
  token: string,
  messageCount: number,
  name: string,
): Promise<string> {
  const auth = { Authorization: `Bearer ${token}` };
  const createRes = await request.post(`${BACKEND_URL}/api/rooms`, {
    headers: auth,
    data: { name },
  });
  expect(createRes.ok()).toBeTruthy();
  const room = await createRes.json();

  // Creator is auto-added as a member, so we can post immediately.
  const posts = [];
  for (let i = 0; i < messageCount; i++) {
    posts.push(
      request.post(`${BACKEND_URL}/api/rooms/${room.id}/messages`, {
        headers: auth,
        data: { text: `scroll-test message number ${i} 🎯` },
      }),
    );
  }
  const results = await Promise.all(posts);
  for (const r of results) expect(r.ok()).toBeTruthy();

  return room.id as string;
}

test.describe('Chat layout', () => {
  test('MessageInput stays in viewport even with many messages', async ({ browser }) => {
    const u = uniqueUser('layout');
    const { ctx, page, token } = await registerAndLogin(browser, u.email, u.username, u.password);

    const roomId = await seedRoomWithMessages(
      page.request,
      token,
      60,
      `scroll-room-${Date.now().toString().slice(-7)}`,
    );

    await page.goto(`/rooms/${roomId}`);
    await page.waitForLoadState('networkidle');

    const textarea = page.getByPlaceholder(/type a message/i);
    await expect(textarea).toBeVisible();

    // Textarea's bottom edge must lie inside the viewport.
    const box = await textarea.boundingBox();
    const viewport = page.viewportSize();
    expect(box).not.toBeNull();
    expect(viewport).not.toBeNull();
    expect(box!.y + box!.height).toBeLessThanOrEqual(viewport!.height);

    // And the textarea must be interactive (focusable + typeable).
    await textarea.click();
    await textarea.fill('live message from the bottom');
    await expect(textarea).toHaveValue('live message from the bottom');

    // The message list itself should be scrollable: scrollHeight > clientHeight.
    const scrollable = await page
      .locator('div.flex-1.min-h-0.overflow-y-auto')
      .first()
      .evaluate((el) => ({
        scrollHeight: (el as HTMLElement).scrollHeight,
        clientHeight: (el as HTMLElement).clientHeight,
      }));
    expect(scrollable.scrollHeight).toBeGreaterThan(scrollable.clientHeight);

    await ctx.close();
  });

  test('sending a YouTube link renders an inline iframe', async ({ browser }) => {
    const u = uniqueUser('ytembed');
    const { ctx, page, token } = await registerAndLogin(browser, u.email, u.username, u.password);

    // Create a bare room (no pre-seeded messages).
    const auth = { Authorization: `Bearer ${token}` };
    const createRes = await page.request.post(`${BACKEND_URL}/api/rooms`, {
      headers: auth,
      data: { name: `yt-room-${Date.now().toString().slice(-7)}` },
    });
    expect(createRes.ok()).toBeTruthy();
    const room = await createRes.json();

    await page.goto(`/rooms/${room.id}`);
    await page.waitForLoadState('networkidle');

    const composer = page.getByPlaceholder(/type a message/i);
    await composer.fill('check this https://youtu.be/dQw4w9WgXcQ');
    await composer.press('Control+Enter');

    // Iframe with title "YouTube video <id>" must appear. Allow 10 s for
    // backend extract + oEmbed (may be slow when YouTube rate-limits) + WS + render.
    await expect(page.locator('iframe[title="YouTube video dQw4w9WgXcQ"]'))
      .toBeVisible({ timeout: 10_000 });

    await ctx.close();
  });
});
