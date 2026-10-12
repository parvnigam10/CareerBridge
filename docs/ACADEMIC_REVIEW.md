# Academic document verification

Students can submit their saved academic details with supporting marksheets. A placement officer reviews records within their registered university; administrators can review across universities. Recruiters see the current review status in candidate cards and application reviews. Supporting files are private to the student and authorised campus staff.

## Windows update and manual test

1. Stop the backend and frontend with Ctrl+C. If your data has changed since the last backup, copy the entire `backend/data` folder to your backup location before the first updated startup. Keep the original folder in place.
2. Merge this pull request, fetch/pull main in GitHub Desktop, and start normally. `start-dev.ps1 -EmailTesting` still enables the existing verification/reset inbox when needed; academic review itself needs no email service.
3. Sign in as a student. In **My profile → Academic details**, save both school qualifications and college details. To submit for review, complete institution, board, passing year and result for 10th and 12th/diploma, plus college institution, degree, branch, graduation year, current semester, college CGPA and active backlogs. Other fields may be left blank when unknown.
4. Open **Academic documents** from the sidebar, or **Supporting documents** in your profile. Upload a PDF, PNG or JPEG through each dedicated 10th, 12th/diploma and college section. The section assigns its document type automatically. Use your latest transcript; include additional backlog/gap evidence when relevant. Each file must be at most 5 MB. Files are checked for valid content rather than trusting the filename or supplied MIME type.
5. Check the selected files in the three required sections, add an optional note, and choose **Submit for campus review**. Submission captures your saved academic record. Save edits in My profile before submitting. Your status becomes **Awaiting review**.
6. Sign in as a placement officer from the same university. For the seeded student, use `placement@careerbridge.dev` with `Demo@12345`, unless you changed that password. Open **Academic reviews**, choose **Review submission**, download and inspect the evidence, select your decision, and confirm that you checked the submitted details.
7. For **Request corrections**, enter a reason. The student sees the feedback in Review history and Notifications. They can use Delete beside an old document, confirm its removal from uploads, upload replacement evidence, select the supporting files and submit again. The earlier submitted copy remains in Review history.
8. For **Approve submitted academic record**, the current status becomes **Campus verified**. It appears on the student's profile, recruiter candidate card, application review, campus student table and CSV export. Recruiters do not receive the underlying marksheets or private review notes.
9. Change a reviewed academic value as the student and save. The badge becomes **Review needed again**. Returning a value to its old value does not restore approval; submit the updated record for a new review.

If you registered at a different university, provision a placement officer for that university through the admin Accounts page, or use the admin Academic reviews screen. A campus officer cannot open another university's documents or decide its submissions.

## Status meanings

| Status in the UI | Meaning |
| --- | --- |
| Self-declared | No submitted academic review yet |
| Awaiting review | Current saved record is waiting for campus staff |
| Campus verified | Staff approved the current submitted record |
| Corrections requested | Staff provided a reason; update evidence/details and submit again |
| Review needed again | Academic details or university scope differ from the previous submission |

The review concerns the saved academic record and attached evidence. It is a human review, with a recorded reviewer and date, rather than automatic authentication by the original school or document issuer. University membership is still selected at registration; campus staff should check enrollment as part of their review process. Job eligibility continues using the saved academic values and existing cutoffs. This update adds verification status; it does not make verification a mandatory job requirement or retroactively withdraw applications.

## Data and privacy

Submission snapshots and review decisions are stored in the database. Uploaded files are stored inside the configured `UPLOAD_DIR`, in its `academic-documents` subfolder. With default settings this is `backend/data/resumes/academic-documents`. Backing up `backend/data` preserves both database and uploaded evidence. If you configured another upload directory, back it up too. Restarting the backend preserves files, pending reviews, decisions and status. Local email inbox messages have their existing separate in-memory lifecycle.

The new tables are `academic_documents`, `academic_submissions` and its document-ID collection. The existing student profile receives a nullable academic revision column; existing profiles start at revision 0 and remain self-declared. No existing academic data or applications are reset.

The four upload sections are stacked vertically in the order 10th, 12th/diploma, college and additional documents. Each removable upload has a labelled Delete action with a confirmation. Unused uploads are deleted from storage. After corrections or academic changes, old evidence can be deleted from the upload area; its submitted copy stays available in Review history. Documents supporting a current pending or approved record remain protected. Removed uploads cannot be selected for a new submission. Retained review copies still count toward storage limits. Supported PDFs are unencrypted with 1–100 pages; images are at most 16 megapixels. Each student can retain up to 60 uploads totalling 100 MB. Download responses are authenticated attachments with no-store and nosniff headers. File storage keys are not returned in the API.

Academic changes increment a review revision. Unrelated profile changes, skills, visibility, account name and password changes do not invalidate an approval. A college CGPA change through the general profile API does invalidate it. Academic updates, submission and review use database locks; a second simultaneous submission or decision gets a conflict instead of duplicating work. Decisions also require the version observed by the reviewer, and old or already reviewed submissions cannot be approved again. Actions are recorded in the admin audit log; students receive in-app notifications.

## API

| Method and route | Purpose |
| --- | --- |
| GET `/api/student/academic-documents` | Own uploads, history, review status and current revision |
| POST `/api/student/academic-documents` | Multipart `kind` and `file`; kinds TENTH, SECONDARY, COLLEGE, OTHER |
| DELETE `/api/student/academic-documents/{id}` | Delete an unused own upload, or remove old evidence from uploads while retaining its submitted history |
| POST `/api/student/academic-submissions` | `{documentIds:[...],academicRevision:...,note:...}`; three required categories, at most eight selected files |
| GET `/api/placement/academic-reviews?status=PENDING` | Campus-scoped review queue; also VERIFIED, CHANGES_REQUESTED, NEEDS_RESUBMISSION or ALL |
| PATCH `/api/placement/academic-reviews/{id}` | `{version:...,status:"VERIFIED" or "CHANGES_REQUESTED",note:...,documentsChecked:true}` |
| GET `/api/academic-documents/{id}/download` | Owner or authorised campus staff download |

Profile, candidate and campus responses add `academicVerification` with current `status` and `verified`. Review notes and file lists are returned only through the protected document/review workspace. Submission responses contain the immutable academic snapshot; the displayed status may become NEEDS_RESUBMISSION while the original `decisionStatus` remains recorded in history.

## Automated checks

`AcademicReviewTests` exercises the real HTTP API against an isolated H2 database. The focused browser check covers student upload/submit, campus downloads, correction feedback, replacement documents, approval, recruiter badges and invalidation after a CGPA edit, including desktop and mobile layouts. It starts disposable servers and never uses your saved database.

From the project root, build the backend first, then run:

```powershell
cd backend
.\mvnw.cmd package -DskipTests
cd ..\frontend
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd run test:academics
```

The browser script uses ports 8088, 5178 and 8038; they need to be free. Results and screenshots are in the ignored `frontend/target/academic-review-checks` folder. Full Maven checks and the Chromium workflow also run in GitHub Actions.
