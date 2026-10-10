# Run CareerBridge on Windows

This is the full local v1 of your career and placement project. Build and use it first; the code walkthrough can come afterwards.

## 1. Extract into a new folder

Extract the ZIP so the project folder is `D:\projects\careerbridge`.
Inside it you should see `backend`, `frontend`, `ai-service` and this file.

Stop any currently running backend and frontend servers with **Ctrl+C** before starting this project.

## 2. Check the prerequisites

You already have Java 24 installed. This project targets Java 17 and requires a JDK 17 or newer. Install Node.js 24 LTS if Node is missing, then reopen PowerShell.

```powershell
java -version
node -v
npm.cmd -v
```

Internet is needed for the initial Maven and npm downloads. Maven itself does not need a separate installation; the wrapper is included.

## 3. Start the backend

Open one PowerShell terminal:

```powershell
cd D:\projects\careerbridge\backend
.\mvnw.cmd spring-boot:run
```

Wait for **Started CareerBridgeApplication**. Leave this terminal running.

## 4. Start the frontend

Open a second PowerShell terminal:

```powershell
cd D:\projects\careerbridge\frontend
npm.cmd ci
npm.cmd run dev
```

Open **http://localhost:5173** in your browser. The Spring API runs on port 8081. Use the frontend URL to explore the project.

The default persistent H2 database creates itself in `backend/data`. You do not need to install PostgreSQL, Redis, Python or Docker to try the core project.

Alternative: run `powershell -ExecutionPolicy Bypass -File .\start-dev.ps1` from the project folder. It installs frontend dependencies if missing and opens the two server terminals. The execution-policy override applies only to that process.

## 5. Try all four roles

The login page has buttons for each demo role. All demo passwords are **Demo@12345**.

| Role | Email | Try this |
| --- | --- | --- |
| Student | student@careerbridge.dev | Edit your profile, upload a resume, apply for an opening and start interview practice |
| Recruiter | recruiter@careerbridge.dev | Create an opening, review applications, shortlist and schedule an interview |
| Placement officer | placement@careerbridge.dev | View the batch, filter analytics, export CSV and send a campus announcement |
| Admin | admin@careerbridge.dev | Create staff accounts, add universities, disable accounts and inspect the audit log |

The vacancies, companies, other students and placement results are fictional demo data. New students and recruiters can register. Staff accounts are created by the admin.

For a simple end-to-end demo:

1. Sign in as the student and upload a PDF, DOCX or TXT resume in Resume studio.
2. Open Opportunities and apply to an eligible opening you have not applied for already. The seeded Java internship already has an application.
3. Sign out and sign in as the recruiter who owns that opening.
4. Open Hiring pipeline, review the application, change its stage to Shortlisted and schedule an interview.
5. Sign back in as the student and check Applications, Interviews and Notifications.
6. Advance the candidate to Offered, then Hired as the recruiter. The placement dashboard updates from these records.

## If something fails

| Symptom | Fix |
| --- | --- |
| Port 8081 already in use | Stop the earlier Spring server with Ctrl+C |
| Port 5173 already in use | Stop the earlier Vite server |
| `npm` is blocked by PowerShell | Use `npm.cmd`, as shown above |
| Backend works but the web page cannot load data | Start both terminals; read any backend error first |
| Maven dependency download fails | Check internet access, then run the command again |
| Demo login fails after you changed its password | Use the new password in the normal login form |
| Want to reset the demo | Stop the backend, back up `backend/data`, then remove that folder and restart. This erases local accounts, uploads and applications |
| GitHub sync fails | Check the public username and internet access; GitHub rate limits can also apply |

## Optional services

The app already has explainable matching, learning suggestions and text interview practice without an external AI provider. Python adds TF-IDF text ranking. Generated questions require your own configured API credentials. Email needs SMTP credentials. See `README.md` and `docs/INTEGRATIONS.md` when you want to enable them.

Stop each development server with Ctrl+C. Data persists between restarts.

## Updating to structured academic profiles

After merging the academic profile pull request, stop both development servers, fetch/pull `main` in GitHub Desktop, then run `start-dev.ps1` again. Keep `backend/data`; the existing local `update` schema setting adds nullable academic columns without resetting your accounts, resumes, or applications. Back up that folder before the first updated startup.

Sign in as a student and open **My profile → Academic details**. Add 10th, 12th or diploma, college, semester, backlog and gap information, then select **Save academic details**. Fields can be left blank. The general education list is retained for additional qualifications. College CGPA has moved into the academic section and remains on a 10-point scale. School results keep their original percentage, CGPA scale, or letter grade.

Recruiters can see school results in candidate cards and complete records in application reviews. Placement officers can open **View academics** in their student table and export the extended CSV. Records are self-declared; document verification is a future change. Job eligibility rules are described below.

## Job eligibility update

After merging the eligibility pull request, stop both servers, back up `backend/data`, fetch/pull `main` in GitHub Desktop, and restart the existing project. Keep your data folder. Existing openings start with no added school, branch, graduation-year or backlog restrictions until their recruiter edits them.

Recruiters: open **Your openings → New opening / Edit → Who can apply**. Enter school percentage cutoffs, college CGPA, branch/year lists and a maximum active backlog count. Leave optional rules blank to allow everyone; a backlog limit of 0 means no active backlogs.

Students: open **Opportunities**, use the **Eligibility** filter, and open a job to see its requirements and your result for each one. **Details needed** means missing values or a school result format that cannot be compared with a percentage. Use the **Update academic details** link and enter your institution-reported values. Matching ignores branch-name letter case and extra spaces, but does not guess abbreviations or CGPA-to-percentage conversions. Academic records remain self-declared.

Compatibility and eligibility are separate. A high match score does not bypass a cutoff. Existing applications remain in their pipeline if requirements change later.

## Test email verification

Start with `powershell -ExecutionPolicy Bypass -File .\start-dev.ps1 -EmailTesting`.
Register a new account, open http://127.0.0.1:8025, click its verification link, confirm, then sign in.
Existing accounts keep working. New registration requires the local testing option or configured SMTP.
See [email verification instructions](docs/EMAIL_VERIFICATION.md) for real delivery and limits.
