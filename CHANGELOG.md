# Changelog

## 2026-11-06
- Recreated the owner dashboard foundation in `index.html` after the repository was intentionally emptied.
- Added Supabase Auth owner sign-in for the configured owner email.
- Added live owner-scoped reads for device, browser enrollment, location, command, audit, enrollment-token and redirect records.
- Derived online/offline state from the server heartbeat timestamp with a 90-second timeout.
- Added explicit capability states and disabled claims for unverified or unavailable features.
- Added additive RLS hardening migration for owner-scoped PHANTOM reads and revoked anonymous table access.
- Restored and deployed `phantom-web` Edge Function (version 16) with owner allowlist, one-time Android/browser enrollment, hashed sessions, heartbeat, location upload, command polling/acknowledgment, revocation, consent auditing and bounded request bodies.
- Added five-minute browser command expiry and 90-second dashboard online/offline calculation.
- Added explicit-consent browser enrollment page with tab-only heartbeat and opt-in location sharing.
- Added Android 0.3.0 foundation with Keystore AES-GCM, visible foreground service, 30-second heartbeat, telemetry, one-shot GPS and Stop Protection.
- Added GitHub Actions Android build; debug APK artifact produced successfully (artifact ID 13500803342).
- Hardened PHANTOM table privileges: anonymous access revoked; authenticated clients receive SELECT only; writes are server-mediated.

- Deployed `phantom-web` version 20 with a backend/database health check.
- Added 90-day device/browser session expiry and server-confirmed Android Stop Session.
- Added safe Android re-enrollment after session expiry or revocation.
- Added owner recovery command UI for a single selected online Android device; commands expire after five minutes.
- Added owner revocation controls for both Android and browser enrollments.
- Added location coordinate copy controls, map links and freshness labels.
- Added redirect link creation and privacy-minimal aggregate visit tracking.
- Added a shared original shield/diamond SVG logo for web, enrollment and redirect pages.
- Added automated inline JavaScript syntax and public-secret hygiene validation workflow.

- Deployed `phantom-web` version 22 with Android consent-denial audit and server-side expiry marking for pending Android commands.
- Added bounded 20-second one-shot GPS timeout on Android and a visible-notification permission gate before starting Protection.
- Added Android local credential clearing when a session is reported expired or revoked.

- Deployed `phantom-web` version 23; redirect creation now returns its server record ID for per-link aggregate analytics.
- Dashboard now displays newly generated browser enrollment links, Android codes and redirect URLs once, with copy controls and expiry details; raw tokens remain hashed server-side.

- Added an Android 12+ shield-branded system splash screen and reused the PHANTOM vector logo in the native app header.

- Deployed `phantom-web` version 24; public IP telemetry now accepts only trusted `cf-connecting-ip` or `x-real-ip` server headers and never falls back to client-supplied `x-forwarded-for`.
