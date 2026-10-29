package com.eagleslab.yingge.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.CustomerImportState
import com.eagleslab.yingge.model.DownloadArtifact
import com.eagleslab.yingge.model.EvidenceUpload
import com.eagleslab.yingge.ui.components.ExpandableText
import com.eagleslab.yingge.ui.components.PageHeading
import com.eagleslab.yingge.ui.components.YinggeCard

@Composable
fun DataTransferScreen(
    state: CustomerImportState?,
    artifact: DownloadArtifact?,
    busy: Boolean,
    onBack: () -> Unit,
    onPreview: (EvidenceUpload) -> Unit,
    onCommit: () -> Unit,
    onExport: (String) -> Unit,
    onDownloadHandled: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            } ?: "customers.xlsx"
            val type = context.contentResolver.getType(it) ?: if (name.endsWith(".csv", true)) "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } ?: byteArrayOf()
            onPreview(EvidenceUpload(name, type, bytes))
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(artifact?.contentType ?: "application/octet-stream")) { uri ->
        val saved = uri != null && artifact != null && runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(artifact.bytes) } ?: error("无法写入文件") }.isSuccess
        onDownloadHandled(saved)
    }
    LaunchedEffect(artifact) { artifact?.let { save.launch(it.fileName) } }
    LazyColumn(contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, "返回") }; PageHeading("DATA TRANSFER", "客户导入导出", "文件由服务端预览、权限裁剪并记录审计；提交前不会写入客户数据。") }
        item { YinggeCard(Modifier.fillMaxWidth()) {
            Text("批量导入", style = MaterialTheme.typography.titleMedium)
            Button({ picker.launch(arrayOf("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) { Icon(Icons.Rounded.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("选择 CSV / XLSX 并预览") }
            state?.let { row ->
                Text("${row.fileName} · ${row.status}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
                Text("总计 ${row.totalRows} · 可导入 ${row.readyRows} · 错误 ${row.failedRows} · 已创建 ${row.createdRows} · 跳过 ${row.skippedRows}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (row.messages.isNotEmpty()) ExpandableText(row.messages.joinToString("\n"), collapsedLines = 5, modifier = Modifier.padding(top = 8.dp))
                if (row.status == "PREVIEWED") Button(onCommit, enabled = row.readyRows > 0 && !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("确认提交导入") }
            }
        } }
        item { YinggeCard(Modifier.fillMaxWidth()) {
            Text("权限裁剪导出", style = MaterialTheme.typography.titleMedium)
            Text("只导出当前账号有权访问的客户；导出文件不在 App 内长期缓存。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onExport("xlsx") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Download, null); Text("XLSX") }
                OutlinedButton({ onExport("csv") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Download, null); Text("CSV") }
            }
        } }
    }
}
