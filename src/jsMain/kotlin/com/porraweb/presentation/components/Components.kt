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
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Header
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
    Div(attrs = { classes("topbar") }) {
        A(href = Route.Home.path, attrs = { classes("brand") }) { Text("PorraWeb 2026") }
        Div(attrs = { classes("nav-links") }) {
            NavLink(route = Route.GroupPredictions, current = current)
            NavLink(route = Route.KnockoutPredictions, current = current)
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
fun TextField(label: String, placeholder: String, type: InputType<*> = InputType.Text) {
    Label(attrs = { classes("field") }) {
        Span { Text(label) }
        Input(type = type, attrs = {
            classes("input")
            attr("placeholder", placeholder)
        })
    }
}

@Composable
fun TeamSelectField(label: String, teams: List<Team>, placeholder: String = "Selecciona equipo") {
    Label(attrs = { classes("field") }) {
        Span { Text(label) }
        Select(attrs = { classes("input") }) {
            Option(value = "") { Text(placeholder) }
            teams.forEach { team ->
                Option(value = team.id) { Text(team.name) }
            }
        }
    }
}

@Composable
fun GroupPredictionCard(group: TournamentGroup) {
    Section(attrs = { classes("panel", "group-card") }) {
        H2 { Text("Grupo ${group.code}") }
        P { Text("Predice los 6 partidos y el orden final. Los dos primeros clasifican directo; el tercero puede entrar entre los 8 mejores terceros.") }
        Div(attrs = { classes("team-chips") }) {
            group.teams.forEach { team -> Span(attrs = { classes("team-chip") }) { Text(team.name) } }
        }
        MatchPredictionTable(matches = group.matches)
        H3 { Text("Orden final esperado") }
        Div(attrs = { classes("ranking-inputs") }) {
            TeamSelectField("1er puesto", group.teams)
            TeamSelectField("2do puesto", group.teams)
            TeamSelectField("3er puesto", group.teams)
            TeamSelectField("4to puesto", group.teams)
        }
    }
}

@Composable
fun BestThirdPlacePredictionCard(groups: List<TournamentGroup>) {
    val selected = remember { mutableStateOf(setOf<String>()) }
    val atLimit = selected.value.size >= 8

    Section(attrs = { classes("panel", "group-card") }) {
        H2 { Text("Mejores terceros") }
        P { Text("Marca exactamente 8 grupos: esos terceros lugares son los que crees que también pasan a la ronda de 32.") }
        Div(attrs = { classes("third-place-counter") }) {
            Span(attrs = { classes("counter-value") }) { Text("${selected.value.size}/8") }
            Span(attrs = { classes("counter-label") }) { Text(" seleccionados") }
        }
        Div(attrs = { classes("checkbox-grid") }) {
            groups.forEach { group ->
                val isChecked = group.code in selected.value
                val disabled = atLimit && !isChecked
                Label(attrs = {
                    classes("checkbox-field")
                    if (disabled) classes("checkbox-field-disabled")
                }) {
                    Input(type = InputType.Checkbox, attrs = {
                        checked(isChecked)
                        if (disabled) {
                            attr("disabled", "disabled")
                        }
                        onChange { event ->
                            val current = selected.value.toMutableSet()
                            if (event.value) {
                                if (current.size < 8) current.add(group.code)
                            } else {
                                current.remove(group.code)
                            }
                            selected.value = current
                        }
                    })
                    Span { Text("3er puesto del Grupo ${group.code} clasifica") }
                }
            }
        }
    }
}

@Composable
fun MatchPredictionTable(matches: List<GroupMatch>) {
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
                    Td { NumberInput() }
                    Td { NumberInput() }
                    Td { Text(match.awayTeam.name) }
                }
            }
        }
    }
}

@Composable
fun NumberInput() {
    Input(type = InputType.Number, attrs = {
        classes("score-input")
        attr("min", "0")
    })
}

@Composable
fun SubmitPreviewButton(label: String) {
    Div(attrs = { classes("form-actions") }) {
        Button(attrs = { classes("button", "button-primary") }) { Text(label) }
        Span(attrs = { classes("helper-text") }) { Text("Modo local: todavía no guarda en Supabase.") }
    }
}

@Composable
fun KnockoutPredictionTable(matches: List<KnockoutMatch>) {
    val knockoutTeams = matches.flatMap { it.options }.distinctBy { it.id }

    matches.groupBy { it.phase }.forEach { (phase, phaseMatches) ->
        H3(attrs = { classes("phase-title") }) { Text(phase) }
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
                        Td { NumberInput() }
                        Td { NumberInput() }
                        Td { Text(match.awaySlot) }
                        Td { InlineTeamSelect(match.options) }
                    }
                }
            }
        }
    }

    Div(attrs = { classes("final-picks") }) {
        TeamSelectField("Campeón", knockoutTeams)
        TeamSelectField("Subcampeón", knockoutTeams)
        TeamSelectField("Tercer puesto", knockoutTeams)
        TeamSelectField("Cuarto puesto", knockoutTeams)
    }
}

@Composable
fun InlineTeamSelect(teams: List<Team>) {
    Select(attrs = { classes("input", "compact-select") }) {
        Option(value = "") { Text("Elige") }
        teams.forEach { team -> Option(value = team.id) { Text(team.name) } }
    }
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
        Div(attrs = { classes("match-result") }) {
            Span { Text(result.homeTeam) }
            Span(attrs = { classes("score-pill") }) { Text(result.score) }
            Span { Text(result.awayTeam) }
            Span(attrs = { classes("status-pill") }) { Text(result.status) }
        }
    }
}

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
        TextField("Estado fase de grupos", settings.groupsStatus)
        TextField("Estado eliminatorias", settings.knockoutsStatus)
        TextField("Fecha límite grupos", settings.groupDeadline)
        TextField("Teléfono Bizum", settings.bizumPhone)
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
