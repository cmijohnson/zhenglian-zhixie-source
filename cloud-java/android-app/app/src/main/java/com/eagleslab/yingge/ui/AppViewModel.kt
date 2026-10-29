package com.eagleslab.yingge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eagleslab.yingge.YinggeApplication
import com.eagleslab.yingge.data.PreferencesStore
import com.eagleslab.yingge.data.RepositoryException
import com.eagleslab.yingge.data.ThemePreference
import com.eagleslab.yingge.data.YinggeRepository
import com.eagleslab.yingge.data.contract.ServerOriginValidator
import com.eagleslab.yingge.data.contract.MobileContractGateway
import com.eagleslab.yingge.data.contract.WebSessionTicketResponse
import com.eagleslab.yingge.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class AppUiState(
    val user: SessionUser? = null,
    val appMode: AppMode = AppMode.EMPLOYEE,
    val availableModes: Set<AppMode> = setOf(AppMode.EMPLOYEE),
    val capabilities: Set<String> = emptySet(),
    val busy: Boolean = false,
    val serverUrl: String = "",
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val message: String? = null,
    val error: String? = null,
    val registrationTeams: List<RegistrationTeam> = emptyList(),
    val serverTimeOffsetMillis: Long = 0,
    val organizationTimeZone: String = "Asia/Shanghai",
    val greeting: DashboardGreeting? = null,
    val availableUpdate: BusinessModuleActionItem? = null,
    val updatePromptVisible: Boolean = false,
)

class AppViewModel(
    application: Application,
    private val repository: YinggeRepository,
    private val preferences: PreferencesStore,
) : AndroidViewModel(application) {
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()
    private val _dashboard = MutableStateFlow<DashboardSnapshot?>(null)
    val dashboard = _dashboard.asStateFlow()
    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers = _customers.asStateFlow()
    private val _customerPage = MutableStateFlow(CustomerPage(emptyList(), 0, 30, 0, 0))
    val customerPage = _customerPage.asStateFlow()
    private val _customerOwnerOptions = MutableStateFlow<List<CustomerTransferCandidate>>(emptyList())
    val customerOwnerOptions = _customerOwnerOptions.asStateFlow()
    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer = _selectedCustomer.asStateFlow()
    private val _transferCandidates = MutableStateFlow<List<CustomerTransferCandidate>>(emptyList())
    val transferCandidates = _transferCandidates.asStateFlow()
    private val _customerCollaboration = MutableStateFlow<CustomerCollaboration?>(null)
    val customerCollaboration = _customerCollaboration.asStateFlow()
    private val _customerClassification = MutableStateFlow<CustomerClassificationState?>(null)
    val customerClassification = _customerClassification.asStateFlow()
    private val _customerDownload = MutableStateFlow<DownloadArtifact?>(null)
    val customerDownload = _customerDownload.asStateFlow()
    private val _customerImport = MutableStateFlow<CustomerImportState?>(null)
    val customerImport = _customerImport.asStateFlow()
    private val _customerInsight = MutableStateFlow<CustomerInsight?>(null)
    val customerInsight = _customerInsight.asStateFlow()
    private val _interactions = MutableStateFlow<List<Interaction>>(emptyList())
    val interactions = _interactions.asStateFlow()
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders = _reminders.asStateFlow()
    private val _daily = MutableStateFlow<DailySummary?>(null)
    val daily = _daily.asStateFlow()
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications = _notifications.asStateFlow()
    private val _businessModule = MutableStateFlow<BusinessModule?>(null)
    val businessModule = _businessModule.asStateFlow()
    private val _tasks = MutableStateFlow<List<WorkTask>>(emptyList())
    val tasks = _tasks.asStateFlow()
    private val _performance = MutableStateFlow<PerformanceSnapshot?>(null)
    val performance = _performance.asStateFlow()
    private val _consultations = MutableStateFlow<List<ConsultationItem>>(emptyList())
    val consultations = _consultations.asStateFlow()
    private val _consultationOptions = MutableStateFlow<ConsultationOptions?>(null)
    val consultationOptions = _consultationOptions.asStateFlow()
    private val _contentTasks = MutableStateFlow<List<ContentTaskItem>>(emptyList())
    val contentTasks = _contentTasks.asStateFlow()
    private val _circlePosts = MutableStateFlow<List<CirclePost>>(emptyList())
    val circlePosts = _circlePosts.asStateFlow()
    private val _circleFeed = MutableStateFlow(CircleFeed(emptyList(), emptyList(), 0, 20, 0))
    val circleFeed = _circleFeed.asStateFlow()
    private val _announcements = MutableStateFlow<List<AnnouncementItem>>(emptyList())
    val announcements = _announcements.asStateFlow()
    private val _assistantWorkspace = MutableStateFlow<AssistantWorkspace?>(null)
    val assistantWorkspace = _assistantWorkspace.asStateFlow()
    private val _agentAuthorizations = MutableStateFlow<List<AgentAuthorization>>(emptyList())
    val agentAuthorizations = _agentAuthorizations.asStateFlow()
    private val _mobileDevices = MutableStateFlow<List<MobileDeviceItem>>(emptyList())
    val mobileDevices = _mobileDevices.asStateFlow()
    private val _qqTicket = MutableStateFlow<WebSessionTicketResponse?>(null)
    val qqTicket = _qqTicket.asStateFlow()
    private val _adminOverview = MutableStateFlow<AdminOverview?>(null)
    val adminOverview = _adminOverview.asStateFlow()
    private val _adminMembers = MutableStateFlow<List<AdminMember>>(emptyList())
    val adminMembers = _adminMembers.asStateFlow()
    private val _adminTeams = MutableStateFlow<List<AdminTeam>>(emptyList())
    val adminTeams = _adminTeams.asStateFlow()
    private val _adminRegistrationCodes = MutableStateFlow<List<AdminRegistrationCode>>(emptyList())
    val adminRegistrationCodes = _adminRegistrationCodes.asStateFlow()
    private val _adminDealRequests = MutableStateFlow<List<DealRequest>>(emptyList())
    val adminDealRequests = _adminDealRequests.asStateFlow()
    private val _adminNativeModule = MutableStateFlow<AdminNativeModule?>(null)
    val adminNativeModule = _adminNativeModule.asStateFlow()
    private val _adminDownload = MutableStateFlow<DownloadArtifact?>(null)
    val adminDownload = _adminDownload.asStateFlow()
    private val _leadershipDashboard = MutableStateFlow<LeadershipDashboard?>(null)
    val leadershipDashboard = _leadershipDashboard.asStateFlow()
    val dailyDraft = preferences.dailyDraft.stateIn(viewModelScope, SharingStarted.Eagerly, DailySummaryDraft())
    val customerDraft = preferences.customerDraft.stateIn(viewModelScope, SharingStarted.Eagerly, CustomerDraft())

    init {
        viewModelScope.launch { preferences.serverUrl.collect { value -> _ui.update { it.copy(serverUrl = value) } } }
        viewModelScope.launch { preferences.theme.collect { value -> _ui.update { it.copy(theme = value) } } }
        viewModelScope.launch {
            val server = preferences.currentServerUrl()
            runCatching { repository.restoreSession(server); repository.bootstrap() }.onSuccess { bootstrap ->
                applyBootstrap(bootstrap)
                registerStoredPushIfAvailable()
                refreshAll()
                checkForAppUpdate(showPrompt = true)
            }
        }
    }

    fun login(serverUrl: String, username: String, password: String, insecureHttpConfirmed: Boolean) = launchAction {
        val canonical = ServerOriginValidator.requireValid(serverUrl, insecureHttpConfirmed).canonical
        preferences.setServerUrl(canonical)
        val user = repository.login(canonical, username, password)
        val bootstrap = repository.bootstrap()
        applyBootstrap(bootstrap, "登录成功")
        registerStoredPushIfAvailable()
        refreshAll()
        checkForAppUpdate(showPrompt = true)
    }

    fun prepareRegistration(serverUrl: String, insecureHttpConfirmed: Boolean, onReady: () -> Unit) = launchAction {
        val canonical = ServerOriginValidator.requireValid(serverUrl, insecureHttpConfirmed).canonical
        preferences.setServerUrl(canonical)
        onReady()
    }

    fun loadRegistrationOptions(serverUrl: String, registrationCode: String) = launchAction {
        val canonical = ServerOriginValidator.requireValid(serverUrl, allowInsecureHttp = serverUrl.startsWith("http://")).canonical
        preferences.setServerUrl(canonical)
        val options = repository.registrationOptions(canonical, registrationCode)
        _ui.update { it.copy(registrationTeams = options.teams, message = "邀请码有效，请选择团队") }
    }

    fun registerAndLogin(serverUrl: String, username: String, realName: String, password: String, registrationCode: String, teamId: String) = launchAction {
        val canonical = ServerOriginValidator.requireValid(serverUrl, allowInsecureHttp = serverUrl.startsWith("http://")).canonical
        preferences.setServerUrl(canonical)
        val user = repository.registerAndLogin(canonical, username, realName, password, registrationCode, teamId)
        val bootstrap = repository.bootstrap()
        applyBootstrap(bootstrap, "注册成功，已安全登录")
        registerStoredPushIfAvailable()
        refreshAll()
        checkForAppUpdate(showPrompt = true)
    }

    fun changePassword(currentPassword: String, newPassword: String) = launchAction {
        repository.changePassword(currentPassword, newPassword)
        preferences.clearSensitiveState()
        _ui.update { it.copy(user = null, message = "密码已修改，所有会话已退出，请重新登录") }
    }

    fun logout() = launchAction {
        repository.logout()
        preferences.clearSensitiveState()
        _ui.value = _ui.value.copy(user = null, appMode = AppMode.EMPLOYEE, availableModes = setOf(AppMode.EMPLOYEE), capabilities = emptySet(), message = null)
    }

    fun setServerUrl(value: String, insecureHttpConfirmed: Boolean, onSaved: () -> Unit) = launchAction {
        val canonical = ServerOriginValidator.requireValid(value, insecureHttpConfirmed).canonical
        if (canonical != _ui.value.serverUrl) {
            runCatching { repository.logout() }
            preferences.clearSensitiveState()
            _ui.update { it.copy(user = null) }
        }
        preferences.setServerUrl(canonical)
        onSaved()
    }
    fun setTheme(value: ThemePreference) { viewModelScope.launch { preferences.setTheme(value) } }
    fun switchAppMode(mode: AppMode) {
        if (mode !in _ui.value.availableModes) {
            _ui.update { it.copy(error = "当前账号没有该模式权限") }
            return
        }
        _ui.update { it.copy(appMode = mode) }
        if (mode == AppMode.ADMIN) refreshAdmin()
    }
    fun clearNotice() { _ui.update { it.copy(message = null, error = null) } }

    fun serverNow(): Instant = Instant.ofEpochMilli(System.currentTimeMillis() + _ui.value.serverTimeOffsetMillis)

    fun onAppForegrounded() {
        refreshGreeting()
        checkForAppUpdate(showPrompt = true)
    }

    fun dismissUpdatePrompt() { _ui.update { it.copy(updatePromptVisible = false) } }

    fun installAvailableUpdate() = launchAction {
        val update = _ui.value.availableUpdate ?: throw RepositoryException("UPDATE_NOT_FOUND", "暂无可用更新")
        repository.actOnBusinessModuleItem("releases", update.id)
        _ui.update { it.copy(updatePromptVisible = false, message = "更新包已校验，正在打开系统安装器") }
    }

    private fun checkForAppUpdate(showPrompt: Boolean) = viewModelScope.launch {
        runCatching { repository.businessModule("releases") }.getOrNull()?.actionItems
            ?.firstOrNull { it.id.startsWith("mobile-apk:") }
            ?.let { update -> _ui.update { it.copy(availableUpdate = update, updatePromptVisible = showPrompt) } }
    }

    fun refreshGreeting() = viewModelScope.launch {
        runCatching { repository.dashboardGreeting() }.getOrNull()?.let { greeting ->
            _ui.update { it.copy(greeting = greeting) }
        }
    }

    fun refreshAll() = launchAction(showSpinner = false) {
        val dashboardDeferred = viewModelScope.async { repository.dashboard() }
        val customersDeferred = viewModelScope.async { repository.customerPage(CustomerFilter()) }
        val remindersDeferred = viewModelScope.async { repository.reminders() }
        val dailyDeferred = viewModelScope.async { repository.dailySummary() }
        val notificationsDeferred = viewModelScope.async { repository.notifications() }
        _dashboard.value = dashboardDeferred.await()
        _customerPage.value = customersDeferred.await()
        _customers.value = _customerPage.value.items
        _reminders.value = remindersDeferred.await()
        _daily.value = dailyDeferred.await()
        _notifications.value = notificationsDeferred.await()
        _ui.update { it.copy(greeting = runCatching { repository.dashboardGreeting() }.getOrNull() ?: it.greeting) }
    }

    fun searchCustomers(query: String) = loadCustomerPage(_customerPage.value.let { CustomerFilter(search = query, page = 0, size = it.size) })

    fun loadCustomerPage(filter: CustomerFilter) = launchAction(showSpinner = false) {
        val page = repository.customerPage(filter)
        _customerPage.value = page
        _customers.value = page.items
    }

    fun loadCustomerOwnerOptions() = launchAction(showSpinner = false) {
        _customerOwnerOptions.value = repository.customerOwnerOptions()
    }

    fun openCustomer(id: String) = launchAction {
        loadCustomerInternal(id)
    }

    fun saveCustomer(draft: CustomerDraft, onSaved: (Customer) -> Unit) = launchAction {
        val saved = repository.saveCustomer(draft)
        preferences.clearCustomerDraft()
        _selectedCustomer.value = saved
        val page = repository.customerPage(CustomerFilter())
        _customerPage.value = page
        _customers.value = page.items
        _ui.update { it.copy(message = if (draft.id == null) "客户已创建" else "客户档案已更新") }
        onSaved(saved)
    }

    fun saveCustomerDraft(draft: CustomerDraft) { viewModelScope.launch { preferences.saveCustomerDraft(draft) } }
    fun clearCustomerDraft() { viewModelScope.launch { preferences.clearCustomerDraft() } }

    fun markNotificationRead(id: String) = launchAction(showSpinner = false) {
        repository.markNotificationRead(id)
        _notifications.value = repository.notifications()
    }

    fun markAllNotificationsRead() = launchAction(showSpinner = false) {
        repository.markAllNotificationsRead()
        _notifications.value = repository.notifications()
        _ui.update { it.copy(message = "通知已全部标记为已读") }
    }

    fun loadTransferCandidates(customerId: String) = launchAction {
        _transferCandidates.value = repository.customerTransferCandidates(customerId)
    }

    fun transferCustomer(customerId: String, targetMembershipId: String, reason: String, onTransferred: () -> Unit) = launchAction {
        _selectedCustomer.value = repository.transferCustomer(customerId, targetMembershipId, reason)
        _transferCandidates.value = emptyList()
        _customers.value = repository.customers()
        _ui.update { it.copy(message = "客户已安全转接") }
        onTransferred()
    }

    fun replaceCustomerCollaborators(customerId: String, membershipIds: Set<String>, onSaved: () -> Unit) = launchAction {
        _customerCollaboration.value = repository.replaceCustomerCollaborators(customerId, membershipIds)
        _selectedCustomer.value = repository.customer(customerId)
        _ui.update { it.copy(message = "客户共享协作者已更新") }
        onSaved()
    }

    fun archiveCustomer(customerId: String, onArchived: () -> Unit) = launchAction {
        repository.archiveCustomer(customerId)
        _selectedCustomer.value = repository.customer(customerId)
        _ui.update { it.copy(message = "客户已归档") }
        onArchived()
    }

    fun saveCustomerClassification(customerId: String, draft: CustomerClassificationDraft, onSaved: () -> Unit) = launchAction {
        _customerClassification.value = repository.saveCustomerClassification(customerId, draft)
        _selectedCustomer.value = repository.customer(customerId)
        _ui.update { it.copy(message = "客户分级认定已保存") }
        onSaved()
    }

    fun downloadCustomerTimeline(customerId: String) = launchAction { _customerDownload.value = repository.downloadCustomerTimeline(customerId) }
    fun clearCustomerDownload(saved: Boolean) { _customerDownload.value = null; if (saved) _ui.update { it.copy(message = "客户时间线已导出") } }
    fun previewCustomerImport(file: EvidenceUpload) = launchAction { _customerImport.value = repository.previewCustomerImport(file); _ui.update { it.copy(message = "导入预览已生成，请核对后提交") } }
    fun commitCustomerImport() = launchAction {
        val job = _customerImport.value ?: throw RepositoryException("IMPORT_PREVIEW_REQUIRED", "请先选择文件并生成预览")
        _customerImport.value = repository.commitCustomerImport(job.jobId)
        _ui.update { it.copy(message = "客户导入已完成") }
    }
    fun downloadCustomers(format: String) = launchAction { _customerDownload.value = repository.downloadCustomers(format) }

    fun loadCustomerInsight(customerId: String) = launchAction {
        _customerInsight.value = repository.customerInsight(customerId)
    }

    fun addInteraction(customerId: String, draft: InteractionDraft, onSaved: () -> Unit) = launchAction {
        repository.addInteraction(customerId, draft)
        loadCustomerInternal(customerId)
        _ui.update { it.copy(message = "跟进已写入统一时间线") }
        onSaved()
    }

    fun completeReminder(id: String) = launchAction(showSpinner = false) {
        repository.completeReminder(id)
        _reminders.value = repository.reminders()
        _ui.update { it.copy(message = "提醒已完成") }
    }

    fun saveDailyDraft(draft: DailySummaryDraft) { viewModelScope.launch { preferences.saveDailyDraft(draft) } }
    fun saveDailySummary(draft: DailySummaryDraft, onSaved: () -> Unit) = launchAction {
        _daily.value = repository.saveDailySummary(draft)
        preferences.clearDailyDraft()
        _ui.update { it.copy(message = "今日小结已提交") }
        onSaved()
    }

    fun requestDeal(customerId: String) = launchAction {
        val user = _ui.value.user ?: throw RepositoryException("MOBILE_SESSION_MISSING", "请重新登录")
        val key = preferences.getOrCreateDealKey(_ui.value.serverUrl, user.userId, customerId)
        repository.requestDeal(customerId, key)
        preferences.clearDealKey(_ui.value.serverUrl, user.userId, customerId)
        loadCustomerInternal(customerId)
        _ui.update { it.copy(message = "成单申请已提交，等待管理员审核") }
    }

    fun openBusinessModule(key: String) = launchAction {
        _businessModule.value = null
        try {
            _businessModule.value = repository.businessModule(key)
        } catch (error: RepositoryException) {
            _businessModule.value = failedBusinessModule(key, error.message ?: "服务端暂时不可用")
            throw error
        } catch (error: Exception) {
            _businessModule.value = failedBusinessModule(key, "网络连接失败，请检查服务器地址后重试。")
            throw error
        }
    }
    fun actOnBusinessModuleItem(key: String, id: String) = launchAction {
        _businessModule.value = repository.actOnBusinessModuleItem(key, id)
        _ui.update { it.copy(message = if (key == "pool") "已进入临时认领期" else "已领取二线协作") }
    }

    fun loadTasks() = launchAction { _tasks.value = repository.tasks() }
    fun claimTask(id: String) = launchAction { repository.claimTask(id); _tasks.value = repository.tasks(); _ui.update { it.copy(message = "任务已领取") } }
    fun submitTask(id: String, result: String) = launchAction { repository.submitTask(id, result); _tasks.value = repository.tasks(); _ui.update { it.copy(message = "任务成果已提交") } }
    fun loadPerformance() = launchAction { _performance.value = repository.performance() }
    fun submitPerformanceEvidence(interactionId: String, requestedType: String, evidence: EvidenceUpload) = launchAction { _performance.value = repository.submitPerformanceEvidence(interactionId, requestedType, evidence); _ui.update { it.copy(message = "绩效申报已提交审核") } }
    fun proposePerformanceActivity(eventDate: String, title: String, goal: String, expectedParticipants: Int, description: String) = launchAction {
        _performance.value = repository.proposePerformanceActivity(eventDate, title, goal, expectedParticipants, description)
        _ui.update { it.copy(message = "活动提案已提交立项审核") }
    }
    fun completePerformanceActivity(activityId: String, actualParticipants: Int, expectedVersion: Long, evidence: EvidenceUpload) = launchAction {
        _performance.value = repository.completePerformanceActivity(activityId, actualParticipants, expectedVersion, evidence)
        _ui.update { it.copy(message = "活动成果已提交验收") }
    }
    fun loadConsultations() = launchAction { _consultations.value = repository.consultations() }
    fun loadConsultationOptions(customer: String) = launchAction { _consultationOptions.value = repository.consultationOptions(customer) }
    fun submitConsultation(draft: ConsultationDraft) = launchAction { repository.submitConsultation(draft); _consultationOptions.value = null; _consultations.value = repository.consultations(); _ui.update { it.copy(message = "咨询交接已提交") } }
    fun acceptConsultation(id: String, expectedVersion: Long) = launchAction { repository.acceptConsultation(id, expectedVersion); _consultations.value = repository.consultations(); _ui.update { it.copy(message = "已接受咨询交接") } }
    fun rejectConsultation(id: String, expectedVersion: Long, reason: String) = launchAction { repository.rejectConsultation(id, expectedVersion, reason); _consultations.value = repository.consultations(); _ui.update { it.copy(message = "已拒绝咨询交接") } }
    fun endConsultation(id: String, expectedVersion: Long) = launchAction { repository.endConsultation(id, expectedVersion); _consultations.value = repository.consultations(); _ui.update { it.copy(message = "咨询已结束，请回填结果") } }
    fun completeConsultation(id: String, result: ConsultationResultDraft) = launchAction { repository.completeConsultation(id, result); _consultations.value = repository.consultations(); _ui.update { it.copy(message = "咨询结果已回填") } }
    fun loadContentTasks() = launchAction { _contentTasks.value = repository.contentTasks() }
    fun submitContentTask(id: String, platform: String, content: String) = launchAction { repository.submitContentTask(id, platform, content); _contentTasks.value = repository.contentTasks(); _ui.update { it.copy(message = "内容已提交") } }
    fun updateContentMetrics(id: String, metrics: String) = launchAction { repository.updateContentMetrics(id, metrics); _contentTasks.value = repository.contentTasks(); _ui.update { it.copy(message = "指标已更新") } }
    fun loadCircle(search: String = "", senderMembershipId: String? = null, page: Int = 0) = launchAction {
        _circleFeed.value = repository.circleFeed(search, senderMembershipId, page)
        _circlePosts.value = _circleFeed.value.items
    }
    fun loadAnnouncements() = launchAction { _announcements.value = repository.announcements() }
    fun loadAssistant() = launchAction { _assistantWorkspace.value = repository.assistantWorkspace() }
    fun parseAssistantDraft(mode: String, text: String) = launchAction { _assistantWorkspace.value = repository.parseAssistantDraft(mode, text) }
    fun runAssistantStream(kind: String, customerName: String?) = launchAction { _assistantWorkspace.value = repository.runAssistantStream(kind, customerName) }
    fun loadAgentAuthorizations() = launchAction { _agentAuthorizations.value = repository.agentAuthorizations() }
    fun revokeAgentAuthorization(id: String) = launchAction { repository.revokeAgentAuthorization(id); _agentAuthorizations.value = repository.agentAuthorizations(); _ui.update { it.copy(message = "Agent 授权已撤销") } }
    fun loadMobileDevices() = launchAction { _mobileDevices.value = repository.mobileDevices() }
    fun revokeMobileDevice(id: String) = launchAction { repository.revokeMobileDevice(id); _mobileDevices.value = repository.mobileDevices(); _ui.update { it.copy(message = "设备已撤销") } }

    fun refreshAdmin() = launchAction(showSpinner = false) {
        if (AppMode.ADMIN !in _ui.value.availableModes) throw RepositoryException("ADMIN_MODE_FORBIDDEN", "当前账号没有管理模式权限")
        val overview = viewModelScope.async { repository.adminOverview() }
        val members = viewModelScope.async { repository.adminMembers() }
        val teams = viewModelScope.async { repository.adminTeams() }
        val registrationCodes = viewModelScope.async { repository.adminRegistrationCodes() }
        val deals = viewModelScope.async { repository.adminDealRequests() }
        _adminOverview.value = overview.await()
        _adminMembers.value = members.await()
        _adminTeams.value = teams.await()
        _adminRegistrationCodes.value = registrationCodes.await()
        _adminDealRequests.value = deals.await()
    }

    fun loadAdminMembersTeams() = launchAction {
        _adminMembers.value = repository.adminMembers()
        _adminTeams.value = repository.adminTeams()
        _adminRegistrationCodes.value = repository.adminRegistrationCodes()
    }

    fun createAdminMember(username: String, displayName: String, initialPassword: String, role: String, teamId: String?) = launchAction {
        repository.createAdminMember(username, displayName, initialPassword, role, teamId)
        _adminMembers.value = repository.adminMembers()
        _adminTeams.value = repository.adminTeams()
        _ui.update { it.copy(message = "成员已创建；初始密码只应通过安全渠道交付") }
    }

    fun deleteAdminMember(member: AdminMember, transferTo: String?) = launchAction {
        if (member.ownedCustomerCount > 0 && transferTo.isNullOrBlank()) {
            throw RepositoryException("CUSTOMER_TRANSFER_REQUIRED", "该成员仍有客户，必须先选择客户及提醒接收人")
        }
        repository.deleteAdminMember(member.membershipId, transferTo)
        _adminMembers.value = repository.adminMembers()
        _adminTeams.value = repository.adminTeams()
        _ui.update { it.copy(message = "成员已删除，历史业务记录保持不变") }
    }

    fun createAdminTeam(name: String) = launchAction {
        repository.createAdminTeam(name)
        _adminTeams.value = repository.adminTeams()
        _ui.update { it.copy(message = "团队已创建") }
    }

    fun assignTeamLeader(teamId: String, membershipId: String) = launchAction {
        repository.assignTeamLeader(teamId, membershipId)
        _adminTeams.value = repository.adminTeams()
        _ui.update { it.copy(message = "唯一团队长已更新") }
    }

    fun deleteAdminTeam(teamId: String) = launchAction {
        repository.deleteAdminTeam(teamId)
        _adminTeams.value = repository.adminTeams()
        _adminMembers.value = repository.adminMembers()
        _ui.update { it.copy(message = "团队已删除，成员、客户和历史记录未删除") }
    }

    fun updateAdminMember(member: AdminMember, displayName: String, role: String, status: String, teamId: String?) = launchAction {
        repository.updateAdminMember(member, displayName, role, status, teamId)
        _adminMembers.value = repository.adminMembers()
        _adminTeams.value = repository.adminTeams()
        _ui.update { it.copy(message = "成员资料已更新") }
    }

    fun createAdminRegistrationCode(maxUses: Int, expiresAt: String?) = launchAction {
        val created = repository.createAdminRegistrationCode(maxUses, expiresAt)
        _adminRegistrationCodes.value = listOf(created) + repository.adminRegistrationCodes().filterNot { it.id == created.id }
        _ui.update { it.copy(message = "邀请码已生成：${created.plainCode ?: created.codeHint}（仅本次显示）") }
    }

    fun disableAdminRegistrationCode(id: String) = launchAction {
        repository.disableAdminRegistrationCode(id)
        _adminRegistrationCodes.value = repository.adminRegistrationCodes()
        _ui.update { it.copy(message = "邀请码已停用") }
    }

    fun loadDealRequests() = launchAction { _adminDealRequests.value = repository.adminDealRequests() }
    fun reviewDealRequest(request: DealRequest, approve: Boolean) = launchAction {
        repository.reviewDealRequest(request.id, request.version, approve)
        _adminDealRequests.value = repository.adminDealRequests()
        _adminOverview.value = repository.adminOverview()
        _ui.update { it.copy(message = if (approve) "已确认成单" else "已驳回成单申请") }
    }

    fun loadLeadership(teamId: String? = null, membershipId: String? = null) = launchAction(showSpinner = false) {
        _leadershipDashboard.value = repository.leadershipDashboard(teamId, membershipId)
    }

    fun loadAdminNativeModule(key: String) = launchAction {
        _adminNativeModule.value = null
        _adminNativeModule.value = repository.adminNativeModule(key)
    }

    fun performAdminNativeAction(key: String, item: AdminNativeItem, action: String) = launchAction {
        _adminNativeModule.value = repository.performAdminNativeAction(key, item.id, action, item.version)
        _adminOverview.value = runCatching { repository.adminOverview() }.getOrNull() ?: _adminOverview.value
        _ui.update { it.copy(message = "“${item.title}”已执行：$action") }
    }

    fun executeAdminNativeCommand(command: AdminNativeCommand) = launchAction {
        _adminNativeModule.value = repository.executeAdminNativeCommand(command)
        _adminOverview.value = runCatching { repository.adminOverview() }.getOrNull() ?: _adminOverview.value
        _ui.update { it.copy(message = "管理操作已提交并完成服务端校验") }
    }

    fun downloadAdminArtifact(key: String, item: AdminNativeItem) = launchAction {
        _adminDownload.value = repository.downloadAdminArtifact(key, item.id.removePrefix("export:"))
    }

    fun clearAdminDownload(saved: Boolean) {
        _adminDownload.value = null
        if (saved) _ui.update { it.copy(message = "文件已保存") }
    }

    fun acceptPushRegistration(registrationId: String, vendor: String?) = launchAction(showSpinner = false) {
        preferences.savePushRegistration(registrationId, vendor)
        if (_ui.value.user != null) repository.registerPushToken(registrationId, vendor)
    }

    fun openQqWorkbench(onReady: () -> Unit) = launchAction {
        val server = _ui.value.serverUrl
        val gateway = MobileContractGateway.create(getApplication(), server, allowInsecureHttp = server.startsWith("http://"))
        _qqTicket.value = gateway.createQqTicket()
        onReady()
    }

    fun clearQqTicket() { _qqTicket.value = null }

    private fun applyBootstrap(bootstrap: MobileAppBootstrap, message: String? = null) {
        _ui.update {
            it.copy(
                user = bootstrap.user,
                appMode = AppMode.EMPLOYEE,
                availableModes = bootstrap.availableModes,
                capabilities = bootstrap.capabilities,
                serverTimeOffsetMillis = bootstrap.serverTime.toEpochMilli() - System.currentTimeMillis(),
                organizationTimeZone = bootstrap.organizationTimeZone,
                registrationTeams = emptyList(),
                message = message,
            )
        }
    }

    private suspend fun registerStoredPushIfAvailable() {
        preferences.pushRegistration()?.let { repository.registerPushToken(it.registrationId, it.vendor) }
    }

    private suspend fun loadCustomerInternal(id: String) {
        _customerInsight.value = null
        val customerDeferred = viewModelScope.async { repository.customer(id) }
        val timelineDeferred = viewModelScope.async { repository.interactions(id) }
        val collaborationDeferred = viewModelScope.async { repository.customerCollaboration(id) }
        val classificationDeferred = viewModelScope.async { repository.customerClassificationState(id) }
        _selectedCustomer.value = customerDeferred.await()
        _interactions.value = timelineDeferred.await()
        _customerCollaboration.value = collaborationDeferred.await()
        _customerClassification.value = classificationDeferred.await()
    }

    private fun launchAction(showSpinner: Boolean = true, block: suspend () -> Unit) = viewModelScope.launch {
        if (showSpinner) _ui.update { it.copy(busy = true, error = null) }
        try { block() }
        catch (error: RepositoryException) {
            _ui.update { it.copy(error = buildString { append(error.message); error.traceId?.let { append("（追踪号：$it）") } }) }
        } catch (_: Exception) {
            _ui.update { it.copy(error = "操作暂时失败，请检查网络后重试") }
        } finally {
            if (showSpinner) _ui.update { it.copy(busy = false) }
        }
    }

    companion object {
        fun factory(application: YinggeApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(application, application.repository, application.preferences) as T
        }
    }
}
