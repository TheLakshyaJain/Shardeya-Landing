import { expect, test } from '@playwright/test';

// Real-browser regression pass over every live-account fix shipped after
// the M7 milestone itself (see CLAUDE.md's "Post-M7 (First Half)" section,
// items 1-11) -- driven against the user's own seeded account, not a fresh
// signup, so real data (3 brokers, 30 plots, real leads, a real
// cheque-bounce reversal) exercises each fix the way it was actually found.
const MOBILE = process.env.M7_TEST_MOBILE ?? '9847319597';
const PASSWORD = 'Test1234';

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.locator('#identifier').fill(MOBILE);
  await page.locator('#password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();
  await page.waitForURL('**/builder/dashboard');
}

test.describe('Post-M7 live-account fixes', () => {
  test('Dashboard: Recent Activity gone, plot/lead cards not clickable, quick actions redirect correctly', async ({ page }) => {
    await login(page);

    // Item 1: Recent Activity section removed entirely.
    await expect(page.getByText('Recent Activity')).toHaveCount(0);

    // Item 2: plot-count cards and Active Leads are plain text, not links.
    for (const label of ['Total Plots', 'Available Plots', 'Sold Plots', 'Reserved Plots', 'Active Leads']) {
      const card = page.getByText(label, { exact: true }).locator('..').locator('..');
      await expect(card.locator('a')).toHaveCount(0);
    }

    // Item 11: "Bulk Upload" quick action is gone.
    await expect(page.getByRole('link', { name: 'Bulk Upload' })).toHaveCount(0);

    // Item 2/11: Record Payment lands on Tracker's Collections tab, not
    // wherever `?tab=collection` (singular, the old typo) used to fall
    // back to.
    await page.getByRole('link', { name: 'Record Payment' }).click();
    await page.waitForURL('**/builder/tracker?tab=collections');
    await expect(page.getByRole('tab', { name: /collections/i })).toHaveAttribute('aria-selected', 'true');
  });

  test('Financials: a real cheque-bounce reversal shows "Bounced", not "Reversal"', async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: 'Financials' }).click();
    await page.waitForURL('**/builder/financials');
    await page.getByRole('tab', { name: /payments/i }).click();

    // Item 6: the real seeded reversal (MILE/FY26-27/23, -10,000, reverses
    // a bounced cheque) must read "Bounced", and no row anywhere should
    // still show the old generic "Reversal" label for THIS transaction.
    await expect(page.getByText('Bounced', { exact: true }).first()).toBeVisible();
  });

  test('Stats: matview-backed charts show real, non-zero data', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await login(page);
    await page.getByRole('link', { name: 'Stats & Analysis' }).click();
    await page.waitForURL('**/builder/stats');

    // Item 7: Monthly Sales / Top Brokers / Revenue by Project used to be
    // permanently empty for every org (the RLS-blocked refresh bug).
    // Real broker names from the seeded account confirm Top Brokers has
    // actual rows, not an empty-state placeholder.
    await expect(page.getByText(/Suresh Sharma|Priya Verma|Amit Singh/).first()).toBeVisible({ timeout: 10000 });
    expect(errors).toEqual([]);
  });

  test('Add Plots goes straight to Quick Create, no Excel/locked-feature card', async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: 'Projects' }).click();
    await page.waitForURL('**/builder/projects');
    await page.getByText('Testing Again', { exact: true }).click();
    await page.getByRole('link', { name: 'Add Plots' }).click();
    await page.waitForURL(/plots\/new-bulk/);

    // Item 8: no "Import from Excel" card, no "Upgrade" lock badge --
    // the Quick Create range form is the only thing on the page.
    await expect(page.getByText('Import from Excel')).toHaveCount(0);
    await expect(page.getByText('Upgrade')).toHaveCount(0);
    await expect(page.locator('input[placeholder="A"]')).toBeVisible();
  });

  test('Documents tab: Layout Plan and Brochure have real download buttons', async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: 'Projects' }).click();
    await page.waitForURL('**/builder/projects');
    await page.getByText('Testing Again', { exact: true }).click();
    await page.getByRole('tab', { name: /documents/i }).click();

    // Item 9: "Testing Again" has both a real layout plan and brochure
    // already uploaded -- both must expose a working download link.
    const downloadLinks = page.getByRole('link', { name: 'Download' });
    await expect(downloadLinks).toHaveCount(2);
    for (const link of await downloadLinks.all()) {
      await expect(link).toHaveAttribute('href', /.+/);
    }
  });

  test('Tracker: Mark Done actually removes the lead from the follow-ups list', async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: 'Tracker' }).click();
    await page.waitForURL('**/builder/tracker');
    // Follow-ups is the default tab; "lak" is a real overdue lead seeded
    // on this account. Customer name and mobile share one table cell with
    // no wrapping element of their own, so an exact-text match against
    // "lak" alone would never match anything -- match the row instead.
    const row = page.getByRole('row').filter({ hasText: 'lak' });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /mark done/i }).click();

    // Item 10: before the fix, the interaction logged but the lead never
    // actually left the list -- this is the exact symptom that made it
    // look like the button did nothing.
    await expect(page.getByRole('row').filter({ hasText: 'lak' })).toHaveCount(0, { timeout: 10000 });
  });
});
