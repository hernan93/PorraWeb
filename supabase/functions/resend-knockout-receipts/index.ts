import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import {
  buildKnockoutReceipt,
  KnockoutMatchPredictionInput,
  sendConfirmationEmail,
} from "../_shared/knockout-receipt.ts";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-sync-secret",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

function getEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing env: ${name}`);
  return value;
}

function supabaseSecretKey(): string {
  const secretKeys = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (secretKeys) {
    const parsed = JSON.parse(secretKeys) as Record<string, string>;
    const firstKey = parsed.default ?? Object.values(parsed)[0];
    if (firstKey) return firstKey;
  }
  return getEnv("SUPABASE_SERVICE_ROLE_KEY");
}

interface ResendRequestBody {
  dry_run?: boolean;
  only_emails?: string[];
  limit?: number;
}

interface ParticipantRow {
  id: string;
  name: string;
  email: string;
  normalized_email: string;
  approval_status: string;
}

interface Target {
  submissionId: string;
  participantId: string;
  name: string;
  email: string;
  predictions: KnockoutMatchPredictionInput[];
}

/**
 * Reenvía el correo de confirmación de eliminatorias (ya corregido: resuelve los
 * slots de bracket W##/RU## a las predicciones reales del usuario) a quienes ya
 * enviaron su predicción. Reconstruye cada correo desde la BD y manda copia al admin.
 *
 * Protegida con el secreto admin `RESEND_ADMIN_SECRET` (header `x-sync-secret`).
 * Body (opcional): { dry_run?: boolean, only_emails?: string[], limit?: number }.
 *  - dry_run: no envía nada; devuelve a quién enviaría y cuántos partidos por persona.
 *  - only_emails: restringe a esos correos (útil para una prueba con 1 destinatario).
 *  - limit: tope de destinatarios a procesar en esta llamada.
 */
Deno.serve(async (req: Request) => {
  try {
    if (req.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }
    if (req.method !== "POST") {
      return json({ ok: false, error: "Metodo no permitido" }, 405);
    }

    const expectedSecret = Deno.env.get("RESEND_ADMIN_SECRET");
    if (!expectedSecret || req.headers.get("x-sync-secret") !== expectedSecret) {
      return json({ ok: false, error: "No autorizado" }, 401);
    }

    const raw = await req.json().catch(() => ({})) as ResendRequestBody;
    const dryRun = raw.dry_run === true;
    const onlyEmails = Array.isArray(raw.only_emails)
      ? new Set(raw.only_emails.map((e) => e.toLowerCase().trim()))
      : null;
    const limit = typeof raw.limit === "number" && raw.limit > 0 ? Math.floor(raw.limit) : null;

    const supabase = createClient(getEnv("SUPABASE_URL"), supabaseSecretKey());

    // 1. Submissions de eliminatorias activas
    const { data: submissions, error: subErr } = await supabase
      .from("submissions")
      .select("id, participant_id")
      .eq("phase", "knockouts")
      .in("status", ["submitted", "locked"]);
    if (subErr) return json({ ok: false, error: `Error al leer submissions: ${subErr.message}` }, 500);
    if (!submissions || submissions.length === 0) {
      return json({ ok: true, dry_run: dryRun, total_submissions: 0, eligible: 0, recipients: [] });
    }

    // 2. Participantes (solo aprobados pueden recibir)
    const participantIds = [...new Set(submissions.map((s) => s.participant_id))];
    const { data: participants, error: partErr } = await supabase
      .from("participants")
      .select("id, name, email, normalized_email, approval_status")
      .in("id", participantIds);
    if (partErr) return json({ ok: false, error: `Error al leer participantes: ${partErr.message}` }, 500);

    const participantById = new Map<string, ParticipantRow>();
    for (const p of (participants ?? []) as ParticipantRow[]) participantById.set(p.id, p);

    // 3. match_predictions de todas las submissions, agrupadas por submission
    const submissionIds = submissions.map((s) => s.id);
    const { data: preds, error: predErr } = await supabase
      .from("match_predictions")
      .select("submission_id, match_id, home_goals, away_goals, predicted_winner_team_id")
      .in("submission_id", submissionIds);
    if (predErr) return json({ ok: false, error: `Error al leer predicciones: ${predErr.message}` }, 500);

    const predsBySubmission = new Map<string, KnockoutMatchPredictionInput[]>();
    for (const p of preds ?? []) {
      if (!p.predicted_winner_team_id) continue; // solo eliminatorias (las de grupos no tienen ganador)
      const list = predsBySubmission.get(p.submission_id) ?? [];
      list.push({
        match_id: p.match_id,
        home_goals: p.home_goals,
        away_goals: p.away_goals,
        predicted_winner_team_id: p.predicted_winner_team_id,
      });
      predsBySubmission.set(p.submission_id, list);
    }

    // 4. Destinatarios elegibles
    const targets: Target[] = [];
    const skipped: { reason: string; participant_id: string; email?: string }[] = [];

    for (const sub of submissions) {
      const participant = participantById.get(sub.participant_id);
      if (!participant) {
        skipped.push({ reason: "participante no encontrado", participant_id: sub.participant_id });
        continue;
      }
      if (participant.approval_status !== "approved") {
        skipped.push({ reason: `approval_status=${participant.approval_status}`, participant_id: participant.id, email: participant.email });
        continue;
      }
      if (onlyEmails && !onlyEmails.has(participant.normalized_email.toLowerCase())) continue;
      const predictions = predsBySubmission.get(sub.id) ?? [];
      if (predictions.length === 0) {
        skipped.push({ reason: "sin predicciones de eliminatorias", participant_id: participant.id, email: participant.email });
        continue;
      }
      targets.push({
        submissionId: sub.id,
        participantId: participant.id,
        name: participant.name,
        email: participant.email,
        predictions,
      });
    }

    const limited = limit ? targets.slice(0, limit) : targets;

    // 5. Dry-run: construye el correo desde la BD para inspección, pero NO envía nada.
    if (dryRun) {
      const recipients: { name: string; email: string; predictions: number; preview: string }[] = [];
      for (const t of limited) {
        let preview: string;
        try {
          const receipt = await buildKnockoutReceipt(supabase, t.predictions);
          preview = receipt.text;
        } catch (err) {
          preview = `ERROR: ${err instanceof Error ? err.message : "desconocido"}`;
        }
        recipients.push({ name: t.name, email: t.email, predictions: t.predictions.length, preview });
      }
      return json({
        ok: true,
        dry_run: true,
        total_submissions: submissions.length,
        eligible: targets.length,
        would_send: limited.length,
        skipped,
        recipients,
      });
    }

    // 6. Envío real (secuencial para no saturar a Resend). Cada envío incluye copia al admin.
    let sent = 0;
    const results: { email: string; status: string; error?: string }[] = [];
    for (const t of limited) {
      try {
        const receipt = await buildKnockoutReceipt(supabase, t.predictions);
        await sendConfirmationEmail(supabase, t.participantId, t.name, t.email, receipt);
        sent++;
        results.push({ email: t.email, status: "sent" });
      } catch (err) {
        const msg = err instanceof Error ? err.message : "error desconocido";
        results.push({ email: t.email, status: "failed", error: msg });
      }
    }

    return json({
      ok: true,
      dry_run: false,
      total_submissions: submissions.length,
      eligible: targets.length,
      attempted: limited.length,
      sent,
      failed: limited.length - sent,
      skipped,
      results,
    });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Error interno del servidor";
    console.error("resend-knockout-receipts error:", err);
    return json({ ok: false, error: message }, 500);
  }
});
