package com.porraweb

import com.porraweb.data.mock.MockPorraRepository
import com.porraweb.presentation.app.PorraWebApp
import org.jetbrains.compose.web.renderComposable

fun main() {
    renderComposable(rootElementId = "root") {
        PorraWebApp(repository = MockPorraRepository)
    }
}
