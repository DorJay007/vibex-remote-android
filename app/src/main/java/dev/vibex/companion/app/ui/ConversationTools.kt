package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.DiffHunk
import dev.vibex.companion.DiffLine
import dev.vibex.companion.FileChangeItem
import dev.vibex.companion.FileDiff
import dev.vibex.companion.UnifiedDiff
import dev.vibex.companion.WorkspaceEntry
import dev.vibex.companion.WorkspaceFile
import dev.vibex.companion.WorkspaceTree
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

/**
 * 1:1 port of iOS `FileBrowserView` + `DirectoryView` + `FileContentView` (`ConversationTools.swift`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserSheet(
    workspaceId: String,
    title: String,
    model: CompanionModel,
    onDismiss: () -> Unit,
) {
    val colors = VibexTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dirStack by remember { mutableStateOf(listOf("" to title.ifBlank { "项目文件" })) }
    var viewingFile by remember { mutableStateOf<WorkspaceEntry?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
        ) {
            val activeFile = viewingFile
            if (activeFile != null) {
                ToolSheetTopBar(
                    title = activeFile.name,
                    canGoBack = true,
                    onBack = { viewingFile = null },
                    onDone = onDismiss,
                )
                HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)
                FileContentView(
                    workspaceId = workspaceId,
                    path = activeFile.path,
                    model = model,
                )
            } else {
                val currentDir = dirStack.last()
                ToolSheetTopBar(
                    title = currentDir.second,
                    canGoBack = dirStack.size > 1,
                    onBack = {
                        if (dirStack.size > 1) {
                            dirStack = dirStack.dropLast(1)
                        }
                    },
                    onDone = onDismiss,
                )
                HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)
                DirectoryView(
                    workspaceId = workspaceId,
                    path = currentDir.first,
                    model = model,
                    onOpenDir = { entry ->
                        dirStack = dirStack + (entry.path to entry.name)
                    },
                    onOpenFile = { entry ->
                        viewingFile = entry
                    },
                )
            }
        }
    }
}

@Composable
fun DirectoryView(
    workspaceId: String,
    path: String,
    model: CompanionModel,
    onOpenDir: (WorkspaceEntry) -> Unit,
    onOpenFile: (WorkspaceEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    var tree by remember(workspaceId, path) { mutableStateOf<WorkspaceTree?>(null) }
    var error by remember(workspaceId, path) { mutableStateOf<String?>(null) }

    LaunchedEffect(workspaceId, path) {
        tree = null
        error = null
        model.fetchWorkspaceTree(workspaceId, path) { result ->
            result.onSuccess { tree = it }
                .onFailure { error = it.message ?: "无法读取目录" }
        }
    }

    val currentTree = tree
    when {
        currentTree != null -> {
            if (currentTree.entries.isEmpty()) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("空目录", fontSize = 14.sp, color = colors.textSecondary)
                }
            } else {
                val dirs = remember(currentTree.entries) { currentTree.entries.filter { it.directory } }
                val files = remember(currentTree.entries) { currentTree.entries.filter { !it.directory } }
                val sections = remember(dirs, files) {
                    buildList {
                        if (dirs.isNotEmpty()) {
                            add(
                                BranchedSection(
                                    id = "dirs",
                                    title = "文件夹",
                                    icon = Icons.Rounded.Folder,
                                    count = dirs.size,
                                    items = dirs.map { entry ->
                                        BranchedItem(
                                            id = entry.path,
                                            label = entry.name,
                                            action = { onOpenDir(entry) },
                                        )
                                    },
                                ),
                            )
                        }
                        if (files.isNotEmpty()) {
                            add(
                                BranchedSection(
                                    id = "files",
                                    title = "文件",
                                    icon = Icons.Rounded.Description,
                                    count = files.size,
                                    items = files.map { entry ->
                                        BranchedItem(
                                            id = entry.path,
                                            label = entry.name,
                                            action = { onOpenFile(entry) },
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }

                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    BranchedMenu(sections = sections)
                }
            }
        }
        error != null -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(error!!, fontSize = 13.5.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState(label = "正在读取目录")
            }
        }
    }
}

@Composable
fun FileContentView(
    workspaceId: String,
    path: String,
    model: CompanionModel,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    var file by remember(workspaceId, path) { mutableStateOf<WorkspaceFile?>(null) }
    var error by remember(workspaceId, path) { mutableStateOf<String?>(null) }

    LaunchedEffect(workspaceId, path) {
        file = null
        error = null
        model.fetchWorkspaceFile(workspaceId, path) { result ->
            result.onSuccess { file = it }
                .onFailure { error = it.message ?: "无法读取文件" }
        }
    }

    val currentFile = file
    when {
        currentFile != null -> {
            if (currentFile.binary) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "二进制文件（${currentFile.size} 字节）",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                    )
                }
            } else {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (currentFile.truncated) {
                        Text(
                            text = "文件较大，已截断至前 256 KB",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    CodeBlockView(
                        code = currentFile.content.orEmpty(),
                        filename = currentFile.path,
                        maxHeight = null,
                    )
                }
            }
        }
        error != null -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(error!!, fontSize = 13.5.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState(label = "正在读取文件")
            }
        }
    }
}

/**
 * 1:1 port of iOS `DiffListView` (`ConversationTools.swift`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffListSheet(
    workspaceId: String,
    conversationId: String = "",
    model: CompanionModel,
    onDismiss: () -> Unit,
) {
    val colors = VibexTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var files by remember(workspaceId) { mutableStateOf<List<FileDiff>?>(null) }
    var error by remember(workspaceId) { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<FileDiff?>(null) }

    LaunchedEffect(workspaceId, conversationId) {
        files = null
        error = null
        model.fetchWorkspaceDiff(workspaceId, conversationId) { result ->
            result.onSuccess { files = it }
                .onFailure { error = it.message ?: "无法读取代码变更" }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
        ) {
            val activeFile = selectedFile
            if (activeFile != null) {
                ToolSheetTopBar(
                    title = activeFile.path.substringAfterLast('/').ifBlank { activeFile.path },
                    canGoBack = true,
                    onBack = { selectedFile = null },
                    onDone = onDismiss,
                )
                HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)
                DiffFileView(
                    file = activeFile,
                    workspaceId = workspaceId,
                    model = model,
                )
            } else {
                ToolSheetTopBar(
                    title = "代码变更",
                    canGoBack = false,
                    onBack = {},
                    onDone = onDismiss,
                )
                HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)

                val currentFiles = files
                when {
                    currentFiles != null -> {
                        if (currentFiles.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 56.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(
                                    text = "工作区干净",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary,
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                currentFiles.forEach { file ->
                                    val rowShape = RoundedCornerShape(12.dp)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(rowShape)
                                            .background(colors.card)
                                            .clickable { selectedFile = file }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ChangeBadge(change = file.change)
                                        Text(
                                            text = file.path,
                                            style = TextStyle(
                                                fontFamily = PlexMono,
                                                fontSize = 13.sp,
                                                color = colors.textPrimary,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (file.additions > 0) {
                                            Text(
                                                text = "+${file.additions}",
                                                style = TextStyle(
                                                    fontFamily = PlexMono,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = colors.pass,
                                                ),
                                            )
                                        }
                                        if (file.deletions > 0) {
                                            Text(
                                                text = "−${file.deletions}",
                                                style = TextStyle(
                                                    fontFamily = PlexMono,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = colors.danger,
                                                ),
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = colors.textTertiary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(error!!, fontSize = 13.5.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingState(label = "正在计算变更")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1:1 port of iOS `FileDiffSheet` (`ConversationTools.swift`).
 * Opened when tapping a file-change chip in `ToolChipsView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDiffSheet(
    change: FileChangeItem,
    workspaceId: String,
    conversationId: String = "",
    model: CompanionModel,
    onDismiss: () -> Unit,
) {
    val colors = VibexTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var resolved by remember(change.path) { mutableStateOf<FileDiff?>(null) }
    var loading by remember(change.path) { mutableStateOf(true) }

    LaunchedEffect(change) {
        val rawHunks = change.hunks
        if (!rawHunks.isNullOrBlank()) {
            val parsed = UnifiedDiff.parse(rawHunks)
            if (parsed.isNotEmpty()) {
                resolved = FileDiff(path = change.path, hunks = parsed)
                loading = false
                return@LaunchedEffect
            }
        }
        model.fetchWorkspaceDiff(workspaceId, conversationId) { result ->
            result.onSuccess { list ->
                val match = list.firstOrNull {
                    it.path == change.path ||
                        it.path.endsWith("/${change.name}") ||
                        change.path.endsWith("/${it.path}")
                }
                resolved = match
            }
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
        ) {
            ToolSheetTopBar(
                title = change.name,
                canGoBack = false,
                onBack = {},
                onDone = onDismiss,
            )
            HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)

            val file = resolved
            when {
                file != null -> {
                    DiffFileView(
                        file = file,
                        workspaceId = workspaceId,
                        model = model,
                    )
                }
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingState(label = "正在读取变更")
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = change.path,
                            style = TextStyle(
                                fontFamily = PlexMono,
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                            ),
                        )
                        Text(
                            text = "+${change.additions}  −${change.deletions}",
                            style = TextStyle(
                                fontFamily = PlexMono,
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                            ),
                        )
                        Text(
                            text = "该变更已提交或无可展示的行级差异",
                            fontSize = 13.sp,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeBadge(change: FileDiff.Change) {
    val colors = VibexTheme.colors
    val (symbol, tint) = when (change) {
        FileDiff.Change.Added, FileDiff.Change.Untracked -> "+" to colors.pass
        FileDiff.Change.Deleted -> "−" to colors.danger
        FileDiff.Change.Renamed, FileDiff.Change.Modified -> "~" to Color(0xFFFF9500)
    }
    Text(
        text = symbol,
        style = TextStyle(
            fontFamily = PlexMono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
        ),
        modifier = Modifier.width(14.dp),
    )
}

/**
 * 1:1 port of iOS `DiffFileView` + `DiffRow` (`ConversationTools.swift`).
 */
@Composable
fun DiffFileView(
    file: FileDiff,
    workspaceId: String,
    model: CompanionModel,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    var extraHunks by remember(file.path) { mutableStateOf<List<DiffHunk>?>(null) }
    var loadingExtra by remember(file.path) { mutableStateOf(false) }

    val hunks = extraHunks ?: file.hunks

    LaunchedEffect(file.path, file.needsContent) {
        if (file.needsContent && workspaceId.isNotBlank()) {
            loadingExtra = true
            model.fetchWorkspaceFile(workspaceId, file.path) { result ->
                result.onSuccess { fetched ->
                    val content = fetched.content
                    if (!content.isNullOrEmpty()) {
                        extraHunks = FileDiff.additions(content)
                    }
                }
                loadingExtra = false
            }
        }
    }

    when {
        file.isBinary -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("二进制文件已变更", fontSize = 14.sp, color = colors.textSecondary)
            }
        }
        loadingExtra -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState(label = "正在读取文件")
            }
        }
        hunks.isEmpty() -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("无行级变更", fontSize = 14.sp, color = colors.textSecondary)
            }
        }
        else -> {
            SelectionContainer {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            hunks.forEach { hunk ->
                                Column {
                                    Text(
                                        text = hunk.header,
                                        style = TextStyle(
                                            fontFamily = PlexMono,
                                            fontSize = 11.5.sp,
                                            color = colors.textSecondary,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(colors.card)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                    hunk.lines.forEach { line ->
                                        DiffRow(line = line)
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

@Composable
private fun DiffRow(line: DiffLine) {
    val colors = VibexTheme.colors
    val marker = when (line.kind) {
        DiffLine.Kind.Added -> "+"
        DiffLine.Kind.Removed -> "−"
        DiffLine.Kind.Context -> " "
    }
    val fg = when (line.kind) {
        DiffLine.Kind.Added -> colors.pass
        DiffLine.Kind.Removed -> colors.danger
        DiffLine.Kind.Context -> colors.textPrimary
    }
    val bg = when (line.kind) {
        DiffLine.Kind.Added -> colors.pass.copy(alpha = 0.12f)
        DiffLine.Kind.Removed -> colors.danger.copy(alpha = 0.12f)
        DiffLine.Kind.Context -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = line.oldNumber?.toString().orEmpty(),
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 11.sp,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = line.newNumber?.toString().orEmpty(),
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 11.sp,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier
                .width(34.dp)
                .padding(end = 6.dp),
        )
        Text(
            text = marker,
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 12.sp,
                color = fg,
            ),
            modifier = Modifier.width(12.dp),
        )
        Text(
            text = line.text.ifEmpty { " " },
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 12.sp,
                color = fg,
            ),
            softWrap = false,
        )
    }
}

@Composable
private fun ToolSheetTopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = VibexTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (canGoBack) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = "返回",
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "返回",
                    fontSize = 15.sp,
                    color = colors.accent,
                )
            }
        }
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 72.dp),
        )
        Text(
            text = "完成",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDone)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}
