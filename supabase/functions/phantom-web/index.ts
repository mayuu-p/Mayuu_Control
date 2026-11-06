import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY") ?? "";
const DASHBOARD_ORIGIN = Deno.env.get("DASHBOARD_ORIGIN") ?? "https://mayuu-p.github.io";
const OWNER_EMAIL = "mayur99@gmail.com";
const encoder = new TextEncoder();

class ApiError extends Error {
  status: number;
  code: string;
  constructor(status: number, code: string, message: string) {
    super(message); this.status = status; this.code = code;
  }
}
const json = (body: unknown, status = 200, origin: string | null = null) => new Response(JSON.stringify(body), {
  status,
  headers: {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store, max-age=0",
    "Pragma": "no-cache",
    "Vary": "Origin",
    ...(origin ? {
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-device-session, x-install-id",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Max-Age": "600"
    } : {})
  }
});
const sha256 = async (value: string) => {
  const digest = await crypto.subtle.digest("SHA-256", encoder.encode(value));
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
};
const randomHex = (bytes = 32) => {
  const value = crypto.getRandomValues(new Uint8Array(bytes));
  return Array.from(value, b => b.toString(16).padStart(2, "0")).join("");
};
const asText = (value: unknown, max = 120) => typeof value === "string" ? value.trim().slice(0, max) : "";
const validUuid = (v: unknown) => typeof v === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
const nowIso = () => new Date().toISOString();
const observedIp = (req: Request) => {
  const candidate = req.headers.get("cf-connecting-ip") || req.headers.get("x-real-ip") || req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "";
  return candidate && candidate.length <= 64 && /^[0-9a-fA-F:.]+$/.test(candidate) ? candidate : null;
};
function assertConfigured() {
  if (!SUPABASE_URL || !ANON_KEY || !SERVICE_KEY) throw new ApiError(503, "server_not_configured", "Server configuration is incomplete.");
}
async function rest(table: string, query = "", init: RequestInit = {}) {
  assertConfigured();
  const url = new URL(SUPABASE_URL.replace(/\/$/, "") + "/rest/v1/" + table);
  if (query) new URLSearchParams(query).forEach((v, k) => url.searchParams.append(k, v));
  const headers = new Headers(init.headers || {});
  headers.set("apikey", SERVICE_KEY);
  headers.set("Authorization", "Bearer " + SERVICE_KEY);
  headers.set("Content-Type", "application/json");
  headers.set("Cache-Control", "no-store");
  const response = await fetch(url, { ...init, headers, cache: "no-store" });
  const text = await response.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  if (!response.ok) {
    const msg = typeof data?.message === "string" ? data.message : "Database request failed.";
    throw new ApiError(500, "database_error", msg.slice(0, 220));
  }
  return data;
}
const select = (table: string, params: Record<string, string>) => {
  const q = new URLSearchParams(params).toString();
  return rest(table, q, { method: "GET" });
};
const insert = (table: string, row: unknown) => rest(table, "", {
  method: "POST", headers: { "Prefer": "return=representation" }, body: JSON.stringify(row)
});
const patch = (table: string, params: Record<string, string>, row: unknown) => {
  const q = new URLSearchParams(params).toString();
  return rest(table, q, { method: "PATCH", headers: { "Prefer": "return=representation" }, body: JSON.stringify(row) });
};
async function owner(req: Request) {
  const auth = req.headers.get("authorization") || "";
  if (!auth.startsWith("Bearer ")) throw new ApiError(401, "unauthorized_owner", "Sign in as the owner first.");
  const response = await fetch(SUPABASE_URL.replace(/\/$/, "") + "/auth/v1/user", {
    headers: { "apikey": ANON_KEY, "Authorization": auth, "Cache-Control": "no-store" }
  });
  if (!response.ok) throw new ApiError(401, "unauthorized_owner", "Owner session is invalid or expired.");
  const user = await response.json();
  if (String(user?.email || "").toLowerCase() !== OWNER_EMAIL) throw new ApiError(403, "unauthorized_owner", "This account is not allowlisted as the owner.");
  return { id: user.id as string, email: OWNER_EMAIL };
}
async function deviceSession(req: Request) {
  const raw = req.headers.get("x-device-session") || "";
  if (raw.length < 32 || raw.length > 256) throw new ApiError(401, "invalid_device_session", "Device session is invalid.");
  const hash = await sha256(raw);
  const rows = await select("phantom_devices", { select: "id,owner_user_id,status,revoked_at", session_hash: "eq." + hash, limit: "1" });
  const device = rows?.[0];
  if (!device) throw new ApiError(401, "invalid_device_session", "Device session is invalid.");
  if (device.status === "revoked" || device.revoked_at) throw new ApiError(403, "revoked_device", "This device has been revoked.");
  return device;
}
async function webSession(req: Request) {
  const raw = req.headers.get("x-device-session") || "";
  if (raw.length < 32 || raw.length > 256) throw new ApiError(401, "invalid_web_session", "Browser session is invalid.");
  const hash = await sha256(raw);
  const rows = await select("phantom_web_enrollments", { select: "id,owner_user_id,status,revoked_at", session_hash: "eq." + hash, limit: "1" });
  const enrollment = rows?.[0];
  if (!enrollment) throw new ApiError(401, "invalid_web_session", "Browser session is invalid.");
  if (enrollment.status === "revoked" || enrollment.revoked_at) throw new ApiError(403, "revoked_device", "This browser enrollment has been revoked.");
  return enrollment;
}
async function audit(event_type: string, actor_user_id: string | null, device_id: string | null, event_details: Record<string, unknown> = {}) {
  await insert("phantom_audit_logs", { event_type, actor_user_id, device_id, event_details });
}
function requiredConsent(body: any) {
  if (body?.consent !== true) throw new ApiError(400, "consent_required", "Explicit device-user consent is required.");
}
function validateLocation(body: any) {
  const latitude = Number(body?.latitude), longitude = Number(body?.longitude);
  const accuracy = body?.accuracy_m == null ? null : Number(body.accuracy_m);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
      !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
      (accuracy !== null && (!Number.isFinite(accuracy) || accuracy < 0 || accuracy > 100000))) {
    throw new ApiError(400, "invalid_coordinates", "Coordinates or accuracy are outside valid ranges.");
  }
  const captured = body?.captured_at ? new Date(body.captured_at) : new Date();
  if (!Number.isFinite(captured.getTime()) || captured.getTime() > Date.now() + 120000) throw new ApiError(400, "invalid_timestamp", "Location timestamp is invalid.");
  return { latitude, longitude, accuracy_m: accuracy, captured_at: captured.toISOString() };
}
async function handle(action: string, body: any, req: Request) {
  if (action === "android_code_create") {
    const user = await owner(req);
    const label = asText(body?.device_label, 80);
    if (!label) throw new ApiError(400, "invalid_label", "Enter a device label.");
    const code = randomHex(32), expires_at = new Date(Date.now() + 15 * 60_000).toISOString();
    await insert("phantom_enrollment_tokens", { owner_user_id: user.id, device_label: label, token_hash: await sha256(code), expires_at });
    await audit("android_enrollment_code_created", user.id, null, { label });
    return { code, expires_at, message: "Code created. It can be used once within 15 minutes." };
  }
  if (action === "browser_invite_create") {
    const user = await owner(req);
    const label = asText(body?.label, 80);
    if (!label) throw new ApiError(400, "invalid_label", "Enter a browser device label.");
    const token = randomHex(32), expires_at = new Date(Date.now() + 30 * 60_000).toISOString();
    await insert("phantom_web_invites", { owner_user_id: user.id, label, token_hash: await sha256(token), expires_at });
    await audit("browser_invite_created", user.id, null, { label });
    return { token, expires_at, enrollment_url: DASHBOARD_ORIGIN.replace(/\/$/, "") + "/Mayuu_Control/enroll.html?invite=" + token };
  }
  if (action === "device_redeem") {
    requiredConsent(body);
    const code = asText(body?.code, 64).toLowerCase(), install_id = asText(body?.install_id, 36);
    if (!/^[0-9a-f]{64}$/.test(code)) throw new ApiError(400, "invalid_code", "Invalid code.");
    if (!validUuid(install_id)) throw new ApiError(400, "invalid_install_id", "Installation ID is invalid.");
    const tokens = await select("phantom_enrollment_tokens", { select: "id,owner_user_id,device_label,expires_at,redeemed_at", token_hash: "eq." + await sha256(code), limit: "1" });
    const token = tokens?.[0];
    if (!token) throw new ApiError(404, "invalid_code", "Invalid code.");
    if (token.redeemed_at) throw new ApiError(409, "already_used", "Already-used code.");
    if (new Date(token.expires_at).getTime() <= Date.now()) throw new ApiError(410, "expired_code", "Expired code.");
    const claimed = await patch("phantom_enrollment_tokens", { id: "eq." + token.id, redeemed_at: "is.null", expires_at: "gt." + nowIso() }, { redeemed_at: nowIso() });
    if (!claimed?.length) throw new ApiError(409, "already_used", "This code has already been used or expired.");
    const session_secret = randomHex(32);
    const rows = await insert("phantom_devices", {
      owner_user_id: token.owner_user_id, install_id, device_label: asText(body?.device_label, 80) || token.device_label,
      manufacturer: asText(body?.manufacturer, 80) || null, model: asText(body?.model, 100) || null,
      android_version: asText(body?.android_version, 40) || null, app_version: asText(body?.app_version, 40) || null,
      status: "online", session_hash: await sha256(session_secret), consented_at: nowIso(), last_seen_at: nowIso()
    });
    const device = rows?.[0];
    if (!device) throw new ApiError(500, "enrollment_failed", "Device enrollment could not be completed.");
    await audit("device_enrolled", token.owner_user_id, device.id, { install_id });
    await audit("consent_accepted", token.owner_user_id, device.id, { platform: "android" });
    return { device_id: device.id, session_secret, enrolled_at: device.consented_at };
  }
  if (action === "device_heartbeat") {
    const device = await deviceSession(req);
    const battery = body?.battery_percent == null ? null : Number(body.battery_percent);
    if (battery !== null && (!Number.isInteger(battery) || battery < 0 || battery > 100)) throw new ApiError(400, "invalid_telemetry", "Battery percentage must be between 0 and 100.");
    const network = asText(body?.network_type, 40) || null;
    const ip = observedIp(req);
    const patchRows = await patch("phantom_devices", { id: "eq." + device.id, status: "neq.revoked" }, {
      last_seen_at: nowIso(), status: "online", battery_percent: battery, network_type: network,
      observed_public_ip: ip, manufacturer: asText(body?.manufacturer, 80) || undefined,
      model: asText(body?.model, 100) || undefined, android_version: asText(body?.android_version, 40) || undefined,
      app_version: asText(body?.app_version, 40) || undefined, updated_at: nowIso()
    });
    if (!patchRows?.length) throw new ApiError(403, "revoked_device", "Device is no longer active.");
    await insert("phantom_device_telemetry", { device_id: device.id, battery_percent: battery, charging: typeof body?.charging === "boolean" ? body.charging : null, network_type: network, observed_public_ip: ip });
    return { ok: true, server_time: nowIso(), next_heartbeat_seconds: 30 };
  }
  if (action === "device_location") {
    const device = await deviceSession(req), location = validateLocation(body);
    await insert("phantom_device_locations", { device_id: device.id, ...location });
    await patch("phantom_devices", { id: "eq." + device.id }, { last_location_at: nowIso(), updated_at: nowIso() });
    await audit("location_shared", null, device.id, { accuracy_m: location.accuracy_m, captured_at: location.captured_at });
    return { ok: true, saved_at: nowIso() };
  }
  if (action === "device_poll") {
    const device = await deviceSession(req);
    const commands = await select("phantom_device_commands", { select: "id,command_type,payload,nonce,expires_at,created_at", device_id: "eq." + device.id, status: "eq.pending", expires_at: "gt." + nowIso(), order: "created_at.asc", limit: "20" });
    for (const c of commands || []) await patch("phantom_device_commands", { id: "eq." + c.id, device_id: "eq." + device.id, status: "eq.pending" }, { status: "delivered" });
    return { commands: commands || [], server_time: nowIso() };
  }
  if (action === "device_ack") {
    const device = await deviceSession(req), id = body?.command_id, next = asText(body?.status, 20);
    if (!validUuid(id) || !["acknowledged", "completed", "failed"].includes(next)) throw new ApiError(400, "invalid_ack", "Command acknowledgment is invalid.");
    const update: Record<string, unknown> = { status: next };
    if (next === "acknowledged") update.acknowledged_at = nowIso();
    if (next === "completed") { update.acknowledged_at = nowIso(); update.completed_at = nowIso(); }
    const rows = await patch("phantom_device_commands", { id: "eq." + id, device_id: "eq." + device.id, expires_at: "gt." + nowIso(), status: "in.(pending,delivered,acknowledged)" }, update);
    if (!rows?.length) throw new ApiError(404, "command_not_found", "Command not found, expired or already closed.");
    await audit("recovery_command_" + next, null, device.id, { command_id: id });
    return { ok: true, status: next };
  }
  if (action === "device_command_create") {
    const user = await owner(req), device_id = body?.device_id, type = asText(body?.command_type, 40);
    if (!validUuid(device_id)) throw new ApiError(400, "invalid_device", "Select one valid device.");
    if (!["ring", "lost_mode", "recovery_message", "screen_share_request"].includes(type)) throw new ApiError(400, "unsupported_command", "This recovery command is not supported.");
    const found = await select("phantom_devices", { select: "id,status,revoked_at", id: "eq." + device_id, owner_user_id: "eq." + user.id, limit: "1" });
    if (!found?.length || found[0].status === "revoked" || found[0].revoked_at) throw new ApiError(404, "device_not_found", "Device not found or access revoked.");
    const payload: Record<string, unknown> = {};
    if (type === "recovery_message") {
      const message = asText(body?.message, 300); if (!message) throw new ApiError(400, "invalid_message", "Enter a recovery message."); payload.message = message;
    }
    if (type === "lost_mode") payload.enabled = true;
    const expires_at = new Date(Date.now() + 5 * 60_000).toISOString();
    const rows = await insert("phantom_device_commands", { device_id, requested_by: user.id, command_type: type, payload, expires_at });
    await audit("recovery_command_sent", user.id, device_id, { command_type: type, command_id: rows?.[0]?.id });
    return { command: rows?.[0] || null };
  }
  if (action === "device_revoke") {
    const user = await owner(req), device_id = body?.device_id;
    if (!validUuid(device_id)) throw new ApiError(400, "invalid_device", "Select one valid device.");
    const rows = await patch("phantom_devices", { id: "eq." + device_id, owner_user_id: "eq." + user.id, status: "neq.revoked" }, { status: "revoked", revoked_at: nowIso(), session_hash: null, updated_at: nowIso() });
    if (!rows?.length) throw new ApiError(404, "device_not_found", "Device not found or already revoked.");
    await audit("device_revoked", user.id, device_id, {});
    return { ok: true, revoked_at: nowIso() };
  }
  if (action === "web_consent_deny") {
    const tokenRaw = asText(body?.invite, 64).toLowerCase();
    if (!/^[0-9a-f]{64}$/.test(tokenRaw)) throw new ApiError(400, "invalid_invite", "Invalid browser invitation.");
    const invites = await select("phantom_web_invites", { select: "id,owner_user_id,label,expires_at,redeemed_at", token_hash: "eq." + await sha256(tokenRaw), limit: "1" });
    const invite = invites?.[0];
    if (!invite) throw new ApiError(404, "invalid_invite", "Invalid browser invitation.");
    await audit("consent_denied", null, null, { platform: "browser", invite_id: invite.id, label: invite.label });
    return { ok: true, denied: true };
  }
  if (action === "web_redeem") {
    requiredConsent(body);
    const tokenRaw = asText(body?.invite, 64).toLowerCase();
    if (!/^[0-9a-f]{64}$/.test(tokenRaw)) throw new ApiError(400, "invalid_invite", "Invalid browser invitation.");
    const invites = await select("phantom_web_invites", { select: "id,owner_user_id,label,expires_at,redeemed_at", token_hash: "eq." + await sha256(tokenRaw), limit: "1" });
    const invite = invites?.[0];
    if (!invite) throw new ApiError(404, "invalid_invite", "Invalid browser invitation.");
    if (invite.redeemed_at) throw new ApiError(409, "already_used", "This browser invitation has already been used.");
    if (new Date(invite.expires_at).getTime() <= Date.now()) throw new ApiError(410, "expired_invite", "This browser invitation has expired.");
    const claimed = await patch("phantom_web_invites", { id: "eq." + invite.id, redeemed_at: "is.null", expires_at: "gt." + nowIso() }, { redeemed_at: nowIso() });
    if (!claimed?.length) throw new ApiError(409, "already_used", "This browser invitation has already been used or expired.");
    const session_secret = randomHex(32);
    const rows = await insert("phantom_web_enrollments", {
      owner_user_id: invite.owner_user_id, invite_id: invite.id, label: asText(body?.label, 80) || invite.label,
      session_hash: await sha256(session_secret), browser_info: {
        userAgent: asText(body?.browser_info?.userAgent, 300),
        language: asText(body?.browser_info?.language, 40)
      }, observed_public_ip: observedIp(req), status: "online", consented_at: nowIso(), last_seen_at: nowIso()
    });
    const enrollment = rows?.[0];
    if (!enrollment) throw new ApiError(500, "enrollment_failed", "Browser enrollment could not be completed.");
    await audit("browser_device_enrolled", invite.owner_user_id, null, { enrollment_id: enrollment.id, label: enrollment.label });
    await audit("consent_accepted", null, null, { platform: "browser", enrollment_id: enrollment.id });
    return { enrollment_id: enrollment.id, session_secret, owner_display: OWNER_EMAIL, consented_at: enrollment.consented_at };
  }
  if (action === "web_heartbeat") {
    const enrollment = await webSession(req);
    await patch("phantom_web_enrollments", { id: "eq." + enrollment.id, status: "neq.revoked" }, { last_seen_at: nowIso(), observed_public_ip: observedIp(req), status: "online" });
    return { ok: true, server_time: nowIso() };
  }
  if (action === "web_location") {
    const enrollment = await webSession(req), location = validateLocation(body);
    await insert("phantom_web_locations", { enrollment_id: enrollment.id, ...location });
    await audit("browser_location_shared", null, null, { enrollment_id: enrollment.id, accuracy_m: location.accuracy_m });
    return { ok: true, saved_at: nowIso() };
  }
  if (action === "web_command_create") {
    const user = await owner(req), enrollment_id = body?.enrollment_id, command = asText(body?.command, 40);
    if (!validUuid(enrollment_id) || !["ring", "lost_mode", "clear_lost_mode"].includes(command)) throw new ApiError(400, "invalid_command", "Browser command is invalid.");
    const found = await select("phantom_web_enrollments", { select: "id,status,revoked_at", id: "eq." + enrollment_id, owner_user_id: "eq." + user.id, limit: "1" });
    if (!found?.length || found[0].status === "revoked") throw new ApiError(404, "enrollment_not_found", "Browser enrollment not found or revoked.");
    const expires_at = new Date(Date.now() + 5 * 60_000).toISOString();
    const rows = await insert("phantom_web_commands", { owner_user_id: user.id, enrollment_id, command, payload: {}, status: "queued", expires_at });
    await audit("browser_recovery_command_sent", user.id, null, { enrollment_id, command, command_id: rows?.[0]?.id });
    return { command: rows?.[0] || null };
  }
  if (action === "web_command_poll") {
    const enrollment = await webSession(req);
    await patch("phantom_web_commands", { enrollment_id: "eq." + enrollment.id, status: "eq.queued", expires_at: "lte." + nowIso() }, { status: "expired" });
    const rows = await select("phantom_web_commands", { select: "id,command,payload,status,created_at,expires_at", enrollment_id: "eq." + enrollment.id, owner_user_id: "eq." + enrollment.owner_user_id, status: "eq.queued", expires_at: "gt." + nowIso(), order: "created_at.asc", limit: "20" });
    for (const c of rows || []) await patch("phantom_web_commands", { id: "eq." + c.id, enrollment_id: "eq." + enrollment.id, status: "eq.queued" }, { status: "delivered", delivered_at: nowIso() });
    return { commands: rows || [], server_time: nowIso() };
  }
  if (action === "web_command_ack") {
    const enrollment = await webSession(req), id = body?.command_id, next = asText(body?.status, 20);
    if (!validUuid(id) || !["acknowledged", "failed"].includes(next)) throw new ApiError(400, "invalid_ack", "Command acknowledgment is invalid.");
    const rows = await patch("phantom_web_commands", { id: "eq." + id, enrollment_id: "eq." + enrollment.id, owner_user_id: "eq." + enrollment.owner_user_id, status: "eq.delivered", expires_at: "gt." + nowIso() }, { status: next, acknowledged_at: nowIso() });
    if (!rows?.length) throw new ApiError(404, "command_not_found", "Command not found or already closed.");
    await audit("browser_recovery_command_" + next, null, null, { enrollment_id: enrollment.id, command_id: id });
    return { ok: true, status: next };
  }
  if (action === "web_session_stop") {
    const enrollment = await webSession(req);
    const rows = await patch("phantom_web_enrollments", { id: "eq." + enrollment.id, status: "neq.revoked" }, { status: "revoked", revoked_at: nowIso() });
    if (!rows?.length) throw new ApiError(409, "session_already_stopped", "This browser session has already ended.");
    await audit("browser_session_stopped", null, null, { enrollment_id: enrollment.id });
    return { ok: true, stopped_at: nowIso() };
  }
  if (action === "web_revoke") {
    const user = await owner(req), enrollment_id = body?.enrollment_id;
    if (!validUuid(enrollment_id)) throw new ApiError(400, "invalid_enrollment", "Select a valid browser enrollment.");
    const rows = await patch("phantom_web_enrollments", { id: "eq." + enrollment_id, owner_user_id: "eq." + user.id, status: "neq.revoked" }, { status: "revoked", revoked_at: nowIso() });
    if (!rows?.length) throw new ApiError(404, "enrollment_not_found", "Browser enrollment not found or already revoked.");
    await audit("browser_device_revoked", user.id, null, { enrollment_id });
    return { ok: true };
  }
  throw new ApiError(400, "unknown_action", "Unknown PHANTOM RECOVER action.");
}
Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const allowedOrigin = origin && (origin === DASHBOARD_ORIGIN || origin === DASHBOARD_ORIGIN.replace(/\/$/, "") + "/Mayuu_Control" || origin === DASHBOARD_ORIGIN.replace(/\/$/, "") + "/Mayuu_Control/") ? origin : null;
  if (origin && !allowedOrigin) return json({ error: "origin_not_allowed" }, 403);
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: allowedOrigin ? {
    "Access-Control-Allow-Origin": allowedOrigin,
    "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-device-session, x-install-id",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Max-Age": "600",
    "Vary": "Origin"
  } : { "Vary": "Origin" } });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405, allowedOrigin);
  let requestAction = "unknown";
  try {
    assertConfigured();
    const rawBody = await req.text();
    if (encoder.encode(rawBody).byteLength > 16_384) throw new ApiError(413, "body_too_large", "Request body is too large.");
    let body: any;
    try { body = JSON.parse(rawBody); } catch { throw new ApiError(400, "invalid_json", "Request body must be valid JSON."); }
    if (!body || typeof body !== "object" || Array.isArray(body)) throw new ApiError(400, "invalid_body", "Request body must be an object.");
    const action = asText(body.action, 50);
    requestAction = action || "unknown";
    const result = await handle(action, body, req);
    return json({ ok: true, ...result }, 200, allowedOrigin);
  } catch (error) {
    const e = error instanceof ApiError ? error : new ApiError(500, "internal_error", "The request could not be completed.");
    if ((e.status === 401 || e.status === 403) && SUPABASE_URL && SERVICE_KEY) {
      try { await audit("authentication_failure", null, null, { action: requestAction, reason: e.code }); } catch { /* do not expose secrets or mask the original error */ }
    }
    return json({ ok: false, error: e.code, message: e.message }, e.status, allowedOrigin);
  }
});
