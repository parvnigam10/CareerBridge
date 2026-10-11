# Forgot password

The sign-in page includes **Forgot password?**. Enter the account email, open the reset message, choose and confirm a new password, then sign in normally. Reset links expire after 30 minutes and work once. Merely opening a link does not change a password or consume it.

## Local Windows test

After merging the pull request, stop both servers and fetch/pull main in GitHub Desktop. Keep `backend/data`; the local schema update adds nullable reset columns. Keep a backup before the first updated startup.

Start from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1 -EmailTesting
```

1. Open http://localhost:5173/login and choose **Forgot password?**.
2. Enter an existing account email. If testing a newly registered account, verify its email first.
3. Select **Send reset link**, then **Open test inbox**. The inbox shows reset messages for that address. Refresh if the queued message is still arriving.
4. Select **Reset password**, enter matching new passwords, and select **Save new password**. Use 10–72 characters including uppercase, lowercase and a number; BCrypt also requires at most 72 UTF-8 bytes.
5. Select **Continue to sign in** and use the new password. The previous password must fail. Try the old reset link again: it must be rejected.

These steps stay in the same tab. The inbox remains http://127.0.0.1:8025 and can be bookmarked. Reset messages show a 30-minute expiry and a **Back to password recovery** button; verification messages retain their 24-hour flow. **Show all** removes the email filter while retaining the reset-message filter.

The account, changed password and hashed unexpired links persist in the database when the backend stops. Captured messages clear on backend shutdown. If a pending reset email is lost, restart with `-EmailTesting` and request another link after the 60-second cooldown. Queued requests are held in memory and can be lost if the process exits before delivery.

## Behavior and configuration

- Valid requests receive the same HTTP 202 message for known, unknown, disabled and cooldown-limited addresses. Email lookup and delivery run in a bounded background queue, keeping their latency out of the response. Malformed addresses fail validation. Queue overload or per-IP throttling returns 429.
- One current reset token is stored per account, as a SHA-256 hash of a cryptographically random 32-byte token. Requesting another link after the 60-second cooldown replaces the old one. Password changes in account settings and admin account status changes also invalidate outstanding reset links.
- Saving a reset changes the BCrypt password, increments the account session version and consumes the token in one locked transaction. All earlier login sessions become invalid. It does not sign the user in automatically. A password-change notification is recorded.
- Resetting does not verify an email address or change role, profile, academic records or applications. Pending accounts still need the separate email-verification step. Disabled accounts cannot recover access through reset.
- Limits are 10 reset-email requests and 30 reset submissions per IP per 15 minutes, in memory per backend instance. Restart clears these limits. Multi-instance deployments need shared rate limiting.
- Raw tokens travel in the email URL fragment and the reset POST body. They are not stored in account records or written to application logs. The loopback inbox contains the raw links and is only for local development.

Real email delivery uses the existing SMTP environment settings and `FRONTEND_URL`; see [email setup](EMAIL_VERIFICATION.md). Turn local email testing off for real delivery. If neither SMTP nor local testing is configured, recovery returns 503 regardless of address. A background preparation or SMTP failure rolls back the new token; the HTTP request has already received the generic message, so check spam/provider logs and request another link. No live SMTP provider was exercised by the automated checks.

## API

- `POST /api/auth/forgot-password`: `{"email":"your-account@example.com"}`; 202 with a generic message and no token.
- `POST /api/auth/reset-password`: `{"token":"...","newPassword":"New@54321Strong","confirmPassword":"New@54321Strong"}`; 200 with a message and no JWT. Invalid, expired, already used or disabled-account links return 400. Weak, mismatched or unchanged passwords return 400 without consuming a valid link.

The public React routes are `/forgot-password` and `/reset-password#token=...`. The reset endpoint uses a separate token field from email verification; neither token can be used for the other purpose.
