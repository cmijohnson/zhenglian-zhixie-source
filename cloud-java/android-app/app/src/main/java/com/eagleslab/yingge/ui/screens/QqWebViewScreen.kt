package com.eagleslab.yingge.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QqWebViewScreen(baseUrl: String, oneTimeTicket: String?, onBack: () -> Unit) {
    val origin = remember(baseUrl) { runCatching { Uri.parse(baseUrl.trim().trimEnd('/')) }.getOrNull() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(false) }
    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回更多") }
            Column { Text("QQ 对话工作台", style = MaterialTheme.typography.titleMedium); Text("一次性网页会话 · 不暴露长期令牌", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.Lock, contentDescription = "受控安全会话", tint = MaterialTheme.colorScheme.primary)
        }
        if (oneTimeTicket == null || origin?.host.isNullOrBlank()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Text("等待安全会话票据", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                    Text("正式连接后，客户端会通过 /api/v1/mobile/web-sessions 获取 60 秒一次性票据，再由 WebView 交换为路径受限的 HttpOnly Session。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url
                            return target.scheme !in setOf("https", "http") ||
                                target.scheme != origin?.scheme ||
                                target.host != origin?.host ||
                                target.port != origin?.port
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { loading = true }
                        override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                    }
                    val exchange = baseUrl.trim().trimEnd('/') + "/api/v1/mobile/web-sessions/exchange"
                    val body = "ticket=" + URLEncoder.encode(oneTimeTicket, Charsets.UTF_8.name())
                    postUrl(exchange, body.toByteArray(Charsets.UTF_8))
                }
            },
            update = { webView = it },
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    DisposableEffect(Unit) { onDispose { webView?.apply { stopLoading(); loadUrl("about:blank"); clearHistory(); removeAllViews(); destroy() }; webView = null } }
}
