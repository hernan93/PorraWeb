package com.porraweb.presentation.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.porraweb.data.supabase.AdminAuthService
import com.porraweb.data.supabase.SupabaseConfig
import com.porraweb.domain.model.AdminSettings
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.navigation.Route
import com.porraweb.presentation.components.ActionLink
import com.porraweb.presentation.components.PageHeader
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.events.Event

@Composable
fun PorraWebApp(repository: PorraRepository, authService: AdminAuthService, config: SupabaseConfig?) {
    var route by remember { mutableStateOf(currentRoute()) }
    var settings by remember { mutableStateOf(repository.adminSettings()) }
    var settingsLoaded by remember { mutableStateOf(false) }
    val scope = remember { MainScope() }

    if (!settingsLoaded) {
        settingsLoaded = true
        scope.launch { settings = loadPublicSettings(config, settings) }
    }

    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { route = currentRoute() }
        window.addEventListener("hashchange", listener)
        onDispose { window.removeEventListener("hashchange", listener) }
    }

    Div(attrs = { classes("app-shell") }) {
        TopBar(current = route, settings = settings)
        Main(attrs = { classes("page") }) {
            when (route) {
                Route.Home -> HomeScreen(repository, settings)
                Route.GroupPredictions -> if (settings.groupsStatus == "open") {
                    GroupPredictionsScreen(repository, config, settings)
                } else {
                    PhaseClosedScreen("La fase de grupos esta cerrada", "Ya no se reciben predicciones de grupos.")
                }
                Route.KnockoutPredictions -> if (settings.knockoutsStatus == "open") {
                    KnockoutPredictionsScreen(repository, config, settings)
                } else {
                    PhaseClosedScreen("Las eliminatorias aun no estan abiertas", "Cuando el administrador habilite esta fase, aparecera la pestana de eliminatorias.")
                }
                Route.Dashboard -> DashboardScreen(repository)
                Route.AdminLogin -> AdminLoginScreen(authService)
                Route.AdminHome -> if (authService.isAdmin) AdminHomeScreen() else AdminLoginScreen(authService)
                Route.AdminParticipants -> if (authService.isAdmin) AdminParticipantsScreen(repository, config, authService) else AdminLoginScreen(authService)
                Route.AdminResults -> if (authService.isAdmin) AdminResultsScreen(repository, config, authService) else AdminLoginScreen(authService)
                Route.AdminSettings -> if (authService.isAdmin) {
                    AdminSettingsScreen(repository, config, authService) { updated -> settings = updated }
                } else {
                    AdminLoginScreen(authService)
                }
            }
        }
    }
}

private fun currentRoute(): Route = Route.fromHash(window.location.hash.ifBlank { "#/" })

@Composable
private fun PhaseClosedScreen(title: String, message: String) {
    PageHeader(title = title, subtitle = message)
    P(attrs = { classes("helper-text") }) {
        Text("Puedes seguir el ranking y los resultados desde el dashboard.")
    }
    Div(attrs = { classes("actions") }) {
        ActionLink("Ver dashboard", Route.Dashboard.path, primary = true)
    }
}

private suspend fun loadPublicSettings(config: SupabaseConfig?, fallback: AdminSettings): AdminSettings {
    if (config == null) return fallback
    return try {
        val headers: dynamic = js("({})")
        headers["apikey"] = config.publishableKey
        headers["Authorization"] = "Bearer ${config.publishableKey}"
        headers["Accept"] = "application/json"
        val init: dynamic = js("({})")
        init.method = "GET"
        init.headers = headers
        val response = window.fetch("${config.supabaseUrl}/rest/v1/app_settings?select=key,value", init).await()
        if (!response.ok) return fallback
        val payload: dynamic = response.json().await()
        val length = (payload.length as? Number)?.toInt() ?: 0
        val map = mutableMapOf<String, String>()
        for (i in 0 until length) {
            map[payload[i].key as String] = payload[i].value as String
        }
        fallback.copy(
            groupsStatus = map["groups_form_status"] ?: fallback.groupsStatus,
            knockoutsStatus = map["knockouts_form_status"] ?: fallback.knockoutsStatus,
            groupDeadline = map["group_deadline"] ?: fallback.groupDeadline,
            bizumPhone = map["bizum_phone"] ?: fallback.bizumPhone,
            participationPriceEur = map["participation_price_eur"] ?: fallback.participationPriceEur,
        )
    } catch (e: Exception) {
        fallback
    }
}
