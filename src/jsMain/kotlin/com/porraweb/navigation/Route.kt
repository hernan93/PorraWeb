package com.porraweb.navigation

enum class Route(val path: String, val label: String) {
    Home("#/", "Inicio"),
    GroupPredictions("#/predicciones/grupos", "Participar"),
    KnockoutPredictions("#/predicciones/eliminatorias", "Eliminatorias"),
    Dashboard("#/dashboard", "Dashboard"),
    AdminLogin("#/admin/login", "Admin"),
    AdminHome("#/admin", "Panel"),
    AdminParticipants("#/admin/participantes", "Pagos"),
    AdminResults("#/admin/resultados", "Resultados"),
    AdminSettings("#/admin/configuracion", "Configuracion");

    companion object {
        fun fromHash(hash: String): Route = entries.firstOrNull { it.path == hash } ?: Home
    }
}
