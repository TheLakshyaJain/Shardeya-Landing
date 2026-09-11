import { expect, test, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

// M3 verification: sell a plot through the real wizard UI, record payments,
// bounce a cheque, reveal the gov ID, and cancel a sale -- against a live
// backend (docker compose + mvn spring-boot:run), same standard as M2's
// own e2e suite.

const API_BASE = process.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const BACKEND_LOG_PATH = process.env.M3_BACKEND_LOG ?? '/Users/mriduljain/.claude/jobs/dbd4850a/tmp/m3_backend_run.log';

function randomMobile(): string {
  const first = String(6 + Math.floor(Math.random() * 4));
  let rest = '';
  for (let i = 0; i < 9; i++) rest += Math.floor(Math.random() * 10);
  return first + rest;
}

function readLatestOtp(mobile: string, purpose: string): string {
  const log = readFileSync(BACKEND_LOG_PATH, 'utf-8');
  const lines = log.split('\n').filter((l) => l.includes('[SMS STUB]') && l.includes(`mobile=${mobile}`) && l.includes(`purpose=${purpose}`));
  const last = lines[lines.length - 1];
  const match = last.match(/code=(\d{6})/);
  if (!match) throw new Error(`No OTP found for ${mobile}/${purpose}`);
  return match[1];
}

async function getAccessToken(page: Page): Promise<string> {
  return page.evaluate(() => (window as unknown as { __authStore: { getState: () => { accessToken: string } } }).__authStore.getState().accessToken);
}

async function signupAndCreatePlot(page: Page) {
  const mobile = randomMobile();
  const email = `m3.${Date.now()}@example.com`;

  await page.goto('/signup');
  await page.getByText('Builder', { exact: true }).click();
  await page.locator('#fullName').fill('M Three Test Builder');
  await page.locator('#mobile').fill(mobile);
  await page.locator('#email').fill(email);
  await page.locator('#city').fill('Jaipur');
  await page.locator('#password').fill('Test1234');
  await page.locator('#confirmPassword').fill('Test1234');
  await page.getByRole('checkbox').click();
  await page.getByRole('button', { name: 'Create account' }).click();

  await page.waitForURL('**/signup/verify', { timeout: 10000 });
  const otp = readLatestOtp(mobile, 'SIGNUP');
  const firstDigit = page.locator('input[aria-label="Digit 1"]');
  await firstDigit.click();
  await firstDigit.pressSequentially(otp);
  await page.getByRole('button', { name: 'Verify' }).click();
  await page.waitForURL('**/builder/dashboard', { timeout: 10000 });

  const token = await getAccessToken(page);
  const projectRes = await page.request.post(`${API_BASE}/projects`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: 'M3 Sale Test Project',
      projectType: 'RESIDENTIAL_PLOT_COLONY',
      address: '1 Test Road, Jaipur',
      locality: 'Vaishali Nagar',
      city: 'Jaipur',
      stateCode: 'RJ',
      totalAreaValue: 5,
      totalAreaUnit: 'ACRE',
      declaredPlotCount: 10,
    },
  });
  const project = await projectRes.json();

  const plotRes = await page.request.post(`${API_BASE}/projects/${project.id}/plots`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      plotNumber: 'A-1',
      sizeValue: 1200,
      sizeUnit: 'SQ_FT',
      price: 4200000,
      isGarden: false,
      isCorner: false,
      isHot: false,
    },
  });
  const plot = await plotRes.json();

  return { token, projectId: project.id, plotId: plot.id };
}

test('sell a plot through the wizard, record and bounce a payment, reveal gov ID, then cancel the sale', async ({ page }) => {
  await signupAndCreatePlot(page);

  // Real in-app navigation only -- authStore is in-memory (see helpers.ts's
  // own comment in the M2 suite), so page.goto() here would wipe the
  // session and bounce to /login.
  await page.getByRole('link', { name: 'Projects' }).click();
  await page.waitForURL('**/builder/projects');
  await page.getByText('M3 Sale Test Project').click();
  await page.getByRole('tab', { name: /plots/i }).click();
  await page.getByText('A-1').click();

  await expect(page.getByText('AVAILABLE', { exact: false }).first()).toBeVisible();
  await page.getByRole('button', { name: /mark as sold/i }).click();

  // Step 1: Buyer
  await page.locator('#buyerName').fill('Rajesh Kumar');
  await page.locator('#buyerMobile').fill('9876543210');
  await page.locator('#buyerEmail').fill('rajesh@example.com');
  await page.getByRole('button', { name: 'Next' }).click();

  // Step 2: Deal
  await page.locator('#purchaseDate').fill(new Date().toISOString().slice(0, 10));
  await page.locator('#dealValue').fill('4200000');
  await page.getByRole('button', { name: 'Next' }).click();

  // Step 3: Payment plan -- default single row, fill it in to equal deal value
  await page.locator('.grid input[type="number"]').first().fill('4200000');
  await page.locator('.grid input[type="date"]').first().fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Next' }).click();

  // Step 4: Broker -- skip
  await page.getByRole('button', { name: 'Next' }).click();

  // Step 5: Review + confirm
  const saleResponsePromise = page.waitForResponse((r) => r.url().includes('/sale') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Confirm Sale' }).click();
  const saleResponse = await saleResponsePromise;
  console.log('SALE_CREATE_STATUS', saleResponse.status(), await saleResponse.text());

  // Plot should now show SOLD status inside the drawer.
  await expect(page.getByRole('button', { name: /mark as sold/i })).toHaveCount(0, { timeout: 10000 });

  // Record a cheque payment for the full amount.
  await page.getByRole('button', { name: 'Record Payment' }).click();
  await page.locator('#payment-amount').fill('4200000');
  await page.locator('#payment-paidOn').fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('combobox').filter({ hasText: /cash/i }).click();
  await page.getByRole('option', { name: /cheque/i }).click();
  await page.locator('#payment-reference').fill('CHQ-1001');
  await page.getByRole('button', { name: 'Record Payment' }).click();

  // Balance should now read 0 and schedule row should be PAID.
  await expect(page.getByText('₹0')).toBeVisible({ timeout: 10000 });
  await expect(page.getByText('Paid', { exact: true }).first()).toBeVisible();

  // Bounce the cheque -- balance should revert.
  await page.getByRole('button', { name: 'Mark Bounced' }).click();
  await expect(page.getByText('Bounced', { exact: true })).toBeVisible({ timeout: 10000 });
  await expect(page.getByText('Pending', { exact: true }).first()).toBeVisible();

  // The plot detail Sheet is still open, which Radix marks aria-hidden on
  // the rest of the page (including the TopBar) while open -- close it
  // first so the notification bell is reachable at all.
  await page.keyboard.press('Escape');

  // Notification bell should show unread notifications for at least
  // PLOT_SOLD, PAYMENT_RECORDED, and CHEQUE_BOUNCED by now (outbox -> poller
  // -> notification fan-out, all real, no mocking).
  await expect(page.getByRole('button', { name: 'Notifications' })).toBeVisible();
  await page.getByRole('button', { name: 'Notifications' }).click();
  await expect(page.getByText(/sold to Rajesh Kumar/i)).toBeVisible({ timeout: 15000 });
  await expect(page.getByText(/cheque.*bounced/i)).toBeVisible();
  await page.keyboard.press('Escape');

  // Re-open the plot drawer (closed above to reach the bell).
  await page.getByText('A-1').click();

  // Cancel the sale -- plot should return to Available.
  await page.getByRole('button', { name: 'Cancel Sale' }).click();
  await page.locator('#cancel-reason').fill('Buyer backed out of the deal');
  await page.getByRole('button', { name: 'Cancel Sale', exact: true }).last().click();
  await expect(page.getByRole('button', { name: /mark as sold/i })).toBeVisible({ timeout: 10000 });

  console.log('M3_SALE_FLOW_TEST_PASSED');
});
