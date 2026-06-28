package com.porraweb.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.presentation.components.KnockoutPredictionTable
import com.porraweb.presentation.components.MatchResults
import com.porraweb.presentation.components.PageHeader
import com.porraweb.presentation.components.Panel
import com.porraweb.presentation.components.RankingTable
import com.porraweb.presentation.components.StatCard
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

private enum class DashboardTab { Ranking, Bracket }

@Composable
fun DashboardScreen(repository: PorraRepository) {
    val summary = repository.dashboardSummary()
    var tab by remember { mutableStateOf(DashboardTab.Ranking) }

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

    Div(attrs = { classes("tab-bar") }) {
        Button(attrs = {
            if (tab == DashboardTab.Ranking) classes("tab-btn", "active") else classes("tab-btn")
            onClick { tab = DashboardTab.Ranking }
        }) { Text("Ranking") }
        Button(attrs = {
            if (tab == DashboardTab.Bracket) classes("tab-btn", "active") else classes("tab-btn")
            onClick { tab = DashboardTab.Bracket }
        }) { Text("Bracket") }
    }

    when (tab) {
        DashboardTab.Ranking -> {
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

        DashboardTab.Bracket -> {
            KnockoutPredictionTable(
                matches = repository.knockoutMatches(),
                homeScores = repository.realHomeScores(),
                awayScores = repository.realAwayScores(),
                winners = repository.realWinners(),
                readOnly = true,
            )
        }
    }
}
