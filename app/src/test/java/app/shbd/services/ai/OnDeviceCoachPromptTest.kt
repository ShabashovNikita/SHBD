package app.shbd.services.ai

import app.shbd.models.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The embedded model gets the conversation folded into one prompt, trimmed to its small context. */
class OnDeviceCoachPromptTest {

    private fun user(text: String) = ChatMessage(role = ChatMessage.Role.USER, content = text)
    private fun coach(text: String) = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = text)

    @Test
    fun noHistory_isJustTheQuestion() {
        assertEquals("Что съесть на ужин?", onDeviceCoachPrompt(emptyList(), "Что съесть на ужин?"))
    }

    @Test
    fun history_isLabelledAndFollowedByTheQuestion() {
        val prompt = onDeviceCoachPrompt(listOf(user("Привет"), coach("Здравствуй!")), "Что съесть на ужин?")
        assertTrue(prompt.contains("User: Привет\nCoach: Здравствуй!"))
        assertTrue(prompt.endsWith("Что съесть на ужин?"))
    }

    @Test
    fun onlyTheLastMessagesAreKept() {
        val history = (1..10).map { user("msg$it") }
        val prompt = onDeviceCoachPrompt(history, "q")
        assertFalse(prompt.contains("msg4\n"))
        assertTrue(prompt.contains("User: msg5"))
        assertTrue(prompt.contains("User: msg10"))
        assertEquals(ON_DEVICE_HISTORY_MESSAGES, prompt.lines().count { it.startsWith("User: msg") })
    }

    @Test
    fun longMessagesAreCut() {
        val long = "а".repeat(ON_DEVICE_HISTORY_MESSAGE_CHARS + 100)
        val prompt = onDeviceCoachPrompt(listOf(coach(long)), "q")
        val line = prompt.lines().first { it.startsWith("Coach: ") }
        assertEquals("Coach: ".length + ON_DEVICE_HISTORY_MESSAGE_CHARS + 1, line.length)
        assertTrue(line.endsWith("…"))
    }
}
