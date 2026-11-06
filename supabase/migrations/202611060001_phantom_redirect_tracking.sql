-- Tracked redirect links and click analytics for PHANTOM RECOVER
alter table public.phantom_web_invites add column if not exists destination_url text;
create table if not exists public.phantom_redirect_clicks (
  id uuid primary key default gen_random_uuid(),
  invite_id uuid not null references public.phantom_web_invites(id) on delete cascade,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  label text not null,
  destination_url text not null,
  clicked_at timestamptz not null default now(),
  observed_public_ip text,
  browser_info jsonb not null default '{}'::jsonb,
  ip_location jsonb not null default '{}'::jsonb
);
create index if not exists phantom_redirect_clicks_owner_clicked_idx on public.phantom_redirect_clicks(owner_user_id, clicked_at desc);
alter table public.phantom_redirect_clicks enable row level security;
