# Verification record

CareerBridge rename verified locally on 10 October 2026. All demo identities, companies, opportunities and QA records are fictional.

| Check | Result |
| --- | --- |
| Spring/Maven `verify` | 15 passed: 14 HTTP workflow tests and 1 context startup test |
| React/Vite production build | Passed |
| Python unit/API tests | Original v1 baseline: 9 passed; not rerun for this branding-only Python title change |
| Browser workflow check | 45 authenticated page visits across all four roles; desktop and 390px mobile layouts |
| Browser JavaScript errors | None in the checked workflows |
| HTTP server errors during browser workflows | None |

## What the backend checks exercise

- Public signup versus staff provisioning and role restrictions.
- Recruiter job ownership and student/campus eligibility.
- Actual PDF, DOCX and TXT extraction; resume privacy and version capture.
- Duplicate applications and protection of submitted resume versions.
- Pipeline transitions, interview scheduling and overlapping interview rejection.
- University scope isolation and distinct hired student counts.
- Notification and mock-session ownership.
- Saved jobs, recommendations and missing-skill paths.
- Session revocation after logout or admin disabling an account.
- Invitations, interview updates and campus announcements.
- A healthy default startup with email and Redis disabled.

The tests use real HTTP requests against a random-port Spring server, with a separate in-memory H2 database. They do not read or erase your normal local database.

## What the browser check exercises

The browser signs in through the actual React login page, creates an opening, edits a student profile, adds a project, uploads/downloads a resume, applies for the new opening and completes text interview practice. It then shortlists that application, downloads the submitted resume, schedules an interview, advances it to Offered and Hired, checks the university placement count, exports CSV, sends a campus announcement and provisions a staff account.

It also visits every main workspace page, checks interview/announcement visibility for the student, and verifies mobile navigation and horizontal overflow. The CareerBridge browser rerun used Spring with its local matching fallback; the optional Python service was not started. The original v1 browser check also exercised Python rank/questions endpoints. The screenshots in this folder show the app during these QA workflows, so they include temporary QA entries.

## Repeat the browser check

**Use a fresh, isolated demo database.** This check intentionally creates records and expects the initial seeded placement count. It does not clean up those records afterwards.

In a backend PowerShell terminal, choose a new database filename and isolated upload directory:

```powershell
cd D:\projects\careerbridge\backend
$env:DB_URL="jdbc:h2:file:./target/browser-demo-db;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE"
$env:UPLOAD_DIR="./target/browser-demo-resumes"
.\mvnw.cmd spring-boot:run
```

Start the frontend in a second terminal, then in a third terminal:

```powershell
cd D:\projects\careerbridge\frontend
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

Set `TEST_BASE_URL` if your frontend uses a different URL, or `TEST_OUTPUT_DIR` to choose the screenshot/result directory. The default is `frontend/target/browser-checks`.

Stop the isolated backend after the check. Remove the two temporary `backend/target/browser-demo-*` database files before rerunning against the same filename, or choose another filename. Clearing the environment variables and restarting returns to the default database:

```powershell
Remove-Item Env:DB_URL
Remove-Item Env:UPLOAD_DIR
```

## External services not verified

Docker runtime, a live PostgreSQL/Redis server, authenticated SMTP delivery, real provider-generated questions and public GitHub sync were not exercised in this environment. They require their respective runtime/network/credentials. Configuration and adapters are included; these checks do not establish production readiness or external delivery guarantees.

Python tests check the generated-question response parser and provider-failure fallback with mocked requests. No paid-provider request was made. Match scoring and practice feedback are deterministic aids, not independently validated models.

## Structured academic profile update — 10 October 2026

- Backend: 19 tests passed (14 existing workflow tests, 4 academic integration tests, and the application context test).
- Frontend: `npm ci` and the production build passed.
- Browser: 46 page visits passed across all four roles, with no JavaScript errors or API 5xx responses. Academic coverage includes save/reload, result-format changes, chronology errors, preservation during general-profile edits, recruiter candidate/application views, campus academic review, and mobile overflow checks. Backend tests also cover incomplete/cleared records and access control.
- Compatibility: started the previous main build with a persistent H2 database, uploaded a resume, then restarted the updated build against that same database. Existing accounts, JWT session, CGPA, projects, education, applications and resume download were preserved; new academic columns accepted a saved record.
- New academic results and backlog fields are self-declared. School-mark eligibility and document verification are not implemented in this update. College CGPA still uses the existing 10-point matching system.

These checks ran on Linux with Java 17 and Node.js 24. Windows startup instructions are included; this update was not executed on a Windows host or a live PostgreSQL instance.

## Job eligibility update — 10 October 2026

- Backend: 25 tests passed (14 existing workflow tests, 4 academic tests, 6 eligibility integration tests, and the application context test). Eligibility tests exercise inclusive cutoffs, each failing rule, missing details versus zero backlogs, diploma percentages, school CGPA/grade review, direct API enforcement, input validation, job ownership, clearing rules, and retention of existing applications after requirements change.
- Frontend: `npm ci` and the production build passed.
- Browser: the existing 46-page check passed across all four roles. `npm run test:eligibility` also passed recruiter create/edit persistence, graduation-year validation, missing details, below-cutoff and backlog explanations, eligibility filters, exact-threshold application, mobile dialog overflow, recruiter candidate comparisons, and a cutoff changed after the student opened the application form. No JavaScript errors or API 5xx responses occurred in either browser check. Screenshots/results use ignored `frontend/target/` directories.
- Compatibility: upgraded an existing persistent H2 database from the academic-profile build. Accounts, active JWT sessions, the full student/academic profile, job settings, application history and stored match score, and the submitted resume/download were preserved. New rule columns and branch/year collections saved successfully and survived a further backend restart.
- New restrictions default to absent on existing openings. School percentage cutoffs do not guess a conversion from CGPA or letter grades. Academic records remain self-declared; this feature does not verify marks documents or implement recruiter overrides.

To repeat the focused browser check, follow the isolated database setup above, then run `npm.cmd run test:eligibility` in the frontend terminal. It can follow `test:e2e` on the same isolated database. Do not use your normal database: the checks change demo academics and create openings/applications.

These checks ran on Linux with Java 17 and Node.js 24. A Windows host, a live PostgreSQL instance, and external email delivery were not exercised for this update.

## Email verification update — 11 October 2026

- Backend source compiled against the previously built application's cached dependencies and started successfully on Java 17.
- An isolated Spring/HTTP integration harness passed 33 checks: pending registration without JWT, hash storage, blocked login and direct API access, successful student/recruiter confirmation, single use, expiry, replacement and cooldown, invalid/disabled-account links, concurrent confirmation, local inbox escaping/binding/cache controls, rate limits, and legacy-account compatibility.
- A copied persistent H2 database upgrade preserved all original values across 21 tables. Unconfigured email rolled registration back, and the local inbox remained disabled by default.
- Frontend production build passed.
- Seven JUnit verification tests were added, and registration helpers in existing suites now confirm their test emails. The complete Maven/JUnit suite has not been run in this environment: the Maven repository is unreachable. Browser binaries are unavailable, so browser interaction and Windows launcher execution are not claimed as tested.
- Real SMTP delivery has not been tested. The local capture inbox verifies application behavior only. GitHub CI remains to be run after publishing authorization.

## Password recovery update — 11 October 2026

- Backend source compilation and frontend production build passed locally. Eight password recovery HTTP scenarios passed using a local runner against the compiled Spring application and an isolated H2 database. The full Maven/JUnit suite is also run by GitHub Actions.
- Coverage includes token hashing, expiry/replacement/cooldown, generic responses, mismatched/weak/unchanged passwords, UTF-8 BCrypt limits, purpose separation from verification, pending/disabled accounts, profile preservation, session revocation, account-setting password changes and concurrent single-use consumption.
- The browser check now covers Forgot password, filtered reset inbox, return navigation, matching passwords, reset success, old-session/old-password rejection, link reuse and desktop/mobile overflow, alongside the existing verification flow. GitHub Actions uploads screenshots/results.
- Compatibility: upgrading a copy of the previous persistent H2 database preserved every original column value across 21 tables. The previous 33-check email verification harness also passed; unconfigured recovery uniformly returned 503 for known and unknown addresses.
- Live SMTP delivery, a Windows host and a live PostgreSQL instance were not exercised for this update.

## Academic document review update — 11 October 2026

- Backend sources compiled on Java 17 using cached application dependencies. Ten HTTP integration scenarios passed against an isolated H2 database, covering approval, private access, campus scope, immutable snapshots, academic revision invalidation, mandatory correction reasons, replacement evidence, valid PDF/image contents, incomplete profiles, retained files and concurrent submissions/decisions. GitHub Actions passed all 51 Maven/JUnit tests, including these ten scenarios.
- Frontend production build and browser script syntax checks passed locally. The focused Chromium workflow exercises upload/submit, campus downloads, correction feedback, replacement documents, approval, recruiter badges and invalidation after a CGPA edit. The complete academic browser flow passed in GitHub Actions with no JavaScript errors or API 5xx responses. It captures desktop and mobile screens, including 320px document selection and review dialogs; results are uploaded by GitHub Actions.
- Compatibility: starting the new application against a copy of the existing persistent H2 database preserved every original column value across 21 tables. Pending submissions, immutable academic snapshots, uploaded evidence, campus approval and protected downloads survived two subsequent backend restarts.
- Review is performed by campus staff; these checks do not establish issuer authentication. Recruiters receive review status without document access or private feedback. Eligibility still uses the saved academic values and existing job cutoffs.
- Live SMTP delivery, a Windows host and a live PostgreSQL instance were not exercised for this update.

## University staff, dedicated academic uploads and campus drives — 12 October 2026

- Backend sources compiled with Java 17 using cached dependencies. Twelve isolated HTTP drive scenarios and all ten academic-review HTTP regression scenarios passed locally. The drive tests cover campus/recruiter ownership, hidden drafts, direct application visibility and reuse, current eligibility and optional verification, schedules and windows, status/result versions, withdrawal, ordered outcomes, cancellation, private exports, concurrent direct application/drive registration, admin-only officer provisioning and opening-deadline timezone consistency. GitHub Actions passed all 63 Maven/JUnit tests, including the twelve drive scenarios.
- Frontend production build and both focused browser scripts' syntax checks passed. GitHub Actions runs the existing verification/password recovery flow, the academic workflow using dedicated qualification sections, and a new campus-drive Chromium flow with officer provisioning, local-timezone schedules, registration, application reuse, campus isolation, attendance/results, export and completion. Desktop/mobile screenshots and results are uploaded for inspection.
- Compatibility: starting the updated build against a copy of the previous persistent H2 database preserved every original column value across 25 tables. Prior campus academic approval and uploaded evidence remained downloadable. Drive schedules, registrations, application links, attendance, outcomes, feedback and completion survived two further backend restarts.
- Published schedules and round history are retained. Campus round outcomes do not change recruiter application stages or imply an offer/hire. Students continue applying directly to ordinary recruiter openings. Public registration does not create placement officers.
- A Windows host, live PostgreSQL and external SMTP delivery were not exercised for this update.
