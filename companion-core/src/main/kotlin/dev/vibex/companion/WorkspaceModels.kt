package dev.vibex.companion

data class WorkspaceTree(
    val path: String,
    val entries: List<WorkspaceEntry>,
    val truncated: Boolean,
)

data class WorkspaceFile(
    val path: String,
    val size: Int,
    val binary: Boolean,
    val truncated: Boolean,
    val content: String?,
)

data class DiffLine(
    val id: Int,
    val kind: Kind,
    val text: String,
    val oldNumber: Int?,
    val newNumber: Int?,
) {
    enum class Kind { Context, Added, Removed }
}

data class DiffHunk(
    val id: Int,
    val header: String,
    val lines: List<DiffLine>,
)

data class FileDiff(
    val path: String,
    val change: Change = Change.Modified,
    val isBinary: Boolean = false,
    val truncated: Boolean = false,
    val hunks: List<DiffHunk> = emptyList(),
) {
    enum class Change { Added, Modified, Deleted, Renamed, Untracked }

    val id: String get() = path
    val additions: Int get() = count(DiffLine.Kind.Added)
    val deletions: Int get() = count(DiffLine.Kind.Removed)
    val needsContent: Boolean get() = hunks.isEmpty() && !isBinary && change != Change.Deleted

    private fun count(kind: DiffLine.Kind): Int =
        hunks.sumOf { hunk -> hunk.lines.count { it.kind == kind } }

    companion object {
        fun fromEntry(
            path: String,
            status: String,
            diff: String,
            isBinary: Boolean = false,
            truncated: Boolean = false,
        ): FileDiff {
            val change = when (status.uppercase()) {
                "A", "ADDED" -> Change.Added
                "D", "DELETED" -> Change.Deleted
                "R", "C", "RENAMED" -> Change.Renamed
                "?", "UNTRACKED" -> Change.Untracked
                else -> Change.Modified
            }
            return FileDiff(
                path = path,
                change = change,
                isBinary = isBinary,
                truncated = truncated,
                hunks = UnifiedDiff.parse(diff),
            )
        }

        fun additions(content: String): List<DiffHunk> {
            val lines = content.split("\n")
            val trimmed = if (lines.lastOrNull() == "") lines.dropLast(1) else lines
            if (trimmed.isEmpty()) return emptyList()
            val rows = trimmed.mapIndexed { idx, line ->
                DiffLine(
                    id = idx,
                    kind = DiffLine.Kind.Added,
                    text = line,
                    oldNumber = null,
                    newNumber = idx + 1,
                )
            }
            return listOf(
                DiffHunk(
                    id = 0,
                    header = "@@ -0,0 +1,${rows.size} @@",
                    lines = rows,
                ),
            )
        }
    }
}

object UnifiedDiff {
    fun parse(text: String): List<DiffHunk> {
        val hunks = mutableListOf<DiffHunk>()
        var header = ""
        var lines = mutableListOf<DiffLine>()
        var oldNumber = 0
        var newNumber = 0
        var inHunk = false

        fun flush() {
            if (inHunk) {
                hunks += DiffHunk(id = hunks.size, header = header, lines = lines)
            }
            lines = mutableListOf()
        }

        for (line in text.split("\n")) {
            if (line.startsWith("@@")) {
                flush()
                val starts = hunkStarts(line)
                if (starts == null) {
                    inHunk = false
                    continue
                }
                header = line
                oldNumber = starts.first
                newNumber = starts.second
                inHunk = true
                continue
            }
            if (!inHunk || line.isEmpty()) continue
            val marker = line[0]
            val body = line.drop(1)
            when (marker) {
                '+' -> {
                    lines += DiffLine(id = lines.size, kind = DiffLine.Kind.Added, text = body, oldNumber = null, newNumber = newNumber)
                    newNumber += 1
                }
                '-' -> {
                    lines += DiffLine(id = lines.size, kind = DiffLine.Kind.Removed, text = body, oldNumber = oldNumber, newNumber = null)
                    oldNumber += 1
                }
                ' ' -> {
                    lines += DiffLine(id = lines.size, kind = DiffLine.Kind.Context, text = body, oldNumber = oldNumber, newNumber = newNumber)
                    oldNumber += 1
                    newNumber += 1
                }
                else -> Unit
            }
        }
        flush()
        return hunks
    }

    private fun hunkStarts(header: String): Pair<Int, Int>? {
        val parts = header.split(" ").filter { it.isNotEmpty() }
        if (parts.size < 3 || !parts[1].startsWith("-") || !parts[2].startsWith("+")) return null
        fun start(part: String): Int? = part.drop(1).substringBefore(',').toIntOrNull()
        val old = start(parts[1]) ?: return null
        val new = start(parts[2]) ?: return null
        return old to new
    }
}
