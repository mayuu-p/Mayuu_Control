-- PHANTOM RECOVER web-first enrollment. Additive and isolated; no existing app tables are changed.
create table public.phantom_web_invites (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  label text not null,
  token_hash text not null unique,
  expires_at timestamptz not null,
  redeemed_at timestamptz,
  created_at timestamptz not null default now()
);
create table public.phantom_web_enrollments (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  invite_id uuid references public.phantom_web_invites(id) on delete set null,
  label text not null,
  session_hash text not null,
  browser_info jsonb not null default '{}'::jsonb,
  observed_public_ip inet,
  status text not null default 'online' check (status in ('online','offline','revoked')),
  consented_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);
create table public.phantom_web_locations (
  id uuid primary key default gen_random_uuid(),
  enrollment_id uuid not null references public.phantom_web_enrollments(id) on delete cascade,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accuracy_m double precision,
  captured_at timestamptz not null default now()
);
create index phantom_web_invites_owner_idx on public.phantom_web_invites(owner_user_id, created_at desc);
create index phantom_web_enrollments_owner_idx on public.phantom_web_enrollments(owner_user_id, last_seen_at desc);
create index phantom_web_locations_enrollment_time_idx on public.phantom_web_locations(enrollment_id, captured_at desc);
alter table public.phantom_web_invites enable row level security;
alter table public.phantom_web_enrollments enable row level security;
alter table public.phantom_web_locations enable row level security;
create policy "Owners read own web enrollments" on public.phantom_web_enrollments
 for select to authenticated using ((select auth.uid()) = owner_user_id);
create policy "Owners read locations of own web enrollments" on public.phantom_web_locations
 for select to authenticated using (exists (
   select 1 from public.phantom_web_enrollments e
   where e.id = enrollment_id and e.owner_user_id = (select auth.uid())
 ));
revoke all on public.phantom_web_invites from anon, authenticated;
revoke all on public.phantom_web_enrollments from anon, authenticated;
revoke all on public.phantom_web_locations from anon, authenticated;
grant select on public.phantom_web_enrollments, public.phantom_web_locations to authenticated;
