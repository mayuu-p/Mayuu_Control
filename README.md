# PHANTOM RECOVER — Mayuu Control

Consent-based, web-first browser enrollment and device recovery foundation.

## Live web MVP

- GitHub Pages app: https://mayuu-p.github.io/Mayuu_Control/
- Supabase project: `Mayur-System` (`ewvirpfovwruxshzzlaa`, ap-south-1)
- Edge Function: `phantom-web` (deployed; JWT is validated inside the handler for owner actions; public redeem requires a single-use invitation token and explicit consent).
- Web dashboard supports owner email/password sign-up/sign-in, one-time enrollment link generation (30-minute expiry), consent screen, limited browser metadata, server-observed public IP, opt-in one-shot GPS, owner device list, periodic refresh and revocation.
- Heartbeats run while the enrolled page remains open. Closing the page stops live updates; the dashboard then shows last-seen time.
- Public IP may represent VPN, carrier NAT or a shared gateway; it is not precise location.
- IMEI/SIM, hidden GPS, camera, microphone, messages, contacts, files and remote lock are not available through a browser link.

## Supabase schema

Original PHANTOM tables:
- `phantom_devices`
- `phantom_device_locations`
- `phantom_device_telemetry`
- `phantom_device_commands`
- `phantom_audit_logs`
- `phantom_enrollment_tokens`

Web MVP tables (added in a separate additive migration):
- `phantom_web_invites`: stores only SHA-256 invitation token hashes; raw invitation tokens are returned once.
- `phantom_web_enrollments`: owner-scoped browser enrollment, session secret hash, consent timestamp, limited metadata, observed IP and last seen.
- `phantom_web_locations`: GPS points submitted only after an explicit user click and browser permission.

PHANTOM tables have RLS enabled and the Edge Function performs owner/device authorization using the server-side key. Review Supabase Security Advisor findings before production; do not assume RLS-enabled tables are safe without appropriate policies and grants. No existing dairy, barber or other app tables are modified by PHANTOM migrations.

## Setup / test

1. Open the Pages URL above. If GitHub Pages is not enabled yet: repository Settings → Pages → Deploy from branch → `main` → `/(root)`.
2. Create an owner account with an email you control. If email confirmation is enabled, confirm it before signing in.
3. Sign in, open Enrollment links, generate a link and open it in a second browser/device.
4. Review the disclosure and accept. The owner dashboard should show the new browser and server-observed IP.
5. GPS is optional: tap “Share my current location” on the enrolled browser and approve the browser permission prompt.
6. Revoke the enrollment from the owner device list to invalidate its session.

## Security

- Frontend contains only the Supabase publishable key; no service-role/secret key.
- Edge Function uses the server-side Supabase service key, verifies owner JWTs with Auth, hashes invitation and session secrets, and atomically consumes invitations.
- Keep the Edge Function secret key private. Do not paste it into GitHub or the browser.
- Browser metadata is deliberately limited; no invasive fingerprinting.
- A browser must stay open to send heartbeats. This is a web session monitor, not an Android device-management agent.

## Android agent (0.2.0 foundation integrated)

The Android app now has a one-time owner-issued enrollment code, Android Keystore-protected session credential, visible foreground-service heartbeat, battery/network telemetry, polling recovery command queue, visible ring/lost-mode/recovery-message notifications, one-shot user-approved last-known location upload, and dashboard controls for listing, commanding and revoking enrolled Android devices.

Enrollment: sign in to the dashboard → Enrollment links → Native Android enrollment code → Generate Android code → paste it into the Android app and enroll. The code is single-use and expires after 15 minutes.

Current limits: command delivery uses 30-second polling, not FCM; the Android foreground service can still be stopped by force-stop, OS battery rules, OEM process management, reboot or network loss. Location is one-shot after a user action, not continuous background tracking. Native MediaProjection screen sharing, remote lock, SIM-change detection, FCM, hardware-backed device keys and signed command envelopes are not implemented. The screen-share command currently only displays a notification asking the user to open the app; it does not capture the screen. Camera/mic remain excluded from unattended recovery.

## Known limits

This is an integrated MVP foundation, not a certified production anti-theft product. Android heartbeat and commands require the app to be installed, enrolled and allowed to run its visible foreground service. Android OS restrictions can interrupt it. No 24/7 guarantee, remote lock, continuous background GPS, SIM-change detection, FCM push, native screen sharing, or stealth operation is provided. The Android project has not yet been built and tested on a physical handset in this environment.
