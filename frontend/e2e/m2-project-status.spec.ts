import { expect, test, type Page } from '@playwright/test';
import { goToProjects, signupBuilder } from './helpers';

// Closes an M2 gap flagged during real-user testing: the backend has always
// supported changing a project's status (PATCH /projects/{id}/status,
// ProjectService.updateStatus has no transition guard -- any of
// UPCOMING/ACTIVE/COMPLETED can be set directly), but nothing in the UI ever
// called it. ProjectStatusSelector + this spec close that gap.

const API_BASE = process.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

async function getAccessToken(page: Page): Promise<string> {
  return page.evaluate(() => (window as unknown as { __authStore: { getState: () => { accessToken: string } } }).__authStore.getState().accessToken);
}

test('project status selector changes status and persists server-side', async ({ page }) => {
  await signupBuilder(page);
  const token = await getAccessToken(page);

  const createRes = await page.request.post(`${API_BASE}/projects`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name: 'Status Test Project',
      projectType: 'RESIDENTIAL_PLOT_COLONY',
      address: '1 Test Road, Jaipur',
      locality: 'Vaishali Nagar',
      city: 'Jaipur',
      stateCode: 'RJ',
      totalAreaValue: 5,
      totalAreaUnit: 'ACRE',
      declaredPlotCount: 20,
    },
  });
  expect(createRes.ok()).toBeTruthy();
  const project = await createRes.json();

  // Real in-app navigation only -- authStore is in-memory (see helpers.ts's
  // own comment), so page.goto() here would wipe the session.
  await goToProjects(page);
  await page.getByText('Status Test Project').click();
  await page.waitForURL(new RegExp(`/builder/projects/${project.id}`));

  const statusSelect = page.getByRole('combobox', { name: 'Status' });
  await expect(statusSelect).toContainText(/upcoming/i);

  await statusSelect.click();
  await page.getByRole('option', { name: /^active$/i }).click();
  await expect(statusSelect).toContainText(/active/i);

  // Verify persistence server-side via a direct API call rather than a page
  // reload -- a real reload logs the user out by design (in-memory session),
  // which is unrelated to what this assertion checks.
  const afterActive = await page.request.get(`${API_BASE}/projects/${project.id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect((await afterActive.json()).status).toBe('ACTIVE');

  await statusSelect.click();
  await page.getByRole('option', { name: /^completed$/i }).click();
  await expect(statusSelect).toContainText(/completed/i);

  const afterCompleted = await page.request.get(`${API_BASE}/projects/${project.id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect((await afterCompleted.json()).status).toBe('COMPLETED');
});
