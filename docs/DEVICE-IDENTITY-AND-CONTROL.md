# Device identity, network data and Android control — implementation contract

This repository's dashboard is a static preview. It does not currently communicate with Android devices or Supabase.

## Device identity: IMEI is not a reliable app identifier

Do not promise IMEI collection from a normal Android application. On modern Android versions, access to non-resettable identifiers such as IMEI is restricted to privileged/system apps, carrier/device-owner contexts with specific privileges, or other narrowly permitted cases. A regular Play-distributed app generally cannot read it. The app must not try to bypass those restrictions.

Use these identifiers instead:
- A cryptographically random PHANTOM installation ID generated at first run.
- Android app version and OS version.
- Manufacturer/model reported by supported Android APIs.
- Optional owner-entered asset tag or serial number, clearly marked as user-provided.

If an organization has a legitimate enterprise device-owner deployment, evaluate Android Enterprise / managed-device APIs separately and disclose any additional identifiers to the administrator and device user.

## IP address

- The backend can record the public source IP observed on an authenticated device heartbeat. It may represent a carrier NAT, VPN, proxy, or shared gateway—not a precise device location.
- The Android app may report local network interface information only where available and justified.
- IP addresses are personal data in many contexts: minimize retention, restrict access, and disclose collection.
- A browser invitation page must not be used as an IP/device fingerprinting trick.

## Supported owner-approved capabilities

| Capability | Implementation requirement |
|---|---|
| Online / last seen | App heartbeat over authenticated TLS |
| Battery / charging | Android BatteryManager APIs |
| Network type / connectivity | Android connectivity APIs |
| Model / Android version | Supported Build fields |
| Location | Runtime location permission; background access only when separately justified and approved |
| Ring / audible alert | Explicitly enrolled app receives signed command and plays visible/audible alert |
| Recovery message | App displays owner-provided message |
| Screen sharing | Android MediaProjection consent dialog every session; visible ongoing notification and stop control |
| Lock | Prefer Android's supported Find Hub / managed-device capabilities; do not abuse Accessibility or Device Admin |
| Camera / microphone | Not part of unattended recovery; only a separate visible, user-approved session if a legitimate product need exists |

## Enrollment and command security

1. Admin signs in with Supabase Auth and creates a short-lived, one-time enrollment invitation through an Edge Function.
2. Server stores only a cryptographic hash of the invitation token. Raw token is shown once.
3. Device owner opens the invitation, sees the organization and data disclosure, installs the official app, authenticates, and accepts.
4. Android app generates a per-installation key pair in Android Keystore. Server binds the public key to the device record.
5. Device reports a permission inventory; each permission is independently granted in Android settings/prompts.
6. App sends signed, authenticated heartbeats. Server records minimal telemetry and observed source IP.
7. Admin recovery requests are created server-side after role, organization, device status and permission checks. App verifies command signature, expiry, nonce and allowed command type.
8. Owner can revoke enrollment from the app and admin can revoke it from the dashboard. Revocation invalidates credentials and pending commands.
9. Every enrollment, permission change, data access, command and revocation is audit logged.

## Never implement

- Link-click permission escalation or silent installation.
- Hidden GPS, camera, microphone, screen recording or keystroke collection.
- Reading private messages, passwords, OTPs or other apps' data.
- Accessibility-service abuse, root exploits, stealth persistence or bypassing Android permission prompts.
- Commands that evade the device owner's ability to see or stop a session.

## Production readiness checklist

- [ ] Android Kotlin app with owner-facing disclosure and consent screens
- [ ] Supabase Auth and organization membership
- [ ] Tenant-isolated RLS and server-only Edge Function access to device credentials
- [ ] One-time hashed enrollment tokens with expiry and rate limits
- [ ] Android Keystore device identity and signed heartbeats/commands
- [ ] FCM delivery plus polling/retry fallback
- [ ] Owner revocation and server-side credential invalidation
- [ ] Private Realtime channels, no public device telemetry
- [ ] Retention/deletion controls and audit review
- [ ] Security tests for cross-tenant access and replayed/expired commands
- [ ] Physical-device testing across supported Android versions

## Current status

The dashboard is still a demo. Its IMEI column intentionally says restricted/unavailable, and its IP column says not connected. No live device data is collected until the Android agent and authenticated backend are implemented and deployed.
