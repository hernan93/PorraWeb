package com.porraweb.presentation.screens

import androidx.compose.runtime.Composable
import com.porraweb.domain.model.AdminSettings
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.navigation.Route
import com.porraweb.presentation.components.ActionLink
import com.porraweb.presentation.components.RuleList
import com.porraweb.presentation.components.ScoringCard
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Header
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

@Composable
fun HomeScreen(repository: PorraRepository, settings: AdminSettings) {
    val groupsOpen = settings.groupsStatus == "open"
    val knockoutsOpen = settings.knockoutsStatus == "open"
    val price = settings.participationPriceEur.ifBlank { "5" }

    Header(attrs = { classes("hero", "participant-hero") }) {
        Div(attrs = { classes("badge") }) { Text("Mundial 2026") }
        H1 { Text("Participa en la porra del equipo") }
        P(attrs = { classes("lead") }) {
            Text("Haz tus predicciones, paga tu participacion de $price EUR y compite por el ranking general.")
        }
        Div(attrs = { classes("actions") }) {
            when {
                groupsOpen -> ActionLink("Participar ahora", Route.GroupPredictions.path, primary = true)
                knockoutsOpen -> ActionLink("Predecir eliminatorias", Route.KnockoutPredictions.path, primary = true)
            }
            ActionLink("Ver ranking", Route.Dashboard.path, primary = false)
        }
    }

    Section(attrs = { classes("panel", "rules-panel") }) {
        H2 { Text("Reglas para participantes") }
        RuleList(repository.participantRules())
    }

    Section(attrs = { classes("grid") }) {
        repository.scoringRules().forEach { rule -> ScoringCard(rule) }
    }
}
