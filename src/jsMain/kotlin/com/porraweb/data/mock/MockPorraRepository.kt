package com.porraweb.data.mock

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

object MockPorraRepository : PorraRepository {
    private val groupTeams: Map<String, List<Team>> = linkedMapOf(
        "A" to listOf(team("mex", "México"), team("rsa", "Sudáfrica"), team("kor", "Corea del Sur"), team("cze", "República Checa")),
        "B" to listOf(team("can", "Canadá"), team("bih", "Bosnia y Herzegovina"), team("qat", "Qatar"), team("sui", "Suiza")),
        "C" to listOf(team("bra", "Brasil"), team("mar", "Marruecos"), team("hai", "Haití"), team("sco", "Escocia")),
        "D" to listOf(team("usa", "Estados Unidos"), team("par", "Paraguay"), team("aus", "Australia"), team("tur", "Turquía")),
        "E" to listOf(team("ger", "Alemania"), team("cuw", "Curazao"), team("civ", "Costa de Marfil"), team("ecu", "Ecuador")),
        "F" to listOf(team("ned", "Países Bajos"), team("jpn", "Japón"), team("swe", "Suecia"), team("tun", "Túnez")),
        "G" to listOf(team("bel", "Bélgica"), team("egy", "Egipto"), team("irn", "Irán"), team("nzl", "Nueva Zelanda")),
        "H" to listOf(team("esp", "España"), team("cpv", "Cabo Verde"), team("ksa", "Arabia Saudita"), team("uru", "Uruguay")),
        "I" to listOf(team("fra", "Francia"), team("sen", "Senegal"), team("irq", "Irak"), team("nor", "Noruega")),
        "J" to listOf(team("arg", "Argentina"), team("alg", "Argelia"), team("aut", "Austria"), team("jor", "Jordania")),
        "K" to listOf(team("por", "Portugal"), team("cod", "RD Congo"), team("uzb", "Uzbekistán"), team("col", "Colombia")),
        "L" to listOf(team("eng", "Inglaterra"), team("cro", "Croacia"), team("gha", "Ghana"), team("pan", "Panamá")),
    )

    private val tournamentGroups: List<TournamentGroup> = groupTeams.map { (code, teams) ->
        TournamentGroup(
            code = code,
            teams = teams,
            matches = createGroupMatches(code, teams),
        )
    }

    override fun participantRules(): List<Rule> = listOf(
        Rule("1", "Completa fase de grupos con marcadores, orden final de los 12 grupos y los 8 mejores terceros."),
        Rule("2", "La participación cuesta 5 EUR. Puedes pagar por Bizum o en efectivo."),
        Rule("3", "El administrador debe aprobar tu pago para que aparezcas en el ranking."),
        Rule("4", "Clasifican a ronda de 32 los dos primeros de cada grupo y los 8 mejores terceros."),
        Rule("5", "Cuando se conozcan los 32 clasificados reales, se abrirá el formulario de eliminatorias."),
        Rule("6", "La fase final incluye ronda de 32, octavos, cuartos, semifinales, tercer puesto y final."),
        Rule("7", "Gana quien acumule más puntos entre fase de grupos y eliminatorias."),
    )

    override fun scoringRules(): List<ScoringRule> = listOf(
        ScoringRule(
            title = "Fase de grupos 2026",
            items = listOf(
                "Resultado correcto por partido: +1 punto",
                "Marcador exacto por partido: +2 puntos adicionales",
                "Posición exacta de un equipo en su grupo: +2 puntos",
                "Orden completo de un grupo perfecto: +2 puntos extra",
                "Equipo clasificado a ronda de 32: +1 punto. Incluye primeros, segundos y mejores terceros.",
            ),
        ),
        ScoringRule(
            title = "Eliminatorias 2026",
            items = listOf(
                "Resultado correcto por partido: +3 puntos",
                "Marcador exacto por partido: 5 puntos en total",
                "Clasificado desde ronda de 32: +4 puntos por equipo",
                "Clasificado a cuartos: +5 puntos por equipo",
                "Clasificado a semifinales: +6 puntos por equipo",
                "Clasificado a final o tercer puesto: +7 puntos por equipo",
                "4o puesto: +8, 3er puesto: +9, subcampeón: +10, campeón: +20",
            ),
        ),
        ScoringRule(
            title = "Pago",
            items = listOf(
                "5 EUR por participante",
                "Bizum o efectivo",
                "Ranking solo para aprobados",
            ),
        ),
    )

    override fun teams(): List<Team> = tournamentGroups.flatMap { it.teams }

    override fun groups(): List<TournamentGroup> = tournamentGroups

    override fun groupMatches(): List<GroupMatch> = tournamentGroups.flatMap { it.matches }

    override fun knockoutMatches(): List<KnockoutMatch> {
        val classifiedTeams = knockoutTeams()
        val round32 = classifiedTeams.chunked(2).mapIndexed { index, pair ->
            KnockoutMatch(
                id = "r32-${index + 1}",
                phase = "Ronda de 32",
                label = "Cruce ${index + 1}",
                homeSlot = pair[0].name,
                awaySlot = pair[1].name,
                options = pair,
            )
        }

        val round16 = createAggregateKnockoutMatches(
            idPrefix = "r16",
            phase = "Octavos de final",
            labelPrefix = "Octavos",
            sourceTeams = classifiedTeams,
            optionChunkSize = 4,
            previousSlotLabel = "del cruce",
        )
        val quarterFinals = createAggregateKnockoutMatches(
            idPrefix = "qf",
            phase = "Cuartos de final",
            labelPrefix = "Cuartos",
            sourceTeams = classifiedTeams,
            optionChunkSize = 8,
            previousSlotLabel = "de octavos",
        )
        val semiFinals = createAggregateKnockoutMatches(
            idPrefix = "sf",
            phase = "Semifinales",
            labelPrefix = "Semifinal",
            sourceTeams = classifiedTeams,
            optionChunkSize = 16,
            previousSlotLabel = "de cuartos",
        )
        val thirdPlaceAndFinal = listOf(
            KnockoutMatch(
                id = "third-place",
                phase = "Tercer puesto",
                label = "Partido por tercer lugar",
                homeSlot = "Perdedor semifinal 1",
                awaySlot = "Perdedor semifinal 2",
                options = classifiedTeams,
            ),
            KnockoutMatch(
                id = "final",
                phase = "Final",
                label = "Final",
                homeSlot = "Ganador semifinal 1",
                awaySlot = "Ganador semifinal 2",
                options = classifiedTeams,
            ),
        )

        return round32 + round16 + quarterFinals + semiFinals + thirdPlaceAndFinal
    }

    override fun dashboardSummary(): DashboardSummary = DashboardSummary(
        approvedParticipants = 12,
        updatedMatches = 8,
        currentPhase = "Fase de grupos 2026",
        participationPriceEur = "5",
        prizePotEur = "60",
    )

    override fun ranking(): List<RankingEntry> = listOf(
        RankingEntry(1, "Andrea Mora", 38, 0),
        RankingEntry(2, "Carlos Ibarra", 35, 0),
        RankingEntry(3, "Paula Benítez", 31, 0),
        RankingEntry(4, "Diego Vera", 29, 0),
        RankingEntry(5, "María Torres", 27, 0),
    )

    override fun latestResults(): List<MatchResult> = listOf(
        MatchResult("México", "MEX", "2 - 1", "Sudáfrica", "RSA", "Finalizado"),
        MatchResult("Corea del Sur", "KOR", "1 - 1", "República Checa", "CZE", "Finalizado"),
        MatchResult("Alemania", "GER", "-", "Ecuador", "ECU", "Pendiente"),
        MatchResult("España", "ESP", "-", "Uruguay", "URU", "Pendiente"),
    )

    override fun pendingParticipants(): List<PaymentParticipant> = listOf(
        PaymentParticipant("mock-1", "María Torres", "maria@empresa.com", "Pendiente", "Revisión"),
        PaymentParticipant("mock-2", "Luis Zambrano", "luis@empresa.com", "Bizum recibido", "Pendiente"),
        PaymentParticipant("mock-3", "Sofía Delgado", "sofia@empresa.com", "Efectivo", "Aprobado"),
    )

    override fun adminSettings(): AdminSettings = AdminSettings(
        groupsStatus = "open",
        knockoutsStatus = "closed",
        groupDeadline = "2026-06-10 18:00",
        bizumPhone = "+34 600 000 000",
        participationPriceEur = "5",
    )

    private fun team(id: String, name: String): Team = Team(id = id, name = name)

    private fun knockoutTeams(): List<Team> = listOf(
        "mex", "kor", "can", "sui", "bra", "mar", "usa", "par",
        "ger", "ecu", "ned", "jpn", "bel", "egy", "esp", "uru",
        "fra", "sen", "arg", "aut", "por", "col", "eng", "cro",
        "rsa", "qat", "sco", "aus", "civ", "swe", "irn", "gha",
    ).mapNotNull { id -> teams().firstOrNull { it.id == id } }

    private fun createAggregateKnockoutMatches(
        idPrefix: String,
        phase: String,
        labelPrefix: String,
        sourceTeams: List<Team>,
        optionChunkSize: Int,
        previousSlotLabel: String,
    ): List<KnockoutMatch> = sourceTeams.chunked(optionChunkSize).mapIndexed { index, options ->
        val firstPreviousSlot = index * 2 + 1

        KnockoutMatch(
            id = "$idPrefix-${index + 1}",
            phase = phase,
            label = "$labelPrefix ${index + 1}",
            homeSlot = "Ganador $previousSlotLabel $firstPreviousSlot",
            awaySlot = "Ganador $previousSlotLabel ${firstPreviousSlot + 1}",
            options = options,
        )
    }

    private fun createGroupMatches(groupCode: String, teams: List<Team>): List<GroupMatch> = listOf(
        teams[0] to teams[1],
        teams[2] to teams[3],
        teams[0] to teams[2],
        teams[3] to teams[1],
        teams[3] to teams[0],
        teams[1] to teams[2],
    ).mapIndexed { index, pair ->
        GroupMatch(
            id = "${groupCode.lowercase()}-${index + 1}",
            groupCode = groupCode,
            label = "Grupo $groupCode - Partido ${index + 1}",
            homeTeam = pair.first,
            awayTeam = pair.second,
        )
    }
}
