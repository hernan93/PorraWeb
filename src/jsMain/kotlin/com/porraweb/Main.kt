package com.porraweb

import com.porraweb.data.supabase.AdminAuthFactory
import com.porraweb.data.supabase.PorraRepositoryFactory
import com.porraweb.data.supabase.SupabaseConfig
import com.porraweb.presentation.app.PorraWebApp
import org.jetbrains.compose.web.renderComposable

fun main() {
    val config = SupabaseConfig.fromWindow()
    val repository = PorraRepositoryFactory.create()
    val authService = AdminAuthFactory.create(config)

    renderComposable(rootElementId = "root") {
        PorraWebApp(repository = repository, authService = authService, config = config)
    }
}
