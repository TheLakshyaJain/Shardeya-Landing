import { readFileSync } from 'node:fs';
import type { Page } from '@playwright/test';

// The backend's stub SMS gateway logs OTP codes to its own process log
// rather than sending a real SMS (no telecom provider wired up locally) --
// this reads the most recent one for a given mobile+purpose so e2e tests can
// complete a real OTP verification without any backend test-mode bypass.
// Still used for any SMS-channel purpose (forgot-password's mobile-OTP
// branch) -- unaffected by the Email OTP fix below.
const BACKEND_LOG_PATH = process.env.M2_BACKEND_LOG ?? '/Users/mriduljain/.claude/jobs/dbd4850a/tmp/m2_backend_run.log';

// Signup verification (and login-with-OTP) moved from SMS to email delivery
// -- see CLAUDE.md's "Email OTP" writeup. Reads the real, actually-delivered
// email back from MailHog's own REST API (v2), the same mechanism the
// backend's own MailHogReader test utility uses, rather than a stub log file
// that signup no longer writes to at all.
const MAILHOG_API_URL = process.env.E2E_MAILHOG_URL ?? 'http://localhost:8025';

export function randomMobile(): string {
  const first = String(6 + Math.floor(Math.random() * 4)); // 6-9
  let rest = '';
  for (let i = 0; i < 9; i++) rest += Math.floor(Math.random() * 10);
  return first + rest;
}

export function readLatestOtp(mobile: string, purpose: 'SIGNUP' | 'LOGIN' | 'RESET'): string {
  const log = readFileSync(BACKEND_LOG_PATH, 'utf-8');
  const lines = log.split('\n').filter((l) => l.includes('[SMS STUB]') && l.includes(`mobile=${mobile}`) && l.includes(`purpose=${purpose}`));
  if (lines.length === 0) {
    throw new Error(`No OTP found in backend log for mobile=${mobile} purpose=${purpose}`);
  }
  const last = lines[lines.length - 1];
  const match = last.match(/code=(\d{6})/);
  if (!match) throw new Error(`Could not parse OTP code from log line: ${last}`);
  return match[1];
}

interface MailHogMessage {
  Content?: { Headers?: { To?: string[] }; Body?: string };
}

export async function readLatestEmailOtp(email: string): Promise<string> {
  const res = await fetch(`${MAILHOG_API_URL}/api/v2/messages?limit=50`);
  const data = (await res.json()) as { items?: MailHogMessage[] };
  for (const item of data.items ?? []) {
    const to = item.Content?.Headers?.To ?? [];
    if (to.some((addr) => addr.toLowerCase().includes(email.toLowerCase()))) {
      const body = item.Content?.Body ?? '';
      const match = body.match(/code is: (\d{6})/);
      if (match) return match[1];
    }
  }
  throw new Error(`No OTP email found in MailHog for ${email}`);
}

export async function fillOtp(page: Page, code: string) {
  const firstDigit = page.locator('input[aria-label="Digit 1"]');
  await firstDigit.click();
  await firstDigit.pressSequentially(code);
}

export interface SignupResult {
  fullName: string;
  mobile: string;
  email: string;
  password: string;
}

/** Drives the real signup -> OTP verify UI flow end to end and lands on the builder dashboard. */
export async function signupBuilder(page: Page): Promise<SignupResult> {
  const mobile = randomMobile();
  const email = `e2e.${Date.now()}.${Math.floor(Math.random() * 10000)}@example.com`;
  const password = 'Test1234';
  const fullName = 'Test Automation Builder';

  await page.goto('/signup');
  await page.getByText('Builder', { exact: true }).click();
  await page.locator('#fullName').fill(fullName);
  await page.locator('#mobile').fill(mobile);
  await page.locator('#email').fill(email);
  await page.locator('#city').fill('Jaipur');
  await page.locator('#password').fill(password);
  await page.locator('#confirmPassword').fill(password);
  await page.getByRole('checkbox').click();
  await page.getByRole('button', { name: 'Create account' }).click();

  await page.waitForURL('**/signup/verify');
  const code = await readLatestEmailOtp(email);
  await fillOtp(page, code);
  await page.getByRole('button', { name: 'Verify' }).click();

  await page.waitForURL('**/builder/dashboard');
  return { fullName, mobile, email, password };
}

// authStore is in-memory only (no localStorage tokens, by design -- see
// authStore.ts's own comment) so page.goto() after login performs a full
// browser navigation that wipes the session and bounces to /login. Every
// post-login navigation in these specs must go through an in-app link/button
// click (client-side React Router navigation) instead.
export async function goToProjects(page: Page) {
  await page.getByRole('link', { name: 'Projects' }).click();
  await page.waitForURL('**/builder/projects');
}

export async function goToNewProject(page: Page) {
  await goToProjects(page);
  await page.getByRole('link', { name: 'New Project' }).first().click();
  await page.waitForURL('**/builder/projects/new');
}
