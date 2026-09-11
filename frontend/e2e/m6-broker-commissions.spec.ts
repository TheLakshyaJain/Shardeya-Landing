import { expect, test, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';

// Free plan seeds BUILDER_BROKERS=0 (B-14 §21.1: "Free = N/A, module
// hidden") -- same "throwaway high-limit plan, switched only for this test
// org, purely to unblock test-data setup" pattern M2/M3/M4 already
// established for their own quota-limited setup needs. The M6TEST plan
// itself is seeded once, out of band, before this spec runs (see the
// verification session's own setup notes) -- this only points one org at it.
function unblockBrokerQuota(mobile: string) {
  execSync(
    `docker exec m6verify-postgres-1 psql -U shardeya -d shardeya -c ` +
      `"UPDATE subscription SET plan_code = 'M6TEST' WHERE org_id = (SELECT org_id FROM app_user WHERE mobile = '${mobile}')"`,
    { env: { ...process.env, DOCKER_HOST: 'unix:///Users/mriduljain/.colima/default/docker.sock' } },
  );
}

// M6 verification: broker CRUD, commission resolution (PLOT override vs
// GLOBAL fallback), rate-change-after-sale immutability, tier auto-upgrade
// on sale completion, and commission payment recording -- against a live
// backend (isolated m6verify docker compose stack), same standard as
// M2/M3/M4/M5's own e2e suites. authStore is in-memory only (documented
// since M2), so every navigation after login goes through a real in-app
// link/button click, never page.goto().

const API_BASE = process.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const BACKEND_LOG_PATH = process.env.M6_BACKEND_LOG ?? '/Users/mriduljain/.claude/jobs/dbd4850a/tmp/m6verify_backend.log';

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

async function signupAndCreatePlots(page: Page) {
  const mobile = randomMobile();
  const email = `m6.${Date.now()}@example.com`;

  await page.goto('/signup');
  await page.getByText('Builder', { exact: true }).click();
  await page.locator('#fullName').fill('M Six Test Builder');
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

  unblockBrokerQuota(mobile);

  const token = await getAccessToken(page);
  const projectRes = await page.request.post(`${API_BASE}/projects`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: 'M6 Broker Test Project',
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

  for (const plotNumber of ['B-1', 'B-2']) {
    await page.request.post(`${API_BASE}/projects/${project.id}/plots`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { plotNumber, sizeValue: 1000, sizeUnit: 'SQ_FT', price: 1000000, isGarden: false, isCorner: false, isHot: false },
    });
  }

  return { projectName: 'M6 Broker Test Project' as const };
}

async function sellPlot(page: Page, plotNumber: string, buyerName: string, buyerMobile: string, dealValue: string) {
  await page.getByText(plotNumber, { exact: true }).click();
  await page.getByRole('button', { name: /mark as sold/i }).click();

  await page.locator('#buyerName').fill(buyerName);
  await page.locator('#buyerMobile').fill(buyerMobile);
  await page.getByRole('button', { name: 'Next' }).click();
  await page.locator('#purchaseDate').fill(new Date().toISOString().slice(0, 10));
  await page.locator('#dealValue').fill(dealValue);
  await page.getByRole('button', { name: 'Next' }).click();
  await page.locator('.grid input[type="number"]').first().fill(dealValue);
  await page.locator('.grid input[type="date"]').first().fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Next' }).click();
  await page.getByRole('button', { name: 'Select Broker' }).click();
  await page.getByRole('combobox').click();
  await page.getByRole('option', { name: 'Suresh Sharma' }).click();
}

test('broker commission resolution, tier auto-upgrade, rate immutability, and payment recording', async ({ page }) => {
  const { projectName } = await signupAndCreatePlots(page);

  // ---- Add a broker with a 2% default rate ----
  await page.getByRole('link', { name: 'Brokers' }).click();
  await page.waitForURL('**/builder/brokers');
  await page.getByRole('button', { name: 'Add Broker' }).click();
  await page.locator('#broker-fullName').fill('Suresh Sharma');
  await page.locator('#broker-mobile').fill(randomMobile());
  await page.locator('#broker-commissionPct').fill('2');
  await page.getByRole('button', { name: 'Save Broker' }).click();
  await page.waitForURL('**/builder/brokers/*', { timeout: 10000 });
  await expect(page.getByRole('heading', { name: 'Suresh Sharma' })).toBeVisible();

  // ---- Commission Config: GLOBAL 3%, PLOT-level override 5% on B-1 ----
  await page.getByRole('tab', { name: 'Commission Config' }).click();
  await page.getByRole('button', { name: 'Add Rate' }).click();
  await page.locator('input[type="number"][step="0.001"]').fill('3');
  await page.locator('input[type="date"]').first().fill('2020-01-01');
  await page.getByRole('button', { name: 'Save Rate' }).last().click();
  await expect(page.getByText('3%')).toBeVisible({ timeout: 10000 });

  await page.getByRole('button', { name: 'Add Rate' }).click();
  const scopeCombobox = page.getByRole('dialog').getByRole('combobox').first();
  await scopeCombobox.click();
  await page.getByRole('option', { name: 'Per Plot' }).click();
  const dialogCombos = page.getByRole('dialog').getByRole('combobox');
  await dialogCombos.nth(1).click();
  await page.getByRole('option', { name: projectName }).click();
  // Wait for the Plot select's own label to mount before targeting it by
  // index -- clicking dialogCombos.nth(2) immediately after the project
  // selection can race the re-render that inserts the Plot select between
  // Project and Type, transiently hitting the Type select instead.
  await page.getByRole('dialog').getByText('Plot', { exact: true }).waitFor();
  await dialogCombos.nth(2).click();
  await page.getByRole('option', { name: 'B-1' }).click();
  await page.locator('input[type="number"][step="0.001"]').fill('5');
  await page.locator('input[type="date"]').first().fill('2020-01-01');
  await page.getByRole('button', { name: 'Save Rate' }).last().click();
  await expect(page.getByText('5%')).toBeVisible({ timeout: 10000 });
  console.log('M6_COMMISSION_CONFIG_CREATED');

  // ---- Tier setup: narrow Bronze to 0-0 and Silver to 1-9 so a single
  // completed deal is enough to force an observable auto-upgrade. ----
  await page.getByRole('link', { name: 'Brokers' }).click();
  await page.waitForURL('**/builder/brokers');
  await page.getByRole('link', { name: 'Broker Tiers' }).click();
  await page.waitForURL('**/builder/brokers/tiers');
  await page.getByRole('row', { name: /Bronze/ }).click();
  await page.getByLabel('Maximum Deals').fill('0');
  await page.getByRole('button', { name: 'Save Tier' }).click();
  await expect(page.getByRole('row', { name: /Bronze/ })).toBeVisible({ timeout: 10000 });

  await page.getByRole('row', { name: /Silver/ }).click();
  await page.getByLabel('Minimum Deals').fill('1');
  await page.getByRole('button', { name: 'Save Tier' }).click();
  await expect(page.getByRole('row', { name: /Silver/ })).toBeVisible({ timeout: 10000 });
  console.log('M6_TIERS_ADJUSTED');

  // ---- Sale 1: B-1, broker attributed -> should use the 5% PLOT override, not the 3% GLOBAL rate ----
  await page.getByRole('link', { name: 'Projects' }).click();
  await page.waitForURL('**/builder/projects');
  await page.getByText(projectName).click();
  await page.getByRole('tab', { name: /plots/i }).click();
  await sellPlot(page, 'B-1', 'Anita Verma', '9812345670', '1000000');
  // 5% of 10,00,000 = 50,000 -- PLOT override wins over GLOBAL (30,000) and broker default (20,000).
  await expect(page.getByText(/50,000/)).toBeVisible({ timeout: 10000 });
  console.log('M6_PLOT_OVERRIDE_PREVIEW_CORRECT');
  await page.getByRole('button', { name: 'Next' }).click();
  const sale1ResponsePromise = page.waitForResponse((r) => r.url().includes('/sale') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Confirm Sale' }).click();
  const sale1Response = await sale1ResponsePromise;
  console.log('M6_SALE1_STATUS', sale1Response.status());
  await expect(page.getByRole('button', { name: /mark as sold/i })).toHaveCount(0, { timeout: 10000 });
  await page.keyboard.press('Escape');

  // ---- Record a full payment for sale 1, then complete it -> deals_closed_count 0->1 -> Bronze(0-0) no longer matches -> auto-upgrades to Silver ----
  // The wizard's Payment Plan step only defines what's EXPECTED (the
  // schedule), not an actual payment -- same distinction M3's own e2e spec
  // already established (record a real payment separately, via the drawer's
  // own "Record Payment" action, before a sale can ever reach COMPLETED).
  await page.getByText('B-1', { exact: true }).click();
  await page.getByRole('button', { name: 'Record Payment' }).click();
  await page.locator('#payment-amount').fill('1000000');
  await page.locator('#payment-paidOn').fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Record Payment' }).click();
  await expect(page.getByText('₹0').first()).toBeVisible({ timeout: 10000 });

  await page.getByRole('button', { name: 'Mark Complete' }).click();
  // No standalone "COMPLETED" status badge is rendered in this panel --
  // "Mark Complete"/"Cancel Sale" both gate on sale.status === 'ACTIVE',
  // so their disappearance is the panel's own signal the sale left ACTIVE.
  await expect(page.getByRole('button', { name: 'Mark Complete' })).toHaveCount(0, { timeout: 10000 });
  console.log('M6_SALE1_COMPLETED');
  await page.keyboard.press('Escape');

  // ---- Sale 2: B-2, same broker, no plot override -> should fall back to GLOBAL 3% ----
  await sellPlot(page, 'B-2', 'Ramesh Gupta', '9812345671', '1000000');
  // 3% of 10,00,000 = 30,000 -- GLOBAL fallback, not the 5% PLOT rate (scoped to B-1 only).
  await expect(page.getByText(/30,000/)).toBeVisible({ timeout: 10000 });
  console.log('M6_GLOBAL_FALLBACK_PREVIEW_CORRECT');
  await page.getByRole('button', { name: 'Next' }).click();
  const sale2ResponsePromise = page.waitForResponse((r) => r.url().includes('/sale') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Confirm Sale' }).click();
  await sale2ResponsePromise;
  await expect(page.getByRole('button', { name: /mark as sold/i })).toHaveCount(0, { timeout: 10000 });
  await page.keyboard.press('Escape');

  // ---- Rate-immutability: bump GLOBAL rate to 6%, confirm sale 2's ledger entry (already resolved at 3%) stays untouched ----
  await page.getByRole('link', { name: 'Brokers' }).click();
  await page.waitForURL('**/builder/brokers');
  await page.getByText('Suresh Sharma').click();

  // ---- Tier auto-upgrade confirmation: broker should now show Silver, not Bronze. ----
  await expect(page.getByText(/Silver/)).toBeVisible({ timeout: 10000 });
  console.log('M6_TIER_AUTO_UPGRADE_CONFIRMED');

  await page.getByRole('tab', { name: 'Commission Config' }).click();
  await page.getByRole('button', { name: 'Add Rate' }).click();
  await page.locator('input[type="number"][step="0.001"]').fill('6');
  await page.locator('input[type="date"]').first().fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Save Rate' }).last().click();
  await expect(page.getByText('6%')).toBeVisible({ timeout: 10000 });

  await page.getByRole('tab', { name: 'Ledger' }).click();
  const ledgerRows = page.locator('table tbody tr');
  // ₹30,000 legitimately appears twice in this row (Commission Earned AND
  // Balance Due, since nothing's been paid against it yet) -- .first() is
  // enough to prove the figure itself is correct without a strict-mode
  // violation over which of the two identical cells matched.
  await expect(ledgerRows.filter({ hasText: 'B-2' }).getByText(/30,000/).first()).toBeVisible({ timeout: 10000 });
  console.log('M6_RATE_IMMUTABILITY_CONFIRMED');

  // ---- Record a commission payment against sale 2's (still-PENDING) ledger entry ----
  await ledgerRows.filter({ hasText: 'B-2' }).getByRole('button', { name: 'Record Payment' }).click();
  await page.getByLabel('Amount').fill('30000');
  await page.locator('input[type="date"]').first().fill(new Date().toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Record Payment' }).last().click();
  await expect(page.getByText('Paid').first()).toBeVisible({ timeout: 10000 });
  console.log('M6_COMMISSION_PAYMENT_RECORDED');

  console.log('M6_BROKER_COMMISSIONS_TEST_PASSED');
});
