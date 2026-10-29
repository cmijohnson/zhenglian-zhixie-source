package com.eagleslab.yingge.data.contract

import com.eagleslab.yingge.data.RepositoryException
import java.net.URI

data class ServerOrigin(val scheme: String, val host: String, val port: Int) {
    val canonical: String get() = "$scheme://$host${if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) "" else ":$port"}"
}

object ServerOriginValidator {
    fun requireValid(raw: String, allowInsecureHttp: Boolean): ServerOrigin {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: invalid()
        val scheme = uri.scheme?.lowercase() ?: invalid()
        if (scheme != "https" && scheme != "http") invalid()
        if (scheme == "http" && !allowInsecureHttp) throw RepositoryException("INSECURE_ORIGIN_CONFIRMATION_REQUIRED", "HTTP 为明文传输，请明确确认风险")
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) invalid()
        if (!uri.path.isNullOrBlank() && uri.path != "/") invalid()
        val port = if (uri.port == -1) if (scheme == "https") 443 else 80 else uri.port
        if (port !in 1..65535) invalid()
        return ServerOrigin(scheme, uri.host.lowercase(), port)
    }

    private fun invalid(): Nothing = throw RepositoryException("SERVER_ORIGIN_INVALID", "服务器地址必须是无路径、参数和账号信息的完整 HTTP(S) Origin")
}
