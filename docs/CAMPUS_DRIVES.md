# University staff, academic uploads and campus drives

## Update your Windows project

Stop both servers with Ctrl+C. If your saved data changed since the last backup, copy the whole `backend/data` folder to your backup location, keeping the original folder in the project. Merge this pull request, fetch/pull main in GitHub Desktop, then restart normally with `start-dev.ps1`. A configured custom upload folder needs its own backup. This update adds four drive tables and leaves existing accounts, applications, academic reviews and documents in place.

## University placement officers

Sign in as admin and open **Accounts → Create placement officer**. Enter the name, email and temporary password, and choose the university. An officer manages that university's students, academic evidence and campus drives. A university can have multiple officers. The admin can manage all universities. To add a university first, use **Universities**.

Officers sign in through the normal sign-in page. They can change their temporary password in **Settings**. Public registration still offers Student and Recruiter; it does not grant campus staff privileges. The account table now includes account IDs and a separate **Create other account** action. Original demo credentials remain `Demo@12345` unless changed.

For a cross-university test, create a placement officer for **Northfield Institute (Demo)**, then sign in using that new account. The officer can review Northfield students and create Northfield drives. It cannot open SRM student evidence, SRM drive rosters or update SRM rounds. The original officer `placement@careerbridge.dev` belongs to SRM.

## Dedicated academic upload areas

Students open **Academic documents**. The page has separate sections for **10th marksheet**, **12th / diploma marksheet**, **College transcript** and **Additional supporting document**. Each upload receives its section's type automatically; there is no type dropdown. The first three categories are required for academic review, and additional documents are optional.

Choose a file in each required section and upload it. New uploads are selected for review automatically; a replacement in a required category replaces that category's current selection. You can still select supporting copies manually. The submission button remains disabled until all three required categories are selected; at most eight files may be selected. Save complete academic details in My profile before submitting.

Previously uploaded files stay under their original categories, with their history and retention rules. If an older upload was assigned the wrong category, remove it when unused and upload it through the correct section. Files already used in a review are retained. PDF, PNG and JPEG validation, privacy, corrections, approvals and renewed review after academic changes follow [academic review rules](ACADEMIC_REVIEW.md).

## What a campus drive adds

A recruiter opening describes the role and its eligibility requirements. A campus drive describes one university's recruitment event for an existing opening: registration dates, venue, instructions and ordered assessment/interview rounds. Ordinary openings continue appearing directly in **Opportunities**; students do not need a drive to apply.

Each drive links one opening. Create separate drives when roles need different events or processes. Staff can browse public and own-campus recruiter openings through **Campus openings**. Recruiters remain responsible for agreeing company participation and hiring decisions; the officer should confirm arrangements before publishing an event. Creating or publishing a drive does not duplicate or change the recruiter opening.

### Test creating and publishing a drive

1. Sign in as `placement@careerbridge.dev` (SRM), or an officer you created for your university. Open **Campus drives → Create campus drive**.
2. Select an active recruiter opening available to that university. Enter a drive title, venue, student instructions and the registration opening/closing times.
3. Add one to eight rounds. Give each a distinct name, time, venue and instructions. Registration must close in the future, after it opens, and by the opening's application deadline when set. Rounds must occur in order, after registration closes.
4. Times entered in the browser use your device's local timezone and are stored as UTC instants. The displayed schedule uses the viewer's local timezone.
5. Optionally require **campus verification of current academic details**. This applies to drive registration only; it does not add a new requirement to direct job applications.
6. Select **Save draft drive**. Drafts are visible to authorised campus staff and administrators. You can edit them before publishing.
7. Inspect the saved schedule, then choose **Publish campus drive**. Students in that university and the opening's recruiter are notified. Students explicitly register; an officer does not automatically enrol everyone.

Published schedules are retained rather than editable. If the event arrangements are wrong, cancel it and create a corrected event. Cancelling preserves registrations, round history and recruiter applications, and notifies registered students. Completed and cancelled drives are read-only.

### Test student registration and application reuse

1. Sign in as a student at the drive's university. Open **Campus drives → View drive**.
2. Read the schedule, opening eligibility checks and any academic-verification requirement. Register while the drive's registration window and linked opening are active.
3. If you already applied to that opening, registration links the existing application. If you have not applied, it creates a normal application visible to the recruiter. There is one application per student/opening, even when direct application and drive registration occur simultaneously.
4. A rejected, withdrawn or hired application cannot join a new drive. Current opening eligibility and the optional verification requirement are checked when registering, including registering again after leaving the drive.
5. You can **Withdraw drive registration** before the first round starts. This leaves the recruiter application in place. You can register again while the registration window is open; it reuses the same drive registration and application.

Changes to eligibility after registration do not silently delete registrations or existing applications. The latest requirements remain visible when viewing the drive. Global recruiter rejection or student withdrawal is reflected in the drive's campus progress and prevents further campus round updates.

### Test attendance, rounds and completion

1. After a scheduled round begins, the placement officer opens the drive (or selects **Refresh drive** if already viewing it) and chooses **Manage rounds for [student]**.
2. Record attendance as Pending, Present or Absent, and the campus outcome as Pending, Passed or Not selected. A Passed outcome requires Present attendance; a Not selected outcome requires feedback.
3. The student must pass earlier rounds before later rounds can be recorded. Once later rounds have recorded attendance, an earlier pass cannot be changed into a failure or pending result.
4. Students see their own results and feedback in the drive and receive notifications. The opening's recruiter sees registered campus participants and read-only round results. Another recruiter cannot see or export the roster. Students cannot see other students' registrations or download the roster.
5. Campus round outcomes do not change the original application's hiring stage. Recruiters continue Shortlist, Interview, Offer and Hire actions in **Hiring pipeline**. Campus rounds completed does not mean an offer or hire.
6. Use **Export participants** to download attendance, outcomes, feedback, application IDs and recruiter stages. Access is limited to authorised staff and the opening's recruiter; spreadsheet formula prefixes are escaped.
7. After the final round starts and active participants have terminal campus outcomes, select **Complete drive → Confirm completion**. Unresolved participants prevent completion. Withdrawn/rejected participants and students not selected in an earlier round do not require later-round outcomes.

## API

| Method and route | Purpose |
| --- | --- |
| GET `/api/drives` | Scoped list; students see their university, recruiters their openings, officers their university, admin all |
| GET `/api/drives/{id}` | Schedule and own registration, or authorised staff/recruiter roster |
| POST `/api/placement/drives` | Create draft: title, jobId, universityId for admin, venue, instructions, registrationOpensAt, registrationClosesAt, requireVerifiedAcademics, rounds |
| PUT `/api/placement/drives/{id}` | Edit a draft with its observed version |
| PATCH `/api/placement/drives/{id}/status` | Observed version plus PUBLISHED, CANCELLED or COMPLETED |
| POST `/api/student/drives/{id}/registration` | Register and reuse/create a recruiter application |
| DELETE `/api/student/drives/{id}/registration` | Leave the campus event before its first round begins |
| PATCH `/api/placement/drives/{id}/results/{resultId}` | Observed version, attendance, outcome and note |
| GET `/api/drives/{id}/export` | Authorised participant CSV export |

The added tables are `campus_drives`, `drive_rounds`, `drive_registrations` and `drive_results`. Account, drive, application and result checks use database-backed permissions; versions and locks protect conflicting submissions. Drive mutations and round updates are audited. Academic evidence remains accessible only through the existing protected document workflow.

## Automated checks

`CampusDriveTests` exercises the real HTTP API using an isolated H2 database, including university and recruiter permissions, draft visibility, application reuse, concurrency, schedules, registration windows, optional verification, withdrawal, outcome sequencing, cancellation, exports and officer provisioning. Existing academic review tests remain in place.

The dedicated browser checks use disposable databases and fictional records. After building the backend, run `npm.cmd run test:academics` and `npm.cmd run test:drives` from `frontend`, with Playwright Chromium installed. The drives check uses ports 8089 and 5179. It waits for its scheduled assessment to start, so allow a few minutes. Screenshots and results are stored under ignored `frontend/target/` folders and uploaded by GitHub Actions. No browser check uses your saved database.
