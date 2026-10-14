# Resume Studio: estimated ATS readiness

Students can upload a text-based PDF, DOCX or TXT, then analyse a saved version in **Resume Studio → ATS resume review**. Choose general readiness, an active opening visible to the student, or a pasted job description (30–12,000 characters). Reports are private to the student. Recruiters and other students cannot read or generate them.

## Explained scores

This is a CareerBridge content heuristic, not an employer ATS integration, hiring probability or validated assessment. There is no universal score claimed here. The first scorer version is `CONTENT_V1`.

| Readiness check | Points | Full-credit rule |
|---|---:|---|
| Contact details | 20 | Detected email (10) and supported phone pattern (10) |
| Standard sections | 30 | Separate Education (10), Skills (10), and Projects or Experience (10) heading lines; common heading aliases accepted |
| Contribution and evidence | 25 | Recognised action verb in Projects/Experience text (10), a number with an outcome/scope unit there (10), and a portfolio/profile URL pattern (5) |
| Extracted text completeness | 15 | At least 80 extracted words; 30–79 earns 8 |
| Content length | 10 | 100–1,000 extracted words; 50–99 or 1,001–1,800 earns 5 |

The report displays these thresholds and missing checks. Word counts are local heuristics, not ATS limits; adding filler is discouraged. A metric mention is evidence of a pattern, not a verification of the claim. Education grades/dates do not count as project outcome evidence. Freshers do not need employment history if they include projects. No check uses university prestige, demographics, student profile scores or academic eligibility.

Job match = `100 × matched unique skills / identified unique target skills`, rounded to an integer. Selected openings use declared required skills, falling back to the dictionary when none are declared. Pasted descriptions use a limited, documented-in-code skill dictionary. Aliases such as JS/JavaScript and Postgres/PostgreSQL are canonicalised. Boundaries distinguish Java from JavaScript, SQL from MySQL, and C from C++/C#. Repeated mentions add no points. When there are no identified skills or no target, the match score is **null**, never a fabricated 0 or 100. A mention does not establish proficiency; always review the identified skills.

## Versions and privacy

`resume_reports` stores the filename, source resume ID, report timestamp, scorer version, check results, target title, target fingerprint, skill lists and optional suggestions. It does not store the extracted resume text or job description. Reports are snapshots: editing an opening, changing a profile or deleting an unused resume version cannot rewrite a prior score. Deleting an attached resume still obeys the existing application-retention rule. Students may delete a report independently. Reports are listed in pages of 20; the UI supports browsing pages and selecting two reports across pages. Comparisons warn when targets or scoring methods differ.

The owner-only extracted-text preview helps identify missing/reordered content. The current parser does not perform OCR or assess page layout, columns, tables, grammar, factual accuracy or skill proficiency. No layout compatibility claim is made from plain extracted text.

## Optional AI suggestions

The checkbox is off by default. Students explicitly choose to share Projects/Experience section text (at most 8,000 characters), target title/description, missing skills and local suggestions. Common email, phone and URL patterns in project text are masked. This is **not full anonymisation**; other personal information may remain. Readiness and job-match scores stay local and are never taken from provider output.

The existing Python service must be running; the Spring process needs `AI_SERVICE_URL` and Python needs `OPENAI_API_KEY` and `OPENAI_MODEL` for a model supporting Structured Outputs. Compose already wires these environment variables. Native `start-dev.ps1` starts Java and React only; it does not start Python or auto-load `.env`. Do not commit keys. Provider calls may incur API charges.

Python uses the Responses API with `store: false`, a strict suggestion-only JSON schema and a 25-second timeout. Java's existing internal-service timeout is 35 seconds. Refusals, incomplete/invalid outputs, missing credentials and outages yield an explicit unavailable label while retaining the local report. Advice must preserve actual achievements and cannot create skills, credentials or metrics. Only suggestions are accepted; scores from a provider are ignored. There are no provider calls during automated tests.

## API and checks

All routes below require an enabled, verified student session and enforce ownership:

| Method | Route | Purpose |
|---|---|---|
| GET | `/api/student/resumes/{id}/text` | Owner-only extracted text |
| POST | `/api/student/resumes/{id}/reports` | Create snapshot; body `{jobId?, targetTitle?, jobDescription?, aiSuggestions:false}` |
| GET | `/api/student/resume-reports?page=0` | Latest reports, 20 per page |
| GET | `/api/student/resume-reports/{id}` | Read one saved report |
| DELETE | `/api/student/resume-reports/{id}` | Delete owned report; returns 204 |

Run `sh mvnw verify` in backend, `npm run build` and `npm run test:resumes` in frontend (install Chromium with `npx playwright install chromium`), and `python -m unittest discover -v` in ai-service. The browser check starts disposable Java/Vite servers on 8090/5180, uses an in-memory H2 database and fictional uploads, and stops the servers afterwards. Backend tests cover rule calculations, alias/boundary matching, ownership/role/campus enforcement, immutable snapshots, deletion, opt-in provider requests, masking and malformed response fallback. Python tests mock provider requests, including success/failure/refusal cases.
