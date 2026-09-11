import { expect, test, type Page } from '@playwright/test';
import { goToProjects, signupBuilder } from './helpers';

// Regression coverage for a real bug reported by the user: "Mark all read"
// silently did nothing. Root cause was in the shared apiFetch client
// (lib/api/client.ts): it only treated HTTP 204 as an empty body, but
// NotificationController.markRead()/markAllRead() were plain void methods,
// which Spring MVC defaults to 200 OK with a genuinely empty body -- calling
// res.json() on that threw, silently rejecting the mutation's promise
// before onSuccess (which invalidates the query that refreshes the badge)
// ever ran. Fixed on both sides: apiFetch now treats any empty body as "no
// content" regardless of status, and the two endpoints now explicitly
// return 204 (matching the same pattern PlotController.delete() already
// used). Confirmed via the database that the backend was ALWAYS correctly
// persisting read_at -- this was a pure frontend bug.

const API_BASE = process.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

async function getAccessToken(page: Page): Promise<string> {
  return page.evaluate(() => (window as unknown as { __authStore: { getState: () => { accessToken: string } } }).__authStore.getState().accessToken);
}

test('Mark all read actually clears the unread badge', async ({ page }) => {
  await signupBuilder(page);
  const token = await getAccessToken(page);
  const headers = { Authorization: `Bearer ${token}` };

  const projectRes = await page.request.post(`${API_BASE}/projects`, {
    headers,
    data: {
      name: 'Mark All Read E2E Project', projectType: 'RESIDENTIAL_PLOT_COLONY', address: '1 Test Road',
      locality: 'Vaishali Nagar', city: 'Jaipur', stateCode: 'RJ', totalAreaValue: 5, totalAreaUnit: 'ACRE', declaredPlotCount: 10,
    },
  });
  const project = await projectRes.json();
  const plotRes = await page.request.post(`${API_BASE}/projects/${project.id}/plots`, {
    headers,
    data: { plotNumber: 'A-1', sizeValue: 1200, sizeUnit: 'SQ_FT', price: 4200000, isGarden: false, isCorner: false, isHot: false },
  });
  const plot = await plotRes.json();
  // Sell the plot to generate a real notification via the outbox -> poller
  // fan-out (same mechanism m3-sale-payments.spec.ts exercises).
  await page.request.post(`${API_BASE}/plots/${plot.id}/sale`, {
    headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() },
    data: {
      buyerName: 'Test Buyer', buyerMobile: '9876543210', purchaseDate: new Date().toISOString().slice(0, 10),
      dealValue: 4200000, paymentType: 'LUMP_SUM',
      schedule: [{ label: 'Full payment', amount: 4200000, dueDate: new Date().toISOString().slice(0, 10) }],
    },
  });

  await goToProjects(page); // just to leave the dashboard's empty state, any authenticated page works

  // Bell polls every 15s -- wait for the unread badge to actually show, then open it.
  await expect(page.getByRole('button', { name: 'Notifications' })).toContainText(/\d/, { timeout: 20000 });
  await page.getByRole('button', { name: 'Notifications' }).click();

  const markAllReadBtn = page.getByRole('button', { name: /mark all read/i });
  await expect(markAllReadBtn).toBeVisible({ timeout: 5000 });
  const readAllResponsePromise = page.waitForResponse((r) => r.url().includes('/read-all'));
  await markAllReadBtn.click();
  const readAllResponse = await readAllResponsePromise;
  expect(readAllResponse.status()).toBe(204);

  // This is the actual regression: the button must disappear once the
  // unread count query refetches as 0, not stay stuck forever.
  await expect(markAllReadBtn).toHaveCount(0, { timeout: 10000 });
});
