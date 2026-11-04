alter table public.phantom_devices
  add column if not exists session_hash text,
  add column if not exists consented_at timestamptz,
  add column if not exists last_location_at timestamptz;

create index if not exists phantom_devices_owner_status_idx
  on public.phantom_devices(owner_user_id, status);
create index if not exists phantom_device_commands_pending_idx
  on public.phantom_device_commands(device_id, status, created_at);
create index if not exists phantom_device_locations_latest_idx
  on public.phantom_device_locations(device_id, captured_at desc);
create index if not exists phantom_device_telemetry_latest_idx
  on public.phantom_device_telemetry(device_id, recorded_at desc);
