-- PHANTOM RECOVER: owner-scoped read access and anonymous-role hardening.
-- Existing tables and rows are preserved. All device/browser writes remain server-mediated.
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
FROM anon;

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

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='phantom_enrollment_tokens' AND policyname='Owners read own Android enrollment tokens') THEN
    CREATE POLICY "Owners read own Android enrollment tokens"
      ON public.phantom_enrollment_tokens FOR SELECT TO authenticated
      USING ((SELECT auth.uid()) = owner_user_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='phantom_web_invites' AND policyname='Owners read own browser invites') THEN
    CREATE POLICY "Owners read own browser invites"
      ON public.phantom_web_invites FOR SELECT TO authenticated
      USING ((SELECT auth.uid()) = owner_user_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='phantom_redirect_clicks' AND policyname='Owners read own redirect analytics') THEN
    CREATE POLICY "Owners read own redirect analytics"
      ON public.phantom_redirect_clicks FOR SELECT TO authenticated
      USING ((SELECT auth.uid()) = owner_user_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='phantom_web_commands' AND policyname='Owners read own browser commands') THEN
    CREATE POLICY "Owners read own browser commands"
      ON public.phantom_web_commands FOR SELECT TO authenticated
      USING ((SELECT auth.uid()) = owner_user_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='phantom_web_signals' AND policyname='Owners read signals for own browser enrollments') THEN
    CREATE POLICY "Owners read signals for own browser enrollments"
      ON public.phantom_web_signals FOR SELECT TO authenticated
      USING (EXISTS (
        SELECT 1 FROM public.phantom_web_enrollments e
        WHERE e.id = phantom_web_signals.enrollment_id
          AND e.owner_user_id = (SELECT auth.uid())
      ));
  END IF;
END $$;
