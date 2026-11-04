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

All new tables have RLS enabled. Invitation/session writes use the trusted Edge Function. Owner reads are scoped by `auth.uid()`. No existing dairy, barber or other app tables are modified by PHANTOM migrations.

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

## Android stage (not yet integrated)

The `android/` folder is a starter only. It does not yet send telemetry to this web dashboard or receive recovery commands. A later Android release requires visible installation, Android permissions, app signing, secure device keys, and user-approved capabilities. IMEI collection is intentionally excluded.

## Known limits

This is a functional web MVP for consent-based browser enrollment, not a complete Android recovery product. It does not provide remote lock, alarm, background GPS, SIM-change detection, or reliable monitoring after the browser is closed. Do not advertise those capabilities until the Android agent and its security tests are implemented.
