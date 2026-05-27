package com.porraweb.presentation.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.navigation.Route
import com.porraweb.presentation.components.TopBar
import com.porraweb.presentation.screens.AdminHomeScreen
import com.porraweb.presentation.screens.AdminLoginScreen
import com.porraweb.presentation.screens.AdminParticipantsScreen
import com.porraweb.presentation.screens.AdminResultsScreen
import com.porraweb.presentation.screens.AdminSettingsScreen
import com.porraweb.presentation.screens.DashboardScreen
import com.porraweb.presentation.screens.GroupPredictionsScreen
import com.porraweb.presentation.screens.HomeScreen
import com.porraweb.presentation.screens.KnockoutPredictionsScreen
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Main
import org.w3c.dom.events.Event

@Composable
fun PorraWebApp(repository: PorraRepository) {
    var route by remember { mutableStateOf(currentRoute()) }

    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { route = currentRoute() }
        window.addEventListener("hashchange", listener)
        onDispose { window.removeEventListener("hashchange", listener) }
    }

    Div(attrs = { classes("app-shell") }) {
        TopBar(current = route)
        Main(attrs = { classes("page") }) {
            when (route) {
                Route.Home -> HomeScreen(repository)
                Route.GroupPredictions -> GroupPredictionsScreen(repository)
                Route.KnockoutPredictions -> KnockoutPredictionsScreen(repository)
                Route.Dashboard -> DashboardScreen(repository)
                Route.AdminLogin -> AdminLoginScreen()
                Route.AdminHome -> AdminHomeScreen()
                Route.AdminParticipants -> AdminParticipantsScreen(repository)
                Route.AdminResults -> AdminResultsScreen(repository)
                Route.AdminSettings -> AdminSettingsScreen(repository)
            }
        }
    }
}

private fun currentRoute(): Route = Route.fromHash(window.location.hash.ifBlank { "#/" })
