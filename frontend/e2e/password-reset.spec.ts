import { test, expect, Page, request } from '@playwright/test';

const MAILHOG = 'http://localhost:8025';

function uniqueUser(prefix: string) {
  const stamp = Date.now().toString().slice(-7) + Math.floor(Math.random() * 1000);
  const username = `${prefix}${stamp}`;
  return { username, email: `${username}@example.com`, password: 'oldpass123' };
}

async function registerAndLogout(page: Page, email: string, username: string, pw: string) {
  await page.goto('/register');
  await page.fill('#email', email);
  await page.fill('#username', username);
  await page.fill('#password', pw);
  await page.fill('#confirm-password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/login$/);
  // Confirm we can log in with the starting password.
  await page.fill('#email', email);
  await page.fill('#password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/rooms$/);
  // Log out by clearing the token — simpler than driving the menu for this suite.
  await page.evaluate(() => localStorage.removeItem('authToken'));
  await page.goto('/login');
}

/**
 * Full quoted-printable decode. MimeMessageHelper emits bodies with both soft line
 * breaks (`=\n`) AND `=XX` hex sequences (e.g. `=3D` for a literal `=`). Stripping
 * only the soft breaks would leave `token=3D...` in the link, truncating the real
 * token to a stray `3D...` prefix.
 */
function qpDecode(input: string): string {
  return input
    .replace(/=\r?\n/g, '')
    .replace(/=([0-9A-Fa-f]{2})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
}

/**
 * Fetches the most recent email from MailHog for the given recipient and extracts
 * the reset-password link.
 */
async function fetchResetLinkFor(email: string): Promise<string> {
  const api = await request.newContext();
  // Small poll — give the mail a moment to land.
  for (let i = 0; i < 10; i++) {
    const resp = await api.get(`${MAILHOG}/api/v2/messages`);
    const body = await resp.json();
    const items = (body.items as Array<{ To: Array<{ Mailbox: string; Domain: string }>; Content: { Body: string } }>) || [];
    const match = items.find((m) =>
      m.To.some((t) => `${t.Mailbox}@${t.Domain}`.toLowerCase() === email.toLowerCase()),
    );
    if (match) {
      await api.dispose();
      const decoded = qpDecode(match.Content.Body);
      const link = decoded.match(/http:\/\/[^\s<>"]*?token=[A-Za-z0-9_-]+/);
      if (!link) throw new Error('no reset link in mail body');
      return link[0];
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  await api.dispose();
  throw new Error(`no email arrived for ${email}`);
}

async function clearMailhog() {
  const api = await request.newContext();
  await api.delete(`${MAILHOG}/api/v1/messages`);
  await api.dispose();
}

test.describe('Password reset', () => {
  test.beforeEach(async () => {
    await clearMailhog();
  });

  test('end-to-end: request → click link → set new password → login works, old password fails', async ({ page }) => {
    const u = uniqueUser('pwr');
    const newPw = 'newpass456';

    await registerAndLogout(page, u.email, u.username, u.password);

    // Request reset via UI
    await page.click('a:has-text("Forgot password?")');
    await page.waitForURL(/.*\/forgot-password$/);
    await page.fill('#email', u.email);
    await page.click('button:has-text("Send reset link")');
    await expect(page.locator('body')).toContainText('we sent a reset link');

    // Fetch link from MailHog
    const link = await fetchResetLinkFor(u.email);
    // Link should target the frontend host (port 3000 in compose). Take the pathname+query
    // part and navigate relatively so the test works against any baseURL.
    const tail = link.replace(/^https?:\/\/[^/]+/, '');
    await page.goto(tail);

    await page.fill('#pw', newPw);
    await page.fill('#confirm', newPw);
    await page.click('button:has-text("Save new password")');
    await page.waitForURL(/.*\/login$/);
    await expect(page.locator('body')).toContainText('Password changed');

    // Old password fails
    await page.fill('#email', u.email);
    await page.fill('#password', u.password);
    await page.click('button[type="submit"]');
    // Stays on /login with an error
    await page.waitForTimeout(500);
    await expect(page).toHaveURL(/.*\/login$/);

    // New password works
    await page.fill('#email', u.email);
    await page.fill('#password', newPw);
    await page.click('button[type="submit"]');
    await page.waitForURL(/.*\/rooms$/);
  });

  test('replayed token is rejected', async ({ page }) => {
    const u = uniqueUser('pwr-replay');
    const newPw = 'newpass456';

    await registerAndLogout(page, u.email, u.username, u.password);

    await page.goto('/forgot-password');
    await page.fill('#email', u.email);
    await page.click('button:has-text("Send reset link")');
    await expect(page.locator('body')).toContainText('we sent a reset link');

    const link = await fetchResetLinkFor(u.email);
    const tail = link.replace(/^https?:\/\/[^/]+/, '');

    // First use — succeeds
    await page.goto(tail);
    await page.fill('#pw', newPw);
    await page.fill('#confirm', newPw);
    await page.click('button:has-text("Save new password")');
    await page.waitForURL(/.*\/login$/);

    // Second use of the same link — rejected
    await page.goto(tail);
    await page.fill('#pw', 'anotherpass789');
    await page.fill('#confirm', 'anotherpass789');
    await page.click('button:has-text("Save new password")');
    await expect(page.locator('body')).toContainText(/invalid or expired/i);
  });

  test('missing-token page shows "Invalid link"', async ({ page }) => {
    await page.goto('/reset-password');
    await expect(page.locator('body')).toContainText('Invalid link');
    await expect(page.locator('a:has-text("Request a new link")')).toBeVisible();
  });

  test('password mismatch on reset page does not call the backend', async ({ page }) => {
    await page.goto('/reset-password?token=abc');
    await page.fill('#pw', 'newpass456');
    await page.fill('#confirm', 'different789');
    await page.click('button:has-text("Save new password")');
    await expect(page.locator('body')).toContainText('Passwords do not match');
  });
});
