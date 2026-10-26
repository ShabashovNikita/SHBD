package app.shbd.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.models.HomeTopNutrient
import app.shbd.models.WeekStartDay
import app.shbd.ui.diary.blocks.DiaryBlock
import app.shbd.ui.home.HomeViewModel
import app.shbd.ui.home.ManualActiveSheet
import app.shbd.ui.home.WaterCustomAmountSheet
import app.shbd.ui.home.dayTypeMarkerMaps
import app.shbd.ui.home.isoToLocalDates
import app.shbd.ui.home.weekStripDates
import app.shbd.ui.navigation.bottomNavContentPadding
import app.shbd.ui.progress.AddWeightDialog
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Diary tab dashboard (dashboard mockup): week card + Nutrition, Workouts, Weight, Steps,
 * Water and Sleep cards for the selected day.
 *
 * Shares [HomeViewModel] with the block screens (it is scoped to the HOME
 * back-stack entry), so the selected day and the calorie goal math stay single
 * sourced. Every card opens its block screen via [onOpenBlock]; `add = true`
 * opens Meals with the add-food menu already up.
 */
@Composable
fun DiaryDashboardScreen(
    container: AppContainer,
    onOpenBlock: (block: DiaryBlock, add: Boolean) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenHealthSettings: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val dashVm: DiaryDashboardViewModel = viewModel(factory = DiaryDashboardViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    val extras by dashVm.extras.collectAsState()
    val weekStartDay by container.prefs.weekStartDay.collectAsState(initial = WeekStartDay.MONDAY)
    val scope = rememberCoroutineScope()
    val selectedDate = ui.date
    val manualByDay by container.manualDayMetricsRepository.byDay.collectAsState(initial = emptyMap())
    val manualDay = manualByDay[selectedDate]

    LaunchedEffect(selectedDate) { dashVm.load(selectedDate) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                vm.refreshActivitySnapshot()
                dashVm.load(vm.ui.value.date)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showManualActive by rememberSaveable { mutableStateOf(false) }
    var showWaterAdd by rememberSaveable { mutableStateOf(false) }
    var showWeightAdd by rememberSaveable { mutableStateOf(false) }
    var showStepsEdit by rememberSaveable { mutableStateOf(false) }
    var showSleepEdit by rememberSaveable { mutableStateOf(false) }

    val stripDates = remember(weekStartDay) { weekStripDates(weekStartDay) }
    val stripMarkers = remember(ui.profile, stripDates) { dayTypeMarkerMaps(ui.profile, vm.weekDayTypes(stripDates)) }
    val stripUntracked = remember(ui.untrackedDays) { isoToLocalDates(ui.untrackedDays) }

    val logged = remember(ui.todayEntries) { ui.todayEntries.filter { !it.planned } }
    val targets = ui.resolvedDayTargets
    // Fixed day goal: workouts never raise it (no burn credit, macro scale 1).
    fun macro(n: HomeTopNutrient): Pair<Double, Double> =
        n.current(logged) to n.goal(targets, ui.profile, ui.optionalNutrientGoals, 1f).toDouble()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomNavContentPadding(extra = 16.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.diary_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        item {
            WeekCard(
                selectedDate = selectedDate,
                weekStartDay = weekStartDay,
                onSelect = { vm.setSelectedDate(it) },
                typeColorsByDate = stripMarkers.first,
                typeNamesByDate = stripMarkers.second,
                untrackedDates = stripUntracked,
            )
        }
        item {
            NutritionCard(
                caloriesKcal = ui.loggedCaloriesToday,
                goalKcal = ui.baseCalorieGoal,
                protein = macro(HomeTopNutrient.PROTEIN),
                fat = macro(HomeTopNutrient.FAT),
                carbs = macro(HomeTopNutrient.CARBS),
                onOpenMeals = { onOpenBlock(DiaryBlock.NUTRITION, false) },
                onAddFood = { onOpenBlock(DiaryBlock.NUTRITION, true) },
                onChangeCalorieGoal = onOpenGoals,
            )
        }
        item {
            ActivityCard(
                exerciseMinutes = extras.exerciseMinutes,
                activeKcal = ui.liveActiveBurn,
                onOpenWorkouts = { onOpenBlock(DiaryBlock.ACTIVITY, false) },
                onAdd = { showManualActive = true },
            )
        }
        item {
            val weight = extras.latestWeight
            WeightDashboardCard(
                weightKg = weight?.weightKg,
                measuredOn = weight?.date?.atZone(ZoneId.systemDefault())?.toLocalDate(),
                useMetric = ui.weightMetric,
                onAdd = { showWeightAdd = true },
                onClick = { onOpenBlock(DiaryBlock.WEIGHT, false) },
            )
        }
        item {
            StepsDashboardCard(
                steps = manualDay?.steps ?: ui.activitySnapshot.steps,
                goal = ui.homeDisplay.stepGoal,
                granted = extras.stepsGranted,
                manual = manualDay?.steps != null,
                onClick = { onOpenBlock(DiaryBlock.STEPS, false) },
                onConnect = onOpenHealthSettings,
                onEdit = { showStepsEdit = true },
            )
        }
        item {
            WaterDashboardCard(
                currentMl = ui.waterTodayMl,
                goalMl = ui.waterDailyGoalMl,
                useMetric = ui.weightMetric,
                onAdd = { showWaterAdd = true },
                onClick = { onOpenBlock(DiaryBlock.WATER, false) },
            )
        }
        item {
            SleepDashboardCard(
                sleepMinutes = manualDay?.sleepMinutes ?: extras.sleepMinutes,
                granted = extras.sleepGranted,
                manual = manualDay?.sleepMinutes != null,
                onClick = { onOpenBlock(DiaryBlock.SLEEP, false) },
                onConnect = onOpenHealthSettings,
                onEdit = { showSleepEdit = true },
            )
        }
    }

    ManualMetricEditors(
        container = container,
        date = selectedDate,
        manual = manualDay,
        healthSteps = ui.activitySnapshot.steps,
        healthSleepMinutes = extras.sleepMinutes,
        showSteps = showStepsEdit,
        showSleep = showSleepEdit,
        onClose = { showStepsEdit = false; showSleepEdit = false },
    )

    if (showManualActive) {
        ManualActiveSheet(
            onSave = { name, kcal ->
                vm.addManualActive(name, kcal)
                showManualActive = false
            },
            onDismiss = { showManualActive = false },
            day = selectedDate,
        )
    }
    if (showWaterAdd) {
        WaterCustomAmountSheet(
            useMetric = ui.weightMetric,
            onDismiss = { showWaterAdd = false },
            onAdd = { ml ->
                vm.addWater(ml)
                showWaterAdd = false
            },
        )
    }
    if (showWeightAdd) {
        AddWeightDialog(
            useMetric = ui.weightMetric,
            initialKg = extras.latestWeight?.weightKg ?: DEFAULT_WEIGHT_KG,
            onDismiss = { showWeightAdd = false },
        ) { kg, whenLogged ->
            dashVm.addWeight(kg, whenLogged)
            showWeightAdd = false
        }
    }
}

internal const val DEFAULT_WEIGHT_KG = 70.0
