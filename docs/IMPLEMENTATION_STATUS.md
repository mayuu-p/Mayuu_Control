# PHANTOM RECOVER implementation status

Last checked: 2026-11-06

## Infrastructure audit
- GitHub repository and write access: verified.
- `main` and `android-agent-foundation` working trees: empty at start of rebuild.
- Supabase project: ACTIVE_HEALTHY.
- Owner Auth account: exists and email is confirmed.
- Existing PHANTOM tables: present, zero rows at audit time.
- Existing migration history: present.
- Existing `phantom-web` function: restored and deployed as version 24; runtime invocation still needs end-to-end verification.
- No secret values are printed or committed.

## Phase 1 progress
- [x] Recreate web owner dashboard foundation.
- [x] Supabase Auth sign-in and owner email allowlist.
- [x] Read real PHANTOM data under RLS.
- [x] Derive online status from server timestamps.
- [x] Add owner-scoped read policies and revoke anonymous access to PHANTOM tables.
- [x] Revoke authenticated client write privileges; PHANTOM writes are server-mediated.
- [x] Add expiring redirect links with privacy-minimal aggregate visit records; raw URL is returned once and visit counts are aggregated by redirect ID.
- [x] Add explicit browser consent, tab-only heartbeat, opt-in location sharing and Stop Session.
- [x] Add owner recovery controls for one selected online Android device and browser-device revocation.
- [x] Add Android explicit consent-denial audit, one-shot GPS timeout, 90-day session expiry and server-confirmed Stop Session.
- [x] Restore Edge Function actions: owner allowlist, hashed device/browser sessions, enrollment expiry, revocation, validation, no-store responses.
- [ ] Verify enrollment, heartbeat and location writes end-to-end.
- [x] Android project and build workflow; GitHub Actions debug APK build passed.
- [ ] Live owner sign-in and backend health test from GitHub Pages.
- [ ] Android enrollment/heartbeat/GPS test on a physical device.
- [ ] Browser enrollment/consent/location test in a real browser.
- [ ] Cross-owner, revoked-session and expired-token negative tests.

## Feature truth table
Android Keystore AES-GCM, foreground service, heartbeat, telemetry and one-shot GPS are present in source and compile in CI; physical-device behavior remains unverified. Browser media and WebRTC are not marked implemented. Redirect analytics records visit count and timestamp only; it does not store IP or browser fingerprints. Remote lock, SIM-change detection and covert collection of private notifications/calls are excluded.

## Deployment gate
Do not call the product complete until:
1. The Edge Function passes live owner/device authentication, expiry and revocation tests (not yet run from this environment).
2. Enrollment tokens and device session secrets are hashed at rest.
3. RLS and cross-owner isolation tests pass.
4. Android Gradle builds successfully.
5. Browser and physical Android tests are performed.
6. GitHub Pages and an actual Actions APK artifact are verified.
