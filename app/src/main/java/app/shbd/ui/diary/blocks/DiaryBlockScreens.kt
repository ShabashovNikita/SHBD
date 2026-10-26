package app.shbd.ui.diary.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.models.BodyMeasurement
import app.shbd.models.EnergyFormat
import app.shbd.models.HomeTopNutrient
import app.shbd.models.LocaleFormat
import app.shbd.models.ManualActiveEntry
import app.shbd.models.UnitFormat
import app.shbd.models.WaterAmountFormat
import app.shbd.models.WaterEntry
import app.shbd.ui.components.ShbdDialog
import app.shbd.ui.components.ShbdDialogActions
import app.shbd.ui.components.energyUnitLabel
import app.shbd.ui.diary.ActivityValues
import app.shbd.ui.diary.ManualMetricEditors
import app.shbd.ui.diary.SleepValues
import app.shbd.ui.diary.StepsValues
import app.shbd.ui.diary.DEFAULT_WEIGHT_KG
import app.shbd.ui.diary.DashboardEmptyText
import app.shbd.ui.diary.DiaryDashboardMath
import app.shbd.ui.diary.NutritionSummaryContent
import app.shbd.ui.diary.SummaryCard
import app.shbd.ui.diary.ValueOfGoal
import app.shbd.ui.home.HomeBlockMode
import app.shbd.ui.home.HomeScreen
import app.shbd.ui.home.HomeViewModel
import app.shbd.ui.home.ManualActiveSheet
import app.shbd.ui.home.WaterCustomAmountSheet
import app.shbd.ui.home.WaterEditAmountSheet
import app.shbd.ui.navigation.LocalEnergyUnit
import app.shbd.ui.progress.AddBodyFatDialog
import app.shbd.ui.progress.AddWeightDialog
import app.shbd.ui.progress.AllBodyFatHistorySheet
import app.shbd.ui.progress.AllWeightHistorySheet
import app.shbd.ui.progress.BodyFatHistoryLink
import app.shbd.ui.progress.BodyMeasurementEntryDialog
import app.shbd.ui.progress.BodySection
import app.shbd.ui.progress.MeasurementPlotCard
import app.shbd.ui.progress.MeasurementChartCanvas
import app.shbd.ui.progress.TrendPoint
import app.shbd.ui.progress.NutrientAveragesSection
import app.shbd.ui.progress.ProgressAdherenceSection
import app.shbd.ui.progress.ProgressViewModel
import app.shbd.ui.progress.TimeRange
import app.shbd.ui.progress.WeightHistoryLink
import app.shbd.ui.theme.AppTextOpacity
import app.shbd.ui.theme.ShbdTheme
import app.shbd.ui.theme.dayTypeColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import app.shbd.models.NutritionConstants
import app.shbd.ui.progress.CalorieSlot
import app.shbd.ui.progress.ChartPeriod
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val StepsColor = Color(0xFF42A5F5)
private val WorkoutColor = Color(0xFFFF8A65)
private val WaterColor = Color(0xFF29B6F6)
private val SleepColor = Color(0xFF7E57C2)

/** Navigation callbacks a block screen may need. */
class DiaryBlockActions(
    val onBack: () -> Unit,
    val onOpenHealthSettings: () -> Unit,
    val onOpenBodyMeasurements: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDayTypes: () -> Unit,
    val onOpenFoodSettings: () -> Unit,
    val onPlanWeek: () -> Unit,
)

/**
 * One Diary block screen (block-screen mockup). Must be composed with the HOME back-stack
 * entry as the ViewModelStoreOwner: HomeViewModel (selected day) and
 * ProgressViewModel (chart range) are shared with the dashboard and the other blocks.
 */
@Composable
fun DiaryBlockScreen(
    block: DiaryBlock,
    container: AppContainer,
    openAddOnStart: Boolean,
    actions: DiaryBlockActions,
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val progressVm: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory(container))
    AnchorProgressToSelectedDay(vm, progressVm)
    when (block) {
        DiaryBlock.NUTRITION -> NutritionBlockScreen(container, vm, progressVm, openAddOnStart, actions)
        DiaryBlock.ACTIVITY -> ActivityBlockScreen(container, vm, progressVm, actions)
        DiaryBlock.STEPS -> HealthBlockScreen(DiaryBlock.STEPS, container, vm, progressVm, actions)
        DiaryBlock.SLEEP -> HealthBlockScreen(DiaryBlock.SLEEP, container, vm, progressVm, actions)
        DiaryBlock.WATER -> WaterBlockScreen(container, vm, progressVm, actions)
        DiaryBlock.WEIGHT -> WeightBlockScreen(container, vm, progressVm, actions)
    }
}

// region Meals

@Composable
private fun NutritionBlockScreen(
    container: AppContainer,
    vm: HomeViewModel,
    progressVm: ProgressViewModel,
    openAddOnStart: Boolean,
    actions: DiaryBlockActions,
) {
    val ui by vm.ui.collectAsState()
    val progress by progressVm.ui.collectAsState()
    val logged = remember(ui.todayEntries) { ui.todayEntries.filter { !it.planned } }
    val targets = ui.resolvedDayTargets
    // Same fixed day goal as the dashboard: workouts never raise it.
    fun macro(n: HomeTopNutrient): Pair<Double, Double> =
        n.current(logged) to n.goal(targets, ui.profile, ui.optionalNutrientGoals, 1f).toDouble()
    val protein = macro(HomeTopNutrient.PROTEIN)
    val fat = macro(HomeTopNutrient.FAT)
    val carbs = macro(HomeTopNutrient.CARBS)

    val blockMode = HomeBlockMode(
        header = {
            item { Box16 { DiaryBlockHeader(DiaryBlock.NUTRITION, actions.onBack) } }
            item { Box16 { SelectedDayWeekCard(container, vm) } }
            item {
                Box16(top = 12.dp) {
                    SummaryCard {
                        NutritionSummaryContent(
                            caloriesKcal = ui.loggedCaloriesToday,
                            goalKcal = ui.baseCalorieGoal,
                            protein = protein,
                            fat = fat,
                            carbs = carbs,
                        )
                    }
                }
            }
        },
        emptyContent = { Box16(top = 4.dp) { SummaryCard { DashboardEmptyText() } } },
        footer = {
            item {
                // The calorie deficit chart (moved here from the Weight block) replaces
                // the old "Progress" calorie bars.
                Box16(top = 16.dp) {
                    BlockChartCard(progressVm, title = stringResource(R.string.diary_deficit_chart_title)) { range ->
                        CalorieDeficitChart(
                            normKcal = progress.profile?.tdee?.roundToInt(),
                            calorieSlots = progress.calorieSlots,
                            workouts = workoutsByDay(ui.healthEnergyActiveByDay, ui.manualActiveByDay),
                            period = ChartPeriod(range, ui.date),
                        )
                    }
                }
            }
            progress.adherence?.let { adherence ->
                item {
                    Box16(top = 12.dp) {
                        SummaryCard { ProgressAdherenceSection(adherence = adherence, plan = progress.profile?.macroPlan) }
                    }
                }
            }
            if (progress.profile != null) {
                // No average-macros card: the summary above already shows the day's macros
                // against their goals. Optional nutrient averages stay.
                if (progress.showNutrientAverages) {
                    item {
                        Box16(top = 12.dp) {
                            SummaryCard { NutrientAveragesSection(nutrientAverages = progress.nutrientAverages) }
                        }
                    }
                }
            }
        },
    )
    HomeScreen(
        container = container,
        onOpenSettings = actions.onOpenSettings,
        onOpenDayTypes = actions.onOpenDayTypes,
        onOpenFoodSettings = actions.onOpenFoodSettings,
        onPlanWeek = actions.onPlanWeek,
        openAddFoodOnStart = openAddOnStart,
        blockMode = blockMode,
    )
}

/** HomeScreen list items carry no side padding of their own; block cards need 16dp gutters. */
@Composable
private fun Box16(top: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = top)) { content() }
}

// endregion

/**
 * Meals block chart: daily calorie deficit = (norm − eaten) + workouts, where the
 * norm is the maintenance TDEE. Same line chart as the weight and measurement plots
 * (trend line, raw dots, tap to inspect, x axis over the selected period, first day
 * with data on the left); negative values are a surplus. Days without logged food
 * have no point. Below: the period total and its weight equivalent (7 700 kcal ≈ 1 kg).
 * The title and range chips come from the surrounding [BlockChartCard].
 */
@Composable
private fun CalorieDeficitChart(
    normKcal: Int?,
    calorieSlots: List<CalorieSlot>,
    workouts: Map<LocalDate, Int>,
    period: ChartPeriod,
) {
    val unit = LocalEnergyUnit.current
    val kcalLabel = energyUnitLabel()
    val zone = remember { ZoneId.systemDefault() }
    val deficit = remember(normKcal, calorieSlots, workouts) {
        if (normKcal == null) {
            emptyMap()
        } else {
            val eaten = calorieSlots
                .filter { !it.untracked }
                .mapNotNull { slot -> slot.kcal?.let { slot.day to it } }
                .toMap()
            deficitByDay(normKcal, eaten, workouts)
        }
    }
    val points = remember(deficit, zone) {
        deficit.entries
            .sortedBy { it.key }
            .map { (day, kcal) -> TrendPoint(day.atStartOfDay(zone).toInstant().toEpochMilli(), kcal) }
    }
    val title = stringResource(R.string.diary_deficit_chart_title)
    fun kcalText(v: Double) = LocaleFormat.integer(EnergyFormat.quantity(v.roundToInt(), unit))
    Column {
        if (points.isEmpty()) {
            DashboardEmptyText()
        } else {
            MeasurementChartCanvas(
                series = points,
                tagFormatter = { "${kcalText(it)} $kcalLabel" },
                title = title,
                period = period,
                lineColor = MaterialTheme.colorScheme.primary,
            )
            val total = points.sumOf { it.value }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    if (total >= 0) R.string.diary_deficit_total else R.string.diary_surplus_total,
                    kcalText(kotlin.math.abs(total)),
                    kcalLabel,
                    stringResource(R.string.kg_value_format, kotlin.math.abs(total) / NutritionConstants.KCAL_PER_KG_BODY_MASS),
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// region Health Connect blocks (Steps, Sleep) and Workouts

/** Loads the Health Connect series for the current range/day and re-reads on app resume. */
@Composable
private fun rememberBlockHealth(
    container: AppContainer,
    end: LocalDate,
    range: TimeRange,
): BlockHealthState {
    val blockVm: DiaryBlockViewModel = viewModel(key = "diary-block-health", factory = DiaryBlockViewModel.Factory(container))
    val state by blockVm.health.collectAsState()
    LaunchedEffect(range, end) { blockVm.load(range, end) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, range, end) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) blockVm.load(range, end, force = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
private fun HealthBlockScreen(
    block: DiaryBlock,
    container: AppContainer,
    vm: HomeViewModel,
    progressVm: ProgressViewModel,
    actions: DiaryBlockActions,
) {
    val ui by vm.ui.collectAsState()
    val progress by progressVm.ui.collectAsState()
    val health = rememberBlockHealth(container, ui.date, progress.timeRange)
    val day = health.days[ui.date]
    val manualByDay by container.manualDayMetricsRepository.byDay.collectAsState(initial = emptyMap())
    val manualDay = manualByDay[ui.date]
    var showEdit by rememberSaveable { mutableStateOf(false) }
    // Hand-typed values win over Health Connect on the chart too.
    val overrides = remember(manualByDay, block) {
        manualByDay.mapNotNull { (date, m) ->
            val v = if (block == DiaryBlock.STEPS) m.steps?.toDouble() else m.sleepMinutes?.let { it / 60.0 }
            v?.let { date to it }
        }.toMap()
    }
    val hoursFormat = stringResource(R.string.diary_block_hours, "%s")
    DiaryBlockScaffold(block, container, vm, actions.onBack, onAdd = { showEdit = true }) {
        item {
            SummaryCard {
                if (block == DiaryBlock.STEPS) {
                    StepsValues(
                        steps = manualDay?.steps ?: ui.activitySnapshot.steps.takeIf { it > 0 } ?: day?.steps ?: 0L,
                        goal = ui.homeDisplay.stepGoal,
                        granted = health.stepsGranted || health.loading,
                        manual = manualDay?.steps != null,
                        onConnect = actions.onOpenHealthSettings,
                        barColor = StepsColor,
                    )
                } else {
                    SleepValues(
                        sleepMinutes = manualDay?.sleepMinutes ?: day?.sleepMinutes,
                        granted = health.sleepGranted || health.loading,
                        manual = manualDay?.sleepMinutes != null,
                        onConnect = actions.onOpenHealthSettings,
                    )
                }
            }
        }
        item {
            BlockChartCard(progressVm) { range ->
                HealthChart(health, range, ui.date, overrides) { values ->
                    if (block == DiaryBlock.STEPS) {
                        DiaryBarChart(
                            series = values { it.steps },
                            range = range,
                            color = StepsColor,
                            title = stringResource(R.string.diary_card_steps),
                            formatValue = { LocaleFormat.integer(it.toLong()) },
                            goal = ui.homeDisplay.stepGoal.takeIf { it > 0 }?.toDouble(),
                        )
                    } else {
                        DiaryBarChart(
                            series = values { p -> p.sleepMinutes?.let { it / 60.0 } },
                            range = range,
                            color = SleepColor,
                            title = stringResource(R.string.diary_card_sleep),
                            formatValue = { hoursFormat.format(LocaleFormat.decimal(it, 1)) },
                        )
                    }
                }
            }
        }
    }
    ManualMetricEditors(
        container = container,
        date = ui.date,
        manual = manualDay,
        healthSteps = ui.activitySnapshot.steps.takeIf { it > 0 } ?: day?.steps,
        healthSleepMinutes = day?.sleepMinutes,
        showSteps = showEdit && block == DiaryBlock.STEPS,
        showSleep = showEdit && block == DiaryBlock.SLEEP,
        onClose = { showEdit = false },
    )
}

/**
 * Chart body for a Health Connect block: placeholder while loading, then the
 * bucketed series. [content] receives a builder turning a per-day metric into
 * the bucketed chart series; [overrides] (hand-typed days) replace Health Connect.
 */
@Composable
private fun HealthChart(
    health: BlockHealthState,
    range: TimeRange,
    end: LocalDate,
    overrides: Map<LocalDate, Double> = emptyMap(),
    content: @Composable ((metric: (app.shbd.services.health.DailySeriesPoint) -> Number?) -> List<DayValue>) -> Unit,
) {
    if (health.loading && health.days.isEmpty() && overrides.isEmpty()) {
        app.shbd.ui.progress.ChartPlaceholder(height = 160.dp)
        return
    }
    content { metric ->
        val healthValues = positiveByDay(health.days.values.toList(), { it.date }, metric)
        bucketSeries(daySeries(range, end, withOverrides(healthValues, overrides)), range)
    }
}

@Composable
private fun BlockProgressBar(current: Double, goal: Double, color: Color) {
    LinearProgressIndicator(
        progress = { DiaryDashboardMath.progressFraction(current, goal) },
        color = color,
        strokeCap = StrokeCap.Round,
        drawStopIndicator = {},
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().height(10.dp),
    )
}

@Composable
private fun ActivityBlockScreen(
    container: AppContainer,
    vm: HomeViewModel,
    progressVm: ProgressViewModel,
    actions: DiaryBlockActions,
) {
    val ui by vm.ui.collectAsState()
    val progress by progressVm.ui.collectAsState()
    val health = rememberBlockHealth(container, ui.date, progress.timeRange)
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ManualActiveEntry?>(null) }
    val minutesFormat = stringResource(R.string.diary_block_minutes, "%s")
    DiaryBlockScaffold(DiaryBlock.ACTIVITY, container, vm, actions.onBack, onAdd = { showAdd = true }) {
        item {
            SummaryCard {
                ActivityValues(
                    exerciseMinutes = health.days[ui.date]?.exerciseMinutes,
                    activeKcal = ui.liveActiveBurn,
                )
                if (!health.exerciseGranted && !health.loading) {
                    TextConnect(actions.onOpenHealthSettings)
                }
            }
        }
        item {
            ManualActiveList(
                entries = ui.manualActiveTodayEntries,
                onEdit = { editing = it },
                onDelete = { vm.deleteManualActive(it.id) },
            )
        }
        item {
            BlockChartCard(progressVm) { range ->
                HealthChart(health, range, ui.date) { values ->
                    DiaryBarChart(
                        series = values { it.exerciseMinutes },
                        range = range,
                        color = WorkoutColor,
                        title = stringResource(R.string.diary_workouts_link),
                        formatValue = { minutesFormat.format(LocaleFormat.integer(it.toLong())) },
                    )
                }
            }
        }
    }
    if (showAdd || editing != null) {
        val entry = editing
        ManualActiveSheet(
            onSave = { name, kcal ->
                if (entry != null) vm.updateManualActive(entry.id, name, kcal) else vm.addManualActive(name, kcal)
                showAdd = false
                editing = null
            },
            onDismiss = {
                showAdd = false
                editing = null
            },
            initial = entry,
            day = ui.date,
        )
    }
}


@Composable
private fun TextConnect(onConnect: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onConnect) {
        Text(stringResource(R.string.diary_connect_health))
    }
}

/** Manual activity entries of the selected day; tap edits, the bin deletes. Hidden when empty. */
@Composable
internal fun ManualActiveList(
    entries: List<ManualActiveEntry>,
    onEdit: (ManualActiveEntry) -> Unit,
    onDelete: (ManualActiveEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    val unit = LocalEnergyUnit.current
    EntryListCard(title = stringResource(R.string.diary_block_entries)) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            EntryRow(
                title = entry.name.ifBlank { stringResource(R.string.diary_card_activity) },
                value = "${LocaleFormat.integer(EnergyFormat.quantity(entry.calories, unit))} ${energyUnitLabel()}",
                onClick = { onEdit(entry) },
                onDelete = { onDelete(entry) },
            )
        }
    }
}

// endregion

// region Water

@Composable
private fun WaterBlockScreen(
    container: AppContainer,
    vm: HomeViewModel,
    progressVm: ProgressViewModel,
    actions: DiaryBlockActions,
) {
    val ui by vm.ui.collectAsState()
    val allWater by container.waterRepository.entries.collectAsState(initial = emptyList())
    val byDay = remember(allWater) { waterByDay(allWater) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WaterEntry?>(null) }
    val useMetric = ui.weightMetric
    DiaryBlockScaffold(DiaryBlock.WATER, container, vm, actions.onBack, onAdd = { showAdd = true }) {
        item {
            SummaryCard { WaterSummary(ui.waterTodayMl, ui.waterDailyGoalMl, useMetric) }
        }
        item {
            WaterPortionList(
                entries = ui.waterTodayEntries,
                useMetric = useMetric,
                onEdit = { editing = it },
                onDelete = { vm.deleteWater(it.id) },
            )
        }
        item {
            BlockChartCard(progressVm) { range ->
                val context = LocalContext.current
                DiaryBarChart(
                    series = bucketSeries(daySeries(range, ui.date, byDay), range),
                    range = range,
                    color = WaterColor,
                    title = stringResource(R.string.diary_card_water),
                    formatValue = { ml ->
                        val amount = ml.toInt()
                        if (useMetric) context.getString(R.string.water_amount_ml, amount)
                        else context.getString(R.string.water_amount_fl_oz, WaterAmountFormat.flOzFromMl(amount))
                    },
                    // Bars and goal stay in ml; the tap label shows the user's unit.
                    goal = ui.waterDailyGoalMl.takeIf { it > 0 }?.toDouble(),
                )
            }
        }
    }
    if (showAdd) {
        WaterCustomAmountSheet(
            useMetric = useMetric,
            onDismiss = { showAdd = false },
            onAdd = { ml ->
                vm.addWater(ml)
                showAdd = false
            },
        )
    }
    editing?.let { entry ->
        WaterEditAmountSheet(
            entry = entry,
            useMetric = useMetric,
            onDismiss = { editing = null },
            onSave = { ml -> vm.updateWater(entry.id, ml) },
        )
    }
}

/** "250 ml" / "8 fl oz" from millilitres. */
@Composable
private fun waterAmountText(ml: Int, useMetric: Boolean): String =
    if (useMetric) stringResource(R.string.water_amount_ml, ml)
    else stringResource(R.string.water_amount_fl_oz, WaterAmountFormat.flOzFromMl(ml))

@Composable
internal fun WaterSummary(currentMl: Int, goalMl: Int, useMetric: Boolean) {
    if (currentMl <= 0) {
        DashboardEmptyText()
        return
    }
    val current = if (useMetric) currentMl else WaterAmountFormat.flOzFromMl(currentMl)
    ValueOfGoal(
        value = LocaleFormat.integer(current),
        rest = if (goalMl > 0) "/ ${waterAmountText(goalMl, useMetric)}" else null,
    )
    if (goalMl > 0) {
        Spacer(Modifier.height(12.dp))
        BlockProgressBar(currentMl.toDouble(), goalMl.toDouble(), WaterColor)
    }
}

@Composable
internal fun WaterPortionList(
    entries: List<WaterEntry>,
    useMetric: Boolean,
    onEdit: (WaterEntry) -> Unit,
    onDelete: (WaterEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    val timeFmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val zone = remember { ZoneId.systemDefault() }
    EntryListCard(title = stringResource(R.string.diary_block_portions)) {
        entries.sortedBy { it.date }.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            EntryRow(
                title = entry.date.atZone(zone).toLocalTime().format(timeFmt),
                value = waterAmountText(entry.milliliters, useMetric),
                onClick = { onEdit(entry) },
                onDelete = { onDelete(entry) },
            )
        }
    }
}

// endregion

// region Weight

@Composable
private fun WeightBlockScreen(
    container: AppContainer,
    vm: HomeViewModel,
    progressVm: ProgressViewModel,
    actions: DiaryBlockActions,
) {
    val ui by vm.ui.collectAsState()
    val progress by progressVm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val weightMetric = progress.weightUnit == "kg"
    val heightUnit by container.prefs.heightUnit.collectAsState(initial = "cm")
    val heightMetric = heightUnit == "cm"
    val allWeights by container.weightRepository.entries.collectAsState(initial = emptyList())
    val allBodyFats by container.bodyFatRepository.entries.collectAsState(initial = emptyList())
    val allMeasurements by container.bodyMeasurementRepository.entries.collectAsState(initial = emptyList())
    val latest = remember(allWeights, ui.date) { DiaryDashboardMath.latestWeightOnOrBefore(allWeights, ui.date) }
    val latestBodyFat = remember(allBodyFats, ui.date) {
        val endOfDay = ui.date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        allBodyFats.filter { it.date.isBefore(endOfDay) }.maxByOrNull { it.date }
    }
    var showAddWeight by rememberSaveable { mutableStateOf(false) }
    var showAddBodyFat by rememberSaveable { mutableStateOf(false) }
    var showAllWeights by rememberSaveable { mutableStateOf(false) }
    var showAllBodyFats by rememberSaveable { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<BodyMeasurement.Site?>(null) }
    val bodyFatAvailable = progress.bodyFatCount > 0 ||
        progress.profile?.bodyFatPercentage != null ||
        progress.profile?.goalBodyFatPercentage != null
    val typeColorOf: (String) -> Color = remember(progress.profile) {
        val plan = progress.profile?.macroPlan
        { id: String -> dayTypeColor(plan?.profileById(id)?.colorKey, id) }
    }
    val sites = BodyMeasurement.Site.values().filter { it in progress.measurementSites }
    // Charts span the whole selected period (Week = 7 days, Month = 30) from the first reading.
    val period = ChartPeriod(progress.timeRange, ui.date)

    DiaryBlockScaffold(DiaryBlock.WEIGHT, container, vm, actions.onBack, onAdd = { showAddWeight = true }) {
        item {
            SummaryCard {
                WeightSummary(
                    weightKg = latest?.weightKg,
                    measuredOn = latest?.date?.atZone(ZoneId.systemDefault())?.toLocalDate(),
                    bodyFatFraction = latestBodyFat?.bodyFatFraction,
                    useMetric = weightMetric,
                )
            }
        }
        item {
            BodySection(
                weights = progress.filteredWeights,
                bodyFats = progress.filteredBodyFats,
                measurements = progress.filteredMeasurements,
                sites = sites,
                selectedKeys = progress.selectedBodySeries,
                onToggleSeries = progressVm::toggleBodySeries,
                weightStats = progress.weightStats,
                bodyFatStats = progress.bodyFatStats,
                profileBodyFatFraction = progress.profile?.bodyFatPercentage,
                goalWeightKg = progress.profile?.goalWeightKg,
                goalBodyFatFraction = progress.profile?.goalBodyFatPercentage,
                useMetricWeight = weightMetric,
                useMetricLength = heightMetric,
                bodyFatAvailable = bodyFatAvailable,
                onLogWeight = { showAddWeight = true },
                onLogBodyFat = { showAddBodyFat = true },
                dayTypeByDay = progress.dayTypeByDay,
                untrackedDays = progress.untrackedDays,
                typeColorOf = typeColorOf,
                period = period,
                title = stringResource(R.string.diary_card_weight),
                weightLocked = true,
                rangePicker = { DiaryRangePicker(progressVm) },
            )
        }
        if (progress.weightCount > 0) {
            item { WeightHistoryLink(count = progress.weightCount) { showAllWeights = true } }
        }
        if (progress.bodyFatCount > 0) {
            item { BodyFatHistoryLink(count = progress.bodyFatCount) { showAllBodyFats = true } }
        }
        sites.forEach { site ->
            item {
                MeasurementPlotCard(
                    site = site,
                    entries = progress.filteredMeasurements,
                    useMetric = heightMetric,
                    dayTypeByDay = progress.dayTypeByDay,
                    untrackedDays = progress.untrackedDays,
                    typeColorOf = typeColorOf,
                    onLog = { editingSite = site },
                    period = period,
                    rangePicker = { DiaryRangePicker(progressVm) },
                )
            }
        }
        item {
            LinkRow(stringResource(R.string.diary_block_body_measurements), actions.onOpenBodyMeasurements)
        }
    }

    if (showAddWeight) {
        AddWeightDialog(
            useMetric = weightMetric,
            initialKg = latest?.weightKg ?: progress.profile?.weightKg ?: DEFAULT_WEIGHT_KG,
            onDismiss = { showAddWeight = false },
        ) { kg, whenLogged ->
            progressVm.addWeightAt(kg, whenLogged)
            showAddWeight = false
        }
    }
    if (showAddBodyFat) {
        AddBodyFatDialog(
            initialFraction = progress.latestBodyFatFraction ?: progress.profile?.bodyFatPercentage ?: 0.20,
            onDismiss = { showAddBodyFat = false },
        ) { fraction, whenLogged ->
            progressVm.addBodyFatAt(fraction, whenLogged)
            showAddBodyFat = false
        }
    }
    editingSite?.let { site ->
        BodyMeasurementEntryDialog(
            site = site,
            currentCm = allMeasurements.maxByOrNull { it.date }?.value(site),
            useMetric = heightMetric,
            onSave = { cm ->
                progressVm.setBodyMeasurement(site, cm)
                editingSite = null
            },
            onDismiss = { editingSite = null },
        )
    }
    if (showAllWeights) {
        AllWeightHistorySheet(
            entries = allWeights.sortedByDescending { it.date },
            useMetric = weightMetric,
            onDelete = progressVm::deleteWeight,
            onDismiss = { showAllWeights = false },
        )
    }
    if (showAllBodyFats) {
        AllBodyFatHistorySheet(
            entries = allBodyFats.sortedByDescending { it.date },
            onDelete = progressVm::deleteBodyFat,
            onDismiss = { showAllBodyFats = false },
        )
    }
    if (progress.goalReached) {
        ShbdDialog(onDismissRequest = { progressVm.dismissGoalReached() }) {
            Text(stringResource(R.string.progress_goal_reached_title), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.progress_goal_reached_message),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Secondary),
            )
            ShbdDialogActions(
                primaryText = stringResource(R.string.action_keep_going),
                onPrimary = { progressVm.dismissGoalReached() },
            )
        }
    }
}

@Composable
internal fun WeightSummary(weightKg: Double?, measuredOn: LocalDate?, bodyFatFraction: Double?, useMetric: Boolean) {
    if (weightKg == null) {
        DashboardEmptyText()
        return
    }
    val text = UnitFormat.weight(weightKg, useMetric, LocaleFormat.displayLocale())
    ValueOfGoal(
        value = text.substringBeforeLast(' '),
        rest = stringResource(if (useMetric) R.string.unit_kg else R.string.unit_lbs),
    )
    if (measuredOn != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.diary_weight_measured, measuredOn.format(LocaleFormat.mediumDate())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (bodyFatFraction != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.diary_block_body_fat, "${LocaleFormat.decimal(bodyFatFraction * 100, 1)} %"),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// endregion

// region Shared rows

@Composable
private fun EntryListCard(title: String, content: @Composable () -> Unit) {
    SummaryCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun EntryRow(title: String, value: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.diary_block_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// endregion

@Preview(showBackground = true, name = "Block summaries — light")
@Preview(showBackground = true, name = "Block summaries — dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlockSummariesPreview() {
    val day = LocalDate.of(2026, 10, 25)
    ShbdTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DiaryBlockHeader(DiaryBlock.NUTRITION, onBack = {})
                SummaryCard {
                    NutritionSummaryContent(0, 1896, 0.0 to 95.0, 0.0 to 63.0, 0.0 to 237.0)
                }
                SummaryCard { DashboardEmptyText() }
                SummaryCard { StepsValues(steps = 8000, goal = 10_000, granted = true, manual = true, onConnect = {}) }
                SummaryCard { SleepValues(sleepMinutes = null, granted = false, manual = false, onConnect = {}) }
                SummaryCard { ActivityValues(exerciseMinutes = 45, activeKcal = 300) }
                ManualActiveList(
                    entries = listOf(ManualActiveEntry.forDay(day, "Swimming", 250)),
                    onEdit = {},
                    onDelete = {},
                )
                SummaryCard { WaterSummary(currentMl = 750, goalMl = 2000, useMetric = true) }
                SummaryCard { WeightSummary(72.4, day, 0.21, useMetric = true) }
            }
        }
    }
}
