# PHANTOM RECOVER Android

Package: `com.mayuu.phantomrecover` · Version: `0.3.0` · Minimum Android: API 26

## Implemented in this source baseline
- Shield-branded Android 12+ system splash screen and shared PHANTOM logo in the app header.
- One-time owner-issued 64-character enrollment code with explicit consent and an explicit Deny Enrollment audit action.
- Session secret encrypted with Android Keystore AES-GCM. Only ciphertext and IV are stored in app-private preferences.
- User-started visible foreground protection service with notification Stop action and server-confirmed Stop Session / Unenroll.
- 30-second authenticated heartbeat with battery, charging, network, Android and app-version telemetry.
- Authenticated recovery command polling and acknowledgment.
- One-shot location request after runtime permission, with a bounded 20-second timeout.

## Deliberate limitations
- User-started service only; no boot persistence guarantee or promise of 24/7 connectivity.
- Native camera, microphone, MediaProjection, WebRTC, FCM and remote lock are not implemented.
- Device sessions expire after 90 days and are rejected server-side after expiry.
- A screen-share request only displays a notification; it never starts capture.
- No private notifications, messages, calls, contacts, files, keystrokes or hidden app usage are collected.

## Build
JDK 17 and Gradle 8.9:
```bash
gradle --no-daemon :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`.
