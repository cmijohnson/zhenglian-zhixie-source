package com.eagleslab.yingge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eagleslab.yingge.model.Customer
import com.eagleslab.yingge.model.CustomerFilter
import com.eagleslab.yingge.model.CustomerPage
import com.eagleslab.yingge.model.CustomerTransferCandidate
import com.eagleslab.yingge.model.CustomerQuality
import com.eagleslab.yingge.ui.components.*
import kotlinx.coroutines.delay

@Composable
fun CustomersScreen(
    customers: List<Customer>,
    page: CustomerPage,
    ownerOptions: List<CustomerTransferCandidate>,
    canWrite: Boolean,
    onLoadOwners: () -> Unit,
    onFilter: (CustomerFilter) -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var quality by rememberSaveable { mutableStateOf<CustomerQuality?>(null) }
    var filter by remember { mutableStateOf(CustomerFilter()) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    val visible = customers.filter { quality == null || it.quality == quality }
    LaunchedEffect(query) { delay(300); val next = filter.copy(search = query, page = 0); filter = next; onFilter(next) }
    LaunchedEffect(filterOpen) { if (filterOpen && ownerOptions.isEmpty()) onLoadOwners() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageHeading("CUSTOMERS", "客户档案", "只显示当前账号有权访问的数据。") }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索姓名、院校或联系方式") }, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { AssistChip(onClick = { filterOpen = true }, leadingIcon = { Icon(Icons.Rounded.FilterList, null) }, label = { Text("筛选${if (filter.hasAdvancedFilters()) " · 已启用" else ""}") }) }
                    item { FilterChip(selected = quality == null, onClick = { quality = null }, label = { Text("全部等级") }, modifier = Modifier.heightIn(min = 44.dp)) }
                    items(CustomerQuality.entries) { item -> FilterChip(selected = quality == item, onClick = { quality = item }, label = { Text("${item.name} 类") }, modifier = Modifier.heightIn(min = 44.dp)) }
                }
            }
            item { Text("共 ${page.totalElements} 位客户 · 第 ${if (page.totalPages == 0) 0 else page.page + 1}/${page.totalPages} 页", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
            if (visible.isEmpty()) item { EmptyPane("没有匹配客户", "换一个关键词或清除筛选条件。") }
            items(visible, key = Customer::id) { customer -> CustomerRow(customer, onOpen) }
            if (page.totalPages > 1) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    OutlinedButton(enabled = page.page > 0, onClick = { val next = filter.copy(page = page.page - 1); filter = next; onFilter(next) }) { Text("上一页") }
                    Button(enabled = page.page + 1 < page.totalPages, onClick = { val next = filter.copy(page = page.page + 1); filter = next; onFilter(next) }) { Text("下一页") }
                }
            }
        }
        if (canWrite) FloatingActionButton(onClick = onCreate, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 88.dp), shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
            Icon(Icons.Rounded.Add, contentDescription = "新建客户")
        }
    }
    if (filterOpen) CustomerFilterSheet(filter, ownerOptions, onDismiss = { filterOpen = false }) { next ->
        val applied = next.copy(search = query, page = 0)
        filter = applied
        onFilter(applied)
        filterOpen = false
    }
}

private fun CustomerFilter.hasAdvancedFilters() = status.isNotBlank() || intention.isNotBlank() || grade.isNotBlank() || school.isNotBlank() || ownerMembershipId.isNotBlank() || includeArchived || sort != "updatedDesc"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerFilterSheet(initial: CustomerFilter, owners: List<CustomerTransferCandidate>, onDismiss: () -> Unit, onApply: (CustomerFilter) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("筛选客户", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { OutlinedTextField(value.school, { value = value.copy(school = it) }, label = { Text("院校") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value.grade, { value = value.copy(grade = it.filter(Char::isDigit).take(4)) }, label = { Text("年级，例如 2026") }, modifier = Modifier.fillMaxWidth()) }
            item {
                Text("客户状态", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("", "初次接触", "跟进中", "已预约", "已成单", "已流失")) { status -> FilterChip(value.status == status, { value = value.copy(status = status) }, { Text(status.ifBlank { "全部" }) }) } }
            }
            item {
                Text("意向", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("", "低", "中", "高").forEach { level -> FilterChip(value.intention == level, { value = value.copy(intention = level) }, { Text(level.ifBlank { "全部" }) }) } }
            }
            item {
                Text("负责人", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(value.ownerMembershipId.isBlank(), { value = value.copy(ownerMembershipId = "") }, { Text("全部") }) }
                    items(owners, key = { it.membershipId }) { owner -> FilterChip(value.ownerMembershipId == owner.membershipId, { value = value.copy(ownerMembershipId = owner.membershipId) }, { Text(owner.displayName) }) }
                }
            }
            item {
                Text("排序", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("updatedDesc" to "最近更新", "createdDesc" to "最近创建", "lastContactDesc" to "最近沟通", "nextFollowUpAsc" to "下次跟进")) { option -> FilterChip(value.sort == option.first, { value = value.copy(sort = option.first) }, { Text(option.second) }) } }
            }
            item { Row(Modifier.fillMaxWidth().clickable { value = value.copy(includeArchived = !value.includeArchived) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(value.includeArchived, { value = value.copy(includeArchived = it) }); Text("包含归档", modifier = Modifier.padding(start = 6.dp)) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { value = CustomerFilter(search = initial.search) }, modifier = Modifier.weight(1f)) { Text("清空") }
                    Button(onClick = { onApply(value) }, modifier = Modifier.weight(1f)) { Text("应用筛选") }
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(customer: Customer, onOpen: (String) -> Unit) {
    YinggeCard(Modifier.fillMaxWidth().clickable { onOpen(customer.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(customer.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(customer.name, style = MaterialTheme.typography.titleMedium); QualityBadge(customer.quality) }
                Text(listOf(customer.school, customer.major, customer.grade).filter(String::isNotBlank).joinToString(" · ").ifBlank { "画像待补充" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text("负责人 ${customer.ownerName.ifBlank { "未分配" }}${if (customer.collaboratorNames.isNotEmpty()) " · ${customer.collaboratorNames.size} 位协作者" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
