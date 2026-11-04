# PHANTOM RECOVER — Mayuu Control

A consent-based Android device recovery and management platform.

## Current implementation status

- `index.html`: responsive dark control-centre preview with overview, device inventory, location page, recovery action catalogue, enrollment preview, audit log and settings.
- `android/`: Kotlin Android starter app. It displays an app-generated installation ID, model, Android version, battery and network type. It requests location only after the owner taps the location button and displays last-known location locally.
- `supabase/migrations/`: isolated `phantom_*` tables created in the existing `Mayur-System` project. RLS is enabled and owner-scoped read access is configured.
- The web dashboard is **not yet connected** to the Android app or live Supabase data. The Android starter does not upload telemetry or receive commands.

## Supabase

Project: `Mayur-System` (ref `ewvirpfovwruxshzzlaa`, region `ap-south-1`).

The migrations add:
- `phantom_devices`
- `phantom_device_locations`
- `phantom_device_telemetry`
- `phantom_device_commands`
- `phantom_audit_logs`
- `phantom_enrollment_tokens`

These tables are additive and do not alter the existing dairy, barber or other application tables. RLS is enabled on every PHANTOM table. Enrollment tokens, telemetry writes, command creation and audit writes are intended to be handled by trusted Edge Functions only. The browser must never contain a Supabase service-role/secret key.

## Recovery capabilities and platform limits

Planned, subject to Android version and owner permissions:
- Consent-based location reporting and history; geofencing after background-location approval where required.
- Battery, charging, network and last-seen heartbeat.
- Public IP observed by the authenticated backend; it may represent carrier NAT, VPN or a shared gateway and is not precise location.
- SIM-state change alerts only where Android/OEM APIs and permissions permit.
- Remote audible alert and in-app recovery message.
- Supported lost/lock workflows; do not abuse Accessibility or Device Admin.
- Screen-sharing request through Android MediaProjection, with visible owner approval, ongoing notification and stop control.

IMEI is not collected by this ordinary app. Android restricts access to non-resettable identifiers for normal apps. Use the app-generated installation ID and optional owner-entered asset tag instead.

## Safety and enrollment

- An invitation URL is only an enrollment invitation; opening it does not grant device access.
- The device owner must install the agent, review the disclosure, authenticate and approve each Android permission.
- No covert camera, microphone, screen capture, message access, password collection or permission bypass.
- Owners must be able to revoke enrollment. Recovery commands must be authorized server-side, signed, expiring and audited.
- A powered-off, offline, reset or uninstalled device cannot be guaranteed to report live location or receive commands.

## Build the Android starter

Open the `android/` folder in Android Studio, sync Gradle, then build/install the `app` module on a device you own or are authorized to administer. This starter is a local preview only; it does not connect to the dashboard yet.

## Next engineering stages

1. Implement Auth-backed one-time enrollment Edge Functions and device key binding.
2. Add Android Keystore identity, authenticated heartbeat, permission inventory and FCM delivery.
3. Connect the dashboard to authenticated Supabase queries and private Realtime channels.
4. Implement location history, geofence evaluation, SIM-state capability checks, alarm and recovery message.
5. Implement safe lost/lock workflows, screen-share consent flow, command expiry/nonces and revocation.
6. Test cross-account isolation, replay resistance, expired tokens and Android-version-specific behavior; rerun Supabase security/performance advisors.
