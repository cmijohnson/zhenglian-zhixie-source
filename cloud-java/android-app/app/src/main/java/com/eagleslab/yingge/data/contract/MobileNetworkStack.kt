package com.eagleslab.yingge.data.contract

import com.eagleslab.yingge.data.RepositoryException
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

internal data class MobileNetworkTimeouts(
    val connectMillis: Long = 15_000,
    val readMillis: Long = 120_000,
    val writeMillis: Long = 45_000,
)

/** The single HTTP policy used by both the mobile control-plane and employee APIs. */
internal object MobileHttpClientFactory {
    fun create(
        origin: ServerOrigin,
        deviceId: String,
        appVersion: String,
        versionCode: Long,
        accessToken: () -> String?,
        timeouts: MobileNetworkTimeouts = MobileNetworkTimeouts(),
    ): OkHttpClient {
        val metadata = Interceptor { chain ->
            val target = chain.request().url
            if (!OriginRequestPolicy.isExactOrigin(target, origin)) {
                throw CrossOriginRequestException()
            }
            val request = chain.request().newBuilder()
                .removeHeader("Authorization")
                .header("X-Yingge-App-Version", appVersion)
                .header("X-Yingge-Version-Code", versionCode.toString())
                .header("X-Yingge-Platform", "ANDROID")
                .header("X-Yingge-Device-Id", deviceId)
                .header("User-Agent", "Yingge-Android/$appVersion ($versionCode)")
                .apply {
                    if (OriginRequestPolicy.shouldAttachAuthorization(target.encodedPath)) {
                        accessToken()?.let { header("Authorization", "Bearer $it") }
                    }
                }
                .build()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(metadata)
            .followRedirects(false)
            .followSslRedirects(false)
            // Business writes use idempotency keys where supported. The transport itself
            // must never silently replay a POST/PATCH after a dropped connection.
            .retryOnConnectionFailure(false)
            .connectTimeout(timeouts.connectMillis, TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.readMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.writeMillis, TimeUnit.MILLISECONDS)
            .build()
    }
}

internal object MobileNetworkCall {
    suspend fun <T> execute(block: suspend () -> T): T = try {
        block()
    } catch (error: RepositoryException) {
        throw error
    } catch (error: HttpException) {
        val parsed = runCatching {
            Gson().fromJson(error.response()?.errorBody()?.string(), ApiErrorDto::class.java)
        }.getOrNull()
        throw RepositoryException(
            code = parsed?.code?.takeIf(String::isNotBlank) ?: "HTTP_${error.code()}",
            message = parsed?.message?.takeIf(String::isNotBlank) ?: "服务器请求失败",
            traceId = parsed?.traceId?.takeIf(String::isNotBlank),
            httpStatus = error.code(),
        )
    } catch (error: IOException) {
        throw when (error) {
            is CrossOriginRequestException -> RepositoryException("NETWORK_ORIGIN_BLOCKED", "已阻止跨服务器请求")
            is SocketTimeoutException -> RepositoryException("NETWORK_TIMEOUT", "服务器响应超时，请检查网络后重试")
            is UnknownHostException -> RepositoryException("NETWORK_DNS_FAILED", "无法解析服务器地址")
            is ConnectException, is NoRouteToHostException -> RepositoryException("NETWORK_CONNECT_FAILED", "无法连接服务器")
            is SSLException -> RepositoryException("NETWORK_TLS_FAILED", "服务器安全连接失败")
            else -> if (error.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                RepositoryException("NETWORK_CLEARTEXT_BLOCKED", "系统阻止了 HTTP 明文连接")
            } else {
                RepositoryException("NETWORK_IO_FAILED", "网络请求失败，请检查连接后重试")
            }
        }
    }
}

private class CrossOriginRequestException : IOException("Cross-origin mobile request blocked")
