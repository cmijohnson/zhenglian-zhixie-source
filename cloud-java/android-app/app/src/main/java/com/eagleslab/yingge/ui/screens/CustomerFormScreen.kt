package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.CustomerDraft
import com.eagleslab.yingge.ui.components.PageHeading

@Composable
fun CustomerFormScreen(
    initial: CustomerDraft,
    editing: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onDraftChanged: (CustomerDraft) -> Unit,
    onSave: (CustomerDraft) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    fun update(value: CustomerDraft) { draft = value; if (!editing) onDraftChanged(value) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            PageHeading(if (editing) "EDIT CUSTOMER" else "NEW CUSTOMER", if (editing) "编辑客户" else "新建客户", "姓名必填，且手机号、微信号、QQ 号至少填写一项；提交前会由服务端执行查重和权限校验。")
            SectionTitle("01", "基础信息")
            AppField("姓名 *", draft.name, { update(draft.copy(name = it)) }, ImeAction.Next)
            AppField("昵称", draft.nickname, { update(draft.copy(nickname = it)) }, ImeAction.Next)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(onClick = { update(draft.copy(school = "院校甲")) }, label = { Text("院校甲") })
                SuggestionChip(onClick = { update(draft.copy(school = "院校乙")) }, label = { Text("院校乙") })
            }
            AppField("学校", draft.school, { update(draft.copy(school = it)) }, ImeAction.Next)
            AppField("专业", draft.major, { update(draft.copy(major = it)) }, ImeAction.Next)
            AppField("年级", draft.grade, { update(draft.copy(grade = it)) }, ImeAction.Next, "例如 2026级")
            SectionTitle("02", "联系与来源")
            AppField("手机号", draft.phone, { update(draft.copy(phone = it)) }, ImeAction.Next)
            AppField("微信", draft.wechat, { update(draft.copy(wechat = it)) }, ImeAction.Next)
            AppField("QQ", draft.qq, { update(draft.copy(qq = it)) }, ImeAction.Next)
            AppField("来源", draft.source, { update(draft.copy(source = it)) }, ImeAction.Next, "小红书、抖音、新生群、运营商等")
            OutlinedTextField(value = draft.demandSummary, onValueChange = { update(draft.copy(demandSummary = it)) }, label = { Text("需求摘要") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp))
            Text(if (editing) "修改会保留版本与审计历史。" else "未提交内容已保存在当前设备草稿中。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(100.dp))
        }
        Surface(shadowElevation = 8.dp) {
            Button(onClick = { onSave(draft) }, enabled = !busy && draft.canSubmit, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp).heightIn(min = 52.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Check, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (editing) "保存修改" else "创建客户") }
        }
    }
}

@Composable
private fun SectionTitle(number: String, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { AssistChip(onClick = {}, enabled = false, label = { Text(number) }); Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
}

@Composable
private fun AppField(label: String, value: String, onChange: (String) -> Unit, ime: ImeAction, hint: String? = null) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, supportingText = hint?.let { { Text(it) } }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ime), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(15.dp))
}
