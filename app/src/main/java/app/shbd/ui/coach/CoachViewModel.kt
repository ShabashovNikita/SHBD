package app.shbd.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.data.disambiguateFoodName
import app.shbd.models.ChatMessage
import app.shbd.models.EnergyFormat
import app.shbd.models.EnergyUnit
import app.shbd.models.FoodEntry
import app.shbd.models.LocaleFormat
import app.shbd.models.WaterEntry
import app.shbd.models.WeightEntry
import app.shbd.models.WeightGoal
import app.shbd.BuildConfig
import app.shbd.services.ai.AiError
import app.shbd.services.ai.userMessage
import app.shbd.services.ondevice.AiAvailability
import app.shbd.services.ondevice.ModelCatalog
import app.shbd.services.ondevice.OnDeviceDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Base64
import java.util.UUID

/**
 * Sealed wrapper around chip text — either a resource (for our preset chips,
 * so they translate) or a literal string (for user-typed sends, which already
 * go through the localized input path).
 */
sealed class CoachError {
    data class FromResource(val resId: Int) : CoachError()
    data class Literal(val message: String) : CoachError()
}

data class CoachUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
    val suggestions: List<Int> = emptyList(),
    /** A write action the coach proposed, awaiting explicit user confirmation.
     *  At most one of these is non-null at a time. */
    val pendingFood: FoodEntry? = null,
    val pendingWeight: WeightEntry? = null,
    val pendingWater: WaterEntry? = null,
    /** Active model availability; null until the first value arrives (no card, no auto-answer). */
    val availability: AiAvailability? = null,
    val downloadState: OnDeviceDownloadState = OnDeviceDownloadState.NotDownloaded,
    /** The device can run the local model (ABI + RAM floor). */
    val localSupported: Boolean = true,
    /** The "connect a remote model" instructions are open. */
    val showRemoteHelp: Boolean = false,
    /** The model download card is the last chat item (see [coachModelAction]). */
    val showModelCard: Boolean = false,
)

class CoachViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CoachUiState())
    val ui: StateFlow<CoachUiState> = _ui.asStateFlow()

    /** User messages answered (or being answered) in this session; stops repeat auto-answers. */
    private val handledMessageIds = mutableSetOf<UUID>()

    init {
        container.chatRepository.messages
            .onEach {
                _ui.value = _ui.value.copy(messages = it)
                evaluateModelAction()
            }
            .launchIn(viewModelScope)

        container.aiAvailability
            .onEach {
                _ui.value = _ui.value.copy(availability = it)
                evaluateModelAction()
            }
            .launchIn(viewModelScope)
        container.onDeviceModelDownloadManager.state(ModelCatalog.default.modelId)
            .onEach { _ui.value = _ui.value.copy(downloadState = it) }
            .launchIn(viewModelScope)
        _ui.value = _ui.value.copy(localSupported = container.onDeviceSupported)

        // Live-subscribe to profile so chips update when the user changes goal in Settings.
        container.profileRepository.profile
            .onEach { p -> _ui.value = _ui.value.copy(suggestions = chipsFor(p?.goal)) }
            .launchIn(viewModelScope)
    }

    private fun chipsFor(goal: WeightGoal?): List<Int> = when (goal) {
        WeightGoal.LOSE -> listOf(
            R.string.coach_chip_predict_30_days,
            R.string.coach_chip_lose_faster,
            R.string.coach_chip_eating_too_much,
            R.string.coach_chip_what_dinner
        )
        WeightGoal.GAIN -> listOf(
            R.string.coach_chip_predict_30_days,
            R.string.coach_chip_gain_healthy,
            R.string.coach_chip_eating_enough,
            R.string.coach_chip_high_protein
        )
        WeightGoal.MAINTAIN -> listOf(
            R.string.coach_chip_holding_weight,
            R.string.coach_chip_average_intake,
            R.string.coach_chip_macro_suggestions,
            R.string.coach_chip_trend
        )
        else -> listOf(
            R.string.coach_chip_doing_this_week,
            R.string.coach_chip_predict_30_days,
            R.string.coach_chip_log_advice
        )
    }

    /** Shows the download card or answers the waiting message once the model is there. */
    private fun evaluateModelAction() {
        val state = _ui.value
        val availability = state.availability ?: return
        val last = state.messages.lastOrNull()
        val action = coachModelAction(last, availability, state.sending, last?.id in handledMessageIds)
        _ui.value = state.copy(showModelCard = action == CoachModelAction.SHOW_CARD)
        if (action == CoachModelAction.AUTO_ANSWER && last != null) {
            handledMessageIds += last.id
            _ui.value = _ui.value.copy(sending = true, error = null, errorRes = null)
            // Only the thumbnail is kept in history, so a delayed photo question is answered by text.
            viewModelScope.launch { answer(last.content, imageBytes = null) }
        }
    }

    fun send(userText: String, imageBytes: ByteArray? = null, thumbnailBytes: ByteArray? = null) {
        val trimmed = userText.trim()
        if ((trimmed.isBlank() && imageBytes == null) || _ui.value.sending) return
        val text = trimmed.ifEmpty { container.appContext.getString(R.string.coach_image_only_message) }
        val userMsg = ChatMessage(
            role = ChatMessage.Role.USER,
            content = text,
            attachmentImageBase64 = thumbnailBytes?.let { Base64.getEncoder().encodeToString(it) }
        )
        // Mark busy before the message lands so the history observer never auto-answers it too.
        handledMessageIds += userMsg.id
        _ui.value = _ui.value.copy(sending = true, error = null, errorRes = null)
        viewModelScope.launch {
            container.chatRepository.append(userMsg)
            // No model yet: the message stays in the chat and the card offers a model.
            if (container.aiAvailability.first() == AiAvailability.MODEL_MISSING) {
                // Not answered: once the model is downloaded, it gets the automatic answer.
                handledMessageIds -= userMsg.id
                _ui.value = _ui.value.copy(sending = false)
                evaluateModelAction()
                return@launch
            }
            answer(text, imageBytes)
        }
    }

    /** "Install the local model": makes it the active AI and downloads it over any connection. */
    fun installLocalModel() {
        if (!container.onDeviceSupported) return
        viewModelScope.launch {
            container.prefs.useEmbeddedModel()
            val entry = ModelCatalog.default
            if (!container.onDeviceModelDownloadManager.isDownloaded(entry)) {
                container.onDeviceModelDownloadManager.startDownload(entry, overWifiOnly = false)
            }
        }
    }

    fun showRemoteHelp(show: Boolean) {
        _ui.value = _ui.value.copy(showRemoteHelp = show)
    }

    /** Instructions → Profile → AI Provider (same inbox the shbd://go/settings/ai link uses). */
    fun openAiProvider() {
        _ui.value = _ui.value.copy(showRemoteHelp = false)
        container.launchDestinationInbox.value = app.shbd.ui.navigation.ShbdRoutes.SETTINGS_AI
    }

    fun cancelModelDownload() {
        container.onDeviceModelDownloadManager.cancelDownload()
    }

    /** Asks the coach about the last user message [text] (already in the history). */
    private suspend fun answer(text: String, imageBytes: ByteArray?) {
        try {
            val history = container.chatRepository.contextMessages(limit = 20).dropLast(1) // exclude the just-appended user msg — it's passed separately
            val profile = container.profileRepository.current()
                ?: return run {
                    _ui.value = _ui.value.copy(
                        sending = false,
                        errorRes = R.string.coach_no_profile_error
                    )
                }
            val weights = container.weightRepository.entries.first()
            val bodyFats = container.bodyFatRepository.entries.first()
            val measurements = container.bodyMeasurementRepository.entries.first()
            val foods = container.foodRepository.entries.first()
            val heightMetric = container.prefs.heightUnit.first() == "cm"
            val weightMetric = container.prefs.weightUnit.first() == "kg"

            val result = container.chatService.sendMessage(
                history = history,
                newUserMessage = text,
                profile = profile,
                weights = weights,
                bodyFats = bodyFats,
                measurements = measurements,
                foods = foods,
                heightMetric = heightMetric,
                weightMetric = weightMetric,
                imageBytes = imageBytes
            )
            container.chatRepository.append(ChatMessage(role = ChatMessage.Role.ASSISTANT, content = result.reply.trim()))
            _ui.value = _ui.value.copy(
                sending = false,
                pendingFood = result.proposedFood,
                pendingWeight = result.proposedWeight,
                pendingWater = result.proposedWater,
            )
        } catch (e: AiError) {
            _ui.value = _ui.value.copy(sending = false, error = e.userMessage(container.appContext))
        } catch (e: Throwable) {
            _ui.value = _ui.value.copy(
                sending = false,
                error = if (BuildConfig.DEBUG) e.javaClass.simpleName else null,
                errorRes = R.string.coach_chat_failed,
            )
        }
    }

    /** Surfaces a voice-input failure (mic permission / transcription) in the chat error row. */
    fun onVoiceError(res: Int) {
        _ui.value = _ui.value.copy(error = null, errorRes = res)
    }

    fun confirmPendingFood() {
        val entry = _ui.value.pendingFood ?: return
        viewModelScope.launch {
            val unique = entry.copy(
                name = disambiguateFoodName(
                    entry.name,
                    container.foodRepository.existingFoodIdentityKeys(),
                )
            )
            container.foodRepository.addEntry(unique)
            val unit = EnergyUnit.fromStorage(container.prefs.energyUnit.first())
            val ctx = container.appContext
            val unitLabel = ctx.getString(if (unit == EnergyUnit.KJ) R.string.unit_kj else R.string.unit_kcal)
            container.chatRepository.append(
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = ctx.getString(
                        R.string.coach_logged_food,
                        unique.name,
                        EnergyFormat.quantity(unique.calories, unit).toString(),
                        unitLabel,
                    ),
                )
            )
            _ui.value = _ui.value.copy(pendingFood = null)
        }
    }

    fun confirmPendingWeight() {
        val entry = _ui.value.pendingWeight ?: return
        viewModelScope.launch {
            container.weightRepository.addEntry(entry)
            container.chatRepository.append(
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = container.appContext.getString(
                        R.string.coach_logged_weight,
                        LocaleFormat.decimal(entry.weightKg, 1),
                    ),
                )
            )
            _ui.value = _ui.value.copy(pendingWeight = null)
        }
    }

    fun confirmPendingWater() {
        val entry = _ui.value.pendingWater ?: return
        viewModelScope.launch {
            container.waterRepository.add(entry)
            container.chatRepository.append(
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = container.appContext.getString(
                        R.string.coach_logged_water,
                        entry.milliliters,
                    ),
                )
            )
            _ui.value = _ui.value.copy(pendingWater = null)
        }
    }

    /** Discards whichever pending proposal is currently set. */
    fun discardPending() {
        viewModelScope.launch {
            container.chatRepository.append(
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    content = container.appContext.getString(R.string.coach_logged_discard),
                )
            )
            _ui.value = _ui.value.copy(pendingFood = null, pendingWeight = null, pendingWater = null)
        }
    }

    fun resetConversation() {
        viewModelScope.launch { container.chatRepository.clear() }
    }

    fun dismissError() { _ui.value = _ui.value.copy(error = null, errorRes = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CoachViewModel(container) as T
    }
}
