package dev.vibex.companion.app

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vibex.companion.AccountAuthErrors
import dev.vibex.companion.AccountAuthRequests
import dev.vibex.companion.AccountDesktop
import dev.vibex.companion.AccountDesktops
import dev.vibex.companion.AccountLinkException
import dev.vibex.companion.AccountLinkFailure
import dev.vibex.companion.AgentPlanUsage
import dev.vibex.companion.AuthRequiredException
import dev.vibex.companion.MobileDeviceConflictException
import dev.vibex.companion.CatalogAgent
import dev.vibex.companion.CatalogWorkspace
import dev.vibex.companion.CollabBotAvatarConfig
import dev.vibex.companion.CollabCard
import dev.vibex.companion.CollabCollectionItem
import dev.vibex.companion.CollabContactGroup
import dev.vibex.companion.CollabMemberConfig
import dev.vibex.companion.CollabMemoryView
import dev.vibex.companion.CollabMessagesPage
import dev.vibex.companion.CollabSidebarItem
import dev.vibex.companion.CollabSpaceMember
import dev.vibex.companion.CollabSpaceSummary
import dev.vibex.companion.CollabTeam
import dev.vibex.companion.CollaborationMessageItem
import dev.vibex.companion.CollaborationThreadSummary
import dev.vibex.companion.ConnectionState
import dev.vibex.companion.ConversationSummary
import dev.vibex.companion.ConversationView
import dev.vibex.companion.DiscoveredLanHost
import dev.vibex.companion.EventFold
import dev.vibex.companion.ExtensionCatalogItem
import dev.vibex.companion.HostApi
import dev.vibex.companion.HostOrigin
import dev.vibex.companion.KanbanSessionStatus
import dev.vibex.companion.KanbanSessionSummary
import dev.vibex.companion.OriginOrder
import dev.vibex.companion.PairingClient
import dev.vibex.companion.PairingCoordinator
import dev.vibex.companion.PairingInvitationParser
import dev.vibex.companion.PendingItem
import dev.vibex.companion.PlanUsageResponse
import dev.vibex.companion.ProjectUsageStatistics
import dev.vibex.companion.ReachabilityTarget
import dev.vibex.companion.SessionCatalog
import dev.vibex.companion.SubagentRoleItem
import dev.vibex.companion.TimelineSession
import dev.vibex.companion.UsageDateRange
import dev.vibex.companion.WorkspaceFileDiff
import dev.vibex.companion.agentPlanUsage
import dev.vibex.companion.applyCreateTimeSessionConfig
import dev.vibex.companion.collabAddMemberToSpace
import dev.vibex.companion.collabCancelSpaceTurn
import dev.vibex.companion.collabCollectionItems
import dev.vibex.companion.collabContactGroups
import dev.vibex.companion.collabCreateMember
import dev.vibex.companion.collabMarkRead
import dev.vibex.companion.collabMarkUnread
import dev.vibex.companion.collabMemberConfig
import dev.vibex.companion.collabMemoryView
import dev.vibex.companion.collabMessagesPage
import dev.vibex.companion.collabPostMessage
import dev.vibex.companion.collabReact
import dev.vibex.companion.collabResolveCard
import dev.vibex.companion.collabResolveSession
import dev.vibex.companion.collabSidebar
import dev.vibex.companion.collabSpaceMembers
import dev.vibex.companion.collabSpaces
import dev.vibex.companion.collabTeamsList
import dev.vibex.companion.deleteKanbanSession
import dev.vibex.companion.forkKanbanConversation
import dev.vibex.companion.kanbanAllSessions
import dev.vibex.companion.markKanbanSessionViewed
import dev.vibex.companion.projectUsageStatistics
import dev.vibex.companion.renameKanbanSession
import dev.vibex.companion.setKanbanSessionPinned
import dev.vibex.companion.updateKanbanSessionStatus
import dev.vibex.companion.app.data.CredentialStore
import dev.vibex.companion.app.data.HostProfile
import dev.vibex.companion.app.data.OfflineStore
import dev.vibex.companion.app.data.ProfileStore
import dev.vibex.companion.app.data.StoredReachability
import dev.vibex.companion.app.data.StoredState
import dev.vibex.companion.app.data.StreamDisplayPrefs
import dev.vibex.companion.app.data.VibeXAccountException
import dev.vibex.companion.app.data.VibeXAccountOverview
import dev.vibex.companion.app.data.VibeXAccountRepository
import dev.vibex.companion.app.data.VibeXDeviceLogin
import dev.vibex.companion.app.data.VibeXSyncableHostProfile
import android.content.Intent
import android.net.Uri
import java.time.Instant
import dev.vibex.companion.PendingQuestion
import dev.vibex.companion.PermissionAllowScope
import dev.vibex.companion.PermissionOption
import dev.vibex.companion.PermissionPrompt
import dev.vibex.companion.PlanTask
import dev.vibex.companion.QuestionAnswer
import dev.vibex.companion.QuestionChoice
import dev.vibex.companion.QuestionForm
import dev.vibex.companion.app.monitor.AttentionToastKind
import dev.vibex.companion.app.monitor.CompanionAttentionToast
import dev.vibex.companion.app.monitor.CompanionMonitorService
import dev.vibex.companion.app.monitor.SystemIslandOverlayController
import dev.vibex.companion.app.monitor.SystemNotificationAction
import dev.vibex.companion.app.monitor.SystemNotifier
import dev.vibex.companion.app.monitor.TaskLiveActivityState
import dev.vibex.companion.app.monitor.VibeXActivityPhase
import dev.vibex.companion.app.ui.theme.Appearance
import dev.vibex.companion.app.ui.theme.AccentChoice
import dev.vibex.companion.app.net.EventSocket
import dev.vibex.companion.app.net.OkHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val profiles: List<HostProfile> = emptyList(),
    val selectedHostId: String? = null,
    val connection: ConnectionState = ConnectionState.Offline,
    val activeOrigin: String? = null,
    val triedOrigins: List<String> = emptyList(),
    val conversations: List<ConversationSummary> = emptyList(),
    val query: String = "",
    val inbox: List<PendingItem> = emptyList(),
    val timeline: ConversationView? = null,
    val catalog: SessionCatalog? = null,
    val catalogLoading: Boolean = false,
    val conversationsLoading: Boolean = false,
    val selectedProjectId: String? = null,
    val projectQuery: String = "",
    val sinceDays: Int = 3,
    val error: String? = null,
    val notice: String? = null,
    val activeAttentionToast: CompanionAttentionToast? = null,
    val attentionActionBusy: Boolean = false,
    val attentionActionError: String? = null,
    val liveActivity: TaskLiveActivityState? = null,
    val pairing: Boolean = false,
    val monitor: Boolean = true,
    val appearance: Appearance = Appearance.System,
    val accent: AccentChoice = AccentChoice.Blue,
    val streamDisplay: StreamDisplayPrefs = StreamDisplayPrefs(),
    val hostVersion: String = "",
    val hostProtocol: String = "",
    val hostId: String = "",
    val hostCapabilities: List<String> = emptyList(),
    val collaborationThreads: List<CollaborationThreadSummary> = emptyList(),
    val collaborationLoading: Boolean = false,
    val selectedThread: CollaborationThreadSummary? = null,
    val collaborationMessages: List<CollaborationMessageItem> = emptyList(),
    val skillsCatalog: List<ExtensionCatalogItem> = emptyList(),
    val mcpCatalog: List<ExtensionCatalogItem> = emptyList(),
    val pluginsCatalog: List<ExtensionCatalogItem> = emptyList(),
    val subagentRoles: List<SubagentRoleItem> = emptyList(),
    val extensionsLoading: Boolean = false,
    val workspaceDiffs: List<WorkspaceFileDiff> = emptyList(),
    val diffsLoading: Boolean = false,
    val discoveredHosts: List<DiscoveredLanHost> = emptyList(),
    val discoveringHosts: Boolean = false,
    val lastConnectDurationMs: Long? = null,
    val realtimeRttMs: Long? = null,
    val transportPath: String = "LAN 直连",
    val connectionFailureReason: String? = null,
    val pendingConflictDeviceName: String? = null,
    val pendingCloudFallbackOrigin: String? = null,
    val pendingCloudFallbackHostName: String? = null,
    val autoConnect: Boolean = true,
    val accountSignedIn: Boolean = false,
    val accountOverview: VibeXAccountOverview? = null,
    val accountDesktops: List<AccountDesktop> = emptyList(),
    val accountSavedHosts: List<VibeXSyncableHostProfile> = emptyList(),
    val accountLoading: Boolean = false,
    val accountSyncingHosts: Boolean = false,
    val accountSigningIn: Boolean = false,
    val accountSendingCode: Boolean = false,
    val accountCodeSent: Boolean = false,
    val accountCodeResendAtMs: Long = 0L,
    val accountSigningOut: Boolean = false,
    val accountError: String? = null,
    val accountLoginRequested: Boolean = false,
    val desktopConnect: DesktopConnectSheet? = null,
    val route: Route = Route.Conversations,
) {
    /** Signed out with nothing paired, or right after an explicit sign-out. */
    val showsLogin: Boolean
        get() = !accountSignedIn && (profiles.isEmpty() || accountLoginRequested)
}

enum class SettingsSection { Profile, Account, Connection, Extensions, Font, Notification, About }

sealed class Route {
    data object ManualPair : Route()
    data object Folders : Route()
    data object Conversations : Route()
    data object Status : Route()
    data object Collaboration : Route()
    data class Settings(val section: SettingsSection? = null) : Route()
    data class Timeline(val conversationId: String) : Route()
}

data class CollabDetailBundle(
    val members: List<CollabSpaceMember> = emptyList(),
    val leadMemberId: String = "",
    val memberConfig: CollabMemberConfig? = null,
    val memory: CollabMemoryView = CollabMemoryView(),
    val collection: List<CollabCollectionItem> = emptyList(),
)

private const val PREF_ACCOUNT_LOGIN_REQUESTED = "account_login_requested"

class CompanionModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("vibex_companion_prefs", Context.MODE_PRIVATE)
    private val profiles = ProfileStore(application)
    private val credentials = CredentialStore(application)
    private val offline = OfflineStore(application)
    private val accountRepo = VibeXAccountRepository(application)
    private var accountLoginPollJob: Job? = null
    private val transport = OkHttpTransport()
    private val api = HostApi(transport)
    private val pairingClient = PairingClient(transport)
    private val pairing = PairingCoordinator(pairingClient, api)
    private val socket = EventSocket()
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, TimelineSession>()
    private val subscriptions = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var pingJob: Job? = null
    private var timelinePollJob: Job? = null
    private var connectPollJob: Job? = null
    private var autoReconnectJob: Job? = null
    private var noticeDismissJob: Job? = null
    private var pendingConflictRetryAction: (() -> Unit)? = null
    private val declinedCloudFallbackHostIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val notifiedEventKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val knownConversationIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val locallyCreatedConversationIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val wasRunningByConversation = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val initializedConversations = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val liveRunStartMsByConversation = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val livePeakProgressByConversation = java.util.concurrent.ConcurrentHashMap<String, Float>()
    private var liveActivityLingerJob: Job? = null
    @Volatile
    private var activeLiveConversationId: String? = null
    @Volatile
    private var didSeedKnownConversations: Boolean = false
    @Volatile
    private var hasAutoOpenedInitialConversation: Boolean = false
    @Volatile
    private var userRequestedNewChatLanding: Boolean = false
    @Volatile
    private var boostPollingUntil: Long = 0L

    private fun scheduleAutoDismissNotice(delayMs: Long = 2_800L) {
        noticeDismissJob?.cancel()
        noticeDismissJob = viewModelScope.launch {
            delay(delayMs)
            _state.update { it.copy(notice = null, error = null) }
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val stored = profiles.load()
        val savedAutoConnect = prefs.getBoolean("auto_connect", true)
        val selectedHostId = stored.selectedHostId ?: stored.profiles.firstOrNull()?.hostId
        val selected = stored.profiles.find { it.hostId == selectedHostId }
        val hasAccountToken = accountRepo.getToken() != null
        _state.value = UiState(
            profiles = stored.profiles,
            selectedHostId = selectedHostId,
            selectedProjectId = selected?.selectedProjectId,
            appearance = stored.appearance,
            accent = if (stored.accent == AccentChoice.Ink) AccentChoice.Blue else stored.accent,
            streamDisplay = stored.streamDisplay,
            autoConnect = savedAutoConnect,
            accountSignedIn = hasAccountToken,
            accountLoginRequested = prefs.getBoolean(PREF_ACCOUNT_LOGIN_REQUESTED, false),
            route = Route.Conversations,
            connection = if (stored.profiles.isEmpty() || !savedAutoConnect) ConnectionState.Offline else ConnectionState.Connecting,
            connectionFailureReason = if (!savedAutoConnect && stored.profiles.isNotEmpty()) "自动连接已关闭" else null,
        )
        if (hasAccountToken) {
            refreshAccount()
        }
        if (stored.profiles.isNotEmpty() && savedAutoConnect) {
            connectSelected()
        } else {
            discoverHosts()
        }
        startAutoReconnectLoop()
        SystemIslandOverlayController.onDismissToast = { dismissAttentionToast() }
        SystemIslandOverlayController.onDismissIsland = { dismissLiveActivityOverlay() }
        SystemIslandOverlayController.onRespondPermission = { convId, permId, optId ->
            respondToastPermission(convId, permId, optId)
        }
        SystemIslandOverlayController.onRespondQuestion = { convId, question, choice, fallbackText ->
            respondToastQuestion(convId, question, choice, fallbackText)
        }
        SystemIslandOverlayController.onSendQuickReply = { toast, text ->
            sendToastQuickReply(toast, text)
        }
    }

    private fun syncSystemIslandOverlay() {
        val current = _state.value
        SystemIslandOverlayController.updateState(
            context = getApplication(),
            liveActivity = current.liveActivity,
            toast = current.activeAttentionToast,
            busy = current.attentionActionBusy,
            error = current.attentionActionError,
        )
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        if (inForeground) {
            // If the live activity was only a temporary background standby pill, clear it on return
            val currentLive = _state.value.liveActivity
            if (currentLive != null && currentLive.stageTitle == "后台守护中") {
                _state.update { it.copy(liveActivity = null) }
            }
            syncSystemIslandOverlay()
            return
        }
        // Entering background (exited app to Home screen / other apps)
        if (_state.value.liveActivity == null) {
            val activeId = currentObservedConversationId() ?: _state.value.conversations.firstOrNull()?.id
            val session = activeId?.let { sessions[it] }
            if (session != null) {
                syncSessionAttentionAndLiveActivity(session, isFirstSnapshot = false)
            }
            if (_state.value.liveActivity == null && !activeId.isNullOrBlank()) {
                val summary = _state.value.conversations.find { it.id == activeId }
                val title = session?.title?.ifBlank { null }
                    ?: summary?.title?.ifBlank { null }
                    ?: "当前会话"
                val wsId = session?.workspaceId?.ifBlank { null }
                    ?: summary?.workspaceId.orEmpty()
                val project = resolveProjectName(wsId)
                val standby = TaskLiveActivityState(
                    conversationId = activeId,
                    projectName = project,
                    conversationTitle = title,
                    phase = VibeXActivityPhase.Thinking,
                    stageTitle = "后台守护中",
                    stageSubtitle = "VibeX 正在后台实时监听任务进展，点按返回会话",
                    progress = 0.18f,
                    completed = 0,
                    total = 0,
                    stepCount = session?.rows?.count { it.kind == "tool" } ?: 0,
                    isRunning = true,
                    startedAtMs = System.currentTimeMillis(),
                )
                _state.update { it.copy(liveActivity = standby) }
            }
        }
        syncSystemIslandOverlay()
    }

    fun setAutoConnect(enabled: Boolean) {
        prefs.edit().putBoolean("auto_connect", enabled).apply()
        _state.update { it.copy(autoConnect = enabled) }
        if (enabled && _state.value.profiles.isNotEmpty() && _state.value.connection != ConnectionState.Online) {
            connectSelected()
        }
    }

    fun confirmReplaceMobileDevice() {
        val retry = pendingConflictRetryAction
        pendingConflictRetryAction = null
        _state.update { it.copy(pendingConflictDeviceName = null) }
        retry?.invoke()
    }

    fun cancelReplaceMobileDevice() {
        pendingConflictRetryAction = null
        _state.update { state ->
            val sheet = state.desktopConnect?.let {
                if (it.stage is DesktopConnectStage.Connecting) it.copy(stage = DesktopConnectStage.Choosing) else it
            }
            state.copy(pendingConflictDeviceName = null, pairing = false, desktopConnect = sheet)
        }
    }

    fun confirmSwitchToCloudFallback() {
        val cloudOrigin = _state.value.pendingCloudFallbackOrigin ?: return
        val profile = selectedProfile() ?: return
        declinedCloudFallbackHostIds.remove(profile.hostId)
        _state.update {
            it.copy(
                pendingCloudFallbackOrigin = null,
                pendingCloudFallbackHostName = null,
            )
        }
        val updated = profile.copy(
            lastSuccessOrigin = cloudOrigin,
            reachability = (profile.reachability + StoredReachability(cloudOrigin, "published")).distinctBy { it.origin },
        )
        upsertProfile(updated, true)
        viewModelScope.launch {
            connect(updated, allowCloudSwitch = true)
        }
    }

    fun cancelSwitchToCloudFallback() {
        selectedProfile()?.let { declinedCloudFallbackHostIds.add(it.hostId) }
        _state.update {
            it.copy(
                pendingCloudFallbackOrigin = null,
                pendingCloudFallbackHostName = null,
            )
        }
    }

    private fun startAutoReconnectLoop() {
        autoReconnectJob?.cancel()
        autoReconnectJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val current = _state.value
                val sheetStage = current.desktopConnect?.stage
                val accountConnectInFlight = sheetStage is DesktopConnectStage.Connecting ||
                sheetStage is DesktopConnectStage.AwaitingApproval
                if (current.profiles.isNotEmpty() && current.autoConnect && !accountConnectInFlight) {
                    if (
                        current.connection == ConnectionState.Offline ||
                        current.connection == ConnectionState.Recovering ||
                        current.connection == ConnectionState.HostSleeping
                    ) {
                        val profile = selectedProfile()
                        if (profile != null) {
                            connect(profile)
                        }
                    }
                }
                if (
                    sheetStage == DesktopConnectStage.Choosing &&
                    current.discoveredHosts.isEmpty() &&
                    !current.discoveringHosts
                ) {
                    discoverHosts()
                }
            }
        }
    }

    fun discoverHosts() {
        if (_state.value.discoveringHosts) return
        viewModelScope.launch {
            _state.update { it.copy(discoveringHosts = true) }
            val found = withContext(Dispatchers.IO) {
                runCatching { PairingClient.discoverLanHosts() }.getOrDefault(emptyList())
            }
            _state.update { it.copy(discoveredHosts = found, discoveringHosts = false) }
        }
    }

    /** Opens the「发现你的电脑」popup and refreshes the account's desktops and the LAN scan. */
    fun openDesktopConnect() {
        if (!_state.value.accountSignedIn) return
        connectPollJob?.cancel()
        _state.update { it.copy(desktopConnect = DesktopConnectSheet()) }
        refreshAccount()
        discoverHosts()
    }

    fun dismissDesktopConnect() {
        connectPollJob?.cancel()
        connectPollJob = null
        _state.update { it.copy(desktopConnect = null) }
    }

    fun selectAccountDesktop(hostId: String) {
        _state.update { state ->
            state.copy(desktopConnect = state.desktopConnect?.copy(selectedHostId = hostId, stage = DesktopConnectStage.Choosing))
        }
    }

    fun retryDesktopConnect() {
        val stage = _state.value.desktopConnect?.stage as? DesktopConnectStage.Failed ?: return
        val route = stage.route
        if (route == null) openDesktopConnect() else connectAccountDesktop(route)
    }

    /** LAN origin of an account desktop when the current Wi-Fi scan found it. */
    fun lanOriginFor(desktop: AccountDesktop): String? =
        _state.value.discoveredHosts.firstOrNull { it.hostId == desktop.hostId }?.origin

    fun connectAccountDesktop(route: DesktopConnectRoute, replaceExistingMobile: Boolean = false) {
        val current = _state.value
        val sheet = current.desktopConnect ?: return
        val desktop = current.accountDesktops.firstOrNull { it.hostId == sheet.selectedHostId } ?: return
        val originUrl = when (route) {
            DesktopConnectRoute.Lan -> lanOriginFor(desktop) ?: return
            DesktopConnectRoute.Cloud -> AccountDesktops.tenantOrigin(
                AccountDesktops.relayBase(current.accountOverview?.assignedRelayServer, PairingClient.DEFAULT_OFFICIAL_RELAY_BASE),
                desktop.hostId,
            )
        }
        val origin = runCatching { HostOrigin.parse(originUrl) }.getOrNull() ?: return
        if (route == DesktopConnectRoute.Cloud) PairingClient.rememberRelayBase(origin.value)
        val reachabilityKind = if (route == DesktopConnectRoute.Lan) "lan" else "published"
        fun setStage(stage: DesktopConnectStage) =
            _state.update { it.copy(desktopConnect = it.desktopConnect?.copy(stage = stage)) }

        connectPollJob?.cancel()
        connectPollJob = viewModelScope.launch {
            setStage(DesktopConnectStage.Connecting(route))
            val saved = current.accountSavedHosts.firstOrNull {
                AccountDesktops.rawHostId(it.hostId) == desktop.hostId && it.accessToken.isNotBlank()
            }
            if (saved != null) {
                val existing = current.profiles.firstOrNull { AccountDesktops.rawHostId(it.hostId) == desktop.hostId }
                val hostId = existing?.hostId ?: desktop.hostId
                credentials.put(hostId, saved.accessToken)
                val base = existing ?: HostProfile(
                    hostId = hostId,
                    name = desktop.hostName,
                    deviceId = saved.deviceId.ifBlank { saved.profileId },
                    reachability = emptyList(),
                    lastSuccessOrigin = null,
                    lastSyncAt = System.currentTimeMillis(),
                )
                val profile = base.copy(
                    reachability = (listOf(StoredReachability(origin.value, reachabilityKind)) + base.reachability)
                        .distinctBy { it.origin },
                    lastSuccessOrigin = origin.value,
                )
                upsertProfile(profile, selected = true)
                connect(profile, allowCloudSwitch = route == DesktopConnectRoute.Cloud)
                val revoked = _state.value.profiles.none { it.hostId == hostId }
                if (!revoked) {
                    if (_state.value.connection != ConnectionState.Online) {
                        setStage(DesktopConnectStage.Failed(_state.value.connectionFailureReason ?: "电脑离线", route))
                    }
                    return@launch
                }
                // The synced credential was revoked on the desktop; ask it for a fresh one instead.
                _state.update { it.copy(error = null, connectionFailureReason = null, accountSavedHosts = it.accountSavedHosts - saved) }
            }

            val accountToken = accountRepo.getToken()
            if (accountToken == null) {
                signOutAccount()
                return@launch
            }
            val requested = withContext(Dispatchers.IO) {
                runCatching {
                    pairingClient.requestConnect(
                        origin = origin,
                        deviceName = Build.MODEL.ifBlank { "Android Companion" },
                        replaceExistingMobile = replaceExistingMobile,
                        accountToken = accountToken,
                    )
                }
            }.getOrElse { err ->
                when {
                    err is MobileDeviceConflictException -> {
                        pendingConflictRetryAction = { connectAccountDesktop(route, replaceExistingMobile = true) }
                        _state.update { it.copy(pendingConflictDeviceName = err.existingDeviceName) }
                    }
                    err is AccountLinkException && err.failure == AccountLinkFailure.AccountTokenInvalid -> signOutAccount()
                    else -> setStage(DesktopConnectStage.Failed(desktopConnectErrorMessage(err), route))
                }
                return@launch
            }
            setStage(DesktopConnectStage.AwaitingApproval(route, requested.sasCode))

            while (isActive) {
                delay(1_500)
                val poll = withContext(Dispatchers.IO) {
                    runCatching { pairingClient.pollConnect(origin, requested.requestId) }.getOrNull()
                } ?: continue
                when (poll.status) {
                    "approved" -> {
                        val cred = poll.credential ?: continue
                        val hostId = poll.hostId.ifBlank { desktop.hostId }
                        credentials.put(hostId, cred.access_token)
                        val profile = HostProfile(
                            hostId = hostId,
                            name = (poll.hostName ?: requested.hostName)?.ifBlank { null } ?: desktop.hostName,
                            deviceId = cred.device_id,
                            reachability = listOf(StoredReachability(origin.value, reachabilityKind)),
                            lastSuccessOrigin = origin.value,
                            lastSyncAt = System.currentTimeMillis(),
                            selectedProjectId = null,
                        )
                        upsertProfile(profile, selected = true)
                        _state.update { it.copy(route = Route.Conversations) }
                        SystemNotifier.notifyStatus(getApplication(), "已连接电脑", profile.name)
                        connect(profile, allowCloudSwitch = route == DesktopConnectRoute.Cloud)
                        if (_state.value.connection != ConnectionState.Online) {
                            setStage(DesktopConnectStage.Failed(_state.value.connectionFailureReason ?: "电脑离线", route))
                        }
                        return@launch
                    }
                    "rejected" -> {
                        setStage(DesktopConnectStage.Failed(DESKTOP_APPROVAL_REJECTED, route))
                        return@launch
                    }
                    "expired" -> {
                        setStage(DesktopConnectStage.Failed(DESKTOP_APPROVAL_EXPIRED, route))
                        return@launch
                    }
                }
            }
        }
    }


    fun selectProject(projectId: String) {
        val profile = selectedProfile()?.copy(selectedProjectId = projectId) ?: return
        upsertProfile(profile, selected = true)
        _state.update { it.copy(selectedProjectId = projectId, route = Route.Conversations) }
        viewModelScope.launch { runCatching { loadConversations() }.onFailure { fail(it) } }
    }

    fun selectProjectInPlace(projectId: String?) {
        val profile = selectedProfile()?.copy(selectedProjectId = projectId) ?: return
        upsertProfile(profile, selected = true)
        _state.update { it.copy(selectedProjectId = projectId) }
        viewModelScope.launch { runCatching { loadConversations() }.onFailure { fail(it) } }
    }

    fun loadCatalogSilent() {
        viewModelScope.launch {
            runCatching { fetchCatalog() }.onFailure { fail(it) }
        }
    }

    fun query(value: String) = _state.update { it.copy(query = value) }

    fun queryProject(value: String) = _state.update { it.copy(projectQuery = value) }

    fun setSinceDays(days: Int) {
        _state.update { it.copy(sinceDays = days.coerceIn(1, 90)) }
        refreshConversations()
    }

    fun refreshCurrent() {
        loadCatalogSilent()
        refreshConversations()
    }

    fun go(route: Route) = _state.update { it.copy(route = route, error = null) }

    fun dismissNotice() {
        noticeDismissJob?.cancel()
        _state.update { it.copy(notice = null, error = null) }
    }

    fun clearOfflineCache() {
        offline.clear()
        _state.update { it.copy(notice = "已清除本机缓存") }
        scheduleAutoDismissNotice(2_500L)
    }

    fun setMonitor(enabled: Boolean) {
        _state.update { it.copy(monitor = enabled) }
    }

    fun setAppearance(appearance: Appearance) {
        _state.update { it.copy(appearance = appearance) }
        persist { it.copy(appearance = appearance) }
    }

    fun setAccent(accent: AccentChoice) {
        _state.update { it.copy(accent = accent) }
        persist { it.copy(accent = accent) }
    }

    fun setStreamDisplay(prefs: StreamDisplayPrefs) {
        _state.update { it.copy(streamDisplay = prefs) }
        persist { it.copy(streamDisplay = prefs) }
    }

    fun connectSelected() {
        val profile = selectedProfile() ?: return
        viewModelScope.launch { connect(profile) }
    }

    fun selectHost(hostId: String) {
        val same = hostId == _state.value.selectedHostId
        val online = _state.value.connection == ConnectionState.Online
        persist { it.copy(selectedHostId = hostId) }
        val selectedProject = _state.value.profiles.find { it.hostId == hostId }?.selectedProjectId
        _state.update {
            it.copy(
                selectedHostId = hostId,
                selectedProjectId = selectedProject,
                route = if (same) it.route else Route.Folders,
                conversations = if (same) it.conversations else emptyList(),
            )
        }
        if (!(same && online)) {
            connectSelected()
        }
    }

    fun pairFromRaw(raw: String, replaceExistingMobile: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(pairing = true, error = null) }
            runCatching {
                val invitation = PairingInvitationParser.parse(raw)
                redeemOrMerge(invitation, replaceExistingMobile = replaceExistingMobile)
            }.onFailure { err ->
                if (err is MobileDeviceConflictException) {
                    pendingConflictRetryAction = {
                        pairFromRaw(raw, replaceExistingMobile = true)
                    }
                    _state.update {
                        it.copy(
                            pairing = false,
                            pendingConflictDeviceName = err.existingDeviceName,
                            error = null,
                        )
                    }
                    return@launch
                }
                fail(err)
            }
            _state.update { it.copy(pairing = false) }
        }
    }

    fun pairManual(origin: String, token: String, replaceExistingMobile: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(pairing = true, error = null) }
            runCatching {
                val rawToken = token.trim()
                if (rawToken.startsWith("vibex-pairing:") || rawToken.startsWith("{")) {
                    redeemOrMerge(
                        PairingInvitationParser.parse(rawToken),
                        replaceExistingMobile = replaceExistingMobile,
                    )
                } else {
                    val candidates = LinkedHashSet<String>()
                    origin.trim().takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
                    val knownOrigins = _state.value.profiles.mapNotNull { it.lastSuccessOrigin }
                    val codeRendezvous = withContext(Dispatchers.IO) {
                        runCatching {
                            PairingClient.resolvePairingCodeRendezvous(rawToken, knownOrigins = knownOrigins)
                        }.getOrDefault(emptyList())
                    }
                    candidates.addAll(codeRendezvous)
                    _state.value.discoveredHosts.forEach { candidates.add(it.origin) }
                    _state.value.activeOrigin?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                    knownOrigins.forEach { candidates.add(it) }
                    if (candidates.isEmpty()) {
                        val autoFound = withContext(Dispatchers.IO) {
                            runCatching { PairingClient.discoverLanHosts() }.getOrDefault(emptyList())
                        }
                        autoFound.forEach { candidates.add(it.origin) }
                    }
                    val reachability = candidates.mapNotNull { url ->
                        runCatching { ReachabilityTarget(HostOrigin.parse(url), "manual") }.getOrNull()
                    }
                    if (reachability.isEmpty()) {
                        throw IllegalStateException("未发现在线电脑，请先确认电脑端在线")
                    }
                    val invitation = dev.vibex.companion.PairingInvitation(
                        hostId = null,
                        preset = "companion",
                        expiresAt = null,
                        pairingId = null,
                        pairingToken = rawToken.uppercase(),
                        reachability = reachability,
                    )
                    redeemOrMerge(invitation, replaceExistingMobile = replaceExistingMobile)
                }
            }.onFailure { err ->
                if (err is MobileDeviceConflictException) {
                    pendingConflictRetryAction = {
                        pairManual(origin, token, replaceExistingMobile = true)
                    }
                    _state.update {
                        it.copy(
                            pairing = false,
                            pendingConflictDeviceName = err.existingDeviceName,
                            error = null,
                        )
                    }
                    return@launch
                }
                fail(err)
            }
            _state.update { it.copy(pairing = false) }
        }
    }

    fun forgetSelected() {
        val profile = selectedProfile() ?: return
        viewModelScope.launch {
            val token = credentials.get(profile.hostId)
            val origin = profile.lastSuccessOrigin ?: profile.reachability.firstOrNull()?.origin
            if (!token.isNullOrBlank() && !origin.isNullOrBlank()) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        api.revoke(HostOrigin.parse(origin), token, profile.deviceId)
                    }
                }
            }
            credentials.remove(profile.hostId)
            val remaining = _state.value.profiles.filterNot { it.hostId == profile.hostId }
            persist { it.copy(selectedHostId = remaining.firstOrNull()?.hostId, profiles = remaining) }
            if (_state.value.accountSignedIn) {
                runCatching {
                    accountRepo.deleteSavedHost(
                        profileId = profile.deviceId.ifBlank { profile.hostId },
                        hostId = profile.hostId,
                        origin = origin.orEmpty(),
                    )
                }
            }
            socket.close()
            _state.update {
                it.copy(
                    profiles = remaining,
                    selectedHostId = remaining.firstOrNull()?.hostId,
                    route = if (remaining.isEmpty()) Route.Conversations else Route.Folders,
                    conversations = emptyList(),
                    timeline = null,
                    inbox = emptyList(),
                    connection = if (remaining.isEmpty()) ConnectionState.Offline else ConnectionState.Connecting,
                )
            }
            if (remaining.isNotEmpty()) connectSelected()
        }
    }

    fun openNewChatLanding() {
        userRequestedNewChatLanding = true
        timelinePollJob?.cancel()
        _state.update { it.copy(route = Route.Conversations, timeline = null) }
    }

    private var activeInlineConversationId: String? = null

    private fun currentObservedConversationId(): String? =
        (_state.value.route as? Route.Timeline)?.conversationId ?: activeInlineConversationId

    fun openConversation(id: String) {
        activeInlineConversationId = null
        userRequestedNewChatLanding = false
        hasAutoOpenedInitialConversation = true
        _state.update { it.copy(route = Route.Timeline(id), timeline = sessions[id]?.snapshot()) }
        viewModelScope.launch { attachConversation(id) }
        startTimelinePoller(id)
    }

    fun openInlineConversation(id: String) {
        activeInlineConversationId = id
        _state.update { it.copy(timeline = sessions[id]?.snapshot()) }
        viewModelScope.launch { attachConversation(id) }
        startTimelinePoller(id)
    }

    fun closeInlineConversation() {
        val wasInline = activeInlineConversationId
        activeInlineConversationId = null
        if ((_state.value.route as? Route.Timeline)?.conversationId == null && wasInline != null) {
            timelinePollJob?.cancel()
            _state.update { it.copy(timeline = null) }
        }
    }

    private fun startTimelinePoller(id: String) {
        timelinePollJob?.cancel()
        timelinePollJob = viewModelScope.launch {
            delay(800)
            while (isActive && currentObservedConversationId() == id) {
                val session = sessions[id]
                val fast = session?.inFlightTurnId != null || System.currentTimeMillis() < boostPollingUntil
                runCatching { syncConversationTimeline(id, forceRows = fast) }
                if (fast) {
                    delay(900)
                } else {
                    for (step in 0 until 8) {
                        if (!isActive || currentObservedConversationId() != id) break
                        if (sessions[id]?.inFlightTurnId != null || System.currentTimeMillis() < boostPollingUntil) break
                        delay(450)
                    }
                }
            }
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            runCatching { loadConversations() }.onFailure { fail(it) }
        }
    }

    fun createConversation(
        workspaceId: String,
        agentId: String,
        title: String,
        prompt: String = "",
        modeId: String? = null,
        config: Map<String, String> = emptyMap(),
    ) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val resolvedWorkspaceId = workspaceId.ifBlank { currentCollaborationWorkspaceId() }
                if (resolvedWorkspaceId.isBlank()) error("当前没有可用项目工作区，请先在桌面端导入项目")
                val resolvedAgentId = agentId.ifBlank {
                    _state.value.catalog?.agents?.firstOrNull { it.usable }?.id
                        ?: _state.value.catalog?.agents?.firstOrNull()?.id
                        ?: "claude_code"
                }
                val (origin, token) = live()
                val created = withContext(Dispatchers.IO) {
                    api.createConversation(
                        origin,
                        token,
                        resolvedWorkspaceId,
                        resolvedAgentId,
                        title.ifBlank { null },
                        prompt.ifBlank { null },
                    )
                }
                locallyCreatedConversationIds.add(created.id)
                knownConversationIds.add(created.id)
                val agent = _state.value.catalog?.agents?.find { it.id == resolvedAgentId }
                withContext(Dispatchers.IO) {
                    runCatching { api.ensureSessionControls(origin, token, created.id) }
                    if (!modeId.isNullOrBlank()) {
                        runCatching { api.setSessionMode(origin, token, created.id, modeId) }
                    }
                    config.forEach { (key, value) ->
                        if (key.isNotBlank() && value.isNotBlank()) {
                            runCatching { api.setSessionConfigOption(origin, token, created.id, key, value) }
                        }
                    }
                }
                val boundAgent = created.agentId.ifBlank { resolvedAgentId }
                val boundWorkspace = created.workspaceId.ifBlank { resolvedWorkspaceId }
                val session = sessions.getOrPut(created.id) {
                    TimelineSession().also { createdSession ->
                        createdSession.conversationId = created.id
                        createdSession.title = title.ifBlank { created.title }
                        createdSession.agentId = boundAgent
                        createdSession.workspaceId = boundWorkspace
                    }
                }.apply {
                    this.agentId = boundAgent
                    this.workspaceId = boundWorkspace
                    if (title.isNotBlank()) this.title = title
                }
                val bound = _state.value.catalog?.agents?.find { it.id == boundAgent } ?: agent
                applyCreateTimeSessionConfig(
                    session,
                    modes = bound?.sessionModes.orEmpty(),
                    currentModeId = modeId.orEmpty().ifBlank { bound?.currentModeId.orEmpty() },
                    options = bound?.sessionConfig.orEmpty(),
                    selected = config,
                )
                val summary = created.copy(
                    workspaceId = boundWorkspace,
                    title = title.ifBlank { created.title },
                    agentId = boundAgent,
                )
                _state.update { state ->
                    state.copy(
                        conversations = listOf(summary) + state.conversations.filterNot { it.id == summary.id },
                    )
                }
                openConversation(created.id)
                if (prompt.isNotBlank()) {
                    send(created.id, prompt)
                }
                viewModelScope.launch { runCatching { loadConversations() } }
            }.onFailure { fail(it) }
        }
    }

    fun pinConversation(conversationId: String, pinned: Boolean) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.setConversationPinned(origin, token, conversationId, pinned) }
                loadConversations()
            }.onFailure { fail(it) }
        }
    }

    fun archiveConversation(conversationId: String) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.archiveConversation(origin, token, conversationId) }
                loadConversations()
                if ((_state.value.route as? Route.Timeline)?.conversationId == conversationId) {
                    _state.update { it.copy(route = Route.Conversations, timeline = null) }
                }
            }.onFailure { fail(it) }
        }
    }

    fun deleteConversation(conversationId: String) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.deleteConversation(origin, token, conversationId) }
                loadConversations()
                if ((_state.value.route as? Route.Timeline)?.conversationId == conversationId) {
                    _state.update { it.copy(route = Route.Conversations, timeline = null) }
                }
            }.onFailure { fail(it) }
        }
    }

    fun setSessionMode(conversationId: String, modeId: String) {
        if (!_state.value.connection.allowsWrites || modeId.isBlank()) return
        sessions[conversationId]?.let { session ->
            session.currentModeId = modeId
            publishTimeline(session)
        }
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.setSessionMode(origin, token, conversationId, modeId) }
            }.onFailure { fail(it) }
        }
    }

    fun setSessionConfig(conversationId: String, key: String, value: String) {
        if (!_state.value.connection.allowsWrites || key.isBlank()) return
        sessions[conversationId]?.let { session ->
            val index = session.sessionConfig.indexOfFirst { it.key == key }
            if (index >= 0) session.sessionConfig[index] = session.sessionConfig[index].copy(value = value)
            publishTimeline(session)
        }
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.setSessionConfigOption(origin, token, conversationId, key, value) }
            }.onFailure { fail(it) }
        }
    }

    fun compact(conversationId: String) {
        send(conversationId, "/compact")
    }

    fun renameConversation(conversationId: String, title: String) {
        if (!_state.value.connection.allowsWrites || title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.renameConversation(origin, token, conversationId, title) }
                loadConversations()
                sessions[conversationId]?.title = title
                sessions[conversationId]?.let(::publishTimeline)
            }.onFailure { fail(it) }
        }
    }

    fun setConversationStatus(conversationId: String, status: String) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.setConversationStatus(origin, token, conversationId, status) }
                loadConversations()
            }.onFailure { fail(it) }
        }
    }

    fun dismissSessionNotice(conversationId: String, noticeId: String) {
        sessions[conversationId]?.notices?.removeAll { it.id == noticeId }
        sessions[conversationId]?.let(::publishTimeline)
    }

    fun createWorkspace(projectId: String, name: String, branch: String?, onCreated: (String) -> Unit) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                val created = withContext(Dispatchers.IO) {
                    api.createWorkspace(origin, token, projectId, name, branch)
                }
                fetchCatalog()
                onCreated(created.id)
            }.onFailure { fail(it) }
        }
    }

    fun send(conversationId: String, text: String) {
        if (!_state.value.connection.allowsWrites || text.isBlank()) return
        val summary = selectedConversation(conversationId)
        val session = sessions.getOrPut(conversationId) {
            TimelineSession().also {
                it.conversationId = conversationId
                it.title = summary?.title.orEmpty()
                it.agentId = summary?.agentId.orEmpty()
                it.workspaceId = summary?.workspaceId.orEmpty()
            }
        }
        if (session.agentId.isBlank()) session.agentId = summary?.agentId.orEmpty()
        if (session.workspaceId.isBlank()) session.workspaceId = summary?.workspaceId.orEmpty()
        viewModelScope.launch {
            runCatching {
                val agentId = session.agentId.ifBlank { summary?.agentId.orEmpty() }
                    .ifBlank {
                        _state.value.catalog?.agents?.firstOrNull { it.usable }?.id
                            ?: _state.value.catalog?.agents?.firstOrNull()?.id
                            ?: "claude_code"
                    }
                session.agentId = agentId
                val workspaceId = session.workspaceId.ifBlank { summary?.workspaceId.orEmpty() }
                    .ifBlank { currentCollaborationWorkspaceId() }
                if (workspaceId.isBlank()) error("会话还没有工作区")
                session.workspaceId = workspaceId
                boostPollingUntil = System.currentTimeMillis() + 30_000L
                val (origin, token) = live()
                val localId = "local:${System.currentTimeMillis()}"
                if (session.inFlightTurnId == null) session.inFlightTurnId = "local"
                session.rows += dev.vibex.companion.TimelineRow(
                    id = localId,
                    kind = "user",
                    title = "",
                    body = text,
                    tone = dev.vibex.companion.TimelineTone.Quiet,
                )
                publishTimeline(session)
                withContext(Dispatchers.IO) {
                    api.submitInput(origin, token, conversationId, agentId, workspaceId, text)
                }
                syncConversationTimeline(conversationId)
                if (session.rows.any { it.kind == "user" && it.id != localId && it.body == text }) {
                    session.rows.removeAll { it.id == localId }
                }
                publishTimeline(session)
            }.onFailure { fail(it) }
        }
    }

    fun cancelQueuedInput(conversationId: String, inputId: String, revision: Long) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.cancelConversationInput(origin, token, conversationId, inputId, revision) }
                sessions[conversationId]?.queued?.removeAll { it.id == inputId }
                sessions[conversationId]?.let(::publishTimeline)
            }.onFailure { fail(it) }
        }
    }

    fun preferWorkspace(conversationId: String, workspaceId: String) {
        if (workspaceId.isBlank()) return
        sessions[conversationId]?.workspaceId = workspaceId
        sessions[conversationId]?.let(::publishTimeline)
    }

    fun cancel(conversationId: String) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.cancelTurn(origin, token, conversationId) }
            }.onFailure { fail(it) }
        }
    }

    fun retryInterrupted(conversationId: String) {
        val session = sessions[conversationId] ?: return
        send(conversationId, "继续")
        session.agentId
    }

    fun approve(item: PendingItem, optionId: String) {
        if (!_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                boostPollingUntil = System.currentTimeMillis() + 30_000L
                val (origin, token) = live()
                withContext(Dispatchers.IO) {
                    when (item.kind) {
                        dev.vibex.companion.PendingKind.Permission ->
                            api.respondPermission(origin, token, item.conversationId, item.id, optionId)
                        else -> api.respondQuestion(origin, token, item.conversationId, item.id, optionId)
                    }
                }
                syncConversationTimeline(item.conversationId)
            }.onFailure { fail(it) }
        }
    }

    fun respondPermission(
        conversationId: String,
        permissionId: String,
        optionId: String?,
        persist: Boolean = false,
        onDone: () -> Unit = {},
    ) {
        if (!_state.value.connection.allowsWrites) {
            onDone()
            return
        }
        viewModelScope.launch {
            runCatching {
                boostPollingUntil = System.currentTimeMillis() + 30_000L
                val (origin, token) = live()
                withContext(Dispatchers.IO) {
                    api.respondPermission(origin, token, conversationId, permissionId, optionId, persist)
                }
                sessions[conversationId]?.let { session ->
                    if (session.permissionPrompt?.id == permissionId) {
                        session.permissionPrompt = null
                    }
                    session.rows.replaceAll { row ->
                        if (row.pendingKind == dev.vibex.companion.PendingKind.Permission && row.pendingId == permissionId) {
                            row.copy(pendingKind = null, tone = dev.vibex.companion.TimelineTone.Quiet)
                        } else {
                            row
                        }
                    }
                    publishTimeline(session)
                }
                syncConversationTimeline(conversationId)
            }.onFailure { fail(it) }
            onDone()
        }
    }

    fun respondQuestionContent(
        conversationId: String,
        questionId: String,
        content: dev.vibex.remote.v1.JsonValue?,
        onDone: () -> Unit = {},
    ) {
        if (!_state.value.connection.allowsWrites) {
            onDone()
            return
        }
        viewModelScope.launch {
            runCatching {
                boostPollingUntil = System.currentTimeMillis() + 30_000L
                val (origin, token) = live()
                withContext(Dispatchers.IO) {
                    api.respondQuestion(origin, token, conversationId, questionId, content)
                }
                sessions[conversationId]?.let { session ->
                    if (session.pendingQuestion?.id == questionId) {
                        session.pendingQuestion = null
                    }
                    session.rows.replaceAll { row ->
                        if (row.pendingKind == dev.vibex.companion.PendingKind.Question && row.pendingId == questionId) {
                            row.copy(pendingKind = null, tone = dev.vibex.companion.TimelineTone.Quiet)
                        } else {
                            row
                        }
                    }
                    publishTimeline(session)
                }
                syncConversationTimeline(conversationId)
            }.onFailure { fail(it) }
            onDone()
        }
    }

    fun fetchWorkspaceTree(
        workspaceId: String,
        path: String = "",
        onResult: (Result<dev.vibex.companion.WorkspaceTree>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.workspaceTree(origin, token, workspaceId, path) }
            }
            onResult(result)
        }
    }

    fun fetchWorkspaceFile(
        workspaceId: String,
        path: String,
        onResult: (Result<dev.vibex.companion.WorkspaceFile>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.workspaceFile(origin, token, workspaceId, path) }
            }
            onResult(result)
        }
    }

    fun fetchWorkspaceDiff(
        workspaceId: String,
        conversationId: String = "",
        onResult: (Result<List<dev.vibex.companion.FileDiff>>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.workspaceDiff(origin, token, workspaceId, conversationId) }
            }
            onResult(result)
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            runCatching { fetchCatalog() }.onFailure { fail(it) }
        }
    }

    fun loadSlashCommands(conversationId: String, agentId: String, workspaceId: String) {
        if (agentId.isBlank()) return
        viewModelScope.launch {
            val commands = runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.listSlashCommands(origin, token, agentId, workspaceId) }
            }.getOrDefault(emptyList())
            sessions[conversationId]?.let { session ->
                session.skillCommands.clear()
                session.skillCommands += commands
                publishTimeline(session)
            }
        }
    }

    fun loadWorkspaceEntries(workspaceId: String, onLoaded: (List<dev.vibex.companion.WorkspaceEntry>) -> Unit) {
        if (workspaceId.isBlank()) {
            onLoaded(emptyList())
            return
        }
        viewModelScope.launch {
            val entries = runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.listWorkspaceEntries(origin, token, workspaceId) }
            }.getOrDefault(emptyList())
            onLoaded(entries)
        }
    }

    fun loadArtifactPaths(conversationId: String, onLoaded: (List<String>) -> Unit) {
        viewModelScope.launch {
            val paths = runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.listArtifacts(origin, token, conversationId) }
            }.getOrDefault(emptyList())
            onLoaded(paths)
        }
    }

    fun loadProjectSessionCount(projectId: String, onLoaded: (Int?) -> Unit) {
        viewModelScope.launch {
            val count = runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.listRecent(origin, token, _state.value.sinceDays, projectId).size }
            }.getOrNull()
            onLoaded(count)
        }
    }

    fun loadWorkspaceDiffs(workspaceId: String, conversationId: String) {
        viewModelScope.launch {
            _state.update { it.copy(diffsLoading = true) }
            val diffs = runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.listWorkspaceDiffs(origin, token, workspaceId, conversationId) }
            }.getOrDefault(emptyList())
            _state.update { it.copy(workspaceDiffs = diffs, diffsLoading = false) }
        }
    }

    private fun currentCollaborationWorkspaceId(): String {
        val state = _state.value
        val projectId = state.selectedProjectId ?: state.catalog?.projects?.firstOrNull()?.id
        return state.catalog?.workspaces.orEmpty()
            .firstOrNull { projectId == null || it.projectId == projectId }?.id
            ?: state.conversations.firstOrNull()?.workspaceId.orEmpty()
    }

    fun loadCollaboration() {
        viewModelScope.launch {
            _state.update { it.copy(collaborationLoading = true) }
            runCatching {
                val (origin, token) = live()
                val wsId = currentCollaborationWorkspaceId()
                val threads = withContext(Dispatchers.IO) { api.listCollaborationThreads(origin, token, wsId) }
                _state.update { it.copy(collaborationThreads = threads, collaborationLoading = false) }
                val activeThread = _state.value.selectedThread
                if (activeThread != null) {
                    val messages = withContext(Dispatchers.IO) { api.listCollaborationMessages(origin, token, activeThread.id) }
                    _state.update { it.copy(collaborationMessages = messages) }
                }
            }.onFailure {
                _state.update { s -> s.copy(collaborationLoading = false) }
            }
        }
    }

    fun openCollaborationThread(thread: CollaborationThreadSummary) {
        _state.update { it.copy(selectedThread = thread, collaborationMessages = emptyList()) }
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                val messages = withContext(Dispatchers.IO) { api.listCollaborationMessages(origin, token, thread.id) }
                _state.update { it.copy(collaborationMessages = messages) }
            }
        }
    }

    fun closeCollaborationThread() {
        _state.update { it.copy(selectedThread = null, collaborationMessages = emptyList()) }
    }

    fun sendCollaborationMessage(content: String) {
        val thread = _state.value.selectedThread ?: return
        if (content.isBlank() || !_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                withContext(Dispatchers.IO) { api.submitCollaborationMessage(origin, token, thread.id, content) }
                val messages = withContext(Dispatchers.IO) { api.listCollaborationMessages(origin, token, thread.id) }
                _state.update { it.copy(collaborationMessages = messages) }
            }.onFailure { fail(it) }
        }
    }

    fun createCollaborationGroup(name: String) {
        if (name.isBlank() || !_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                val wsId = currentCollaborationWorkspaceId()
                withContext(Dispatchers.IO) { api.createCollaborationGroup(origin, token, wsId, name) }
                loadCollaboration()
            }.onFailure { fail(it) }
        }
    }

    fun createCollaborationBot(name: String, agentId: String, description: String) {
        if (name.isBlank() || !_state.value.connection.allowsWrites) return
        viewModelScope.launch {
            runCatching {
                val (origin, token) = live()
                val wsId = currentCollaborationWorkspaceId()
                withContext(Dispatchers.IO) {
                    api.createCollaborationBot(origin, token, wsId, name, agentId.ifBlank { "claude_code" }, description)
                }
                loadCollaboration()
            }.onFailure { fail(it) }
        }
    }

    fun loadExtensionsCatalog() {
        viewModelScope.launch {
            _state.update { it.copy(extensionsLoading = true) }
            runCatching {
                val (origin, token) = live()
                val wsId = currentCollaborationWorkspaceId()
                val skills = withContext(Dispatchers.IO) { api.listSkillsCatalog(origin, token) }
                val mcps = withContext(Dispatchers.IO) { api.listMcpCatalog(origin, token) }
                val plugins = withContext(Dispatchers.IO) { api.listPluginCatalog(origin, token) }
                val roles = withContext(Dispatchers.IO) { api.listSubagentRoles(origin, token, wsId) }
                _state.update {
                    it.copy(
                        skillsCatalog = skills,
                        mcpCatalog = mcps,
                        pluginsCatalog = plugins,
                        subagentRoles = roles,
                        extensionsLoading = false,
                    )
                }
            }.onFailure {
                _state.update { s -> s.copy(extensionsLoading = false) }
            }
        }
    }

    private suspend fun fetchCatalog() {
        _state.update { it.copy(catalogLoading = true) }
        try {
            val (origin, token) = live()
            val catalog = withContext(Dispatchers.IO) { api.catalog(origin, token) }
            val selected = _state.value.selectedProjectId
            val stillValid = selected != null && catalog.projects.any { it.id == selected }
            val resolvedProject = if (stillValid) selected else catalog.projects.firstOrNull()?.id
            _state.update {
                it.copy(
                    catalog = catalog,
                    catalogLoading = false,
                    selectedProjectId = resolvedProject,
                    error = null,
                )
            }
            if (resolvedProject != null) {
                loadConversations()
            }
        } catch (error: Throwable) {
            _state.update { it.copy(catalogLoading = false) }
            throw error
        }
    }

    private fun handleForcedOfflineByRevocation(hostId: String? = _state.value.selectedHostId) {
        val targetId = hostId ?: selectedProfile()?.hostId
        if (!targetId.isNullOrBlank()) {
            credentials.remove(targetId)
        }
        val remaining = if (targetId.isNullOrBlank()) emptyList() else _state.value.profiles.filterNot { it.hostId == targetId }
        persist { it.copy(selectedHostId = remaining.firstOrNull()?.hostId, profiles = remaining) }
        socket.close()
        pingJob?.cancel()
        _state.update {
            it.copy(
                profiles = remaining,
                selectedHostId = remaining.firstOrNull()?.hostId,
                activeOrigin = null,
                connection = ConnectionState.Offline,
                realtimeRttMs = null,
                connectionFailureReason = "已被其他手机接管下线",
                conversations = emptyList(),
                timeline = null,
                inbox = emptyList(),
                pairing = false,
                error = "当前电脑已切换至其他手机远程连接，本机已强制下线并清除凭据，下次需重新连接",
            )
        }
    }

    private suspend fun redeemOrMerge(
        invitation: dev.vibex.companion.PairingInvitation,
        replaceExistingMobile: Boolean = false,
    ) {
        val result = withContext(Dispatchers.IO) {
            pairing.redeemInvitation(
                invitation = invitation,
                deviceName = Build.MODEL,
                replaceExistingMobile = replaceExistingMobile,
            )
        }
        credentials.put(result.hostId, result.session.credential.access_token)
        val displayHost = result.session.origin.value
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore(":")
        val profile = HostProfile(
            hostId = result.hostId,
            name = "VibeX ($displayHost)",
            deviceId = result.session.credential.device_id,
            reachability = invitation.reachability.map { StoredReachability(it.origin.value, it.kind) }
                .ifEmpty { listOf(StoredReachability(result.session.origin.value, "paired")) },
            lastSuccessOrigin = result.session.origin.value,
            lastSyncAt = System.currentTimeMillis(),
            selectedProjectId = null,
        )
        upsertProfile(profile, selected = true)
        setAccountLoginRequested(false)
        _state.update { it.copy(notice = null, route = Route.Conversations, triedOrigins = result.tried) }
        connect(profile)
    }

    private fun inferTransportPath(originUrl: String, profile: HostProfile): String {
        val lower = originUrl.lowercase()
        if (lower.contains("://127.0.0.1") || lower.contains("://localhost")) return "USB / 本机直连"
        if (Regex("""^https?://100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.""").containsMatchIn(lower)) {
            return "Tailscale P2P"
        }
        val kind = profile.reachability.find { it.origin == originUrl }?.kind?.lowercase()
        if (kind == "webrtc") return "WebRTC P2P"
        if (lower.contains("/t/")) return "VibeX 云服务 · Beta"
        if (kind == "published" || kind == "tunnel" || lower.startsWith("https://")) return "云服务 · Beta"
        val isPrivateLan = Regex("""^http://(192\.168\.|10\.|172\.(1[6-9]|2\d|3[0-1])\.)""").containsMatchIn(lower)
        if (!isPrivateLan) return "云服务 · Beta"
        return "LAN 局域网直连"
    }

    private suspend fun connect(profile: HostProfile, allowCloudSwitch: Boolean = false) {
        if (
            _state.value.connection == ConnectionState.Online &&
                _state.value.selectedHostId == profile.hostId &&
                !_state.value.activeOrigin.isNullOrBlank()
        ) {
            loadCatalogSilent()
            return
        }
        _state.update {
            it.copy(
                connection = if (it.connection == ConnectionState.Recovering) ConnectionState.Recovering else ConnectionState.Connecting,
                error = null,
                triedOrigins = emptyList(),
            )
        }
        val token = credentials.get(profile.hostId)
        if (token.isNullOrBlank()) {
            handleForcedOfflineByRevocation(profile.hostId)
            return
        }
        val targets = profile.reachability.map {
            if (it.origin.contains("/t/")) {
                PairingClient.rememberRelayBase(it.origin)
            }
            ReachabilityTarget(HostOrigin.parse(it.origin), it.kind)
        }.toMutableList()
        val order = OriginOrder.probeOrder(targets, profile.lastSuccessOrigin).toMutableList()
        val tried = mutableListOf<String>()
        var lastError: Exception? = null
        val connectStartMs = System.currentTimeMillis()

        fun isLanOriginUrl(url: String): Boolean {
            val lower = url.lowercase()
            if (lower.contains("/t/")) return false
            return lower.contains("://127.0.0.1") ||
                lower.contains("://10.0.2.2") ||
                lower.contains("://localhost") ||
                Regex("""^http://(192\.168\.|10\.|172\.(1[6-9]|2\d|3[0-1])\.)""").containsMatchIn(lower)
        }

        val wasLanMode = !allowCloudSwitch &&
            profile.lastSuccessOrigin != null &&
            isLanOriginUrl(profile.lastSuccessOrigin)

        suspend fun tryConnectOrigin(origin: HostOrigin): Boolean {
            tried += origin.value
            val VerifyHost = withContext(Dispatchers.IO) {
                PairingClient.probeHostHealth(
                    originUrl = origin.value,
                    timeoutMs = if (isLanOriginUrl(origin.value)) 350 else 3500,
                    expectedHostId = profile.hostId.takeUnless { it.startsWith("host:") },
                )
            }
            if (VerifyHost == null) {
                throw IllegalStateException("电脑端暂未响应")
            }
            val (caps, serverReachability) = withContext(Dispatchers.IO) {
                api.capabilitiesWithReachability(origin, token)
            }
            val totalDuration = (System.currentTimeMillis() - connectStartMs).coerceAtLeast(1L)
            if (caps.protocol_version.substringBefore('.') != "1") {
                _state.update {
                    it.copy(
                        connection = ConnectionState.Incompatible,
                        triedOrigins = tried,
                        realtimeRttMs = null,
                        connectionFailureReason = "协议版本不兼容 (${caps.protocol_version})",
                    )
                }
                return true
            }
            val warmRttMs = withContext(Dispatchers.IO) {
                val t0 = System.nanoTime()
                val ok = runCatching { api.health(origin) }.isSuccess
                if (ok) ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L) else null
            }
            val syncedTargets = serverReachability.map {
                if (it.origin.value.contains("/t/")) {
                    PairingClient.rememberRelayBase(it.origin.value)
                }
                StoredReachability(it.origin.value, it.kind)
            }
            val selfKind = if (origin.value.startsWith("https://") || origin.value.contains("/t/")) "published" else "lan"
            if (selfKind == "lan") {
                declinedCloudFallbackHostIds.remove(profile.hostId)
            }
            val mergedReachability = (syncedTargets + profile.reachability + StoredReachability(origin.value, selfKind))
                .filterNot { it.origin.contains(":17890") }
                .distinctBy { it.origin }
            val updatedProfile = profile.copy(
                reachability = mergedReachability,
                lastSuccessOrigin = origin.value,
                lastSyncAt = System.currentTimeMillis(),
            )
            upsertProfile(updatedProfile, true)
            _state.update {
                it.copy(
                    connection = ConnectionState.Online,
                    activeOrigin = origin.value,
                    triedOrigins = tried,
                    hostVersion = caps.server_version,
                    hostProtocol = caps.protocol_version,
                    hostCapabilities = caps.capabilities,
                    lastConnectDurationMs = totalDuration,
                    realtimeRttMs = warmRttMs,
                    transportPath = inferTransportPath(origin.value, updatedProfile),
                    connectionFailureReason = null,
                    pendingCloudFallbackOrigin = null,
                    pendingCloudFallbackHostName = null,
                    desktopConnect = null,
                    error = null,
                )
            }
            bindSocket(origin.value, token)
            loadCatalogSilent()
            return true
        }

        // Happy Eyeballs Step 1: Give LAN candidates a fast priority window (350ms probe)
        val (lanCandidates, remoteCandidates) = order.partition { isLanOriginUrl(it.value) }
        for (origin in lanCandidates) {
            try {
                if (tryConnectOrigin(origin)) return
            } catch (error: AuthRequiredException) {
                handleForcedOfflineByRevocation(profile.hostId)
                return
            } catch (error: Exception) {
                lastError = error
            }
        }

        // Step 2: If LAN IP changed via DHCP on the same Wi-Fi, try LAN discovery before considering Cloud Service
        val knownOriginStrings = profile.reachability.map { it.origin }
        val discovered = withContext(Dispatchers.IO) {
            runCatching { PairingClient.discoverLanHosts() }
                .getOrDefault(emptyList())
        }
        val matchedHost = discovered.find { it.hostId == profile.hostId } ?: discovered.firstOrNull {
            profile.hostId.startsWith("host:")
        }
        if (matchedHost != null && !tried.contains(matchedHost.origin) && isLanOriginUrl(matchedHost.origin)) {
            runCatching { HostOrigin.parse(matchedHost.origin) }.getOrNull()?.let { discoveredOrigin ->
                try {
                    if (tryConnectOrigin(discoveredOrigin)) return
                } catch (error: AuthRequiredException) {
                    handleForcedOfflineByRevocation(profile.hostId)
                    return
                } catch (error: Exception) {
                    lastError = error
                }
            }
        }

        // Step 3: Domestic Cloud Relay / Published origins
        val rendezvousOrigins = withContext(Dispatchers.IO) {
            runCatching { PairingClient.resolveHostRendezvous(profile.hostId, knownOrigins = knownOriginStrings) }
                .getOrDefault(emptyList())
        }
        val cleanRemoteCandidates = remoteCandidates.map { it.value }.filterNot { url ->
            val lower = url.lowercase()
            lower.contains("serveousercontent.com") ||
                lower.contains("serveo.net") ||
                lower.contains("lhr.life") ||
                lower.contains(":17890") ||
                lower.contains(":17891")
        }
        val combinedRemote = (rendezvousOrigins + cleanRemoteCandidates).distinct()

        // If previously connected via LAN direct, ask the user before switching to Cloud Service!
        if (wasLanMode) {
            for (rawOrigin in combinedRemote) {
                val reachable = withContext(Dispatchers.IO) {
                    PairingClient.probeHostHealth(
                        originUrl = rawOrigin,
                        timeoutMs = 2200,
                        expectedHostId = profile.hostId.takeUnless { it.startsWith("host:") },
                    )
                }
                if (reachable != null) {
                    val shouldPrompt = profile.hostId !in declinedCloudFallbackHostIds
                    _state.update {
                        it.copy(
                            connection = ConnectionState.Offline,
                            triedOrigins = tried,
                            realtimeRttMs = null,
                            connectionFailureReason = "局域网已断开",
                            pendingCloudFallbackOrigin = if (shouldPrompt) reachable.origin else it.pendingCloudFallbackOrigin,
                            pendingCloudFallbackHostName = if (shouldPrompt) profile.name else it.pendingCloudFallbackHostName,
                            error = null,
                        )
                    }
                    return
                }
            }
        } else {
            for (rawOrigin in combinedRemote) {
                if (tried.contains(rawOrigin)) continue
                val parsedOrigin = runCatching { HostOrigin.parse(rawOrigin) }.getOrNull() ?: continue
                try {
                    if (tryConnectOrigin(parsedOrigin)) return
                } catch (error: AuthRequiredException) {
                    handleForcedOfflineByRevocation(profile.hostId)
                    return
                } catch (error: Exception) {
                    lastError = error
                }
            }
        }

        val errMsg = lastError?.message.orEmpty()
        val failureReason = when {
            errMsg.contains("sleep", ignoreCase = true) -> "电脑已休眠"
            errMsg.contains("Network is unreachable", ignoreCase = true) ||
                errMsg.contains("EHOSTUNREACH", ignoreCase = true) -> "网络受限"
            else -> "电脑离线"
        }
        _state.update {
            it.copy(
                connection = if (failureReason == "电脑已休眠") ConnectionState.HostSleeping else ConnectionState.Offline,
                triedOrigins = tried,
                realtimeRttMs = null,
                connectionFailureReason = failureReason,
                error = null,
            )
        }
    }

    private fun bindSocket(origin: String, token: String) {
        socket.connect(
            origin = origin,
            token = token,
            onOpen = {
                viewModelScope.launch {
                    val openId = (_state.value.route as? Route.Timeline)?.conversationId
                    if (!openId.isNullOrBlank()) {
                        attachConversation(openId)
                    }
                }
            },
            onPongRttMs = { rttMs ->
                val normalizedRtt = if (origin.contains("/t/")) (rttMs / 2L).coerceAtLeast(1L) else rttMs
                _state.update { it.copy(realtimeRttMs = normalizedRtt) }
            },
            onEvent = { subscriptionId, event ->
                val conversationId = subscriptions[subscriptionId] ?: (_state.value.route as? Route.Timeline)?.conversationId ?: return@connect
                val session = sessions[conversationId] ?: return@connect
                synchronized(session) {
                    EventFold.apply(session, event)
                }
                if (event.kind == "turn_completed" || event.kind == "turn_failed") {
                    val key = "terminal:$conversationId:${event.sequence}"
                    if (notifiedEventKeys.add(key)) {
                        val project = resolveProjectName(session.workspaceId)
                        val titleText = session.title.ifBlank { "对话任务" }
                        if (event.kind == "turn_failed") {
                            SystemNotifier.notifyAlert(
                                getApplication(),
                                key,
                                "$project · 会话执行出错",
                                titleText,
                                conversationId,
                            )
                            presentAttentionToast(
                                CompanionAttentionToast(
                                    id = key,
                                    conversationId = conversationId,
                                    workspaceId = session.workspaceId,
                                    agentId = session.agentId,
                                    projectName = project,
                                    title = "$project：会话执行异常",
                                    subtitle = titleText,
                                    kind = AttentionToastKind.Failed(titleText),
                                ),
                            )
                            finishLiveActivity(conversationId, failedMessage = titleText)
                        } else {
                            SystemNotifier.notifyAlert(
                                getApplication(),
                                key,
                                "$project · 会话已完成",
                                titleText,
                                conversationId,
                            )
                            presentAttentionToast(
                                CompanionAttentionToast(
                                    id = key,
                                    conversationId = conversationId,
                                    workspaceId = session.workspaceId,
                                    agentId = session.agentId,
                                    projectName = project,
                                    title = "$project：会话已完成",
                                    subtitle = titleText,
                                    kind = AttentionToastKind.Completed,
                                ),
                            )
                            finishLiveActivity(conversationId, failedMessage = null)
                        }
                    }
                }
                publishTimeline(session)
            },
            onSnapshot = { subscriptionId, payload ->
                val conversationId = subscriptions[subscriptionId] ?: (_state.value.route as? Route.Timeline)?.conversationId ?: return@connect
                val session = sessions.getOrPut(conversationId) { TimelineSession().also { it.conversationId = conversationId } }
                synchronized(session) {
                    EventFold.applySnapshot(session, payload)
                }
                publishTimeline(session)
            },
            onClosed = { reason ->
                if (reason == "auth") {
                    viewModelScope.launch {
                        handleForcedOfflineByRevocation()
                    }
                } else if (_state.value.connection == ConnectionState.Online) {
                    _state.update { it.copy(connection = ConnectionState.Recovering) }
                    viewModelScope.launch {
                        delay(800)
                        if (_state.value.connection == ConnectionState.Recovering) {
                            connectSelected()
                        }
                    }
                }
            },
        )
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (isActive) {
                delay(2_500)
                socket.ping()
                runCatching { loadConversations(silent = true) }
                val refreshedResult = withContext(Dispatchers.IO) {
                    runCatching { api.capabilitiesWithReachability(HostOrigin.parse(origin), token) }
                }
                val failure = refreshedResult.exceptionOrNull()
                if (failure is AuthRequiredException) {
                    handleForcedOfflineByRevocation()
                    break
                }
                val refreshed = refreshedResult.getOrNull()
                if (refreshed != null) {
                    val synced = refreshed.second.map { StoredReachability(it.origin.value, it.kind) }
                    selectedProfile()?.let { currentProfile ->
                        val merged = (synced + currentProfile.reachability).distinctBy { it.origin }
                        if (merged != currentProfile.reachability) {
                            upsertProfile(currentProfile.copy(reachability = merged), true)
                        }
                    }
                }
            }
        }
    }

    private suspend fun attachConversation(id: String) {
        val summary = _state.value.conversations.find { it.id == id }
        val session = sessions.getOrPut(id) {
            TimelineSession().also {
                it.conversationId = id
                it.title = summary?.title.orEmpty()
                it.agentId = summary?.agentId.orEmpty()
                it.workspaceId = summary?.workspaceId.orEmpty()
            }
        }
        synchronized(session) {
            if (session.agentId.isBlank()) session.agentId = summary?.agentId.orEmpty()
            if (session.workspaceId.isBlank()) session.workspaceId = summary?.workspaceId.orEmpty()
        }
        if (synchronized(session) { session.rows.isEmpty() }) {
            val cached = withContext(Dispatchers.IO) { offline.load(id) }
            if (cached.second.isNotEmpty()) {
                synchronized(session) {
                    EventFold.applyAll(session, cached.second)
                }
                publishTimeline(session)
            }
        }
        if (!_state.value.connection.allowsWrites && _state.value.connection != ConnectionState.Online) {
            return
        }
        val after = synchronized(session) { session.lastSequence }
        val subscriptionId = socket.attach(id, after)
        subscriptions[subscriptionId] = id
        runCatching {
            val (origin, token) = live()
            syncConversationTimeline(id, forceRows = true)
            val needControls = synchronized(session) {
                session.sessionModes.isEmpty() && session.sessionConfig.isEmpty()
            }
            if (needControls) {
                val controls = withContext(Dispatchers.IO) {
                    api.ensureSessionControls(origin, token, id)
                }
                synchronized(session) {
                    EventFold.applySessionControls(session, controls)
                }
            }
            val skills = withContext(Dispatchers.IO) {
                val agId = synchronized(session) { session.agentId }.ifBlank { summary?.agentId.orEmpty() }
                val wsId = synchronized(session) { session.workspaceId }.ifBlank { summary?.workspaceId.orEmpty() }
                api.listSlashCommands(origin, token, agId, wsId)
            }
            synchronized(session) {
                session.skillCommands.clear()
                session.skillCommands += skills
            }
            publishTimeline(session)
        }
    }

    private suspend fun syncConversationTimeline(id: String, forceRows: Boolean = true) {
        val summary = _state.value.conversations.find { it.id == id }
        val session = sessions.getOrPut(id) {
            TimelineSession().also {
                it.conversationId = id
                it.title = summary?.title.orEmpty()
                it.agentId = summary?.agentId.orEmpty()
                it.workspaceId = summary?.workspaceId.orEmpty()
            }
        }
        val (afterSeq, wasEmpty) = synchronized(session) {
            if (session.agentId.isBlank()) session.agentId = summary?.agentId.orEmpty()
            if (session.workspaceId.isBlank()) session.workspaceId = summary?.workspaceId.orEmpty()
            session.lastSequence to session.rows.isEmpty()
        }
        val (origin, token) = live()
        val events = withContext(Dispatchers.IO) {
            runCatching { api.offlineEvents(origin, token, id, afterSeq) }.getOrDefault(emptyList())
        }
        if (events.isNotEmpty()) {
            val latestSeq = synchronized(session) {
                EventFold.applyAll(session, events)
                session.lastSequence
            }
            withContext(Dispatchers.IO) {
                offline.save(id, latestSeq, events)
            }
        }
        if (forceRows || wasEmpty || events.isNotEmpty()) {
            val rawRows = withContext(Dispatchers.IO) {
                api.timelineRows(origin, token, id)
            }
            if (rawRows.isNotEmpty()) {
                synchronized(session) {
                    EventFold.applyTimelineRows(session, rawRows)
                }
            }
        }
        if (forceRows || wasEmpty || events.isNotEmpty()) {
            publishTimeline(session)
        }
    }

    private suspend fun loadConversations(silent: Boolean = false) {
        if (!silent) {
            _state.update { it.copy(conversationsLoading = true) }
        }
        try {
            val (origin, token) = live()
            val listed = withContext(Dispatchers.IO) {
                api.listRecent(origin, token, _state.value.sinceDays, null)
            }
            val merged = listed.map { summary ->
                if (summary.agentId.isNotBlank()) summary
                else summary.copy(
                    agentId = sessions[summary.id]?.agentId.orEmpty().ifBlank {
                        _state.value.conversations.find { it.id == summary.id }?.agentId.orEmpty()
                    },
                )
            }
            _state.update { it.copy(conversations = merged, conversationsLoading = false, error = null) }

            if (!didSeedKnownConversations) {
                knownConversationIds.addAll(merged.map { it.id })
                didSeedKnownConversations = true
            } else {
                for (conv in merged) {
                    if (knownConversationIds.add(conv.id) && !locallyCreatedConversationIds.contains(conv.id)) {
                        val alertKey = "new_conv:${conv.id}"
                        if (notifiedEventKeys.add(alertKey)) {
                            val project = resolveProjectName(conv.workspaceId)
                            val titleText = conv.title.ifBlank { "新对话已创建，点击查看" }
                            SystemNotifier.notifyAlert(
                                getApplication(),
                                alertKey,
                                "$project · 新会话已创建",
                                titleText,
                                conv.id,
                            )
                            presentAttentionToast(
                                CompanionAttentionToast(
                                    id = alertKey,
                                    conversationId = conv.id,
                                    workspaceId = conv.workspaceId,
                                    agentId = conv.agentId,
                                    projectName = project,
                                    title = "$project：新会话已创建",
                                    subtitle = titleText,
                                    kind = AttentionToastKind.Created,
                                ),
                            )
                            if (_state.value.route == Route.Conversations) {
                                userRequestedNewChatLanding = false
                                openConversation(conv.id)
                            }
                        }
                    }
                }
            }

            if (
                !hasAutoOpenedInitialConversation &&
                !userRequestedNewChatLanding &&
                merged.isNotEmpty() &&
                _state.value.route == Route.Conversations
            ) {
                hasAutoOpenedInitialConversation = true
                openConversation(merged.first().id)
            }

            viewModelScope.launch(Dispatchers.IO) {
                var anyChanged = false
                merged.take(6).forEach { summary ->
                    runCatching {
                        val session = sessions.getOrPut(summary.id) {
                            TimelineSession().also {
                                it.conversationId = summary.id
                                it.title = summary.title
                                it.agentId = summary.agentId
                                it.workspaceId = summary.workspaceId
                            }
                        }
                        val isFirstSnapshot = !initializedConversations.contains(summary.id)
                        val previouslyRunning = wasRunningByConversation[summary.id] ?: false
                        val afterSeq = synchronized(session) { session.lastSequence }
                        if (afterSeq == 0L || summary.status.equals("inprogress", ignoreCase = true) || previouslyRunning) {
                            val events = api.offlineEvents(origin, token, summary.id, afterSeq)
                            if (events.isNotEmpty()) {
                                synchronized(session) {
                                    EventFold.applyAll(session, events)
                                }
                                anyChanged = true
                            }
                        }
                        syncSessionAttentionAndLiveActivity(session, isFirstSnapshot = isFirstSnapshot)
                    }
                }
                if (anyChanged) {
                    rebuildInbox()
                }
            }
        } catch (error: Throwable) {
            _state.update { it.copy(conversationsLoading = false) }
            throw error
        }
    }

    private fun publishTimeline(session: TimelineSession) {
        if (currentObservedConversationId() == session.conversationId) {
            val snap = synchronized(session) { session.snapshot() }
            _state.update { if (it.timeline == snap) it else it.copy(timeline = snap) }
        }
        val isFirstSnapshot = !initializedConversations.contains(session.conversationId)
        syncSessionAttentionAndLiveActivity(session, isFirstSnapshot = isFirstSnapshot)
        rebuildInbox()
    }

    private fun resolveProjectName(workspaceId: String): String {
        val cat = _state.value.catalog ?: return "VibeX"
        val ws = cat.workspaces.find { it.id == workspaceId } ?: return "VibeX"
        return cat.projects.find { it.id == ws.projectId }?.name?.ifBlank { "VibeX" } ?: "VibeX"
    }

    fun dismissAttentionToast() {
        _state.update { it.copy(activeAttentionToast = null, attentionActionError = null) }
        syncSystemIslandOverlay()
    }

    fun dismissLiveActivityOverlay() {
        liveActivityLingerJob?.cancel()
        liveActivityLingerJob = null
        activeLiveConversationId = null
        SystemNotifier.cancelLiveTaskProgress(getApplication())
        _state.update { it.copy(liveActivity = null) }
        syncSystemIslandOverlay()
    }

    private fun presentAttentionToast(toast: CompanionAttentionToast) {
        _state.update {
            it.copy(
                activeAttentionToast = toast,
                attentionActionError = null,
            )
        }
        syncSystemIslandOverlay()
    }

    fun respondToastPermission(conversationId: String, permissionId: String, optionId: String) {
        _state.update { it.copy(attentionActionBusy = true, attentionActionError = null) }
        syncSystemIslandOverlay()
        respondPermission(conversationId, permissionId, optionId) {
            _state.update {
                it.copy(
                    attentionActionBusy = false,
                    activeAttentionToast = null,
                    attentionActionError = null,
                )
            }
            syncSystemIslandOverlay()
        }
    }

    fun respondToastQuestion(
        conversationId: String,
        question: PendingQuestion,
        choice: QuestionChoice?,
        fallbackText: String?,
    ) {
        _state.update { it.copy(attentionActionBusy = true, attentionActionError = null) }
        syncSystemIslandOverlay()
        val forms = QuestionForm.questions(question)
        val payload = when {
            choice != null -> {
                val first = forms.firstOrNull()
                val answers = if (first != null) {
                    mapOf(first.id to QuestionAnswer(selected = listOf(choice.value), customText = ""))
                } else emptyMap()
                QuestionForm.content(question, forms, answers)
            }
            !fallbackText.isNullOrBlank() -> {
                val first = forms.firstOrNull()
                val answers = if (first != null) {
                    mapOf(first.id to QuestionAnswer(selected = emptyList(), customText = fallbackText))
                } else emptyMap()
                QuestionForm.content(question, forms, answers)
            }
            else -> null
        }
        respondQuestionContent(conversationId, question.id, payload) {
            _state.update {
                it.copy(
                    attentionActionBusy = false,
                    activeAttentionToast = null,
                    attentionActionError = null,
                )
            }
            syncSystemIslandOverlay()
        }
    }

    fun sendToastQuickReply(toast: CompanionAttentionToast, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (toast.kind is AttentionToastKind.Question) {
            respondToastQuestion(toast.conversationId, toast.kind.question, null, trimmed)
            return
        }
        if (toast.kind is AttentionToastKind.Permission) {
            val allowOpt = toast.kind.prompt.resolveAllowOption(PermissionAllowScope.Once)
                ?: toast.kind.prompt.options.firstOrNull()
            if (allowOpt != null) {
                respondPermission(toast.conversationId, toast.kind.prompt.id, allowOpt.id)
            }
        }
        dismissAttentionToast()
        send(toast.conversationId, trimmed)
    }

    fun handleSystemNotificationAction(
        actionKind: String,
        conversationId: String,
        targetId: String,
        optionId: String,
    ) {
        if (conversationId.isBlank()) return
        when (actionKind) {
            "perm_allow", "perm_reject" -> {
                if (targetId.isNotBlank()) {
                    respondPermission(conversationId, targetId, optionId.ifBlank { null })
                    dismissAttentionToast()
                }
            }
            "question_confirm" -> {
                val q = sessions[conversationId]?.pendingQuestion
                if (q != null) {
                    val firstChoice = QuestionForm.questions(q).firstOrNull()?.options?.firstOrNull()
                    respondToastQuestion(
                        conversationId,
                        q,
                        firstChoice,
                        if (firstChoice == null) "Yes" else null,
                    )
                } else if (targetId.isNotBlank()) {
                    approve(
                        PendingItem(
                            conversationId = conversationId,
                            conversationTitle = "",
                            kind = dev.vibex.companion.PendingKind.Question,
                            id = targetId,
                            title = "",
                            body = "",
                            options = emptyList(),
                            rowId = "",
                        ),
                        optionId.ifBlank { "Yes" },
                    )
                }
            }
            "question_reject" -> {
                if (targetId.isNotBlank()) {
                    respondQuestionContent(conversationId, targetId, null)
                    dismissAttentionToast()
                }
            }
            else -> openConversation(conversationId)
        }
    }

    private fun syncSessionAttentionAndLiveActivity(
        session: TimelineSession,
        isFirstSnapshot: Boolean = false,
    ) {
        val convId = session.conversationId.ifBlank { return }
        val snap = synchronized(session) {
            val isTurnRunning = session.inFlightTurnId != null
            val perm = session.permissionPrompt
            val quest = session.pendingQuestion
            val tasks = session.planTasks.toList()
            val rows = session.rows.toList()
            val title = session.title.ifBlank {
                _state.value.conversations.find { it.id == convId }?.title.orEmpty()
            }.ifBlank { "未命名对话" }
            val wsId = session.workspaceId.ifBlank {
                _state.value.conversations.find { it.id == convId }?.workspaceId.orEmpty()
            }
            val agId = session.agentId.ifBlank {
                _state.value.conversations.find { it.id == convId }?.agentId.orEmpty()
            }
            Triple(isTurnRunning, perm to quest, Triple(tasks, rows, Triple(title, wsId, agId)))
        }

        val isTurnRunning = snap.first
        val (perm, quest) = snap.second
        val (tasks, rows, meta) = snap.third
        val (sessionTitle, wsId, agId) = meta
        val project = resolveProjectName(wsId)

        val previouslyRunning = wasRunningByConversation.put(convId, isTurnRunning) ?: false
        initializedConversations.add(convId)

        // 1. Permission Prompt Attention Toast & System Notification with Action Buttons
        if (perm != null) {
            val eventKey = "perm:$convId:${perm.id}"
            if (notifiedEventKeys.add(eventKey)) {
                val subtitle = perm.command ?: perm.displaySubtitle ?: perm.displayHeading
                val allowOnce = perm.resolveAllowOption(PermissionAllowScope.Once)
                val allowSession = perm.resolveAllowOption(PermissionAllowScope.Session)
                val reject = perm.rejectOption
                val sysActions = buildList {
                    if (allowOnce != null) {
                        add(SystemNotificationAction("允许本次", "perm_allow", convId, perm.id, allowOnce.id))
                    }
                    if (allowSession != null && allowSession.id != allowOnce?.id) {
                        add(SystemNotificationAction("本会话允许", "perm_allow", convId, perm.id, allowSession.id))
                    }
                    if (reject != null) {
                        add(SystemNotificationAction("拒绝", "perm_reject", convId, perm.id, reject.id))
                    }
                }
                SystemNotifier.notifyAlert(
                    getApplication(),
                    eventKey,
                    "$project：需要批准操作权限",
                    "$sessionTitle · $subtitle",
                    convId,
                    actions = sysActions,
                )
                presentAttentionToast(
                    CompanionAttentionToast(
                        id = eventKey,
                        conversationId = convId,
                        workspaceId = wsId,
                        agentId = agId,
                        projectName = project,
                        title = "$project：需要批准操作权限",
                        subtitle = "$sessionTitle · $subtitle",
                        kind = AttentionToastKind.Permission(perm),
                    ),
                )
            }
        }

        // 2. Question Prompt Attention Toast & System Notification with Action Buttons
        if (quest != null) {
            val eventKey = "question:$convId:${quest.id}"
            if (notifiedEventKeys.add(eventKey)) {
                val qText = quest.prompt.ifBlank { "Agent 需要你确认下一步方案" }
                val sysActions = listOf(
                    SystemNotificationAction("批准执行", "question_confirm", convId, quest.id, "Yes"),
                    SystemNotificationAction("拒绝", "question_reject", convId, quest.id, ""),
                )
                SystemNotifier.notifyAlert(
                    getApplication(),
                    eventKey,
                    "$project：等待你的回答",
                    "$sessionTitle · $qText",
                    convId,
                    actions = sysActions,
                )
                presentAttentionToast(
                    CompanionAttentionToast(
                        id = eventKey,
                        conversationId = convId,
                        workspaceId = wsId,
                        agentId = agId,
                        projectName = project,
                        title = "$project：等待你的回答",
                        subtitle = "$sessionTitle · $qText",
                        kind = AttentionToastKind.Question(quest),
                    ),
                )
            }
        }

        // 3. Turn Transition: Running -> Completed / Failed (for background & foreground sessions)
        if (!isFirstSnapshot && previouslyRunning && !isTurnRunning && perm == null && quest == null) {
            val lastRow = rows.lastOrNull()
            val eventKey = "done:$convId:${session.lastSequence}:${rows.size}"
            if (notifiedEventKeys.add(eventKey)) {
                if (lastRow?.kind == "turn_failed") {
                    val errText = lastRow.body.ifBlank { sessionTitle }
                    SystemNotifier.notifyAlert(
                        getApplication(),
                        eventKey,
                        "$project · 会话执行异常",
                        errText,
                        convId,
                    )
                    presentAttentionToast(
                        CompanionAttentionToast(
                            id = eventKey,
                            conversationId = convId,
                            workspaceId = wsId,
                            agentId = agId,
                            projectName = project,
                            title = "$project：会话执行异常",
                            subtitle = errText,
                            kind = AttentionToastKind.Failed(errText),
                        ),
                    )
                    finishLiveActivity(convId, failedMessage = errText)
                } else {
                    SystemNotifier.notifyAlert(
                        getApplication(),
                        eventKey,
                        "$project · 会话已完成",
                        sessionTitle,
                        convId,
                    )
                    presentAttentionToast(
                        CompanionAttentionToast(
                            id = eventKey,
                            conversationId = convId,
                            workspaceId = wsId,
                            agentId = agId,
                            projectName = project,
                            title = "$project：会话已完成",
                            subtitle = sessionTitle,
                            kind = AttentionToastKind.Completed,
                        ),
                    )
                    finishLiveActivity(convId, failedMessage = null)
                }
            }
        }

        // 4. Sync Dynamic Island & Live Progress Notification
        val completedTasks = tasks.count { it.status == PlanTask.Status.Completed }
        val planActive = tasks.isNotEmpty() && completedTasks < tasks.size
        val hasPendingApproval = perm != null || quest != null
        val effectivelyActive = isTurnRunning || planActive || hasPendingApproval

        if (effectivelyActive) {
            liveActivityLingerJob?.cancel()
            liveActivityLingerJob = null
            activeLiveConversationId = convId
            val startedAtMs = liveRunStartMsByConversation.getOrPut(convId) { System.currentTimeMillis() }

            val latestToolRow = rows.lastOrNull { it.kind == "tool" }
            val stepCount = rows.count { it.kind == "tool" }
            val hasThinking = rows.any { it.kind == "reasoning" && it.body.isNotBlank() }
            val hasTextOutput = rows.lastOrNull()?.kind == "assistant"
            val approvalSummary = perm?.let { it.command ?: it.displaySubtitle ?: it.displayHeading }
                ?: quest?.prompt?.takeIf { it.isNotBlank() }

            val resolved = resolveHumanStage(
                tasks = tasks,
                completed = completedTasks,
                planActive = planActive,
                latestToolName = latestToolRow?.toolName?.ifBlank { latestToolRow.title },
                stepCount = stepCount,
                hasThinking = hasThinking,
                hasTextOutput = hasTextOutput,
                hasPendingApproval = hasPendingApproval,
                approvalSummary = approvalSummary,
            )

            val prevPeak = livePeakProgressByConversation[convId] ?: 0.12f
            val nextPeak = maxOf(prevPeak, resolved.progress)
            livePeakProgressByConversation[convId] = nextPeak
            val smoothProgress = if (planActive) resolved.progress else nextPeak

            val liveState = TaskLiveActivityState(
                conversationId = convId,
                projectName = project,
                conversationTitle = sessionTitle,
                phase = resolved.phase,
                stageTitle = resolved.stageTitle,
                stageSubtitle = resolved.stageSubtitle,
                progress = smoothProgress.coerceIn(0.10f, 0.96f),
                completed = if (planActive) completedTasks else 0,
                total = if (planActive) tasks.size else 0,
                stepCount = maxOf(stepCount, completedTasks),
                isRunning = isTurnRunning || hasPendingApproval,
                startedAtMs = startedAtMs,
            )

            _state.update { if (it.liveActivity == liveState) it else it.copy(liveActivity = liveState) }
            SystemNotifier.notifyLiveTaskProgress(getApplication(), liveState)
            syncSystemIslandOverlay()
        } else if (activeLiveConversationId == convId && liveActivityLingerJob == null) {
            finishLiveActivity(convId, failedMessage = null)
        }
    }

    private fun finishLiveActivity(conversationId: String, failedMessage: String?) {
        val current = _state.value.liveActivity
        if (current == null || current.conversationId != conversationId) return
        if (!current.isRunning && liveActivityLingerJob != null) return

        liveRunStartMsByConversation.remove(conversationId)
        livePeakProgressByConversation.remove(conversationId)

        val doneState = if (failedMessage != null) {
            current.copy(
                phase = VibeXActivityPhase.Failed,
                stageTitle = "执行中断",
                stageSubtitle = failedMessage,
                isRunning = false,
            )
        } else {
            current.copy(
                phase = VibeXActivityPhase.Completed,
                stageTitle = "任务已完成",
                stageSubtitle = "全部开发步骤已执行完毕，点击查看结果",
                progress = 1.0f,
                completed = if (current.total > 0) current.total else current.completed,
                isRunning = false,
            )
        }

        _state.update { it.copy(liveActivity = doneState) }
        SystemNotifier.notifyLiveTaskProgress(getApplication(), doneState)
        syncSystemIslandOverlay()

        liveActivityLingerJob?.cancel()
        liveActivityLingerJob = viewModelScope.launch {
            delay(20_000L)
            if (activeLiveConversationId == conversationId) {
                activeLiveConversationId = null
                _state.update { it.copy(liveActivity = null) }
                SystemNotifier.cancelLiveTaskProgress(getApplication())
                syncSystemIslandOverlay()
            }
        }
    }

    private data class ResolvedStage(
        val phase: VibeXActivityPhase,
        val stageTitle: String,
        val stageSubtitle: String,
        val progress: Float,
    )

    private fun resolveHumanStage(
        tasks: List<PlanTask>,
        completed: Int,
        planActive: Boolean,
        latestToolName: String?,
        stepCount: Int,
        hasThinking: Boolean,
        hasTextOutput: Boolean,
        hasPendingApproval: Boolean,
        approvalSummary: String?,
    ): ResolvedStage {
        val baseProgress: Float = if (planActive && tasks.isNotEmpty()) {
            val raw = completed.toFloat() / tasks.size.toFloat()
            raw.coerceIn(0.15f, 0.94f)
        } else {
            val stepFactor = (1.0 - kotlin.math.exp(-maxOf(1, stepCount).toDouble() * 0.22)).toFloat()
            (0.16f + stepFactor * 0.74f).coerceAtMost(0.92f)
        }

        if (hasPendingApproval) {
            return ResolvedStage(
                phase = VibeXActivityPhase.WaitingInput,
                stageTitle = "等待你确认",
                stageSubtitle = approvalSummary ?: "需要批准操作权限或确认执行方案",
                progress = maxOf(baseProgress, 0.55f),
            )
        }

        val tool = latestToolName?.lowercase().orEmpty()
        if (tool.isNotEmpty()) {
            if (
                tool.contains("edit") || tool.contains("write") || tool.contains("patch") ||
                tool.contains("create") || tool.contains("replace")
            ) {
                return ResolvedStage(
                    phase = VibeXActivityPhase.Developing,
                    stageTitle = "代码开发中",
                    stageSubtitle = "正在编写与修改项目模块",
                    progress = maxOf(baseProgress, 0.58f),
                )
            }
            if (
                tool.contains("bash") || tool.contains("exec") || tool.contains("run") ||
                tool.contains("shell") || tool.contains("terminal") || tool.contains("test") ||
                tool.contains("check") || tool.contains("build")
            ) {
                return ResolvedStage(
                    phase = VibeXActivityPhase.Verifying,
                    stageTitle = "调试验证中",
                    stageSubtitle = "正在执行构建或测试验证",
                    progress = maxOf(baseProgress, 0.74f),
                )
            }
            if (
                tool.contains("read") || tool.contains("search") || tool.contains("grep") ||
                tool.contains("find") || tool.contains("list") || tool.contains("view") ||
                tool.contains("tree") || tool.contains("glob")
            ) {
                return ResolvedStage(
                    phase = VibeXActivityPhase.Exploring,
                    stageTitle = "查阅项目中",
                    stageSubtitle = "正在检索项目结构与依赖关系",
                    progress = maxOf(baseProgress, 0.36f),
                )
            }
            return ResolvedStage(
                phase = VibeXActivityPhase.Developing,
                stageTitle = "代码开发中",
                stageSubtitle = "正在调用研发工具推进任务",
                progress = maxOf(baseProgress, 0.50f),
            )
        }

        if (hasTextOutput) {
            return ResolvedStage(
                phase = VibeXActivityPhase.Summarizing,
                stageTitle = "整理回复中",
                stageSubtitle = "正在生成总结与解决方案",
                progress = maxOf(baseProgress, 0.88f),
            )
        }

        if (hasThinking) {
            return ResolvedStage(
                phase = VibeXActivityPhase.Thinking,
                stageTitle = "思考规划中",
                stageSubtitle = "正在分析需求与梳理实现路径",
                progress = maxOf(baseProgress, 0.24f),
            )
        }

        return ResolvedStage(
            phase = VibeXActivityPhase.Thinking,
            stageTitle = "准备执行中",
            stageSubtitle = "VibeX 正在同步工作区状态",
            progress = 0.16f,
        )
    }

    private fun rebuildInbox() {
        val items = sessions.values.flatMap { session ->
            synchronized(session) { EventFold.pendingItems(session) }
        }
        _state.update { if (it.inbox == items) it else it.copy(inbox = items) }
    }

    private fun upsertProfile(profile: HostProfile, selected: Boolean) {
        val rest = _state.value.profiles.filterNot { it.hostId == profile.hostId } + profile
        val selectedId = if (selected) profile.hostId else _state.value.selectedHostId
        persist { it.copy(selectedHostId = selectedId, profiles = rest) }
        _state.update { it.copy(profiles = rest, selectedHostId = selectedId) }
        if (_state.value.accountSignedIn) {
            pushLocalHostsToAccount()
        }
    }

    fun openAccountSitePath(path: String) {
        val clean = if (path.startsWith("/")) path else "/$path"
        val url = "${VibeXAccountRepository.SITE_BASE_URL}$clean"
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        }
    }

    fun signInWithPassword(login: String, password: String) =
        runAccountSignIn { accountRepo.signInWithPassword(login, password) }

    fun signInWithCode(login: String, code: String) =
        runAccountSignIn { accountRepo.signInWithCode(login, code) }

    fun sendLoginCode(target: String) {
        if (_state.value.accountSendingCode) return
        viewModelScope.launch {
            _state.update { it.copy(accountSendingCode = true, accountError = null) }
            runCatching { accountRepo.sendLoginCode(target) }
                .onSuccess { cooldown ->
                    _state.update {
                        it.copy(
                            accountSendingCode = false,
                            accountCodeSent = true,
                            accountCodeResendAtMs = System.currentTimeMillis() + cooldown * 1000L,
                        )
                    }
                }
                .onFailure { err ->
                    val retryAfter = (err as? VibeXAccountException)?.retryAfter
                    _state.update {
                        it.copy(
                            accountSendingCode = false,
                            accountError = loginErrorMessage(err),
                            accountCodeResendAtMs = retryAfter?.let { s -> System.currentTimeMillis() + s * 1000L }
                                ?: it.accountCodeResendAtMs,
                        )
                    }
                }
        }
    }

    /**
     * Starts a device login, then hands the provider sign-in page to [openUrl] (an in-app browser tab); the site
     * lands on its authorize page, and the app returns to the foreground once the user approves there.
     */
    fun startOAuthLogin(provider: String, openUrl: (String) -> Unit) {
        if (_state.value.accountSigningIn) return
        accountLoginPollJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(accountSigningIn = true, accountError = null) }
            runCatching { accountRepo.startDeviceLogin() }
                .onSuccess { login ->
                    _state.update { it.copy(accountSigningIn = false) }
                    openUrl(AccountAuthRequests.oauthStartUrl(VibeXAccountRepository.SITE_BASE_URL, provider, login.verifyUrl))
                    pollOAuthLogin(login)
                }
                .onFailure { err ->
                    _state.update { it.copy(accountSigningIn = false, accountError = loginErrorMessage(err)) }
                }
        }
    }

    /** Shows the login screen from Settings while a paired computer keeps the app usable. */
    fun requestAccountLogin() = setAccountLoginRequested(true)

    fun dismissAccountLogin() {
        setAccountLoginRequested(false)
        _state.update { it.copy(accountError = null) }
    }

    private fun runAccountSignIn(signIn: suspend () -> Unit) {
        if (_state.value.accountSigningIn) return
        accountLoginPollJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(accountSigningIn = true, accountError = null) }
            runCatching { signIn() }
                .onSuccess { completeAccountLogin() }
                .onFailure { err ->
                    _state.update { it.copy(accountSigningIn = false, accountError = loginErrorMessage(err)) }
                }
        }
    }

    /** Polls silently until the request expires: an unapproved tab just leaves the user on the login form. */
    private fun pollOAuthLogin(login: VibeXDeviceLogin) {
        val expiresAtMs = System.currentTimeMillis() + login.expiresIn.coerceAtLeast(30) * 1000L
        accountLoginPollJob = viewModelScope.launch {
            while (isActive && System.currentTimeMillis() < expiresAtMs) {
                delay(login.interval * 1000L)
                val approved = try {
                    accountRepo.pollDeviceLogin(login)
                } catch (err: VibeXAccountException) {
                    if (err.code == "network") false else return@launch
                }
                if (approved) {
                    completeAccountLogin()
                    bringAppToForeground()
                    return@launch
                }
            }
        }
    }

    private fun completeAccountLogin() {
        setAccountLoginRequested(false)
        _state.update {
            it.copy(
                accountSignedIn = true,
                accountSigningIn = false,
                accountCodeSent = false,
                accountCodeResendAtMs = 0L,
                accountError = null,
                desktopConnect = if (it.connection == ConnectionState.Online) null else DesktopConnectSheet(),
            )
        }
        refreshAccount()
        discoverHosts()
    }

    /** Clearing the task above MainActivity closes the sign-in browser tab. */
    private fun bringAppToForeground() {
        val app = getApplication<Application>()
        runCatching {
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                ),
            )
        }
    }

    private fun loginErrorMessage(err: Throwable): String =
        if (err is VibeXAccountException) AccountAuthErrors.message(err.code, err.fields) else AccountAuthErrors.FALLBACK

    fun signOutAccount() {
        if (_state.value.accountSigningOut) return
        accountLoginPollJob?.cancel()
        accountLoginPollJob = null
        connectPollJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(accountSigningOut = true) }
            accountRepo.signOut()
            setAccountLoginRequested(true)
            _state.update {
                it.copy(
                    accountSignedIn = false,
                    accountOverview = null,
                    accountDesktops = emptyList(),
                    accountSavedHosts = emptyList(),
                    accountSigningOut = false,
                    accountError = null,
                    desktopConnect = null,
                )
            }
        }
    }

    /** Keeps the login screen up after an explicit sign-out, even while a paired computer remains. */
    private fun setAccountLoginRequested(requested: Boolean) {
        prefs.edit().putBoolean(PREF_ACCOUNT_LOGIN_REQUESTED, requested).apply()
        _state.update { it.copy(accountLoginRequested = requested) }
    }

    fun refreshAccount() {
        if (accountRepo.getToken() == null) {
            _state.update { it.copy(accountSignedIn = false, accountOverview = null, desktopConnect = null) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    accountSignedIn = true,
                    accountLoading = it.accountOverview == null,
                    accountError = null,
                )
            }
            runCatching { accountRepo.fetchOverview() }
                .onSuccess { overview ->
                    _state.update {
                        it.copy(
                            accountSignedIn = true,
                            accountOverview = overview,
                            accountLoading = false,
                            accountError = null,
                        )
                    }
                    syncAccountHosts(forcePush = true)
                }
                .onFailure { err ->
                    if (err is VibeXAccountException && err.code == "signed_out") {
                        _state.update {
                            it.copy(
                                accountSignedIn = false,
                                accountOverview = null,
                                accountDesktops = emptyList(),
                                accountSavedHosts = emptyList(),
                                accountLoading = false,
                                desktopConnect = null,
                            )
                        }
                    } else {
                        _state.update {
                            val sheet = it.desktopConnect?.takeIf { s -> s.stage == DesktopConnectStage.Syncing }
                                ?.copy(stage = DesktopConnectStage.Failed(DESKTOP_SYNC_FAILED, route = null))
                            it.copy(
                                accountLoading = false,
                                accountError = describeAccountError(err),
                                desktopConnect = sheet ?: it.desktopConnect,
                            )
                        }
                    }
                }
        }
    }

    fun syncAccountHosts(forcePush: Boolean = false) {
        if (accountRepo.getToken() == null) return
        viewModelScope.launch {
            _state.update { it.copy(accountSyncingHosts = true) }
            val desktops = runCatching { accountRepo.fetchDesktops() }
            val remoteSaved = runCatching { accountRepo.fetchSavedHosts() }.getOrDefault(emptyList())
            mergeRemoteAccountProfiles(remoteSaved)
            val localExport = exportSyncableProfiles()
            val finalSaved = if (localExport.isNotEmpty() && (forcePush || localExport.size != remoteSaved.size)) {
                runCatching { accountRepo.putSavedHosts(localExport) }.getOrDefault(remoteSaved)
            } else {
                remoteSaved
            }
            _state.update { current ->
                val listed = desktops.getOrDefault(current.accountDesktops)
                val sheet = current.desktopConnect?.takeIf { it.stage == DesktopConnectStage.Syncing }?.let {
                    if (desktops.isSuccess) {
                        it.copy(stage = DesktopConnectStage.Choosing, selectedHostId = listed.firstOrNull()?.hostId)
                    } else {
                        it.copy(stage = DesktopConnectStage.Failed(DESKTOP_SYNC_FAILED, route = null))
                    }
                } ?: current.desktopConnect
                current.copy(
                    accountDesktops = listed,
                    accountSavedHosts = finalSaved,
                    accountSyncingHosts = false,
                    desktopConnect = sheet,
                )
            }
        }
    }

    private fun pushLocalHostsToAccount() {
        if (accountRepo.getToken() == null) return
        viewModelScope.launch {
            val localExport = exportSyncableProfiles()
            if (localExport.isEmpty()) return@launch
            runCatching { accountRepo.putSavedHosts(localExport) }.onSuccess { saved ->
                _state.update { it.copy(accountSavedHosts = saved) }
            }
        }
    }

    private fun mergeRemoteAccountProfiles(remoteSaved: List<VibeXSyncableHostProfile>) {
        val relayBase = PairingClient.DEFAULT_OFFICIAL_RELAY_BASE.trimEnd('/')
        val currentList = _state.value.profiles.toMutableList()
        var didChange = false

        for (remote in remoteSaved) {
            if (remote.accessToken.isBlank()) continue
            val cleanHostId = remote.hostId.ifBlank { remote.profileId }
            if (cleanHostId.isBlank()) continue
            var targetOrigin = remote.origin.trim().trimEnd('/')
            if (
                (targetOrigin.isBlank() || targetOrigin.contains("127.0.0.1") || targetOrigin.contains("localhost")) &&
                remote.hostId.isNotBlank() &&
                relayBase.isNotEmpty()
            ) {
                targetOrigin = "$relayBase/t/${remote.hostId}"
            }
            if (targetOrigin.isBlank()) continue
            val exists = currentList.any {
                it.hostId == cleanHostId ||
                    it.lastSuccessOrigin == targetOrigin ||
                    it.reachability.any { r -> r.origin == targetOrigin }
            }
            if (!exists) {
                credentials.put(cleanHostId, remote.accessToken)
                currentList += HostProfile(
                    hostId = cleanHostId,
                    name = remote.name.ifBlank { "VibeX 电脑" },
                    deviceId = remote.deviceId.ifBlank { remote.profileId },
                    reachability = listOf(StoredReachability(targetOrigin, "published")),
                    lastSuccessOrigin = targetOrigin,
                    lastSyncAt = System.currentTimeMillis(),
                )
                didChange = true
            }
        }

        if (didChange) {
            val selectedId = _state.value.selectedHostId ?: currentList.firstOrNull()?.hostId
            persist { it.copy(selectedHostId = selectedId, profiles = currentList) }
            _state.update { it.copy(profiles = currentList, selectedHostId = selectedId) }
        }
    }

    private fun exportSyncableProfiles(): List<VibeXSyncableHostProfile> {
        val nowIso = Instant.now().toString()
        return _state.value.profiles.mapNotNull { profile ->
            val token = credentials.get(profile.hostId)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val origin = profile.lastSuccessOrigin
                ?: profile.reachability.firstOrNull()?.origin
                ?: return@mapNotNull null
            VibeXSyncableHostProfile(
                syncKey = profile.hostId,
                profileId = profile.deviceId.ifBlank { profile.hostId },
                hostId = profile.hostId,
                name = profile.name,
                origin = origin,
                provisionKind = "http",
                deviceId = profile.deviceId.ifBlank { profile.hostId },
                accessToken = token,
                lastConnectedAt = nowIso,
                updatedAt = nowIso,
            )
        }
    }

    private fun describeAccountError(err: Throwable): String {
        if (err is VibeXAccountException) {
            return when (err.code) {
                "network" -> "无法连接到 VibeX 账号服务，请检查网络后重试"
                "signed_out" -> "登录状态已失效，请重新登录"
                else -> "账号服务请求失败（${err.code}）"
            }
        }
        return err.message ?: "账号请求失败"
    }

    private fun persist(transform: (StoredState) -> StoredState) {
        val next = transform(StoredState(_state.value.selectedHostId, _state.value.profiles, _state.value.appearance, _state.value.streamDisplay, _state.value.accent))
        viewModelScope.launch(Dispatchers.IO) {
            profiles.save(next)
        }
    }

    private fun selectedProfile(): HostProfile? =
        _state.value.profiles.find { it.hostId == _state.value.selectedHostId }

    private fun selectedConversation(id: String) = _state.value.conversations.find { it.id == id }

    private fun live(): Pair<HostOrigin, String> {
        val profile = selectedProfile() ?: error("没有已选 Host")
        val origin = _state.value.activeOrigin ?: profile.lastSuccessOrigin ?: error("没有可用地址")
        val token = credentials.get(profile.hostId) ?: throw AuthRequiredException()
        return HostOrigin.parse(origin) to token
    }

    fun activeWorkspaceIdForProject(projectId: String?): String {
        val state = _state.value
        val pid = projectId ?: state.selectedProjectId ?: state.catalog?.projects?.firstOrNull()?.id
        return state.catalog?.workspaces.orEmpty()
            .firstOrNull { pid == null || it.projectId == pid }?.id
            ?: state.conversations.firstOrNull()?.workspaceId.orEmpty()
    }

    suspend fun fetchKanbanSessions(projectId: String?): List<KanbanSessionSummary> {
        val (origin, token) = live()
        val catalog = _state.value.catalog ?: withContext(Dispatchers.IO) { api.catalog(origin, token) }
        val wsIds = if (!projectId.isNullOrBlank()) {
            catalog.workspaces.filter { it.projectId.equals(projectId, ignoreCase = true) }.map { it.id }
        } else {
            catalog.workspaces.map { it.id }
        }
        return withContext(Dispatchers.IO) {
            api.kanbanAllSessions(origin, token, wsIds)
        }
    }

    suspend fun fetchMonitorPreview(sessionId: String): String? {
        val (origin, token) = live()
        return withContext(Dispatchers.IO) {
            val rows = runCatching { api.timelineRows(origin, token, sessionId) }.getOrNull().orEmpty()
            val temp = TimelineSession().also { it.conversationId = sessionId }
            EventFold.applyTimelineRows(temp, rows)
            temp.rows.lastOrNull { it.body.isNotBlank() }?.body
        }
    }

    suspend fun mutateKanbanSessionStatus(sessionId: String, status: KanbanSessionStatus) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) { api.updateKanbanSessionStatus(origin, token, sessionId, status) }
        runCatching { loadConversations() }
    }

    suspend fun mutateKanbanSessionPinned(sessionId: String, pinned: Boolean) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) { api.setKanbanSessionPinned(origin, token, sessionId, pinned) }
        runCatching { loadConversations() }
    }

    suspend fun mutateKanbanSessionRename(sessionId: String, name: String?) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) { api.renameKanbanSession(origin, token, sessionId, name) }
        runCatching { loadConversations() }
    }

    suspend fun mutateKanbanSessionDelete(sessionId: String) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) { api.deleteKanbanSession(origin, token, sessionId) }
        runCatching { loadConversations() }
    }

    suspend fun mutateKanbanSessionViewed(sessionId: String) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) { api.markKanbanSessionViewed(origin, token, sessionId) }
    }

    suspend fun mutateKanbanSessionFork(sessionId: String): String? {
        val (origin, token) = live()
        val forkedId = withContext(Dispatchers.IO) { api.forkKanbanConversation(origin, token, sessionId) }
        runCatching { loadConversations() }
        return forkedId
    }

    suspend fun fetchUsageStatistics(
        scope: String,
        projectId: String?,
        dateRange: UsageDateRange,
    ): ProjectUsageStatistics {
        val (origin, token) = live()
        return withContext(Dispatchers.IO) {
            api.projectUsageStatistics(origin, token, scope, projectId, dateRange)
        }
    }

    suspend fun fetchAgentPlanUsage(agentId: String): PlanUsageResponse {
        val (origin, token) = live()
        return withContext(Dispatchers.IO) {
            api.agentPlanUsage(origin, token, agentId)
        }
    }

    suspend fun fetchCollabOverview(workspaceId: String): Triple<List<CollabSidebarItem>, List<CollabContactGroup>, List<CollabSpaceSummary>> {
        val (origin, token) = live()
        return withContext(Dispatchers.IO) {
            val items = api.collabSidebar(origin, token, workspaceId)
            val groups = api.collabContactGroups(origin, token, workspaceId)
            val spaces = api.collabSpaces(origin, token, workspaceId)
            Triple(items, groups, spaces)
        }
    }

    suspend fun fetchCollabChatState(spaceId: String, isGroup: Boolean): Pair<CollabMessagesPage, List<CollabSpaceMember>> {
        val (origin, token) = live()
        return withContext(Dispatchers.IO) {
            val page = api.collabMessagesPage(origin, token, spaceId)
            val members = if (isGroup) api.collabSpaceMembers(origin, token, spaceId) else emptyList()
            page to members
        }
    }

    suspend fun postCollabMessage(
        spaceId: String,
        content: String,
        to: List<String> = emptyList(),
        inReplyTo: String? = null,
        delaySeconds: Int? = null,
    ) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) {
            api.collabPostMessage(origin, token, spaceId, content, to, inReplyTo, delaySeconds)
        }
    }

    suspend fun reactCollab(spaceId: String, messageId: String, emoji: String) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) {
            api.collabReact(origin, token, spaceId, messageId, emoji)
        }
    }

    suspend fun markCollabThreadRead(threadId: String, seq: Int? = null, read: Boolean = true) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) {
            if (read) api.collabMarkRead(origin, token, threadId, seq)
            else api.collabMarkUnread(origin, token, threadId)
        }
    }

    suspend fun execCollabCommand(operation: String, input: Map<String, Any?>) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) {
            api.collaboration(origin, token, operation, input)
        }
    }

    suspend fun createCollabAssistantFull(
        workspaceId: String,
        name: String,
        agentId: String?,
        roleId: String?,
        personaPrompt: String?,
        avatar: CollabBotAvatarConfig,
        isSupervisor: Boolean,
        targetSpaceId: String? = null,
    ) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) {
            val convId = api.collabCreateMember(
                origin = origin,
                token = token,
                workspaceId = workspaceId,
                name = name,
                agentId = agentId,
                roleId = roleId,
                personaPrompt = personaPrompt,
                avatar = avatar,
                color = avatar.color,
                isSupervisor = isSupervisor,
            )
            if (!targetSpaceId.isNullOrBlank() && !convId.isNullOrBlank()) {
                api.collabAddMemberToSpace(origin, token, targetSpaceId, convId, name)
            }
        }
    }

    suspend fun addCollabMemberToSpace(spaceId: String, conversationId: String, nickname: String) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) {
            api.collabAddMemberToSpace(origin, token, spaceId, conversationId, nickname)
        }
    }

    suspend fun resolveCollabMemberSession(memberId: String, threadId: String): String? {
        val (origin, token) = live()
        return withContext(Dispatchers.IO) {
            api.collabResolveSession(origin, token, memberId, threadId)
        }
    }

    suspend fun resolveCollabPendingCard(
        cardId: String,
        decision: String? = null,
        answer: String? = null,
        declined: Boolean = false,
    ) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) {
            api.collabResolveCard(origin, token, cardId, decision, answer, declined)
        }
    }

    suspend fun cancelCollabSpaceTurn(spaceId: String, memberId: String) {
        val (origin, token) = live()
        withContext(Dispatchers.IO) {
            api.collabCancelSpaceTurn(origin, token, spaceId, memberId)
        }
    }

    suspend fun fetchCollabDetailData(
        workspaceId: String,
        spaceId: String,
        isGroup: Boolean,
        memberIdentityId: String?,
    ): CollabDetailBundle {
        val (origin, token) = live()
        return withContext(Dispatchers.IO) {
            val members = if (isGroup) api.collabSpaceMembers(origin, token, spaceId).filter { it.isAgent } else emptyList()
            val leadId = if (isGroup) {
                api.collabSpaces(origin, token, workspaceId).firstOrNull { it.spaceId == spaceId }?.leadMemberId.orEmpty()
            } else ""
            val memberCfg = if (!isGroup && !memberIdentityId.isNullOrBlank()) {
                api.collabMemberConfig(origin, token, memberIdentityId)
            } else null
            val memory = api.collabMemoryView(origin, token, spaceId, memberIdentityId)
            val collection = api.collabCollectionItems(origin, token, spaceId)
            CollabDetailBundle(
                members = members,
                leadMemberId = leadId,
                memberConfig = memberCfg,
                memory = memory,
                collection = collection,
            )
        }
    }

    suspend fun fetchRolesAndTeams(workspaceId: String): Pair<List<SubagentRoleItem>, List<CollabTeam>> {
        val (origin, token) = live()
        return withContext(Dispatchers.IO) {
            val roles = api.listSubagentRoles(origin, token, workspaceId)
            val teams = api.collabTeamsList(origin, token, workspaceId)
            roles to teams
        }
    }

    internal fun fail(error: Throwable) {
        if (error is AuthRequiredException) {
            handleForcedOfflineByRevocation()
            return
        }
        val raw = error.message.orEmpty()
        val message = when {
            raw.contains("is not registered") -> "电脑端需要更新后重启"
            raw.contains("timeout", true) || raw.contains("timed out", true) -> "连接超时"
            raw.contains("Host health check", true) ||
                raw.contains("/t/") -> "电脑端暂未响应，请确认电脑在线"
            raw.isBlank() -> "请求失败"
            else -> raw
        }
        _state.update { it.copy(error = message, pairing = false) }
        scheduleAutoDismissNotice(3_500L)
    }
}
