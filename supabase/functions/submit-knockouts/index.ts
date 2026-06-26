import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const PHASE_REQUIRED_COUNTS: Record<string, number> = {
  round_32: 16,
  round_16: 8,
  quarter_final: 4,
  semi_final: 2,
  third_place: 1,
  final: 1,
};

const VALID_KNOCKOUT_PHASES = new Set(Object.keys(PHASE_REQUIRED_COUNTS));

const PHASE_LABELS: Record<string, string> = {
  round_32: "Ronda de 32",
  round_16: "Octavos",
  quarter_final: "Cuartos de final",
  semi_final: "Semifinales",
  third_place: "Tercer puesto",
  final: "Final",
};

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

interface EmailReceipt {
  text: string;
  html: string;
}

function teamName(teamId: string | null | undefined, teams: Map<string, string>, fallback = "Por definir"): string {
  if (!teamId) return fallback;
  return teams.get(teamId) ?? fallback;
}

interface MatchInfo {
  match_number: number | null;
  phase: string;
  home_team_id: string | null;
  away_team_id: string | null;
  home_slot: string | null;
  away_slot: string | null;
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function slotLabel(slotCode: string, matches: Map<string, MatchInfo>, slotNumber: number): string {
  const match = matches.get(slotCode);
  if (match?.match_number) return `Partido ${match.match_number}`;
  return isUuid(slotCode) ? `Casilla ${slotNumber}` : slotCode;
}

async function buildKnockoutReceipt(client: SupabaseClient, body: RequestBody): Promise<EmailReceipt> {
  const teamIds = new Set<string>([
    ...body.knockout_predictions.map((p) => p.predicted_team_id),
    ...body.knockout_match_predictions.map((p) => p.predicted_winner_team_id),
  ]);
  const matchIds = body.knockout_match_predictions.map((p) => p.match_id);

  const matchesResult = await client
    .from("matches")
    .select("id, match_number, phase, home_team_id, away_team_id, home_slot, away_slot")
    .in("id", matchIds);
  if (matchesResult.error) throw new Error(`Error al preparar email de partidos: ${matchesResult.error.message}`);

  for (const match of matchesResult.data ?? []) {
    if (match.home_team_id) teamIds.add(match.home_team_id);
    if (match.away_team_id) teamIds.add(match.away_team_id);
  }

  const teamsResult = await client.from("teams").select("id, name, fifa_code").in("id", [...teamIds]);
  if (teamsResult.error) throw new Error(`Error al preparar email de equipos: ${teamsResult.error.message}`);

  const teams = new Map<string, string>();
  for (const team of teamsResult.data ?? []) {
    teams.set(team.id, team.fifa_code ? `${team.name} (${team.fifa_code})` : team.name);
  }

  const matches = new Map<string, MatchInfo>();
  for (const match of matchesResult.data ?? []) {
    matches.set(match.id, match);
  }

  const phaseOrder = Object.keys(PHASE_REQUIRED_COUNTS);
  const orderedBracket = [...body.knockout_predictions].sort((a, b) => {
    const phaseDiff = phaseOrder.indexOf(a.phase) - phaseOrder.indexOf(b.phase);
    const matchA = matches.get(a.slot_code)?.match_number ?? 999;
    const matchB = matches.get(b.slot_code)?.match_number ?? 999;
    return phaseDiff !== 0 ? phaseDiff : matchA - matchB || a.slot_code.localeCompare(b.slot_code);
  });

  const phaseSlotCounts = new Map<string, number>();
  const bracketItems = orderedBracket.map((prediction) => {
    const slotNumber = (phaseSlotCounts.get(prediction.phase) ?? 0) + 1;
    phaseSlotCounts.set(prediction.phase, slotNumber);
    return {
      prediction,
      slot: slotLabel(prediction.slot_code, matches, slotNumber),
    };
  });

  const bracketTextLines = bracketItems.map(({ prediction, slot }) => {
    return `${PHASE_LABELS[prediction.phase] ?? prediction.phase} - ${slot}: ${teamName(prediction.predicted_team_id, teams)}`;
  });

  const bracketRows = bracketItems.map(({ prediction, slot }) => {
    return `<tr><td>${escapeHtml(PHASE_LABELS[prediction.phase] ?? prediction.phase)}</td><td>${escapeHtml(slot)}</td><td>${escapeHtml(teamName(prediction.predicted_team_id, teams))}</td></tr>`;
  }).join("");

  const orderedMatches = [...body.knockout_match_predictions].sort((a, b) => {
    return (matches.get(a.match_id)?.match_number ?? 999) - (matches.get(b.match_id)?.match_number ?? 999);
  });

  const matchTextLines = orderedMatches.map((prediction) => {
    const match = matches.get(prediction.match_id);
    const home = teamName(match?.home_team_id, teams, match?.home_slot ?? "Por definir");
    const away = teamName(match?.away_team_id, teams, match?.away_slot ?? "Por definir");
    const winner = teamName(prediction.predicted_winner_team_id, teams);
    const number = match?.match_number ? `Partido ${match.match_number}` : "Partido";
    const phase = match?.phase ? ` - ${PHASE_LABELS[match.phase] ?? match.phase}` : "";
    return `${number}${phase}: ${home} ${prediction.home_goals} - ${prediction.away_goals} ${away}. Ganador: ${winner}`;
  });

  const matchRows = orderedMatches.map((prediction) => {
    const match = matches.get(prediction.match_id);
    const home = teamName(match?.home_team_id, teams, match?.home_slot ?? "Por definir");
    const away = teamName(match?.away_team_id, teams, match?.away_slot ?? "Por definir");
    const winner = teamName(prediction.predicted_winner_team_id, teams);
    return `<tr><td>${escapeHtml(match?.match_number?.toString() ?? "-")}</td><td>${escapeHtml(PHASE_LABELS[match?.phase ?? ""] ?? match?.phase ?? "")}</td><td>${escapeHtml(home)}</td><td>${prediction.home_goals} - ${prediction.away_goals}</td><td>${escapeHtml(away)}</td><td>${escapeHtml(winner)}</td></tr>`;
  }).join("");

  const html = `
    <h3>Clasificados y posiciones de eliminatorias</h3>
    <table>
      <thead><tr><th>Ronda</th><th>Casilla</th><th>Equipo elegido</th></tr></thead>
      <tbody>${bracketRows}</tbody>
    </table>
    <h3>Marcadores de eliminatorias</h3>
    <table>
      <thead><tr><th>Partido</th><th>Ronda</th><th>Local</th><th>Marcador</th><th>Visitante</th><th>Ganador</th></tr></thead>
      <tbody>${matchRows}</tbody>
    </table>
  `;

  const text = [
    "Clasificados y posiciones de eliminatorias:",
    ...bracketTextLines,
    "",
    "Marcadores de eliminatorias:",
    ...matchTextLines,
  ].join("\n");

  return { text, html };
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
  subject: string,
  text: string,
  html: string,
  template: string,
): Promise<void> {
  try {
    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ from, to: [to], subject, text, html }),
    });
    const payload = await response.json().catch(() => ({})) as { id?: string; message?: string; error?: string };

    if (!response.ok) {
      await logEmail(
        client,
        participantId,
        to,
        template,
        "failed",
        undefined,
        payload.message ?? payload.error ?? `Resend returned ${response.status}`,
      );
      return;
    }

    await logEmail(client, participantId, to, template, "sent", payload.id);
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown email error";
    await logEmail(client, participantId, to, template, "failed", undefined, message);
  }
}

async function sendConfirmationEmail(
  client: SupabaseClient,
  participantId: string,
  name: string,
  email: string,
  receipt: EmailReceipt,
): Promise<void> {
  const template = "knockouts_submission_confirmation";
  const apiKey = Deno.env.get("RESEND_API_KEY");
  if (!apiKey) {
    await logEmail(client, participantId, email, template, "failed", undefined, "Missing RESEND_API_KEY");
    return;
  }

  const safeName = escapeHtml(name);
  const from = Deno.env.get("RESEND_FROM_EMAIL") ?? "PorraWeb <onboarding@resend.dev>";
  const adminReceiptEmail = Deno.env.get("ADMIN_RECEIPT_EMAIL") ?? "hernancit1993@gmail.com";
  const subject = "Confirmacion de prediccion de eliminatorias - PorraWeb";
  const text = `Hola ${name},\n\nRecibimos tu prediccion de eliminatorias en PorraWeb. Podras seguir el ranking en la pagina cuando avancen los resultados oficiales.\n\nEste es el resguardo de lo que enviaste:\n\n${receipt.text}\n\nGracias por participar.`;
  const html = `
    <p>Hola ${safeName},</p>
    <p>Recibimos tu prediccion de eliminatorias en PorraWeb.</p>
    <p>Podras seguir el ranking en la pagina cuando avancen los resultados oficiales.</p>
    <p><strong>Este es el resguardo de lo que enviaste:</strong></p>
    ${receipt.html}
    <p>Gracias por participar.</p>
  `;

  await sendEmailWithLog(client, apiKey, participantId, from, email, subject, text, html, template);

  if (adminReceiptEmail.toLowerCase() !== email.toLowerCase()) {
    const adminSubject = `Copia admin - ${subject}`;
    const adminText = `Resguardo admin de prediccion enviada por ${name} <${email}>.\n\n${receipt.text}`;
    const adminHtml = `
      <p><strong>Resguardo admin de prediccion enviada.</strong></p>
      <p>Participante: ${safeName} &lt;${escapeHtml(email)}&gt;</p>
      ${receipt.html}
    `;
    await sendEmailWithLog(client, apiKey, participantId, from, adminReceiptEmail, adminSubject, adminText, adminHtml, "knockouts_submission_admin_copy");
  }
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

interface KnockoutPrediction {
  phase: string;
  slot_code: string;
  predicted_team_id: string;
}

interface KnockoutMatchPrediction {
  match_id: string;
  home_goals: number;
  away_goals: number;
  predicted_winner_team_id: string;
}

interface RequestBody {
  participant_name?: string;
  participant_email: string;
  knockout_predictions: KnockoutPrediction[];
  knockout_match_predictions: KnockoutMatchPrediction[];
}

async function findOrCreateParticipant(
  supabase: SupabaseClient,
  name: string,
  email: string,
): Promise<{ participant: { id: string; approval_status: string } | null; created: boolean; error: string | null }> {
  const normalizedEmail = email.toLowerCase().trim();
  const { data: existing, error: lookupError } = await supabase
    .from("participants")
    .select("id, approval_status, name")
    .eq("normalized_email", normalizedEmail)
    .maybeSingle();

  if (lookupError) {
    console.error("findOrCreateParticipant lookup error:", lookupError.message);
    return { participant: null, created: false, error: "Error al buscar participante. Intenta de nuevo." };
  }

  if (!existing) {
    return { participant: null, created: false, error: "No encontramos una participacion con ese correo. Debes haber enviado la fase de grupos primero y estar aprobado." };
  }

  if (existing.approval_status !== "approved") {
    return { participant: null, created: false, error: "Tu participacion no esta aprobada. Contacta con el administrador." };
  }

  return { participant: existing, created: false, error: null };
}

function validateRequest(body: unknown): RequestBody {
  if (!body || typeof body !== "object") {
    throw new Error("Cuerpo JSON no valido");
  }

  const b = body as Record<string, unknown>;

  if (typeof b.participant_email !== "string" || !b.participant_email.trim()) {
    throw new Error("El campo participant_email es obligatorio");
  }

  if (!Array.isArray(b.knockout_predictions) || b.knockout_predictions.length === 0) {
    throw new Error("knockout_predictions debe ser una lista no vacia");
  }

  if (!Array.isArray(b.knockout_match_predictions) || b.knockout_match_predictions.length === 0) {
    throw new Error("knockout_match_predictions debe ser una lista no vacia");
  }

  for (const p of b.knockout_predictions as KnockoutPrediction[]) {
    if (typeof p.phase !== "string" || !p.phase.trim()) {
      throw new Error("Cada prediccion de cruce debe tener una fase");
    }
    if (typeof p.slot_code !== "string" || !p.slot_code.trim()) {
      throw new Error("Cada prediccion de cruce debe tener un slot_code");
    }
    if (typeof p.predicted_team_id !== "string" || !p.predicted_team_id.trim()) {
      throw new Error("Cada prediccion de cruce debe tener un predicted_team_id");
    }
  }

  for (const p of b.knockout_match_predictions as KnockoutMatchPrediction[]) {
    if (typeof p.match_id !== "string" || !p.match_id.trim()) {
      throw new Error("Cada prediccion de marcador debe tener un match_id");
    }
    if (typeof p.home_goals !== "number" || !Number.isInteger(p.home_goals) || p.home_goals < 0) {
      throw new Error("Los goles locales deben ser un entero no negativo");
    }
    if (typeof p.away_goals !== "number" || !Number.isInteger(p.away_goals) || p.away_goals < 0) {
      throw new Error("Los goles visitantes deben ser un entero no negativo");
    }
    if (typeof p.predicted_winner_team_id !== "string" || !p.predicted_winner_team_id.trim()) {
      throw new Error("Cada prediccion de marcador debe tener un predicted_winner_team_id");
    }
  }

  return {
    participant_name: (typeof b.participant_name === "string" ? b.participant_name.trim() : ""),
    participant_email: b.participant_email.trim(),
    knockout_predictions: b.knockout_predictions as KnockoutPrediction[],
    knockout_match_predictions: b.knockout_match_predictions as KnockoutMatchPrediction[],
  };
}

Deno.serve(async (req: Request) => {
  try {
    if (req.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    if (req.method !== "POST") {
      return json({ ok: false, error: "Metodo no permitido" }, 405);
    }

    const supabaseUrl = getEnv("SUPABASE_URL");
    const serviceRoleKey = supabaseSecretKey();

    const supabase = createClient(supabaseUrl, serviceRoleKey);

    const raw: unknown = await req.json().catch(() => {
      throw new Error("Cuerpo JSON no valido");
    });

    const body = validateRequest(raw);
    const { participant, created, error: participantErr } = await findOrCreateParticipant(
      supabase,
      body.participant_name,
      body.participant_email,
    );

    if (participantErr) return json({ ok: false, error: participantErr }, 500);
    if (!participant) return json({ ok: false, error: "Participante no encontrado" }, 500);
    if (participant.approval_status === "rejected") {
      return json({ ok: false, error: "Tu participacion fue rechazada. Contacta con el administrador." }, 403);
    }

    // 2. Check knockout form status
    const { data: appSettings, error: settingsErr } = await supabase
      .from("app_settings")
      .select("value")
      .eq("key", "knockouts_form_status")
      .single();

    if (settingsErr) {
      return json({ ok: false, error: "Error al leer configuracion" }, 500);
    }

    if (appSettings.value === "closed") {
      return json({ ok: false, error: "El formulario de eliminatorias esta cerrado." }, 403);
    }

    // 3. Check no existing submission
    const { data: existingSub, error: existingErr } = await supabase
      .from("submissions")
      .select("id")
      .eq("participant_id", participant.id)
      .eq("phase", "knockouts")
      .in("status", ["submitted", "locked"])
      .maybeSingle();

    if (existingErr) {
      return json({ ok: false, error: "Error al verificar predicciones existentes" }, 500);
    }

    if (existingSub) {
      return json({ ok: false, error: "Ya tienes una predicción de eliminatorias enviada." }, 409);
    }

    // 4. Validate phases have correct counts
    const phaseCounts: Record<string, number> = {};
    for (const p of body.knockout_predictions) {
      phaseCounts[p.phase] = (phaseCounts[p.phase] ?? 0) + 1;
    }

    for (const [phase, expected] of Object.entries(PHASE_REQUIRED_COUNTS)) {
      const actual = phaseCounts[phase] ?? 0;
      if (actual !== expected) {
        return json({
          ok: false,
          error: `${PHASE_LABELS[phase] ?? phase}: debe tener exactamente ${expected} predicciones, recibio ${actual}`,
        }, 400);
      }
    }

    for (const phase of Object.keys(phaseCounts)) {
      if (!VALID_KNOCKOUT_PHASES.has(phase)) {
        return json({ ok: false, error: `Fase de eliminatorias no valida: ${phase}` }, 400);
      }
    }

    // 5. Validate all predicted_team_ids exist
    const predictedTeamIds = [
      ...new Set(body.knockout_predictions.map((p) => p.predicted_team_id)),
      ...new Set(body.knockout_match_predictions.map((p) => p.predicted_winner_team_id)),
    ];

    const { data: teams, error: teamsErr } = await supabase
      .from("teams")
      .select("id")
      .in("id", predictedTeamIds);

    if (teamsErr) {
      return json({ ok: false, error: "Error al validar equipos" }, 500);
    }

    const validTeamIds = new Set((teams ?? []).map((t: { id: string }) => t.id));
    for (const tid of predictedTeamIds) {
      if (!validTeamIds.has(tid)) {
        return json({ ok: false, error: `Equipo no encontrado: ${tid}` }, 400);
      }
    }

    // 6. Validate knockout match predictions: match_ids exist and are knockout matches
    const matchIds = [...new Set(body.knockout_match_predictions.map((p) => p.match_id))];

    const { data: matches, error: matchesErr } = await supabase
      .from("matches")
      .select("id, phase")
      .in("id", matchIds);

    if (matchesErr) {
      return json({ ok: false, error: "Error al validar partidos" }, 500);
    }

    const validMatchIds = new Set((matches ?? []).map((m: { id: string }) => m.id));
    for (const mid of matchIds) {
      if (!validMatchIds.has(mid)) {
        return json({ ok: false, error: `Partido no encontrado: ${mid}` }, 400);
      }
    }

    for (const m of matches ?? []) {
      if (m.phase === "group") {
        return json({ ok: false, error: `El partido ${m.id} es de fase de grupos, no de eliminatorias` }, 400);
      }
    }

    // All validations passed — insert submission, predictions, match_predictions

    const { data: submission, error: insertSubErr } = await supabase
      .from("submissions")
      .insert({
        participant_id: participant.id,
        phase: "knockouts",
        status: "submitted",
      })
      .select("id")
      .single();

    if (insertSubErr || !submission) {
      return json({ ok: false, error: "Error al crear prediccion" }, 500);
    }

    const submissionId: string = submission.id;

    const knockoutPredictionsRow = body.knockout_predictions.map((p) => ({
      submission_id: submissionId,
      phase: p.phase,
      slot_code: p.slot_code,
      predicted_team_id: p.predicted_team_id,
    }));

    const { error: insertKnockoutErr } = await supabase
      .from("knockout_predictions")
      .insert(knockoutPredictionsRow);

    if (insertKnockoutErr) {
      await supabase.from("submissions").delete().eq("id", submissionId);
      return json({ ok: false, error: "Error al guardar predicciones de eliminatorias" }, 500);
    }

    const knockouMatchPredictionsRow = body.knockout_match_predictions.map((p) => ({
      submission_id: submissionId,
      match_id: p.match_id,
      home_goals: p.home_goals,
      away_goals: p.away_goals,
      predicted_winner_team_id: p.predicted_winner_team_id,
    }));

    const { error: insertMatchErr } = await supabase
      .from("match_predictions")
      .insert(knockouMatchPredictionsRow);

    if (insertMatchErr) {
      await supabase.from("submissions").delete().eq("id", submissionId);
      return json({ ok: false, error: "Error al guardar marcadores de eliminatorias" }, 500);
    }

    // Trigger recalculation
    const { error: recalcErr } = await supabase.rpc("recalculate_scores", {
      p_source: "manual_recalc",
    });

    if (recalcErr) {
      console.error("recalculate_scores failed:", recalcErr);
    }

    const receipt = await buildKnockoutReceipt(supabase, body);

    await sendConfirmationEmail(
      supabase,
      participant.id,
      body.participant_name.trim(),
      body.participant_email.trim(),
      receipt,
    );

    const message = created || participant.approval_status !== "approved"
      ? "Predicción recibida. Queda pendiente de aprobación del pago por el administrador."
      : "Predicción guardada correctamente.";

    return json({ ok: true, submission_id: submissionId, message });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Error interno del servidor";
    console.error("submit-knockouts error:", err);
    const status = /required|must|invalid/i.test(message) ? 400 : 500;
    return json({ ok: false, error: message }, status);
  }
});
