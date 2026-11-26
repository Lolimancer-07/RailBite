package com.railbite.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private class FakeAuthStore(initial: AuthSession?) : AuthStore {
        private val _session = MutableStateFlow(initial)
        override val session: StateFlow<AuthSession?> = _session.asStateFlow()
        override fun accessToken(): String? = _session.value?.accessToken
        override fun refreshToken(): String? = _session.value?.refreshToken
        override fun save(session: AuthSession) {
            _session.value = session
        }

        override fun clear() {
            _session.value = null
        }
    }

    private var devLoginStatus = 200
    private var refreshStatus = 200
    private var devLoginCalls = 0
    private var refreshCalls = 0
    private var lastDevLoginBody = ""

    @Before
    fun setUp() {
        devLoginStatus = 200
        refreshStatus = 200
        devLoginCalls = 0
        refreshCalls = 0
        lastDevLoginBody = ""
    }

    private val fakeServer = Interceptor { chain ->
        val req = chain.request()
        val path = req.url.encodedPath
        val jsonMedia = "application/json".toMediaType()

        fun respond(code: Int, body: String): Response = Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody(jsonMedia))
            .build()

        when (path) {
            "/orders" -> when (req.header("Authorization")) {
                "Bearer real-jwt-1" -> respond(201, """{"id":"o1"}""")
                "Bearer refreshed-jwt" -> respond(200, """{"id":"o2"}""")
                else -> respond(401, """{"error":"Invalid or expired session"}""")
            }
            "/auth/dev/login" -> {
                devLoginCalls++
                lastDevLoginBody = Buffer().also { req.body?.writeTo(it) }.readUtf8()
                if (devLoginStatus == 200) {
                    respond(
                        200,
                        """{"ok":true,"accessToken":"real-jwt-1","refreshToken":"real-refresh-1","user":{"id":"u1","phone":"+919876543210","role":"customer"}}""",
                    )
                } else {
                    respond(devLoginStatus, """{"error":"Development login is disabled"}""")
                }
            }
            "/auth/refresh" -> {
                refreshCalls++
                if (refreshStatus == 200) {
                    respond(
                        200,
                        """{"ok":true,"accessToken":"refreshed-jwt","refreshToken":"new-refresh","user":{"id":"u1","phone":"+919876543210","role":"customer"}}""",
                    )
                } else {
                    respond(refreshStatus, """{"error":"Invalid refresh token"}""")
                }
            }
            else -> respond(404, """{"error":"Not found"}""")
        }
    }

    private fun testClient(store: AuthStore): OkHttpClient {
        val refreshClient = OkHttpClient.Builder().addInterceptor(fakeServer).build()
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store, refreshClient))
            .addInterceptor(fakeServer)
            .build()
    }

    @Test
    fun demo_token_upgrades_via_dev_login_on_401_and_retries() {
        val store = FakeAuthStore(
            AuthSession(
                accessToken = DemoAuth.ACCESS_TOKEN,
                refreshToken = DemoAuth.REFRESH_TOKEN,
                user = AuthUser(id = "demo-3210", name = "Demo Passenger", phone = "+919876543210", role = "customer"),
            ),
        )
        val req = Request.Builder()
            .url("https://example.com/orders")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        testClient(store).newCall(req).execute().use { res ->
            assertEquals(201, res.code)
        }
        assertEquals(1, devLoginCalls)
        assertEquals(0, refreshCalls)
        assertTrue(lastDevLoginBody.contains("+919876543210"))
        assertEquals("real-jwt-1", store.accessToken())
        assertEquals("real-refresh-1", store.refreshToken())
    }

    @Test
    fun demo_token_preserved_when_dev_login_disabled() {
        devLoginStatus = 403
        val store = FakeAuthStore(
            AuthSession(
                accessToken = DemoAuth.ACCESS_TOKEN,
                refreshToken = DemoAuth.REFRESH_TOKEN,
                user = AuthUser(id = "demo-3210", name = "Demo Passenger", phone = "+919876543210", role = "customer"),
            ),
        )
        val req = Request.Builder().url("https://example.com/orders").build()
        testClient(store).newCall(req).execute().use { res ->
            assertEquals(401, res.code)
        }
        assertEquals(1, devLoginCalls)
        assertEquals(DemoAuth.ACCESS_TOKEN, store.accessToken())
    }

    @Test
    fun expired_jwt_rotates_via_refresh_and_retries() {
        val store = FakeAuthStore(
            AuthSession(
                accessToken = "expired-jwt",
                refreshToken = "old-refresh",
                user = AuthUser(id = "u1", phone = "+919876543210", role = "customer"),
            ),
        )
        val req = Request.Builder().url("https://example.com/orders").build()
        testClient(store).newCall(req).execute().use { res ->
            assertEquals(200, res.code)
        }
        assertEquals(0, devLoginCalls)
        assertEquals(1, refreshCalls)
        assertEquals("refreshed-jwt", store.accessToken())
        assertEquals("new-refresh", store.refreshToken())
    }

    @Test
    fun rejected_refresh_clears_non_demo_session() {
        refreshStatus = 401
        val store = FakeAuthStore(
            AuthSession(
                accessToken = "expired-jwt",
                refreshToken = "revoked-refresh",
                user = AuthUser(id = "u1", phone = "+919876543210", role = "customer"),
            ),
        )
        val req = Request.Builder().url("https://example.com/orders").build()
        testClient(store).newCall(req).execute().use { res ->
            assertEquals(401, res.code)
        }
        assertEquals(1, refreshCalls)
        assertNull(store.session.value)
    }
}
