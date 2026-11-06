# PHANTOM RECOVER

**Your Device. Your Consent. Your Control.**  
Mayuu Control · Personal Device Security & Recovery Platform

## Project status

The repository was intentionally emptied on 2026-11-06. This is the clean rebuild on the existing `main` branch. The Supabase project was preserved; PHANTOM tables and migration history still exist, but the `phantom-web` Edge Function currently returns HTTP 410.

The current web foundation provides:
- Supabase Auth sign-in restricted to `mayur99@gmail.com`
- Owner-scoped reads protected by database RLS
- Live device, browser enrollment, location, command, audit and redirect records
- Online/offline display derived from server heartbeat timestamps (90-second threshold)
- Explicit capability status; unavailable backend actions are not presented as working

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

Open `index.html` through a local static server or GitHub Pages. Supabase JS is loaded from the v2 CDN as specified in the project baseline. The Android project is being restored in a later implementation phase.

## Current limitations

The Edge Function currently returns 410. Enrollment, heartbeat writes, location uploads, recovery command dispatch, redirect generation and media signaling are intentionally disabled until the API has been rebuilt and tested. The dashboard must not be treated as a complete recovery product yet.

See [CHANGELOG.md](CHANGELOG.md) and [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md).
