-- PHANTOM RECOVER: force all writes through the authenticated Edge Function.
-- Preserve RLS policies and all rows; service_role retains its existing privileges.
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

GRANT SELECT ON TABLE
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
TO authenticated;
