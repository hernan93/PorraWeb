package com.porraweb.presentation.screens

import androidx.compose.runtime.Composable
import com.porraweb.domain.repository.PorraRepository
import com.porraweb.presentation.components.BestThirdPlacePredictionCard
import com.porraweb.presentation.components.FormGrid
import com.porraweb.presentation.components.GroupPredictionCard
import com.porraweb.presentation.components.KnockoutPredictionTable
import com.porraweb.presentation.components.PageHeader
import com.porraweb.presentation.components.Panel
import com.porraweb.presentation.components.SubmitPreviewButton
import com.porraweb.presentation.components.TextField
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

@Composable
fun GroupPredictionsScreen(repository: PorraRepository) {
    PageHeader(
        title = "Predicciones de fase de grupos",
        subtitle = "Completa marcadores, orden final de los 12 grupos y selecciona los 8 mejores terceros del formato 2026.",
    )

    Panel(title = "Datos del participante") {
        FormGrid {
            TextField("Nombre completo", "Ej. Andrea Mora")
            TextField("Correo", "nombre@empresa.com", InputType.Email)
        }
    }

    repository.groups().forEach { group ->
        GroupPredictionCard(group)
    }

    BestThirdPlacePredictionCard(repository.groups())

    SubmitPreviewButton("Enviar predicciones de grupos")
}

@Composable
fun KnockoutPredictionsScreen(repository: PorraRepository) {
    PageHeader(
        title = "Predicciones de eliminatorias",
        subtitle = "El Mundial 2026 arranca la fase final en ronda de 32, no en octavos. Por eso hay una capa extra de predicción.",
    )

    Section(attrs = { classes("notice") }) {
        H3 { Text("Formato 2026") }
        P { Text("Son 48 equipos: 12 grupos de 4. Pasan 32 equipos: los 24 primeros/segundos y los 8 mejores terceros. La app real generará los cruces cuando el admin cargue los clasificados oficiales.") }
    }

    Panel(title = "Datos del participante") {
        FormGrid {
            TextField("Nombre completo", "Igual que en fase de grupos")
            TextField("Correo aprobado", "nombre@empresa.com", InputType.Email)
        }
    }

    Panel(title = "Ronda de 32, octavos, cuartos, semifinales y finales") {
        KnockoutPredictionTable(matches = repository.knockoutMatches())
        SubmitPreviewButton("Enviar predicciones de eliminatorias")
    }
}
