package com.porraweb.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.porraweb.data.supabase.SupabaseConfig
import com.porraweb.domain.model.AdminSettings
import com.porraweb.domain.model.TournamentGroup
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.presentation.components.BestThirdPlacePredictionCard
import com.porraweb.presentation.components.FormGrid
import com.porraweb.presentation.components.GroupPredictionCard
import com.porraweb.presentation.components.KnockoutPredictionTable
import com.porraweb.presentation.components.PageHeader
import com.porraweb.presentation.components.Panel
import com.porraweb.presentation.components.TextField
import org.jetbrains.compose.web.dom.H2
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement

private object PredictionDrafts {
    var groupName by mutableStateOf("")
    var groupEmail by mutableStateOf("")
    val groupHomeScores = mutableStateMapOf<String, String>()
    val groupAwayScores = mutableStateMapOf<String, String>()
    val groupPositions = mutableStateMapOf<String, String>()
    val groupThirdPlaces = mutableStateMapOf<String, Boolean>()

    var knockoutEmail by mutableStateOf("")
    val knockoutHomeScores = mutableStateMapOf<String, String>()
    val knockoutAwayScores = mutableStateMapOf<String, String>()
    val knockoutWinners = mutableStateMapOf<String, String>()
}

@Composable
fun GroupPredictionsScreen(repository: PorraRepository, config: SupabaseConfig?, settings: AdminSettings) {
    var submitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    val scope = remember { MainScope() }
    val groups = repository.groups()
    val duplicateGroupCodes = duplicateGroupCodes(groups, PredictionDrafts.groupPositions)

    PageHeader(
        title = "Predicciones de fase de grupos",
        subtitle = "Completa marcadores, orden final de los 12 grupos y selecciona los 8 mejores terceros.",
    )

    PaymentInstructions(settings)

    Panel(title = "Datos del participante") {
        FormGrid {
            TextField("Nombre completo", "Ej. Andrea Mora", fieldValue = PredictionDrafts.groupName) { PredictionDrafts.groupName = it }
            TextField("Correo", "nombre@empresa.com", InputType.Email, fieldValue = PredictionDrafts.groupEmail) { PredictionDrafts.groupEmail = it }
        }
    }

    groups.forEach { group ->
        GroupPredictionCard(
            group = group,
            homeScores = PredictionDrafts.groupHomeScores,
            awayScores = PredictionDrafts.groupAwayScores,
            positions = PredictionDrafts.groupPositions,
            orderError = if (group.code in duplicateGroupCodes) "No repitas equipos en el Grupo ${group.code}." else null,
            onHomeScoreChange = { matchId, value -> PredictionDrafts.groupHomeScores[matchId] = value },
            onAwayScoreChange = { matchId, value -> PredictionDrafts.groupAwayScores[matchId] = value },
            onPositionChange = { groupCode, position, value -> PredictionDrafts.groupPositions["$groupCode-$position"] = value },
        )
    }

    BestThirdPlacePredictionCard(
        groups = groups,
        selectedCodes = PredictionDrafts.groupThirdPlaces.filterValues { it }.keys,
        onToggle = { groupCode, checked -> PredictionDrafts.groupThirdPlaces[groupCode] = checked },
    )

    message?.let {
        P(attrs = { if (messageError) classes("error-text") else classes("success-text") }) { Text(it) }
    }

    Div(attrs = { classes("form-actions") }) {
        Button(attrs = {
            classes("button", "button-primary")
            if (submitting) attr("disabled", "disabled")
            onClick {
                if (config == null) {
                    message = "Error de configuracion"
                    messageError = true
                    return@onClick
                }
                if (PredictionDrafts.groupName.isBlank() || PredictionDrafts.groupEmail.isBlank()) {
                    message = "Completa nombre y correo"
                    messageError = true
                    return@onClick
                }
                if (duplicateGroupCodes.isNotEmpty()) {
                    message = "No repitas equipos en el orden final de los grupos: ${duplicateGroupCodes.joinToString()}"
                    messageError = true
                    return@onClick
                }
                submitting = true
                message = null
                scope.launch {
                    val result = collectAndSubmitGroups(config, PredictionDrafts.groupName, PredictionDrafts.groupEmail, repository)
                    message = result.first
                    messageError = !result.second
                    submitting = false
                }
            }
        }) {
            Text(if (submitting) "Enviando..." else "Enviar predicciones de grupos")
        }
    }
}

@Composable
fun KnockoutPredictionsScreen(repository: PorraRepository, config: SupabaseConfig?, settings: AdminSettings) {
    var submitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }
    val scope = remember { MainScope() }

    PageHeader(
        title = "Predicciones de eliminatorias",
        subtitle = "El Mundial 2026 arranca la fase final en ronda de 32.",
    )

    PaymentInstructions(settings)

    Panel(title = "Correo del participante") {
        FormGrid {
            TextField("Correo aprobado", "nombre@empresa.com", InputType.Email, fieldValue = PredictionDrafts.knockoutEmail) { PredictionDrafts.knockoutEmail = it }
        }
        P(attrs = { classes("notice", "notice-info") }) {
            Text("Usa el mismo correo con el que enviaste la fase de grupos. Debes estar aprobado para participar.")
        }
    }

    Div(attrs = { classes("bracket-panel") }) {
        H2 { Text("Ronda de 32, octavos, cuartos, semifinales y finales") }
        KnockoutPredictionTable(
            matches = repository.knockoutMatches(),
            homeScores = PredictionDrafts.knockoutHomeScores,
            awayScores = PredictionDrafts.knockoutAwayScores,
            winners = PredictionDrafts.knockoutWinners,
            onHomeScoreChange = { matchId, value -> PredictionDrafts.knockoutHomeScores[matchId] = value },
            onAwayScoreChange = { matchId, value -> PredictionDrafts.knockoutAwayScores[matchId] = value },
            onWinnerChange = { matchId, value -> PredictionDrafts.knockoutWinners[matchId] = value },
        )

        message?.let {
            P(attrs = { if (messageError) classes("error-text") else classes("success-text") }) { Text(it) }
        }

        Div(attrs = { classes("form-actions") }) {
            Button(attrs = {
                classes("button", "button-primary")
                if (submitting) attr("disabled", "disabled")
                onClick {
                    if (config == null) {
                        message = "Error de configuracion"
                        messageError = true
                        return@onClick
                    }
                    if (PredictionDrafts.knockoutEmail.isBlank()) {
                        message = "Completa tu correo"
                        messageError = true
                        return@onClick
                    }
                    submitting = true
                    message = null
                    scope.launch {
                        val result = collectAndSubmitKnockouts(config, PredictionDrafts.knockoutEmail, repository)
                        message = result.first
                        messageError = !result.second
                        submitting = false
                    }
                }
            }) {
                Text(if (submitting) "Enviando..." else "Enviar predicciones de eliminatorias")
            }
        }
    }
}

@Composable
private fun PaymentInstructions(settings: AdminSettings) {
    val price = settings.participationPriceEur.ifBlank { "5" }
    val phone = settings.bizumPhone.ifBlank { "el numero indicado por el administrador" }
    Panel(title = "Pago por Bizum") {
        P {
            Text("Antes de que el administrador apruebe tu participacion, envia $price EUR por Bizum a $phone. Usa tu nombre y correo como referencia para que el admin pueda validar el pago.")
        }
        P(attrs = { classes("helper-text") }) {
            Text("Tu prediccion quedara registrada y pendiente hasta que el admin confirme el Bizum. Si ya enviaste esta fase con el mismo correo, no podras repetirla.")
        }
    }
}

private fun duplicateGroupCodes(groups: List<TournamentGroup>, positions: Map<String, String>): Set<String> = groups
    .filter { group ->
        val selected = (1..4)
            .mapNotNull { pos -> positions["${group.code}-$pos"]?.takeIf { it.isNotBlank() } }
        selected.size != selected.toSet().size
    }
    .map { it.code }
    .toSet()

private suspend fun collectAndSubmitGroups(
    config: SupabaseConfig,
    name: String,
    email: String,
    repository: PorraRepository,
): Pair<String, Boolean> {
    val groups = repository.groups()
    val matches = repository.groupMatches()

    val body: dynamic = js("({})")
    body.participant_name = name
    body.participant_email = email

    val mp = js("([])")
    for (match in matches) {
        val homeEl = document.getElementById("score-home-${match.id}") as? HTMLInputElement
        val awayEl = document.getElementById("score-away-${match.id}") as? HTMLInputElement
        val home = homeEl?.value?.toIntOrNull()
        val away = awayEl?.value?.toIntOrNull()
        if (home == null || away == null) return "Falta rellenar todos los marcadores" to false
        val item: dynamic = js("({})")
        item.match_id = match.id
        item.home_goals = home
        item.away_goals = away
        mp.push(item)
    }
    body.match_predictions = mp

    val gpa = js("([])")
    for (group in groups) {
        for (pos in 1..4) {
            val sel = document.getElementById("pos-${group.code}-$pos") as? HTMLSelectElement
            val teamId = sel?.value ?: return "Falta orden final del grupo ${group.code}" to false
            if (teamId.isEmpty()) return "Falta orden final del grupo ${group.code}" to false
            val item: dynamic = js("({})")
            item.group_id = group.id
            item.team_id = teamId
            item.predicted_position = pos
            gpa.push(item)
        }
    }
    body.group_positions = gpa

    val tpa = js("([])")
    for (group in groups) {
        val cb = document.getElementById("tp-${group.code}") as? HTMLInputElement
        if (cb?.checked == true) tpa.push(group.id)
    }
    body.third_place_selections = tpa

    val bodyStr: String = js("JSON.stringify(body)")
    return fetchPost(config, "submit-groups", bodyStr)
}

private suspend fun collectAndSubmitKnockouts(
    config: SupabaseConfig,
    email: String,
    repository: PorraRepository,
): Pair<String, Boolean> {
    val matches = repository.knockoutMatches()

    val body: dynamic = js("({})")
    body.participant_email = email

    val kpa = js("([])")
    val kma = js("([])")
    for (match in matches) {
        val homeEl = document.getElementById("score-home-${match.id}") as? HTMLInputElement
        val awayEl = document.getElementById("score-away-${match.id}") as? HTMLInputElement
        val winEl = document.getElementById("winner-${match.id}") as? HTMLSelectElement
        val home = homeEl?.value?.toIntOrNull()
        val away = awayEl?.value?.toIntOrNull()
        val winnerId = winEl?.value
        if (home == null || away == null) return "Falta rellenar todos los marcadores" to false
        if (winnerId.isNullOrEmpty()) return "Falta seleccionar el ganador de todos los cruces" to false

        val km: dynamic = js("({})")
        km.match_id = match.id
        km.home_goals = home
        km.away_goals = away
        km.predicted_winner_team_id = winnerId
        kma.push(km)
    }
    body.knockout_match_predictions = kma

    val kpMap = linkedMapOf<String, MutableMap<String, String>>()
    for (match in matches) {
        val phase = match.phase
        val sel = document.getElementById("winner-${match.id}") as? HTMLSelectElement
        val teamId = sel?.value
        if (!teamId.isNullOrEmpty()) {
            kpMap.getOrPut(phase) { mutableMapOf() }[match.id] = teamId
        }
    }
    for ((phase, slots) in kpMap) {
        for ((slotCode, teamId) in slots) {
            val item: dynamic = js("({})")
            item.phase = phase
            item.slot_code = slotCode
            item.predicted_team_id = teamId
            kpa.push(item)
        }
    }
    body.knockout_predictions = kpa

    val bodyStr: String = js("JSON.stringify(body)")
    return fetchPost(config, "submit-knockouts", bodyStr)
}

private suspend fun fetchPost(config: SupabaseConfig, path: String, bodyStr: String): Pair<String, Boolean> {
    val h: dynamic = js("({})")
    h["Content-Type"] = "application/json"
    h["apikey"] = config.publishableKey
    val i: dynamic = js("({})")
    i.method = "POST"
    i.headers = h
    i.body = bodyStr
    return try {
        val res = window.fetch("${config.supabaseUrl}/functions/v1/$path", i).await()
        if (res.ok) {
            val payload: dynamic = res.json().await()
            if (payload.ok as? Boolean == true) (payload.message?.toString() ?: "Guardado correctamente") to true
            else (payload.error?.toString() ?: "Error desconocido") to false
        } else {
            val payload: dynamic = try { res.json().await() } catch (_: Exception) { null }
            val serverError = payload?.error?.toString() ?: "Error del servidor (${res.status})"
            serverError to false
        }
    } catch (e: Exception) {
        (e.message ?: "Error de conexion") to false
    }
}
