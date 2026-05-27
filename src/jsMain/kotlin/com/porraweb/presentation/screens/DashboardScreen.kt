package com.porraweb.presentation.screens

import androidx.compose.runtime.Composable
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.presentation.components.MatchResults
import com.porraweb.presentation.components.PageHeader
import com.porraweb.presentation.components.Panel
import com.porraweb.presentation.components.RankingTable
import com.porraweb.presentation.components.StatCard
import org.jetbrains.compose.web.dom.Section

@Composable
fun DashboardScreen(repository: PorraRepository) {
    val summary = repository.dashboardSummary()

    PageHeader(
        title = "Dashboard general",
        subtitle = "Ranking, resultados y estado de la porra. Este link se enviara por correo a los participantes.",
    )

    Section(attrs = { classes("stats-grid") }) {
        StatCard("Participantes aprobados", summary.approvedParticipants.toString())
        StatCard("Partidos actualizados", summary.updatedMatches.toString())
        StatCard("Fase actual", summary.currentPhase)
    }

    Panel(title = "Ranking") {
        RankingTable(repository.ranking())
    }

    Panel(title = "Ultimos resultados") {
        MatchResults(repository.latestResults())
    }
}
