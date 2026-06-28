package com.porraweb.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.jetbrains.compose.web.css.style
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

private const val CARD_W = 208
private const val CONN_W = 34

private val INTERNAL_SLOT_REGEX = Regex("^(W\\d+|RU\\d+).*$")

private fun placeholderSlot(slot: String?): String {
    if (slot.isNullOrBlank()) return "Por definir"
    return if (slot.trim().matches(INTERNAL_SLOT_REGEX)) "Por definir" else slot
}

private fun canSelectWinner(match: KnockoutMatch, winners: Map<String, String>, losers: Map<String, String>): Boolean {
    val homeSource = match.homeFromMatchId
    val awaySource = match.awayFromMatchId
    if (homeSource == null && awaySource == null) return true
    if (homeSource == null || awaySource == null) return false

    val homeSourceReady = winners[homeSource]?.isNotEmpty() == true
    val awaySourceReady = winners[awaySource]?.isNotEmpty() == true
    if (!homeSourceReady || !awaySourceReady) return false

    val loserBased = match.homeFromResult == "loser" || match.awayFromResult == "loser"
    if (loserBased) {
        val homeLoserReady = losers[homeSource] != null
        val awayLoserReady = losers[awaySource] != null
        return homeLoserReady && awayLoserReady
    }
    return true
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
    // NO usar remember(winners): winners es un SnapshotStateMap (misma instancia siempre),
    // asi que la key nunca "cambia" y el bloque se congelaria con winners vacio.
    // Llamar directo hace que los reads de winners[...] registren este composable como
    // observador -> recompone y recomputa losers cuando cambian los ganadores en cascada.
    val losers = computeKnockoutLosers(matches, winners)
    val sortedByBracket = remember(matches) { sortMatchesByBracketTree(matches) }
    val matchesByPhase = sortedByBracket.groupBy { it.phase }
    val r32 = matchesByPhase["round_32"] ?: emptyList()
    val r16 = matchesByPhase["round_16"] ?: emptyList()
    val qf = matchesByPhase["quarter_final"] ?: emptyList()
    val sf = matchesByPhase["semi_final"] ?: emptyList()
    val finalMatch = matchesByPhase["final"]?.firstOrNull()
    val thirdMatch = matchesByPhase["third_place"]?.firstOrNull()

    if (r32.isEmpty()) {
        legacyKnockoutTable(matches, homeScores, awayScores, winners, losers, onHomeScoreChange, onAwayScoreChange, onWinnerChange)
        return
    }

    Div(attrs = { classes("bracket-scroll") }) {
        Div(attrs = { classes("bracket-canvas") }) {
            bracketRound("Ronda de 32", 1, r32, homeScores, awayScores, winners, losers, onHomeScoreChange, onAwayScoreChange, onWinnerChange)
            bracketConnector(1, 2, 8)
            bracketRound("Octavos de final", 2, r16, homeScores, awayScores, winners, losers, onHomeScoreChange, onAwayScoreChange, onWinnerChange)
            bracketConnector(2, 4, 4)
            bracketRound("Cuartos de final", 4, qf, homeScores, awayScores, winners, losers, onHomeScoreChange, onAwayScoreChange, onWinnerChange)
            bracketConnector(4, 8, 2)
            bracketRound("Semifinales", 8, sf, homeScores, awayScores, winners, losers, onHomeScoreChange, onAwayScoreChange, onWinnerChange)

            Div(attrs = {
                classes("bracket-connector")
                style {
                    property("position", "relative")
                    property("width", "${CONN_W}px")
                    property("flex-shrink", "0")
                }
            }) {
                finalsConnectorLines()
            }

            Div(attrs = {
                classes("bracket-round", "bracket-finals")
                style {
                    property("width", "${CARD_W}px")
                    property("flex-shrink", "0")
                    property("display", "flex")
                    property("flex-direction", "column")
                }
            }) {
                Div(attrs = { classes("bracket-round-label") }) {
                    Span(attrs = { classes("bracket-round-title") }) { Text("Finales") }
                }
                Div(attrs = {
                    classes("bracket-round-grid")
                    style {
                        property("display", "grid")
                        property("grid-template-rows", "1fr 1fr")
                    }
                }) {
                    Div(attrs = {
                        style {
                            property("grid-row", "1 / 2")
                            property("display", "flex")
                            property("flex-direction", "column")
                            property("align-items", "center")
                            property("justify-content", "center")
                            property("gap", "4px")
                        }
                    }) {
                        Span(attrs = { style { property("font-size", "10px"); property("font-weight", "700"); property("color", "#64748b"); property("text-transform", "uppercase") } }) { Text("1er y 2do puesto") }
                        finalMatch?.let { m ->
                            bracketMatchCard(m, homeScores, awayScores, winners, losers, onHomeScoreChange, onAwayScoreChange, onWinnerChange)
                        }
                    }
                    Div(attrs = {
                        style {
                            property("grid-row", "2 / 3")
                            property("display", "flex")
                            property("flex-direction", "column")
                            property("align-items", "center")
                            property("justify-content", "center")
                            property("gap", "4px")
                        }
                    }) {
                        Span(attrs = { style { property("font-size", "10px"); property("font-weight", "700"); property("color", "#64748b"); property("text-transform", "uppercase") } }) { Text("3er puesto") }
                        thirdMatch?.let { m ->
                            bracketMatchCard(m, homeScores, awayScores, winners, losers, onHomeScoreChange, onAwayScoreChange, onWinnerChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun bracketRound(
    label: String,
    rowSpan: Int,
    matches: List<KnockoutMatch>,
    homeScores: Map<String, String>,
    awayScores: Map<String, String>,
    winners: Map<String, String>,
    losers: Map<String, String>,
    onHomeScoreChange: (String, String) -> Unit,
    onAwayScoreChange: (String, String) -> Unit,
    onWinnerChange: (String, String) -> Unit,
) {
    val filled = matches.count { winners[it.id]?.isNotEmpty() == true }
    val total = matches.size

    Div(attrs = {
        classes("bracket-round")
        style {
            property("width", "${CARD_W}px")
            property("flex-shrink", "0")
            property("display", "flex")
            property("flex-direction", "column")
        }
    }) {
        Div(attrs = { classes("bracket-round-label") }) {
            Span(attrs = { classes("bracket-round-title") }) { Text(label) }
            Span(attrs = { classes("bracket-round-count") }) { Text("$filled/$total") }
        }
        Div(attrs = {
            classes("bracket-round-grid")
            style {
                property("display", "grid")
                property("grid-template-rows", "repeat(16, 1fr)")
            }
        }) {
            matches.forEachIndexed { index, match ->
                val rowStart = index * rowSpan + 1
                val rowEnd = rowStart + rowSpan
                Div(attrs = {
                    style {
                        property("grid-row", "$rowStart / $rowEnd")
                        property("display", "flex")
                        property("align-items", "center")
                        property("justify-content", "center")
                    }
                }) {
                    bracketMatchCard(match, homeScores, awayScores, winners, losers, onHomeScoreChange, onAwayScoreChange, onWinnerChange)
                }
            }
        }
    }
}

@Composable
private fun bracketConnector(sourceSpan: Int, targetSpan: Int, numTargets: Int) {
    Div(attrs = {
        classes("bracket-connector")
        style {
            property("position", "relative")
            property("width", "${CONN_W}px")
            property("flex-shrink", "0")
        }
    }) {
        val total = 16.0
        for (i in 0 until numTargets) {
            val srcCenter0 = ((i * 2) * sourceSpan + sourceSpan / 2.0) / total
            val srcCenter1 = ((i * 2 + 1) * sourceSpan + sourceSpan / 2.0) / total
            val tgtCenter = (i * targetSpan + targetSpan / 2.0) / total
            val connHeight = srcCenter1 - srcCenter0
            val armTop = if (connHeight > 0) ((tgtCenter - srcCenter0) / connHeight) * 100.0 else 50.0
            val halfW = "${CONN_W / 2}px"

            Div(attrs = {
                style {
                    property("position", "absolute")
                    property("top", "${srcCenter0 * 100}%")
                    property("height", "${connHeight * 100}%")
                    property("left", "0")
                    property("width", halfW)
                    property("border-right", "2px solid #cbd5e1")
                }
            })
            Div(attrs = {
                style {
                    property("position", "absolute")
                    property("top", "${srcCenter0 * 100}%")
                    property("left", "0")
                    property("width", halfW)
                    property("height", "2px")
                    property("background", "#cbd5e1")
                }
            })
            Div(attrs = {
                style {
                    property("position", "absolute")
                    property("top", "${srcCenter1 * 100}%")
                    property("left", "0")
                    property("width", halfW)
                    property("height", "2px")
                    property("background", "#cbd5e1")
                }
            })
            Div(attrs = {
                style {
                    property("position", "absolute")
                    property("top", "${tgtCenter * 100}%")
                    property("left", halfW)
                    property("right", "0")
                    property("height", "2px")
                    property("background", "#cbd5e1")
                }
            })
        }
    }
}

private fun finalsConnectorLines(): @Composable () -> Unit = {
    val hw = "${CONN_W / 2}px"
    val total = 16.0
    val sf0Center = 4.5 / total * 100
    val sf1Center = 12.5 / total * 100
    val finalCenter = 4.5 / total * 100
    val thirdCenter = 12.5 / total * 100

    Div(attrs = {
        style {
            property("position", "absolute")
            property("top", "${sf0Center}%")
            property("bottom", "${100.0 - sf1Center}%")
            property("left", "0")
            property("width", hw)
            property("border-right", "2px solid #cbd5e1")
        }
    })
    Div(attrs = {
        style {
            property("position", "absolute")
            property("top", "${sf0Center}%")
            property("left", "0")
            property("width", hw)
            property("height", "2px")
            property("background", "#cbd5e1")
        }
    })
    Div(attrs = {
        style {
            property("position", "absolute")
            property("top", "${sf1Center}%")
            property("left", "0")
            property("width", hw)
            property("height", "2px")
            property("background", "#cbd5e1")
        }
    })
    Div(attrs = {
        style {
            property("position", "absolute")
            property("top", "${finalCenter}%")
            property("left", hw)
            property("right", "0")
            property("height", "2px")
            property("background", "#cbd5e1")
        }
    })
    Div(attrs = {
        style {
            property("position", "absolute")
            property("top", "${thirdCenter}%")
            property("left", hw)
            property("right", "0")
            property("height", "2px")
            property("background", "#cbd5e1")
        }
    })
}

private fun computeKnockoutLosers(matches: List<KnockoutMatch>, winners: Map<String, String>): Map<String, String> {
    val losers = mutableMapOf<String, String>()
    val resolvedHome = mutableMapOf<String, Team?>()
    val resolvedAway = mutableMapOf<String, Team?>()

    for (match in matches) {
        val hfw = match.homeFromMatchId?.let { winners[it] }
        val afw = match.awayFromMatchId?.let { winners[it] }
        resolvedHome[match.id] = when {
            hfw != null -> match.options.find { it.id == hfw }
            match.homeTeam != null -> match.homeTeam
            else -> null
        }
        resolvedAway[match.id] = when {
            afw != null -> match.options.find { it.id == afw }
            match.awayTeam != null -> match.awayTeam
            else -> null
        }
    }

    for (match in matches) {
        val winner = winners[match.id] ?: continue
        val home = resolvedHome[match.id] ?: continue
        val away = resolvedAway[match.id] ?: continue
        val loserId = when (winner) {
            home.id -> away.id
            away.id -> home.id
            else -> null
        }
        if (loserId != null) losers[match.id] = loserId
    }

    return losers
}

private fun sortMatchesByBracketTree(matches: List<KnockoutMatch>): List<KnockoutMatch> {
    val matchesById = matches.associateBy { it.id }
    val finalMatch = matches.find { it.phase == "final" }
        ?: return matches.sortedBy { it.matchNumber ?: 0 }

    val r32Order = mutableListOf<String>()
    fun visit(matchId: String?) {
        if (matchId == null) return
        val m = matchesById[matchId] ?: return
        if (m.phase == "round_32") {
            r32Order.add(matchId)
            return
        }
        visit(m.homeFromMatchId)
        visit(m.awayFromMatchId)
    }
    visit(finalMatch.id)

    val position = mutableMapOf<String, Double>()
    fun computePos(matchId: String?, visited: MutableSet<String> = mutableSetOf()): Double {
        if (matchId == null) return 0.0
        if (matchId in position) return position[matchId]!!
        val m = matchesById[matchId] ?: return 0.0
        if (m.phase == "round_32") {
            val idx = r32Order.indexOf(matchId).toDouble()
            position[matchId] = idx
            return idx
        }
        val left = computePos(m.homeFromMatchId, visited)
        val right = computePos(m.awayFromMatchId, visited)
        val avg = (left + right) / 2.0
        position[matchId] = avg
        return avg
    }
    for (match in matches) {
        computePos(match.id)
    }

    return matches.sortedBy { position[it.id] ?: (it.matchNumber?.toDouble() ?: 999.0) }
}

@Composable
private fun bracketMatchCard(
    match: KnockoutMatch,
    homeScores: Map<String, String>,
    awayScores: Map<String, String>,
    winners: Map<String, String>,
    losers: Map<String, String>,
    onHomeScoreChange: (String, String) -> Unit,
    onAwayScoreChange: (String, String) -> Unit,
    onWinnerChange: (String, String) -> Unit,
) {
    val homeFromWinner = match.homeFromMatchId?.let { winners[it] }
    val awayFromWinner = match.awayFromMatchId?.let { winners[it] }
    val homeFromLoserId = if (match.homeFromResult == "loser") match.homeFromMatchId?.let { losers[it] } else null
    val awayFromLoserId = if (match.awayFromResult == "loser") match.awayFromMatchId?.let { losers[it] } else null

    val resolvedHome: Team? = when {
        match.homeFromResult == "loser" -> homeFromLoserId?.let { match.options.find { o -> o.id == it } }
        homeFromWinner != null -> match.options.find { it.id == homeFromWinner }
        match.homeTeam != null -> match.homeTeam
        else -> null
    }
    val resolvedAway: Team? = when {
        match.awayFromResult == "loser" -> awayFromLoserId?.let { match.options.find { o -> o.id == it } }
        awayFromWinner != null -> match.options.find { it.id == awayFromWinner }
        match.awayTeam != null -> match.awayTeam
        else -> null
    }

    val homeName = resolvedHome?.name ?: placeholderSlot(match.homeSlot)
    val awayName = resolvedAway?.name ?: placeholderSlot(match.awaySlot)
    val homePlaceholder = resolvedHome == null
    val awayPlaceholder = resolvedAway == null

    val selectOptions = when {
        match.homeTeam != null && match.awayTeam != null && match.homeFromMatchId == null ->
            listOf(match.homeTeam!!, match.awayTeam!!)
        resolvedHome != null && resolvedAway != null -> listOf(resolvedHome, resolvedAway)
        resolvedHome != null -> listOf(resolvedHome)
        resolvedAway != null -> listOf(resolvedAway)
        match.homeFromMatchId == null -> match.options
        else -> emptyList()
    }

    val canSelect = canSelectWinner(match, winners, losers)

    // Si el ganador guardado dejo de ser una opcion valida (porque cambio una ronda
    // anterior de la cascada), limpialo para que el dropdown no quede con una
    // seleccion obsoleta. El clear propaga rio abajo y converge solo.
    val currentWinner = winners[match.id].orEmpty()
    LaunchedEffect(selectOptions.map { it.id }, currentWinner) {
        if (currentWinner.isNotEmpty() && selectOptions.none { it.id == currentWinner }) {
            onWinnerChange(match.id, "")
        }
    }

    Div(attrs = { classes("bracket-card") }) {
        Span(attrs = { classes("bracket-card-label") }) { Text(match.label) }

        Div(attrs = { classes("bracket-teams") }) {
            Div(attrs = { classes("bracket-team") }) {
                if (resolvedHome?.fifaCode != null) {
                    Img(src = flagSrc(resolvedHome.fifaCode!!), attrs = { classes("bracket-flag") })
                }
                Span(attrs = {
                    if (homePlaceholder) classes("bracket-team-ph") else classes("bracket-team-name")
                }) { Text(homeName) }
                NumberInput("score-home-${match.id}", homeScores[match.id].orEmpty()) { onHomeScoreChange(match.id, it) }
            }
            Div(attrs = { classes("bracket-team") }) {
                if (resolvedAway?.fifaCode != null) {
                    Img(src = flagSrc(resolvedAway.fifaCode!!), attrs = { classes("bracket-flag") })
                }
                Span(attrs = {
                    if (awayPlaceholder) classes("bracket-team-ph") else classes("bracket-team-name")
                }) { Text(awayName) }
                NumberInput("score-away-${match.id}", awayScores[match.id].orEmpty()) { onAwayScoreChange(match.id, it) }
            }
        }

        Div(attrs = { classes("bracket-winner") }) {
            Span(attrs = { classes("bracket-winner-label") }) { Text("Ganador") }
            InlineTeamSelect(
                teams = selectOptions,
                elementId = "winner-${match.id}",
                value = winners[match.id].orEmpty(),
                onValueChange = { onWinnerChange(match.id, it) },
                disabled = !canSelect,
            )
            if (!canSelect) {
                Span(attrs = { classes("bracket-winner-hint") }) {
                    Text("Llena las rondas anteriores")
                }
            }
        }
    }
}

@Composable
private fun legacyKnockoutTable(
    matches: List<KnockoutMatch>,
    homeScores: Map<String, String>,
    awayScores: Map<String, String>,
    winners: Map<String, String>,
    losers: Map<String, String>,
    onHomeScoreChange: (String, String) -> Unit,
    onAwayScoreChange: (String, String) -> Unit,
    onWinnerChange: (String, String) -> Unit,
) {
    val phaseOrder = listOf("round_32", "round_16", "quarter_final", "semi_final", "third_place", "final")
    val phaseLabels = mapOf(
        "round_32" to "Ronda de 32 (16 partidos)",
        "round_16" to "Octavos de final (8 partidos)",
        "quarter_final" to "Cuartos de final (4 partidos)",
        "semi_final" to "Semifinales (2 partidos)",
        "third_place" to "Tercer puesto",
        "final" to "Final",
    )

    phaseOrder.forEach { phase ->
        val phaseMatches = matches.filter { it.phase == phase }
        if (phaseMatches.isEmpty()) return@forEach

        val filledCount = phaseMatches.count { winners[it.id]?.isNotEmpty() == true }
        val total = phaseMatches.size
        val isExpanded = remember { mutableStateOf(phase == "round_32") }

        Div(attrs = { classes("round-section") }) {
            Div(attrs = {
                classes("round-header")
                onClick { isExpanded.value = !isExpanded.value }
            }) {
                Span(attrs = { classes("round-title") }) { Text(phaseLabels[phase] ?: phase) }
                Span(attrs = { classes("round-badge") }) { Text("$filledCount/$total") }
            }
            if (isExpanded.value) {
                Div(attrs = { classes("round-matches") }) {
                    phaseMatches.forEach { match ->
                        KnockoutMatchCard(
                            match = match,
                            homeScore = homeScores[match.id].orEmpty(),
                            awayScore = awayScores[match.id].orEmpty(),
                            winner = winners[match.id].orEmpty(),
                            homeFromWinner = match.homeFromMatchId?.let { winners[it] },
                            awayFromWinner = match.awayFromMatchId?.let { winners[it] },
                            homeFromLoser = if (match.homeFromResult == "loser") match.homeFromMatchId?.let { losers[it] } else null,
                            awayFromLoser = if (match.awayFromResult == "loser") match.awayFromMatchId?.let { losers[it] } else null,
                            winners = winners,
                            losers = losers,
                            onHomeScoreChange = onHomeScoreChange,
                            onAwayScoreChange = onAwayScoreChange,
                            onWinnerChange = onWinnerChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
    fun KnockoutMatchCard(
    match: KnockoutMatch,
    homeScore: String,
    awayScore: String,
    winner: String,
    homeFromWinner: String?,
    awayFromWinner: String?,
    homeFromLoser: String? = null,
    awayFromLoser: String? = null,
    winners: Map<String, String> = emptyMap(),
    losers: Map<String, String> = emptyMap(),
    onHomeScoreChange: (String, String) -> Unit,
    onAwayScoreChange: (String, String) -> Unit,
    onWinnerChange: (String, String) -> Unit,
) {
    val resolvedHome: Team? = when {
        match.homeFromResult == "loser" -> homeFromLoser?.let { match.options.find { o -> o.id == it } }
        homeFromWinner != null -> match.options.find { it.id == homeFromWinner }
        match.homeTeam != null -> match.homeTeam
        else -> null
    }
    val resolvedAway: Team? = when {
        match.awayFromResult == "loser" -> awayFromLoser?.let { match.options.find { o -> o.id == it } }
        awayFromWinner != null -> match.options.find { it.id == awayFromWinner }
        match.awayTeam != null -> match.awayTeam
        else -> null
    }

    val homeName = resolvedHome?.name ?: placeholderSlot(match.homeSlot)
    val awayName = resolvedAway?.name ?: placeholderSlot(match.awaySlot)
    val homePlaceholder = resolvedHome == null
    val awayPlaceholder = resolvedAway == null

    val selectOptions = when {
        match.homeTeam != null && match.awayTeam != null && match.homeFromMatchId == null ->
            listOf(match.homeTeam!!, match.awayTeam!!)
        resolvedHome != null && resolvedAway != null -> listOf(resolvedHome, resolvedAway)
        resolvedHome != null -> listOf(resolvedHome)
        resolvedAway != null -> listOf(resolvedAway)
        match.homeFromMatchId == null -> match.options
        else -> emptyList()
    }

    val canSelect = canSelectWinner(match, winners, losers)

    LaunchedEffect(selectOptions.map { it.id }, winner) {
        if (winner.isNotEmpty() && selectOptions.none { it.id == winner }) {
            onWinnerChange(match.id, "")
        }
    }

    Div(attrs = { classes("ko-card") }) {
        Span(attrs = { classes("ko-card-label") }) { Text(match.label) }
        Div(attrs = { classes("ko-teams") }) {
            Div(attrs = { classes("ko-team") }) {
                if (resolvedHome?.fifaCode != null) {
                    Img(src = flagSrc(resolvedHome.fifaCode!!), attrs = { classes("team-flag-sm") })
                }
                Span(attrs = {
                    if (homePlaceholder) classes("ko-team-ph")
                    else classes("ko-team-name")
                }) { Text(homeName) }
                NumberInput("score-home-${match.id}", homeScore) { onHomeScoreChange(match.id, it) }
            }
            Div(attrs = { classes("ko-vs") }) { Text("vs") }
            Div(attrs = { classes("ko-team") }) {
                if (resolvedAway?.fifaCode != null) {
                    Img(src = flagSrc(resolvedAway.fifaCode!!), attrs = { classes("team-flag-sm") })
                }
                Span(attrs = {
                    if (awayPlaceholder) classes("ko-team-ph")
                    else classes("ko-team-name")
                }) { Text(awayName) }
                NumberInput("score-away-${match.id}", awayScore) { onAwayScoreChange(match.id, it) }
            }
        }
        Div(attrs = { classes("ko-winner") }) {
            Text("Ganador: ")
            InlineTeamSelect(
                teams = selectOptions,
                elementId = "winner-${match.id}",
                value = winner,
                onValueChange = { onWinnerChange(match.id, it) },
                disabled = !canSelect,
            )
            if (!canSelect) {
                Span(attrs = { classes("ko-winner-hint") }) {
                    Text("Llena las rondas anteriores")
                }
            }
        }
    }
}



@Composable
fun InlineTeamSelect(
    teams: List<Team>,
    elementId: String = "",
    value: String = "",
    onValueChange: (String) -> Unit = {},
    disabled: Boolean = false,
) {
    Select(attrs = {
        classes("input", "compact-select")
        if (elementId.isNotEmpty()) id(elementId)
        if (disabled) attr("disabled", "disabled")
        attr("value", value)
        onInput { e -> onValueChange(e.target.value) }
    }) {
        Option(value = "", attrs = { if (value.isEmpty()) attr("selected", "selected") }) { Text(if (disabled) "Bloqueado" else "Elige") }
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
