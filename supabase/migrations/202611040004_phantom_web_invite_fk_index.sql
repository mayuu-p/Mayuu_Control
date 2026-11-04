-- Cover the optional invite foreign key for efficient joins/deletes.
create index phantom_web_enrollments_invite_idx on public.phantom_web_enrollments(invite_id);
