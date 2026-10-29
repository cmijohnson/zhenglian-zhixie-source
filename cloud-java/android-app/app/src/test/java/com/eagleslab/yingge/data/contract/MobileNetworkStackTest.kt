package com.eagleslab.yingge.data.contract

import com.eagleslab.yingge.data.RepositoryException
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class MobileNetworkStackTest {
    @Test
    fun `login emits exact release json and required device metadata without bearer`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody(tokenResponse()))
            val (api, _) = api(server, accessToken = "stale-access")

            api.login(MobileLoginRequest("employee", "secret", "Android 客户端"))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/mobile/auth/login", request.path)
            assertEquals("0.1.3", request.getHeader("X-Yingge-App-Version"))
            assertEquals("4", request.getHeader("X-Yingge-Version-Code"))
            assertEquals("ANDROID", request.getHeader("X-Yingge-Platform"))
            assertEquals(DEVICE_ID, request.getHeader("X-Yingge-Device-Id"))
            assertEquals("Yingge-Android/0.1.3 (4)", request.getHeader("User-Agent"))
            assertNull(request.getHeader("Authorization"))
            val body = Gson().fromJson(request.body.readUtf8(), JsonObject::class.java)
            assertEquals(setOf("username", "password", "deviceName"), body.keySet())
            assertEquals("employee", body["username"].asString)
            assertEquals("secret", body["password"].asString)
            assertEquals("Android 客户端", body["deviceName"].asString)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `protected calls attach current bearer and bootstrap is parsed`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody(bootstrapResponse()))
            val (api, _) = api(server, accessToken = "current-access")

            val bootstrap = api.bootstrap()

            assertEquals("employee", bootstrap.actor.username)
            assertEquals("Bearer current-access", server.takeRequest().getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `current release accepts no content when installed build is current`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val (api, _) = api(server, accessToken = "current-access")

            val response = api.currentRelease()

            assertEquals(204, response.code())
            assertTrue(response.isSuccessful)
            assertNull(response.body())
            assertEquals("Bearer current-access", server.takeRequest().getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `apk download streams the complete body with the current bearer`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            val apkBytes = ByteArray(128 * 1024) { index -> (index % 251).toByte() }
            server.enqueue(MockResponse().setHeader("Content-Type", "application/vnd.android.package-archive").setBody(okio.Buffer().write(apkBytes)))
            val (api, _) = api(server, accessToken = "current-access")

            val received = api.releaseApk("00000000-0000-4000-8000-000000000777").use { it.bytes() }

            assertTrue(apkBytes.contentEquals(received))
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("Bearer current-access", request.getHeader("Authorization"))
            assertEquals("/api/v1/mobile/app-releases/00000000-0000-4000-8000-000000000777/apk", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `caller supplied bearer is removed from public auth calls`() {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val (_, client) = api(server, accessToken = "vault-access")
            val request = Request.Builder()
                .url(server.url("/api/v1/mobile/auth/login"))
                .header("Authorization", "Bearer caller-injected")
                .post("{}".toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { assertEquals(204, it.code) }
            assertNull(server.takeRequest().getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `redirects and implicit connection retries are disabled`() {
        val source = MockWebServer().apply { start() }
        val target = MockWebServer().apply { start() }
        try {
            source.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target.url("/stolen")))
            val (_, client) = api(source, accessToken = "vault-access")
            assertFalse(client.followRedirects)
            assertFalse(client.followSslRedirects)
            assertFalse(client.retryOnConnectionFailure)

            client.newCall(Request.Builder().url(source.url("/api/v1/mobile/bootstrap")).build()).execute().use {
                assertEquals(302, it.code)
            }
            assertEquals(1, source.requestCount)
            assertEquals(0, target.requestCount)
        } finally {
            source.shutdown()
            target.shutdown()
        }
    }

    @Test
    fun `cross origin request is blocked before a packet reaches the other host`() {
        val source = MockWebServer().apply { start() }
        val target = MockWebServer().apply { start() }
        try {
            val (_, client) = api(source, accessToken = "vault-access")
            val failure = runCatching {
                client.newCall(Request.Builder().url(target.url("/api/v1/mobile/bootstrap")).build()).execute().close()
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertEquals(0, target.requestCount)
        } finally {
            source.shutdown()
            target.shutdown()
        }
    }

    @Test
    fun `structured http error preserves business code trace and status for refresh logic`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("""
                {"code":"MOBILE_ACCESS_TOKEN_EXPIRED","message":"登录状态已过期","fieldErrors":[],"traceId":"trace-401"}
            """.trimIndent()))
            val (api, _) = api(server, accessToken = "expired-access")

            val failure = expectRepositoryFailure { MobileNetworkCall.execute { api.bootstrap() } }

            assertEquals("MOBILE_ACCESS_TOKEN_EXPIRED", failure.code)
            assertEquals("登录状态已过期", failure.message)
            assertEquals("trace-401", failure.traceId)
            assertEquals(401, failure.httpStatus)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `invalid error payload becomes stable generic http error`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(500).setBody("not-json"))
            val (api, _) = api(server, accessToken = "access")

            val failure = expectRepositoryFailure { MobileNetworkCall.execute { api.bootstrap() } }

            assertEquals("HTTP_500", failure.code)
            assertEquals("服务器请求失败", failure.message)
            assertEquals(500, failure.httpStatus)
            assertNull(failure.traceId)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `timeout dns tls and generic io failures have stable user facing codes`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val origin = origin(server)
            val client = MobileHttpClientFactory.create(
                origin,
                DEVICE_ID,
                "0.1.3",
                4,
                { null },
                MobileNetworkTimeouts(connectMillis = 150, readMillis = 150, writeMillis = 150),
            )
            val timeout = expectRepositoryFailure {
                MobileNetworkCall.execute {
                    client.newCall(Request.Builder().url(server.url("/timeout")).build()).execute().use { it.body?.string() }
                }
            }
            assertEquals("NETWORK_TIMEOUT", timeout.code)

            assertEquals("NETWORK_DNS_FAILED", mapped(UnknownHostException()).code)
            assertEquals("NETWORK_TLS_FAILED", mapped(SSLException("bad certificate")).code)
            assertEquals("NETWORK_IO_FAILED", mapped(IOException("socket reset")).code)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `dropped write is sent at most once`() {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY).setBody("partial"))
            val (_, client) = api(server, accessToken = "access")
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(server.url("/api/v1/customers"))
                        .post("{\"fullName\":\"test\"}".toRequestBody(JSON))
                        .build(),
                ).execute().use { it.body?.string() }
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun api(server: MockWebServer, accessToken: String?): Pair<MobileContractApi, okhttp3.OkHttpClient> {
        val origin = origin(server)
        val client = MobileHttpClientFactory.create(origin, DEVICE_ID, "0.1.3", 4, { accessToken })
        val api = Retrofit.Builder()
            .baseUrl(origin.canonical + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MobileContractApi::class.java)
        return api to client
    }

    private fun origin(server: MockWebServer): ServerOrigin =
        ServerOriginValidator.requireValid(server.url("/").toString(), allowInsecureHttp = true)

    private suspend fun mapped(error: IOException): RepositoryException =
        expectRepositoryFailure { MobileNetworkCall.execute<Unit> { throw error } }

    private suspend fun expectRepositoryFailure(block: suspend () -> Unit): RepositoryException {
        return try {
            block()
            throw AssertionError("Expected RepositoryException")
        } catch (error: RepositoryException) {
            error
        }
    }

    private fun tokenResponse() = """
        {
          "accessToken":"access","accessExpiresAt":"2026-08-23T11:00:00Z",
          "refreshToken":"refresh","refreshExpiresAt":"2026-09-22T11:00:00Z",
          "actor":{"userId":"00000000-0000-4000-8000-000000000001","membershipId":"00000000-0000-4000-8000-000000000002","organizationId":"00000000-0000-4000-8000-000000000003","username":"employee","displayName":"员工","roles":["MEMBER"],"passwordChangeRequired":false}
        }
    """.trimIndent()

    private fun bootstrapResponse() = """
        {
          "actor":{"userId":"00000000-0000-4000-8000-000000000001","membershipId":"00000000-0000-4000-8000-000000000002","organizationId":"00000000-0000-4000-8000-000000000003","username":"employee","displayName":"员工","roles":["MEMBER"],"passwordChangeRequired":false},
          "serverTime":"2026-08-23T11:00:00Z","organizationTimeZone":"Asia/Shanghai",
          "capabilities":["CUSTOMER_READ"],"unread":{"urgentReminders":0,"notifications":0}
        }
    """.trimIndent()

    private companion object {
        const val DEVICE_ID = "00000000-0000-4000-8000-000000000123"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
