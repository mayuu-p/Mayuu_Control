create table if not exists public.phantom_redirect_telemetry (
  id uuid primary key default gen_random_uuid(),
  invite_id uuid not null references public.phantom_web_invites(id) on delete cascade,
  owner_user_id uuid not null,
  visitor_session_hash text not null unique,
  consent_state text not null default 'pending' check (consent_state in ('pending','active','stopped','denied')),
  consented_at timestamptz,
  revoked_at timestamptz,
  expires_at timestamptz not null,
  battery_percent integer check (battery_percent between 0 and 100),
  charging boolean,
  network_type text,
  online boolean,
  latitude double precision check (latitude between -90 and 90),
  longitude double precision check (longitude between -180 and 180),
  accuracy_m double precision check (accuracy_m >= 0 and accuracy_m <= 100000),
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  constraint redirect_location_pair check ((latitude is null and longitude is null) or (latitude is not null and longitude is not null))
);
create index if not exists phantom_redirect_telemetry_owner_invite_updated_idx
  on public.phantom_redirect_telemetry(owner_user_id, invite_id, updated_at desc);
alter table public.phantom_redirect_telemetry enable row level security;
revoke all on table public.phantom_redirect_telemetry from public, anon, authenticated;
