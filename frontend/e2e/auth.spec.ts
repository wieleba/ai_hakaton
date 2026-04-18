import { test, expect, Page } from '@playwright/test';

const password = 'password123';

function uniqueUser(prefix: string) {
  const stamp = Date.now().toString().slice(-7) + Math.floor(Math.random() * 1000);
  const username = `${prefix}${stamp}`;
  return { username, email: `${username}@example.com`, password };
}

async function fillRegister(page: Page, email: string, username: string, pw: string) {
  await page.goto('/register');
  await page.fill('#email', email);
  await page.fill('#username', username);
  await page.fill('#password', pw);
  await page.fill('#confirm-password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/login$/);
}

async function fillLogin(page: Page, email: string, pw: string) {
  if (!page.url().endsWith('/login')) await page.goto('/login');
  await page.fill('#email', email);
  await page.fill('#password', pw);
  await page.click('button[type="submit"]');
  await page.waitForURL(/.*\/dashboard$/);
}

test.describe('Authentication', () => {
  test('unauthenticated visit redirects to /login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/.*\/login$/);
  });

  test('register → login → JWT stored → protected route accessible', async ({ page }) => {
    const u = uniqueUser('auth');

    await fillRegister(page, u.email, u.username, u.password);
    expect(page.url()).toContain('/login');

    await fillLogin(page, u.email, u.password);
    const token = await page.evaluate(() => localStorage.getItem('authToken'));
    expect(token).toBeTruthy();

    // Hit a protected page and confirm AuthGuard does NOT bounce us back.
    await page.goto('/rooms');
    await expect(page).toHaveURL(/.*\/rooms$/);
    await expect(page.locator('body')).toContainText('Chat Rooms');
  });

  test('all sidebar destinations render with auth', async ({ page }) => {
    const u = uniqueUser('nav');
    await fillRegister(page, u.email, u.username, u.password);
    await fillLogin(page, u.email, u.password);

    await page.goto('/rooms');
    await expect(page.locator('body')).toContainText('Chat Rooms');

    await page.goto('/friends');
    await expect(page.locator('body')).toContainText('Send request');
    await expect(page.locator('body')).toContainText('Friends list');

    await page.goto('/dms');
    await expect(page.locator('body')).toContainText('Direct Messages');
  });
});
