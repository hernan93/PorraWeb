import { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

export const PHASE_LABELS: Record<string, string> = {
  round_32: "Ronda de 32",
  round_16: "Octavos",
  quarter_final: "Cuartos de final",
  semi_final: "Semifinales",
  third_place: "Tercer puesto",
  final: "Final",
};

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

export interface EmailReceipt {
  text: string;
  html: string;
}

export interface KnockoutMatchPredictionInput {
  match_id: string;
  home_goals: number;
  away_goals: number;
  predicted_winner_team_id: string;
}

interface MatchInfo {
  match_number: number | null;
  phase: string;
  home_team_id: string | null;
  away_team_id: string | null;
  home_slot: string | null;
  away_slot: string | null;
}

function teamName(teamId: string | null | undefined, teams: Map<string, string>, fallback = "Por definir"): string {
  if (!teamId) return fallback;
  return teams.get(teamId) ?? fallback;
}

/**
 * Construye el resguardo (texto + HTML) de una predicción de eliminatorias.
 *
 * Resolución de los slots de bracket (W##/RU##): desde Octavos en adelante los
 * partidos NO tienen `home_team_id`/`away_team_id` en la tabla `matches` (son
 * NULL hasta que se juega el torneo); en su lugar guardan `home_slot`/`away_slot`
 * con códigos tipo "W74" (ganador del partido 74) o "RU101" (perdedor del 101).
 *
 * Antes el correo mostraba esos códigos crudos. Ahora el backend replica la
 * cascada que ya hacía el frontend Kotlin (SupabasePorraRepository.parseSlotSource
 * + Components.computeKnockoutLosers): un slot "W{n}" se resuelve al equipo que el
 * propio usuario predijo como ganador del partido n, y "RU{n}" al perdedor de ese
 * partido (el otro equipo menos el ganador predicho), de forma recursiva.
 *
 * Todos los equipos alcanzables por la cascada ya están en el mapa `teams`
 * (los ganadores son `predicted_winner_team_id`; los perdedores de Ronda de 32 son
 * `home_team_id`/`away_team_id` reales), por lo que no hace falta ninguna consulta
 * adicional.
 */
export async function buildKnockoutReceipt(
  client: SupabaseClient,
  predictions: KnockoutMatchPredictionInput[],
): Promise<EmailReceipt> {
  const teamIds = new Set<string>(predictions.map((p) => p.predicted_winner_team_id));
  const matchIds = predictions.map((p) => p.match_id);

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

  // Índices por número de partido para resolver la cascada de slots.
  const winnerByNum = new Map<number, string>();
  for (const prediction of predictions) {
    const num = matches.get(prediction.match_id)?.match_number;
    if (num != null) winnerByNum.set(num, prediction.predicted_winner_team_id);
  }
  const matchByNum = new Map<number, MatchInfo>();
  for (const match of matches.values()) {
    if (match.match_number != null) matchByNum.set(match.match_number, match);
  }

  // "W{n}" -> ganador predicho del partido n; "RU{n}" -> perdedor predicho del partido n.
  function resolveSlotTeamId(slot: string | null, seen: Set<number>): string | null {
    if (!slot) return null;
    const trimmed = slot.trim();

    const winnerMatch = /^W(\d+)$/.exec(trimmed);
    if (winnerMatch) {
      return winnerByNum.get(Number(winnerMatch[1])) ?? null;
    }

    const loserMatch = /^RU(\d+)$/.exec(trimmed);
    if (loserMatch) {
      const num = Number(loserMatch[1]);
      if (seen.has(num)) return null; // guard anti-ciclo
      seen.add(num);
      const winner = winnerByNum.get(num);
      const sourceMatch = matchByNum.get(num);
      if (!winner || !sourceMatch) return null;
      const homeId = sourceMatch.home_team_id ?? resolveSlotTeamId(sourceMatch.home_slot, seen);
      const awayId = sourceMatch.away_team_id ?? resolveSlotTeamId(sourceMatch.away_slot, seen);
      if (homeId && homeId !== winner) return homeId;
      if (awayId && awayId !== winner) return awayId;
      return null;
    }

    return null; // slot no reconocido (o un equipo ya resuelto)
  }

  function sideTeamId(match: MatchInfo | undefined, side: "home" | "away"): string | null {
    if (!match) return null;
    const direct = side === "home" ? match.home_team_id : match.away_team_id;
    if (direct) return direct;
    return resolveSlotTeamId(side === "home" ? match.home_slot : match.away_slot, new Set<number>());
  }

  const orderedMatches = [...predictions].sort((a, b) => {
    return (matches.get(a.match_id)?.match_number ?? 999) - (matches.get(b.match_id)?.match_number ?? 999);
  });

  const matchTextLines = orderedMatches.map((prediction) => {
    const match = matches.get(prediction.match_id);
    const home = teamName(sideTeamId(match, "home"), teams, "Por definir");
    const away = teamName(sideTeamId(match, "away"), teams, "Por definir");
    const winner = teamName(prediction.predicted_winner_team_id, teams);
    const number = match?.match_number ? `Partido ${match.match_number}` : "Partido";
    const phase = match?.phase ? PHASE_LABELS[match.phase] ?? match.phase : "";
    return `${number} - ${phase}: ${home} ${prediction.home_goals} - ${prediction.away_goals} ${away}. Avanza: ${winner}`;
  });

  const matchRows = orderedMatches.map((prediction) => {
    const match = matches.get(prediction.match_id);
    const home = teamName(sideTeamId(match, "home"), teams, "Por definir");
    const away = teamName(sideTeamId(match, "away"), teams, "Por definir");
    const winner = teamName(prediction.predicted_winner_team_id, teams);
    return `<tr>` +
      `<td>${escapeHtml(match?.match_number?.toString() ?? "-")}</td>` +
      `<td>${escapeHtml(PHASE_LABELS[match?.phase ?? ""] ?? match?.phase ?? "")}</td>` +
      `<td>${escapeHtml(home)}</td>` +
      `<td>${prediction.home_goals} - ${prediction.away_goals}</td>` +
      `<td>${escapeHtml(away)}</td>` +
      `<td>${escapeHtml(winner)}</td>` +
      `</tr>`;
  }).join("");

  const html = `
    <table style="border-collapse:collapse;width:100%">
      <thead>
        <tr style="background:#f1f5f9">
          <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Partido</th>
          <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Ronda</th>
          <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Local</th>
          <th style="padding:8px;text-align:center;border-bottom:2px solid #e2e8f0">Marcador</th>
          <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Visitante</th>
          <th style="padding:8px;text-align:left;border-bottom:2px solid #e2e8f0">Avanza</th>
        </tr>
      </thead>
      <tbody>${matchRows}</tbody>
    </table>
  `;

  const text = matchTextLines.join("\n");

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

/**
 * Envía el correo de confirmación al participante y una copia al admin.
 * Reutilizado tanto por el alta inicial (submit-knockouts) como por el reenvío
 * del correo corregido (resend-knockout-receipts).
 */
export async function sendConfirmationEmail(
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

/**
 * Envía SOLO la copia al admin del resguardo de un participante (NO envía nada al
 * participante). Sirve para reponer copias admin que quedaron pendientes (p.ej. por
 * límite de cuota del proveedor) sin volver a notificar a los participantes.
 */
export async function sendAdminCopyOnly(
  client: SupabaseClient,
  participantId: string,
  name: string,
  email: string,
  receipt: EmailReceipt,
): Promise<void> {
  const template = "knockouts_submission_admin_copy";
  const apiKey = Deno.env.get("RESEND_API_KEY");
  if (!apiKey) {
    await logEmail(client, participantId, email, template, "failed", undefined, "Missing RESEND_API_KEY");
    return;
  }

  const safeName = escapeHtml(name);
  const from = Deno.env.get("RESEND_FROM_EMAIL") ?? "PorraWeb <onboarding@resend.dev>";
  const adminReceiptEmail = Deno.env.get("ADMIN_RECEIPT_EMAIL") ?? "hernancit1993@gmail.com";
  const adminSubject = "Copia admin - Confirmacion de prediccion de eliminatorias - PorraWeb";
  const adminText = `Resguardo admin de prediccion enviada por ${name} <${email}>.\n\n${receipt.text}`;
  const adminHtml = `
    <p><strong>Resguardo admin de prediccion enviada.</strong></p>
    <p>Participante: ${safeName} &lt;${escapeHtml(email)}&gt;</p>
    ${receipt.html}
  `;

  await sendEmailWithLog(client, apiKey, participantId, from, adminReceiptEmail, adminSubject, adminText, adminHtml, template);
}
