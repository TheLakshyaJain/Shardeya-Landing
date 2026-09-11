import { expect, test, type Page } from '@playwright/test';
import { goToProjects, signupBuilder } from './helpers';

// Closes a real bug flagged during user testing: a plot marked RESERVED had
// no way back to AVAILABLE (or forward to SOLD via the sale wizard) at all.
// PlotForm never rendered PlotStatusSelector in edit mode (only on create),
// and even if it had, the general PATCH /plots/{id} it submits to has no
// status/reservedFor/reservedUntil fields -- those only exist on the
// dedicated PATCH /plots/{id}/status endpoint (updatePlotStatus in
// plotApi.ts), which was defined but never actually called from anywhere.
// Only a direct edit to/from SOLD should ever be blocked; PlotForm now
// always renders the status control and routes status changes through the
// dedicated endpoint when editing.

const API_BASE = process.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

async function getAccessToken(page: Page): Promise<string> {
  return page.evaluate(() => (window as unknown as { __authStore: { getState: () => { accessToken: string } } }).__authStore.getState().accessToken);
}

test('a reserved plot can be changed back to Available (and started from Available to Reserved) via the edit form', async ({ page }) => {
  await signupBuilder(page);
  const token = await getAccessToken(page);

  const createRes = await page.request.post(`${API_BASE}/projects`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: 'Reserved Status Test Project',
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
  expect(createRes.ok()).toBeTruthy();
  const project = await createRes.json();

  await page.request.post(`${API_BASE}/projects/${project.id}/plots`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { plotNumber: 'R-1', sizeValue: 1000, sizeUnit: 'SQ_FT', price: 300000, isGarden: false, isCorner: false, isHot: false },
  });

  await goToProjects(page);
  await page.getByText('Reserved Status Test Project').click();
  await page.getByRole('tab', { name: /plots/i }).click();
  await page.getByText('R-1', { exact: true }).click();
  await expect(page.getByText('AVAILABLE', { exact: false }).first()).toBeVisible();

  // Available -> Reserved.
  await page.getByRole('button', { name: 'Edit' }).click();
  await page.getByRole('combobox').filter({ hasText: 'Available' }).click();
  await page.getByRole('option', { name: 'Reserved', exact: true }).click();
  await page.locator('#reservedFor').fill('Ramesh Kumar');
  await page.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByText('RESERVED', { exact: false }).first()).toBeVisible({ timeout: 10000 });

  // Reserved -> Available -- this direction was the actual reported bug.
  await page.getByRole('button', { name: 'Edit' }).click();
  await page.getByRole('combobox').filter({ hasText: 'Reserved' }).click();
  await page.getByRole('option', { name: 'Available', exact: true }).click();
  await page.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByText('AVAILABLE', { exact: false }).first()).toBeVisible({ timeout: 10000 });
});
