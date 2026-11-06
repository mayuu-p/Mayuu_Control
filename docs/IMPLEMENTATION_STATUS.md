# PHANTOM RECOVER implementation status

Last checked: 2026-11-06

## Infrastructure audit
- GitHub repository and write access: verified.
- `main` and `android-agent-foundation` working trees: empty at start of rebuild.
- Supabase project: ACTIVE_HEALTHY.
- Owner Auth account: exists and email is confirmed.
- Existing PHANTOM tables: present, zero rows at audit time.
- Existing migration history: present.
- Existing `phantom-web` function: active deployment returning HTTP 410; it must be replaced.
- No secret values are printed or committed.

## Phase 1 progress
- [x] Recreate web owner dashboard foundation.
- [x] Supabase Auth sign-in and owner email allowlist.
- [x] Read real PHANTOM data under RLS.
- [x] Derive online status from server timestamps.
- [x] Add owner-scoped read policies and revoke anonymous access to PHANTOM tables.
- [ ] Restore secure Edge Function actions.
- [ ] Verify enrollment, heartbeat and location writes end-to-end.
- [ ] Android project and build workflow.
- [ ] Real browser/device testing.

## Feature truth table
The dashboard intentionally marks unverified Android cryptography/service behavior as "Not verified". Browser media and WebRTC are not marked implemented. Remote lock, SIM-change detection and covert collection of private notifications/calls are excluded.

## Deployment gate
Do not call the product complete until:
1. The Edge Function has server-side owner/device authentication, expiry and revocation checks.
2. Enrollment tokens and device session secrets are hashed at rest.
3. RLS and cross-owner isolation tests pass.
4. Android Gradle builds successfully.
5. Browser and physical Android tests are performed.
6. GitHub Pages and an actual Actions APK artifact are verified.
