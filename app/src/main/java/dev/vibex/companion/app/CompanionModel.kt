package dev.vibex.companion.app

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vibex.companion.AuthRequiredException
import dev.vibex.companion.CatalogAgent
import dev.vibex.companion.CatalogWorkspace
import dev.vibex.companion.CollaborationMessageItem
import dev.vibex.companion.CollaborationThreadSummary
import dev.vibex.companion.ConnectionState
import dev.vibex.companion.ConversationSummary
import dev.vibex.companion.ConversationView
import dev.vibex.companion.EventFold
import dev.vibex.companion.ExtensionCatalogItem
import dev.vibex.companion.HostApi
import dev.vibex.companion.HostOrigin
import dev.vibex.companion.OriginOrder
import dev.vibex.companion.PairingClient
import dev.vibex.companion.PairingCoordinator
import dev.vibex.companion.PairingInvitationParser
import dev.vibex.companion.PendingItem
import dev.vibex.companion.ReachabilityTarget
import dev.vibex.companion.SessionCatalog
import dev.vibex.companion.SubagentRoleItem
import dev.vibex.companion.TimelineSession
import dev.vibex.companion.WorkspaceFileDiff
import dev.vibex.companion.applyCreateTimeSessionConfig
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
    val route: Route = Route.Conversations,
)

enum class SettingsSection { Profile, Connection, Extensions, Appearance, Font, Notification, Stream, Host, Capabilities, About }

sealed class Route {
    data object Onboarding : Route()
    data object ManualPair : Route()
    data object Folders : Route()
    data object Conversations : Route()
    data object Status : Route()
    data object Collaboration : Route()
    data class Settings(val section: SettingsSection? = null) : Route()
    data object NewConversation : Route()
    data class Timeline(val conversationId: String) : Route()
}

class CompanionModel(application: Application) : AndroidViewModel(application) {
    private val profiles = ProfileStore(application)
    private val credentials = CredentialStore(application)
    private val offline = OfflineStore(application)
    private val transport = OkHttpTransport()
    private val api = HostApi(transport)
    private val pairing = PairingCoordinator(PairingClient(transport), api)
    private val socket = EventSocket()
    private val sessions = mutableMapOf<String, TimelineSession>()
    private val subscriptions = mutableMapOf<String, String>()
    private var pingJob: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val stored = profiles.load()
        val selectedHostId = stored.selectedHostId ?: stored.profiles.firstOrNull()?.hostId
        val selected = stored.profiles.find { it.hostId == selectedHostId }
        _state.value = UiState(
            profiles = stored.profiles,
            selectedHostId = selectedHostId,
            selectedProjectId = selected?.selectedProjectId,
            appearance = stored.appearance,
            accent = if (stored.accent == AccentChoice.Ink) AccentChoice.Blue else stored.accent,
            streamDisplay = stored.streamDisplay,
            route = Route.Conversations,
            connection = if (stored.profiles.isEmpty()) ConnectionState.Offline else ConnectionState.Connecting,
        )
        if (stored.profiles.isNotEmpty()) {
            connectSelected()
        }
    }


    fun selectProject(projectId: String) {
        val profile = selectedProfile()?.copy(selectedProjectId = projectId) ?: return
        upsertProfile(profile, selected = true)
        _state.update { it.copy(selectedProjectId = projectId, route = Route.Conversations) }
        viewModelScope.launch { runCatching { loadConversations() }.onFailure { fail(it) } }
    }

    fun loadCatalogSilent() {
        viewModelScope.launch {
            runCatching { fetchCatalog(openNew = false) }.onFailure { fail(it) }
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

    fun pairFromRaw(raw: String) {
        viewModelScope.launch {
            _state.update { it.copy(pairing = true, error = null) }
            runCatching {
                val invitation = PairingInvitationParser.parse(raw)
                redeemOrMerge(invitation)
            }.onFailure { fail(it) }
            _state.update { it.copy(pairing = false) }
        }
    }

    fun pairManual(origin: String, token: String) {
        viewModelScope.launch {
            _state.update { it.copy(pairing = true, error = null) }
            runCatching {
                redeemOrMerge(PairingInvitationParser.parseManual(origin, token))
            }.onFailure { fail(it) }
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
                val (origin, token) = live()
                val created = withContext(Dispatchers.IO) {
                    api.createConversation(origin, token, workspaceId, agentId, title.ifBlank { null }, prompt.ifBlank { null })
                }
                val agent = _state.value.catalog?.agents?.find { it.id == agentId }
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
                val boundAgent = created.agentId.ifBlank { agentId }
                val session = sessions.getOrPut(created.id) {
                    TimelineSession().also { createdSession ->
                        createdSession.conversationId = created.id
                        createdSession.title = title.ifBlank { created.title }
                        createdSession.agentId = boundAgent
                        createdSession.workspaceId = workspaceId
                    }
                }.apply {
                    this.agentId = boundAgent
                    this.workspaceId = workspaceId.ifBlank { this.workspaceId }
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
                    workspaceId = created.workspaceId.ifBlank { workspaceId },
                    title = title.ifBlank { created.title },
                    agentId = boundAgent,
                )
                _state.update { state ->
                    state.copy(
                        conversations = listOf(summary) + state.conversations.filterNot { it.id == summary.id },
                    )
                }
                loadConversations()
                openConversation(created.id)
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
                fetchCatalog(openNew = false)
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
                if (agentId.isBlank()) error("会话还没有 Agent")
                val workspaceId = session.workspaceId.ifBlank { summary?.workspaceId.orEmpty() }
                if (workspaceId.isBlank()) error("会话还没有工作区")
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
                val events = withContext(Dispatchers.IO) {
                    api.offlineEvents(origin, token, conversationId, session.lastSequence)
                }
                EventFold.applyAll(session, events)
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
                val (origin, token) = live()
                withContext(Dispatchers.IO) {
                    when (item.kind) {
                        dev.vibex.companion.PendingKind.Permission ->
                            api.respondPermission(origin, token, item.conversationId, item.id, optionId)
                        else -> api.respondQuestion(origin, token, item.conversationId, item.id, optionId)
                    }
                }
            }.onFailure { fail(it) }
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            runCatching { fetchCatalog(openNew = false) }.onFailure { fail(it) }
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

    private suspend fun fetchCatalog(openNew: Boolean) {
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
                    route = if (openNew) Route.NewConversation else it.route,
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

    private suspend fun redeemOrMerge(invitation: dev.vibex.companion.PairingInvitation) {
        val existing = invitation.hostId?.let { id -> _state.value.profiles.find { it.hostId == id } }
        if (existing != null) {
            val merged = (existing.reachability + invitation.reachability.map {
                StoredReachability(it.origin.value, it.kind)
            }).distinctBy { it.origin }
            val updated = existing.copy(reachability = merged)
            upsertProfile(updated, selected = true)
            _state.update { it.copy(notice = "已更新地址", route = Route.Conversations) }
            connect(updated)
            return
        }
        val result = withContext(Dispatchers.IO) {
            pairing.redeemInvitation(invitation, Build.MODEL)
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

    private suspend fun connect(profile: HostProfile) {
        if (
            _state.value.connection == ConnectionState.Online &&
                _state.value.selectedHostId == profile.hostId &&
                !_state.value.activeOrigin.isNullOrBlank()
        ) {
            loadCatalogSilent()
            return
        }
        _state.update { it.copy(connection = ConnectionState.Connecting, error = null, triedOrigins = emptyList()) }
        val token = credentials.get(profile.hostId)
        if (token.isNullOrBlank()) {
            _state.update { it.copy(connection = ConnectionState.AuthRequired) }
            return
        }
        val targets = profile.reachability.map {
            ReachabilityTarget(HostOrigin.parse(it.origin), it.kind)
        }
        val order = OriginOrder.probeOrder(targets, profile.lastSuccessOrigin)
        val tried = mutableListOf<String>()
        var lastError: Exception? = null
        for (origin in order) {
            tried += origin.value
            try {
                val caps = withContext(Dispatchers.IO) { api.capabilities(origin, token) }
                if (caps.protocol_version.substringBefore('.') != "1") {
                    _state.update { it.copy(connection = ConnectionState.Incompatible, triedOrigins = tried) }
                    return
                }
                upsertProfile(profile.copy(lastSuccessOrigin = origin.value, lastSyncAt = System.currentTimeMillis()), true)
                _state.update {
                    it.copy(
                        connection = ConnectionState.Online,
                        activeOrigin = origin.value,
                        triedOrigins = tried,
                        hostVersion = caps.server_version,
                        hostProtocol = caps.protocol_version,
                        hostCapabilities = caps.capabilities,
                    )
                }
                bindSocket(origin.value, token)
                loadCatalogSilent()
                if (_state.value.selectedProjectId != null) {
                    loadConversations()
                }
                return
            } catch (error: AuthRequiredException) {
                _state.update { it.copy(connection = ConnectionState.AuthRequired, triedOrigins = tried) }
                return
            } catch (error: Exception) {
                lastError = error
            }
        }
        _state.update {
            it.copy(
                connection = ConnectionState.Offline,
                triedOrigins = tried,
                error = lastError?.message,
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
            onEvent = { subscriptionId, event ->
                val conversationId = subscriptions[subscriptionId] ?: (_state.value.route as? Route.Timeline)?.conversationId ?: return@connect
                val session = sessions[conversationId] ?: return@connect
                EventFold.apply(session, event)
                offline.save(conversationId, session.lastSequence, emptyList())
                publishTimeline(session)
            },
            onSnapshot = { subscriptionId, payload ->
                val conversationId = subscriptions[subscriptionId] ?: (_state.value.route as? Route.Timeline)?.conversationId ?: return@connect
                val session = sessions.getOrPut(conversationId) { TimelineSession().also { it.conversationId = conversationId } }
                EventFold.applySnapshot(session, payload)
                publishTimeline(session)
            },
            onClosed = { reason ->
                if (reason == "auth") {
                    _state.update { it.copy(connection = ConnectionState.AuthRequired) }
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
                delay(20_000)
                socket.ping()
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
        if (session.agentId.isBlank()) session.agentId = summary?.agentId.orEmpty()
        if (session.workspaceId.isBlank()) session.workspaceId = summary?.workspaceId.orEmpty()
        val cached = offline.load(id)
        if (cached.second.isNotEmpty()) {
            EventFold.applyAll(session, cached.second)
            publishTimeline(session)
        }
        if (!_state.value.connection.allowsWrites && _state.value.connection != ConnectionState.Online) {
            return
        }
        val after = session.lastSequence
        val subscriptionId = socket.attach(id, after)
        subscriptions[subscriptionId] = id
        runCatching {
            val (origin, token) = live()
            val events = withContext(Dispatchers.IO) { api.offlineEvents(origin, token, id, after) }
            EventFold.applyAll(session, events)
            offline.save(id, session.lastSequence, events)
            val skills = withContext(Dispatchers.IO) {
                api.listSlashCommands(
                    origin,
                    token,
                    session.agentId.ifBlank { summary?.agentId.orEmpty() },
                    session.workspaceId.ifBlank { summary?.workspaceId.orEmpty() },
                )
            }
            session.skillCommands.clear()
            session.skillCommands += skills
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
            merged.take(8).forEach { summary ->
                viewModelScope.launch {
                    runCatching {
                        val events = withContext(Dispatchers.IO) {
                            api.offlineEvents(origin, token, summary.id, 0)
                        }
                        val session = sessions.getOrPut(summary.id) {
                            TimelineSession().also {
                                it.conversationId = summary.id
                                it.title = summary.title
                                it.agentId = summary.agentId
                                it.workspaceId = summary.workspaceId
                            }
                        }
                        EventFold.applyAll(session, events)
                        rebuildInbox()
                    }
                }
            }
        } catch (error: Throwable) {
            _state.update { it.copy(conversationsLoading = false) }
            throw error
        }
    }

    private fun publishTimeline(session: TimelineSession) {
        if ((_state.value.route as? Route.Timeline)?.conversationId == session.conversationId) {
            _state.update { it.copy(timeline = session.snapshot()) }
        }
        rebuildInbox()
    }

    private fun rebuildInbox() {
        val items = sessions.values.flatMap { EventFold.pendingItems(it) }
        _state.update { it.copy(inbox = items) }
    }

    private fun upsertProfile(profile: HostProfile, selected: Boolean) {
        val rest = _state.value.profiles.filterNot { it.hostId == profile.hostId } + profile
        val selectedId = if (selected) profile.hostId else _state.value.selectedHostId
        persist { it.copy(selectedHostId = selectedId, profiles = rest) }
        _state.update { it.copy(profiles = rest, selectedHostId = selectedId) }
    }

    private fun persist(transform: (StoredState) -> StoredState) {
        val next = transform(StoredState(_state.value.selectedHostId, _state.value.profiles, _state.value.appearance, _state.value.streamDisplay, _state.value.accent))
        profiles.save(next)
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

    private fun fail(error: Throwable) {
        if (error is AuthRequiredException) {
            _state.update { it.copy(connection = ConnectionState.AuthRequired, error = "需要重新配对", pairing = false) }
            return
        }
        val raw = error.message.orEmpty()
        val message = when {
            raw.contains("is not registered") -> "电脑端需要更新后重启"
            raw.contains("timeout", true) || raw.contains("timed out", true) -> "连接超时"
            raw.isBlank() -> "请求失败"
            else -> raw
        }
        _state.update { it.copy(error = message, pairing = false) }
    }
}
