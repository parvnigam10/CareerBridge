// Run against an isolated demo database: this test creates accounts/jobs and advances applications.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const BASE = process.env.TEST_BASE_URL || 'http://127.0.0.1:5173';
const OUT = path.resolve(process.env.TEST_OUTPUT_DIR || 'target/browser-checks');
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
  const page = await context.newPage();
  const errors = [], failures = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.url().includes('/api/') && response.status() >= 500) failures.push(`${response.status()} ${response.url()}`); });
  const unique = Date.now();
  const title = `Browser QA Java Internship ${unique}`;
  const checked = [];

  async function workspace(route) {
    await page.goto(BASE + route);
    await page.locator('.workspace-main').waitFor();
    assert.equal(await page.locator('.sidebar .brand-wordmark').innerText(), 'careerbridge\nPREPARE. CONNECT. GROW.');
    await page.waitForFunction(() => document.querySelector('.workspace-main') && !document.querySelector('.workspace-main .loading'));
    assert.equal(await page.locator('.errorbox').count(), 0, `Error at ${route}: ${await page.locator('body').innerText()}`);
    checked.push(route);
  }
  async function login(role) {
    await page.goto(BASE + '/login');
    await page.locator('.demo-grid').getByRole('button', { name: role, exact: true }).click();
    await page.waitForURL('**/dashboard');
    await page.locator('.workspace-main h1').waitFor();
    await page.waitForFunction(() => !document.querySelector('.workspace-main .loading'));
  }
  async function logout() {
    await page.locator('.sidebar').getByRole('button', { name: 'Sign out', exact: true }).click();
    await page.waitForURL('**/login');
  }
  async function screenshot(name) { await page.screenshot({ path: path.join(OUT, name + '.png'), fullPage: true }); }
  async function success(text) { await page.getByRole('status').filter({ hasText: text }).waitFor(); }
  async function ownApi(route) { return page.evaluate(async route => (await fetch('/api' + route, { headers: { Authorization: 'Bearer ' + localStorage.getItem('careerbridge.token') } })).json(), route); }

  try {
    await page.goto(BASE);
    assert.match(await page.title(), /^CareerBridge/);
    assert.equal(await page.locator('.public-header .brand-symbol').getAttribute('src'), '/careerbridge-icon.svg');
    await page.getByRole('heading', { name: /Your next chapter/ }).waitFor();
    await page.locator('.public-jobs .job-card').first().waitFor();
    await screenshot('landing-desktop');
    await page.locator('.public-jobs .job-card h3 button').first().click();
    await page.locator('dialog').waitFor();
    await page.getByRole('button', { name: 'Close dialog' }).click();

    await login('Recruiter');
    for (const route of ['/dashboard', '/openings', '/pipeline', '/candidates', '/interviews', '/company', '/notifications', '/settings']) await workspace(route);
    await workspace('/openings');
    await page.getByRole('button', { name: 'New opening' }).click();
    const form = page.locator('dialog form');
    await form.locator('[name=title]').fill(title);
    await form.locator('[name=description]').fill('Build Java services and SQL queries with tests for student career features.');
    await form.locator('[name=location]').fill('Remote');
    await form.locator('[name=workMode]').selectOption('REMOTE');
    await form.locator('[name=requiredSkills]').fill('Java, SQL');
    await form.getByRole('button', { name: 'Publish opening', exact: true }).click();
    await success('Opening published');
    await page.getByRole('heading', { name: title, exact: true }).waitFor();
    await screenshot('recruiter-openings');
    await logout();

    await login('Student');
    for (const route of ['/dashboard', '/profile', '/jobs', '/saved', '/applications', '/resumes', '/preparation', '/interviews', '/notifications', '/settings']) await workspace(route);
    await workspace('/profile');
    await page.locator('[name=headline]').fill('Java and SQL student building practical backend projects');
    await page.getByRole('button', { name: 'Save profile', exact: true }).click();
    await success('Profile updated');
    await page.getByRole('button', { name: 'Add project', exact: true }).click();
    await page.locator('dialog [name=title]').fill('Career workflow QA project');
    await page.locator('dialog [name=stack]').fill('Java, SQL');
    await page.getByRole('button', { name: 'Add to profile', exact: true }).click();
    await success('Added to your profile');
    await workspace('/resumes');
    await page.getByLabel('Choose resume file').setInputFiles({ name: 'browser-resume.txt', mimeType: 'text/plain', buffer: Buffer.from('Java SQL Spring Boot Python Git Docker projects') });
    await page.getByRole('button', { name: 'Upload new version' }).click();
    await success('Resume uploaded');
    await page.getByRole('heading', { name: 'browser-resume.txt' }).waitFor();
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download', exact: true }).first().click();
    const resumeDownload = await downloadPromise;
    assert.equal(resumeDownload.suggestedFilename(), 'browser-resume.txt');
    await workspace('/jobs');
    await page.getByLabel('Search opportunities').fill(title);
    await page.getByRole('button', { name: title, exact: true }).click();
    await page.locator('dialog textarea').fill('I have Java and SQL project experience and would like to contribute.');
    await page.getByRole('button', { name: 'Submit application', exact: true }).click();
    await success('Application submitted');
    await workspace('/applications');
    await page.getByRole('heading', { name: title, exact: true }).waitFor();
    await workspace('/preparation');
    await page.getByRole('button', { name: /Start a practice session/ }).click();
    await page.locator('dialog textarea').first().waitFor();
    const answers = page.locator('dialog textarea');
    for (let i = 0; i < await answers.count(); i++) await answers.nth(i).fill('I explain the problem, approach and result, reproduce errors using logs and tests, and measure performance with a profiler before optimizing.');
    await page.getByRole('button', { name: 'Finish and review', exact: true }).click();
    await page.getByRole('heading', { name: 'Your practice review', exact: true }).waitFor();
    await page.getByRole('button', { name: 'Close dialog' }).click();
    await workspace('/dashboard');
    await screenshot('student-dashboard');
    await logout();

    await login('Recruiter');
    await workspace('/pipeline');
    const row = () => page.getByRole('row').filter({ hasText: title });
    await row().getByRole('button', { name: 'Review' }).click();
    await page.locator('dialog [name=status]').selectOption('SHORTLISTED');
    await page.getByRole('button', { name: 'Update stage', exact: true }).click();
    await success('Application stage updated');
    await row().getByRole('cell', { name: 'Shortlisted', exact: true }).waitFor();
    await row().getByRole('button', { name: 'Review' }).click();
    const candidateDownloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download submitted resume' }).click();
    assert.equal((await candidateDownloadPromise).suggestedFilename(), 'browser-resume.txt');
    await page.getByRole('button', { name: 'Schedule interview', exact: true }).click();
    const future = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000);
    const localDate = new Date(future.getTime() - future.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    await page.locator('dialog [name=scheduledAt]').fill(localDate);
    await page.locator('dialog [name=meetingUrl]').fill('https://example.com/qa-meeting');
    await page.locator('dialog').getByRole('button', { name: 'Schedule interview', exact: true }).click();
    await success('Interview scheduled');
    for (const stage of ['OFFERED', 'HIRED']) {
      await row().getByRole('button', { name: 'Review' }).click();
      await page.locator('dialog [name=status]').selectOption(stage);
      await page.getByRole('button', { name: 'Update stage', exact: true }).click();
      await success('Application stage updated');
    }
    await screenshot('recruiter-pipeline');
    await logout();

    await login('Placement Officer');
    for (const route of ['/dashboard', '/batch', '/analytics', '/drives', '/notifications', '/settings']) await workspace(route);
    await workspace('/dashboard');
    const analytics = await ownApi('/placement/analytics');
    assert.equal(analytics.placedStudents, 2);
    assert(!analytics.students.some(s => s.user.name === 'Dev Mehta'));
    await screenshot('placement-dashboard');
    const csvPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export report' }).click();
    assert.equal((await csvPromise).suggestedFilename(), 'placement-report.csv');
    await page.getByRole('button', { name: 'Announcement', exact: true }).click();
    await page.locator('dialog [name=title]').fill('Browser QA campus workshop');
    await page.locator('dialog [name=message]').fill('Prepare a project walkthrough.');
    await page.getByRole('button', { name: 'Send announcement', exact: true }).click();
    await success('Announcement delivered');
    await logout();

    await login('Admin');
    for (const route of ['/dashboard', '/users', '/universities', '/analytics', '/audit', '/system', '/notifications', '/settings']) await workspace(route);
    await workspace('/users');
    await page.getByRole('button', { name: 'Create account', exact: true }).click();
    await page.locator('dialog [name=name]').fill('Browser QA Officer');
    await page.locator('dialog [name=email]').fill(`browser-officer-${unique}@example.com`);
    await page.locator('dialog [name=password]').fill('Strong@12345');
    await page.locator('dialog [name=universityId]').selectOption({ index: 1 });
    await page.locator('dialog').getByRole('button', { name: 'Create account', exact: true }).click();
    await success('Account created');
    await page.getByRole('row').filter({ hasText: `browser-officer-${unique}@example.com` }).waitFor();
    await screenshot('admin-accounts');
    await logout();

    await login('Student');
    await workspace('/interviews');
    await page.getByRole('heading', { name: title, exact: true }).waitFor();
    await workspace('/notifications');
    await page.getByRole('heading', { name: 'Browser QA campus workshop' }).waitFor();
    await page.setViewportSize({ width: 390, height: 844 });
    await workspace('/dashboard');
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'Mobile dashboard overflows');
    await page.getByRole('button', { name: 'Open menu', exact: true }).click();
    await page.locator('.sidebar').getByRole('link', { name: 'My profile', exact: true }).click();
    await page.waitForURL('**/profile');
    await page.waitForFunction(() => document.querySelector('.sidebar').getBoundingClientRect().right <= 0);
    await screenshot('student-mobile');
    await page.goto(BASE);
    await page.getByRole('heading', { name: /Your next chapter/ }).waitFor();
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'Mobile landing overflows');
    await screenshot('landing-mobile');

    assert.deepEqual(errors, [], 'Browser JavaScript errors');
    assert.deepEqual(failures, [], 'Backend errors during browser workflows');
    fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify({ checkedPages: checked, workflows: ['opening creation', 'profile/project edit', 'resume upload/download', 'apply', 'text mock', 'shortlist/interview/offer/hire', 'campus export/announcement', 'staff provisioning', 'mobile navigation'], javascriptErrors: errors, serverErrors: failures }, null, 2));
    console.log(`PASS: ${checked.length} page visits; all four role workflows and mobile layouts checked.`);
  } catch (error) {
    await screenshot('failure');
    console.error('Page at failure:', page.url());
    console.error((await page.locator('body').innerText()).slice(-5000));
    console.error(error);
    process.exitCode = 1;
  } finally { await browser.close(); }
})();
