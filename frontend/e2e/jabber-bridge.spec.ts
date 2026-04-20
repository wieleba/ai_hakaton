import { test, expect, APIRequestContext, Browser, Page } from '@playwright/test';

/**
 * End-to-end coverage for the Chat ↔ XMPP DM bridge.
 *
 * The tests use ejabberd's admin HTTP API (`/api/send_stanza`) as a Psi+
 * stand-in: anything that injects a stanza into ejabberd exercises exactly
 * the same routing path a real XMPP client would hit, so the bridge's
 * incoming-side listener sees the same inputs it sees in production.
 *
 * Each test registers fresh users with JID-safe usernames so the
 * provisioning hook auto-creates the matching JID on chat-a.local.
 */

const password = 'password123';
const BACKEND_URL = process.env.E2E_BACKEND_URL || 'http://localhost:8080';
const EJABBERD_A_URL = process.env.E2E_EJABBERD_A_URL || 'http://localhost:5280';
const EJABBERD_A_DOMAIN = 'chat-a.local';
const EJABBERD_ADMIN_JID = 'admin@chat-a.local';
const EJABBERD_ADMIN_PASS = 'adminpass';

// Usernames must be [a-z0-9._-]+ to be JID-safe (see JabberProvisioningService).
function uniqueJabberUser(prefix: string) {
  const stamp = Date.now().toString().slice(-7) + Math.floor(Math.random() * 1000);
  const username = `${prefix}${stamp}`.toLowerCase();
  return { username, email: `${username}@example.com`, password };
}

async function registerAndLogin(
  browser: Browser,
  email: string,
  username: string,
  pw: string,
): Promise<{
  ctx: Awaited<ReturnType<Browser['newContext']>>;
  page: Page;
  token: string;
}> {
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

async function makeFriends(
  request: APIRequestContext,
  requesterToken: string,
  addresseeToken: string,
  addresseeUsername: string,
): Promise<void> {
  const reqRes = await request.post(`${BACKEND_URL}/api/friendships/requests`, {
    headers: { Authorization: `Bearer ${requesterToken}` },
    data: { username: addresseeUsername },
  });
  expect(reqRes.ok()).toBeTruthy();

  const incomingRes = await request.get(`${BACKEND_URL}/api/friendships/requests`, {
    headers: { Authorization: `Bearer ${addresseeToken}` },
  });
  expect(incomingRes.ok()).toBeTruthy();
  const incoming = await incomingRes.json();
  expect(Array.isArray(incoming)).toBeTruthy();
  const pending = (incoming as Array<{ id: string }>)[0];
  expect(pending).toBeDefined();

  const acceptRes = await request.post(
    `${BACKEND_URL}/api/friendships/requests/${pending.id}/accept`,
    { headers: { Authorization: `Bearer ${addresseeToken}` } },
  );
  expect(acceptRes.ok()).toBeTruthy();
}

async function injectXmppMessage(
  request: APIRequestContext,
  fromJid: string,
  toJid: string,
  body: string,
): Promise<void> {
  // Build a bare <message> stanza. The bridge marker is deliberately absent —
  // this simulates a message from a real XMPP client such as Psi+, which
  // MUST be delivered to the recipient's Chat inbox.
  const stanza = `<message type='chat'><body>${escapeXml(body)}</body></message>`;
  const basic = Buffer.from(`${EJABBERD_ADMIN_JID}:${EJABBERD_ADMIN_PASS}`).toString('base64');
  const res = await request.post(`${EJABBERD_A_URL}/api/send_stanza`, {
    headers: {
      Authorization: `Basic ${basic}`,
      'Content-Type': 'application/json',
    },
    data: { from: fromJid, to: toJid, stanza },
  });
  expect(res.ok()).toBeTruthy();
}

function escapeXml(v: string): string {
  return v
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** Polls {@code fetch} at 200 ms intervals until {@code predicate} returns
 *  true for its value, or the deadline passes. Returns the last value seen. */
async function pollUntil<T>(
  fetch: () => Promise<T>,
  predicate: (v: T) => boolean,
  timeoutMs: number,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  let last = await fetch();
  while (!predicate(last) && Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, 200));
    last = await fetch();
  }
  return last;
}

/** Blocks until ejabberd shows a /chat-bridge session for the given username. */
async function waitForBridgeResource(
  request: APIRequestContext,
  username: string,
): Promise<void> {
  const basic = Buffer.from(`${EJABBERD_ADMIN_JID}:${EJABBERD_ADMIN_PASS}`).toString('base64');
  const deadline = Date.now() + 8_000;
  while (Date.now() < deadline) {
    const res = await request.post(`${EJABBERD_A_URL}/api/user_sessions_info`, {
      headers: {
        Authorization: `Basic ${basic}`,
        'Content-Type': 'application/json',
      },
      data: { user: username, host: EJABBERD_A_DOMAIN },
    });
    if (res.ok()) {
      const info = (await res.json()) as Array<{ resource?: string }>;
      if (Array.isArray(info) && info.some((s) => s.resource === 'chat-bridge')) {
        return;
      }
    }
    await new Promise((r) => setTimeout(r, 200));
  }
  throw new Error(`Bridge session for ${username}@${EJABBERD_A_DOMAIN} never opened`);
}

/** Counts messages in a conversation via the Chat DM history endpoint. */
async function conversationMessages(
  request: APIRequestContext,
  token: string,
  conversationId: string,
): Promise<Array<{ text: string | null }>> {
  const res = await request.get(
    `${BACKEND_URL}/api/dms/${conversationId}/messages?limit=50`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(res.ok()).toBeTruthy();
  return (await res.json()) as Array<{ text: string | null }>;
}

test.describe('Jabber bridge', () => {
  test('GET /api/jabber/me exposes XMPP credentials for JID-safe users', async ({
    browser,
  }) => {
    const u = uniqueJabberUser('jabme');
    const { ctx, page, token } = await registerAndLogin(browser, u.email, u.username, u.password);

    const meRes = await page.request.get(`${BACKEND_URL}/api/jabber/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(meRes.ok()).toBeTruthy();
    const me = await meRes.json();

    expect(me.available).toBe(true);
    expect(me.jid).toBe(`${u.username}@chat-a.local`);
    expect(me.host).toBe('chat-a.local');
    expect(me.port).toBe(5222);
    expect(typeof me.password).toBe('string');
    expect(me.password.length).toBeGreaterThan(10);

    await ctx.close();
  });

  test('XMPP stanza injected for Bob (as if from Alice via Psi+) appears in Bob\'s Chat DMs', async ({
    browser,
  }) => {
    const alice = uniqueJabberUser('xa');
    const bob = uniqueJabberUser('xb');

    // Both users get auto-provisioned JIDs on chat-a.local at registration.
    const aliceSession = await registerAndLogin(browser, alice.email, alice.username, alice.password);
    const bobSession = await registerAndLogin(browser, bob.email, bob.username, bob.password);

    // Wait until Bob's bridge actually has a Smack session on ejabberd before
    // injecting — otherwise the stanza goes to offline storage and never hits
    // the bridge listener. Poll ejabberd's user_sessions_info until the
    // 'chat-bridge' resource is present.
    await waitForBridgeResource(aliceSession.page.request, bob.username);

    const aliceJid = `${alice.username}@${EJABBERD_A_DOMAIN}`;
    const bobJid = `${bob.username}@${EJABBERD_A_DOMAIN}`;
    const body = 'hello from simulated psi+';

    await injectXmppMessage(aliceSession.page.request, aliceJid, bobJid, body);

    // Poll Bob's conversations rather than fixed-sleep — lets the inbound
    // listener + AFTER_COMMIT event + WS broadcast + DB commit settle.
    const convo = await pollUntil(
      async () => {
        const convRes = await bobSession.page.request.get(
          `${BACKEND_URL}/api/dms/conversations`,
          { headers: { Authorization: `Bearer ${bobSession.token}` } },
        );
        if (!convRes.ok()) return null;
        const conversations = (await convRes.json()) as Array<{
          id: string;
          otherUsername: string;
          lastMessage: string | null;
        }>;
        return conversations.find((c) => c.otherUsername === alice.username) ?? null;
      },
      (v) => v !== null && v.lastMessage === body,
      5_000,
    );
    expect(convo).not.toBeNull();
    expect(convo!.lastMessage).toBe(body);

    await aliceSession.ctx.close();
    await bobSession.ctx.close();
  });

  test('Chat UI DM relays to XMPP without a duplicate making it back (loop-prevention)', async ({
    browser,
  }) => {
    const alice = uniqueJabberUser('ra');
    const bob = uniqueJabberUser('rb');

    const aliceSession = await registerAndLogin(browser, alice.email, alice.username, alice.password);
    const bobSession = await registerAndLogin(browser, bob.email, bob.username, bob.password);

    // Friendship is enforced for DM sends; bridge paths don't require it but
    // the outgoing relay hangs off DirectMessageService.send() which does.
    await makeFriends(
      aliceSession.page.request,
      aliceSession.token,
      bobSession.token,
      bob.username,
    );

    // Give ejabberd/bridge time to register the new Smack sessions.
    await aliceSession.page.waitForTimeout(1500);

    // Look up Bob's UUID via /api/search, then getOrCreate the conversation
    // via /api/dms/with/{otherUserId} (both real endpoints in this codebase).
    const searchRes = await aliceSession.page.request.get(
      `${BACKEND_URL}/api/search?q=${bob.username}`,
      { headers: { Authorization: `Bearer ${aliceSession.token}` } },
    );
    expect(searchRes.ok()).toBeTruthy();
    const searchBody = (await searchRes.json()) as {
      users: Array<{ id: string; username: string }>;
    };
    const bobRow = searchBody.users.find((u) => u.username === bob.username);
    expect(bobRow).toBeDefined();

    const convRes = await aliceSession.page.request.get(
      `${BACKEND_URL}/api/dms/with/${bobRow!.id}`,
      { headers: { Authorization: `Bearer ${aliceSession.token}` } },
    );
    expect(convRes.ok()).toBeTruthy();
    const conv = (await convRes.json()) as { id: string };

    const body = `relay-${Date.now()}`;
    const sendRes = await aliceSession.page.request.post(
      `${BACKEND_URL}/api/dms/${conv.id}/messages`,
      {
        headers: {
          Authorization: `Bearer ${aliceSession.token}`,
          'Content-Type': 'application/json',
        },
        data: { text: body },
      },
    );
    expect(sendRes.ok()).toBeTruthy();

    // Wait past the outgoing relay's AFTER_COMMIT + possible ejabberd round
    // trip + Smack re-delivery window. If the <bridge> marker weren't working,
    // the duplicate would land by now.
    await bobSession.page.waitForTimeout(2500);

    const bobMessages = await conversationMessages(bobSession.page.request, bobSession.token, conv.id);
    const copies = bobMessages.filter((m) => m.text === body);
    expect(copies.length).toBe(1); // exactly one — no bridge-back duplicate

    await aliceSession.ctx.close();
    await bobSession.ctx.close();
  });

  test('XMPP bridge unavailable message shows for a username with invalid JID characters', async ({
    browser,
  }) => {
    // Create a username the provisioning regex will refuse (contains uppercase).
    // Can't pipe through the helper because registerAndLogin forces lowercase
    // assumptions; build the user manually.
    const stamp = Date.now().toString().slice(-7) + Math.floor(Math.random() * 1000);
    const username = `Bad${stamp}Name`; // uppercase → JID-unsafe
    const email = `${username}@example.com`;

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await page.goto('/register');
    await page.fill('#email', email);
    await page.fill('#username', username);
    await page.fill('#password', password);
    await page.fill('#confirm-password', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(/.*\/login$/);
    await page.fill('#email', email);
    await page.fill('#password', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(/.*\/rooms$/);
    const token = await page.evaluate(() => localStorage.getItem('authToken'));
    expect(token).toBeTruthy();

    const meRes = await page.request.get(`${BACKEND_URL}/api/jabber/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(meRes.ok()).toBeTruthy();
    const me = await meRes.json();
    expect(me.available).toBe(false);

    // The Account Settings page should render the unavailable branch.
    await page.goto('/account');
    await expect(page.locator('body')).toContainText('XMPP bridge unavailable', {
      timeout: 5_000,
    });

    await ctx.close();
  });
});
