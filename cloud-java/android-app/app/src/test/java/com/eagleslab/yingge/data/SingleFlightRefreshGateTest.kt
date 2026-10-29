package com.eagleslab.yingge.data

import com.eagleslab.yingge.data.contract.SingleFlightRefreshGate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SingleFlightRefreshGateTest {
    @Test fun `concurrent unauthorized requests rotate refresh only once`() = runTest {
        val refreshCount = AtomicInteger()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when {
                request.path == "/refresh" -> { refreshCount.incrementAndGet(); MockResponse().setBody("new-access") }
                request.getHeader("Authorization") == "Bearer new-access" -> MockResponse().setBody("ok")
                else -> MockResponse().setResponseCode(401)
            }
        }
        server.start()
        try {
            val client = OkHttpClient()
            val gate = SingleFlightRefreshGate()
            val access = AtomicReference("old-access")
            suspend fun callProtected(): String {
                fun execute(): okhttp3.Response = client.newCall(Request.Builder().url(server.url("/protected")).header("Authorization", "Bearer ${access.get()}").build()).execute()
                val used = access.get()
                execute().use { if (it.code == 200) return it.body!!.string() }
                gate.refreshIfUnchanged(used, access::get) {
                    client.newCall(Request.Builder().url(server.url("/refresh")).build()).execute().use { access.set(it.body!!.string()) }
                }
                return execute().use { it.body!!.string() }
            }
            val results = (1..8).map { async { callProtected() } }.awaitAll()
            assertEquals(List(8) { "ok" }, results)
            assertEquals(1, refreshCount.get())
        } finally { server.shutdown() }
    }
}
