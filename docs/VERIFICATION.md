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
