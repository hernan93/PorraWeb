import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface MatchPrediction {
  match_id: string;
  home_goals: number;
  away_goals: number;
}

interface GroupPosition {
  group_id: string;
  team_id: string;
  predicted_position: number;
}

interface RequestBody {
  participant_name: string;
  participant_email: string;
  match_predictions: MatchPrediction[];
  group_positions: GroupPosition[];
  third_place_selections: string[];
}

interface EmailReceipt {
  text: string;
  html: string;
}

function getSupabaseClient(): SupabaseClient {
  const url = Deno.env.get("SUPABASE_URL")!;
  const key = supabaseSecretKey();
  return createClient(url, key, {
    auth: { persistSession: false },
  });
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

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function teamName(teamId: string | null | undefined, teams: Map<string, string>, fallback = "Por definir"): string {
  if (!teamId) return fallback;
  return teams.get(teamId) ?? fallback;
}

async function buildGroupReceipt(client: SupabaseClient, body: RequestBody): Promise<EmailReceipt> {
  const matchIds = body.match_predictions.map((p) => p.match_id);
  const teamIds = new Set<string>(body.group_positions.map((p) => p.team_id));
  const groupIds = new Set<string>([
    ...body.group_positions.map((p) => p.group_id),
    ...body.third_place_selections,
  ]);

  const [matchesResult, groupsResult] = await Promise.all([
    client
      .from("matches")
      .select("id, match_number, group_id, home_team_id, away_team_id, home_slot, away_slot")
      .in("id", matchIds),
    client.from("tournament_groups").select("id, code, name").in("id", [...groupIds]),
  ]);

  if (matchesResult.error) throw new Error(`Error al preparar email de partidos: ${matchesResult.error.message}`);
  if (groupsResult.error) throw new Error(`Error al preparar email de grupos: ${groupsResult.error.message}`);

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

  const groups = new Map<string, { code: string; name: string }>();
  for (const group of groupsResult.data ?? []) {
    groups.set(group.id, { code: group.code, name: group.name });
  }

  const matches = new Map<string, {
    match_number: number | null;
    group_id: string | null;
    home_team_id: string | null;
    away_team_id: string | null;
    home_slot: string | null;
    away_slot: string | null;
  }>();
  for (const match of matchesResult.data ?? []) {
    matches.set(match.id, match);
  }

  const orderedMatches = [...body.match_predictions].sort((a, b) => {
    return (matches.get(a.match_id)?.match_number ?? 999) - (matches.get(b.match_id)?.match_number ?? 999);
  });

  const matchTextLines = orderedMatches.map((prediction) => {
    const match = matches.get(prediction.match_id);
    const group = match?.group_id ? groups.get(match.group_id)?.code : "";
    const home = teamName(match?.home_team_id, teams, match?.home_slot ?? "Por definir");
    const away = teamName(match?.away_team_id, teams, match?.away_slot ?? "Por definir");
    const number = match?.match_number ? `Partido ${match.match_number}` : "Partido";
    return `${number}${group ? ` - Grupo ${group}` : ""}: ${home} ${prediction.home_goals} - ${prediction.away_goals} ${away}`;
  });

  const matchRows = orderedMatches.map((prediction) => {
    const match = matches.get(prediction.match_id);
    const group = match?.group_id ? groups.get(match.group_id)?.code ?? "" : "";
    const home = teamName(match?.home_team_id, teams, match?.home_slot ?? "Por definir");
    const away = teamName(match?.away_team_id, teams, match?.away_slot ?? "Por definir");
    return `<tr><td>${escapeHtml(match?.match_number?.toString() ?? "-")}</td><td>${escapeHtml(group)}</td><td>${escapeHtml(home)}</td><td>${prediction.home_goals} - ${prediction.away_goals}</td><td>${escapeHtml(away)}</td></tr>`;
  }).join("");

  const positionsByGroup = new Map<string, GroupPosition[]>();
  for (const position of body.group_positions) {
    const rows = positionsByGroup.get(position.group_id) ?? [];
    rows.push(position);
    positionsByGroup.set(position.group_id, rows);
  }

  const orderedGroupIds = [...positionsByGroup.keys()].sort((a, b) => {
    return (groups.get(a)?.code ?? "").localeCompare(groups.get(b)?.code ?? "");
  });

  const positionTextLines: string[] = [];
  const positionTables = orderedGroupIds.map((groupId) => {
    const group = groups.get(groupId);
    const rows = (positionsByGroup.get(groupId) ?? []).sort((a, b) => a.predicted_position - b.predicted_position);
    positionTextLines.push(`Grupo ${group?.code ?? groupId}:`);
    const htmlRows = rows.map((position) => {
      const name = teamName(position.team_id, teams);
      positionTextLines.push(`  ${position.predicted_position}. ${name}`);
      return `<tr><td>${position.predicted_position}</td><td>${escapeHtml(name)}</td></tr>`;
    }).join("");
    return `<h4>Grupo ${escapeHtml(group?.code ?? groupId)}</h4><table>${htmlRows}</table>`;
  }).join("");

  const thirdText = body.third_place_selections
    .map((groupId) => groups.get(groupId)?.code ?? groupId)
    .sort()
    .join(", ");

  const html = `
    <h3>Predicciones de partidos</h3>
    <table>
      <thead><tr><th>Partido</th><th>Grupo</th><th>Local</th><th>Marcador</th><th>Visitante</th></tr></thead>
      <tbody>${matchRows}</tbody>
    </table>
    <h3>Posiciones por grupo</h3>
    ${positionTables}
    <h3>Mejores terceros</h3>
    <p>${escapeHtml(thirdText)}</p>
  `;

  const text = [
    "Predicciones de partidos:",
    ...matchTextLines,
    "",
    "Posiciones por grupo:",
    ...positionTextLines,
    "",
    `Mejores terceros: ${thirdText}`,
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
  const template = "groups_submission_confirmation";
  const apiKey = Deno.env.get("RESEND_API_KEY");
  if (!apiKey) {
    await logEmail(client, participantId, email, template, "failed", undefined, "Missing RESEND_API_KEY");
    return;
  }

  const safeName = escapeHtml(name);
  const from = Deno.env.get("RESEND_FROM_EMAIL") ?? "PorraWeb <onboarding@resend.dev>";
  const adminReceiptEmail = Deno.env.get("ADMIN_RECEIPT_EMAIL") ?? "hernancit1993@gmail.com";
  const subject = "Confirmacion de prediccion de grupos - PorraWeb";
  const text = `Hola ${name},\n\nRecibimos tu prediccion de fase de grupos en PorraWeb. Cuando el administrador revise el pago y avance el torneo, podras seguir el ranking en la pagina.\n\nEste es el resguardo de lo que enviaste:\n\n${receipt.text}\n\nGracias por participar.`;
  const html = `
    <p>Hola ${safeName},</p>
    <p>Recibimos tu prediccion de fase de grupos en PorraWeb.</p>
    <p>Cuando el administrador revise el pago y avance el torneo, podras seguir el ranking en la pagina.</p>
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
    await sendEmailWithLog(client, apiKey, participantId, from, adminReceiptEmail, adminSubject, adminText, adminHtml, "groups_submission_admin_copy");
  }
}

function validateNonBlank(value: unknown, field: string): string | null {
  if (typeof value !== "string" || value.trim().length === 0) {
    return `El campo ${field} es obligatorio.`;
  }
  return null;
}

async function findOrCreateParticipant(
  client: SupabaseClient,
  name: string,
  email: string,
): Promise<{ error: string | null; participant: { id: string; approval_status: string } | null }> {
  const normalizedEmail = email.toLowerCase().trim();

  const { data: existing, error: lookupError } = await client
    .from("participants")
    .select("id, approval_status")
    .eq("normalized_email", normalizedEmail)
    .maybeSingle();

  if (lookupError) {
    return { error: `Error al buscar participante: ${lookupError.message}`, participant: null };
  }

  if (!existing) {
    const { error: insertError } = await client.from("participants").insert({
      name: name.trim(),
      email: email.trim(),
      normalized_email: normalizedEmail,
      approval_status: "pending",
    });

    if (insertError) {
      return { error: `Error al crear participante: ${insertError.message}`, participant: null };
    }

    return { error: "Participante no encontrado o no aprobado", participant: null };
  }

  return { error: null, participant: existing };
}

async function validateMatches(
  client: SupabaseClient,
  predictions: MatchPrediction[],
): Promise<string | null> {
  const matchIds = [...new Set(predictions.map((p) => p.match_id))];

  const { data: matches, error } = await client
    .from("matches")
    .select("id")
    .eq("phase", "group")
    .in("id", matchIds);

  if (error) {
    return `Error al validar partidos: ${error.message}`;
  }

  const existingIds = new Set(matches?.map((m) => m.id) ?? []);

  for (const id of matchIds) {
    if (!existingIds.has(id)) {
      return `El match_id '${id}' no existe en la fase de grupos.`;
    }
  }

  return null;
}

async function validateGroupsAndTeams(
  client: SupabaseClient,
  positions: GroupPosition[],
): Promise<string | null> {
  const groupIds = [...new Set(positions.map((p) => p.group_id))];
  const teamIds = [...new Set(positions.map((p) => p.team_id))];

  const [{ data: groups, error: groupError }, { data: teams, error: teamError }] =
    await Promise.all([
      client.from("tournament_groups").select("id").in("id", groupIds),
      client.from("teams").select("id").in("id", teamIds),
    ]);

  if (groupError) return `Error al validar grupos: ${groupError.message}`;
  if (teamError) return `Error al validar equipos: ${teamError.message}`;

  const existingGroupIds = new Set(groups?.map((g) => g.id) ?? []);
  const existingTeamIds = new Set(teams?.map((t) => t.id) ?? []);

  for (const id of groupIds) {
    if (!existingGroupIds.has(id)) return `El group_id '${id}' no existe.`;
  }

  for (const id of teamIds) {
    if (!existingTeamIds.has(id)) return `El team_id '${id}' no existe.`;
  }

  return null;
}

serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ ok: false, error: "Método no permitido" }, 405);
  }

  try {
    const client = getSupabaseClient();

    const body: RequestBody = await req.json();

    const nameErr = validateNonBlank(body.participant_name, "participant_name");
    if (nameErr) return jsonResponse({ ok: false, error: nameErr }, 400);

    const emailErr = validateNonBlank(body.participant_email, "participant_email");
    if (emailErr) return jsonResponse({ ok: false, error: emailErr }, 400);

    const { error: participantError, participant } = await findOrCreateParticipant(
      client,
      body.participant_name,
      body.participant_email,
    );
    if (participantError) return jsonResponse({ ok: false, error: participantError }, 400);
    if (!participant) return jsonResponse({ ok: false, error: "Participante no encontrado." }, 400);

    if (participant.approval_status !== "approved") {
      return jsonResponse(
        {
          ok: false,
          error:
            "Tu participación aún no ha sido aprobada. Contacta con el administrador.",
        },
        403,
      );
    }

    const { data: formSetting, error: formError } = await client
      .from("app_settings")
      .select("value")
      .eq("key", "groups_form_status")
      .maybeSingle();

    if (formError) {
      return jsonResponse({ ok: false, error: `Error al verificar estado del formulario: ${formError.message}` }, 500);
    }

    if (formSetting?.value === "closed") {
      return jsonResponse(
        { ok: false, error: "El formulario de grupos está cerrado." },
        400,
      );
    }

    const { data: existingSub } = await client
      .from("submissions")
      .select("id")
      .eq("participant_id", participant.id)
      .eq("phase", "groups")
      .in("status", ["submitted", "locked"])
      .maybeSingle();

    if (existingSub) {
      return jsonResponse(
        { ok: false, error: "Ya tienes una predicción de grupos enviada." },
        409,
      );
    }

    if (!Array.isArray(body.match_predictions) || body.match_predictions.length !== 72) {
      return jsonResponse(
        { ok: false, error: "Debes enviar exactamente 72 predicciones de partidos." },
        400,
      );
    }

    const matchIds = body.match_predictions.map((p) => p.match_id);
    if (new Set(matchIds).size !== 72) {
      return jsonResponse({ ok: false, error: "No puedes repetir partidos en las predicciones." }, 400);
    }

    for (const p of body.match_predictions) {
      if (!Number.isInteger(p.home_goals) || !Number.isInteger(p.away_goals) || p.home_goals < 0 || p.away_goals < 0) {
        return jsonResponse({ ok: false, error: "Los goles deben ser enteros mayores o iguales a cero." }, 400);
      }
    }

    if (!Array.isArray(body.group_positions) || body.group_positions.length !== 48) {
      return jsonResponse(
        { ok: false, error: "Debes enviar exactamente 48 posiciones de grupo (12 grupos x 4 posiciones)." },
        400,
      );
    }

    if (!Array.isArray(body.third_place_selections) || body.third_place_selections.length !== 8) {
      return jsonResponse(
        { ok: false, error: "Debes seleccionar exactamente 8 mejores terceros." },
        400,
      );
    }

    if (new Set(body.third_place_selections).size !== 8) {
      return jsonResponse({ ok: false, error: "No puedes repetir grupos en mejores terceros." }, 400);
    }

    for (const p of body.group_positions) {
      if (
        typeof p.predicted_position !== "number" ||
        p.predicted_position < 1 ||
        p.predicted_position > 4
      ) {
        return jsonResponse(
          { ok: false, error: `predicted_position debe estar entre 1 y 4.` },
          400,
        );
      }
    }

    const positionsByGroup = new Map<string, GroupPosition[]>();
    for (const p of body.group_positions) {
      const rows = positionsByGroup.get(p.group_id) ?? [];
      rows.push(p);
      positionsByGroup.set(p.group_id, rows);
    }

    if (positionsByGroup.size !== 12) {
      return jsonResponse({ ok: false, error: "Debes enviar posiciones para los 12 grupos." }, 400);
    }

    for (const [groupId, positions] of positionsByGroup.entries()) {
      const teams = new Set(positions.map((p) => p.team_id));
      const ranks = new Set(positions.map((p) => p.predicted_position));
      if (positions.length !== 4 || teams.size !== 4 || ranks.size !== 4) {
        return jsonResponse({ ok: false, error: `El grupo ${groupId} debe tener 4 equipos y 4 posiciones únicas.` }, 400);
      }
    }

    const submittedGroupIds = new Set(body.group_positions.map((p) => p.group_id));
    for (const groupId of body.third_place_selections) {
      if (!submittedGroupIds.has(groupId)) {
        return jsonResponse({ ok: false, error: `El grupo ${groupId} no existe en tus posiciones de grupo.` }, 400);
      }
    }

    const matchError = await validateMatches(client, body.match_predictions);
    if (matchError) return jsonResponse({ ok: false, error: matchError }, 400);

    const groupTeamError = await validateGroupsAndTeams(client, body.group_positions);
    if (groupTeamError) return jsonResponse({ ok: false, error: groupTeamError }, 400);

    const { data: submission, error: subInsertError } = await client
      .from("submissions")
      .insert({
        participant_id: participant.id,
        phase: "groups",
        status: "submitted",
      })
      .select("id")
      .single();

    if (subInsertError || !submission) {
      return jsonResponse(
        { ok: false, error: `Error al crear la predicción: ${subInsertError?.message}` },
        500,
      );
    }

    const submissionId = submission.id;

    const matchPredictionsPayload = body.match_predictions.map((mp) => ({
      submission_id: submissionId,
      match_id: mp.match_id,
      home_goals: mp.home_goals,
      away_goals: mp.away_goals,
    }));

    const { error: mpError } = await client
      .from("match_predictions")
      .insert(matchPredictionsPayload);

    if (mpError) {
      await client.from("submissions").delete().eq("id", submissionId);
      return jsonResponse(
        { ok: false, error: `Error al guardar predicciones de partidos: ${mpError.message}` },
        500,
      );
    }

    const groupPositionPayload = body.group_positions.map((gp) => ({
      submission_id: submissionId,
      group_id: gp.group_id,
      team_id: gp.team_id,
      predicted_position: gp.predicted_position,
    }));

    const { error: gpError } = await client
      .from("group_position_predictions")
      .insert(groupPositionPayload);

    if (gpError) {
      await client.from("submissions").delete().eq("id", submissionId);
      return jsonResponse(
        { ok: false, error: `Error al guardar posiciones de grupo: ${gpError.message}` },
        500,
      );
    }

    if (body.third_place_selections.length > 0) {
      const thirdPlacePayload = body.third_place_selections.map((groupId) => ({
        submission_id: submissionId,
        group_id: groupId,
      }));

      const { error: tpError } = await client
        .from("third_place_qualifier_predictions")
        .insert(thirdPlacePayload);

      if (tpError) {
        await client.from("submissions").delete().eq("id", submissionId);
        return jsonResponse(
          { ok: false, error: `Error al guardar selecciones de terceros: ${tpError.message}` },
          500,
        );
      }
    }

    const { error: rpcError } = await client.rpc("recalculate_scores", {
      p_source: "manual_recalc",
    });

    if (rpcError) {
      console.error("RPC recalculate_scores failed:", rpcError.message);
    }

    const receipt = await buildGroupReceipt(client, body);

    await sendConfirmationEmail(
      client,
      participant.id,
      body.participant_name.trim(),
      body.participant_email.trim(),
      receipt,
    );

    return jsonResponse({ ok: true, submission_id: submissionId });
  } catch (err) {
    console.error("Unexpected error:", err);
    const message = err instanceof Error ? err.message : "Error interno del servidor.";
    return jsonResponse({ ok: false, error: message }, 500);
  }
});
