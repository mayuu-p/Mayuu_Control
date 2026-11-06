create policy "deny direct client access to redirect visitor telemetry"
on public.phantom_redirect_telemetry
for all
to anon, authenticated
using (false)
with check (false);
