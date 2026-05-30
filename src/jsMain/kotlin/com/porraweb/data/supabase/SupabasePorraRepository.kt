package com.porraweb.data.supabase

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.porraweb.data.i18n.TeamNamesEs
import com.porraweb.data.mock.MockPorraRepository
import com.porraweb.domain.model.AdminSettings
import com.porraweb.domain.model.DashboardSummary
import com.porraweb.domain.model.GroupMatch
import com.porraweb.domain.model.KnockoutMatch
import com.porraweb.domain.model.MatchResult
import com.porraweb.domain.model.PaymentParticipant
import com.porraweb.domain.model.RankingEntry
import com.porraweb.domain.model.Rule
import com.porraweb.domain.model.ScoringRule
import com.porraweb.domain.model.Team
import com.porraweb.domain.model.TournamentGroup
import com.porraweb.domain.repository.PorraRepository
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

class SupabasePorraRepository(private val config: SupabaseConfig) : PorraRepository {
    private val scope = MainScope()
    private var state by mutableStateOf(RepositoryState.from(MockPorraRepository))

    init {
        scope.launch {
            runCatching { loadRemoteData() }
                .onFailure { error -> println("Supabase load failed: ${error.message}") }
        }
    }

    override fun participantRules(): List<Rule> = MockPorraRepository.participantRules()

    override fun scoringRules(): List<ScoringRule> = MockPorraRepository.scoringRules()

    override fun teams(): List<Team> = state.teams

    override fun groups(): List<TournamentGroup> = state.groups

    override fun groupMatches(): List<GroupMatch> = state.groupMatches

    override fun knockoutMatches(): List<KnockoutMatch> = state.knockoutMatches

    override fun dashboardSummary(): DashboardSummary = state.dashboardSummary

    override fun ranking(): List<RankingEntry> = state.ranking

    override fun latestResults(): List<MatchResult> = state.latestResults

    override fun pendingParticipants(): List<PaymentParticipant> = MockPorraRepository.pendingParticipants()

    override fun adminSettings(): AdminSettings = MockPorraRepository.adminSettings()

    private suspend fun loadRemoteData() {
        val teams = fetchRows("teams?select=id,fifa_code,name&order=name.asc")
            .mapNotNull { row ->
                val fifaCode = text(row.fifa_code) ?: return@mapNotNull null
                Team(
                    id = text(row.id) ?: return@mapNotNull null,
                    name = TeamNamesEs.es(fifaCode),
                )
            }
        val teamsById = teams.associateBy { it.id }

        val groupRows = fetchRows("tournament_groups?select=id,code,name&order=code.asc")
            .mapNotNull { row ->
                DbGroup(
                    id = text(row.id) ?: return@mapNotNull null,
                    code = text(row.code) ?: return@mapNotNull null,
                )
            }
        val groupCodesById = groupRows.associate { it.id to it.code }

        val groupTeams = fetchRows("group_teams?select=group_id,team_id")
            .mapNotNull { row ->
                DbGroupTeam(
                    groupId = text(row.group_id) ?: return@mapNotNull null,
                    teamId = text(row.team_id) ?: return@mapNotNull null,
                )
            }
            .groupBy { it.groupId }

        val matches = fetchRows("matches?select=id,match_number,phase,group_id,home_team_id,away_team_id,home_slot,away_slot,status&order=match_number.asc")
            .mapNotNull { row ->
                DbMatch(
                    id = text(row.id) ?: return@mapNotNull null,
                    matchNumber = intOrNull(row.match_number),
                    phase = text(row.phase) ?: return@mapNotNull null,
                    groupId = text(row.group_id),
                    homeTeamId = text(row.home_team_id),
                    awayTeamId = text(row.away_team_id),
                    homeSlot = text(row.home_slot),
                    awaySlot = text(row.away_slot),
                    status = text(row.status) ?: "scheduled",
                )
            }

        val groupMatches = matches
            .filter { it.phase == "group" && it.groupId != null }
            .map { match -> match.toGroupMatch(teamsById, groupCodesById) }

        val groupMatchesByGroup = groupMatches.groupBy { it.groupCode }
        val groups = groupRows.map { group ->
            TournamentGroup(
                id = group.id,
                code = group.code,
                teams = groupTeams[group.id]
                    .orEmpty()
                    .mapNotNull { teamsById[it.teamId] }
                    .sortedBy { it.name },
                matches = groupMatchesByGroup[group.code].orEmpty(),
            )
        }

        state = state.copy(
            teams = teams,
            groups = groups,
            groupMatches = groupMatches,
            knockoutMatches = matches
                .filter { it.phase != "group" }
                .map { it.toKnockoutMatch(teamsById, teams) },
            dashboardSummary = loadDashboardSummary(),
            ranking = loadRanking(),
            latestResults = loadLatestResults(),
        )
    }

    private suspend fun loadDashboardSummary(): DashboardSummary {
        val row = fetchRows("public_dashboard_summary?select=*").firstOrNull()
        return DashboardSummary(
            approvedParticipants = intOrNull(row?.approved_participants) ?: 0,
            updatedMatches = intOrNull(row?.updated_matches) ?: 0,
            currentPhase = text(row?.current_phase) ?: "Fase de grupos 2026",
            participationPriceEur = text(row?.participation_price_eur) ?: "5",
            prizePotEur = text(row?.prize_pot_eur) ?: "0",
        )
    }

    private suspend fun loadRanking(): List<RankingEntry> = fetchRows("public_ranking?select=*&order=position.asc")
        .mapNotNull { row ->
            RankingEntry(
                position = intOrNull(row.position) ?: return@mapNotNull null,
                participantName = text(row.participant_name) ?: return@mapNotNull null,
                groupPoints = intOrNull(row.group_points) ?: 0,
                knockoutPoints = intOrNull(row.knockout_points) ?: 0,
            )
        }

    private suspend fun loadLatestResults(): List<MatchResult> = fetchRows("public_latest_results?select=*&limit=6")
        .mapNotNull { row ->
            val homeFifaCode = text(row.home_fifa_code)
            val awayFifaCode = text(row.away_fifa_code)
            MatchResult(
                homeTeam = homeFifaCode?.let { TeamNamesEs.es(it) } ?: text(row.home_team) ?: return@mapNotNull null,
                score = text(row.score) ?: "-",
                awayTeam = awayFifaCode?.let { TeamNamesEs.es(it) } ?: text(row.away_team) ?: return@mapNotNull null,
                status = text(row.status) ?: "Pendiente",
            )
        }

    private suspend fun fetchRows(path: String): List<dynamic> {
        val response = window.fetch("${config.supabaseUrl}/rest/v1/$path", requestInit()).await()
        if (!response.ok) error("${response.status} ${response.statusText}")

        val payload: dynamic = response.json().await()
        val rows = mutableListOf<dynamic>()
        val length = (payload.length as? Number)?.toInt() ?: 0
        for (index in 0 until length) rows.add(payload[index])
        return rows
    }

    private fun requestInit(): dynamic {
        val headers = js("({})")
        headers["apikey"] = config.publishableKey
        headers["Authorization"] = "Bearer ${config.publishableKey}"
        headers["Accept"] = "application/json"

        val init = js("({})")
        init.method = "GET"
        init.headers = headers
        return init
    }
}

data class SupabaseConfig(
    val supabaseUrl: String,
    val publishableKey: String,
) {
    companion object {
        fun fromWindow(): SupabaseConfig? {
            val config: dynamic = js("window.PORRAWEB_CONFIG || ({})")
            val supabaseUrl = text(config.supabaseUrl)?.trimEnd('/')
            val publishableKey = text(config.supabasePublishableKey)

            if (supabaseUrl.isNullOrBlank() || publishableKey.isNullOrBlank()) return null

            return SupabaseConfig(
                supabaseUrl = supabaseUrl,
                publishableKey = publishableKey,
            )
        }
    }
}

object PorraRepositoryFactory {
    fun create(): PorraRepository = SupabaseConfig.fromWindow()?.let { SupabasePorraRepository(it) } ?: MockPorraRepository
}

private data class RepositoryState(
    val teams: List<Team>,
    val groups: List<TournamentGroup>,
    val groupMatches: List<GroupMatch>,
    val knockoutMatches: List<KnockoutMatch>,
    val dashboardSummary: DashboardSummary,
    val ranking: List<RankingEntry>,
    val latestResults: List<MatchResult>,
) {
    companion object {
        fun from(repository: PorraRepository): RepositoryState = RepositoryState(
            teams = repository.teams(),
            groups = repository.groups(),
            groupMatches = repository.groupMatches(),
            knockoutMatches = repository.knockoutMatches(),
            dashboardSummary = repository.dashboardSummary(),
            ranking = repository.ranking(),
            latestResults = repository.latestResults(),
        )
    }
}

private data class DbGroup(
    val id: String,
    val code: String,
)

private data class DbGroupTeam(
    val groupId: String,
    val teamId: String,
)

private data class DbMatch(
    val id: String,
    val matchNumber: Int?,
    val phase: String,
    val groupId: String?,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val homeSlot: String?,
    val awaySlot: String?,
    val status: String,
) {
    fun toGroupMatch(teamsById: Map<String, Team>, groupCodesById: Map<String, String>): GroupMatch {
        val homeTeam = teamFrom(homeTeamId, homeSlot, teamsById, "home")
        val awayTeam = teamFrom(awayTeamId, awaySlot, teamsById, "away")
        val groupCode = groupId?.let { groupCodesById[it] } ?: homeSlot?.take(1) ?: awaySlot?.take(1) ?: "?"

        return GroupMatch(
            id = id,
            groupCode = groupCode,
            label = "Grupo $groupCode - Partido ${matchNumber ?: ""}",
            homeTeam = homeTeam,
            awayTeam = awayTeam,
        )
    }

    fun toKnockoutMatch(teamsById: Map<String, Team>, teams: List<Team>): KnockoutMatch {
        val home = teamFrom(homeTeamId, homeSlot, teamsById, "home")
        val away = teamFrom(awayTeamId, awaySlot, teamsById, "away")
        return KnockoutMatch(
            id = id,
            phase = phase,
            label = "Partido ${matchNumber ?: ""}",
            homeSlot = home.name,
            awaySlot = away.name,
            options = if (homeTeamId != null && awayTeamId != null) listOf(home, away) else teams,
        )
    }
}

private fun DbMatch.teamFrom(
    teamId: String?,
    slot: String?,
    teamsById: Map<String, Team>,
    side: String,
): Team = teamId?.let { teamsById[it] } ?: Team(
    id = "$id-$side",
    name = slot ?: "Por definir",
)

private fun phaseLabel(phase: String): String = when (phase) {
    "round_32" -> "Ronda de 32"
    "round_16" -> "Octavos de final"
    "quarter_final" -> "Cuartos de final"
    "semi_final" -> "Semifinales"
    "third_place" -> "Tercer puesto"
    "final" -> "Final"
    else -> phase
}

private fun text(value: dynamic): String? = value?.toString()

private fun intOrNull(value: dynamic): Int? = when (value) {
    null -> null
    else -> (value as Number).toInt()
}
