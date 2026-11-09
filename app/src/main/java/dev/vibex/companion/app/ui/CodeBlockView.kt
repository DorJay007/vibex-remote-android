package dev.vibex.companion.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay

enum class CodeLanguage {
    Swift, TypeScript, JavaScript, Python, Rust, Go, Kotlin,
    C, Cpp, Java, Shell, Json, Yaml, Toml, Html, Css, Sql, Markdown, Diff, Plain;

    val displayName: String
        get() = when (this) {
            Swift -> "Swift"
            TypeScript -> "TypeScript"
            JavaScript -> "JavaScript"
            Python -> "Python"
            Rust -> "Rust"
            Go -> "Go"
            Kotlin -> "Kotlin"
            C -> "C"
            Cpp -> "C++"
            Java -> "Java"
            Shell -> "Terminal"
            Json -> "JSON"
            Yaml -> "YAML"
            Toml -> "TOML"
            Html -> "HTML"
            Css -> "CSS"
            Sql -> "SQL"
            Markdown -> "Markdown"
            Diff -> "Diff"
            Plain -> "Text"
        }

    val badgeLabel: String
        get() = when (this) {
            Swift -> "SW"
            TypeScript -> "TS"
            JavaScript -> "JS"
            Python -> "PY"
            Rust -> "RS"
            Go -> "GO"
            Kotlin -> "KT"
            C -> "C"
            Cpp -> "C+"
            Java -> "JV"
            Shell -> "SH"
            Json -> "{}"
            Yaml -> "YM"
            Toml -> "TM"
            Html -> "<>"
            Css -> "CS"
            Sql -> "SQ"
            Markdown -> "MD"
            Diff -> "±"
            Plain -> "TX"
        }

    val badgeTint: Color
        get() = when (this) {
            Swift -> Color(0xFFF05238)
            TypeScript -> Color(0xFF3078C7)
            JavaScript -> Color(0xFFDEC41F)
            Python -> Color(0xFF3875AB)
            Rust -> Color(0xFFDE6633)
            Go -> Color(0xFF00ADD9)
            Kotlin -> Color(0xFF8052FF)
            C, Cpp -> Color(0xFF5987BF)
            Java -> Color(0xFFDB732E)
            Shell -> Color(0xFF38B866)
            Json, Yaml, Toml -> Color(0xFF8F6BC7)
            Html -> Color(0xFFE34D26)
            Css -> Color(0xFF2966F2)
            Sql -> Color(0xFFD9871F)
            Markdown -> Color(0xFF6B7380)
            Diff -> Color(0xFF33B36B)
            Plain -> Color(0xFF8C8C94)
        }

    val usesDarkSurface: Boolean
        get() = this == Shell

    val hashComments: Boolean
        get() = when (this) {
            Python, Shell, Yaml, Toml -> true
            else -> false
        }

    val slashComments: Boolean
        get() = when (this) {
            Swift, TypeScript, JavaScript, Rust, Go, Kotlin, C, Cpp, Java, Css -> true
            else -> false
        }

    val keywords: Set<String>
        get() = when (this) {
            Swift -> setOf(
                "import", "struct", "class", "enum", "protocol", "extension", "func", "var", "let",
                "if", "else", "guard", "switch", "case", "default", "for", "in", "while", "return",
                "break", "continue", "throw", "throws", "try", "catch", "async", "await", "actor",
                "public", "private", "fileprivate", "internal", "open", "static", "mutating",
                "init", "deinit", "self", "Self", "super", "nil", "true", "false", "some", "any", "where",
            )
            TypeScript, JavaScript -> setOf(
                "import", "export", "from", "default", "function", "const", "let", "var", "class",
                "interface", "type", "enum", "implements", "extends", "new", "return", "if", "else",
                "switch", "case", "for", "of", "in", "while", "do", "break", "continue", "try",
                "catch", "finally", "throw", "async", "await", "yield", "this", "super", "typeof",
                "instanceof", "keyof", "readonly", "public", "private", "protected", "static",
                "true", "false", "null", "undefined", "void", "as", "satisfies",
            )
            Python -> setOf(
                "def", "class", "import", "from", "as", "return", "yield", "if", "elif", "else",
                "for", "in", "while", "break", "continue", "pass", "try", "except", "finally",
                "raise", "with", "async", "await", "lambda", "global", "nonlocal", "assert",
                "True", "False", "None", "and", "or", "not", "is", "self", "match", "case",
            )
            Rust -> setOf(
                "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "type",
                "mod", "use", "pub", "crate", "super", "self", "Self", "where", "for", "in",
                "while", "loop", "if", "else", "match", "return", "break", "continue", "async",
                "await", "move", "ref", "unsafe", "extern", "dyn", "as", "true", "false", "Some", "None", "Ok", "Err",
            )
            Go -> setOf(
                "package", "import", "func", "var", "const", "type", "struct", "interface", "map",
                "chan", "go", "defer", "select", "case", "default", "if", "else", "switch", "for",
                "range", "return", "break", "continue", "fallthrough", "nil", "true", "false", "make", "new",
            )
            Kotlin, Java -> setOf(
                "package", "import", "class", "interface", "object", "enum", "fun", "val", "var",
                "if", "else", "when", "for", "while", "do", "return", "break", "continue", "try",
                "catch", "finally", "throw", "public", "private", "protected", "internal", "open",
                "final", "abstract", "override", "static", "suspend", "data", "sealed", "companion",
                "this", "super", "null", "true", "false", "new", "void", "extends", "implements",
            )
            C, Cpp -> setOf(
                "include", "define", "ifdef", "ifndef", "endif", "pragma", "struct", "class",
                "enum", "union", "typedef", "using", "namespace", "template", "typename", "const",
                "constexpr", "static", "extern", "inline", "virtual", "override", "public",
                "private", "protected", "if", "else", "switch", "case", "default", "for", "while",
                "do", "return", "break", "continue", "new", "delete", "nullptr", "NULL", "true", "false", "void", "auto",
            )
            Shell -> setOf(
                "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until",
                "case", "esac", "function", "return", "exit", "export", "local", "readonly",
                "set", "unset", "shift", "source", "echo", "printf", "cd", "pwd", "ls", "rm",
                "cp", "mv", "mkdir", "cat", "grep", "sed", "awk", "find", "xargs", "curl",
                "git", "cargo", "npm", "pnpm", "yarn", "bun", "python", "python3", "swift", "xcodebuild",
            )
            Sql -> setOf(
                "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
                "CREATE", "TABLE", "INDEX", "VIEW", "DROP", "ALTER", "ADD", "PRIMARY", "KEY",
                "FOREIGN", "REFERENCES", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "ON", "AS",
                "AND", "OR", "NOT", "NULL", "IS", "IN", "EXISTS", "BETWEEN", "LIKE", "ORDER",
                "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "DISTINCT", "UNION", "ALL", "ASC", "DESC",
                "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
                "create", "table", "join", "inner", "left", "right", "on", "as", "and", "or",
                "not", "null", "is", "in", "order", "by", "group", "having", "limit", "offset",
            )
            Json, Yaml, Toml -> setOf("true", "false", "null", "yes", "no")
            else -> emptySet()
        }

    val types: Set<String>
        get() = when (this) {
            Swift -> setOf(
                "String", "Int", "Double", "Float", "Bool", "Data", "Date", "URL", "UUID",
                "Array", "Dictionary", "Set", "Optional", "Result", "Error", "Any", "AnyObject",
                "Void", "Never", "View", "State", "Binding", "ObservedObject", "StateObject",
                "Environment", "EnvironmentObject", "Color", "Font", "Image", "Text", "Task", "MainActor",
            )
            TypeScript, JavaScript -> setOf(
                "string", "number", "boolean", "bigint", "symbol", "object", "any", "unknown",
                "never", "Record", "Partial", "Required", "Readonly", "Pick", "Omit", "Promise",
                "Array", "Map", "Set", "Date", "RegExp", "Error", "React", "JSX", "HTMLElement",
            )
            Python -> setOf(
                "int", "float", "str", "bool", "bytes", "list", "dict", "set", "tuple", "object",
                "type", "Optional", "Union", "Any", "List", "Dict", "Set", "Tuple", "Callable", "Exception",
            )
            Rust -> setOf(
                "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize",
                "f32", "f64", "bool", "char", "str", "String", "Vec", "Box", "Rc", "Arc", "Cell",
                "RefCell", "Mutex", "RwLock", "HashMap", "HashSet", "BTreeMap", "Option", "Result",
            )
            Go -> setOf(
                "int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16", "uint32", "uint64",
                "uintptr", "float32", "float64", "complex64", "complex128", "byte", "rune", "string",
                "bool", "error", "any", "Context", "WaitGroup", "Mutex",
            )
            Kotlin, Java -> setOf(
                "String", "Int", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Char",
                "Unit", "Nothing", "Any", "List", "MutableList", "Map", "Set", "Array", "Object", "Integer",
            )
            C, Cpp -> setOf(
                "int", "long", "short", "char", "float", "double", "bool", "size_t", "ssize_t",
                "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int8_t", "int16_t", "int32_t", "int64_t",
                "string", "vector", "map", "unordered_map", "set", "shared_ptr", "unique_ptr", "optional",
            )
            else -> emptySet()
        }

    companion object {
        fun detect(raw: String, filename: String? = null): CodeLanguage {
            val token = raw.trim().lowercase()
            if (token.isNotEmpty()) {
                fromToken(token)?.let { return it }
            }
            val name = filename?.lowercase().orEmpty()
            if (name.isNotEmpty()) {
                val ext = name.substringAfterLast('.', "")
                if (ext.isNotEmpty()) {
                    fromToken(ext)?.let { return it }
                }
                if (name.endsWith("dockerfile") || name.endsWith("makefile")) return Shell
            }
            return Plain
        }

        private fun fromToken(token: String): CodeLanguage? = when (token) {
            "swift" -> Swift
            "ts", "tsx", "typescript" -> TypeScript
            "js", "jsx", "mjs", "cjs", "javascript" -> JavaScript
            "py", "python", "python3" -> Python
            "rs", "rust" -> Rust
            "go", "golang" -> Go
            "kt", "kts", "kotlin" -> Kotlin
            "c", "h" -> C
            "cpp", "cc", "cxx", "hpp", "hxx", "c++" -> Cpp
            "java" -> Java
            "sh", "bash", "zsh", "fish", "shell", "console", "terminal", "cmd", "powershell", "ps1" -> Shell
            "json", "jsonc", "jsonl" -> Json
            "yml", "yaml" -> Yaml
            "toml", "ini" -> Toml
            "html", "htm", "xml", "svg", "vue", "svelte" -> Html
            "css", "scss", "less" -> Css
            "sql", "sqlite", "postgres", "postgresql", "mysql" -> Sql
            "md", "markdown", "mdx" -> Markdown
            "diff", "patch" -> Diff
            "txt", "text", "plain" -> Plain
            else -> null
        }
    }
}

object CodeHighlighter {
    private data class Palette(
        val plain: Color,
        val keyword: Color,
        val type: Color,
        val string: Color,
        val number: Color,
        val comment: Color,
        val property: Color,
        val added: Color,
        val removed: Color,
    )

    private fun palette(dark: Boolean): Palette = if (dark) {
        Palette(
            plain = Color(0xFFEBECF2),
            keyword = Color(0xFFFF7AB2),
            type = Color(0xFF6BD9FF),
            string = Color(0xFFFFA17A),
            number = Color(0xFFD9A6FF),
            comment = Color(0xFF828C99),
            property = Color(0xFF8CE3B8),
            added = Color(0xFF52D17A),
            removed = Color(0xFFFF6E70),
        )
    } else {
        Palette(
            plain = Color(0xFF1F2129),
            keyword = Color(0xFFAD1F80),
            type = Color(0xFF0A69A1),
            string = Color(0xFFC43821),
            number = Color(0xFF6B3DD6),
            comment = Color(0xFF757D8A),
            property = Color(0xFF147859),
            added = Color(0xFF1A8740),
            removed = Color(0xFFC92E2E),
        )
    }

    fun highlight(line: String, language: CodeLanguage, dark: Boolean): AnnotatedString {
        val pal = palette(dark)
        if (line.isEmpty()) return AnnotatedString("")

        if (language == CodeLanguage.Diff) {
            val c = when {
                line.startsWith("+") -> pal.added
                line.startsWith("-") -> pal.removed
                line.startsWith("@@") -> pal.type
                else -> pal.plain
            }
            return buildAnnotatedString {
                pushStyle(SpanStyle(color = c))
                append(line)
                pop()
            }
        }

        return buildAnnotatedString {
            var i = 0
            val len = line.length
            val keywords = language.keywords
            val types = language.types

            while (i < len) {
                val ch = line[i]

                if (language.slashComments && ch == '/' && i + 1 < len && line[i + 1] == '/') {
                    pushStyle(SpanStyle(color = pal.comment))
                    append(line.substring(i))
                    pop()
                    break
                }

                if (language.hashComments && ch == '#') {
                    pushStyle(SpanStyle(color = pal.comment))
                    append(line.substring(i))
                    pop()
                    break
                }

                if (ch == '"' || ch == '\'' || ch == '`') {
                    val quote = ch
                    val start = i
                    i++
                    while (i < len) {
                        val c = line[i]
                        if (c == '\\') {
                            i += 2
                            continue
                        }
                        i++
                        if (c == quote) break
                    }
                    val token = line.substring(start, i.coerceAtMost(len))
                    var isJsonKey = false
                    if (language == CodeLanguage.Json && quote == '"') {
                        var look = i
                        while (look < len && (line[look] == ' ' || line[look] == '\t')) look++
                        if (look < len && line[look] == ':') isJsonKey = true
                    }
                    pushStyle(SpanStyle(color = if (isJsonKey) pal.property else pal.string))
                    append(token)
                    pop()
                    continue
                }

                if (ch == '@' && (language == CodeLanguage.Swift || language == CodeLanguage.Python || language == CodeLanguage.Java || language == CodeLanguage.Kotlin || language == CodeLanguage.TypeScript)) {
                    val start = i
                    i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                    pushStyle(SpanStyle(color = pal.keyword))
                    append(line.substring(start, i))
                    pop()
                    continue
                }

                if (ch.isDigit()) {
                    val start = i
                    i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_')) i++
                    pushStyle(SpanStyle(color = pal.number))
                    append(line.substring(start, i))
                    pop()
                    continue
                }

                if (ch.isLetter() || ch == '_' || ch == '$') {
                    val start = i
                    i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$' || line[i] == '-')) i++
                    val word = line.substring(start, i)
                    val color = when {
                        keywords.contains(word) -> pal.keyword
                        types.contains(word) -> pal.type
                        word.first().isUpperCase() && language != CodeLanguage.Plain && language != CodeLanguage.Markdown -> pal.type
                        else -> pal.plain
                    }
                    val weight = if (keywords.contains(word)) FontWeight.Medium else FontWeight.Normal
                    pushStyle(SpanStyle(color = color, fontWeight = weight))
                    append(word)
                    pop()
                    continue
                }

                val start = i
                i++
                while (i < len) {
                    val c = line[i]
                    if (c.isLetterOrDigit() || c == '_' || c == '$' || c == '"' || c == '\'' || c == '`' || c == '/' || c == '#' || c == '@') break
                    i++
                }
                pushStyle(SpanStyle(color = pal.plain))
                append(line.substring(start, i))
                pop()
            }
        }
    }
}

/**
 * 1:1 port of iOS `FileGlyph` (`CodeBlockView.swift`).
 */
@Composable
fun FileGlyph(
    language: CodeLanguage,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val strokeColor = colors.textSecondary.copy(alpha = 0.55f)
    Box(
        modifier = modifier.size(width = 18.dp, height = 20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val fold = 5.dp.toPx()
            val outline = Path().apply {
                moveTo(2.dp.toPx(), 1.dp.toPx())
                lineTo(w - fold - 1.dp.toPx(), 1.dp.toPx())
                lineTo(w - 1.dp.toPx(), fold + 1.dp.toPx())
                lineTo(w - 1.dp.toPx(), h - 1.dp.toPx())
                lineTo(2.dp.toPx(), h - 1.dp.toPx())
                close()
            }
            drawPath(
                path = outline,
                color = strokeColor,
                style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round),
            )
            val corner = Path().apply {
                moveTo(w - fold - 1.dp.toPx(), 1.dp.toPx())
                lineTo(w - fold - 1.dp.toPx(), fold + 1.dp.toPx())
                lineTo(w - 1.dp.toPx(), fold + 1.dp.toPx())
            }
            drawPath(
                path = corner,
                color = strokeColor,
                style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Text(
            text = language.badgeLabel,
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
            modifier = Modifier
                .offset(y = (-1.5).dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(language.badgeTint)
                .padding(horizontal = 2.5.dp, vertical = 0.5.dp),
        )
    }
}

/**
 * 1:1 port of iOS `CodeBlockView` (`CodeBlockView.swift`).
 */
@Composable
fun CodeBlockView(
    code: String,
    language: String = "",
    filename: String? = null,
    startLine: Int = 1,
    maxHeight: Dp? = 380.dp,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val clipboard = LocalClipboardManager.current

    var copied by remember { mutableStateOf(false) }
    var wrapLines by remember { mutableStateOf(false) }
    var fontSizePt by remember { mutableFloatStateOf(12.5f) }
    var sizeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    val resolvedLanguage = remember(language, filename) {
        CodeLanguage.detect(raw = language, filename = filename)
    }
    val isDarkSurface = resolvedLanguage.usesDarkSurface || colors.isDark
    val lines = remember(code) {
        val raw = code.split("\n")
        if (raw.size > 1 && raw.last().isEmpty()) raw.dropLast(1) else raw
    }

    val titleText = remember(filename, resolvedLanguage) {
        val f = filename?.trim().orEmpty()
        if (f.isNotEmpty()) f.substringAfterLast('/').ifBlank { f } else resolvedLanguage.displayName
    }

    val surfaceBackground = when {
        resolvedLanguage.usesDarkSurface -> Color(0xFF14161D)
        colors.isDark -> Color(0xFF181A21)
        else -> Color(0xFFF6F7FA)
    }
    val headerBackground = when {
        resolvedLanguage.usesDarkSurface -> Color(0xFF1D202A)
        colors.isDark -> Color(0xFF20222B)
        else -> Color(0xFFEDEFF4)
    }
    val headerPrimary = if (resolvedLanguage.usesDarkSurface) Color(0xFFEBECF2) else colors.textPrimary
    val headerSecondary = if (resolvedLanguage.usesDarkSurface) Color(0xFF9EA4B3) else colors.textSecondary
    val gutterColor = if (isDarkSurface) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.32f)
    val dividerColor = if (isDarkSurface) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)
    val borderColor = if (isDarkSurface) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

    val gutterMinWidth = remember(lines.size, startLine) {
        val maxLineNum = maxOf(startLine + lines.size - 1, 1)
        val digits = maxLineNum.toString().length
        maxOf(22, digits * 8 + 6).dp
    }

    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceBackground)
            .border(0.6.dp, borderColor, shape),
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBackground)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileGlyph(language = resolvedLanguage)

            Text(
                text = titleText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = headerPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (lines.size > 1) {
                Text(
                    text = "${lines.size} 行",
                    style = TextStyle(
                        fontFamily = PlexMono,
                        fontSize = 11.sp,
                        color = headerSecondary.copy(alpha = 0.75f),
                    ),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Wrap toggle
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (wrapLines) colors.accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { wrapLines = !wrapLines },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.WrapText,
                    contentDescription = if (wrapLines) "取消自动换行" else "自动换行",
                    tint = if (wrapLines) colors.accent else headerSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }

            // Font size menu
            Box {
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { sizeMenuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FormatSize,
                        contentDescription = "字号",
                        tint = headerSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                DropdownMenu(
                    expanded = sizeMenuExpanded,
                    onDismissRequest = { sizeMenuExpanded = false },
                ) {
                    listOf(
                        11f to "小 (11pt)",
                        12.5f to "默认 (12.5pt)",
                        14f to "大 (14pt)",
                    ).forEach { (pt, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 13.sp) },
                            onClick = {
                                fontSizePt = pt
                                sizeMenuExpanded = false
                            },
                        )
                    }
                }
            }

            // Copy button
            Row(
                modifier = Modifier
                    .height(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(headerPrimary.copy(alpha = 0.06f))
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = "复制",
                    tint = if (copied) colors.pass else headerSecondary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = if (copied) "已复制" else "复制",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (copied) colors.pass else headerSecondary,
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)

        // Code Body
        val bodyModifier = if (maxHeight != null) {
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }

        SelectionContainer {
            if (wrapLines) {
                Column(
                    modifier = bodyModifier.padding(vertical = 8.dp, horizontal = 10.dp),
                ) {
                    lines.forEachIndexed { idx, line ->
                        CodeLineRow(
                            number = startLine + idx,
                            line = line,
                            language = resolvedLanguage,
                            dark = isDarkSurface,
                            fontSizePt = fontSizePt,
                            gutterColor = gutterColor,
                            gutterMinWidth = gutterMinWidth,
                            wrap = true,
                        )
                    }
                }
            } else {
                Row(
                    modifier = bodyModifier.horizontalScroll(rememberScrollState()),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                    ) {
                        lines.forEachIndexed { idx, line ->
                            CodeLineRow(
                                number = startLine + idx,
                                line = line,
                                language = resolvedLanguage,
                                dark = isDarkSurface,
                                fontSizePt = fontSizePt,
                                gutterColor = gutterColor,
                                gutterMinWidth = gutterMinWidth,
                                wrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeLineRow(
    number: Int,
    line: String,
    language: CodeLanguage,
    dark: Boolean,
    fontSizePt: Float,
    gutterColor: Color,
    gutterMinWidth: Dp,
    wrap: Boolean,
) {
    val highlighted = remember(line, language, dark) {
        CodeHighlighter.highlight(line = line, language = language, dark = dark)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = number.toString(),
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = (fontSizePt - 1f).sp,
                color = gutterColor,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier
                .widthIn(min = gutterMinWidth)
                .padding(top = 1.dp),
        )
        Text(
            text = if (highlighted.isEmpty()) AnnotatedString(" ") else highlighted,
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = fontSizePt.sp,
                lineHeight = (fontSizePt + 5f).sp,
            ),
            softWrap = wrap,
            modifier = if (wrap) Modifier.fillMaxWidth() else Modifier,
        )
    }
}
