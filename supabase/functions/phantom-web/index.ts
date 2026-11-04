import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY")!;
const ALLOWED_ORIGIN = Deno.env.get("DASHBOARD_ORIGIN") ?? "https://mayuu-p.github.io";
const cors = {
  "Access-Control-Allow-Origin": ALLOWED_ORIGIN,
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};
const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false, autoRefreshToken: false } });
const authClient = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false, autoRefreshToken: false } });

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
}
async function hash(value: string) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map(x => x.toString(16).padStart(2, "0")).join("");
}
function randomToken(bytes = 32) {
  const data = crypto.getRandomValues(new Uint8Array(bytes));
  return [...data].map(x => x.toString(16).padStart(2, "0")).join("");
}
function clientIp(req: Request) {
  const forwarded = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim();
  const candidate = forwarded || req.headers.get("x-real-ip") || "";
  return candidate.length <= 64 && /^[0-9a-fA-F:.]+$/.test(candidate) ? candidate : null;
}
async function ownerFrom(req: Request) {
  const header = req.headers.get("authorization") || "";
  if (!header.startsWith("Bearer ")) return null;
  const { data, error } = await authClient.auth.getUser(header.slice(7));
  if (error || !data.user) return null;
  return data.user;
}
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "POST required" }, 405);
  const origin = req.headers.get("origin");
  if (origin && origin !== ALLOWED_ORIGIN) return json({ error: "Origin not allowed" }, 403);
  try {
    const body = await req.json();
    const action = String(body.action || "");

    if (action === "redeem") {
      const raw = String(body.token || "");
      const consent = body.consent === true;
      if (!/^[a-f0-9]{64}$/.test(raw) || !consent) return json({ error: "Valid invitation and explicit consent are required." }, 400);
      const tokenHash = await hash(raw);
      const { data: invite, error: inviteError } = await admin.from("phantom_web_invites")
        .select("id,owner_user_id,label,expires_at,redeemed_at").eq("token_hash", tokenHash).maybeSingle();
      if (inviteError || !invite || invite.redeemed_at || new Date(invite.expires_at).getTime() < Date.now()) {
        return json({ error: "Invitation is invalid, expired, or already used." }, 410);
      }
      const sessionSecret = randomToken();
      const info = body.browserInfo && typeof body.browserInfo === "object" ? body.browserInfo : {};
      const ip = clientIp(req);
      const redeemedAt = new Date().toISOString();
      const { data: consumed, error: redeemError } = await admin.from("phantom_web_invites").update({ redeemed_at: redeemedAt })
        .eq("id", invite.id).is("redeemed_at", null).select("id").maybeSingle();
      if (redeemError) throw redeemError;
      if (!consumed) return json({ error: "Invitation has already been used." }, 410);
      const { data: enrollment, error: insertError } = await admin.from("phantom_web_enrollments").insert({
        owner_user_id: invite.owner_user_id, invite_id: invite.id, label: invite.label,
        session_hash: await hash(sessionSecret), browser_info: info, observed_public_ip: ip,
        status: "online", consented_at: redeemedAt, last_seen_at: redeemedAt
      }).select("id,label").single();
      if (insertError) throw insertError;
      return json({ enrollmentId: enrollment.id, sessionSecret, label: enrollment.label });
    }

    if (action === "heartbeat" || action === "location") {
      const enrollmentId = String(body.enrollmentId || "");
      const secret = String(body.sessionSecret || "");
      if (!/^[0-9a-f-]{36}$/i.test(enrollmentId) || !/^[a-f0-9]{64}$/.test(secret)) return json({ error: "Invalid enrollment session." }, 401);
      const { data: enrollment, error } = await admin.from("phantom_web_enrollments")
        .select("id,session_hash,status").eq("id", enrollmentId).maybeSingle();
      if (error || !enrollment || enrollment.status === "revoked" || enrollment.session_hash !== await hash(secret)) return json({ error: "Session revoked or invalid." }, 401);
      const now = new Date().toISOString();
      if (action === "heartbeat") {
        const info = body.browserInfo && typeof body.browserInfo === "object" ? body.browserInfo : {};
        const ip = clientIp(req);
        const update: Record<string, unknown> = { last_seen_at: now, status: "online", browser_info: info };
        if (ip) update.observed_public_ip = ip;
        const { error: updateError } = await admin.from("phantom_web_enrollments").update(update).eq("id", enrollmentId);
        if (updateError) throw updateError;
        return json({ ok: true, serverTime: now, ipObserved: !!ip });
      }
      const lat = Number(body.latitude), lng = Number(body.longitude), accuracy = Number(body.accuracy);
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) return json({ error: "Invalid coordinates." }, 400);
      const { error: locationError } = await admin.from("phantom_web_locations").insert({
        enrollment_id: enrollmentId, latitude: lat, longitude: lng,
        accuracy_m: Number.isFinite(accuracy) && accuracy >= 0 ? accuracy : null, captured_at: now
      });
      if (locationError) throw locationError;
      await admin.from("phantom_web_enrollments").update({ last_seen_at: now, status: "online" }).eq("id", enrollmentId);
      return json({ ok: true, capturedAt: now });
    }

    const owner = await ownerFrom(req);
    if (!owner) return json({ error: "Sign in required." }, 401);

    if (action === "create_invite") {
      const label = String(body.label || "My device").trim().slice(0, 80);
      if (!label) return json({ error: "Enter a device label." }, 400);
      const token = randomToken();
      const expiresAt = new Date(Date.now() + 30 * 60 * 1000).toISOString();
      const { data, error } = await admin.from("phantom_web_invites").insert({
        owner_user_id: owner.id, label, token_hash: await hash(token), expires_at: expiresAt
      }).select("id,expires_at").single();
      if (error) throw error;
      await admin.from("phantom_audit_logs").insert({
        actor_user_id: owner.id, event_type: "web_invite_created",
        event_details: { invite_id: data.id, label, expires_at: expiresAt }
      });
      return json({ token, inviteId: data.id, expiresAt });
    }

    if (action === "revoke") {
      const id = String(body.enrollmentId || "");
      const { data, error } = await admin.from("phantom_web_enrollments").update({
        status: "revoked", revoked_at: new Date().toISOString(), session_hash: randomToken()
      }).eq("id", id).eq("owner_user_id", owner.id).select("id").maybeSingle();
      if (error) throw error;
      if (!data) return json({ error: "Enrollment not found." }, 404);
      await admin.from("phantom_audit_logs").insert({
        actor_user_id: owner.id, event_type: "web_enrollment_revoked",
        event_details: { enrollment_id: id }
      });
      return json({ ok: true });
    }

    return json({ error: "Unknown action." }, 400);
  } catch (error) {
    console.error("phantom-web-api", error);
    return json({ error: "Server operation failed. Check Edge Function logs for details." }, 500);
  }
});
