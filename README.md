# PHANTOM RECOVER

**Your Device. Your Consent. Your Control.**  
Mayuu Control · Personal Device Security & Recovery Platform

## Project status

The repository was intentionally emptied on 2026-11-06. This is the clean rebuild on the existing `main` branch. The Supabase project and existing PHANTOM tables/migration history were preserved. The `phantom-web` Edge Function has been restored and is deployed (version 22).

The current web and API foundation provides:
- Supabase Auth sign-in restricted to `mayur99@gmail.com`
- Owner-scoped reads protected by database RLS
- Live device, browser enrollment, location, command, audit and redirect records
- Online/offline display derived from server heartbeat timestamps (90-second threshold)
- Explicit capability status; unavailable backend actions are not presented as working
- Browser and Android one-time enrollment APIs, heartbeat, telemetry, location upload, command polling/acknowledgment, revocation and 90-day session expiry
- Owner-generated redirect links with 90-day expiry and aggregate visit counts
- One-device-at-a-time recovery controls and browser-device revocation
- Backend health endpoint that verifies database connectivity without exposing secrets
- Android 0.3.0 foundation with AES-GCM Keystore storage and a visible user-started foreground service

## Infrastructure

- GitHub: [mayuu-p/Mayuu_Control](https://github.com/mayuu-p/Mayuu_Control), branch `main`
- GitHub Pages: https://mayuu-p.github.io/Mayuu_Control/
- Supabase project: `ewvirpfovwruxshzzlaa` (ap-south-1)
- Edge Function: `phantom-web`
- Owner allowlist: `mayur99@gmail.com`

## Security rules

- The publishable key may appear in public web code. Never expose service-role/secret keys.
- Owner access uses Supabase Auth; frontend allowlisting is not the authorization boundary. Server authorization and RLS are required.
- Device/browser session secrets are returned once and stored server-side only as SHA-256 hashes.
- Device and browser writes must be mediated by authenticated Edge Function actions.
- Location, camera, microphone and screen sharing require explicit device-side consent and visible stop controls.
- No covert surveillance, private notification/message/call collection, hidden camera/microphone, or permission bypass.

## Local development

Open `index.html` through a local static server or GitHub Pages. Supabase JS is loaded from the v2 CDN as specified in the project baseline. The Android project and GitHub Actions debug build workflow are present.

## Current limitations

The Edge Function is deployed with owner authentication, one-time Android/browser enrollment, hashed sessions, heartbeat, one-shot location uploads, command polling/acknowledgment, revocation and consent audit events. Redirect generation and aggregate visit tracking are implemented. WebRTC/media signaling, TURN, native Android media capture and push notifications remain unimplemented. Runtime API and physical-device end-to-end tests are still required. The dashboard must not be treated as a complete recovery product yet.

See [CHANGELOG.md](CHANGELOG.md) and [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md).
