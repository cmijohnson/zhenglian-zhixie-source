package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.BusinessModule
import com.eagleslab.yingge.model.ModuleAvailability
import com.eagleslab.yingge.ui.components.EmptyPane
import com.eagleslab.yingge.ui.components.LoadingPane
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard

/** Native allow-listed surface for compact employee feeds and their named actions. */
@Composable
fun BusinessModuleScreen(module: BusinessModule?, busy: Boolean, onBack: () -> Unit, onRetry: () -> Unit, onAction: (String, String) -> Unit) {
    if (module == null) { LoadingPane(); return }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, "返回") } }
        item { PageHeading("MOBILE WORKSPACE", module.title, module.description) }
        item {
            val (label, icon, color) = when (module.availability) {
                ModuleAvailability.AVAILABLE -> Triple("服务端已开放", Icons.Rounded.Info, MaterialTheme.colorScheme.primary)
                ModuleAvailability.READ_ONLY -> Triple("当前只读", Icons.Rounded.Lock, MaterialTheme.colorScheme.tertiary)
                ModuleAvailability.OFFLINE -> Triple("当前离线", Icons.Rounded.CloudOff, MaterialTheme.colorScheme.error)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = .10f)) {
                Row(Modifier.fillMaxWidth().padding(14.dp)) { Icon(icon, null, tint = color); Text(label, color = color, modifier = Modifier.padding(start = 10.dp)) }
            }
        }
        if (module.items.isEmpty() && module.actionItems.isEmpty()) item { EmptyPane("暂无可展示内容", "服务端暂未返回该模块数据。") }
        else items(module.items) { line -> YinggeCard(Modifier.fillMaxWidth()) { Text(line, style = MaterialTheme.typography.titleMedium); Text("移动端不会在服务端能力缺失时猜测写入接口。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) } }
        items(module.actionItems, key = { it.id }) { action -> YinggeCard(Modifier.fillMaxWidth()) {
            Text(action.title, style = MaterialTheme.typography.titleMedium)
            Text(action.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Button(onClick = { onAction(module.key, action.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(action.actionLabel) }
        } }
        module.errorMessage?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (module.availability == ModuleAvailability.OFFLINE) {
            item {
                Button(
                    onClick = onRetry,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("重新加载") }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}
