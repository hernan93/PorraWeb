import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const ALLOWED_KEYS = [
  "groups_form_status",
  "knockouts_form_status",
  "participation_price_eur",
  "group_deadline",
  "bizum_phone",
  "admin_email",
] as const;

type AllowedKey = (typeof ALLOWED_KEYS)[number];

interface SettingsPayload {
  settings?: Array<{ key: string; value: string }>;
  groups_form_status?: string;
  knockouts_form_status?: string;
  participation_price_eur?: string;
  group_deadline?: string;
  bizum_phone?: string;
  admin_email?: string;
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(
      JSON.stringify({ ok: false, error: "Method not allowed" }),
      { status: 405, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = supabaseSecretKey();

    if (!supabaseUrl || !serviceRoleKey) {
      return new Response(
        JSON.stringify({ ok: false, error: "Missing environment variables" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey);

    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(
        JSON.stringify({ ok: false, error: "Missing authorization header" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const token = authHeader.replace("Bearer ", "");
    const { data: { user }, error: authError } = await supabase.auth.getUser(token);

    if (authError || !user) {
      return new Response(
        JSON.stringify({ ok: false, error: "Invalid or expired token" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const { data: adminRow, error: adminCheckError } = await supabase
      .from("admin_users")
      .select("user_id")
      .eq("user_id", user.id)
      .maybeSingle();

    if (adminCheckError || !adminRow) {
      return new Response(
        JSON.stringify({ ok: false, error: "Not an admin" }),
        { status: 403, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const body: SettingsPayload = await req.json();

    const updates: Array<{ key: AllowedKey; value: string }> = [];

    if (Array.isArray(body.settings)) {
      for (const s of body.settings) {
        if ((ALLOWED_KEYS as readonly string[]).includes(s.key)) {
          updates.push({ key: s.key as AllowedKey, value: s.value });
        }
      }
    }

    const individualFields: Array<AllowedKey> = [
      "groups_form_status",
      "knockouts_form_status",
      "participation_price_eur",
      "group_deadline",
      "bizum_phone",
      "admin_email",
    ];

    for (const field of individualFields) {
      if (body[field] !== undefined) {
        updates.push({ key: field, value: body[field] as string });
      }
    }

    if (updates.length === 0) {
      return new Response(
        JSON.stringify({ ok: false, error: "No valid settings provided" }),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } },
      );
    }

    const { error: upsertError } = await supabase
      .from("app_settings")
      .upsert(updates, { onConflict: "key" });

    if (upsertError) {
      throw upsertError;
    }

    return new Response(
      JSON.stringify({ ok: true, updated: updates.length }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown error";
    return new Response(
      JSON.stringify({ ok: false, error: message }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});

function supabaseSecretKey(): string {
  const secretKeys = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (secretKeys) {
    const parsed = JSON.parse(secretKeys) as Record<string, string>;
    const firstKey = parsed.default ?? Object.values(parsed)[0];
    if (firstKey) return firstKey;
  }

  const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!legacy) throw new Error("Missing Supabase service role key");
  return legacy;
}
