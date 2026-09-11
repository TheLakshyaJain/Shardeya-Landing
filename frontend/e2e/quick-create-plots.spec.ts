import { expect, test, type Page } from '@playwright/test';
import { goToProjects, signupBuilder } from './helpers';

// B-06 Path A: Quick Range Create. Verifies the two things the user
// explicitly asked to see proven in a real browser: a deliberate collision
// is flagged before anything is committed, and a valid range creates plots
// that land correctly placed in the grid. Excel import (Path B) no longer
// has an entry point on this page at all -- it was removed from Add Plots
// entirely (permanently locked on every plan today, dead-end UI -- see
// CLAUDE.md's post-M7 notes), so this spec no longer asserts on it.

const API_BASE = process.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

async function getAccessToken(page: Page): Promise<string> {
  return page.evaluate(() => (window as unknown as { __authStore: { getState: () => { accessToken: string } } }).__authStore.getState().accessToken);
}

test('Quick Create flags a collision before commit, then creates and places a valid range', async ({ page }) => {
  await signupBuilder(page);
  const token = await getAccessToken(page);

  const projectRes = await page.request.post(`${API_BASE}/projects`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: 'Quick Create E2E Project', projectType: 'RESIDENTIAL_PLOT_COLONY', address: '1 Test Road',
      locality: 'Vaishali Nagar', city: 'Jaipur', stateCode: 'RJ', totalAreaValue: 5, totalAreaUnit: 'ACRE', declaredPlotCount: 10,
    },
  });
  const project = await projectRes.json();
  // Pre-existing plot that WILL collide with the range submitted below.
  await page.request.post(`${API_BASE}/projects/${project.id}/plots`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { plotNumber: 'A-2', sizeValue: 1000, sizeUnit: 'SQ_FT', price: 300000, isGarden: false, isCorner: false, isHot: false },
  });

  await goToProjects(page);
  await page.getByText('Quick Create E2E Project').click();
  await page.getByRole('link', { name: 'Add Plots' }).click();
  await page.waitForURL(/plots\/new-bulk/);

  // Add Plots now goes straight to the Quick Create form -- no intermediate
  // "choose a method" screen, since Excel import is no longer offered here.
  await page.locator('input[placeholder="A"]').fill('A');
  const numberInputs = page.locator('input[type="number"]');
  await numberInputs.nth(0).fill('1');
  await numberInputs.nth(1).fill('3');
  await page.locator('#quick-create-price').fill('420000');
  await numberInputs.nth(3).fill('1200');
  await page.getByRole('combobox').filter({ hasText: 'Unit' }).click();
  await page.getByRole('option', { name: 'Square Feet' }).click();

  // A-2 collides -- flagged before commit, Create Plots stays disabled.
  await expect(page.getByText(/already exist or repeat/i)).toBeVisible({ timeout: 10000 });
  await expect(page.getByRole('button', { name: 'Create Plots' })).toBeDisabled();

  // Shift the range clear of the collision.
  await numberInputs.nth(0).fill('10');
  await numberInputs.nth(1).fill('12');
  await expect(page.getByText(/already exist or repeat/i)).toHaveCount(0, { timeout: 10000 });
  await expect(page.getByRole('button', { name: 'Create Plots' })).toBeEnabled({ timeout: 10000 });

  await page.getByRole('button', { name: 'Create Plots' }).click();
  await expect(page.getByText('3 plots created')).toBeVisible({ timeout: 10000 });

  await page.getByRole('button', { name: 'View Grid' }).click();
  await page.waitForURL(/\/builder\/projects\/[a-f0-9-]+$/);
  await page.getByRole('tab', { name: /plots/i }).click();
  await expect(page.getByText('A-10', { exact: true })).toBeVisible();
  await expect(page.getByText('A-11', { exact: true })).toBeVisible();
  await expect(page.getByText('A-12', { exact: true })).toBeVisible();

  const gridRes = await page.request.get(`${API_BASE}/projects/${project.id}/plots/grid`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const grid = await gridRes.json();
  const placed = grid.plots.map((p: unknown[]) => ({ row: p[0], col: p[1], number: p[2] }));
  expect(placed).toContainEqual({ row: 0, col: 9, number: 'A-10' });
  expect(placed).toContainEqual({ row: 0, col: 10, number: 'A-11' });
  expect(placed).toContainEqual({ row: 0, col: 11, number: 'A-12' });
});
