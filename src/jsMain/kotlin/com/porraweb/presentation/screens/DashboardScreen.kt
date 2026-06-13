package com.porraweb.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.presentation.components.MatchResults
import com.porraweb.presentation.components.PageHeader
import com.porraweb.presentation.components.Panel
import com.porraweb.presentation.components.RankingTable
import com.porraweb.presentation.components.StatCard
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

@Composable
fun DashboardScreen(repository: PorraRepository) {
    val summary = repository.dashboardSummary()

    DisposableEffect(repository) {
        val intervalId = window.setInterval({ repository.refresh() }, 30_000)
        onDispose { window.clearInterval(intervalId) }
    }

    PageHeader(
        title = "Dashboard general",
        subtitle = "Ranking, resultados y estado de la porra.",
    )

    Section(attrs = { classes("stats-grid") }) {
        StatCard("Participantes aprobados", summary.approvedParticipants.toString())
        StatCard("Partidos actualizados", summary.updatedMatches.toString())
        StatCard("Fase actual", summary.currentPhase)
    }

    Panel(title = "Ranking") {
        P(attrs = { classes("pot-banner") }) {
            Text("Bote actual: ${summary.prizePotEur} EUR (${summary.participationPriceEur} EUR por participante aprobado)")
        }
        RankingTable(repository.ranking())
    }

    Panel(title = "Ultimos resultados") {
        MatchResults(repository.latestResults())
    }
}
