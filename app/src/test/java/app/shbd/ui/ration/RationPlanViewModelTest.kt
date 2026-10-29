package app.shbd.ui.ration

import android.app.Application
import android.os.Looper
import app.shbd.models.RationMeal
import app.shbd.models.RationPlan
import app.shbd.services.ai.AiError
import app.shbd.services.ondevice.AiAvailability
import app.shbd.services.ration.RationPlanError
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Plan ViewModel on fakes: model gate (live), success, failures keep the saved plan. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RationPlanViewModelTest {
    private val store = MutableStateFlow<RationPlan?>(null)
    private var builds = 0
    private val today = LocalDate.of(2026, 11, 2)
    private val model = MutableStateFlow(AiAvailability.READY)
    private var installs = 0

    private fun vm(availability: AiAvailability = AiAvailability.READY, build: suspend (LocalDate) -> RationPlan): RationPlanViewModel {
        model.value = availability
        return RationPlanViewModel(
            plan = store,
            availability = model,
            build = { builds++; build(it) },
            today = { today },
            installLocal = { installs++ },
        ).also { idle() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun modelMissing_needModel_withoutBuilding() {
        val vm = vm(AiAvailability.MODEL_MISSING) { error("must not run") }
        vm.buildMenu(); idle()
        assertEquals(RationPlanPhase.NeedModel, vm.state.value.phase)
        assertEquals(0, builds)
    }

    @Test
    fun modelInstalledElsewhere_cardGoes_andMenuIsBuilt() {
        val plan = RationPlan(comment = "after model")
        val vm = vm(AiAvailability.MODEL_MISSING) { store.value = plan; plan }
        vm.buildMenu(); idle()
        assertEquals(RationPlanPhase.NeedModel, vm.state.value.phase)
        // E.g. the model was installed in Coach or Profile → AI.
        model.value = AiAvailability.READY; idle()
        assertEquals(RationPlanPhase.Idle, vm.state.value.phase)
        assertEquals(1, builds)
        assertEquals(plan, vm.state.value.plan)
    }

    @Test
    fun aiTurnedOff_cardGoes_withoutBuilding() {
        val vm = vm(AiAvailability.MODEL_MISSING) { error("must not run") }
        vm.buildMenu(); idle()
        model.value = AiAvailability.DISABLED; idle()
        assertEquals(RationPlanPhase.Idle, vm.state.value.phase)
        assertEquals(0, builds)
    }

    @Test
    fun modelReady_withoutRequest_doesNotBuild() {
        vm(AiAvailability.MODEL_MISSING) { error("must not run") }
        model.value = AiAvailability.READY; idle()
        assertEquals(0, builds)
    }

    @Test
    fun modelGoneDuringBuild_showsCard() {
        val vm = vm { throw AiError.OnDeviceModelNotDownloaded }
        vm.buildMenu(); idle()
        assertEquals(RationPlanPhase.NeedModel, vm.state.value.phase)
    }

    @Test
    fun installLocal_andRemoteHelp() {
        val vm = vm(AiAvailability.MODEL_MISSING) { error("must not run") }
        vm.installLocalModel(); idle()
        assertEquals(1, installs)
        vm.showRemoteHelp(true); idle()
        assertTrue(vm.state.value.showRemoteHelp)
        vm.showRemoteHelp(false); idle()
        assertTrue(!vm.state.value.showRemoteHelp)
    }

    @Test
    fun success_idleWithSavedPlan() {
        val plan = RationPlan(startDate = "2026-11-02", comment = "ok")
        val vm = vm { day ->
            assertEquals(today, day)
            store.value = plan
            plan
        }
        assertEquals(null, vm.state.value.plan)
        vm.buildMenu(); idle()
        assertEquals(RationPlanPhase.Idle, vm.state.value.phase)
        assertEquals(plan, vm.state.value.plan)
        assertEquals(1, builds)
    }

    @Test
    fun aiError_failed_previousPlanStays() {
        val old = RationPlan(comment = "old")
        store.value = old
        val vm = vm { throw AiError.Timeout }
        vm.buildMenu(); idle()
        val phase = vm.state.value.phase
        assertTrue(phase is RationPlanPhase.Failed && phase.failure is RationPlanFailure.Ai)
        assertEquals(old, vm.state.value.plan)
    }

    @Test
    fun noDishes_andBadAnswer_mapped() {
        val vm1 = vm { throw RationPlanError.NoDishes(setOf(RationMeal.DINNER)) }
        vm1.buildMenu(); idle()
        val f1 = (vm1.state.value.phase as RationPlanPhase.Failed).failure
        assertEquals(setOf(RationMeal.DINNER), (f1 as RationPlanFailure.NoDishes).meals)

        val vm2 = vm { throw RationPlanError.BadAnswer }
        vm2.buildMenu(); idle()
        assertEquals(RationPlanFailure.BadAnswer, (vm2.state.value.phase as RationPlanPhase.Failed).failure)
    }
}
