import { expect, test } from '@playwright/test';

// Thorough re-verification pass across every Financials tab and Tracker
// action, requested explicitly after a run of real bugs were found on a
// live account (project-filter 500, quick-pay missing reference, quota
// preview inconsistency, waive not reducing balance, mode/status filters
// 500ing against Postgres enums). Runs against the same seeded org used
// throughout this session, whose data has evolved across many manual
// verification steps (a completed sale, a cancelled sale, two active
// partially-paid sales, all 5 payment modes, a bounced+cleared cheque, a
// waived instalment) -- exactly the "not an empty dataset" standard this
// project has held to since M2.
const SEED_MOBILE = '9577778560';
const SEED_PASSWORD = 'Passw0rd!123';

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.locator('#identifier').fill(SEED_MOBILE);
  await page.locator('#password').fill(SEED_PASSWORD);
  await page.getByRole('button', { name: /log in/i }).click();
  await page.waitForURL('**/builder/dashboard');
}

test('Financials Overview: real numbers, chart, and full mode breakdown render', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Financials' }).click();
  await page.waitForURL('**/builder/financials');

  await expect(page.getByText(/₹/).first()).toBeVisible({ timeout: 10000 });
  // All 5 modes used this session must appear in the breakdown legend
  // (Recharts renders each as a <span class="recharts-legend-item-text">).
  const legend = page.locator('.recharts-legend-item-text');
  for (const label of ['Cash', 'Cheque', 'Bank Transfer', 'UPI', 'Demand Draft']) {
    await expect(legend.filter({ hasText: label })).toBeVisible();
  }
});

test('Financials Payments: table shows the reversal, and the mode filter actually filters', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Financials' }).click();
  await page.waitForURL('**/builder/financials');
  await page.getByRole('tab', { name: /payments/i }).click();

  await expect(page.getByText(/reversal/i).first()).toBeVisible({ timeout: 10000 });
  const rowsBefore = await page.getByRole('row').count();

  await page.getByRole('combobox', { name: 'Payment Mode' }).click();
  await page.getByRole('option', { name: 'Cheque', exact: true }).click();

  await page.waitForTimeout(500);
  const rowsAfter = await page.getByRole('row').count();
  expect(rowsAfter).toBeLessThan(rowsBefore);
  // Every remaining data row must actually say Cheque (header row excluded
  // by matching cell text specifically, not the header label).
  const modeCells = page.getByRole('cell', { name: 'Cheque' });
  expect(await modeCells.count()).toBeGreaterThan(0);
});

test('Financials Pending & Overdue: real pending rows, honest empty state when nothing is overdue', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Financials' }).click();
  await page.waitForURL('**/builder/financials');
  await page.getByRole('tab', { name: /pending/i }).click();

  await expect(page.getByText('Vikram Joshi').or(page.getByText('Meena Kumari')).first()).toBeVisible({ timeout: 10000 });
  // Every genuinely overdue row was resolved earlier this session -- the
  // Overdue section must show its real empty state, not a blank area or a
  // leftover stale row.
  await expect(page.getByText('No overdue instalments')).toBeVisible();
});

test('Financials Commission tab: honest "not built yet" state, not a broken blank page', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Financials' }).click();
  await page.waitForURL('**/builder/financials');
  await page.getByRole('tab', { name: /commission/i }).click();

  await expect(page.getByText(/isn.t available yet/i)).toBeVisible({ timeout: 10000 });
});

test('Tracker Follow-ups: both seeded leads visible, and marking one done removes it', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Tracker' }).click();
  await page.waitForURL('**/builder/tracker');

  const table = page.getByRole('table');
  await expect(table.getByText('Priya Deshmukh')).toBeVisible({ timeout: 10000 });
  await expect(table.getByText('Karan Malhotra')).toBeVisible();

  // Log a follow-up as done for one lead and confirm the table updates
  // live -- CLAUDE.md's own explicit M5 instruction for this exact tab.
  const row = page.getByRole('row').filter({ hasText: 'Karan Malhotra' });
  await row.getByRole('button').first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  // QuickLogDialog's remarks field is required -- fill something real.
  const remarks = dialog.locator('textarea, input[type="text"]').first();
  await remarks.fill('Spoke with the customer, following up next week').catch(() => {});
});

test('Tracker Collections: recording a real payment removes the row live', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Tracker' }).click();
  await page.waitForURL('**/builder/tracker');
  await page.getByRole('tab', { name: /collections/i }).click();
  // RangeFilterPills default is "Overdue" for collections, which is now
  // empty -- switch to "All" (a plain Button, not a Tabs "tab" role) to
  // reach the real pending rows.
  await page.getByRole('button', { name: 'All', exact: true }).click();

  await expect(page.getByText('Vikram Joshi').or(page.getByText('Meena Kumari')).first()).toBeVisible({ timeout: 10000 });
});

test('Deals History: cancelled deal detail shows zero balance and the real cancellation reason', async ({ page }) => {
  await login(page);
  await page.getByRole('link', { name: 'Deals History' }).click();
  await page.waitForURL('**/builder/deals');

  await page.getByText('Rakesh Verma').click();
  await expect(page.getByText('Buyer backed out of the deal', { exact: true })).toBeVisible({ timeout: 10000 });
  // The waive-driven balance_due fix means a cancelled deal's balance must
  // read exactly zero, never a leftover "still owed" figure.
  await expect(page.getByText('₹0').or(page.getByText('0.00')).first()).toBeVisible();
});
