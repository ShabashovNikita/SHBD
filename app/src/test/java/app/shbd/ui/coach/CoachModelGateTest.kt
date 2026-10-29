package app.shbd.ui.coach

import app.shbd.models.ChatMessage
import app.shbd.services.ondevice.AiAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachModelGateTest {
    private val user = ChatMessage(role = ChatMessage.Role.USER, content = "Что съесть на ужин?")
    private val assistant = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Салат")

    private fun action(
        last: ChatMessage?,
        availability: AiAvailability,
        busy: Boolean = false,
        handled: Boolean = false,
    ) = coachModelAction(last, availability, busy, handled)

    @Test
    fun modelMissing_alwaysShowsCard_evenInEmptyChat() {
        // First visit: empty chat.
        assertEquals(CoachModelAction.SHOW_CARD, action(null, AiAvailability.MODEL_MISSING))
        assertEquals(CoachModelAction.SHOW_CARD, action(user, AiAvailability.MODEL_MISSING))
        assertEquals(CoachModelAction.SHOW_CARD, action(user, AiAvailability.MODEL_MISSING, handled = true))
        assertEquals(CoachModelAction.SHOW_CARD, action(assistant, AiAvailability.MODEL_MISSING))
    }

    @Test
    fun modelReady_unansweredUserMessage_autoAnswers() {
        assertEquals(CoachModelAction.AUTO_ANSWER, action(user, AiAvailability.READY))
    }

    @Test
    fun modelReady_busyOrAlreadyHandled_doesNotAnswerTwice() {
        assertEquals(CoachModelAction.NONE, action(user, AiAvailability.READY, busy = true))
        assertEquals(CoachModelAction.NONE, action(user, AiAvailability.READY, handled = true))
        assertEquals(CoachModelAction.NONE, action(assistant, AiAvailability.READY))
        assertEquals(CoachModelAction.NONE, action(null, AiAvailability.READY))
    }

    @Test
    fun disabled_neverShowsCardOrAnswers() {
        assertEquals(CoachModelAction.NONE, action(user, AiAvailability.DISABLED))
        assertEquals(CoachModelAction.NONE, action(null, AiAvailability.DISABLED))
    }
}
