# PHANTOM RECOVER Android agent — starter

This is the first Kotlin Android app scaffold, not a production anti-theft agent.

## Included
- Random app-generated installation ID stored locally.
- Device manufacturer/model and Android version.
- Battery percentage and active network type.
- Optional location permission request initiated by a visible button.
- Last-known location display only after the owner grants location permission.
- Explicit UI disclosure; no telemetry is uploaded.

## Not implemented yet
- Supabase authentication, one-time enrollment, heartbeat or IP observation.
- Continuous/background location, location history or geofencing.
- FCM command delivery, remote alarm, lost-mode UI, or supported lock workflow.
- SIM-change monitoring (Android/OEM restrictions must be assessed).
- Screen sharing (must use Android MediaProjection consent for each session).
- Production key management, signed commands, audit API or revocation.

## Build
Open the `android/` folder in Android Studio and let Gradle sync. Build and install the `app` module on an Android device you own or are authorized to administer. The app currently runs locally and does not connect to the web dashboard.

Before production use, add backend enrollment and device-key binding, test Android-version-specific restrictions, add clear privacy disclosures and retention controls, and verify security rules. Never add service-role keys to this app.
