package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.ui.components.BrandLogo

@Composable
fun PasswordChangeScreen(busy: Boolean, onChange: (String, String) -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val valid = current.isNotBlank() && next.length >= 8 && next == confirm
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BrandLogo(Modifier.fillMaxWidth().height(66.dp))
        Icon(Icons.Rounded.LockReset, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 28.dp).size(50.dp))
        Text("首次登录请修改密码", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp))
        Text("修改成功后所有 Web 与移动端会话都会失效，需要使用新密码重新登录。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        OutlinedTextField(current, { current = it }, label = { Text("当前密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(next, { next = it }, label = { Text("新密码（至少 8 位）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        OutlinedTextField(confirm, { confirm = it }, label = { Text("确认新密码") }, isError = confirm.isNotEmpty() && next != confirm, supportingText = { if (confirm.isNotEmpty() && next != confirm) Text("两次密码不一致") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Button(onClick = { onChange(current, next) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("修改并退出全部会话") }
    }
}
