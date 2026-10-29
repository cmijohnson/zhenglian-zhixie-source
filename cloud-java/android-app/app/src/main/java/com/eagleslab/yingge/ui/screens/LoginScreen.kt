package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.ui.components.BrandLogo
import com.eagleslab.yingge.BuildConfig

@Composable
fun LoginScreen(
    initialServerUrl: String,
    busy: Boolean,
    onLogin: (String, String, String, Boolean) -> Unit,
    onRegister: (String, Boolean) -> Unit,
) {
    var serverUrl by rememberSaveable(initialServerUrl) { mutableStateOf(initialServerUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showServer by rememberSaveable { mutableStateOf(initialServerUrl.isBlank()) }
    var insecureConfirmed by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val isHttp = serverUrl.trim().startsWith("http://", ignoreCase = true)
    val submit = { focus.clearFocus(); onLogin(serverUrl.trim(), username.trim(), password, insecureConfirmed) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BrandLogo(Modifier.fillMaxWidth().height(76.dp))
            Spacer(Modifier.height(30.dp))
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 7.dp) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("欢迎回来", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("登录后继续管理客户和每日执行。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                    }
                    OutlinedTextField(
                        value = username, onValueChange = { username = it }, label = { Text("账号") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("密码") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                    TextButton(onClick = { showServer = !showServer }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        Text(if (showServer) "收起服务器设置" else "服务器设置")
                    }
                    if (showServer) OutlinedTextField(
                        value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("服务器地址") },
                        supportingText = { Text("支持任意 HTTP(S) Origin；HTTP 会明文传输，确认后仅向该精确地址发送凭据。") },
                        leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    )
                    if (isHttp) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = insecureConfirmed, onCheckedChange = { insecureConfirmed = it })
                        Text("我确认该 HTTP 地址会明文传输，仅用于受控企业网络。", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = submit, enabled = !busy && username.isNotBlank() && password.isNotBlank() && serverUrl.isNotBlank() && (!isHttp || insecureConfirmed), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("安全登录")
                    }
                    TextButton(onClick = { onRegister(serverUrl.trim(), insecureConfirmed) }, enabled = !busy && serverUrl.isNotBlank() && (!isHttp || insecureConfirmed), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("使用邀请码注册") }
                    Text(if (BuildConfig.USE_MOCK_REPOSITORY) "当前为显式 Mock 构建，不会连接服务器。" else "当前为真实服务模式，操作会提交到所选服务器。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
