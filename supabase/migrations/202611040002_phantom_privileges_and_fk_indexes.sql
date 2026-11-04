-- Tighten API grants for the PHANTOM RECOVER tables and cover FK indexes.
revoke all on public.phantom_devices from anon;
revoke all on public.phantom_device_locations from anon, authenticated;
revoke all on public.phantom_device_telemetry from anon, authenticated;
revoke all on public.phantom_device_commands from anon, authenticated;
revoke all on public.phantom_audit_logs from anon, authenticated;
revoke all on public.phantom_enrollment_tokens from anon, authenticated;

grant select, update on public.phantom_devices to authenticated;
grant select on public.phantom_device_locations, public.phantom_device_telemetry,
  public.phantom_device_commands, public.phantom_audit_logs to authenticated;

create index phantom_commands_requested_by_idx on public.phantom_device_commands(requested_by);
create index phantom_audit_actor_user_idx on public.phantom_audit_logs(actor_user_id);
