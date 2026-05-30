package com.porraweb.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.porraweb.data.supabase.AdminAuthService
import com.porraweb.data.supabase.SupabaseConfig
import com.porraweb.domain.model.AdminSettings
import com.porraweb.domain.model.PaymentParticipant
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.navigation.Route
import com.porraweb.presentation.components.AdminCard
import com.porraweb.presentation.components.PageHeader
import com.porraweb.presentation.components.Panel
import com.porraweb.presentation.components.SelectField
import com.porraweb.presentation.components.TextField
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
fun AdminLoginScreen(authService: AdminAuthService) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = remember { MainScope() }
    val submitLogin = {
        if (!loading) {
            loading = true
            error = null
            scope.launch {
                authService.signIn(email, password)
                    .onSuccess { window.location.hash = Route.AdminHome.path }
                    .onFailure {
                        error = it.message ?: "Error al iniciar sesion"
                        loading = false
                    }
            }
        }
    }

    PageHeader(
        title = "Acceso administrador",
        subtitle = "Ingresa con tu cuenta de Supabase Auth autorizada como admin.",
    )

    Section(attrs = { classes("panel", "narrow") }) {
        H2 { Text("Iniciar sesion") }
        TextField("Correo administrador", "admin@empresa.com", InputType.Email, value = email, onEnter = submitLogin) { email = it }
        TextField("Contrasena", "", InputType.Password, value = password, onEnter = submitLogin) { password = it }

        error?.let {
            P(attrs = { classes("error-text") }) { Text(it) }
        }

        Button(
            attrs = {
                classes("button", "button-primary")
                if (loading) attr("disabled", "disabled")
                onClick {
                    submitLogin()
                }
            }
        ) {
            Text(if (loading) "Entrando..." else "Entrar al panel")
        }
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
fun AdminParticipantsScreen(repository: PorraRepository, config: SupabaseConfig?, authService: AdminAuthService) {
    val scope = remember { MainScope() }
    var participants by remember { mutableStateOf<List<PaymentParticipant>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    if (!loaded) {
        loaded = true
        scope.launch { participants = loadParticipants(config, authService) }
    }

    PageHeader(
        title = "Aprobacion de participantes",
        subtitle = "Solo los aprobados entran al ranking general.",
    )

    message?.let {
        P(attrs = { classes("success-text") }) { Text(it) }
    }

    Panel(title = "Pagos y aprobaciones") {
        RealParticipantTable(
            participants,
            config,
            authService,
            scope,
            onAction = { message = it },
            onRefresh = { scope.launch { participants = loadParticipants(config, authService) } },
        )
    }
}

@Composable
fun AdminResultsScreen(repository: PorraRepository, config: SupabaseConfig?, authService: AdminAuthService) {
    val scope = remember { MainScope() }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }

    PageHeader(
        title = "Carga de resultados",
        subtitle = "Los resultados oficiales se obtienen desde FIFA via sincronizacion automatica.",
    )

    Panel(title = "Resultados de partidos") {
        P(attrs = { classes("helper-text") }) {
            Text("La app llama a la API publica de FIFA para sincronizar calendario, equipos reales de eliminatorias y resultados. Si FIFA publica cambios, esta sincronizacion actualiza Supabase y recalcula puntajes.")
        }
        message?.let {
            P(attrs = { if (messageError) classes("error-text") else classes("success-text") }) { Text(it) }
        }
        P(attrs = { classes("form-actions") }) {
            Button(attrs = {
                classes("button", "button-primary")
                if (syncing) attr("disabled", "disabled")
                onClick {
                    syncing = true
                    message = null
                    scope.launch {
                        val result = syncFifaMatches(config, authService)
                        message = result.first
                        messageError = !result.second
                        syncing = false
                    }
                }
            }) {
                Text(if (syncing) "Sincronizando..." else "Sincronizar FIFA ahora")
            }
        }
    }
}

@Composable
fun AdminSettingsScreen(repository: PorraRepository, config: SupabaseConfig?, authService: AdminAuthService, onSettingsSaved: (AdminSettings) -> Unit = {}) {
    val scope = remember { MainScope() }
    var settings by remember { mutableStateOf<AdminSettings?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var groupsStatus by remember { mutableStateOf("open") }
    var knockoutsStatus by remember { mutableStateOf("closed") }
    var groupDeadline by remember { mutableStateOf("") }
    var bizumPhone by remember { mutableStateOf("") }
    var participationPriceEur by remember { mutableStateOf("5") }

    if (!loaded) {
        loaded = true
        scope.launch {
            val s = loadSettings(config, authService)
            if (s != null) {
                settings = s
                groupsStatus = s.groupsStatus
                knockoutsStatus = s.knockoutsStatus
                groupDeadline = s.groupDeadline
                bizumPhone = s.bizumPhone
                participationPriceEur = s.participationPriceEur
            }
        }
    }

    PageHeader(
        title = "Configuracion de la porra",
        subtitle = "Controla fechas, estado de formularios y datos de pago.",
    )

    message?.let {
        P(attrs = { classes("success-text") }) { Text(it) }
    }

    Panel(title = "Fases") {
        SelectField(
            label = "Estado fase de grupos",
            value = groupsStatus,
            options = listOf("open" to "Abierta: mostrar Participar", "closed" to "Cerrada: ocultar Participar"),
        ) { groupsStatus = it }
        SelectField(
            label = "Estado eliminatorias",
            value = knockoutsStatus,
            options = listOf("closed" to "Cerrada: ocultar Eliminatorias", "open" to "Abierta: mostrar Eliminatorias"),
        ) { knockoutsStatus = it }
        TextField("Fecha limite grupos", "2026-06-10 18:00", value = groupDeadline) { groupDeadline = it }
        TextField("Importe participacion (EUR)", "5", InputType.Number, value = participationPriceEur) { participationPriceEur = it }
        TextField("Telefono Bizum para participantes", "+34 600 000 000", value = bizumPhone) { bizumPhone = it }
        P(attrs = { classes("helper-text") }) {
            Text("Este numero se muestra en la pantalla de participacion para que el participante sepa donde enviar el Bizum antes de que lo apruebes.")
        }

        P(attrs = { classes("form-actions") }) {
            Button(attrs = {
                classes("button", "button-primary")
                if (saving) attr("disabled", "disabled")
                onClick {
                    saving = true
                    message = null
                    scope.launch {
                        val result = saveSettings(config, authService, groupsStatus, knockoutsStatus, groupDeadline, bizumPhone, participationPriceEur)
                        message = if (result) "Configuracion guardada" else "Error al guardar"
                        if (result) {
                            onSettingsSaved(
                                AdminSettings(
                                    groupsStatus = groupsStatus,
                                    knockoutsStatus = knockoutsStatus,
                                    groupDeadline = groupDeadline,
                                    bizumPhone = bizumPhone,
                                    participationPriceEur = participationPriceEur,
                                )
                            )
                        }
                        saving = false
                    }
                }
            }) {
                Text(if (saving) "Guardando..." else "Guardar configuracion")
            }
        }
    }
}

@Composable
private fun RealParticipantTable(
    participants: List<PaymentParticipant>,
    config: SupabaseConfig?,
    authService: AdminAuthService,
    scope: CoroutineScope,
    onAction: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    if (participants.isEmpty()) {
        P { Text("No hay participantes pendientes.") }
        return
    }

    Table(attrs = { classes("table") }) {
        Thead {
            Tr {
                Th { Text("Nombre") }
                Th { Text("Correo") }
                Th { Text("Pago") }
                Th { Text("Estado") }
                Th { Text("Accion") }
            }
        }
        Tbody {
            participants.forEach { participant ->
                var approving by remember { mutableStateOf(false) }
                Tr {
                    Td { Text(participant.name) }
                    Td { Text(participant.email) }
                    Td { Span(attrs = { classes("status-pill") }) { Text(paymentLabel(participant.paymentStatus)) } }
                    Td { Text(approvalLabel(participant.approvalStatus)) }
                    Td {
                        if (participant.approvalStatus == "pending") {
                            Button(attrs = {
                                classes("button", "button-small")
                                if (approving) attr("disabled", "disabled")
                                onClick {
                                    approving = true
                                    scope.launch {
                                        val ok = approveParticipant(config, authService, participant.participantId, "approve")
                                        onAction(if (ok) "${participant.name} aprobado como Bizum recibido" else "Error al aprobar")
                                        approving = false
                                        if (ok) onRefresh()
                                    }
                                }
                            }) {
                                Text(if (approving) "..." else "Aprobar Bizum")
                            }
                            Span { Text(" ") }
                            Button(attrs = {
                                classes("button", "button-small")
                                if (approving) attr("disabled", "disabled")
                                onClick {
                                    approving = true
                                    scope.launch {
                                        val ok = approveParticipant(config, authService, participant.participantId, "reject")
                                        onAction(if (ok) "${participant.name} rechazado" else "Error al rechazar")
                                        approving = false
                                        if (ok) onRefresh()
                                    }
                                }
                            }) {
                                Text("Rechazar")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadParticipants(config: SupabaseConfig?, authService: AdminAuthService): List<PaymentParticipant> {
    if (config == null) return emptyList()
    val token = authService.getAccessToken() ?: return emptyList()
    val response = window.fetch(
        "${config.supabaseUrl}/rest/v1/participants?select=id,name,email,payment_status,approval_status&order=created_at.asc",
        authInit(config, token)
    ).await()
    if (!response.ok) return emptyList()
    val payload: dynamic = response.json().await()
    val length = (payload.length as? Number)?.toInt() ?: 0
    val result = mutableListOf<PaymentParticipant>()
    for (i in 0 until length) {
        val r = payload[i]
        result.add(PaymentParticipant(
            participantId = (r.id as String),
            name = (r.name as String),
            email = (r.email as String),
            paymentStatus = (r.payment_status as String),
            approvalStatus = (r.approval_status as String),
        ))
    }
    return result
}

private suspend fun loadSettings(config: SupabaseConfig?, authService: AdminAuthService): AdminSettings? {
    if (config == null) return null
    val token = authService.getAccessToken() ?: return null
    val response = window.fetch(
        "${config.supabaseUrl}/rest/v1/app_settings?select=key,value",
        authInit(config, token)
    ).await()
    if (!response.ok) return null
    val payload: dynamic = response.json().await()
    val length = (payload.length as? Number)?.toInt() ?: 0
    val map = mutableMapOf<String, String>()
    for (i in 0 until length) {
        map[(payload[i].key as String)] = (payload[i].value as String)
    }
    return AdminSettings(
        groupsStatus = map["groups_form_status"] ?: "open",
        knockoutsStatus = map["knockouts_form_status"] ?: "closed",
        groupDeadline = map["group_deadline"] ?: "",
        bizumPhone = map["bizum_phone"] ?: "",
        participationPriceEur = map["participation_price_eur"] ?: "5",
    )
}

private suspend fun saveSettings(config: SupabaseConfig?, authService: AdminAuthService, groupsStatus: String, knockoutsStatus: String, groupDeadline: String, bizumPhone: String, participationPriceEur: String): Boolean {
    if (config == null) return false
    val token = authService.getAccessToken() ?: return false
    val body: dynamic = js("({})")
    val arr = js("([])")
    val settings = listOf(
        "groups_form_status" to groupsStatus,
        "knockouts_form_status" to knockoutsStatus,
        "group_deadline" to groupDeadline,
        "bizum_phone" to bizumPhone,
        "participation_price_eur" to participationPriceEur,
    )
    for ((k, v) in settings) {
        val item: dynamic = js("({})")
        item.key = k
        item.value = v
        arr.push(item)
    }
    body.settings = arr
    val bodyStr: String = js("JSON.stringify(body)")
    return fetchPost(config, token, "update-settings", bodyStr)
}

private suspend fun syncFifaMatches(config: SupabaseConfig?, authService: AdminAuthService): Pair<String, Boolean> {
    if (config == null) return "Error de configuracion" to false
    val token = authService.getAccessToken() ?: return "Sesion admin expirada" to false
    val headers: dynamic = js("({})")
    headers["Authorization"] = "Bearer $token"
    headers["Content-Type"] = "application/json"
    headers["apikey"] = config.publishableKey
    val init: dynamic = js("({})")
    init.method = "POST"
    init.headers = headers
    init.body = "{}"
    return try {
        val response = window.fetch("${config.supabaseUrl}/functions/v1/sync-fifa-matches", init).await()
        val payload: dynamic = response.json().await()
        if (!response.ok || payload.ok as? Boolean != true) {
            (payload.error?.toString() ?: "Error al sincronizar FIFA") to false
        } else {
            val seen = payload.matchesSeen?.toString() ?: "0"
            val upserted = payload.matchesUpserted?.toString() ?: "0"
            val results = payload.resultsUpserted?.toString() ?: "0"
            "FIFA sincronizado: $seen partidos leidos, $upserted partidos actualizados, $results resultados actualizados." to true
        }
    } catch (e: Exception) {
        (e.message ?: "Error de conexion") to false
    }
}

private fun paymentLabel(value: String): String = when (value) {
    "pending_payment" -> "Pendiente de pago"
    "paid_bizum" -> "Bizum recibido"
    "paid_cash" -> "Efectivo recibido"
    "rejected" -> "Pago rechazado"
    else -> value
}

private fun approvalLabel(value: String): String = when (value) {
    "pending" -> "Pendiente"
    "approved" -> "Aprobado"
    "rejected" -> "Rechazado"
    "needs_review" -> "Revisar"
    else -> value
}

private suspend fun approveParticipant(config: SupabaseConfig?, authService: AdminAuthService, participantId: String, action: String): Boolean {
    if (config == null) return false
    val token = authService.getAccessToken() ?: return false
    val body: dynamic = js("({})")
    body.participant_id = participantId
    body.action = action
    val bodyStr: String = js("JSON.stringify(body)")
    return fetchPost(config, token, "approve-participant", bodyStr)
}

private suspend fun fetchPost(config: SupabaseConfig, token: String, path: String, bodyStr: String): Boolean {
    val headers: dynamic = js("({})")
    headers["Authorization"] = "Bearer $token"
    headers["Content-Type"] = "application/json"
    headers["apikey"] = config.publishableKey
    val init: dynamic = js("({})")
    init.method = "POST"
    init.headers = headers
    init.body = bodyStr
    val response = window.fetch("${config.supabaseUrl}/functions/v1/$path", init).await()
    return response.ok
}

private fun authInit(config: SupabaseConfig, token: String): dynamic {
    val h: dynamic = js("({})")
    h["apikey"] = config.publishableKey
    h["Authorization"] = "Bearer $token"
    h["Accept"] = "application/json"
    val i: dynamic = js("({})")
    i.method = "GET"
    i.headers = h
    return i
}
