package app.shbd.ui.settings

import android.app.Application
import app.shbd.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Formatted AI-model strings take the argument types their call sites pass,
 * and no message points at a "Settings" screen: the app has the Profile tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EmbeddedAiStringsTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun english_formatsWithCallSiteArgs() {
        assertEquals("Downloading the model… 42%", context.getString(R.string.coach_model_downloading, 42))
        assertEquals("Download failed: boom", context.getString(R.string.coach_model_failed, "boom"))
        assertEquals("Install the local model (2.6 GB)", context.getString(R.string.coach_model_install_local, "2.6 GB"))
    }

    @Test
    @Config(qualifiers = "ru")
    fun russian_formatsWithCallSiteArgs() {
        assertEquals("Скачиваю модель… 42 %", context.getString(R.string.coach_model_downloading, 42))
        assertEquals("Установить локальную модель (2,6 ГБ)", context.getString(R.string.coach_model_install_local, "2,6 ГБ"))
        assertEquals(
            "Для разбора еды нужна модель ИИ. Установите локальную модель или подключите удалённую во вкладке «Тренер».",
            context.getString(R.string.model_required_food),
        )
    }

    @Test
    @Config(qualifiers = "ru")
    fun remoteSetupSteps_areSeparateLines() {
        val steps = context.getString(R.string.coach_remote_setup_steps)
        assertEquals(5, steps.lines().size)
        assert(steps.contains("Профиль → ИИ-провайдер"))
    }

    @Test
    fun noMessagePointsAtSettingsScreen() {
        // Unit tests run with the module dir as the working directory.
        for (path in listOf("src/main/res/values/strings.xml", "src/main/res/values-ru/strings.xml")) {
            val xml = File(path).readText()
            assertFalse("$path mentions Настройки →", xml.contains("Настройки →"))
            // "System Settings → Apps → …" is Android's own settings app and stays.
            val appSettings = Regex("(?<!System )Settings →").find(xml)
            assertEquals("$path mentions Settings →", null, appSettings?.value)
        }
    }
}
