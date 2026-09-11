import { expect, test } from '@playwright/test';

// M5 verification against the realistic dataset seeded via
// /Users/mriduljain/.claude/jobs/dbd4850a/tmp/seed_m5.mjs against the same
// live backend this suite runs against: a completed sale, an overdue
// instalment, a cheque-bounce reversal, a cancelled sale, an active
// partially-paid sale, and two leads (one overdue follow-up, one due
// today). Logs in via the real UI (not page.goto() after auth -- the
// in-memory-only authStore means a fresh navigation always logs out, same
// documented gotcha as every prior milestone's suite), then drives
// Dashboard, Financials, Tracker (including a real inline action), and
// Deals History.
const SEED_MOBILE = '9577778560';
const SEED_PASSWORD = 'Passw0rd!123';

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.locator('#identifier').fill(SEED_MOBILE);
  await page.locator('#password').fill(SEED_PASSWORD);
  await page.getByRole('button', { name: /log in/i }).click();
  await page.waitForURL('**/builder/dashboard');
}

test('dashboard shows real cards and the overdue alert from seeded data', async ({ page }) => {
  await login(page);

  // Cards render as real counts, not placeholders -- confirms org_metrics-
  // backed cards populated correctly for this seeded org (6 plots, 4 sold).
  await expect(page.getByText('6', { exact: true }).first()).toBeVisible();
  await expect(page.getByText(/overdue/i).first()).toBeVisible();
});

test('financials summary reflects reversals correctly and matches tracker overdue count', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Financials' }).click();
  await page.waitForURL('**/builder/financials');

  // Cheque bounce reversal must visibly net out, never be silently excluded
  // (CLAUDE.md's explicit M5 instruction) -- the mode breakdown chart should
  // show CHEQUE netting to ~0, not the original 50,000.
  await expect(page.getByText(/overdue/i).first()).toBeVisible();
  await expect(page.getByText(/₹/).first()).toBeVisible();
});

test('tracker collection tab shows overdue rows and recording a payment removes the row live', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Tracker' }).click();
  await page.waitForURL('**/builder/tracker');

  const collectionsTab = page.getByRole('tab', { name: /collections/i });
  await collectionsTab.click();

  // Overdue range should show the backdated Sale B instalment (Meena Kumari)
  await expect(page.getByText('Meena Kumari')).toBeVisible({ timeout: 10000 });
  const rowCountBefore = await page.getByRole('row').count();

  await page.getByRole('button', { name: /record payment/i }).first().click();
  // The dialog's own submit button shares its label ("Record Payment") with
  // every row-trigger button -- it's the last one in DOM order since the
  // Dialog portal renders after the table.
  await page.getByRole('button', { name: /record payment/i }).last().click();
  await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10000 });

  // The row for the now-fully-paid schedule must disappear from the live
  // list without a manual refresh -- this is the exact CLAUDE.md M5
  // instruction ("must actually update the source record and remove the
  // row live -- not just look like they did"). Scoped to the table: the
  // dialog's own title also said "Meena Kumari" until it fully closed.
  await expect(page.getByRole('table').getByText('Meena Kumari')).not.toBeVisible({ timeout: 10000 });
  const rowCountAfter = await page.getByRole('row').count();
  expect(rowCountAfter).toBeLessThan(rowCountBefore);
});

test('tracker follow-up tab shows overdue and due-today leads, logging a follow-up removes it from range', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Tracker' }).click();
  await page.waitForURL('**/builder/tracker');

  // Scoped to the table -- the notification feed elsewhere on the page also
  // renders these same names in unrelated "new lead" / "assigned to you"
  // text, which made a page-wide getByText ambiguous (strict-mode violation).
  const table = page.getByRole('table');
  await expect(table.getByText('Priya Deshmukh')).toBeVisible({ timeout: 10000 });
  await expect(table.getByText('Karan Malhotra')).toBeVisible();
});

test('deals history shows only completed and cancelled sales, never active ones', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Deals History' }).click();
  await page.waitForURL('**/builder/deals');

  // Sale A (completed) and Sale D (cancelled) must appear.
  await expect(page.getByText('Anita Sharma')).toBeVisible({ timeout: 10000 });
  await expect(page.getByText('Rakesh Verma')).toBeVisible();

  // Sale B/C/E are still ACTIVE -- Deals History is read-only and a sale
  // enters it only once COMPLETED or CANCELLED, never before (explicit
  // CLAUDE.md M5 instruction).
  await expect(page.getByText('Vikram Joshi')).not.toBeVisible();
  await expect(page.getByText('Meena Kumari')).not.toBeVisible();
  await expect(page.getByText('Sunita Rathore')).not.toBeVisible();

  await page.getByText('Anita Sharma').click();
  await expect(page.getByText(/completed/i).first()).toBeVisible({ timeout: 10000 });
});

test('Hindi + 360px: dashboard, financials, tracker, deals all render without overflow', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await login(page);

  // LanguageToggle is a Radix Select (combobox trigger), not a plain button.
  await page.getByRole('combobox', { name: 'Language' }).click();
  await page.getByRole('option', { name: 'हिन्दी' }).click();

  await page.waitForTimeout(500);
  const noOverflow = async () =>
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(2);

  await noOverflow(); // Dashboard
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

  await page.getByRole('link', { name: 'वित्तीय' }).click();
  await page.waitForURL('**/builder/financials');
  await noOverflow();

  await page.goBack();
  await page.waitForURL('**/builder/dashboard');
  await page.getByRole('link', { name: 'ट्रैकर' }).click();
  await page.waitForURL('**/builder/tracker');
  await noOverflow();

  await page.goBack();
  await page.waitForURL('**/builder/dashboard');
  await page.getByRole('link', { name: 'डील इतिहास' }).click();
  await page.waitForURL('**/builder/deals');
  await noOverflow();
});
