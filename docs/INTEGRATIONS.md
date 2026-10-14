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

## Local interview questions and resume suggestions

Interview questions come from the Java or Python built-in question bank. Resume readiness, job skill coverage and improvement suggestions use Java rules. There are no external AI generation calls, provider API keys or per-request AI charges. The optional Python service only supplies local TF-IDF ranking and a local question bank; it does not contact a model provider.

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
