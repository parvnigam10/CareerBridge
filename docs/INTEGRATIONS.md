# Optional integrations

## Python career service

Install Python 3.12. In PowerShell:

```powershell
cd D:\projects\careerbridge\ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```

In the backend terminal, stop Spring, set the variable and restart:

```powershell
$env:AI_SERVICE_URL="http://127.0.0.1:8000"
.\mvnw.cmd spring-boot:run
```

The service needs no model download or credentials for TF-IDF ranking and its question bank. If unavailable, the Spring application falls back to its own matching and question bank. Keep the Python service private: it relies on Spring for user authentication and is not an independently public API.

## Generated interview questions

Before starting Python, set your own provider key and an available model that supports Structured Outputs:

```powershell
$env:OPENAI_API_KEY="your-key"
$env:OPENAI_MODEL="your-selected-model"
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```

Only the requested target role and skill are sent to the OpenAI Responses API. Resumes and mock answers are not sent to that provider. Requests set `store=false`. Provider terms and billing still apply. Do not commit keys or put them in frontend code. Provider failures use the labeled question-bank fallback.

No real paid-provider call was made during verification. Request construction, response parsing and fallback were checked without credentials. Official API reference: https://developers.openai.com/api/reference/resources/responses/methods/create

## SMTP email

Notifications always appear inside the application. Enable optional email in the backend terminal before restarting Spring:

```powershell
$env:MAIL_ENABLED="true"
$env:MAIL_FROM="your-sender@example.com"
$env:SMTP_HOST="your-smtp-host"
$env:SMTP_PORT="587"
$env:SMTP_USER="your-smtp-user"
$env:SMTP_PASSWORD="your-app-password"
$env:SMTP_AUTH="true"
$env:SMTP_TLS="true"
.\mvnw.cmd spring-boot:run
```

Use your provider's actual settings. Email is asynchronous and best effort. Delivery errors preserve the in-app notification; there is no retry queue or delivery-status UI. External email delivery was not verified without SMTP credentials.

## Redis and GitHub

Redis caches public GitHub API responses for 15 minutes. It is not used for student private profiles or JWT sessions. With a local Redis server:

```powershell
$env:REDIS_ENABLED="true"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
```

Then restart the backend. Without Redis, GitHub sync calls the public API directly. If Redis fails, sync tries the API. Public GitHub sync is subject to rate limits and does not verify account ownership. LeetCode is a profile link only.

## PostgreSQL

Create an empty database and user first, then set these backend variables before starting:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/careerbridge"
$env:DB_USER="careerbridge"
$env:DB_PASSWORD="your-database-password"
$env:JWT_SECRET="a-secret-containing-at-least-32-random-bytes"
.\mvnw.cmd spring-boot:run
```

No H2 data is automatically migrated into PostgreSQL. Demo data is seeded only when the account table is empty and `SEED_DEMO=true`. Docker Compose already supplies a PostgreSQL database/user and Redis connection settings.

The root `.env.example` is for Compose: copy it to `.env` to override Compose values. Maven/PowerShell does not automatically read `.env`; use `$env:NAME` as shown above.
