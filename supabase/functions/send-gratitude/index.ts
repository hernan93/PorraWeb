import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const SUBJECT = "Arranca el Mundial y Hernan manda saludos";

const BODY_TEXT = [
  "Hernan me pidio que les mande un mensaje para desearles suerte.",
  "Asi que aca estoy, un bot haciendo de cartero.",
  "",
  "Gracias por estar, de verdad. Se anotaron, predijeron, armaron sus grupos",
  "y seguro ya tienen el fixture mas estudiado que la alineacion de su propio equipo.",
  "La fase de grupos esta cerrada y las predicciones guardadas.",
  "Ya no hay vuelta atras.",
  "",
  "Ahora solo queda ver los partidos, gritar los goles, sufrir los que nos meten",
  "y revisar el ranking como quien revisa el marcador cada cinco minutos",
  "aunque el partido recien empieza.",
  "",
  "Mucha suerte a todos. Que gane el que mejor predijo",
  "y que el VAR no nos arruine la birra del finde.",
  "",
  "Nos vemos en el ranking.",
].join("\n");

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

const BODY_HTML = [
  "<p>Hernan me pidio que les mande un mensaje para desearles suerte.",
  "Asi que aca estoy, un bot haciendo de cartero.</p>",
  "<p>Gracias por estar, de verdad. Se anotaron, predijeron, armaron sus grupos",
  "y seguro ya tienen el fixture mas estudiado que la alineacion de su propio equipo.",
  "La fase de grupos esta cerrada y las predicciones guardadas.",
  "Ya no hay vuelta atras.</p>",
  "<p>Ahora solo queda ver los partidos, gritar los goles, sufrir los que nos meten",
  "y revisar el ranking como quien revisa el marcador cada cinco minutos",
  "aunque el partido recien empieza.</p>",
  "<p>Mucha suerte a todos. Que gane el que mejor predijo",
  "y que el VAR no nos arruine la birra del finde.</p>",
  "<p>Nos vemos en el ranking.</p>",
].join("\n");

function getSupabaseClient(): SupabaseClient {
  const url = Deno.env.get("SUPABASE_URL")!;
  const key = supabaseSecretKey();
  return createClient(url, key, { auth: { persistSession: false } });
}

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

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

async function logEmail(
  client: SupabaseClient,
  participantId: string,
  email: string,
  template: string,
  status: "sent" | "failed",
  providerMessageId?: string,
  errorMessage?: string,
): Promise<void> {
  const { error } = await client.from("email_logs").insert({
    participant_id: participantId,
    email,
    template,
    status,
    provider_message_id: providerMessageId,
    error_message: errorMessage,
  });
  if (error) {
    console.error("Could not write email log:", error.message);
  }
}

async function sendEmailWithLog(
  client: SupabaseClient,
  apiKey: string,
  participantId: string,
  from: string,
  to: string,
): Promise<boolean> {
  try {
    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ from, to: [to], subject: SUBJECT, text: BODY_TEXT, html: BODY_HTML }),
    });
    const payload = await response.json().catch(() => ({})) as { id?: string; message?: string; error?: string };

    if (!response.ok) {
      await logEmail(client, participantId, to, "gratitude", "failed", undefined, payload.message ?? payload.error ?? `Resend returned ${response.status}`);
      return false;
    }

    await logEmail(client, participantId, to, "gratitude", "sent", payload.id);
    return true;
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown email error";
    await logEmail(client, participantId, to, "gratitude", "failed", undefined, message);
    return false;
  }
}

serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ ok: false, error: "Metodo no permitido" }, 405);
  }

  try {
    const client = getSupabaseClient();

    const expectedSecret = Deno.env.get("GRATITUDE_SECRET");
    const secretOk = expectedSecret && req.headers.get("x-sync-secret") === expectedSecret;

    if (!secretOk) {
      const authHeader = req.headers.get("Authorization");
      if (!authHeader) {
        return jsonResponse({ ok: false, error: "Falta cabecera de autorizacion" }, 401);
      }

      const token = authHeader.replace("Bearer ", "");
      const { data: { user }, error: authError } = await client.auth.getUser(token);
      if (authError || !user) {
        return jsonResponse({ ok: false, error: "Token invalido o expirado" }, 401);
      }

      const { data: adminUser, error: adminLookupError } = await client
        .from("admin_users")
        .select("user_id")
        .eq("user_id", user.id)
        .maybeSingle();

      if (adminLookupError || !adminUser) {
        return jsonResponse({ ok: false, error: "No autorizado. Se requiere acceso de administrador." }, 403);
      }
    }

    const apiKey = Deno.env.get("RESEND_API_KEY");
    if (!apiKey) {
      return jsonResponse({ ok: false, error: "Falta RESEND_API_KEY en el entorno" }, 500);
    }

    const from = Deno.env.get("RESEND_FROM_EMAIL") ?? "PorraWeb <onboarding@resend.dev>";

    const { data: participants, error: listError } = await client
      .from("participants")
      .select("id, name, email")
      .eq("approval_status", "approved");

    if (listError) {
      return jsonResponse({ ok: false, error: `Error al leer participantes: ${listError.message}` }, 500);
    }

    if (!participants || participants.length === 0) {
      return jsonResponse({ ok: false, error: "No hay participantes aprobados para enviar" }, 400);
    }

    let sent = 0;
    let failed = 0;

    for (const p of participants) {
      const ok = await sendEmailWithLog(client, apiKey, p.id, from, p.email);
      if (ok) sent++; else failed++;
    }

    return jsonResponse({
      ok: true,
      message: `Mensaje enviado a ${sent} participantes (${failed} fallos) de ${participants.length} aprobados.`,
      sent,
      failed,
      total: participants.length,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : "Error desconocido";
    return jsonResponse({ ok: false, error: message }, 500);
  }
});
