import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const SUBJECT = "\u00A1Se abrieron las eliminatorias! \u26BD\u{1F3C3}";

const BODY_TEXT = [
  "\u00A1Hola, hola, hola! Otra vez yo, el bot.",
  "\u00BFVieron qu\u00E9 r\u00E1pido pas\u00F3 todo?",
  "La fase de grupos se fue m\u00E1s r\u00E1pido que Mbapp\u00E9 por la banda y ya estamos en los cruces.",
  "",
  "Las predicciones de eliminatorias YA est\u00E1n abiertas. Entren ahora a:",
  "",
  "https://porraweb.us/#/knockouts",
  "",
  "Instrucciones r\u00E1pidas:",
  "- Usen el mismo correo de la fase de grupos.",
  "- Llenen todos los partidos: ronda de 32, octavos, cuartos, semis, final y tercer puesto.",
  "  Si falta uno no se env\u00EDa.",
  "- Elijan marcador y ganador de cada cruce. Los ganadores pasan solos a la siguiente ronda.",
  "- Pueden poner empate en el marcador, pero igual tienen que elegir un ganador.",
  "  No hace falta adivinar penales.",
  "- Una vez enviado no se edita. Revisen bien antes de apretar el bot\u00F3n.",
  "",
  "Tienen hasta las 21:00 de hoy (hora Espa\u00F1a peninsular).",
  "Cuando arranque Sud\u00E1frica vs Canad\u00E1 se cierra todo.",
  "",
  "Mucha suerte a todos. Que gane el que mejor predijo...",
  "y si no, siempre queda echarle la culpa al VAR.",
  "",
  "-- El Bot de PorraWeb \u{1F916}\u26BD",
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
  '<p>\u00A1Hola, hola, hola! Otra vez yo, el bot.',
  '\u00BFVieron qu\u00E9 r\u00E1pido pas\u00F3 todo?',
  'La fase de grupos se fue m\u00E1s r\u00E1pido que Mbapp\u00E9 por la banda y ya estamos en los cruces.</p>',
  '<p><strong>Las predicciones de eliminatorias YA est\u00E1n abiertas.</strong> Entren ahora a:</p>',
  '<p><a href="https://porraweb.us/#/knockouts" style="font-size:16px;font-weight:700;color:#0f766e">https://porraweb.us/#/knockouts</a></p>',
  '<p><strong>Instrucciones r\u00E1pidas:</strong></p>',
  '<ul style="padding-left:18px">',
  '<li>Usen el <strong>mismo correo</strong> de la fase de grupos.</li>',
  '<li>Llenen <strong>todos los partidos</strong>: ronda de 32, octavos, cuartos, semis, final y tercer puesto. Si falta uno no se env\u00EDa.</li>',
  '<li>Elijan marcador y ganador de cada cruce. Los ganadores pasan solos a la siguiente ronda.</li>',
  '<li>Pueden poner empate en el marcador, pero igual tienen que elegir un ganador. No hace falta adivinar penales.</li>',
  '<li>Una vez enviado <strong>no se edita</strong>. Revisen bien antes de apretar el bot\u00F3n.</li>',
  '</ul>',
  '<p>\u23F0 <strong>Tienen hasta las 21:00 de hoy (hora Espa\u00F1a peninsular).</strong>',
  'Cuando arranque Sud\u00E1frica vs Canad\u00E1 se cierra todo.</p>',
  '<p>Mucha suerte a todos. Que gane el que mejor predijo...',
  'y si no, siempre queda echarle la culpa al VAR.</p>',
  '<p>-- El Bot de PorraWeb \u{1F916}\u26BD</p>',
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
      await logEmail(client, participantId, to, "preaviso", "failed", undefined, payload.message ?? payload.error ?? `Resend returned ${response.status}`);
      return false;
    }

    await logEmail(client, participantId, to, "preaviso", "sent", payload.id);
    return true;
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown email error";
    await logEmail(client, participantId, to, "preaviso", "failed", undefined, message);
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

    const expectedSecret = Deno.env.get("PREAVISO_SECRET");
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
