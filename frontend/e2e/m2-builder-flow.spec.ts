import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import { goToNewProject, goToProjects, signupBuilder } from './helpers';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TEST_IMAGE_PATH = path.join(__dirname, 'fixtures', 'test-image.png');

// Drives the M2 exit-criteria flows through a real browser against a live
// backend (see CLAUDE.md "Milestone 2 -- Decisions & Environment Notes" for
// the docker-compose stack this expects to be running). One serial spec so
// each step can build on the previous page's state, mirroring how a builder
// would actually use the app in one sitting.
//
// IMPORTANT: authStore is in-memory only (CLAUDE.md rule #15 -- no
// localStorage tokens), so after signupBuilder() every navigation MUST go
// through an in-app link/button click, never page.goto() -- a real browser
// navigation reloads the page and wipes the session, redirecting to /login.
test.describe.configure({ mode: 'serial' });

async function fillProjectBasics(page: import('@playwright/test').Page, name: string) {
  await page.locator('#name').fill(name);
  // .first() would match LanguageToggle's Select in the TopBar (also role
  // combobox, appears earlier in DOM order on every authenticated page) --
  // filter by the placeholder text to target the actual Project Type select.
  await page.getByRole('combobox').filter({ hasText: 'Project Type' }).click();
  await page.getByRole('option', { name: 'Residential Plot Colony' }).click();
  await page.getByRole('button', { name: 'Next' }).click();
}

async function fillProjectLocation(page: import('@playwright/test').Page, address: string) {
  await page.locator('#address').fill(address);
  await page.locator('#locality').fill('Test Locality');
  await page.locator('#city').fill('Jaipur');
  await page.getByRole('combobox').filter({ hasText: 'Select state' }).click();
  await page.getByRole('option', { name: 'Rajasthan' }).click();
  await page.getByRole('button', { name: 'Next' }).click();
}

async function fillProjectArea(page: import('@playwright/test').Page) {
  // step="any" is unique to AreaInput's own number input on this page.
  await page.locator('input[type="number"][step="any"]').fill('5');
  await page.locator('#declaredPlotCount').fill('10');
  await page.getByRole('button', { name: 'Next' }).click();
}

test.describe('M2 builder core inventory flow', () => {
  test('signup lands on the builder dashboard', async ({ page }) => {
    await signupBuilder(page);
    await expect(page).toHaveURL(/\/builder\/dashboard/);
  });

  test('create a project with all fields including cover image and layout map', async ({ page }) => {
    await signupBuilder(page);
    await goToProjects(page);
    await expect(page.getByText('No projects yet')).toBeVisible();

    await page.getByRole('link', { name: 'New Project' }).first().click();
    await expect(page).toHaveURL(/\/builder\/projects\/new/);

    await fillProjectBasics(page, 'Sunrise Meadows E2E');
    await fillProjectLocation(page, 'Plot 42, NH-8 Frontage Road');
    await fillProjectArea(page);

    // Step 4: media -- cover + layout images (exit criteria explicitly names both)
    const fileInputs = page.locator('input[type="file"]');
    await fileInputs.nth(0).setInputFiles(TEST_IMAGE_PATH);
    await expect(page.locator('img[alt="Cover Photo"]')).toBeVisible({ timeout: 10_000 });
    await fileInputs.nth(1).setInputFiles(TEST_IMAGE_PATH);
    await expect(page.locator('img[alt="Layout Plan"]')).toBeVisible({ timeout: 10_000 });
    await page.getByRole('button', { name: 'Next' }).click();

    // Step 5: review + submit
    await expect(page.getByRole('button', { name: 'Create Project' })).toBeVisible();
    await expect(page.getByText('Sunrise Meadows E2E')).toBeVisible();
    await page.getByRole('button', { name: 'Create Project' }).click();

    await page.waitForURL(/\/builder\/projects\/[0-9a-f-]+$/);
    await expect(page.getByRole('heading', { name: 'Sunrise Meadows E2E' })).toBeVisible();
  });

  test('add plots manually, grid renders with status colours, filter and search work', async ({ page }) => {
    await signupBuilder(page);
    await goToNewProject(page);
    await fillProjectBasics(page, 'Grid Test Project');
    await fillProjectLocation(page, '123 Test Road');
    await fillProjectArea(page);
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('button', { name: 'Create Project' }).click();
    await page.waitForURL(/\/builder\/projects\/[0-9a-f-]+$/);

    await page.getByRole('tab', { name: 'Plot Grid' }).click();
    await page.getByRole('button', { name: 'Configure Grid' }).click();
    await page.locator('#grid-rows').fill('10');
    await page.locator('#grid-cols').fill('10');
    await page.getByRole('button', { name: 'Save' }).click();

    // Plot 1: AVAILABLE at (0,0). AreaInput's size field has no id of its
    // own, so it's targeted via step="any" -- the one attribute unique to
    // it among this page's several type="number" inputs (PlotFilterBar's
    // Min/Max sqft filters are still mounted behind the sheet overlay, so
    // a plain `.first()` is not reliably this field).
    const sizeInput = page.locator('input[type="number"][step="any"]');
    await page.getByRole('button', { name: 'Add Plot' }).click();
    await page.locator('#plotNumber').fill('A-1');
    await sizeInput.fill('1200');
    await page.locator('#price').fill('4200000');
    await page.locator('#gridRow').fill('0');
    await page.locator('#gridCol').fill('0');
    await page.getByRole('button', { name: 'Add Plot', exact: true }).click();
    await expect(page.locator('#plotNumber')).toHaveCount(0, { timeout: 10_000 });

    // Plot 2: RESERVED at (0,1)
    await page.getByRole('button', { name: 'Add Plot' }).click();
    await page.locator('#plotNumber').fill('A-2');
    await page.getByRole('combobox').filter({ hasText: 'Available' }).click();
    await page.getByRole('option', { name: 'Reserved' }).click();
    await page.locator('#reservedFor').fill('Test Buyer');
    await sizeInput.fill('1200');
    await page.locator('#price').fill('4500000');
    await page.locator('#gridRow').fill('0');
    await page.locator('#gridCol').fill('1');
    await page.getByRole('button', { name: 'Add Plot', exact: true }).click();

    // Grid should now show both cells (DOM renderer at this scale)
    await expect(page.getByTitle('A-1')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTitle('A-2')).toBeVisible();

    // Filter: Available Only shows only the AVAILABLE cell at full opacity
    await page.getByRole('combobox').filter({ hasText: 'All statuses' }).click();
    await page.getByRole('option', { name: 'Available' }).click();
    await expect(page.getByTitle('A-2')).toHaveClass(/opacity-25/);
    await expect(page.getByTitle('A-1')).not.toHaveClass(/opacity-25/);
    await page.getByRole('button', { name: 'Clear all' }).click();

    // Search highlights the matching cell
    await page.getByPlaceholder('Search plot number...').fill('A-2');
    await expect(page.getByTitle('A-2')).toHaveClass(/ring-yellow-400/);
    await page.getByPlaceholder('Search plot number...').fill('');

    // Edit price -> grid/detail reflects the change
    await page.getByTitle('A-1').click();
    await page.getByRole('button', { name: 'Edit' }).click();
    await page.locator('#price').fill('5000000');
    await page.getByRole('button', { name: 'Save Changes' }).click();
    await expect(page.getByText('₹50,00,000')).toBeVisible({ timeout: 10_000 });
  });

  test('project quota is enforced on the Free plan', async ({ page }) => {
    await signupBuilder(page);
    await goToNewProject(page);
    await fillProjectBasics(page, 'First Project');
    await fillProjectLocation(page, '1 First Road');
    await fillProjectArea(page);
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('button', { name: 'Create Project' }).click();
    await page.waitForURL(/\/builder\/projects\/[0-9a-f-]+$/);

    await goToNewProject(page);
    await fillProjectBasics(page, 'Second Project');
    await fillProjectLocation(page, '2 Second Road');
    await fillProjectArea(page);
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('button', { name: 'Create Project' }).click();

    await expect(page.getByText(/reached your plan's project limit/i)).toBeVisible({ timeout: 10_000 });
  });

  test('Hindi language switch translates the projects page', async ({ page }) => {
    await signupBuilder(page);
    await goToProjects(page);
    await page.getByRole('combobox', { name: /language/i }).click();
    await page.getByRole('option', { name: 'हिन्दी' }).click();
    await expect(page.getByRole('heading', { name: 'प्रोजेक्ट' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'नया प्रोजेक्ट' }).first()).toBeVisible();
  });
});
