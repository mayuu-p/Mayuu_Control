# PHANTOM RECOVER — Mayuu Control

A consent-based Android device recovery and management dashboard.

## Current status

The first commit adds a responsive, static dashboard preview in `index.html`. It contains sample devices and demo-only actions. It does **not** connect to a phone, Supabase, GPS, Android screen sharing, or real command delivery yet.

## Safety and enrollment model

- An invitation URL is only an enrollment invitation; opening it does not grant device access.
- The device owner must install the Android agent, review the disclosure, authenticate, and approve each Android permission.
- Location reporting requires Android location permission.
- Screen sharing must use Android's visible MediaProjection consent flow and remain stoppable by the device owner.
- No covert camera, microphone, screen capture, message access, password collection, or permission bypass.
- Owners must be able to revoke enrollment. Actions must be authorized server-side and audit logged.

## Planned architecture

- Dashboard: HTML/CSS/JS preview; can later move to React.
- Backend: existing Supabase project `Mayur-System` (project ref `ewvirpfovwruxshzzlaa`, ap-south-1).
- Android agent: Kotlin, Android permission prompts, secure device identity, Firebase Cloud Messaging.
- Backend functions: authenticated enrollment, one-time expiring invitation tokens (store only token hashes), device heartbeat, permission state, recovery command queue, audit events.
- Database: isolated `phantom_*` tables with strict tenant-aware RLS. Existing unrelated tables must remain untouched.
- Never put Supabase service-role or secret keys in browser code.

## Run locally

Open `index.html` in a browser, or enable GitHub Pages from **Settings → Pages → Deploy from a branch → main → /(root)**.

## Next implementation stages

1. Review and apply a safe, isolated Supabase schema.
2. Implement Auth, organization membership, enrollment token issuance and one-time redemption through Edge Functions.
3. Build the Android Kotlin agent and consent screens.
4. Connect dashboard data through authenticated APIs and private Realtime channels.
5. Add tested recovery workflows, audit logging, revocation, security and performance checks.
