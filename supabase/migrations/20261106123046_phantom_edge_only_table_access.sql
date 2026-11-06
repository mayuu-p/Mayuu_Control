-- PHANTOM RECOVER: no direct PostgREST/GraphQL table access for browser users.
-- Dashboard reads and all writes are now mediated by the owner/device-authenticated Edge Function.
-- RLS policies remain enabled as defense in depth; no rows or tables are deleted.
REVOKE ALL ON TABLE
  public.phantom_devices,
  public.phantom_device_locations,
  public.phantom_device_telemetry,
  public.phantom_device_commands,
  public.phantom_audit_logs,
  public.phantom_enrollment_tokens,
  public.phantom_web_invites,
  public.phantom_web_enrollments,
  public.phantom_web_locations,
  public.phantom_web_commands,
  public.phantom_web_signals,
  public.phantom_redirect_clicks
FROM PUBLIC, anon, authenticated;
