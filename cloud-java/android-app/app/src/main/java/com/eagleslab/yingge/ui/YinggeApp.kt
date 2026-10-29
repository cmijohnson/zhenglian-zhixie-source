package com.eagleslab.yingge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eagleslab.yingge.model.CustomerDraft
import com.eagleslab.yingge.model.AppMode
import com.eagleslab.yingge.navigation.Routes
import com.eagleslab.yingge.navigation.adminBottomDestinations
import com.eagleslab.yingge.navigation.bottomDestinations
import com.eagleslab.yingge.navigation.isAdminRootDestination
import com.eagleslab.yingge.navigation.isRootDestination
import com.eagleslab.yingge.ui.screens.*

@Composable
fun YinggeApp(viewModel: AppViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val customerPage by viewModel.customerPage.collectAsStateWithLifecycle()
    val customerOwnerOptions by viewModel.customerOwnerOptions.collectAsStateWithLifecycle()
    val selected by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val transferCandidates by viewModel.transferCandidates.collectAsStateWithLifecycle()
    val customerCollaboration by viewModel.customerCollaboration.collectAsStateWithLifecycle()
    val customerClassification by viewModel.customerClassification.collectAsStateWithLifecycle()
    val customerDownload by viewModel.customerDownload.collectAsStateWithLifecycle()
    val customerImport by viewModel.customerImport.collectAsStateWithLifecycle()
    val customerInsight by viewModel.customerInsight.collectAsStateWithLifecycle()
    val interactions by viewModel.interactions.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val daily by viewModel.daily.collectAsStateWithLifecycle()
    val dailyDraft by viewModel.dailyDraft.collectAsStateWithLifecycle()
    val customerDraft by viewModel.customerDraft.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val businessModule by viewModel.businessModule.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val performance by viewModel.performance.collectAsStateWithLifecycle()
    val consultations by viewModel.consultations.collectAsStateWithLifecycle()
    val consultationOptions by viewModel.consultationOptions.collectAsStateWithLifecycle()
    val contentTasks by viewModel.contentTasks.collectAsStateWithLifecycle()
    val circlePosts by viewModel.circlePosts.collectAsStateWithLifecycle()
    val circleFeed by viewModel.circleFeed.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val assistantWorkspace by viewModel.assistantWorkspace.collectAsStateWithLifecycle()
    val agentAuthorizations by viewModel.agentAuthorizations.collectAsStateWithLifecycle()
    val mobileDevices by viewModel.mobileDevices.collectAsStateWithLifecycle()
    val qqTicket by viewModel.qqTicket.collectAsStateWithLifecycle()
    val adminOverview by viewModel.adminOverview.collectAsStateWithLifecycle()
    val adminMembers by viewModel.adminMembers.collectAsStateWithLifecycle()
    val adminTeams by viewModel.adminTeams.collectAsStateWithLifecycle()
    val adminRegistrationCodes by viewModel.adminRegistrationCodes.collectAsStateWithLifecycle()
    val adminDealRequests by viewModel.adminDealRequests.collectAsStateWithLifecycle()
    val adminNativeModule by viewModel.adminNativeModule.collectAsStateWithLifecycle()
    val adminDownload by viewModel.adminDownload.collectAsStateWithLifecycle()
    val leadership by viewModel.leadershipDashboard.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message, ui.error) {
        (ui.error ?: ui.message)?.let { snackbar.showSnackbar(it); viewModel.clearNotice() }
    }

    var registrationOpen by rememberSaveable { mutableStateOf(false) }
    if (ui.user == null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.padding(padding)) {
                if (registrationOpen) RegisterScreen(
                    serverUrl = ui.serverUrl,
                    teams = ui.registrationTeams,
                    busy = ui.busy,
                    onBack = { registrationOpen = false },
                    onLoadTeams = viewModel::loadRegistrationOptions,
                    onRegister = viewModel::registerAndLogin,
                ) else LoginScreen(ui.serverUrl, ui.busy, viewModel::login, onRegister = { server, confirmed -> viewModel.prepareRegistration(server, confirmed) { registrationOpen = true } })
            }
        }
        return
    }
    val user = ui.user!!
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, user.userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppForegrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(user.userId) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (user.passwordChangeRequired) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.padding(padding)) { PasswordChangeScreen(ui.busy, viewModel::changePassword) }
        }
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route.orEmpty()
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val roots = remember(user.roles, ui.appMode) {
        if (ui.appMode == AppMode.ADMIN) adminBottomDestinations else bottomDestinations(user)
    }
    LaunchedEffect(ui.appMode) {
        val target = if (ui.appMode == AppMode.ADMIN) Routes.ADMIN_OVERVIEW else Routes.DASHBOARD
        nav.navigate(target) { popUpTo(nav.graph.findStartDestination().id) { inclusive = true }; launchSingleTop = true }
    }

    val promptedUpdate = ui.availableUpdate
    if (ui.updatePromptVisible && promptedUpdate != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdatePrompt,
            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
            title = { Text("发现新版本 ${promptedUpdate.title}") },
            text = { Text(promptedUpdate.details) },
            confirmButton = { Button(onClick = viewModel::installAvailableUpdate, enabled = !ui.busy) { Text("立即更新") } },
            dismissButton = { TextButton(onClick = viewModel::dismissUpdatePrompt) { Text("稍后") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val showRoot = if (ui.appMode == AppMode.ADMIN) currentRoute.isAdminRootDestination() else currentRoute.isRootDestination(user)
            if (!keyboardVisible && showRoot) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.navigationBarsPadding()) {
                roots.forEach { item -> NavigationBarItem(
                    selected = currentRoute == item.route,
                    onClick = { nav.navigate(item.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    alwaysShowLabel = true,
                ) }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(padding)) {
            composable(Routes.DASHBOARD) { DashboardScreen(dashboard, user.displayName, ui.greeting, ui.availableUpdate, viewModel::installAvailableUpdate, onCustomer = { id -> viewModel.openCustomer(id); nav.navigate(Routes.detail(id)) }, onRefresh = viewModel::refreshAll) }
            composable(Routes.CUSTOMERS) {
                CustomersScreen(
                    customers = customers,
                    page = customerPage,
                    ownerOptions = customerOwnerOptions,
                    canWrite = user.canWrite,
                    onLoadOwners = viewModel::loadCustomerOwnerOptions,
                    onFilter = viewModel::loadCustomerPage,
                    onOpen = { id -> viewModel.openCustomer(id); nav.navigate(Routes.detail(id)) },
                    onCreate = { nav.navigate(Routes.CUSTOMER_NEW) },
                )
            }
            composable(Routes.REMINDERS) { RemindersScreen(reminders, viewModel::completeReminder, onCustomer = { id -> viewModel.openCustomer(id); nav.navigate(Routes.detail(id)) }) }
            composable(Routes.DAILY) { DailySummaryScreen(daily, dailyDraft, ui.busy, viewModel::saveDailyDraft) { draft, done -> viewModel.saveDailySummary(draft, done) } }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(notifications, viewModel::markNotificationRead, viewModel::markAllNotificationsRead) }
            composable(Routes.MORE) {
                MoreScreen(
                    user = user,
                    theme = ui.theme,
                    capabilities = ui.capabilities,
                    adminModeAvailable = AppMode.ADMIN in ui.availableModes,
                    onTheme = viewModel::setTheme,
                    onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    onLeadership = { nav.navigate(Routes.LEADERSHIP) },
                    onAdminMode = { viewModel.switchAppMode(AppMode.ADMIN) },
                    onQq = { viewModel.openQqWorkbench { nav.navigate(Routes.QQ) } },
                    onModule = { key -> nav.navigate(Routes.module(key)) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onLogout = viewModel::logout,
                )
            }
            composable(Routes.CUSTOMER_NEW) { CustomerFormScreen(customerDraft, false, ui.busy, { nav.popBackStack() }, viewModel::saveCustomerDraft) { draft -> viewModel.saveCustomer(draft) { saved -> nav.navigate(Routes.detail(saved.id)) { popUpTo(Routes.CUSTOMERS) } } } }
            composable(Routes.CUSTOMER_DETAIL, arguments = listOf(navArgument("customerId") {})) { backStack ->
                val id = backStack.arguments?.getString("customerId").orEmpty()
                LaunchedEffect(id) { viewModel.openCustomer(id) }
                CustomerDetailScreen(
                    selected?.takeIf { it.id == id }, interactions, transferCandidates, customerCollaboration, customerClassification,
                    customerDownload, customerInsight?.takeIf { it.customerId == id }, user.canWrite, ui.busy,
                    { nav.popBackStack() }, { nav.navigate(Routes.edit(id)) },
                    { nav.navigate(Routes.timeline(id) + "?compose=true") }, { nav.navigate(Routes.timeline(id)) },
                    { viewModel.requestDeal(id) }, { viewModel.loadTransferCandidates(id) },
                    { target, reason, done -> viewModel.transferCustomer(id, target, reason, done) },
                    { members, done -> viewModel.replaceCustomerCollaborators(id, members, done) },
                    { done -> viewModel.archiveCustomer(id, done) },
                    { draft, done -> viewModel.saveCustomerClassification(id, draft, done) },
                    { viewModel.downloadCustomerTimeline(id) }, viewModel::clearCustomerDownload,
                    { viewModel.loadCustomerInsight(id) },
                )
            }
            composable(Routes.CUSTOMER_EDIT, arguments = listOf(navArgument("customerId") {})) { backStack ->
                val id = backStack.arguments?.getString("customerId").orEmpty()
                LaunchedEffect(id) { viewModel.openCustomer(id) }
                val draft = selected?.takeIf { it.id == id }?.let { CustomerDraft(it.id, it.name, it.nickname, it.school, it.major, it.grade, it.phone, it.wechat, it.qq, it.source, it.demandSummary) }
                if (draft == null) com.eagleslab.yingge.ui.components.LoadingPane() else CustomerFormScreen(draft, true, ui.busy, { nav.popBackStack() }, {}) { value -> viewModel.saveCustomer(value) { nav.popBackStack() } }
            }
            composable(Routes.TIMELINE + "?compose={compose}", arguments = listOf(navArgument("customerId") {}, navArgument("compose") { defaultValue = "false" })) { backStack ->
                val id = backStack.arguments?.getString("customerId").orEmpty()
                LaunchedEffect(id) { viewModel.openCustomer(id) }
                TimelineScreen(selected?.takeIf { it.id == id }?.name ?: "客户", id, interactions, user.canWrite, ui.busy, serverNow = viewModel.serverNow(), initialEditorOpen = backStack.arguments?.getString("compose") == "true", onBack = { nav.popBackStack() }) { draft -> viewModel.addInteraction(id, draft) {} }
            }
            composable(Routes.SETTINGS) { SettingsScreen(ui.serverUrl, { nav.popBackStack() }, viewModel::setServerUrl) }
            composable(Routes.QQ) { QqWebViewScreen(ui.serverUrl, oneTimeTicket = qqTicket?.ticket, onBack = { viewModel.clearQqTicket(); nav.popBackStack() }) }
            composable(Routes.LEADERSHIP) { LeadershipDashboardScreen(leadership, ui.busy, viewModel::loadLeadership) }
            composable(Routes.ADMIN_OVERVIEW) {
                AdminOverviewScreen(
                    adminOverview, ui.busy, viewModel::refreshAdmin,
                    onOpenGovernance = { nav.navigate(Routes.ADMIN_GOVERNANCE) },
                    onOpenPeople = { nav.navigate(Routes.ADMIN_PEOPLE) },
                    onEmployeeMode = { viewModel.switchAppMode(AppMode.EMPLOYEE) },
                )
            }
            composable(Routes.ADMIN_PEOPLE) {
                AdminPeopleScreen(
                    adminMembers, adminTeams, adminRegistrationCodes, ui.busy, viewModel::loadAdminMembersTeams,
                    viewModel::createAdminMember, viewModel::deleteAdminMember,
                    viewModel::createAdminTeam, viewModel::assignTeamLeader, viewModel::deleteAdminTeam,
                    viewModel::updateAdminMember, viewModel::createAdminRegistrationCode,
                    viewModel::disableAdminRegistrationCode,
                )
            }
            composable(Routes.ADMIN_GOVERNANCE) {
                AdminGovernanceScreen(
                    adminOverview, adminDealRequests, ui.busy, viewModel::refreshAdmin,
                    viewModel::reviewDealRequest,
                    onModule = { nav.navigate(Routes.adminModule(it)) },
                )
            }
            composable(Routes.ADMIN_LEADERSHIP) { LeadershipDashboardScreen(leadership, ui.busy, viewModel::loadLeadership) }
            composable(Routes.ADMIN_MORE) {
                AdminMoreScreen(
                    ui.capabilities,
                    onModule = { nav.navigate(Routes.adminModule(it)) },
                    onEmployeeMode = { viewModel.switchAppMode(AppMode.EMPLOYEE) },
                )
            }
            composable(Routes.ADMIN_MODULE, arguments = listOf(navArgument("moduleKey") {})) { backStack ->
                val key = backStack.arguments?.getString("moduleKey").orEmpty()
                NativeAdminModuleScreen(
                    key = key,
                    module = adminNativeModule?.takeIf { it.key == key },
                    busy = ui.busy,
                    members = adminMembers,
                    onLoad = viewModel::loadAdminNativeModule,
                    onAction = { item, action -> viewModel.performAdminNativeAction(key, item, action) },
                    onCommand = viewModel::executeAdminNativeCommand,
                    downloadArtifact = adminDownload,
                    onDownload = { item -> viewModel.downloadAdminArtifact(key, item) },
                    onDownloadHandled = viewModel::clearAdminDownload,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.MODULE, arguments = listOf(navArgument("moduleKey") {})) { backStack ->
                val key = backStack.arguments?.getString("moduleKey").orEmpty()
                LaunchedEffect(key) {
                    when (key) {
                        "tasks" -> viewModel.loadTasks()
                        "performance" -> viewModel.loadPerformance()
                        "consultations" -> viewModel.loadConsultations()
                        "content_tasks" -> viewModel.loadContentTasks()
                        "circle" -> viewModel.loadCircle()
                        "announcements" -> viewModel.loadAnnouncements()
                        "assistant" -> viewModel.loadAssistant()
                        "data_transfer" -> Unit
                        "agent" -> viewModel.loadAgentAuthorizations()
                        "devices" -> viewModel.loadMobileDevices()
                        else -> viewModel.openBusinessModule(key)
                    }
                }
                when (key) {
                    "tasks" -> TasksScreen(tasks, ui.busy, { nav.popBackStack() }, viewModel::claimTask, viewModel::submitTask)
                    "performance" -> PerformanceScreen(
                        performance, ui.busy, { nav.popBackStack() }, viewModel::submitPerformanceEvidence,
                        viewModel::proposePerformanceActivity, viewModel::completePerformanceActivity,
                    )
                    "consultations" -> ConsultationsScreen(
                        consultations, consultationOptions, ui.busy, { nav.popBackStack() },
                        viewModel::loadConsultationOptions, viewModel::submitConsultation,
                        viewModel::acceptConsultation, viewModel::rejectConsultation,
                        viewModel::endConsultation, viewModel::completeConsultation,
                    )
                    "content_tasks" -> ContentTasksScreen(contentTasks, ui.busy, { nav.popBackStack() }, viewModel::submitContentTask, viewModel::updateContentMetrics)
                    "circle" -> CircleScreen(circleFeed, ui.busy, { nav.popBackStack() }, viewModel::loadCircle)
                    "announcements" -> FeedScreen("公告中心", "阅读置顶与最新公告。", announcements.map { it.title to it.body }, { nav.popBackStack() })
                    "assistant" -> AssistantScreen(assistantWorkspace, ui.busy, { nav.popBackStack() }, viewModel::loadAssistant, viewModel::parseAssistantDraft, viewModel::runAssistantStream)
                    "data_transfer" -> DataTransferScreen(customerImport, customerDownload, ui.busy, { nav.popBackStack() }, viewModel::previewCustomerImport, viewModel::commitCustomerImport, viewModel::downloadCustomers, viewModel::clearCustomerDownload)
                    "agent" -> AccessListScreen("我的 Agent", agentAuthorizations.map { Triple(it.id, "${it.clientName} · ${it.scopes.joinToString()}", false) }, ui.busy, { nav.popBackStack() }, viewModel::revokeAgentAuthorization)
                    "devices" -> AccessListScreen("设备管理", mobileDevices.map { Triple(it.id, it.deviceName, it.current) }, ui.busy, { nav.popBackStack() }, viewModel::revokeMobileDevice)
                    else -> BusinessModuleScreen(businessModule?.takeIf { it.key == key }, ui.busy, onBack = { nav.popBackStack() }, onRetry = { viewModel.openBusinessModule(key) }, onAction = viewModel::actOnBusinessModuleItem)
                }
            }
        }
    }

    var urgentShown by remember(user.userId) { mutableStateOf(false) }
    if (!urgentShown && (dashboard?.staleAClassCustomers ?: 0) > 0) UrgentAClassDialog(dashboard!!.staleAClassCustomers, onLater = { urgentShown = true }, onHandle = { urgentShown = true; nav.navigate(Routes.CUSTOMERS) })
}

@Composable
private fun UrgentAClassDialog(count: Int, onLater: () -> Unit, onHandle: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(88.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp)) } }
                Text("A 类客户需要立即跟进", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
                Text("当前有 $count 位 A 类客户超过 72 小时没有有效时间线更新。每次登录都会重点提醒，直到产生有效跟进。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                Button(onClick = onHandle, modifier = Modifier.fillMaxWidth().padding(top = 30.dp).heightIn(min = 54.dp), shape = RoundedCornerShape(15.dp)) { Text("立即处理") }
                TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("稍后处理") }
            }
        }
    }
}
