package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.RegistrationTeam
import com.eagleslab.yingge.ui.components.BrandLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    serverUrl: String,
    teams: List<RegistrationTeam>,
    busy: Boolean,
    onBack: () -> Unit,
    onLoadTeams: (String, String) -> Unit,
    onRegister: (String, String, String, String, String, String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var realName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var selectedTeamId by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, "返回登录") }
        BrandLogo(Modifier.fillMaxWidth().height(64.dp))
        Text("邀请码注册", style = MaterialTheme.typography.headlineMedium)
        Text("先核验邀请码并选择团队。注册成功后会通过移动端登录接口建立设备会话。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(code, { code = it }, label = { Text("邀请码") }, leadingIcon = { Icon(Icons.Rounded.Key, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        OutlinedButton(onClick = { onLoadTeams(serverUrl, code.trim()) }, enabled = !busy && serverUrl.isNotBlank() && code.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(if (teams.isEmpty()) "验证邀请码" else "重新验证邀请码") }
        if (teams.isNotEmpty()) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(value = teams.firstOrNull { it.id == selectedTeamId }?.name.orEmpty(), onValueChange = {}, readOnly = true, label = { Text("团队") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { teams.forEach { team -> DropdownMenuItem(text = { Text(team.name) }, onClick = { selectedTeamId = team.id; expanded = false }) } }
            }
            OutlinedTextField(username, { username = it }, label = { Text("登录账号") }, leadingIcon = { Icon(Icons.Rounded.Person, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(realName, { realName = it }, label = { Text("真实姓名") }, leadingIcon = { Icon(Icons.Rounded.Badge, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onRegister(serverUrl, username.trim(), realName.trim(), password, code.trim(), selectedTeamId) }, enabled = !busy && username.isNotBlank() && realName.isNotBlank() && password.isNotBlank() && selectedTeamId.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("注册并登录") }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
