package com.porraweb.data.supabase

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

open class AdminAuthService(private val config: SupabaseConfig) {
    private val scope = MainScope()
    private val sessionStorageKey = "porraweb-admin-session"

    open var isLoggedIn by mutableStateOf(false)
        protected set

    open var isAdmin by mutableStateOf(false)
        protected set

    open fun getAccessToken(): String? {
        val s = getSession() ?: return null
        return text(s.access_token)
    }

    suspend fun getValidAccessToken(): String? {
        val session = getSession() ?: return null
        val accessToken = text(session.access_token) ?: return null
        val expiresAt = (session.expires_at as? Number)?.toDouble()
        val nowSeconds = (js("Date.now() / 1000") as Number).toDouble()

        if (expiresAt == null || expiresAt > nowSeconds + 60) {
            return accessToken
        }

        val refreshToken = text(session.refresh_token) ?: return accessToken
        val refreshed = refreshSession(refreshToken) ?: return null
        saveSession(refreshed)
        return text(refreshed.access_token)
    }

    init {
        scope.launch {
            runCatching { checkSession() }
                .onFailure { println("Admin auth check failed: ${it.message}") }
        }
    }

    private suspend fun checkSession() {
        val session = getSession()
        if (session != null) {
            val accessToken = text(session.access_token)
            if (accessToken != null) {
                val isAdminUser = checkAdminUser(accessToken)
                isLoggedIn = isAdminUser
                isAdmin = isAdminUser
            }
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val response = window.fetch(
            "${config.supabaseUrl}/auth/v1/token?grant_type=password",
            signInInit(email, password)
        ).await()

        if (!response.ok) {
            val errorBody = response.text().await()
            error("Login failed: ${response.status} $errorBody")
        }

        val session: dynamic = response.json().await()
        val accessToken = text(session.access_token) ?: error("Supabase no devolvió sesión válida")

        if (checkAdminUser(accessToken)) {
            saveSession(session)
            isLoggedIn = true
            isAdmin = true
        } else {
            error("No tienes permisos de administrador")
        }
    }

    private suspend fun checkAdminUser(accessToken: String): Boolean {
        val response = window.fetch(
            "${config.supabaseUrl}/rest/v1/admin_users?select=user_id&limit=1",
            adminInit(accessToken)
        ).await()

        if (!response.ok) return false

        val payload: dynamic = response.json().await()
        val length = (payload.length as? Number)?.toInt() ?: 0
        return length > 0
    }

    private fun getSession(): dynamic? {
        val raw = window.localStorage.getItem(sessionStorageKey) ?: return null
        return parseJson(raw)
    }

    private fun saveSession(session: dynamic) {
        window.localStorage.setItem(sessionStorageKey, stringifyJson(session))
    }

    private suspend fun refreshSession(refreshToken: String): dynamic? {
        val headers: dynamic = js("({})")
        headers["apikey"] = config.publishableKey
        headers["Content-Type"] = "application/json"

        val body: dynamic = js("({})")
        body.refresh_token = refreshToken

        val init: dynamic = js("({})")
        init.method = "POST"
        init.headers = headers
        init.body = stringifyJson(body)

        val response = window.fetch(
            "${config.supabaseUrl}/auth/v1/token?grant_type=refresh_token",
            init,
        ).await()

        if (!response.ok) {
            window.localStorage.removeItem(sessionStorageKey)
            isLoggedIn = false
            isAdmin = false
            return null
        }

        return response.json().await()
    }

    private fun parseJson(raw: String): dynamic = js("JSON.parse(raw)")

    private fun stringifyJson(value: dynamic): String = js("JSON.stringify(value)")

    private fun signInInit(email: String, password: String): dynamic {
        val headers: dynamic = js("({})")
        headers["apikey"] = config.publishableKey
        headers["Content-Type"] = "application/json"

        val body: dynamic = js("({})")
        body.email = email
        body.password = password

        val init: dynamic = js("({})")
        init.method = "POST"
        init.headers = headers
        init.body = stringifyJson(body)
        return init
    }

    private fun adminInit(accessToken: String): dynamic {
        val headers: dynamic = js("({})")
        headers["apikey"] = config.publishableKey
        headers["Authorization"] = "Bearer $accessToken"
        headers["Accept"] = "application/json"

        val init: dynamic = js("({})")
        init.method = "GET"
        init.headers = headers
        return init
    }
}

object MockAdminAuthService : AdminAuthService(SupabaseConfig("", "")) {
    override var isLoggedIn by mutableStateOf(false)
    override var isAdmin by mutableStateOf(false)
}

object AdminAuthFactory {
    fun create(config: SupabaseConfig?): AdminAuthService {
        if (config == null) return MockAdminAuthService
        return AdminAuthService(config)
    }
}

private fun text(value: dynamic): String? = value?.toString()
