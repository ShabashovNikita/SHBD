package app.shbd.ui.diary.blocks

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shbd.AppContainer
import app.shbd.R
import app.shbd.models.WeekStartDay
import app.shbd.ui.diary.SummaryCard
import app.shbd.ui.diary.WeekCard
import app.shbd.ui.home.HomeViewModel
import app.shbd.ui.home.dayTypeMarkerMaps
import app.shbd.ui.home.isoToLocalDates
import app.shbd.ui.home.weekStripDates
import app.shbd.ui.navigation.bottomNavContentPadding
import app.shbd.ui.navigation.BottomOverlayPadding
import app.shbd.ui.progress.ProgressViewModel
import app.shbd.ui.progress.TimeRange
import app.shbd.ui.progress.TimeRangePicker

/** Block title (block-screen mockup: "Meals") and the muted card name under it ("Nutrition"). */
@get:StringRes
internal val DiaryBlock.titleRes: Int
    get() = when (this) {
        DiaryBlock.NUTRITION -> R.string.diary_meals_link
        DiaryBlock.ACTIVITY -> R.string.diary_workouts_link
        DiaryBlock.STEPS -> R.string.diary_card_steps
        DiaryBlock.WATER -> R.string.diary_card_water
        DiaryBlock.SLEEP -> R.string.diary_card_sleep
        DiaryBlock.WEIGHT -> R.string.diary_card_weight
    }

@get:StringRes
internal val DiaryBlock.subtitleRes: Int
    get() = when (this) {
        DiaryBlock.NUTRITION, DiaryBlock.WATER -> R.string.diary_card_nutrition
        DiaryBlock.ACTIVITY, DiaryBlock.STEPS -> R.string.diary_block_subtitle_activity
        DiaryBlock.SLEEP -> R.string.diary_block_subtitle_health
        DiaryBlock.WEIGHT -> R.string.diary_block_subtitle_body
    }

/** Back arrow on the left, centered title + muted subtitle. */
@Composable
internal fun DiaryBlockHeader(block: DiaryBlock, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.diary_back),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(block.titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(block.subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Dashboard week card bound to the shared [HomeViewModel] selected day. */
@Composable
internal fun SelectedDayWeekCard(container: AppContainer, vm: HomeViewModel) {
    val ui by vm.ui.collectAsState()
    val weekStartDay by container.prefs.weekStartDay.collectAsState(initial = WeekStartDay.MONDAY)
    val stripDates = remember(weekStartDay) { weekStripDates(weekStartDay) }
    val stripMarkers = remember(ui.profile, stripDates) { dayTypeMarkerMaps(ui.profile, vm.weekDayTypes(stripDates)) }
    val stripUntracked = remember(ui.untrackedDays) { isoToLocalDates(ui.untrackedDays) }
    WeekCard(
        selectedDate = ui.date,
        weekStartDay = weekStartDay,
        onSelect = { vm.setSelectedDate(it) },
        typeColorsByDate = stripMarkers.first,
        typeNamesByDate = stripMarkers.second,
        untrackedDates = stripUntracked,
    )
}

/**
 * Chart card of a block: [title], range chips (Week … Year) and the chart. The
 * range lives in the shared [ProgressViewModel] and ends on the selected day.
 */
@Composable
internal fun BlockChartCard(
    progressVm: ProgressViewModel,
    title: String = stringResource(R.string.diary_block_progress),
    content: @Composable (TimeRange) -> Unit,
) {
    val progress by progressVm.ui.collectAsState()
    SummaryCard {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        DiaryRangePicker(progressVm)
        Spacer(Modifier.height(12.dp))
        content(progress.timeRange)
    }
}

/**
 * Range chips (Week … Year) bound to the shared [ProgressViewModel]; every chart
 * of the block screens shows them. A stored "All time" from the old Progress
 * screen falls back to Week.
 */
@Composable
internal fun DiaryRangePicker(progressVm: ProgressViewModel) {
    val progress by progressVm.ui.collectAsState()
    val range = progress.timeRange
    LaunchedEffect(range) {
        if (range !in DiaryChartRanges) progressVm.setTimeRange(TimeRange.WEEK)
    }
    TimeRangePicker(selected = range, onSelect = progressVm::setTimeRange, options = DiaryChartRanges)
}

/** Keeps the shared Progress range ending on the selected day. */
@Composable
internal fun AnchorProgressToSelectedDay(vm: HomeViewModel, progressVm: ProgressViewModel) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(ui.date) { progressVm.setRangeEnd(ui.date) }
}

/** Round "+" above the bottom navigation bar (block-screen mockup). */
@Composable
internal fun DiaryBlockFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 24.dp, bottom = BottomOverlayPadding),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.diary_add))
    }
}

/**
 * Frame of the Steps, Water, Sleep, Activity and Weight blocks: header, week
 * card, then [content] (summary, entries, chart) in one scrolling column, and
 * an optional "+" FAB. Meals uses [app.shbd.ui.home.HomeScreen] in block mode instead.
 */
@Composable
internal fun DiaryBlockScaffold(
    block: DiaryBlock,
    container: AppContainer,
    vm: HomeViewModel,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = bottomNavContentPadding(extra = if (onAdd != null) 88.dp else 16.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DiaryBlockHeader(block, onBack) }
            item { SelectedDayWeekCard(container, vm) }
            content()
        }
        if (onAdd != null) {
            DiaryBlockFab(onAdd, Modifier.align(Alignment.BottomEnd))
        }
    }
}
