package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlashCommandsTest {
    private val commands = slashCatalog("grok")

    @Test
    fun grokCatalogIncludesCompact() {
        assertTrue(commands.any { it.name == "compact" })
    }

    @Test
    fun formatMatchesDesktopMarkup() {
        assertEquals("[/:native:compact:compact](/compact)", formatComposerCommand('/', "native:compact:compact", "/compact"))
        assertEquals("[@:Main.kt](app/Main.kt)", formatComposerCommand('@', "Main.kt", "app/Main.kt"))
        assertEquals("[#:release](release)", formatComposerCommand('#', "release", "release"))
        assertTrue(formatAgentMention("Grok", "grok").startsWith("[&Grok](vibex://agent/"))
    }

    @Test
    fun materializePutsTokenInsideMessage() {
        val draft = insertToken(TokenDraft("/com"), commands.first { it.name == "compact" })
        val withText = TokenDraft(draft.text + "keep diffs")
        assertEquals("/compact keep diffs", withText.materialize())
        val segments = parseComposerSegments(withText.text)
        assertEquals(2, segments.size)
        assertTrue(segments[0] is ComposerSegment.Token)
        assertEquals("keep diffs", (segments[1] as ComposerSegment.Text).value.trim())
    }

    @Test
    fun serializeUsesTokenValueNotChip() {
        val source = formatComposerCommand('/', "native:compact:compact", "/compact") + " 你是谁"
        assertEquals("/compact 你是谁", serializeComposerBackendMessage(source))
    }

    @Test
    fun queryFiltersCommands() {
        val matches = matchingTokens("/com", commands, emptyList(), emptyList())
        assertEquals(listOf("compact"), matches!!.map { it.name })
        assertNull(matchingTokens("hello", commands, emptyList(), emptyList()))
    }

    @Test
    fun atAndHashStayActiveWhenPoolEmpty() {
        assertEquals(emptyList(), matchingTokens("@", emptyList(), emptyList(), emptyList()))
        assertEquals(emptyList(), matchingTokens("#", emptyList(), emptyList(), emptyList()))
        assertEquals(emptyList(), matchingTokens("@Main", emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun atAndAgentQueriesResolve() {
        val files = listOf(ComposerToken(TokenPrefix.File, "Main.kt", "app/Main.kt"))
        val agents = listOf(ComposerToken(TokenPrefix.Agent, "Grok", "grok"))
        assertEquals("Main.kt", matchingTokens("@Main", emptyList(), files, agents)!!.single().name)
        assertEquals("Grok", matchingTokens("&G", emptyList(), files, agents)!!.single().name)
    }

    @Test
    fun hashTokenizesOnSpace() {
        val draft = applyTextChange(TokenDraft("#ship "), "#ship ", emptyList())
        assertTrue(draft.text.contains("[#:ship](ship)"))
        assertEquals("ship ", draft.materialize())
    }

    @Test
    fun insertFileUsesDesktopAtMarkup() {
        val token = ComposerToken(TokenPrefix.File, "Main.kt", "app/Main.kt")
        val draft = insertToken(TokenDraft("@Ma"), token)
        assertTrue(draft.text.contains("[@:Main.kt](app/Main.kt)"))
        assertEquals("app/Main.kt ", draft.materialize())
    }

    @Test
    fun keepsSkillsAfterNativeCommandsEvenWhenNamesOverlap() {
        val skills = listOf(
            ComposerToken(TokenPrefix.Slash, "office-xlsx", "/skill:/tmp/office-xlsx:office-xlsx", "Excel", "skill:/tmp/office-xlsx:office-xlsx"),
            ComposerToken(TokenPrefix.Slash, "compact", "/skill:/tmp/compact:compact", "Skill compact", "skill:/tmp/compact:compact"),
        )
        val merged = slashCatalog("grok", emptyList(), skills)
        assertTrue(merged.any { it.name == "compact" && it.key.startsWith("native:") })
        assertTrue(merged.any { it.name == "office-xlsx" })
        assertTrue(merged.any { it.key.startsWith("skill:") && it.name == "compact" })
        assertEquals("office-xlsx", matchingTokens("/", merged, emptyList(), emptyList())!!.map { it.name }.first { it == "office-xlsx" })
    }

    @Test
    fun backspaceRemovesTokenWhenTrailingTextIsEmpty() {
        val draft = insertToken(TokenDraft("/com"), commands.first { it.name == "compact" })
        assertTrue(draft.text.contains("[/:native:compact:compact](/compact)"))
        val deleted = deleteComposerTokenBehind(draft.text)
        assertEquals("", deleted.trim())
    }

    @Test
    fun backspaceEditsTrailingTextBeforeDeletingToken() {
        val source = formatComposerCommand('/', "native:compact:compact", "/compact") + "ab"
        assertEquals(formatComposerCommand('/', "native:compact:compact", "/compact") + "a", deleteComposerTokenBehind(source))
    }

    @Test
    fun desktopUserMessageRendersAsTokens() {
        val source = formatComposerCommand('/', "native:compact:compact", "/compact") +
            " 看 " +
            formatComposerCommand('@', "Main.kt", "app/Main.kt") +
            " " +
            formatAgentMention("Grok", "grok")
        val segments = parseComposerSegments(source)
        val tokens = segments.filterIsInstance<ComposerSegment.Token>().map { it.token.prefix }
        assertEquals(listOf(TokenPrefix.Slash, TokenPrefix.File, TokenPrefix.Agent), tokens)
        assertTrue(segments.filterIsInstance<ComposerSegment.Text>().any { it.value.contains("看") })
    }

    @Test
    fun skillTokenChipUsesShortName() {
        val source = formatComposerCommand('/', "skill:/tmp/drawio:drawio", "/skill:/tmp/drawio:drawio")
        val token = (parseComposerSegments(source).single() as ComposerSegment.Token).token
        assertEquals("drawio", token.name)
        assertEquals("/drawio", token.label)
    }
}
