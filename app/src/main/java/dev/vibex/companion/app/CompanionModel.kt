package dev.vibex.companion.app

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val pairing: Boolean = false,
    val monitor: Boolean = false,
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
    val preferredDiscoveryMode: String? = "lan",
    val pendingConnectSasCode: String? = null,
    val pendingConnectHostName: String? = null,
    val pendingConnectOrigin: String? = null,
    val lastConnectDurationMs: Long? = null,
    val realtimeRttMs: Long? = null,
    val transportPath: String = "LAN 直连",
    val connectionFailureReason: String? = null,
    val pendingConflictDeviceName: String? = null,
    val pendingCloudFallbackOrigin: String? = null,
    val pendingCloudFallbackHostName: String? = null,
    val autoConnect: Boolean = true,
    val route: Route = Route.Conversations,
)

enum class SettingsSection { Profile, Connection, Extensions, Font, Notification, About }

sealed class Route {
    data object Onboarding : Route()
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

class CompanionModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("vibex_companion_prefs", Context.MODE_PRIVATE)
    private val profiles = ProfileStore(application)
    private val credentials = CredentialStore(application)
    private val offline = OfflineStore(application)
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
    private var pendingConflictRetryAction: (() -> Unit)? = null
    private val declinedCloudFallbackHostIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var boostPollingUntil: Long = 0L

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val stored = profiles.load()
        val savedAutoConnect = prefs.getBoolean("auto_connect", true)
        val selectedHostId = stored.selectedHostId ?: stored.profiles.firstOrNull()?.hostId
        val selected = stored.profiles.find { it.hostId == selectedHostId }
        _state.value = UiState(
            profiles = stored.profiles,
            selectedHostId = selectedHostId,
            selectedProjectId = selected?.selectedProjectId,
            appearance = stored.appearance,
            accent = if (stored.accent == AccentChoice.Ink) AccentChoice.Blue else stored.accent,
            streamDisplay = stored.streamDisplay,
            autoConnect = savedAutoConnect,
            route = Route.Conversations,
            connection = if (stored.profiles.isEmpty() || !savedAutoConnect) ConnectionState.Offline else ConnectionState.Connecting,
            connectionFailureReason = if (!savedAutoConnect && stored.profiles.isNotEmpty()) "自动连接已关闭" else null,
        )
        if (stored.profiles.isNotEmpty() && savedAutoConnect) {
            connectSelected()
        } else {
            discoverHosts(autoRequestIfSingle = false, modeOverride = "lan")
        }
        startAutoReconnectLoop()
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
        _state.update { it.copy(pendingConflictDeviceName = null, pairing = false) }
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
                if (current.profiles.isNotEmpty() && current.autoConnect) {
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
                } else if (
                    current.preferredDiscoveryMode != null &&
                    current.discoveredHosts.isEmpty() &&
                    current.pendingConnectSasCode == null &&
                    !current.discoveringHosts &&
                    !current.pairing
                ) {
                    discoverHosts(autoRequestIfSingle = false)
                }
            }
        }
    }

    fun selectDiscoveryMode(mode: String?) {
        if (mode == null) {
            _state.update {
                it.copy(
                    preferredDiscoveryMode = null,
                    discoveredHosts = emptyList(),
                    discoveringHosts = false,
                )
            }
            return
        }
        _state.update {
            it.copy(
                preferredDiscoveryMode = mode,
                discoveredHosts = emptyList(),
            )
        }
        discoverHosts(autoRequestIfSingle = false, modeOverride = mode)
    }

    fun discoverHosts(autoRequestIfSingle: Boolean = false, modeOverride: String? = null) {
        if (_state.value.discoveringHosts) return
        val mode = modeOverride ?: _state.value.preferredDiscoveryMode ?: return
        viewModelScope.launch {
            _state.update { it.copy(discoveringHosts = true, preferredDiscoveryMode = mode) }
            val knownRelayOrigins = _state.value.profiles.flatMap { p -> p.reachability.map { it.origin } }
            val useCloud = mode == "cloud"
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    PairingClient.discoverLanHosts(
                        extraRelayOrigins = if (useCloud) knownRelayOrigins else emptyList(),
                        includeCloudRelay = useCloud,
                        cloudOnly = useCloud,
                    )
                }.getOrDefault(emptyList())
            }
            _state.update { it.copy(discoveredHosts = found, discoveringHosts = false) }
            if (
                autoRequestIfSingle &&
                found.size == 1 &&
                _state.value.profiles.isEmpty() &&
                _state.value.pendingConnectSasCode == null &&
                !_state.value.pairing
            ) {
                requestConnectToHost(found.first())
            }
        }
    }

    fun requestConnectToHost(host: DiscoveredLanHost, replaceExistingMobile: Boolean = false) {
        val current = _state.value
        if (current.pendingConnectSasCode != null && current.pendingConnectOrigin == host.origin) {
            return
        }
        connectPollJob?.cancel()
        connectPollJob = viewModelScope.launch {
            _state.update { it.copy(pairing = true, error = null) }
            val origin = runCatching { HostOrigin.parse(host.origin) }.getOrNull() ?: run {
                _state.update { it.copy(pairing = false, error = "无效电脑地址") }
                return@launch
            }
            if (origin.value.contains("/t/")) {
                PairingClient.rememberRelayBase(origin.value)
            }
            val requested = withContext(Dispatchers.IO) {
                runCatching {
                    pairingClient.requestConnect(
                        origin = origin,
                        deviceName = Build.MODEL.ifBlank { "Android Companion" },
                        replaceExistingMobile = replaceExistingMobile,
                    )
                }
            }.getOrElse { err ->
                if (err is MobileDeviceConflictException) {
                    pendingConflictRetryAction = {
                        requestConnectToHost(host, replaceExistingMobile = true)
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
                _state.update { it.copy(pairing = false) }
                fail(err)
                return@launch
            }

            _state.update {
                it.copy(
                    pairing = false,
                    pendingConnectSasCode = requested.sasCode,
                    pendingConnectHostName = requested.hostName ?: host.name,
                    pendingConnectOrigin = origin.value,
                    error = null,
                )
            }

            while (isActive) {
                delay(1_500)
                val poll = withContext(Dispatchers.IO) {
                    runCatching { pairingClient.pollConnect(origin, requested.requestId) }.getOrNull()
                } ?: continue

                when (poll.status) {
                    "approved" -> {
                        val cred = poll.credential
                        if (cred != null) {
                            val resolvedHostId = poll.hostId.ifBlank { host.hostId ?: "host:${origin.value}" }
                            credentials.put(resolvedHostId, cred.access_token)
                            val displayHost = (poll.hostName ?: host.name).ifBlank {
                                origin.value.removePrefix("http://").removePrefix("https://").substringBefore(":")
                            }
                            val profile = HostProfile(
                                hostId = resolvedHostId,
                                name = if (displayHost.startsWith("VibeX", ignoreCase = true)) displayHost else "VibeX ($displayHost)",
                                deviceId = cred.device_id,
                                reachability = listOf(StoredReachability(origin.value, "lan")),
                                lastSuccessOrigin = origin.value,
                                lastSyncAt = System.currentTimeMillis(),
                                selectedProjectId = null,
                            )
                            upsertProfile(profile, selected = true)
                            _state.update {
                                it.copy(
                                    pendingConnectSasCode = null,
                                    pendingConnectHostName = null,
                                    pendingConnectOrigin = null,
                                    notice = "已连接电脑 $displayHost",
                                    route = Route.Conversations,
                                )
                            }
                            connect(profile)
                            break
                        }
                    }
                    "rejected" -> {
                        _state.update {
                            it.copy(
                                pendingConnectSasCode = null,
                                pendingConnectHostName = null,
                                pendingConnectOrigin = null,
                                error = "电脑端已忽略连接请求",
                            )
                        }
                        break
                    }
                    "expired" -> {
                        _state.update {
                            it.copy(
                                pendingConnectSasCode = null,
                                pendingConnectHostName = null,
                                pendingConnectOrigin = null,
                                error = "连接请求已超时，请重新点击连接",
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    fun cancelPendingConnectRequest() {
        connectPollJob?.cancel()
        connectPollJob = null
        _state.update {
            it.copy(
                pendingConnectSasCode = null,
                pendingConnectHostName = null,
                pendingConnectOrigin = null,
            )
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

    fun dismissNotice() = _state.update { it.copy(notice = null, error = null) }

    fun clearOfflineCache() {
        offline.clear()
        _state.update { it.copy(notice = "已清除本机缓存") }
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
                    _state.value.discoveredHosts.forEach { candidates.add(it.origin) }
                    _state.value.activeOrigin?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                    _state.value.profiles.mapNotNull { it.lastSuccessOrigin }.forEach { candidates.add(it) }
                    if (candidates.isEmpty()) {
                        val autoFound = withContext(Dispatchers.IO) {
                            runCatching { PairingClient.discoverLanHosts(includeCloudRelay = true) }.getOrDefault(emptyList())
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
            socket.close()
            _state.update {
                it.copy(
                    profiles = remaining,
                    selectedHostId = remaining.firstOrNull()?.hostId,
                    route = if (remaining.isEmpty()) Route.Onboarding else Route.Folders,
                    conversations = emptyList(),
                    timeline = null,
                    inbox = emptyList(),
                    connection = if (remaining.isEmpty()) ConnectionState.Offline else ConnectionState.Connecting,
                )
            }
            if (remaining.isNotEmpty()) connectSelected()
        }
    }

    fun openConversation(id: String) {
        _state.update { it.copy(route = Route.Timeline(id), timeline = sessions[id]?.snapshot()) }
        viewModelScope.launch { attachConversation(id) }
        startTimelinePoller(id)
    }

    private fun startTimelinePoller(id: String) {
        timelinePollJob?.cancel()
        timelinePollJob = viewModelScope.launch {
            delay(800)
            while (isActive && (_state.value.route as? Route.Timeline)?.conversationId == id) {
                val session = sessions[id]
                val fast = session?.inFlightTurnId != null || System.currentTimeMillis() < boostPollingUntil
                runCatching { syncConversationTimeline(id, forceRows = fast) }
                if (fast) {
                    delay(900)
                } else {
                    for (step in 0 until 8) {
                        if (!isActive || (_state.value.route as? Route.Timeline)?.conversationId != id) break
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
                val agent = _state.value.catalog?.agents?.find { it.id == resolvedAgentId }
                withContext(Dispatchers.IO) {
                    if (!modeId.isNullOrBlank() && modeId != agent?.currentModeId) {
                        runCatching { api.setSessionMode(origin, token, created.id, modeId) }.onFailure { fail(it) }
                    }
                    config.forEach { (key, value) ->
                        val default = agent?.sessionConfig?.find { option -> option.key == key }?.value
                        if (key.isNotBlank() && value.isNotBlank() && value != default) {
                            runCatching { api.setSessionConfigOption(origin, token, created.id, key, value) }.onFailure { fail(it) }
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
            runCatching { PairingClient.discoverLanHosts(extraRelayOrigins = knownOriginStrings, includeCloudRelay = false) }
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
                delay(4_000)
                socket.ping()
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

    private suspend fun loadConversations() {
        val projectId = _state.value.selectedProjectId
        if (projectId.isNullOrBlank()) {
            _state.update { it.copy(conversations = emptyList(), conversationsLoading = false) }
            return
        }
        _state.update { it.copy(conversationsLoading = true) }
        try {
            val (origin, token) = live()
            val listed = withContext(Dispatchers.IO) {
                api.listRecent(origin, token, _state.value.sinceDays, projectId)
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
                        val afterSeq = synchronized(session) { session.lastSequence }
                        if (afterSeq == 0L || summary.status.equals("inprogress", ignoreCase = true)) {
                            val events = api.offlineEvents(origin, token, summary.id, afterSeq)
                            if (events.isNotEmpty()) {
                                synchronized(session) {
                                    EventFold.applyAll(session, events)
                                }
                                anyChanged = true
                            }
                        }
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
        if ((_state.value.route as? Route.Timeline)?.conversationId == session.conversationId) {
            val snap = synchronized(session) { session.snapshot() }
            _state.update { if (it.timeline == snap) it else it.copy(timeline = snap) }
        }
        rebuildInbox()
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
    }
}
