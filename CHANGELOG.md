# Changelog

## 2026-11-06
- Recreated the owner dashboard foundation in `index.html` after the repository was intentionally emptied.
- Added Supabase Auth owner sign-in for the configured owner email.
- Added live owner-scoped reads for device, browser enrollment, location, command, audit, enrollment-token and redirect records.
- Derived online/offline state from the server heartbeat timestamp with a 90-second timeout.
- Added explicit capability states and disabled claims for unverified or unavailable features.
- Added additive RLS hardening migration for owner-scoped PHANTOM reads and revoked anonymous table access.
- Confirmed the existing PHANTOM Edge Function currently returns HTTP 410; API actions remain disabled pending restoration.
