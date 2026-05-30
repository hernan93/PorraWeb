import { createClient } from "https://esm.sh/@supabase/supabase-js@2.48.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-sync-secret",
};

type LocalizedText = {
  Locale?: string;
  Description?: string;
};

type FifaTeam = {
  IdTeam?: string | null;
  IdCountry?: string | null;
  TeamName?: LocalizedText[];
  Abbreviation?: string | null;
  ShortClubName?: string | null;
};

type FifaStadium = {
  IdStadium?: string | null;
  Name?: LocalizedText[];
  CityName?: LocalizedText[];
  IdCountry?: string | null;
};

type FifaMatch = {
  IdMatch: string;
  IdStage?: string | null;
  IdGroup?: string | null;
  MatchNumber?: number | null;
  Date?: string | null;
  LocalDate?: string | null;
  StageName?: LocalizedText[];
  GroupName?: LocalizedText[];
  Home?: FifaTeam | null;
  Away?: FifaTeam | null;
  HomeTeamScore?: number | null;
  AwayTeamScore?: number | null;
  HomeTeamPenaltyScore?: number | null;
  AwayTeamPenaltyScore?: number | null;
  Winner?: string | null;
  MatchStatus?: number | null;
  MatchReportUrl?: string | null;
  PlaceHolderA?: string | null;
  PlaceHolderB?: string | null;
  Stadium?: FifaStadium | null;
};

type FifaMatchesResponse = {
  ContinuationToken?: string | null;
  Results?: FifaMatch[];
};

type UpsertSummary = {
  matchesSeen: number;
  matchesUpserted: number;
  resultsUpserted: number;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = requiredEnv("SUPABASE_URL");
  const serviceRoleKey = supabaseSecretKey();
  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false },
  });

  const authResponse = await authorizeSync(req, supabase);
  if (authResponse) return authResponse;

  const sourceUrl = buildFifaUrl().toString();
  const startedAt = new Date().toISOString();
  const logId = await createSyncLog(supabase, sourceUrl, startedAt);

  try {
    const { endpoint, payload, matches } = await fetchFifaMatches();
    const payloadHash = await sha256(JSON.stringify(payload));

    await insertRawPayload(supabase, endpoint, payloadHash, payload);
    const summary = await upsertFifaData(supabase, matches, endpoint, payloadHash);
    const scoreSummary = summary.resultsUpserted > 0 ? await recalculateScores(supabase) : null;

    await updateSyncLog(supabase, logId, {
      status: "success",
      finished_at: new Date().toISOString(),
      matches_seen: summary.matchesSeen,
      matches_upserted: summary.matchesUpserted,
      results_upserted: summary.resultsUpserted,
    });

    return jsonResponse({ ok: true, payloadHash, ...summary, scoreSummary });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    await updateSyncLog(supabase, logId, {
      status: "failed",
      finished_at: new Date().toISOString(),
      error_message: message,
    });
    return jsonResponse({ ok: false, error: message }, 500);
  }
});

async function authorizeSync(req: Request, supabase: ReturnType<typeof createClient>): Promise<Response | null> {
  const expectedSecret = Deno.env.get("SYNC_FIFA_SECRET");
  if (expectedSecret && req.headers.get("x-sync-secret") === expectedSecret) {
    return null;
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) {
    return jsonResponse({ error: "Missing authorization header" }, 401);
  }

  const token = authHeader.replace("Bearer ", "");
  const { data: { user }, error: authError } = await supabase.auth.getUser(token);
  if (authError || !user) {
    return jsonResponse({ error: "Invalid or expired token" }, 401);
  }

  const { data: adminUser, error: adminLookupError } = await supabase
    .from("admin_users")
    .select("user_id")
    .eq("user_id", user.id)
    .maybeSingle();

  if (adminLookupError || !adminUser) {
    return jsonResponse({ error: "Not authorized. Admin access required." }, 403);
  }

  return null;
}

async function fetchFifaMatches() {
  const firstUrl = buildFifaUrl();
  const pages: FifaMatchesResponse[] = [];
  const matches: FifaMatch[] = [];
  let continuationToken: string | null = null;

  for (let page = 0; page < 10; page += 1) {
    const url = buildFifaUrl(continuationToken);
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`FIFA API failed with ${response.status}: ${await response.text()}`);
    }

    const payload = await response.json() as FifaMatchesResponse;
    pages.push(payload);
    matches.push(...(payload.Results ?? []));

    continuationToken = payload.ContinuationToken ?? null;
    if (!continuationToken || matches.length >= 104) break;
  }

  return {
    endpoint: firstUrl.toString(),
    payload: { pages },
    matches: matches.slice(0, 104),
  };
}

function buildFifaUrl(continuationToken?: string | null): URL {
  const baseUrl = Deno.env.get("FIFA_API_BASE_URL") ?? "https://api.fifa.com/api/v3";
  const language = Deno.env.get("FIFA_API_LANGUAGE") ?? "en";
  const competitionId = Deno.env.get("FIFA_COMPETITION_ID") ?? "17";
  const seasonId = Deno.env.get("FIFA_SEASON_ID") ?? "285023";

  const url = new URL(`${baseUrl}/calendar/matches`);
  url.searchParams.set("language", language);
  url.searchParams.set("count", "104");
  url.searchParams.set("idCompetition", competitionId);
  url.searchParams.set("idSeason", seasonId);
  if (continuationToken) {
    url.searchParams.set("continuationToken", continuationToken);
  }
  return url;
}

async function upsertFifaData(
  supabase: ReturnType<typeof createClient>,
  matches: FifaMatch[],
  endpoint: string,
  payloadHash: string,
): Promise<UpsertSummary> {
  const checkedAt = new Date().toISOString();

  const teams = uniqueBy(
    matches.flatMap((match) => [match.Home, match.Away])
      .map(toTeamRow)
      .filter(Boolean),
    (team) => team.fifa_code,
  );
  if (teams.length > 0) {
    await must(
      supabase.from("teams").upsert(teams, { onConflict: "fifa_code" }),
      "upsert teams",
    );
  }

  const groups = uniqueBy(
    matches.map(toGroupRow).filter(Boolean),
    (group) => group.code,
  );
  if (groups.length > 0) {
    await must(
      supabase.from("tournament_groups").upsert(groups, { onConflict: "code" }),
      "upsert tournament groups",
    );
  }

  const venues = uniqueBy(
    matches.map(toVenueRow).filter(Boolean),
    (venue) => venue.fifa_stadium_id,
  );
  if (venues.length > 0) {
    await must(
      supabase.from("venues").upsert(venues, { onConflict: "fifa_stadium_id" }),
      "upsert venues",
    );
  }

  const teamMap = await loadMap(supabase, "teams", "fifa_team_id", teams.map((team) => team.fifa_team_id));
  const groupMap = await loadMap(supabase, "tournament_groups", "fifa_group_id", groups.map((group) => group.fifa_group_id));
  const venueMap = await loadMap(supabase, "venues", "fifa_stadium_id", venues.map((venue) => venue.fifa_stadium_id));

  const matchRows = matches.map((match) => toMatchRow(match, teamMap, groupMap, venueMap, endpoint, checkedAt, payloadHash));
  if (matchRows.length > 0) {
    await must(
      supabase.from("matches").upsert(matchRows, { onConflict: "fifa_match_id" }),
      "upsert matches",
    );
  }

  const matchMap = await loadMap(supabase, "matches", "fifa_match_id", matches.map((match) => match.IdMatch));
  const groupTeams = uniqueBy(
    matches.flatMap((match) => toGroupTeamRows(match, teamMap, groupMap)),
    (row) => `${row.group_id}:${row.team_id}`,
  );
  if (groupTeams.length > 0) {
    await must(
      supabase.from("group_teams").upsert(groupTeams, { onConflict: "group_id,team_id", ignoreDuplicates: true }),
      "upsert group teams",
    );
  }

  const manualOverrideMatchIds = await loadManualOverrideMatchIds(supabase, Array.from(matchMap.values()));
  const resultRows = matches
    .map((match) => toResultRow(match, matchMap, teamMap, checkedAt, payloadHash))
    .filter((row) => row && !manualOverrideMatchIds.has(row.match_id));

  if (resultRows.length > 0) {
    await must(
      supabase.from("match_results").upsert(resultRows, { onConflict: "match_id" }),
      "upsert match results",
    );
  }

  return {
    matchesSeen: matches.length,
    matchesUpserted: matchRows.length,
    resultsUpserted: resultRows.length,
  };
}

function toTeamRow(team?: FifaTeam | null) {
  if (!team?.IdTeam || !team.Abbreviation) return null;
  return {
    fifa_team_id: team.IdTeam,
    fifa_code: team.Abbreviation,
    country_code: team.IdCountry ?? team.Abbreviation,
    name: description(team.TeamName) ?? team.ShortClubName ?? team.Abbreviation,
    short_name: team.ShortClubName ?? team.Abbreviation,
    updated_at: new Date().toISOString(),
  };
}

function toGroupRow(match: FifaMatch) {
  if (!match.IdGroup) return null;
  const name = description(match.GroupName);
  const code = name?.match(/Group\s+([A-L])/i)?.[1];
  if (!code || !name) return null;
  return {
    fifa_group_id: match.IdGroup,
    code,
    name,
    updated_at: new Date().toISOString(),
  };
}

function toVenueRow(match: FifaMatch) {
  const stadium = match.Stadium;
  if (!stadium?.IdStadium) return null;
  return {
    fifa_stadium_id: stadium.IdStadium,
    name: description(stadium.Name) ?? "Unknown venue",
    city_name: description(stadium.CityName),
    country_code: stadium.IdCountry,
    updated_at: new Date().toISOString(),
  };
}

function toMatchRow(
  match: FifaMatch,
  teamMap: Map<string, string>,
  groupMap: Map<string, string>,
  venueMap: Map<string, string>,
  endpoint: string,
  checkedAt: string,
  payloadHash: string,
) {
  return {
    fifa_match_id: match.IdMatch,
    match_number: match.MatchNumber,
    phase: phaseFromMatchNumber(match.MatchNumber),
    fifa_stage_id: match.IdStage,
    fifa_group_id: match.IdGroup,
    group_id: match.IdGroup ? groupMap.get(match.IdGroup) ?? null : null,
    home_team_id: match.Home?.IdTeam ? teamMap.get(match.Home.IdTeam) ?? null : null,
    away_team_id: match.Away?.IdTeam ? teamMap.get(match.Away.IdTeam) ?? null : null,
    venue_id: match.Stadium?.IdStadium ? venueMap.get(match.Stadium.IdStadium) ?? null : null,
    home_slot: match.PlaceHolderA,
    away_slot: match.PlaceHolderB,
    kickoff_at: match.Date,
    status: statusFromFifaMatch(match),
    stage_name: description(match.StageName),
    group_name: description(match.GroupName),
    source_url: endpoint,
    source_checked_at: checkedAt,
    fifa_payload_hash: payloadHash,
    updated_at: checkedAt,
  };
}

function toGroupTeamRows(
  match: FifaMatch,
  teamMap: Map<string, string>,
  groupMap: Map<string, string>,
) {
  if (!match.IdGroup || phaseFromMatchNumber(match.MatchNumber) !== "group") return [];
  const groupId = groupMap.get(match.IdGroup);
  if (!groupId) return [];

  return [match.Home, match.Away]
    .map((team) => team?.IdTeam ? teamMap.get(team.IdTeam) : null)
    .filter(Boolean)
    .map((teamId) => ({ group_id: groupId, team_id: teamId }));
}

function toResultRow(
  match: FifaMatch,
  matchMap: Map<string, string>,
  teamMap: Map<string, string>,
  checkedAt: string,
  payloadHash: string,
) {
  if (match.HomeTeamScore === null || match.HomeTeamScore === undefined) return null;
  if (match.AwayTeamScore === null || match.AwayTeamScore === undefined) return null;

  const matchId = matchMap.get(match.IdMatch);
  if (!matchId) return null;

  return {
    match_id: matchId,
    home_goals: match.HomeTeamScore,
    away_goals: match.AwayTeamScore,
    winner_team_id: match.Winner ? teamMap.get(match.Winner) ?? null : null,
    home_penalty_goals: match.HomeTeamPenaltyScore,
    away_penalty_goals: match.AwayTeamPenaltyScore,
    result_source: "fifa",
    is_manual_override: false,
    source_checked_at: checkedAt,
    fifa_payload_hash: payloadHash,
    updated_at: checkedAt,
  };
}

function phaseFromMatchNumber(matchNumber?: number | null) {
  if (!matchNumber || matchNumber <= 72) return "group";
  if (matchNumber <= 88) return "round_32";
  if (matchNumber <= 96) return "round_16";
  if (matchNumber <= 100) return "quarter_final";
  if (matchNumber <= 102) return "semi_final";
  if (matchNumber === 103) return "third_place";
  return "final";
}

function statusFromFifaMatch(match: FifaMatch) {
  if (match.HomeTeamScore === null || match.HomeTeamScore === undefined) return "scheduled";
  if (match.MatchStatus === 0) return "finished";
  return "in_progress";
}

async function loadMap(
  supabase: ReturnType<typeof createClient>,
  table: string,
  keyColumn: string,
  keys: Array<string | null | undefined>,
) {
  const validKeys = Array.from(new Set(keys.filter(Boolean))) as string[];
  const map = new Map<string, string>();
  if (validKeys.length === 0) return map;

  const { data, error } = await supabase
    .from(table)
    .select(`id, ${keyColumn}`)
    .in(keyColumn, validKeys);

  if (error) throw new Error(`load ${table}: ${error.message}`);

  for (const row of data ?? []) {
    map.set(row[keyColumn], row.id);
  }
  return map;
}

async function loadManualOverrideMatchIds(supabase: ReturnType<typeof createClient>, matchIds: string[]) {
  const manualOverrideMatchIds = new Set<string>();
  if (matchIds.length === 0) return manualOverrideMatchIds;

  const { data, error } = await supabase
    .from("match_results")
    .select("match_id")
    .in("match_id", matchIds)
    .eq("is_manual_override", true);

  if (error) throw new Error(`load manual overrides: ${error.message}`);
  for (const row of data ?? []) manualOverrideMatchIds.add(row.match_id);
  return manualOverrideMatchIds;
}

async function createSyncLog(supabase: ReturnType<typeof createClient>, sourceUrl: string, startedAt: string) {
  const { data, error } = await supabase
    .from("fifa_sync_logs")
    .insert({ status: "running", source_url: sourceUrl, started_at: startedAt })
    .select("id")
    .single();

  if (error) throw new Error(`create sync log: ${error.message}`);
  return data.id as string;
}

async function updateSyncLog(supabase: ReturnType<typeof createClient>, id: string, patch: Record<string, unknown>) {
  const { error } = await supabase.from("fifa_sync_logs").update(patch).eq("id", id);
  if (error) console.error(`update sync log failed: ${error.message}`);
}

async function insertRawPayload(
  supabase: ReturnType<typeof createClient>,
  endpoint: string,
  payloadHash: string,
  payload: unknown,
) {
  await must(
    supabase.from("fifa_raw_payloads").insert({ endpoint, payload_hash: payloadHash, payload }),
    "insert raw FIFA payload",
  );
}

async function recalculateScores(supabase: ReturnType<typeof createClient>) {
  const { data, error } = await supabase.rpc("recalculate_scores", { p_source: "fifa_sync" });
  if (error) throw new Error(`recalculate scores: ${error.message}`);
  return data;
}

async function must<T>(promise: PromiseLike<{ data: T; error: { message: string } | null }>, label: string) {
  const { data, error } = await promise;
  if (error) throw new Error(`${label}: ${error.message}`);
  return data;
}

function description(values?: LocalizedText[]) {
  return values?.find((value) => value.Locale === "en-GB")?.Description ?? values?.[0]?.Description ?? null;
}

function uniqueBy<T>(items: T[], key: (item: T) => string | null | undefined) {
  const seen = new Set<string>();
  const result: T[] = [];

  for (const item of items) {
    const itemKey = key(item);
    if (!itemKey || seen.has(itemKey)) continue;
    seen.add(itemKey);
    result.push(item);
  }

  return result;
}

async function sha256(value: string) {
  const bytes = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(hash)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function requiredEnv(name: string) {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

function supabaseSecretKey() {
  const secretKeys = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (secretKeys) {
    const parsed = JSON.parse(secretKeys) as Record<string, string>;
    const firstKey = parsed.default ?? Object.values(parsed)[0];
    if (firstKey) return firstKey;
  }

  return requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
