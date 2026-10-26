package app.shbd.ui.diary.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.shbd.ui.diary.DashboardEmptyText
import app.shbd.ui.progress.TimeRange
import app.shbd.ui.progress.formatTick
import app.shbd.ui.progress.niceAxisTicks
import app.shbd.ui.progress.axisTickOffsets
import app.shbd.ui.progress.chartTickLabel
import app.shbd.ui.theme.AppTextOpacity
import app.shbd.ui.theme.ShbdTheme
import java.time.LocalDate

/**
 * Bar chart under the Diary block screens: one bar per bucket from
 * [bucketSeries], an optional dashed goal line, y ticks on the right and date
 * labels below ([axisTickOffsets]: every day of a week; 1, 7, 14, 21 and the
 * last day of a month; five for longer ranges). Tapping a bar shows its date and value above the chart.
 * With no value in the whole range it shows "No data added" instead.
 * Negative values (calorie surplus on the deficit chart) grow down from a zero
 * line in [negativeColor].
 */
@Composable
internal fun DiaryBarChart(
    series: List<DayValue>,
    range: TimeRange,
    color: Color,
    title: String,
    formatValue: (Double) -> String,
    goal: Double? = null,
    negativeColor: Color = color,
) {
    if (!hasAnyValue(series)) {
        DashboardEmptyText()
        return
    }
    val maxData = series.mapNotNull { it.value }.maxOrNull() ?: 0.0
    val minData = (series.mapNotNull { it.value }.minOrNull() ?: 0.0).coerceAtMost(0.0)
    val maxValue = maxOf(maxData, goal ?: 0.0).coerceAtLeast(if (minData < 0) 0.0 else 1.0)
    val ticks = niceAxisTicks(minData, maxValue, count = 4)
    val yTop = ticks.last().coerceAtLeast(maxValue)
    val yBottom = ticks.first().coerceAtMost(minData)
    val ySpan = (yTop - yBottom).coerceAtLeast(1.0)
    val zeroLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = AppTextOpacity.Muted)
    val emptyBar = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val dateLabel = remember(range) { chartTickLabel(range) }
    var inspected by remember(series) { mutableStateOf<Int?>(null) }
    val inspectedPoint = inspected?.let { series.getOrNull(it) }?.takeIf { it.value != null }
    val headline = inspectedPoint?.let { "${dateLabel(it.date)} · ${formatValue(it.value!!)}" } ?: ""

    Column(Modifier.fillMaxWidth()) {
        Text(
            headline,
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            modifier = Modifier.height(20.dp),
        )
        Row(Modifier.fillMaxWidth().height(160.dp)) {
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = if (headline.isNotEmpty()) "$title, $headline" else title }
                    .pointerInput(series) {
                        detectTapGestures { tap ->
                            if (series.isEmpty()) return@detectTapGestures
                            val slot = size.width / series.size.toFloat()
                            val index = (tap.x / slot).toInt().coerceIn(0, series.lastIndex)
                            inspected = if (inspected == index) null else index
                        }
                    },
            ) {
                val h = size.height
                val slot = size.width / series.size.toFloat()
                val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                fun yOf(v: Double): Float = h - ((v - yBottom) / ySpan * h).toFloat()
                val y0 = yOf(0.0)
                ticks.forEach { tick ->
                    val y = yOf(tick)
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                if (yBottom < 0) {
                    drawLine(zeroLine, Offset(0f, y0), Offset(size.width, y0), strokeWidth = 1.dp.toPx())
                }
                series.forEachIndexed { i, point ->
                    val x = i * slot + (slot - barWidth) / 2f
                    val value = point.value
                    if (value == null) {
                        drawRoundRect(
                            emptyBar,
                            topLeft = Offset(x, y0 - 3.dp.toPx()),
                            size = Size(barWidth, 3.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    } else {
                        val barH = kotlin.math.abs(yOf(value) - y0).coerceAtLeast(2.dp.toPx())
                        val alpha = if (inspected == null || inspected == i) 1f else 0.45f
                        val barColor = if (value < 0) negativeColor else color
                        drawRoundRect(
                            barColor.copy(alpha = alpha),
                            topLeft = Offset(x, if (value < 0) y0 else y0 - barH),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(minOf(6.dp.toPx(), barWidth / 2f)),
                        )
                    }
                }
                goal?.takeIf { it > 0 }?.let { g ->
                    val y = yOf(g)
                    drawLine(
                        goalColor,
                        Offset(0f, y),
                        Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                    )
                }
            }
            Box(Modifier.width(44.dp).fillMaxHeight()) {
                ticks.forEach { tick ->
                    val fraction = ((tick - yBottom) / ySpan).toFloat()
                    Text(
                        formatTick(tick),
                        fontSize = 10.sp,
                        color = muted,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomEnd)
                            .padding(bottom = (160 * fraction).dp.coerceAtMost(146.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(end = 44.dp)) {
            val labelIndices = axisTickOffsets(range, series.size).toSet()
            series.forEachIndexed { i, point ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    if (i in labelIndices) {
                        Text(
                            dateLabel(point.date),
                            fontSize = 10.sp,
                            color = muted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Diary bar chart", widthDp = 380)
@Preview(name = "Diary bar chart dark", widthDp = 380, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiaryBarChartPreview() {
    val end = LocalDate.of(2026, 10, 25)
    val values = (0..6).associate { end.minusDays(it.toLong()) to (4000.0 + it * 1100) }.minus(end.minusDays(2))
    ShbdTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DiaryBarChart(
                    series = daySeries(TimeRange.WEEK, end, values),
                    range = TimeRange.WEEK,
                    color = MaterialTheme.colorScheme.primary,
                    title = "Steps",
                    formatValue = { formatTick(it) },
                    goal = 10_000.0,
                )
                DiaryBarChart(
                    series = daySeries(TimeRange.WEEK, end, emptyMap()),
                    range = TimeRange.WEEK,
                    color = MaterialTheme.colorScheme.primary,
                    title = "Sleep",
                    formatValue = { formatTick(it) },
                )
            }
        }
    }
}
