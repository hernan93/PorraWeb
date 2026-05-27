package com.porraweb.presentation.screens

import androidx.compose.runtime.Composable
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.navigation.Route
import com.porraweb.presentation.components.ActionLink
import com.porraweb.presentation.components.AdminCard
import com.porraweb.presentation.components.AdminSettingsForm
import com.porraweb.presentation.components.MatchPredictionTable
import com.porraweb.presentation.components.PageHeader
import com.porraweb.presentation.components.Panel
import com.porraweb.presentation.components.ParticipantApprovalTable
import com.porraweb.presentation.components.SubmitPreviewButton
import com.porraweb.presentation.components.TextField
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

@Composable
fun AdminLoginScreen() {
    PageHeader(
        title = "Acceso administrador",
        subtitle = "El admin usara Supabase Auth. Esta pantalla esta en modo mock local.",
    )

    Section(attrs = { classes("panel", "narrow") }) {
        H2 { Text("Iniciar sesion") }
        TextField("Correo administrador", "admin@empresa.com", InputType.Email)
        TextField("Contrasena", "", InputType.Password)
        ActionLink("Entrar al panel", Route.AdminHome.path, primary = true)
    }
}

@Composable
fun AdminHomeScreen() {
    PageHeader(
        title = "Panel administrador",
        subtitle = "Gestiona pagos, resultados y configuracion de fases.",
    )

    Section(attrs = { classes("grid") }) {
        AdminCard("Aprobar pagos", "Marca participantes como pagados por Bizum o efectivo.", Route.AdminParticipants)
        AdminCard("Cargar resultados", "Actualiza marcadores reales y ganadores.", Route.AdminResults)
        AdminCard("Configurar fases", "Abre o cierra formularios por fecha.", Route.AdminSettings)
    }
}

@Composable
fun AdminParticipantsScreen(repository: PorraRepository) {
    PageHeader(
        title = "Aprobacion de participantes",
        subtitle = "Solo los aprobados entran al ranking general.",
    )

    Panel(title = "Pagos pendientes") {
        ParticipantApprovalTable(repository.pendingParticipants())
    }
}

@Composable
fun AdminResultsScreen(repository: PorraRepository) {
    PageHeader(
        title = "Carga de resultados",
        subtitle = "El admin actualiza los resultados reales para recalcular puntos.",
    )

    Panel(title = "Resultados de partidos") {
        MatchPredictionTable(repository.groupMatches())
        SubmitPreviewButton("Guardar resultados")
    }
}

@Composable
fun AdminSettingsScreen(repository: PorraRepository) {
    PageHeader(
        title = "Configuracion de la porra",
        subtitle = "Controla fechas, estado de formularios y datos de pago.",
    )

    Panel(title = "Fases") {
        AdminSettingsForm(repository.adminSettings())
        SubmitPreviewButton("Guardar configuracion")
    }
}
