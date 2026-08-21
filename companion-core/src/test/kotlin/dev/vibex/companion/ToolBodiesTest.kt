package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolBodiesTest {
    @Test
    fun readRendersFileContentNotJson() {
        val body = presentToolBody(
            name = "Read",
            kind = "read",
            input = """{"file_path":"src/Main.kt","offset":10}""",
            output = """{"content":"fun main() {}"}""",
        )
        val read = body as ToolBodyModel.FileRead
        assertEquals("src/Main.kt", read.path)
        assertEquals("fun main() {}", read.content)
        assertEquals(10, read.startLine)
    }

    @Test
    fun searchParsesPathLineHits() {
        val body = presentToolBody(
            name = "Grep",
            kind = "search",
            input = """{"pattern":"TokenChip"}""",
            output = "app/Main.kt:12: fun TokenChip()\napp/Ui.kt:4: TokenChip used",
        )
        val hits = body as ToolBodyModel.SearchHits
        assertEquals("TokenChip", hits.query)
        assertEquals(2, hits.items.size)
        assertEquals("app/Main.kt", hits.items[0].path)
        assertEquals("12", hits.items[0].line)
    }

    @Test
    fun listDirParsesEntries() {
        val body = presentToolBody(
            name = "list_dir",
            kind = "tool",
            input = """{"path":"src"}""",
            output = """{"entries":[{"name":"app","type":"directory"},{"name":"Main.kt","type":"file"}]}""",
        )
        val dir = body as ToolBodyModel.DirList
        assertEquals("src", dir.path)
        assertEquals(listOf("app", "Main.kt"), dir.entries.map { it.name })
        assertTrue(dir.entries[0].folder)
        assertTrue(!dir.entries[1].folder)
    }

    @Test
    fun editSynthesizesDiffFromOldNew() {
        val body = presentToolBody(
            name = "Edit",
            kind = "edit",
            input = """{"file_path":"a.kt","old_string":"foo","new_string":"bar"}""",
            output = "",
        )
        val edit = body as ToolBodyModel.FileEdit
        assertEquals("a.kt", edit.path)
        assertTrue(edit.lines.any { it.kind == DiffLine.Kind.DEL && it.text.contains("foo") })
        assertTrue(edit.lines.any { it.kind == DiffLine.Kind.ADD && it.text.contains("bar") })
    }

    @Test
    fun terminalShowsCommandNotInputJson() {
        val body = presentToolBody(
            name = "Bash",
            kind = "execute",
            input = """{"command":"ls -la","description":"list"}""",
            output = """{"stdout":"Main.kt"}""",
        )
        val term = body as ToolBodyModel.Terminal
        assertEquals("ls -la", term.command)
        assertEquals("Main.kt", term.output)
    }

    @Test
    fun listDirFamilyDetectedFromName() {
        assertEquals(ToolFamily.LIST, toolFamily("list_dir", ""))
        assertEquals(ToolFamily.READ, toolFamily("Read", ""))
        assertEquals(ToolFamily.SEARCH, toolFamily("grep", ""))
    }


    @Test
    fun readFileEnvelopeUnwrapsContent() {
        val output = buildString {
            append("""{"type":"ReadFile","FileContent":{"content":""")
            append('"')
            append("---\\nname: drawio")
            append('"')
            append("}}")
        }
        val body = presentToolBody(
            name = "Read",
            kind = "read",
            input = """{"file_path":"/tmp/SKILL.md"}""",
            output = output,
        )
        val read = body as ToolBodyModel.FileRead
        assertTrue("ReadFile" !in read.content, read.content.take(80))
        assertTrue("drawio" in read.content, read.content.take(120))
    }

    @Test
    fun unwrapToolOutputReadsNestedFileContent() {
        val raw = buildString {
            append("""{"type":"ReadFile","FileContent":{"content":""")
            append('"')
            append("hello\\nworld")
            append('"')
            append("}}")
        }
        val out = unwrapToolOutput(raw)
        assertEquals("hello\nworld", out)
    }
}
