ALTER TABLE public.phantom_web_commands
  ADD COLUMN IF NOT EXISTS expires_at timestamptz;

UPDATE public.phantom_web_commands
SET expires_at = created_at + interval '5 minutes'
WHERE expires_at IS NULL;

ALTER TABLE public.phantom_web_commands
  ALTER COLUMN expires_at SET DEFAULT (now() + interval '5 minutes'),
  ALTER COLUMN expires_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS phantom_web_commands_expiry_idx
  ON public.phantom_web_commands (enrollment_id, status, expires_at, created_at);
