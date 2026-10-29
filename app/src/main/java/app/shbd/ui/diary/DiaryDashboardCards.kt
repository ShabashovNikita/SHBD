package app.shbd.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.shbd.R
import app.shbd.models.EnergyFormat
import app.shbd.models.HomeTopNutrient
import app.shbd.models.LocaleFormat
import app.shbd.models.MacroValueFormatter
import app.shbd.models.UnitFormat
import app.shbd.models.WaterAmountFormat
import app.shbd.models.WeekStartDay
import app.shbd.ui.components.WeekEnergyStrip
import app.shbd.ui.components.energyUnitLabel
import app.shbd.ui.navigation.LocalEnergyUnit
import app.shbd.ui.theme.ShbdTheme
import app.shbd.ui.theme.nutrientAccentColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val CardShape = RoundedCornerShape(24.dp)
private val ButtonShape = RoundedCornerShape(16.dp)

/** One size for every big Diary number (calories, activity, steps, water, sleep, weight). */
internal val ValueFontSize = 30.sp

/**
 * Shared dashboard card (dashboard mockup): icon + title header, [content], and an
 * optional bottom row with a "link ›" pill and a square "+" button.
 */
@Composable
internal fun DashboardCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    linkLabel: String? = null,
    onLink: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                headerTrailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
            if (linkLabel != null || onAdd != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (linkLabel != null && onLink != null) {
                        Surface(
                            shape = ButtonShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = onLink,
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    linkLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (onAdd != null) {
                        Surface(
                            shape = ButtonShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = onAdd,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.diary_add),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "No data added" placeholder line used by every empty card except Nutrition. */
@Composable
internal fun DashboardEmptyText(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.diary_no_data),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Plain rounded card without a header — the block screens' summary cards (block-screen mockup). */
@Composable
internal fun SummaryCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { content() }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = modifier.fillMaxWidth(),
            content = inner,
        )
    } else {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = modifier.fillMaxWidth(),
            content = inner,
        )
    }
}

/** Big value + muted "/ goal unit" line ("0 / 1 896 kcal"). */
@Composable
internal fun ValueOfGoal(value: String, rest: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = ValueFontSize, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
        if (rest != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                rest,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** Month header + calendar picker + week strip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeekCard(
    selectedDate: LocalDate,
    weekStartDay: WeekStartDay,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    typeColorsByDate: Map<LocalDate, Color> = emptyMap(),
    typeNamesByDate: Map<LocalDate, String> = emptyMap(),
    untrackedDates: Set<LocalDate> = emptySet(),
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedDate.format(LocaleFormat.monthYear())
                        .replaceFirstChar { it.titlecase(LocaleFormat.displayLocale()) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showPicker = true }) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = stringResource(R.string.diary_pick_date),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            WeekEnergyStrip(
                selectedDate = selectedDate,
                onSelect = onSelect,
                weekStartDay = weekStartDay,
                typeColorsByDate = typeColorsByDate,
                typeNamesByDate = typeNamesByDate,
                untrackedDates = untrackedDates,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

/** One macro column: colored dot + label, thin bar, "current/goal g". */
@Composable
internal fun MacroColumn(nutrient: HomeTopNutrient, current: Double, goal: Double, modifier: Modifier = Modifier) {
    val color = nutrientAccentColor(nutrient)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(nutrient.displayNameRes), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { DiaryDashboardMath.progressFraction(current, goal) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(MacroValueFormatter.string(current), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "/${MacroValueFormatter.string(goal)} ${stringResource(R.string.unit_g)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "eaten / goal kcal" plus the three macro columns — shared by the dashboard
 * Nutrition card and the Meals block summary so the two never drift apart.
 */
@Composable
internal fun NutritionSummaryContent(
    caloriesKcal: Int,
    goalKcal: Int,
    protein: Pair<Double, Double>,
    fat: Pair<Double, Double>,
    carbs: Pair<Double, Double>,
    trailing: (@Composable () -> Unit)? = null,
) {
    val unit = LocalEnergyUnit.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        ValueOfGoal(
            value = LocaleFormat.integer(EnergyFormat.quantity(caloriesKcal, unit)),
            rest = "/ ${LocaleFormat.integer(EnergyFormat.quantity(goalKcal, unit))} ${energyUnitLabel()}",
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MacroColumn(HomeTopNutrient.PROTEIN, protein.first, protein.second, Modifier.weight(1f))
        MacroColumn(HomeTopNutrient.FAT, fat.first, fat.second, Modifier.weight(1f))
        MacroColumn(HomeTopNutrient.CARBS, carbs.first, carbs.second, Modifier.weight(1f))
    }
}

/** Calories vs goal, ⋮ menu, macro bars, "Meals ›" and "+". */
@Composable
internal fun NutritionCard(
    caloriesKcal: Int,
    goalKcal: Int,
    protein: Pair<Double, Double>,
    fat: Pair<Double, Double>,
    carbs: Pair<Double, Double>,
    onOpenMeals: () -> Unit,
    onAddFood: () -> Unit,
    onChangeCalorieGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        icon = Icons.Filled.Restaurant,
        title = stringResource(R.string.diary_card_nutrition),
        modifier = modifier,
        onClick = onOpenMeals,
        linkLabel = stringResource(R.string.diary_meals_link),
        onLink = onOpenMeals,
        onAdd = onAddFood,
    ) {
        NutritionSummaryContent(caloriesKcal, goalKcal, protein, fat, carbs) {
            CalorieGoalMenu(onChangeCalorieGoal)
        }
    }
}

/** ⋮ with "Change calorie goal" — on the Nutrition card and the Meals block summary. */
@Composable
internal fun CalorieGoalMenu(onChangeCalorieGoal: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.diary_more_options))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.diary_change_calorie_goal)) },
                onClick = {
                    menuOpen = false
                    onChangeCalorieGoal()
                },
            )
        }
    }
}

/**
 * Workouts: burned energy (manual + Health Connect) and workout minutes, in the
 * same number style as the Nutrition card's "0 / 1 985 kcal"; empty when neither
 * is known. Information only — the burn never changes the day's calorie goal.
 */
@Composable
internal fun ActivityCard(
    exerciseMinutes: Int?,
    activeKcal: Int,
    onOpenWorkouts: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        icon = Icons.Filled.LocalFireDepartment,
        title = stringResource(R.string.diary_card_activity),
        modifier = modifier,
        onClick = onOpenWorkouts,
        linkLabel = stringResource(R.string.diary_workouts_all),
        onLink = onOpenWorkouts,
        onAdd = onAdd,
    ) {
        ActivityValues(exerciseMinutes, activeKcal)
    }
}

/** Shared by the Workouts card and the Workouts block summary. */
@Composable
internal fun ActivityValues(exerciseMinutes: Int?, activeKcal: Int) {
    val unit = LocalEnergyUnit.current
    if ((exerciseMinutes ?: 0) <= 0 && activeKcal <= 0) {
        DashboardEmptyText()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val kcalText = LocaleFormat.integer(EnergyFormat.quantity(activeKcal.coerceAtLeast(0), unit))
        ValueOfGoal(value = kcalText, rest = energyUnitLabel())
        if ((exerciseMinutes ?: 0) > 0) {
            Text(
                stringResource(R.string.diary_exercise_minutes, exerciseMinutes ?: 0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shown on Steps/Sleep when Health Connect access is missing and nothing was entered by hand. */
@Composable
internal fun ConnectHealthRow(onConnect: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        DashboardEmptyText()
        TextButton(onClick = onConnect) { Text(stringResource(R.string.diary_connect_health)) }
    }
}

/**
 * "+" in a card header — the one add control of the Water, Weight, Steps and Sleep
 * cards. On Steps and Sleep it opens the hand entry for the day (add or change).
 */
@Composable
internal fun HeaderAddButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.diary_add), tint = MaterialTheme.colorScheme.primary)
    }
}

/** "Entered manually" note under a hand-typed value. */
@Composable
internal fun ManualEnteredNote() {
    Text(
        stringResource(R.string.diary_manual_entered),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Steps value with optional goal bar. A hand-typed value ([manual]) is shown even
 * without Health Connect access; "Connect" appears only when there is nothing to show.
 */
@Composable
internal fun StepsValues(steps: Long, goal: Int, granted: Boolean, manual: Boolean, onConnect: () -> Unit, barColor: Color? = null) {
    when {
        steps <= 0L && !granted && !manual -> ConnectHealthRow(onConnect)
        steps <= 0L -> DashboardEmptyText()
        else -> {
            ValueOfGoal(
                value = LocaleFormat.integer(steps),
                rest = if (goal > 0) "/ ${LocaleFormat.integer(goal)}" else null,
            )
            if (goal > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { DiaryDashboardMath.progressFraction(steps.toDouble(), goal.toDouble()) },
                    color = barColor ?: MaterialTheme.colorScheme.primary,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
            if (manual) {
                Spacer(Modifier.height(4.dp))
                ManualEnteredNote()
            }
        }
    }
}

/** Sleep as "7 h 30 min"; same manual / Connect rules as [StepsValues]. */
@Composable
internal fun SleepValues(sleepMinutes: Int?, granted: Boolean, manual: Boolean, onConnect: () -> Unit) {
    when {
        (sleepMinutes ?: 0) <= 0 && !granted && !manual -> ConnectHealthRow(onConnect)
        sleepMinutes == null || sleepMinutes <= 0 -> DashboardEmptyText()
        else -> {
            val (h, m) = DiaryDashboardMath.sleepHoursMinutes(sleepMinutes)
            Text(
                stringResource(R.string.diary_sleep_format, h, m),
                fontSize = ValueFontSize,
                fontWeight = FontWeight.Bold,
            )
            if (manual) {
                Spacer(Modifier.height(4.dp))
                ManualEnteredNote()
            }
        }
    }
}

@Composable
internal fun StepsDashboardCard(
    steps: Long,
    goal: Int,
    granted: Boolean,
    manual: Boolean,
    onClick: () -> Unit,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
        title = stringResource(R.string.diary_card_steps),
        modifier = modifier,
        onClick = onClick,
        headerTrailing = { HeaderAddButton(onEdit) },
    ) {
        StepsValues(steps, goal, granted, manual, onConnect)
    }
}

@Composable
internal fun WaterDashboardCard(
    currentMl: Int,
    goalMl: Int,
    useMetric: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun amount(ml: Int) = if (useMetric) ml else WaterAmountFormat.flOzFromMl(ml)
    val amountRes = if (useMetric) R.string.water_amount_ml else R.string.water_amount_fl_oz
    DashboardCard(
        icon = Icons.Filled.WaterDrop,
        title = stringResource(R.string.diary_card_water),
        modifier = modifier,
        onClick = onClick,
        headerTrailing = { HeaderAddButton(onAdd) },
    ) {
        if (currentMl <= 0) {
            DashboardEmptyText()
        } else {
            // "750 / 2000 ml": the goal carries the localized unit string.
            ValueOfGoal(
                value = LocaleFormat.integer(amount(currentMl)),
                rest = if (goalMl > 0) "/ ${stringResource(amountRes, amount(goalMl))}" else null,
            )
            if (goalMl > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { DiaryDashboardMath.progressFraction(currentMl.toDouble(), goalMl.toDouble()) },
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
        }
    }
}

@Composable
internal fun SleepDashboardCard(
    sleepMinutes: Int?,
    granted: Boolean,
    manual: Boolean,
    onClick: () -> Unit,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        icon = Icons.Filled.Bedtime,
        title = stringResource(R.string.diary_card_sleep),
        modifier = modifier,
        onClick = onClick,
        headerTrailing = { HeaderAddButton(onEdit) },
    ) {
        SleepValues(sleepMinutes, granted, manual, onConnect)
    }
}

@Composable
internal fun WeightDashboardCard(
    weightKg: Double?,
    measuredOn: LocalDate?,
    useMetric: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(
        icon = Icons.Filled.MonitorWeight,
        title = stringResource(R.string.diary_card_weight),
        modifier = modifier,
        onClick = onClick,
        headerTrailing = { HeaderAddButton(onAdd) },
    ) {
        if (weightKg == null) {
            DashboardEmptyText()
        } else {
            val text = UnitFormat.weight(weightKg, useMetric, LocaleFormat.displayLocale())
            val unitText = stringResource(if (useMetric) R.string.unit_kg else R.string.unit_lbs)
            ValueOfGoal(value = text.substringBeforeLast(' '), rest = unitText)
            if (measuredOn != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.diary_weight_measured, measuredOn.format(LocaleFormat.mediumDate())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Diary cards — light")
@Preview(showBackground = true, name = "Diary cards — dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiaryCardsPreview() {
    ShbdTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NutritionCard(
                    caloriesKcal = 0,
                    goalKcal = 1896,
                    protein = 0.0 to 95.0,
                    fat = 0.0 to 63.0,
                    carbs = 0.0 to 237.0,
                    onOpenMeals = {},
                    onAddFood = {},
                    onChangeCalorieGoal = {},
                )
                ActivityCard(exerciseMinutes = null, activeKcal = 0, onOpenWorkouts = {}, onAdd = {})
                SleepDashboardCard(sleepMinutes = 450, granted = true, manual = true, onClick = {}, onConnect = {}, onEdit = {})
            }
        }
    }
}
