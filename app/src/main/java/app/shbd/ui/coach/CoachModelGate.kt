package app.shbd.ui.coach

import app.shbd.models.ChatMessage
import app.shbd.services.ondevice.AiAvailability

/** What the Coach does about the AI model for the current chat. */
enum class CoachModelAction {
    NONE,

    /** No model is ready: show the "pick a model" card (also in an empty chat). */
    SHOW_CARD,

    /** A model just became ready and the last user message has no answer yet. */
    AUTO_ANSWER,
}

/**
 * Derived from the chat history, not stored, so it survives the app being
 * closed during the download. [alreadyHandled] is true when the last message
 * was already answered (or an answer was attempted) in this session — that
 * keeps a failed request from being retried in a loop.
 */
fun coachModelAction(
    lastMessage: ChatMessage?,
    availability: AiAvailability,
    busy: Boolean,
    alreadyHandled: Boolean,
): CoachModelAction = when (availability) {
    AiAvailability.MODEL_MISSING -> CoachModelAction.SHOW_CARD
    AiAvailability.READY ->
        if (lastMessage?.role != ChatMessage.Role.USER || busy || alreadyHandled) CoachModelAction.NONE
        else CoachModelAction.AUTO_ANSWER
    AiAvailability.DISABLED -> CoachModelAction.NONE
}
