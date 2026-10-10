# Email verification

New public student/recruiter registrations create pending accounts with no JWT. Login and protected API actions require verification. The confirmation page requires a button click, so email previews do not consume the link. Links expire after 24 hours, work once, and are replaced on resend. Only SHA-256 token hashes are stored in the database; the random 32-byte token is sent by email. Per-account resend cooldown is 60 seconds. Per-IP request limits are 10 registration/resend requests or 30 confirmations per 15 minutes (in memory, per backend instance).

## Local Windows testing (no additional installation)

Stop both servers, then run from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1 -EmailTesting
```

1. Open http://localhost:5173/register and register a new student or recruiter. Use a test address such as `my-student@example.com`. Use a new CareerBridge password.
2. Attempt login before verification: it must be blocked.
3. On the CareerBridge verification page click **Open test inbox**. It uses the same tab and filters messages for your registration address. Click **Verify email** on the latest message. You can also bookmark http://127.0.0.1:8025 for direct access; there is no need to ask for its address again.
4. Click **Confirm email address**, then **Continue to sign in** with the registered credentials. The inbox, verification and sign-in steps reuse the same tab.
5. Reopen the same message/link and confirm again: it must be rejected. Use **Resend verification email** on login to request a replacement (wait 60 seconds).

The optional inbox binds only to 127.0.0.1, accepts localhost/127.0.0.1 Host headers, has no public application API, and keeps at most 200 messages in memory. Anyone using the same computer can read it. Messages clear when the backend stops; pending accounts and hashed links persist. Never enable LOCAL_EMAIL_TESTING on a shared or live server. It is an email capture tool; it does not prove ownership of a real inbox or test SMTP delivery. LOCAL_INBOX_PORT changes 8025 if needed.

Manual backend alternative:

```powershell
cd backend
$env:LOCAL_EMAIL_TESTING="true"
.\mvnw.cmd spring-boot:run
```

## Actual email delivery

Turn LOCAL_EMAIL_TESTING off. Configure MAIL_ENABLED=true, MAIL_FROM, SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, SMTP_AUTH and SMTP_TLS in the backend environment. Set FRONTEND_URL to the real HTTPS frontend address so email links open the deployed website. Keep secrets outside Git. Native Maven does not auto-load .env; Docker Compose uses the existing SMTP variables in .env.

Without local testing or SMTP, new registration fails with an explanatory 503 and is rolled back. A synchronous SMTP failure also rolls back a new token; the user can retry. SMTP acceptance is not a delivery guarantee: check spam and the provider's delivery logs. Do not share inbox passwords with CareerBridge users; they create a separate application password.

## Existing data

Existing accounts, including demo and administrator-provisioned accounts, retain access and display EXISTING_ACCOUNT rather than VERIFIED. Their new emailVerified column is null; no proof of inbox access is claimed. New public registrations are false until confirmed. This staged compatibility policy prevents locking existing users out. Retiring demo access, requiring proof for legacy accounts, password recovery, recruiter approval and university membership checks are separate updates. This update does not claim those are complete.

## API

- POST /api/auth/register: existing validated registration body; returns 201 with verificationRequired/message, no JWT.
- POST /api/auth/verify-email: {"token":"..."}; returns message, no JWT.
- POST /api/auth/resend-verification: {"email":"..."}; consistent response for unknown/already verified/disabled accounts and cooldowns.
- GET /api/auth/me includes emailVerified and emailVerificationStatus (VERIFIED/PENDING/EXISTING_ACCOUNT).

Verification links use a URL fragment to keep the raw token out of server request URLs. Confirmation uses POST, and successful consumption increments authVersion. Account locking serializes resend/confirmation; raw tokens and SMTP details are never logged. Limits reset on restart and are not distributed; a live multi-instance deployment needs shared rate limiting.

The inbox includes **Refresh inbox**, **Back to verification**, and **Show all** actions. Messages appear newest first, and older links are labelled. The app shows **Open test inbox** only when the opt-in local inbox is running; SMTP users continue to use their own email inbox. Opening a direct link from this chat may still create a separate tab: use the in-app button for the same-tab flow.
