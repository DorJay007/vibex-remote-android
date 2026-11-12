package dev.vibex.companion.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.BotAvatarExpression
import dev.vibex.companion.BotAvatarShape
import dev.vibex.companion.CollabBotAvatarConfig
import dev.vibex.companion.CollabCard
import dev.vibex.companion.CollabCollectionItem
import dev.vibex.companion.CollabContactGroup
import dev.vibex.companion.CollabDeliveryReceipt
import dev.vibex.companion.CollabMemoryView
import dev.vibex.companion.CollabScheduledMessage
import dev.vibex.companion.CollabSidebarItem
import dev.vibex.companion.CollabSpaceMember
import dev.vibex.companion.CollabSpaceMessage
import dev.vibex.companion.CollabSpaceSummary
import dev.vibex.companion.CollabTeam
import dev.vibex.companion.SubagentRoleItem
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

private enum class CollabTab(val title: String) {
    Primary("主要"),
    All("全部"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaborationScreen(
    state: UiState,
    model: CompanionModel,
    onChatOpenChange: (Boolean) -> Unit = {},
) {
    val c = VibexTheme.colors
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(CollabTab.Primary) }
    var searchText by remember { mutableStateOf("") }
    var sidebarItems by remember { mutableStateOf<List<CollabSidebarItem>>(emptyList()) }
    var contactGroups by remember { mutableStateOf<List<CollabContactGroup>>(emptyList()) }
    var spaces by remember { mutableStateOf<List<CollabSpaceSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var collapsedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var activeChatItem by remember { mutableStateOf<CollabSidebarItem?>(null) }
    var openedBotConversationId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeChatItem != null || openedBotConversationId != null) {
        onChatOpenChange(activeChatItem != null || openedBotConversationId != null)
    }

    var showAddMenu by remember { mutableStateOf(false) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showCreateGroupAlert by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showCreateAssistantSheet by remember { mutableStateOf(false) }
    var showCreateContactGroupAlert by remember { mutableStateOf(false) }
    var newContactGroupName by remember { mutableStateOf("") }
    var showRolesAndTeamsSheet by remember { mutableStateOf(false) }

    var renamingContactGroup by remember { mutableStateOf<CollabContactGroup?>(null) }
    var renameContactGroupText by remember { mutableStateOf("") }

    val activeProject = remember(state.selectedProjectId, state.catalog?.projects) {
        state.catalog?.projects?.firstOrNull { it.id == state.selectedProjectId }
            ?: state.catalog?.projects?.firstOrNull()
    }

    val activeWorkspaceId = remember(state.selectedProjectId, state.catalog?.workspaces, state.conversations) {
        model.activeWorkspaceIdForProject(state.selectedProjectId)
    }

    fun refresh(silent: Boolean = false) {
        val wsId = activeWorkspaceId
        if (wsId.isBlank()) {
            sidebarItems = emptyList()
            contactGroups = emptyList()
            spaces = emptyList()
            return
        }
        scope.launch {
            if (!silent && sidebarItems.isEmpty()) loading = true
            runCatching {
                model.fetchCollabOverview(wsId)
            }.onSuccess { (items, groups, sp) ->
                sidebarItems = items
                contactGroups = groups
                spaces = sp
                activeChatItem?.let { current ->
                    items.firstOrNull { it.id == current.id }?.let { activeChatItem = it }
                }
            }
            loading = false
        }
    }

    LaunchedEffect(activeWorkspaceId, state.connection) {
        refresh(silent = false)
        while (isActive) {
            delay(5_000L)
            refresh(silent = true)
        }
    }

    val filteredItems = remember(sidebarItems, selectedTab, searchText) {
        val q = searchText.trim().lowercase()
        val basePool = when (selectedTab) {
            CollabTab.All -> sidebarItems
            CollabTab.Primary -> {
                val primary = sidebarItems.filter { item ->
                    item.isGroup ||
                        item.member?.isSupervisor == true ||
                        item.unreadCount > 0 ||
                        item.lastMessage != null
                }
                if (primary.isEmpty()) sidebarItems else primary
            }
        }
        if (q.isEmpty()) {
            basePool
        } else {
            basePool.filter { item ->
                item.displayName.lowercase().contains(q) ||
                    item.lastMessage?.preview?.lowercase()?.contains(q) == true
            }
        }
    }

    val supervisorItem = remember(filteredItems) {
        filteredItems.firstOrNull { !it.isGroup && it.member?.isSupervisor == true }
    }
    val groupItems = remember(filteredItems) {
        filteredItems.filter { it.isGroup }
    }
    val directItems = remember(filteredItems, supervisorItem) {
        filteredItems.filter { !it.isGroup && it.id != supervisorItem?.id }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bg)
                // The list stays composed under an open chat; keep it out of TalkBack's reach.
                .then(if (activeChatItem != null) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            // Top Header Bar (Project Selector | 协作空间 | + Menu) — Strictly Centered Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    Surface(
                        onClick = { showProjectMenu = true },
                        shape = RoundedCornerShape(50),
                        color = c.bgElevated,
                        border = BorderStroke(0.5.dp, c.stroke),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
                            Text(
                                text = activeProject?.name ?: "项目",
                                color = c.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp),
                            )
                            Icon(Icons.Outlined.UnfoldMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(12.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = showProjectMenu,
                        onDismissRequest = { showProjectMenu = false },
                    ) {
                        state.catalog?.projects.orEmpty().forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.name) },
                                trailingIcon = {
                                    if (state.selectedProjectId == project.id) {
                                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    showProjectMenu = false
                                    model.selectProjectInPlace(project.id)
                                },
                            )
                        }
                    }
                }

                Text(
                    text = "协作空间",
                    color = c.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center,
                )

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    Surface(
                        onClick = { showAddMenu = true },
                        shape = CircleShape,
                        color = c.bgElevated,
                        border = BorderStroke(0.5.dp, c.stroke),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Add, contentDescription = "新建", tint = c.textPrimary, modifier = Modifier.size(17.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("新建助手") },
                            leadingIcon = { Icon(Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showAddMenu = false
                                showCreateAssistantSheet = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("新建群组") },
                            leadingIcon = { Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showAddMenu = false
                                newGroupName = ""
                                showCreateGroupAlert = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("新建联系人分组") },
                            leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showAddMenu = false
                                newContactGroupName = ""
                                showCreateContactGroupAlert = true
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("角色与团队管理") },
                            leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showAddMenu = false
                                showRolesAndTeamsSheet = true
                            },
                        )
                    }
                }
            }

            // Centered Segmented Switcher (主要 | 全部) + Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .width(196.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.segmentedTrack)
                        .padding(3.dp),
                ) {
                    CollabTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        Text(
                            text = tab.title,
                            color = if (selected) c.textPrimary else c.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (selected) Modifier.shadow(1.5.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black.copy(alpha = 0.08f))
                                    else Modifier,
                                )
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) c.segmentedThumb else Color.Transparent)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 6.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.bgElevated)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(15.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchText.isEmpty()) {
                            Text("搜索群组或助手…", color = c.textSecondary, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            singleLine = true,
                            textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (searchText.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "清除",
                            tint = c.textSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchText = "" },
                        )
                    }
                }
            }

            // Content List
            if (loading && sidebarItems.isEmpty()) {
                CollaborationListSkeletonView(modifier = Modifier.fillMaxSize())
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(36.dp))
                        Text("暂无协作空间或助手", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("点击右上角 + 创建你的第一个助手或协作群组", color = c.textSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Supervisor Slot
                    if (supervisorItem != null) {
                        item(key = "section_supervisor") {
                            CollabSectionCard(title = "主管") {
                                CollabContactRow(
                                    item = supervisorItem,
                                    isSupervisorSlot = true,
                                    contactGroups = contactGroups,
                                    onClick = { activeChatItem = supervisorItem },
                                    onMarkRead = { read ->
                                        scope.launch {
                                            runCatching { model.markCollabThreadRead(supervisorItem.threadId, supervisorItem.lastMessage?.seq, read) }
                                            refresh(silent = true)
                                        }
                                    },
                                    onToggleSupervisor = { memberId, nextSupervisor ->
                                        scope.launch {
                                            runCatching {
                                                model.execCollabCommand(
                                                    "member.update",
                                                    mapOf("memberId" to memberId, "isSupervisor" to nextSupervisor),
                                                )
                                            }
                                            refresh(silent = true)
                                        }
                                    },
                                    onUpdateSpaceTagColor = { _, _ -> },
                                    onMoveToContactGroup = { groupId ->
                                        val entryId = supervisorItem.member?.resolvedIdentityId ?: supervisorItem.targetSpaceId
                                        scope.launch {
                                            runCatching {
                                                model.execCollabCommand(
                                                    "contact.moveToGroup",
                                                    mapOf(
                                                        "workspaceId" to activeWorkspaceId,
                                                        "groupId" to groupId,
                                                        "entryType" to if (supervisorItem.isGroup) "space" else "member",
                                                        "entryId" to entryId,
                                                    ),
                                                )
                                            }
                                            refresh(silent = true)
                                        }
                                    },
                                    onDelete = {
                                        val memberId = supervisorItem.member?.resolvedIdentityId ?: return@CollabContactRow
                                        scope.launch {
                                            runCatching {
                                                model.execCollabCommand("member.delete", mapOf("memberId" to memberId))
                                            }
                                            refresh(silent = false)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (contactGroups.isNotEmpty()) {
                        val knownGroupIds = contactGroups.map { it.groupId }.toSet()

                        items(contactGroups, key = { "cg_${it.groupId}" }) { group ->
                            val groupEntries = filteredItems.filter { item ->
                                item.id != supervisorItem?.id && item.groupId == group.groupId
                            }
                            if (groupEntries.isNotEmpty() || searchText.isEmpty()) {
                                val isCollapsed = collapsedGroupIds.contains(group.groupId)
                                var showGroupMenu by remember { mutableStateOf(false) }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    collapsedGroupIds = if (isCollapsed) collapsedGroupIds - group.groupId else collapsedGroupIds + group.groupId
                                                },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                                contentDescription = null,
                                                tint = c.textSecondary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = "${group.name} (${groupEntries.size})",
                                                color = c.textSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { showGroupMenu = true },
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                                            }
                                            DropdownMenu(
                                                expanded = showGroupMenu,
                                                onDismissRequest = { showGroupMenu = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("重命名分组") },
                                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(17.dp)) },
                                                    onClick = {
                                                        showGroupMenu = false
                                                        renameContactGroupText = group.name
                                                        renamingContactGroup = group
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("删除分组", color = c.danger) },
                                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(17.dp)) },
                                                    onClick = {
                                                        showGroupMenu = false
                                                        scope.launch {
                                                            runCatching {
                                                                model.execCollabCommand("contact.deleteGroup", mapOf("groupId" to group.groupId))
                                                            }
                                                            refresh(silent = false)
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    if (!isCollapsed && groupEntries.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = c.card,
                                            border = BorderStroke(0.5.dp, c.stroke),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column {
                                                groupEntries.forEachIndexed { idx, entry ->
                                                    if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                                    CollabContactRow(
                                                        item = entry,
                                                        isSupervisorSlot = false,
                                                        contactGroups = contactGroups,
                                                        onClick = { activeChatItem = entry },
                                                        onMarkRead = { read ->
                                                            scope.launch {
                                                                runCatching { model.markCollabThreadRead(entry.threadId, entry.lastMessage?.seq, read) }
                                                                refresh(silent = true)
                                                            }
                                                        },
                                                        onToggleSupervisor = { memberId, nextSup ->
                                                            scope.launch {
                                                                runCatching {
                                                                    model.execCollabCommand("member.update", mapOf("memberId" to memberId, "isSupervisor" to nextSup))
                                                                }
                                                                refresh(silent = true)
                                                            }
                                                        },
                                                        onUpdateSpaceTagColor = { spaceId, hex ->
                                                            scope.launch {
                                                                runCatching {
                                                                    model.execCollabCommand("space.update", mapOf("spaceId" to spaceId, "tagColor" to hex))
                                                                }
                                                                refresh(silent = true)
                                                            }
                                                        },
                                                        onMoveToContactGroup = { targetGroupId ->
                                                            val entryId = if (entry.isGroup) entry.targetSpaceId else (entry.member?.resolvedIdentityId ?: entry.targetSpaceId)
                                                            scope.launch {
                                                                runCatching {
                                                                    model.execCollabCommand(
                                                                        "contact.moveToGroup",
                                                                        mapOf(
                                                                            "workspaceId" to activeWorkspaceId,
                                                                            "groupId" to targetGroupId,
                                                                            "entryType" to if (entry.isGroup) "space" else "member",
                                                                            "entryId" to entryId,
                                                                        ),
                                                                    )
                                                                }
                                                                refresh(silent = true)
                                                            }
                                                        },
                                                        onDelete = {
                                                            scope.launch {
                                                                runCatching {
                                                                    if (entry.isGroup) {
                                                                        model.execCollabCommand("space.delete", mapOf("spaceId" to entry.targetSpaceId))
                                                                    } else {
                                                                        val memberId = entry.member?.resolvedIdentityId ?: return@launch
                                                                        model.execCollabCommand("member.delete", mapOf("memberId" to memberId))
                                                                    }
                                                                }
                                                                refresh(silent = false)
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val ungrouped = filteredItems.filter { item ->
                            item.id != supervisorItem?.id && (item.groupId.isNullOrBlank() || item.groupId !in knownGroupIds)
                        }
                        if (ungrouped.isNotEmpty()) {
                            item(key = "section_ungrouped") {
                                CollabSectionCard(title = "未分组 (${ungrouped.size})") {
                                    ungrouped.forEachIndexed { idx, entry ->
                                        if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                        CollabContactRow(
                                            item = entry,
                                            isSupervisorSlot = false,
                                            contactGroups = contactGroups,
                                            onClick = { activeChatItem = entry },
                                            onMarkRead = { read ->
                                                scope.launch {
                                                    runCatching { model.markCollabThreadRead(entry.threadId, entry.lastMessage?.seq, read) }
                                                    refresh(silent = true)
                                                }
                                            },
                                            onToggleSupervisor = { memberId, nextSup ->
                                                scope.launch {
                                                    runCatching {
                                                        model.execCollabCommand("member.update", mapOf("memberId" to memberId, "isSupervisor" to nextSup))
                                                    }
                                                    refresh(silent = true)
                                                }
                                            },
                                            onUpdateSpaceTagColor = { spaceId, hex ->
                                                scope.launch {
                                                    runCatching {
                                                        model.execCollabCommand("space.update", mapOf("spaceId" to spaceId, "tagColor" to hex))
                                                    }
                                                    refresh(silent = true)
                                                }
                                            },
                                            onMoveToContactGroup = { targetGroupId ->
                                                val entryId = if (entry.isGroup) entry.targetSpaceId else (entry.member?.resolvedIdentityId ?: entry.targetSpaceId)
                                                scope.launch {
                                                    runCatching {
                                                        model.execCollabCommand(
                                                            "contact.moveToGroup",
                                                            mapOf(
                                                                "workspaceId" to activeWorkspaceId,
                                                                "groupId" to targetGroupId,
                                                                "entryType" to if (entry.isGroup) "space" else "member",
                                                                "entryId" to entryId,
                                                            ),
                                                        )
                                                    }
                                                    refresh(silent = true)
                                                }
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    runCatching {
                                                        if (entry.isGroup) {
                                                            model.execCollabCommand("space.delete", mapOf("spaceId" to entry.targetSpaceId))
                                                        } else {
                                                            val memberId = entry.member?.resolvedIdentityId ?: return@launch
                                                            model.execCollabCommand("member.delete", mapOf("memberId" to memberId))
                                                        }
                                                    }
                                                    refresh(silent = false)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (groupItems.isNotEmpty()) {
                            item(key = "section_groups") {
                                CollabSectionCard(title = "群组 (${groupItems.size})") {
                                    groupItems.forEachIndexed { idx, entry ->
                                        if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                        CollabContactRow(
                                            item = entry,
                                            isSupervisorSlot = false,
                                            contactGroups = contactGroups,
                                            onClick = { activeChatItem = entry },
                                            onMarkRead = { read ->
                                                scope.launch {
                                                    runCatching { model.markCollabThreadRead(entry.threadId, entry.lastMessage?.seq, read) }
                                                    refresh(silent = true)
                                                }
                                            },
                                            onToggleSupervisor = { _, _ -> },
                                            onUpdateSpaceTagColor = { spaceId, hex ->
                                                scope.launch {
                                                    runCatching {
                                                        model.execCollabCommand("space.update", mapOf("spaceId" to spaceId, "tagColor" to hex))
                                                    }
                                                    refresh(silent = true)
                                                }
                                            },
                                            onMoveToContactGroup = { _ -> },
                                            onDelete = {
                                                scope.launch {
                                                    runCatching {
                                                        model.execCollabCommand("space.delete", mapOf("spaceId" to entry.targetSpaceId))
                                                    }
                                                    refresh(silent = false)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (directItems.isNotEmpty()) {
                            item(key = "section_direct") {
                                CollabSectionCard(title = "助手 (${directItems.size})") {
                                    directItems.forEachIndexed { idx, entry ->
                                        if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                        CollabContactRow(
                                            item = entry,
                                            isSupervisorSlot = false,
                                            contactGroups = contactGroups,
                                            onClick = { activeChatItem = entry },
                                            onMarkRead = { read ->
                                                scope.launch {
                                                    runCatching { model.markCollabThreadRead(entry.threadId, entry.lastMessage?.seq, read) }
                                                    refresh(silent = true)
                                                }
                                            },
                                            onToggleSupervisor = { memberId, nextSup ->
                                                scope.launch {
                                                    runCatching {
                                                        model.execCollabCommand("member.update", mapOf("memberId" to memberId, "isSupervisor" to nextSup))
                                                    }
                                                    refresh(silent = true)
                                                }
                                            },
                                            onUpdateSpaceTagColor = { _, _ -> },
                                            onMoveToContactGroup = { _ -> },
                                            onDelete = {
                                                val memberId = entry.member?.resolvedIdentityId ?: return@CollabContactRow
                                                scope.launch {
                                                    runCatching {
                                                        model.execCollabCommand("member.delete", mapOf("memberId" to memberId))
                                                    }
                                                    refresh(silent = false)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = activeChatItem,
            contentKey = { it?.id },
            transitionSpec = {
                if (targetState != null) {
                    NavMotion.coverEnter togetherWith ExitTransition.None
                } else {
                    EnterTransition.None togetherWith NavMotion.coverExit
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "collaborationChat",
        ) { chatItem ->
            if (chatItem != null) {
                // Swallow touches so blank chat areas never reach the list underneath.
                Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
                    CollaborationChatView(
                        state = state,
                        model = model,
                        item = chatItem,
                        spaceSummary = spaces.firstOrNull { it.spaceId == chatItem.targetSpaceId },
                        workspaceId = activeWorkspaceId,
                        directAssistants = remember(sidebarItems) { sidebarItems.filter { !it.isGroup } },
                        onBack = {
                            activeChatItem = null
                            refresh(silent = true)
                        },
                        onOpenConversation = { convId ->
                            openedBotConversationId = convId
                            model.openInlineConversation(convId)
                        },
                        onMutated = { refresh(silent = true) },
                        onDeleteGroupDone = {
                            activeChatItem = null
                            refresh(silent = false)
                        },
                    )
                }
            }
        }

        AnimatedContent(
            targetState = openedBotConversationId,
            contentKey = { it },
            transitionSpec = {
                if (targetState != null) {
                    NavMotion.coverEnter togetherWith ExitTransition.None
                } else {
                    EnterTransition.None togetherWith NavMotion.coverExit
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "collaborationDetailedSession",
        ) { detailConvId ->
            if (detailConvId != null) {
                BackHandler {
                    model.closeInlineConversation()
                    openedBotConversationId = null
                }
                Box(Modifier.fillMaxSize().background(c.bg).pointerInput(Unit) {}) {
                    TimelinePane(
                        state = state,
                        model = model,
                        conversationId = detailConvId,
                        onBack = {
                            model.closeInlineConversation()
                            openedBotConversationId = null
                        },
                    )
                }
            }
        }
    }

    // Create Group Dialog
    if (showCreateGroupAlert) {
        AlertDialog(
            onDismissRequest = { showCreateGroupAlert = false },
            title = { Text("新建协作群组") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("群组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newGroupName.trim()
                        showCreateGroupAlert = false
                        if (trimmed.isNotEmpty() && activeWorkspaceId.isNotEmpty()) {
                            scope.launch {
                                runCatching {
                                    model.execCollabCommand(
                                        "space.create",
                                        mapOf("workspaceId" to activeWorkspaceId, "name" to trimmed),
                                    )
                                }
                                refresh(silent = false)
                            }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupAlert = false }) { Text("取消") }
            },
        )
    }

    // Create Contact Group Dialog
    if (showCreateContactGroupAlert) {
        AlertDialog(
            onDismissRequest = { showCreateContactGroupAlert = false },
            title = { Text("新建联系人分组") },
            text = {
                OutlinedTextField(
                    value = newContactGroupName,
                    onValueChange = { newContactGroupName = it },
                    label = { Text("分组名称（如：核心研发、产品设计）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newContactGroupName.trim()
                        showCreateContactGroupAlert = false
                        if (trimmed.isNotEmpty() && activeWorkspaceId.isNotEmpty()) {
                            scope.launch {
                                runCatching {
                                    model.execCollabCommand(
                                        "contact.createGroup",
                                        mapOf("workspaceId" to activeWorkspaceId, "name" to trimmed),
                                    )
                                }
                                refresh(silent = false)
                            }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateContactGroupAlert = false }) { Text("取消") }
            },
        )
    }

    // Rename Contact Group Dialog
    if (renamingContactGroup != null) {
        val group = renamingContactGroup!!
        AlertDialog(
            onDismissRequest = { renamingContactGroup = null },
            title = { Text("重命名分组") },
            text = {
                OutlinedTextField(
                    value = renameContactGroupText,
                    onValueChange = { renameContactGroupText = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameContactGroupText.trim()
                        renamingContactGroup = null
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                runCatching {
                                    model.execCollabCommand(
                                        "contact.renameGroup",
                                        mapOf("groupId" to group.groupId, "name" to trimmed),
                                    )
                                }
                                refresh(silent = false)
                            }
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renamingContactGroup = null }) { Text("取消") }
            },
        )
    }

    if (showCreateAssistantSheet) {
        CreateAssistantSheet(
            state = state,
            model = model,
            workspaceId = activeWorkspaceId,
            targetSpaceId = null,
            onDismiss = { showCreateAssistantSheet = false },
            onCreated = {
                showCreateAssistantSheet = false
                refresh(silent = false)
            },
        )
    }

    if (showRolesAndTeamsSheet) {
        RolesAndTeamsSheet(
            model = model,
            workspaceId = activeWorkspaceId,
            directAssistants = sidebarItems.filter { !it.isGroup },
            onDismiss = { showRolesAndTeamsSheet = false },
            onMutated = { refresh(silent = true) },
        )
    }
}

@Composable
private fun CollabSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val c = VibexTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = c.card,
            border = BorderStroke(0.5.dp, c.stroke),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                content()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollabContactRow(
    item: CollabSidebarItem,
    isSupervisorSlot: Boolean,
    contactGroups: List<CollabContactGroup>,
    onClick: () -> Unit,
    onMarkRead: (Boolean) -> Unit,
    onToggleSupervisor: (String, Boolean) -> Unit,
    onUpdateSpaceTagColor: (String, String?) -> Unit,
    onMoveToContactGroup: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    val c = VibexTheme.colors
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CollabAvatarView(item = item, size = 44.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.displayName,
                        color = c.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isSupervisorSlot || item.member?.isSupervisor == true) {
                        Text(
                            text = "主管",
                            color = Color(0xFFF97316),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFF97316).copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    } else if (item.isGroup) {
                        Text(
                            text = "(${item.memberCount})",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        val agId = item.member?.agent
                        if (!agId.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (c.isDark) c.codeSurface else c.bg)
                                    .border(0.5.dp, c.stroke, RoundedCornerShape(50))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                AgentAvatar(agentId = agId, size = 12.dp)
                                Text(
                                    text = agentDisplayName(agId),
                                    color = c.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    item.lastMessage?.createdAt?.takeIf { it.isNotBlank() }?.let { ts ->
                        Text(
                            text = formatCollabRelativeTime(ts),
                            color = c.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val previewText = item.lastMessage?.preview
                        ?: if (item.isGroup) "点击进入群聊" else "点击发起私聊"
                    Text(
                        text = previewText,
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (item.unreadCount > 0) {
                        Text(
                            text = if (item.unreadCount > 99) "99+" else "${item.unreadCount}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF2563EB))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (item.unreadCount > 0) {
                DropdownMenuItem(
                    text = { Text("标为已读") },
                    leadingIcon = { Icon(Icons.Outlined.MarkChatRead, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onMarkRead(true)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("标为未读") },
                    leadingIcon = { Icon(Icons.Outlined.MarkChatUnread, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onMarkRead(false)
                    },
                )
            }

            if (!item.isGroup && item.member != null) {
                val member = item.member!!
                val nextSup = !member.isSupervisor
                DropdownMenuItem(
                    text = { Text(if (nextSup) "设为主管" else "取消主管身份") },
                    leadingIcon = {
                        Icon(
                            if (nextSup) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleSupervisor(member.resolvedIdentityId, nextSup)
                    },
                )
            }

            if (item.isGroup) {
                HorizontalDivider()
                CollabTagPalette.presets.forEach { tag ->
                    DropdownMenuItem(
                        text = { Text("标签颜色：${tag.label}") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(tag.color),
                            )
                        },
                        onClick = {
                            showMenu = false
                            onUpdateSpaceTagColor(item.targetSpaceId, tag.id)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("清除标签颜色") },
                    leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onUpdateSpaceTagColor(item.targetSpaceId, null)
                    },
                )
            }

            if (contactGroups.isNotEmpty()) {
                HorizontalDivider()
                contactGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text("移至分组：${group.name}") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onMoveToContactGroup(group.groupId)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("移出分组（未分组）") },
                    leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onMoveToContactGroup(null)
                    },
                )
            }

            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (item.isGroup) "删除群组" else "删除助手", color = c.danger) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun CollabAvatarView(
    item: CollabSidebarItem,
    size: Dp,
) {
    if (item.isGroup) {
        val configs = item.membersPreview.map { it.botAvatar }
        CollabGroupAvatarView(
            avatars = configs,
            tagColorHex = item.tagColor,
            size = size,
            sessionActive = false,
        )
    } else {
        val m = item.member
        MemberBotAvatarView(
            config = m?.botAvatar ?: CollabBotAvatarConfig.derive(item.displayName),
            size = size,
            sessionActive = false,
            isSupervisor = m?.isSupervisor == true,
        )
    }
}

// ============================================================================
// CollaborationChatView (1:1 with CollaborationChatView.swift)
// ============================================================================

@Composable
private fun CollaborationChatView(
    state: UiState,
    model: CompanionModel,
    item: CollabSidebarItem,
    spaceSummary: CollabSpaceSummary?,
    workspaceId: String,
    directAssistants: List<CollabSidebarItem>,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onMutated: () -> Unit,
    onDeleteGroupDone: () -> Unit,
) {
    val c = VibexTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    val spaceId = item.targetSpaceId

    var messages by remember { mutableStateOf<List<CollabSpaceMessage>>(emptyList()) }
    var scheduled by remember { mutableStateOf<List<CollabScheduledMessage>>(emptyList()) }
    var cards by remember { mutableStateOf<List<CollabCard>>(emptyList()) }
    var spaceMembers by remember { mutableStateOf<List<CollabSpaceMember>>(emptyList()) }

    var inputText by remember { mutableStateOf("") }
    var selectedTargets by remember { mutableStateOf<List<String>>(emptyList()) }
    var replyingTo by remember { mutableStateOf<CollabSpaceMessage?>(null) }
    var delaySeconds by remember { mutableIntStateOf(0) }
    var showDelayMenu by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var headerReactionTrigger by remember { mutableIntStateOf(0) }

    val questionDrafts = remember { mutableStateMapOf<String, String>() }

    val agentMembers = remember(spaceMembers, item) {
        if (item.isGroup) {
            spaceMembers.filter { it.isAgent }
        } else {
            val m = item.member
            if (m != null) {
                listOf(
                    CollabSpaceMember(
                        memberId = m.memberId,
                        identityMemberId = m.identityMemberId,
                        nickname = m.name,
                        kind = "agent",
                        avatar = m.avatar,
                        color = m.color,
                        conversationId = m.conversationId,
                        isSupervisor = m.isSupervisor,
                    ),
                )
            } else emptyList()
        }
    }

    val isChatActive = remember(messages) {
        messages.any { it.streaming } ||
            messages.any { m -> m.receipts.any { it.isProcessing } }
    }

    fun loadChatState(silent: Boolean = false) {
        scope.launch {
            if (!silent && messages.isEmpty()) loading = true
            runCatching {
                model.fetchCollabChatState(spaceId, item.isGroup)
            }.onSuccess { (page, members) ->
                val prevCount = messages.size
                messages = page.messages
                scheduled = page.scheduled
                cards = page.cards
                if (item.isGroup) spaceMembers = members
                page.messages.lastOrNull()?.seq?.let { lastSeq ->
                    runCatching { model.markCollabThreadRead(item.threadId, lastSeq, true) }
                }
                if (page.messages.size > prevCount && page.messages.isNotEmpty()) {
                    runCatching { listState.animateScrollToItem(page.messages.lastIndex) }
                }
            }
            loading = false
        }
    }

    LaunchedEffect(spaceId) {
        loadChatState(silent = false)
        while (isActive) {
            delay(2_500L)
            loadChatState(silent = true)
        }
    }

    fun memberAvatarConfig(sender: String, isSystem: Boolean): CollabBotAvatarConfig {
        agentMembers.firstOrNull {
            it.resolvedIdentityId == sender || it.memberId == sender || it.nickname == sender
        }?.let { return it.botAvatar }
        item.membersPreview.firstOrNull {
            it.resolvedIdentityId == sender || it.memberId == sender || it.name == sender
        }?.let { return it.botAvatar }
        item.member?.takeIf {
            it.resolvedIdentityId == sender || it.memberId == sender || it.name == sender
        }?.let { return it.botAvatar }
        return if (isSystem) {
            CollabBotAvatarConfig(color = "#64748b", shape = BotAvatarShape.Hexagon, expression = BotAvatarExpression.Neutral)
        } else {
            CollabBotAvatarConfig.derive(sender)
        }
    }

    fun openMemberUnderlyingSession(memberIdentityId: String) {
        scope.launch {
            val convId = runCatching {
                model.resolveCollabMemberSession(memberIdentityId, item.threadId)
            }.getOrNull()
            if (!convId.isNullOrBlank()) {
                onOpenConversation(convId)
            }
        }
    }

    fun toggleTarget(target: String) {
        selectedTargets = if (target == "@all") {
            if (selectedTargets.contains("@all")) emptyList() else listOf("@all")
        } else {
            val next = selectedTargets.filterNot { it == "@all" }.toMutableList()
            if (next.contains(target)) next.remove(target) else next.add(target)
            next
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || sending) return
        sending = true
        val targets = selectedTargets
        val replyId = replyingTo?.id
        val delaySec = if (delaySeconds > 0) delaySeconds else null
        inputText = ""
        replyingTo = null
        delaySeconds = 0
        scope.launch {
            runCatching {
                model.postCollabMessage(
                    spaceId = spaceId,
                    content = text,
                    to = targets,
                    inReplyTo = replyId,
                    delaySeconds = delaySec,
                )
            }
            sending = false
            loadChatState(silent = true)
            onMutated()
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Chat Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgElevated.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = c.textPrimary, modifier = Modifier.size(20.dp))
            }

            // Center Interactive Header (tap plays bot emote!)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { headerReactionTrigger += 1 }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (item.isGroup) {
                    val configs = if (agentMembers.isNotEmpty()) {
                        agentMembers.map { it.botAvatar }
                    } else {
                        item.membersPreview.map { it.botAvatar }
                    }
                    CollabGroupAvatarView(
                        avatars = configs,
                        tagColorHex = item.tagColor,
                        size = 28.dp,
                        sessionActive = isChatActive,
                    )
                } else {
                    MemberBotAvatarView(
                        config = item.member?.botAvatar ?: CollabBotAvatarConfig.derive(item.displayName),
                        size = 28.dp,
                        sessionActive = isChatActive,
                        isSupervisor = item.member?.isSupervisor == true,
                        reactionTrigger = headerReactionTrigger,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.displayName,
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isGroup) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${maxOf(item.memberCount, agentMembers.size)})",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            IconButton(
                onClick = { showDetailSheet = true },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    if (item.isGroup) Icons.Outlined.Groups else Icons.Outlined.Tune,
                    contentDescription = "详情配置",
                    tint = c.textPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        HorizontalDivider(color = c.stroke, thickness = 0.5.dp)

        // Scheduled Messages Banner
        if (scheduled.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF97316).copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                scheduled.forEach { sched ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sched.content, color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("定时投递：${formatCollabRelativeTime(sched.deliverAt)}", color = c.textSecondary, fontSize = 10.sp)
                        }
                        Text(
                            text = "取消",
                            color = c.danger,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    runCatching {
                                        model.execCollabCommand("space.cancelScheduledMessage", mapOf("scheduleId" to sched.id))
                                    }
                                    loadChatState(silent = true)
                                }
                            },
                        )
                    }
                }
            }
        }

        // Message Stream
        Box(modifier = Modifier.weight(1f)) {
            if (loading && messages.isEmpty()) {
                CollaborationChatSkeletonView(modifier = Modifier.fillMaxSize())
            } else if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(32.dp))
                        Text("开始对话", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (item.isGroup) "输入消息并使用 @ 指定群内助手协作" else "直接发送消息与助手对话",
                            color = c.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val repliedMsg = msg.inReplyTo?.let { refId -> messages.firstOrNull { it.id == refId || it.rawId == refId } }
                        CollabMessageRow(
                            message = msg,
                            repliedMessage = repliedMsg,
                            isGroup = item.isGroup,
                            leadMemberId = spaceSummary?.leadMemberId,
                            avatarConfig = memberAvatarConfig(msg.envelope.sender, msg.envelope.isSystem),
                            agentMembers = agentMembers,
                            onAvatarTap = {
                                if (!msg.isFromUser && !msg.envelope.isSystem) {
                                    openMemberUnderlyingSession(msg.envelope.sender)
                                }
                            },
                            onReact = { emoji ->
                                scope.launch {
                                    runCatching { model.reactCollab(spaceId, msg.id, emoji) }
                                    loadChatState(silent = true)
                                }
                            },
                            onReply = { replyingTo = msg },
                            onCopy = { clipboard.setText(AnnotatedString(msg.envelope.content)) },
                            onCollect = {
                                scope.launch {
                                    val title = msg.envelope.content.take(40).ifBlank { "收藏消息" }
                                    runCatching {
                                        model.execCollabCommand(
                                            "space.addCollectionItem",
                                            mapOf(
                                                "spaceId" to spaceId,
                                                "kind" to "message",
                                                "name" to title,
                                                "textContent" to msg.envelope.content,
                                                "sourceMessageId" to msg.id,
                                            ),
                                        )
                                    }
                                }
                            },
                            onDistill = {
                                scope.launch {
                                    runCatching {
                                        model.execCollabCommand(
                                            "space.memory.distillMessage",
                                            mapOf("spaceId" to spaceId, "messageId" to msg.id),
                                        )
                                    }
                                }
                            },
                            onStopReceiptTurn = { receiptMemberId ->
                                scope.launch {
                                    runCatching { model.cancelCollabSpaceTurn(spaceId, receiptMemberId) }
                                    loadChatState(silent = true)
                                }
                            },
                        )
                    }
                }
            }
        }

        // Pending Cards Stack (Approvals & Questions)
        val pendingCards = remember(cards) { cards.filter { it.isPending } }
        if (pendingCards.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bgElevated)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pendingCards.forEach { card ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = c.card,
                        border = BorderStroke(0.5.dp, c.stroke),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    if (card.kind == "approval") Icons.Outlined.Shield else Icons.Outlined.HelpOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFF97316),
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = if (card.kind == "approval") "待审批请求" else "助手提问",
                                    color = c.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                val actionName = card.action ?: card.command
                                if (!actionName.isNullOrBlank()) {
                                    Text(
                                        text = actionName,
                                        color = c.textSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }

                            val promptText = card.question ?: card.reason ?: card.action
                            if (!promptText.isNullOrBlank()) {
                                Text(promptText, color = c.textPrimary, fontSize = 13.sp)
                            }

                            if (card.kind == "approval") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                runCatching { model.resolveCollabPendingCard(card.id, decision = "approved") }
                                                loadChatState(silent = true)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    ) {
                                        Text("批准", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { model.resolveCollabPendingCard(card.id, decision = "denied", declined = true) }
                                                loadChatState(silent = true)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    ) {
                                        Text("拒绝", color = c.danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                if (card.options.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        card.options.forEach { opt ->
                                            Surface(
                                                onClick = {
                                                    scope.launch {
                                                        runCatching { model.resolveCollabPendingCard(card.id, answer = opt) }
                                                        loadChatState(silent = true)
                                                    }
                                                },
                                                shape = RoundedCornerShape(50),
                                                color = Color(0xFF2563EB).copy(alpha = 0.12f),
                                            ) {
                                                Text(
                                                    text = opt,
                                                    color = Color(0xFF2563EB),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val draft = questionDrafts[card.id].orEmpty()
                                    OutlinedTextField(
                                        value = draft,
                                        onValueChange = { questionDrafts[card.id] = it },
                                        placeholder = { Text("输入回答…", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(
                                        onClick = {
                                            val ans = draft.trim()
                                            if (ans.isNotEmpty()) {
                                                questionDrafts.remove(card.id)
                                                scope.launch {
                                                    runCatching { model.resolveCollabPendingCard(card.id, answer = ans) }
                                                    loadChatState(silent = true)
                                                }
                                            }
                                        },
                                        enabled = draft.trim().isNotEmpty(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Text("发送", fontSize = 12.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { model.resolveCollabPendingCard(card.id, declined = true) }
                                                loadChatState(silent = true)
                                            }
                                        },
                                    ) {
                                        Text("跳过", color = c.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer Bar
        HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Reply Quote Banner
            if (replyingTo != null) {
                val rep = replyingTo!!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.bgElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                    Text(
                        text = "回复 ${rep.envelope.displaySender}：${rep.envelope.content}",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "取消回复",
                        tint = c.textSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { replyingTo = null },
                    )
                }
            }

            // Group @ Mention Selector Bar
            if (item.isGroup && agentMembers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val allSelected = selectedTargets.contains("@all")
                    Surface(
                        onClick = { toggleTarget("@all") },
                        shape = RoundedCornerShape(50),
                        color = if (allSelected) Color(0xFF2563EB) else c.bgElevated,
                    ) {
                        Text(
                            text = "@全体成员",
                            color = if (allSelected) Color.White else c.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }

                    agentMembers.forEach { member ->
                        val selected = selectedTargets.contains(member.resolvedIdentityId)
                        Surface(
                            onClick = { toggleTarget(member.resolvedIdentityId) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) Color(0xFF2563EB) else c.bgElevated,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                MemberBotAvatarView(
                                    config = member.botAvatar,
                                    size = 17.dp,
                                    sessionActive = false,
                                    isLead = spaceSummary?.leadMemberId == member.resolvedIdentityId,
                                )
                                Text(
                                    text = "@${member.nickname}",
                                    color = if (selected) Color.White else c.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            // Input Row
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.isGroup) {
                    Box {
                        Surface(
                            onClick = { showDelayMenu = true },
                            shape = RoundedCornerShape(50),
                            color = if (delaySeconds > 0) Color(0xFFF97316).copy(alpha = 0.14f) else c.bgElevated,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = "定时发送",
                                    tint = if (delaySeconds > 0) Color(0xFFF97316) else c.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                if (delaySeconds > 0) {
                                    Text(
                                        text = "${delaySeconds}s",
                                        color = Color(0xFFF97316),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = showDelayMenu,
                            onDismissRequest = { showDelayMenu = false },
                        ) {
                            listOf(
                                0 to "立即发送",
                                30 to "30 秒后",
                                60 to "1 分钟后",
                                300 to "5 分钟后",
                                900 to "15 分钟后",
                            ).forEach { (sec, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    trailingIcon = {
                                        if (delaySeconds == sec) {
                                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    onClick = {
                                        delaySeconds = sec
                                        showDelayMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.bgElevated)
                        .border(0.5.dp, c.stroke, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (item.isGroup) "发送消息到 ${item.displayName}…" else "发送消息给 ${item.displayName}…",
                            color = c.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                val canSend = inputText.trim().isNotEmpty() && !sending
                IconButton(
                    onClick = ::sendMessage,
                    enabled = canSend,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Color(0xFF2563EB) else c.textSecondary.copy(alpha = 0.25f)),
                ) {
                    Icon(
                        Icons.Outlined.ArrowUpward,
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (showDetailSheet) {
        CollaborationDetailSheet(
            state = state,
            model = model,
            item = item,
            workspaceId = workspaceId,
            spaceMembers = agentMembers,
            directAssistants = directAssistants,
            onDismiss = { showDetailSheet = false },
            onMutated = {
                loadChatState(silent = true)
                onMutated()
            },
            onOpenConversation = { convId ->
                showDetailSheet = false
                onOpenConversation(convId)
            },
            onDeleteGroupDone = {
                showDetailSheet = false
                onDeleteGroupDone()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollabMessageRow(
    message: CollabSpaceMessage,
    repliedMessage: CollabSpaceMessage?,
    isGroup: Boolean,
    leadMemberId: String?,
    avatarConfig: CollabBotAvatarConfig,
    agentMembers: List<CollabSpaceMember>,
    onAvatarTap: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onCollect: () -> Unit,
    onDistill: () -> Unit,
    onStopReceiptTurn: (String) -> Unit,
) {
    val c = VibexTheme.colors
    var showMenu by remember { mutableStateOf(false) }

    val senderColor = remember(avatarConfig.color) {
        parseCollabHexColor(avatarConfig.color)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.isFromUser) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 10.dp)
                    .clickable { onAvatarTap() },
            ) {
                MemberBotAvatarView(
                    config = avatarConfig,
                    size = 34.dp,
                    sessionActive = message.streaming,
                    isLead = leadMemberId == message.envelope.sender,
                )
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Sender Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!message.isFromUser) {
                    Text(
                        text = message.envelope.displaySender,
                        color = senderColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val recipients = message.envelope.recipientLabels
                if (recipients.isNotEmpty()) {
                    val label = recipients.joinToString(" ") { target ->
                        if (target == "all") "@全体"
                        else {
                            val m = agentMembers.firstOrNull { it.resolvedIdentityId == target || it.memberId == target || it.nickname == target }
                            "@${m?.nickname ?: target}"
                        }
                    }
                    Text(
                        text = label,
                        color = Color(0xFF2563EB),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF2563EB).copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                message.envelope.published?.takeIf { it.isNotBlank() }?.let { pub ->
                    Text(
                        text = formatCollabRelativeTime(pub),
                        color = c.textSecondary,
                        fontSize = 10.sp,
                    )
                }
                if (message.streaming) {
                    CircularProgressIndicator(
                        color = c.accent,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }

            // Replied-to Quote
            if (repliedMessage != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.bgElevated)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(11.dp))
                    Text(
                        text = "${repliedMessage.envelope.displaySender}：${repliedMessage.envelope.content}",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Message Bubble with Context Menu
            Box {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (message.isFromUser) Color(0xFF2563EB) else c.card,
                    border = if (message.isFromUser) null else BorderStroke(0.5.dp, c.stroke),
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true },
                    ),
                ) {
                    val displayContent = message.envelope.content.ifEmpty {
                        if (message.streaming) "思考中…" else ""
                    }
                    Text(
                        text = displayContent,
                        color = if (message.isFromUser) Color.White else c.textPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf("👍", "❤️", "🎉", "🚀", "👀", "💡").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 18.sp,
                                modifier = Modifier.clickable {
                                    showMenu = false
                                    onReact(emoji)
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("回复") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onReply()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("复制内容") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onCopy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("加入空间收藏") },
                        leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onCollect()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("提炼到空间记忆") },
                        leadingIcon = { Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onDistill()
                        },
                    )
                    if (!message.isFromUser && !message.envelope.isSystem) {
                        DropdownMenuItem(
                            text = { Text("打开该助手会话") },
                            leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onAvatarTap()
                            },
                        )
                    }
                }
            }

            // Grouped Emoji Reactions
            val grouped = message.groupedReactions
            if (grouped.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    grouped.forEach { reaction ->
                        Surface(
                            onClick = { onReact(reaction.emoji) },
                            shape = RoundedCornerShape(50),
                            color = c.bgElevated,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(reaction.emoji, fontSize = 12.sp)
                                Text("${reaction.count}", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Delivery Receipts Row
            if (message.receipts.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    message.receipts.forEach { receipt ->
                        val targetMemberId = receipt.conversationId ?: receipt.nickname.orEmpty()
                        ReceiptBadge(
                            receipt = receipt,
                            isGroup = isGroup,
                            onStop = { onStopReceiptTurn(targetMemberId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptBadge(
    receipt: CollabDeliveryReceipt,
    isGroup: Boolean,
    onStop: () -> Unit,
) {
    val c = VibexTheme.colors
    val dotColor = when (receipt.effectiveState) {
        "replied" -> Color(0xFF22C55E)
        "processing" -> Color(0xFF2563EB)
        "queued", "dispatched" -> Color(0xFFF97316)
        "failed", "timed_out", "cancelled" -> Color(0xFFEF4444)
        else -> c.textSecondary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.bgElevated)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = "${receipt.nickname ?: "助手"} · ${receipt.stateLabel}",
            color = c.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        if (isGroup && receipt.isProcessing) {
            Icon(
                Icons.Outlined.StopCircle,
                contentDescription = "停止",
                tint = Color(0xFFEF4444),
                modifier = Modifier
                    .size(13.dp)
                    .clickable { onStop() },
            )
        }
    }
}

// ============================================================================
// CollaborationDetailSheet (1:1 with CollaborationDetailSheet.swift)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollaborationDetailSheet(
    state: UiState,
    model: CompanionModel,
    item: CollabSidebarItem,
    workspaceId: String,
    spaceMembers: List<CollabSpaceMember>,
    directAssistants: List<CollabSidebarItem>,
    onDismiss: () -> Unit,
    onMutated: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteGroupDone: () -> Unit,
) {
    val c = VibexTheme.colors
    val scope = rememberCoroutineScope()

    val spaceId = item.targetSpaceId

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 成员/定制, 1: 记忆与收藏, 2: 设置/会话
    var memorySubTab by remember { mutableIntStateOf(0) } // 0: 记忆, 1: 主题, 2: 收藏

    var liveMembers by remember { mutableStateOf(spaceMembers) }
    var leadMemberId by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf(item.displayName) }
    var groupTagColor by remember { mutableStateOf(item.tagColor.orEmpty()) }

    var memberName by remember { mutableStateOf(item.displayName) }
    var memberPersona by remember { mutableStateOf("") }
    var memberAvatar by remember {
        mutableStateOf(item.member?.botAvatar ?: CollabBotAvatarConfig.derive(item.displayName))
    }
    var memberIsSupervisor by remember { mutableStateOf(item.member?.isSupervisor ?: false) }
    var memberWriteAccessMode by remember { mutableStateOf("write_isolated") }
    var savingMember by remember { mutableStateOf(false) }

    var memoryView by remember { mutableStateOf(CollabMemoryView()) }
    var collectionItems by remember { mutableStateOf<List<CollabCollectionItem>>(emptyList()) }
    var newMemoryText by remember { mutableStateOf("") }
    var newTopicSlug by remember { mutableStateOf("") }
    var newTopicTitle by remember { mutableStateOf("") }
    var newTopicContent by remember { mutableStateOf("") }
    var newCollectionTitle by remember { mutableStateOf("") }
    var newCollectionContent by remember { mutableStateOf("") }

    var showCreateAndAddSheet by remember { mutableStateOf(false) }

    fun loadAll() {
        scope.launch {
            runCatching {
                model.fetchCollabDetailData(
                    workspaceId = workspaceId,
                    spaceId = spaceId,
                    isGroup = item.isGroup,
                    memberIdentityId = item.member?.resolvedIdentityId,
                )
            }.onSuccess { bundle ->
                if (item.isGroup) {
                    liveMembers = bundle.members
                    leadMemberId = bundle.leadMemberId
                } else if (bundle.memberConfig != null) {
                    val cfg = bundle.memberConfig
                    memberPersona = cfg.personaPrompt.orEmpty()
                }
                memoryView = bundle.memory
                collectionItems = bundle.collection
            }
        }
    }

    LaunchedEffect(spaceId) {
        loadAll()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (item.isGroup) "群组设置与记忆" else "助手定制与记忆",
                    color = c.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("完成", fontWeight = FontWeight.SemiBold)
                }
            }

            // Segment Picker
            val tabs = if (item.isGroup) {
                listOf("成员", "记忆与收藏", "设置")
            } else {
                listOf("定制", "记忆与收藏")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.segmentedTrack)
                    .padding(3.dp),
            ) {
                tabs.forEachIndexed { idx, label ->
                    val selected = selectedTab == idx
                    Text(
                        text = label,
                        color = if (selected) c.textPrimary else c.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (selected) Modifier.shadow(1.5.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black.copy(alpha = 0.08f))
                                else Modifier,
                            )
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (selected) c.segmentedThumb else Color.Transparent)
                            .clickable { selectedTab = idx }
                            .padding(vertical = 6.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (selectedTab) {
                    0 -> {
                        if (item.isGroup) {
                            // Group Members Tab
                            CollabSectionCard(title = "添加成员") {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        onClick = { showCreateAndAddSheet = true },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF2563EB).copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                            Text("新建助手并拉入本群", color = Color(0xFF2563EB), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    val existingIds = liveMembers.map { it.resolvedIdentityId }.toSet()
                                    val available = directAssistants.filter { d ->
                                        val mid = d.member?.resolvedIdentityId
                                        !mid.isNullOrBlank() && !existingIds.contains(mid)
                                    }
                                    available.forEach { candidate ->
                                        val m = candidate.member ?: return@forEach
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        val convId = m.conversationId
                                                            ?: model.resolveCollabMemberSession(m.resolvedIdentityId, candidate.threadId)
                                                            ?: return@launch
                                                        runCatching {
                                                            model.addCollabMemberToSpace(spaceId, convId, candidate.displayName)
                                                        }
                                                        loadAll()
                                                        onMutated()
                                                    }
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            MemberBotAvatarView(
                                                config = m.botAvatar,
                                                size = 28.dp,
                                            )
                                            Text("拉入 ${candidate.displayName}", color = c.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                            Icon(Icons.Outlined.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            CollabSectionCard(title = "群内助手 (${liveMembers.size})") {
                                Column {
                                    liveMembers.forEachIndexed { idx, member ->
                                        if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                        var showMemberMenu by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            MemberBotAvatarView(
                                                config = member.botAvatar,
                                                size = 36.dp,
                                                isLead = leadMemberId == member.resolvedIdentityId,
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(member.nickname, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                    if (leadMemberId == member.resolvedIdentityId) {
                                                        Text(
                                                            text = "群负责人",
                                                            color = Color(0xFF2563EB),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(50))
                                                                .background(Color(0xFF2563EB).copy(alpha = 0.12f))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            Box {
                                                IconButton(onClick = { showMemberMenu = true }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                                }
                                                DropdownMenu(
                                                    expanded = showMemberMenu,
                                                    onDismissRequest = { showMemberMenu = false },
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("查看本群会话") },
                                                        leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                        onClick = {
                                                            showMemberMenu = false
                                                            scope.launch {
                                                                val convId = member.conversationId
                                                                    ?: model.resolveCollabMemberSession(member.resolvedIdentityId, item.threadId)
                                                                if (!convId.isNullOrBlank()) {
                                                                    onOpenConversation(convId)
                                                                }
                                                            }
                                                        },
                                                    )
                                                    val isLead = leadMemberId == member.resolvedIdentityId
                                                    DropdownMenuItem(
                                                        text = { Text(if (isLead) "取消群负责人" else "设为群负责人") },
                                                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                        onClick = {
                                                            showMemberMenu = false
                                                            val nextLead = if (isLead) null else member.resolvedIdentityId
                                                            scope.launch {
                                                                runCatching {
                                                                    model.execCollabCommand(
                                                                        "space.setLead",
                                                                        mapOf("spaceId" to spaceId, "memberId" to nextLead),
                                                                    )
                                                                }
                                                                leadMemberId = nextLead.orEmpty()
                                                                onMutated()
                                                            }
                                                        },
                                                    )
                                                    HorizontalDivider()
                                                    DropdownMenuItem(
                                                        text = { Text("移出本群", color = c.danger) },
                                                        leadingIcon = { Icon(Icons.Outlined.PersonRemove, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp)) },
                                                        onClick = {
                                                            showMemberMenu = false
                                                            scope.launch {
                                                                runCatching {
                                                                    model.execCollabCommand(
                                                                        "space.removeMember",
                                                                        mapOf("spaceId" to spaceId, "memberId" to member.resolvedIdentityId),
                                                                    )
                                                                }
                                                                loadAll()
                                                                onMutated()
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Direct Assistant Customization Tab
                            CollabSectionCard(title = "基本信息与形象") {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedTextField(
                                        value = memberName,
                                        onValueChange = { memberName = it },
                                        label = { Text("助手名称") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    MemberBotAvatarPicker(
                                        value = memberAvatar,
                                        onValueChange = { memberAvatar = it },
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("设为主管（置顶）", color = c.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = memberIsSupervisor,
                                            onCheckedChange = { memberIsSupervisor = it },
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("工作区写入模式", color = c.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        val isIsolated = memberWriteAccessMode == "write_isolated"
                                        TextButton(
                                            onClick = {
                                                memberWriteAccessMode = if (isIsolated) "write_serialized" else "write_isolated"
                                            },
                                        ) {
                                            Text(if (isIsolated) "独立隔离" else "串行写入", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            CollabSectionCard(title = "人设提示词 (Persona Prompt)") {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedTextField(
                                        value = memberPersona,
                                        onValueChange = { memberPersona = it },
                                        placeholder = { Text("定义助手的职责、性格与专长…") },
                                        minLines = 3,
                                        maxLines = 6,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(
                                        onClick = {
                                            val mid = item.member?.resolvedIdentityId ?: return@Button
                                            savingMember = true
                                            scope.launch {
                                                runCatching {
                                                    model.execCollabCommand(
                                                        "member.update",
                                                        mapOf(
                                                            "memberId" to mid,
                                                            "name" to memberName.trim(),
                                                            "personaPrompt" to memberPersona.trim().ifEmpty { null },
                                                            "color" to memberAvatar.color,
                                                            "avatar" to memberAvatar.jsonString,
                                                            "isSupervisor" to memberIsSupervisor,
                                                            "writeAccessMode" to memberWriteAccessMode,
                                                        ),
                                                    )
                                                }
                                                savingMember = false
                                                onMutated()
                                            }
                                        },
                                        enabled = !savingMember && memberName.trim().isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (savingMember) "保存中…" else "保存配置")
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Memory & Collection Tab
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.segmentedTrack)
                                .padding(2.dp),
                        ) {
                            listOf("记忆", "主题", "收藏").forEachIndexed { idx, label ->
                                val sel = memorySubTab == idx
                                Text(
                                    text = label,
                                    color = if (sel) c.textPrimary else c.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(
                                            if (sel) Modifier.shadow(1.5.dp, RoundedCornerShape(6.dp), ambientColor = Color.Black.copy(alpha = 0.08f))
                                            else Modifier,
                                        )
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) c.segmentedThumb else Color.Transparent)
                                        .clickable { memorySubTab = idx }
                                        .padding(vertical = 5.dp),
                                )
                            }
                        }

                        when (memorySubTab) {
                            0 -> {
                                CollabSectionCard(title = "新增空间记忆") {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = newMemoryText,
                                            onValueChange = { newMemoryText = it },
                                            placeholder = { Text("输入需要长期记住的事实…", fontSize = 13.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(
                                            onClick = {
                                                val txt = newMemoryText.trim()
                                                if (txt.isNotEmpty()) {
                                                    newMemoryText = ""
                                                    scope.launch {
                                                        runCatching {
                                                            model.execCollabCommand(
                                                                "space.memory.add",
                                                                mapOf(
                                                                    "spaceId" to spaceId,
                                                                    "scope" to "thread",
                                                                    "content" to txt,
                                                                ),
                                                            )
                                                        }
                                                        loadAll()
                                                    }
                                                }
                                            },
                                            enabled = newMemoryText.trim().isNotEmpty(),
                                        ) { Text("添加") }
                                    }
                                }

                                CollabSectionCard(title = "已存记忆 (${memoryView.facts.size})") {
                                    if (memoryView.facts.isEmpty()) {
                                        Text("暂无空间记忆", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                                    } else {
                                        Column {
                                            memoryView.facts.forEachIndexed { idx, fact ->
                                                if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(fact.content, color = c.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                runCatching {
                                                                    model.execCollabCommand(
                                                                        "space.memory.delete",
                                                                        mapOf("spaceId" to spaceId, "factId" to fact.id),
                                                                    )
                                                                }
                                                                loadAll()
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                CollabSectionCard(title = "写入知识主题") {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = newTopicSlug,
                                            onValueChange = { newTopicSlug = it },
                                            label = { Text("Slug（如 architecture）") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value = newTopicTitle,
                                            onValueChange = { newTopicTitle = it },
                                            label = { Text("标题") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value = newTopicContent,
                                            onValueChange = { newTopicContent = it },
                                            label = { Text("Markdown 内容") },
                                            minLines = 2,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Button(
                                            onClick = {
                                                val slug = newTopicSlug.trim()
                                                val content = newTopicContent.trim()
                                                if (slug.isNotEmpty() && content.isNotEmpty()) {
                                                    val title = newTopicTitle.trim().ifEmpty { slug }
                                                    newTopicSlug = ""
                                                    newTopicTitle = ""
                                                    newTopicContent = ""
                                                    scope.launch {
                                                        runCatching {
                                                            model.execCollabCommand(
                                                                "space.memory.writeTopic",
                                                                mapOf(
                                                                    "spaceId" to spaceId,
                                                                    "scope" to "thread",
                                                                    "topic" to slug,
                                                                    "title" to title,
                                                                    "content" to content,
                                                                ),
                                                            )
                                                        }
                                                        loadAll()
                                                    }
                                                }
                                            },
                                            enabled = newTopicSlug.trim().isNotEmpty() && newTopicContent.trim().isNotEmpty(),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("保存主题") }
                                    }
                                }

                                CollabSectionCard(title = "知识主题 (${memoryView.topics.size})") {
                                    if (memoryView.topics.isEmpty()) {
                                        Text("暂无知识主题", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                                    } else {
                                        Column {
                                            memoryView.topics.forEachIndexed { idx, topic ->
                                                if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(topic.title, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                        val body = topic.preview ?: topic.content.orEmpty()
                                                        if (body.isNotEmpty()) {
                                                            Text(body, color = c.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                runCatching {
                                                                    model.execCollabCommand(
                                                                        "space.memory.deleteTopic",
                                                                        mapOf("spaceId" to spaceId, "scope" to "thread", "topic" to topic.topic),
                                                                    )
                                                                }
                                                                loadAll()
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> {
                                CollabSectionCard(title = "添加收藏条目") {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = newCollectionTitle,
                                            onValueChange = { newCollectionTitle = it },
                                            label = { Text("标题") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value = newCollectionContent,
                                            onValueChange = { newCollectionContent = it },
                                            label = { Text("内容或链接") },
                                            minLines = 2,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Button(
                                            onClick = {
                                                val title = newCollectionTitle.trim()
                                                val content = newCollectionContent.trim()
                                                if (title.isNotEmpty() && content.isNotEmpty()) {
                                                    newCollectionTitle = ""
                                                    newCollectionContent = ""
                                                    scope.launch {
                                                        runCatching {
                                                            model.execCollabCommand(
                                                                "space.addCollectionItem",
                                                                mapOf(
                                                                    "spaceId" to spaceId,
                                                                    "kind" to "text",
                                                                    "name" to title,
                                                                    "textContent" to content,
                                                                ),
                                                            )
                                                        }
                                                        loadAll()
                                                    }
                                                }
                                            },
                                            enabled = newCollectionTitle.trim().isNotEmpty() && newCollectionContent.trim().isNotEmpty(),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("添加收藏") }
                                    }
                                }

                                CollabSectionCard(title = "空间收藏 (${collectionItems.size})") {
                                    if (collectionItems.isEmpty()) {
                                        Text("暂无收藏条目", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                                    } else {
                                        Column {
                                            collectionItems.forEachIndexed { idx, col ->
                                                if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(col.name, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                        val body = col.textContent ?: col.note
                                                        if (body.isNotEmpty()) {
                                                            Text(body, color = c.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                runCatching {
                                                                    model.execCollabCommand(
                                                                        "space.removeCollectionItem",
                                                                        mapOf("itemId" to col.id),
                                                                    )
                                                                }
                                                                loadAll()
                                                            }
                                                        },
                                                        modifier = Modifier.size(28.dp),
                                                    ) {
                                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (item.isGroup) {
                            CollabSectionCard(title = "群组信息") {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedTextField(
                                        value = groupName,
                                        onValueChange = { groupName = it },
                                        label = { Text("群组名称") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text("群标签颜色", color = c.textPrimary, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        CollabTagPalette.presets.forEach { tag ->
                                            val sel = groupTagColor == tag.id
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(tag.color)
                                                    .border(if (sel) 2.dp else 0.dp, c.textPrimary, CircleShape)
                                                    .clickable { groupTagColor = tag.id },
                                            )
                                        }
                                        TextButton(onClick = { groupTagColor = "" }) {
                                            Text("清除", fontSize = 12.sp)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    model.execCollabCommand(
                                                        "space.update",
                                                        mapOf(
                                                            "spaceId" to spaceId,
                                                            "name" to groupName.trim(),
                                                            "tagColor" to groupTagColor.ifEmpty { null },
                                                        ),
                                                    )
                                                }
                                                onMutated()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("保存修改") }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    model.execCollabCommand("space.delete", mapOf("spaceId" to spaceId))
                                                }
                                                onDeleteGroupDone()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("删除此群组", color = c.danger)
                                    }
                                }
                            }
                        } else {
                            CollabSectionCard(title = "底层对话会话") {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "可进入该助手的底层 Agent 会话查看完整工具调用、终端输出与代码变更。",
                                        color = c.textSecondary,
                                        fontSize = 13.sp,
                                    )
                                    Button(
                                        onClick = {
                                            val mid = item.member?.resolvedIdentityId ?: return@Button
                                            scope.launch {
                                                val convId = model.resolveCollabMemberSession(mid, item.threadId)
                                                if (!convId.isNullOrBlank()) {
                                                    onOpenConversation(convId)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("打开底层对话详情")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateAndAddSheet) {
        CreateAssistantSheet(
            state = state,
            model = model,
            workspaceId = workspaceId,
            targetSpaceId = spaceId,
            onDismiss = { showCreateAndAddSheet = false },
            onCreated = {
                showCreateAndAddSheet = false
                loadAll()
                onMutated()
            },
        )
    }
}

// ============================================================================
// CreateAssistantSheet & RolesAndTeamsSheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAssistantSheet(
    state: UiState,
    model: CompanionModel,
    workspaceId: String,
    targetSpaceId: String?,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val c = VibexTheme.colors
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var agentId by remember {
        mutableStateOf(
            state.catalog?.agents?.firstOrNull { it.usable }?.id
                ?: state.catalog?.agents?.firstOrNull()?.id
                ?: "claude_code",
        )
    }
    var selectedRoleId by remember { mutableStateOf("") }
    var personaPrompt by remember { mutableStateOf("") }
    var avatarConfig by remember { mutableStateOf(CollabBotAvatarConfig.random()) }
    var isSupervisor by remember { mutableStateOf(false) }
    var roles by remember { mutableStateOf<List<SubagentRoleItem>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(workspaceId) {
        if (workspaceId.isNotEmpty()) {
            runCatching { model.fetchRolesAndTeams(workspaceId) }.onSuccess { (r, _) ->
                roles = r
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (targetSpaceId == null) "新建助手" else "新建群内助手",
                color = c.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("助手名称（如：前端架构师）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MemberBotAvatarPicker(
                value = avatarConfig,
                onValueChange = { avatarConfig = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("设为主管（置顶）", color = c.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = isSupervisor, onCheckedChange = { isSupervisor = it })
            }

            if (roles.isNotEmpty()) {
                Text("角色模板（可选）", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        onClick = { selectedRoleId = "" },
                        shape = RoundedCornerShape(50),
                        color = if (selectedRoleId.isEmpty()) Color(0xFF2563EB) else c.bgElevated,
                    ) {
                        Text(
                            text = "自定义",
                            color = if (selectedRoleId.isEmpty()) Color.White else c.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    roles.forEach { role ->
                        val sel = selectedRoleId == role.id
                        val roleDesc = role.summary
                        Surface(
                            onClick = {
                                selectedRoleId = role.id
                                if (name.trim().isEmpty()) name = role.name
                                if (personaPrompt.trim().isEmpty() && roleDesc.isNotBlank()) {
                                    personaPrompt = roleDesc
                                }
                            },
                            shape = RoundedCornerShape(50),
                            color = if (sel) Color(0xFF2563EB) else c.bgElevated,
                        ) {
                            Text(
                                text = role.name,
                                color = if (sel) Color.White else c.textPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            val agents = state.catalog?.agents.orEmpty()
            if (agents.isNotEmpty()) {
                Text("执行 Agent", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    agents.forEach { ag ->
                        val sel = agentId == ag.id
                        Surface(
                            onClick = { agentId = ag.id },
                            shape = RoundedCornerShape(50),
                            color = if (sel) Color(0xFF2563EB).copy(alpha = 0.14f) else c.bgElevated,
                            border = BorderStroke(if (sel) 1.dp else 0.5.dp, if (sel) Color(0xFF2563EB) else c.stroke),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                AgentAvatar(agentId = ag.id, size = 14.dp)
                                Text(
                                    text = ag.displayName.ifBlank { agentDisplayName(ag.id) },
                                    color = if (sel) Color(0xFF2563EB) else c.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = personaPrompt,
                onValueChange = { personaPrompt = it },
                label = { Text("人设提示词 (Persona Prompt)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty() || creating) return@Button
                    creating = true
                    scope.launch {
                        runCatching {
                            model.createCollabAssistantFull(
                                workspaceId = workspaceId,
                                name = trimmed,
                                agentId = agentId.ifEmpty { null },
                                roleId = selectedRoleId.ifEmpty { null },
                                personaPrompt = personaPrompt.trim().ifEmpty { null },
                                avatar = avatarConfig,
                                isSupervisor = isSupervisor,
                                targetSpaceId = targetSpaceId,
                            )
                        }
                        creating = false
                        onCreated()
                    }
                },
                enabled = name.trim().isNotEmpty() && !creating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (creating) "创建中…" else "创建")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RolesAndTeamsSheet(
    model: CompanionModel,
    workspaceId: String,
    directAssistants: List<CollabSidebarItem>,
    onDismiss: () -> Unit,
    onMutated: () -> Unit,
) {
    val c = VibexTheme.colors
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) } // 0: 角色模板, 1: 团队
    var roles by remember { mutableStateOf<List<SubagentRoleItem>>(emptyList()) }
    var teams by remember { mutableStateOf<List<CollabTeam>>(emptyList()) }

    var roleName by remember { mutableStateOf("") }
    var roleDesc by remember { mutableStateOf("") }
    var rolePrompt by remember { mutableStateOf("") }

    var teamName by remember { mutableStateOf("") }
    var teamDesc by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun load() {
        if (workspaceId.isEmpty()) return
        scope.launch {
            runCatching { model.fetchRolesAndTeams(workspaceId) }.onSuccess { (r, t) ->
                roles = r
                teams = t
            }
        }
    }

    LaunchedEffect(workspaceId) { load() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("角色与团队", color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("完成") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.segmentedTrack)
                    .padding(3.dp),
            ) {
                listOf("角色模板", "团队").forEachIndexed { idx, label ->
                    val sel = tab == idx
                    Text(
                        text = label,
                        color = if (sel) c.textPrimary else c.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (sel) Modifier.shadow(1.5.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black.copy(alpha = 0.08f))
                                else Modifier,
                            )
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (sel) c.segmentedThumb else Color.Transparent)
                            .clickable { tab = idx }
                            .padding(vertical = 6.dp),
                    )
                }
            }

            if (tab == 0) {
                CollabSectionCard(title = "新建角色模板") {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = roleName,
                            onValueChange = { roleName = it },
                            label = { Text("角色名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = roleDesc,
                            onValueChange = { roleDesc = it },
                            label = { Text("简短描述") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = rolePrompt,
                            onValueChange = { rolePrompt = it },
                            label = { Text("System Prompt") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val rn = roleName.trim()
                                val rp = rolePrompt.trim()
                                if (rn.isNotEmpty() && rp.isNotEmpty()) {
                                    val rd = roleDesc.trim()
                                    roleName = ""
                                    roleDesc = ""
                                    rolePrompt = ""
                                    scope.launch {
                                        runCatching {
                                            model.execCollabCommand(
                                                "role.create",
                                                mapOf(
                                                    "workspaceId" to workspaceId,
                                                    "name" to rn,
                                                    "description" to rd.ifEmpty { null },
                                                    "systemPrompt" to rp,
                                                ),
                                            )
                                        }
                                        load()
                                        onMutated()
                                    }
                                }
                            },
                            enabled = roleName.trim().isNotEmpty() && rolePrompt.trim().isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("创建角色") }
                    }
                }

                CollabSectionCard(title = "已有角色 (${roles.size})") {
                    if (roles.isEmpty()) {
                        Text("暂无角色模板", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                    } else {
                        Column {
                            roles.forEachIndexed { idx, r ->
                                if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                val rDesc = r.summary
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(r.name, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        if (rDesc.isNotBlank()) {
                                            Text(rDesc, color = c.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (!r.isBuiltin) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        model.execCollabCommand("role.delete", mapOf("roleId" to r.id))
                                                    }
                                                    load()
                                                    onMutated()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                CollabSectionCard(title = "新建团队") {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = teamName,
                            onValueChange = { teamName = it },
                            label = { Text("团队名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = teamDesc,
                            onValueChange = { teamDesc = it },
                            label = { Text("团队说明（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        directAssistants.forEach { d ->
                            val mid = d.member?.resolvedIdentityId ?: return@forEach
                            val checked = selectedMemberIds.contains(mid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMemberIds = if (checked) selectedMemberIds - mid else selectedMemberIds + mid
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    if (checked) Icons.Outlined.CheckCircle else Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = if (checked) Color(0xFF2563EB) else c.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(d.displayName, color = c.textPrimary, fontSize = 13.sp)
                            }
                        }
                        Button(
                            onClick = {
                                val tn = teamName.trim()
                                if (tn.isNotEmpty()) {
                                    val td = teamDesc.trim()
                                    val mids = selectedMemberIds.toList()
                                    teamName = ""
                                    teamDesc = ""
                                    selectedMemberIds = emptySet()
                                    scope.launch {
                                        runCatching {
                                            model.execCollabCommand(
                                                "team.create",
                                                mapOf(
                                                    "workspaceId" to workspaceId,
                                                    "name" to tn,
                                                    "summary" to td.ifEmpty { null },
                                                    "memberIds" to mids,
                                                ),
                                            )
                                        }
                                        load()
                                        onMutated()
                                    }
                                }
                            },
                            enabled = teamName.trim().isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("创建团队") }
                    }
                }

                CollabSectionCard(title = "已有团队 (${teams.size})") {
                    if (teams.isEmpty()) {
                        Text("暂无团队", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                    } else {
                        Column {
                            teams.forEachIndexed { idx, t ->
                                if (idx > 0) HorizontalDivider(color = c.stroke, thickness = 0.5.dp)
                                val tSummary = t.summary
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(t.name, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        if (!tSummary.isNullOrBlank()) {
                                            Text(tSummary, color = c.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    model.execCollabCommand("team.delete", mapOf("teamId" to t.id))
                                                }
                                                load()
                                                onMutated()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCollabRelativeTime(iso: String): String {
    val trimmed = iso.trim()
    if (trimmed.isEmpty()) return ""
    val millis = runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
        ?: trimmed.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000L else it }
        ?: return ""
    val diffSec = ((System.currentTimeMillis() - millis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "刚刚"
        diffSec < 3600 -> "${diffSec / 60}分钟前"
        diffSec < 86_400 -> "${diffSec / 3600}小时前"
        else -> "${diffSec / 86_400}天前"
    }
}
