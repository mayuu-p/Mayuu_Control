-- PHANTOM RECOVER isolated MVP schema. Additive only; existing application tables are untouched.
create table public.phantom_devices (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  install_id uuid not null,
  device_public_key text,
  device_label text not null,
  manufacturer text,
  model text,
  android_version text,
  app_version text,
  status text not null default 'pending' check (status in ('pending','online','offline','revoked')),
  battery_percent smallint check (battery_percent between 0 and 100),
  network_type text,
  observed_public_ip inet,
  permission_state jsonb not null default '{}'::jsonb,
  last_seen_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (owner_user_id, install_id)
);

create table public.phantom_device_locations (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.phantom_devices(id) on delete cascade,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accuracy_m double precision,
  captured_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table public.phantom_device_telemetry (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.phantom_devices(id) on delete cascade,
  battery_percent smallint check (battery_percent between 0 and 100),
  charging boolean,
  network_type text,
  observed_public_ip inet,
  sim_state text,
  recorded_at timestamptz not null default now()
);

create table public.phantom_device_commands (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.phantom_devices(id) on delete cascade,
  requested_by uuid not null references auth.users(id),
  command_type text not null check (command_type in ('ring','lost_mode','recovery_message','lock_request','screen_share_request')),
  payload jsonb not null default '{}'::jsonb,
  status text not null default 'pending' check (status in ('pending','delivered','acknowledged','completed','failed','expired','cancelled')),
  nonce uuid not null unique default gen_random_uuid(),
  expires_at timestamptz not null,
  acknowledged_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now()
);

create table public.phantom_audit_logs (
  id uuid primary key default gen_random_uuid(),
  device_id uuid references public.phantom_devices(id) on delete set null,
  actor_user_id uuid references auth.users(id) on delete set null,
  event_type text not null,
  event_details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Raw invitation tokens are never readable by browser clients. Edge Functions store token hashes only.
create table public.phantom_enrollment_tokens (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  device_label text not null,
  token_hash text not null unique,
  expires_at timestamptz not null,
  redeemed_at timestamptz,
  created_at timestamptz not null default now()
);

create index phantom_devices_owner_idx on public.phantom_devices(owner_user_id);
create index phantom_locations_device_time_idx on public.phantom_device_locations(device_id, captured_at desc);
create index phantom_telemetry_device_time_idx on public.phantom_device_telemetry(device_id, recorded_at desc);
create index phantom_commands_device_status_idx on public.phantom_device_commands(device_id, status, created_at desc);
create index phantom_audit_device_time_idx on public.phantom_audit_logs(device_id, created_at desc);
create index phantom_tokens_owner_expiry_idx on public.phantom_enrollment_tokens(owner_user_id, expires_at);

alter table public.phantom_devices enable row level security;
alter table public.phantom_device_locations enable row level security;
alter table public.phantom_device_telemetry enable row level security;
alter table public.phantom_device_commands enable row level security;
alter table public.phantom_audit_logs enable row level security;
alter table public.phantom_enrollment_tokens enable row level security;

create policy "Owners read their phantom devices" on public.phantom_devices
  for select to authenticated using ((select auth.uid()) = owner_user_id);
create policy "Owners update their phantom devices" on public.phantom_devices
  for update to authenticated using ((select auth.uid()) = owner_user_id)
  with check ((select auth.uid()) = owner_user_id);

create policy "Owners read their device locations" on public.phantom_device_locations
  for select to authenticated using (exists (
    select 1 from public.phantom_devices d
    where d.id = device_id and d.owner_user_id = (select auth.uid())
  ));
create policy "Owners read their device telemetry" on public.phantom_device_telemetry
  for select to authenticated using (exists (
    select 1 from public.phantom_devices d
    where d.id = device_id and d.owner_user_id = (select auth.uid())
  ));
create policy "Owners read their device commands" on public.phantom_device_commands
  for select to authenticated using (exists (
    select 1 from public.phantom_devices d
    where d.id = device_id and d.owner_user_id = (select auth.uid())
  ));
create policy "Owners read their audit logs" on public.phantom_audit_logs
  for select to authenticated using (
    actor_user_id = (select auth.uid()) or exists (
      select 1 from public.phantom_devices d
      where d.id = device_id and d.owner_user_id = (select auth.uid())
    )
  );

-- Only trusted server-side Edge Functions may create/redeem invitations or write device telemetry/commands/audit events.
revoke all on public.phantom_enrollment_tokens from anon, authenticated;
revoke insert, delete on public.phantom_devices from anon, authenticated;
revoke all on public.phantom_device_locations from anon, authenticated;
revoke all on public.phantom_device_telemetry from anon, authenticated;
revoke all on public.phantom_device_commands from anon, authenticated;
revoke insert, update, delete on public.phantom_audit_logs from anon, authenticated;
grant select, update on public.phantom_devices to authenticated;
grant select on public.phantom_device_locations, public.phantom_device_telemetry,
  public.phantom_device_commands, public.phantom_audit_logs to authenticated;
