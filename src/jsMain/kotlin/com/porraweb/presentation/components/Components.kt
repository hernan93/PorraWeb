package com.porraweb.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.porraweb.domain.model.AdminSettings
import com.porraweb.domain.model.GroupMatch
import com.porraweb.domain.model.KnockoutMatch
import com.porraweb.domain.model.MatchResult
import com.porraweb.domain.model.PaymentParticipant
import com.porraweb.domain.model.RankingEntry
import com.porraweb.domain.model.Rule
import com.porraweb.domain.model.ScoringRule
import com.porraweb.domain.model.Team
import com.porraweb.domain.model.TournamentGroup
import com.porraweb.navigation.Route
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Header
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Ul

@Composable
fun TopBar(current: Route) {
    TopBar(current = current, settings = null)
}

@Composable
fun TopBar(current: Route, settings: AdminSettings?) {
    val groupsOpen = settings?.groupsStatus != "closed"
    val knockoutsOpen = settings?.knockoutsStatus == "open"

    Div(attrs = { classes("topbar") }) {
        A(href = Route.Home.path, attrs = { classes("brand") }) { Text("PorraWeb 2026") }
        Div(attrs = { classes("nav-links") }) {
            if (groupsOpen) NavLink(route = Route.GroupPredictions, current = current)
            if (knockoutsOpen) NavLink(route = Route.KnockoutPredictions, current = current)
            NavLink(route = Route.Dashboard, current = current)
            NavLink(route = Route.AdminLogin, current = current)
        }
    }
}

@Composable
fun NavLink(route: Route, current: Route) {
    A(
        href = route.path,
        attrs = {
            if (route == current) classes("nav-link", "active") else classes("nav-link")
        },
    ) {
        Text(route.label)
    }
}

@Composable
fun PageHeader(title: String, subtitle: String) {
    Header(attrs = { classes("page-header") }) {
        H1 { Text(title) }
        P { Text(subtitle) }
    }
}

@Composable
fun ActionLink(label: String, href: String, primary: Boolean) {
    A(
        href = href,
        attrs = {
            if (primary) classes("button", "button-primary") else classes("button", "button-secondary")
        },
    ) {
        Text(label)
    }
}

@Composable
fun RuleList(rules: List<Rule>) {
    Div(attrs = { classes("rule-list") }) {
        rules.forEach { rule ->
            Div(attrs = { classes("rule-item") }) {
                Span(attrs = { classes("rule-number") }) { Text(rule.number) }
                P { Text(rule.description) }
            }
        }
    }
}

@Composable
fun ScoringCard(rule: ScoringRule) {
    Div(attrs = { classes("card") }) {
        H3 { Text(rule.title) }
        Ul(attrs = { classes("list") }) {
            rule.items.forEach { item -> Li { Text(item) } }
        }
    }
}

@Composable
fun FormGrid(content: @Composable () -> Unit) {
    Div(attrs = { classes("form-grid") }) { content() }
}

@Composable
fun TextField(
    label: String,
    placeholder: String,
    type: InputType<*> = InputType.Text,
    fieldValue: String = "",
    onEnter: () -> Unit = {},
    onValueChange: (String) -> Unit = {},
) {
    Label(attrs = { classes("field") }) {
        Span { Text(label) }
        Input(type = type, attrs = {
            classes("input")
            attr("placeholder", placeholder)
            value(fieldValue)
            onInput { e -> onValueChange(e.target.value) }
            onKeyDown { e ->
                if (e.key == "Enter") onEnter()
            }
        })
    }
}

@Composable
fun SelectField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
) {
    Label(attrs = { classes("field") }) {
        Span { Text(label) }
        Select(attrs = {
            classes("input")
            attr("value", value)
            onInput { e -> onValueChange(e.target.value) }
        }) {
            options.forEach { (optionValue, optionLabel) ->
                Option(value = optionValue, attrs = {
                    if (optionValue == value) attr("selected", "selected")
                }) { Text(optionLabel) }
            }
        }
    }
}

@Composable
fun TeamSelectField(label: String, teams: List<Team>, placeholder: String = "Selecciona equipo", value: String = "", elementId: String = "", onValueChange: (String) -> Unit = {}) {
    Label(attrs = { classes("field") }) {
        Span { Text(label) }
        Select(attrs = {
            classes("input")
            if (elementId.isNotEmpty()) id(elementId)
            attr("value", value)
            onInput { e -> onValueChange(e.target.value) }
        }) {
            Option(value = "", attrs = { if (value.isEmpty()) attr("selected", "selected") }) { Text(placeholder) }
            teams.forEach { team ->
                Option(value = team.id, attrs = { if (team.id == value) attr("selected", "selected") }) { Text(team.name) }
            }
        }
    }
}

@Composable
fun GroupPredictionCard(
    group: TournamentGroup,
    homeScores: Map<String, String> = emptyMap(),
    awayScores: Map<String, String> = emptyMap(),
    positions: Map<String, String> = emptyMap(),
    orderError: String? = null,
    onHomeScoreChange: (String, String) -> Unit = { _, _ -> },
    onAwayScoreChange: (String, String) -> Unit = { _, _ -> },
    onPositionChange: (String, String, String) -> Unit = { _, _, _ -> },
) {
    Section(attrs = { classes("panel", "group-card") }) {
        H2 { Text("Grupo ${group.code}") }
        P { Text("Predice los 6 partidos y el orden final. Los dos primeros clasifican directo; el tercero puede entrar entre los 8 mejores terceros.") }
        Div(attrs = { classes("team-chips") }) {
            group.teams.forEach { team -> Span(attrs = { classes("team-chip") }) { Text(team.name) } }
        }
        MatchPredictionTable(
            matches = group.matches,
            homeScores = homeScores,
            awayScores = awayScores,
            onHomeScoreChange = onHomeScoreChange,
            onAwayScoreChange = onAwayScoreChange,
        )
        H3 { Text("Orden final esperado") }
        orderError?.let {
            P(attrs = { classes("error-text", "group-order-error") }) { Text(it) }
        }
        Div(attrs = { classes("ranking-inputs") }) {
            TeamSelectField("1er puesto", group.teams, value = positions["${group.code}-1"].orEmpty(), elementId = "pos-${group.code}-1") { onPositionChange(group.code, "1", it) }
            TeamSelectField("2do puesto", group.teams, value = positions["${group.code}-2"].orEmpty(), elementId = "pos-${group.code}-2") { onPositionChange(group.code, "2", it) }
            TeamSelectField("3er puesto", group.teams, value = positions["${group.code}-3"].orEmpty(), elementId = "pos-${group.code}-3") { onPositionChange(group.code, "3", it) }
            TeamSelectField("4to puesto", group.teams, value = positions["${group.code}-4"].orEmpty(), elementId = "pos-${group.code}-4") { onPositionChange(group.code, "4", it) }
        }
    }
}

@Composable
fun BestThirdPlacePredictionCard(
    groups: List<TournamentGroup>,
    selectedCodes: Set<String>? = null,
    onToggle: ((String, Boolean) -> Unit)? = null,
) {
    val internalSelected = remember { mutableStateOf(setOf<String>()) }
    val selected = selectedCodes ?: internalSelected.value
    val atLimit = selected.size >= 8

    Section(attrs = { classes("panel", "group-card") }) {
        H2 { Text("Mejores terceros") }
        P { Text("Marca exactamente 8 grupos: esos terceros lugares son los que crees que también pasan a la ronda de 32.") }
        Div(attrs = { classes("third-place-counter") }) {
            Span(attrs = { classes("counter-value") }) { Text("${selected.size}/8") }
            Span(attrs = { classes("counter-label") }) { Text(" seleccionados") }
        }
        Div(attrs = { classes("checkbox-grid") }) {
            groups.forEach { group ->
                val isChecked = group.code in selected
                val disabled = atLimit && !isChecked
                Label(attrs = {
                    classes("checkbox-field")
                    if (disabled) classes("checkbox-field-disabled")
                }) {
                    Input(type = InputType.Checkbox, attrs = {
                        id("tp-${group.code}")
                        checked(isChecked)
                        if (disabled) {
                            attr("disabled", "disabled")
                        }
                        onChange { event ->
                            val current = selected.toMutableSet()
                            if (event.value) {
                                if (current.size < 8) current.add(group.code)
                            } else {
                                current.remove(group.code)
                            }
                            if (onToggle != null) onToggle(group.code, group.code in current) else internalSelected.value = current
                        }
                    })
                    Span { Text("3er puesto del Grupo ${group.code} clasifica") }
                }
            }
        }
    }
}

@Composable
fun MatchPredictionTable(
    matches: List<GroupMatch>,
    homeScores: Map<String, String> = emptyMap(),
    awayScores: Map<String, String> = emptyMap(),
    onHomeScoreChange: (String, String) -> Unit = { _, _ -> },
    onAwayScoreChange: (String, String) -> Unit = { _, _ -> },
) {
    Table(attrs = { classes("table") }) {
        Thead {
            Tr {
                Th { Text("Partido") }
                Th { Text("Equipo A") }
                Th { Text("Goles A") }
                Th { Text("Goles B") }
                Th { Text("Equipo B") }
            }
        }
        Tbody {
            matches.forEach { match ->
                Tr {
                    Td { Text(match.label) }
                    Td { Text(match.homeTeam.name) }
                    Td { NumberInput("score-home-${match.id}", homeScores[match.id].orEmpty()) { onHomeScoreChange(match.id, it) } }
                    Td { NumberInput("score-away-${match.id}", awayScores[match.id].orEmpty()) { onAwayScoreChange(match.id, it) } }
                    Td { Text(match.awayTeam.name) }
                }
            }
        }
    }
}

@Composable
fun NumberInput(elementId: String = "", fieldValue: String = "", onValueChange: (String) -> Unit = {}) {
    Input(type = InputType.Number, attrs = {
        classes("score-input")
        attr("min", "0")
        if (elementId.isNotEmpty()) id(elementId)
        value(fieldValue)
        onInput { e -> onValueChange(e.target.value) }
    })
}

@Composable
fun SubmitPreviewButton(label: String) {
    Div(attrs = { classes("form-actions") }) {
        Button(attrs = { classes("button", "button-primary") }) { Text(label) }
    }
}

@Composable
fun KnockoutPredictionTable(
    matches: List<KnockoutMatch>,
    homeScores: Map<String, String> = emptyMap(),
    awayScores: Map<String, String> = emptyMap(),
    winners: Map<String, String> = emptyMap(),
    onHomeScoreChange: (String, String) -> Unit = { _, _ -> },
    onAwayScoreChange: (String, String) -> Unit = { _, _ -> },
    onWinnerChange: (String, String) -> Unit = { _, _ -> },
) {
    matches.groupBy { it.phase }.forEach { (phase, phaseMatches) ->
        H3(attrs = { classes("phase-title") }) { Text(phaseLabel(phase)) }
        Table(attrs = { classes("table") }) {
            Thead {
                Tr {
                    Th { Text("Cruce") }
                    Th { Text("Equipo A") }
                    Th { Text("Goles A") }
                    Th { Text("Goles B") }
                    Th { Text("Equipo B") }
                    Th { Text("Ganador / clasifica") }
                }
            }
            Tbody {
                phaseMatches.forEach { match ->
                    Tr {
                        Td { Text(match.label) }
                        Td { Text(match.homeSlot) }
                        Td { NumberInput("score-home-${match.id}", homeScores[match.id].orEmpty()) { onHomeScoreChange(match.id, it) } }
                        Td { NumberInput("score-away-${match.id}", awayScores[match.id].orEmpty()) { onAwayScoreChange(match.id, it) } }
                        Td { Text(match.awaySlot) }
                        Td { InlineTeamSelect(match.options, "winner-${match.id}", winners[match.id].orEmpty()) { onWinnerChange(match.id, it) } }
                    }
                }
            }
        }
    }
}

@Composable
fun InlineTeamSelect(teams: List<Team>, elementId: String = "", value: String = "", onValueChange: (String) -> Unit = {}) {
    Select(attrs = {
        classes("input", "compact-select")
        if (elementId.isNotEmpty()) id(elementId)
        attr("value", value)
        onInput { e -> onValueChange(e.target.value) }
    }) {
        Option(value = "", attrs = { if (value.isEmpty()) attr("selected", "selected") }) { Text("Elige") }
        teams.forEach { team -> Option(value = team.id, attrs = { if (team.id == value) attr("selected", "selected") }) { Text(team.name) } }
    }
}

private fun phaseLabel(phase: String): String = when (phase) {
    "round_32" -> "Ronda de 32"
    "round_16" -> "Octavos de final"
    "quarter_final" -> "Cuartos de final"
    "semi_final" -> "Semifinales"
    "third_place" -> "Tercer puesto"
    "final" -> "Final"
    else -> phase
}

@Composable
fun StatCard(label: String, value: String) {
    Div(attrs = { classes("stat-card") }) {
        Span(attrs = { classes("stat-value") }) { Text(value) }
        Span(attrs = { classes("stat-label") }) { Text(label) }
    }
}

@Composable
fun RankingTable(entries: List<RankingEntry>) {
    Table(attrs = { classes("table") }) {
        Thead {
            Tr {
                Th { Text("#") }
                Th { Text("Participante") }
                Th { Text("Grupos") }
                Th { Text("Eliminatorias") }
                Th { Text("Total") }
            }
        }
        Tbody {
            entries.forEach { entry ->
                Tr {
                    Td { Text(entry.position.toString()) }
                    Td { Text(entry.participantName) }
                    Td { Text(entry.groupPoints.toString()) }
                    Td { Text(entry.knockoutPoints.toString()) }
                    Td { Span(attrs = { classes("points") }) { Text(entry.totalPoints.toString()) } }
                }
            }
        }
    }
}

@Composable
fun MatchResults(results: List<MatchResult>) {
    results.forEach { result ->
        val score = result.score.split("-").map { it.trim() }
        val homeScore = score.getOrNull(0) ?: result.score
        val awayScore = score.getOrNull(1) ?: ""

        Div(attrs = { classes("match-result") }) {
            Span(attrs = { classes("match-team") }) {
                if (result.homeFifaCode != null) {
                    Img(src = flagSrc(result.homeFifaCode), attrs = { classes("team-flag") })
                }
                Span(attrs = { classes("match-team-name") }) { Text(result.homeTeam) }
            }

            Span(attrs = { classes("match-score") }) {
                Span(attrs = { classes("match-score-line") }) {
                    Text(homeScore)
                    Span(attrs = { classes("match-score-separator") }) { Text("-") }
                    Text(awayScore)
                }
                Span(attrs = { classes("status-pill") }) {
                    Span { Text(result.status) }
                    if (result.kickoffAt != null) {
                        Span { Text(result.kickoffAt) }
                    }
                }
            }

            Span(attrs = { classes("match-team") }) {
                if (result.awayFifaCode != null) {
                    Img(src = flagSrc(result.awayFifaCode), attrs = { classes("team-flag") })
                }
                Span(attrs = { classes("match-team-name") }) { Text(result.awayTeam) }
            }
        }
    }
}

private fun flagSrc(fifaCode: String): String = "/flags/${fifaCode.lowercase()}.svg"

@Composable
fun ParticipantApprovalTable(participants: List<PaymentParticipant>) {
    Table(attrs = { classes("table") }) {
        Thead {
            Tr {
                Th { Text("Nombre") }
                Th { Text("Correo") }
                Th { Text("Pago") }
                Th { Text("Estado") }
                Th { Text("Acción") }
            }
        }
        Tbody {
            participants.forEach { participant ->
                Tr {
                    Td { Text(participant.name) }
                    Td { Text(participant.email) }
                    Td { Span(attrs = { classes("status-pill") }) { Text(participant.paymentStatus) } }
                    Td { Text(participant.approvalStatus) }
                    Td { Button(attrs = { classes("button", "button-small") }) { Text("Aprobar") } }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsForm(settings: AdminSettings) {
    FormGrid {
        TextField("Estado fase de grupos", "", fieldValue = settings.groupsStatus)
        TextField("Estado eliminatorias", "", fieldValue = settings.knockoutsStatus)
        TextField("Fecha límite grupos", "", fieldValue = settings.groupDeadline)
        TextField("Teléfono Bizum", "", fieldValue = settings.bizumPhone)
    }
}

@Composable
fun AdminCard(title: String, body: String, route: Route) {
    Div(attrs = { classes("card") }) {
        H3 { Text(title) }
        P { Text(body) }
        ActionLink("Abrir", route.path, primary = false)
    }
}

@Composable
fun Panel(title: String, content: @Composable () -> Unit) {
    Section(attrs = { classes("panel") }) {
        H2 { Text(title) }
        content()
    }
}
