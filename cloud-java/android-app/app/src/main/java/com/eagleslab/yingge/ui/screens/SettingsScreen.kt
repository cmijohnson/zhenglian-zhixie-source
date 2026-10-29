package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard

@Composable
fun SettingsScreen(currentServer: String, onBack: () -> Unit, onSave: (String, Boolean, () -> Unit) -> Unit) {
    var server by rememberSaveable(currentServer) { mutableStateOf(currentServer) }
    var insecureConfirmed by rememberSaveable { mutableStateOf(false) }
    val isHttp = server.trim().startsWith("http://", ignoreCase = true)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回更多") } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading("DEVICE", "服务器与设备", "切换服务器会先尝试安全退出，再清除旧会话与离线草稿。")
            YinggeCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("服务器地址") }, supportingText = { Text("不得填写路径、账号、Token 或 organizationId。") }, singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(15.dp))
                if (isHttp) Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(insecureConfirmed, { insecureConfirmed = it })
                    Text("我确认 HTTP 会明文传输，仅用于受控企业网络。", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onSave(server, insecureConfirmed, onBack) }, enabled = server.isNotBlank() && (!isHttp || insecureConfirmed), modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Save, contentDescription = null); Text("保存并退出当前会话") }
            }
            YinggeCard(Modifier.fillMaxWidth()) {
                Text("设备安全", style = MaterialTheme.typography.titleMedium)
                Text("移动访问令牌由 Android Keystore 加密保存；退出或撤销设备后立即清除。客户端不会保存密码，也不会复用网页 Session。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
