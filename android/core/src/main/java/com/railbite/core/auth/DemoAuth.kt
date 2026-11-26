package com.railbite.core.auth

/**
 * Credentials for the on-device demo sign-in.
 *
 * The customer app's dummy phone+OTP login verifies the OTP locally. When the
 * server exposes the development login it still upgrades to a real session;
 * otherwise it saves a session with these markers so the UI can continue
 * without Firebase/SMS configuration. [AuthInterceptor] never clears a demo
 * session on a 401 (there is no refresh token to rotate).
 */
object DemoAuth {
    const val ACCESS_TOKEN = "demo-local-access"
    const val REFRESH_TOKEN = "demo-local-refresh"

    fun isDemoToken(token: String?): Boolean = token == ACCESS_TOKEN
}
