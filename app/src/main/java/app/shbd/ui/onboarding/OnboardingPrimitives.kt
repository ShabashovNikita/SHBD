package app.shbd.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.shbd.R
import app.shbd.ui.components.ShbdSurface
import app.shbd.ui.components.ShbdIconBubble
import app.shbd.ui.components.NumericWheelPicker
import app.shbd.ui.components.SplitDecimalWheelPicker
import app.shbd.ui.theme.AppColors
import app.shbd.ui.theme.AppTextOpacity
import androidx.compose.material3.Icon
import app.shbd.ui.theme.AppRadii

@Composable
internal fun StepHeader(title: String, subtitle: String? = null) {
    Column {
        Text(
            title,
            // headlineMedium (not displaySmall): long Russian titles fit in two lines
            // and leave room for the footer button on small screens.
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        subtitle?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * iOS selectionCard parity — rounded card with leading icon, title, optional
 * subtitle, and a trailing checkmark.circle.fill / circle. Selected state adds
 * a 2pt onBackground stroke; matches AppColors.appCard background.
 */
@Composable
internal fun SelectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = if (selected) AppColors.Calorie else MaterialTheme.colorScheme.onBackground
    val selectedBorder = if (selected) {
        Modifier.border(BorderStroke(1.4.dp, AppColors.Calorie.copy(alpha = 0.55f)), RoundedCornerShape(AppRadii.Pill))
    } else {
        Modifier
    }
    ShbdSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(selectedBorder)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        cornerRadius = 20.dp,
        padding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShbdIconBubble(icon = icon, size = 40.dp, iconSize = 21.dp, tint = accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                badge?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Calorie
                    )
                }
                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
                    )
                }
            }
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) AppColors.Calorie else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun ChoiceRow(label: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        AppColors.Calorie.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.Card))
            .background(bg)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) AppColors.Calorie else Color.Transparent)
                    .padding(3.dp)
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.95f))
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToggleCard(label: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ShbdSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) },
        cornerRadius = 20.dp,
        padding = 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted)
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
internal fun WheeledColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Onboarding weight wheel: whole kg | tenths, always in kg (units live in Profile). */
@Composable
internal fun OnboardingWeightWheel(kg: Double, onWeightChange: (Double) -> Unit) {
    SplitDecimalWheelPicker(
        value = kg.coerceIn(30.0, 250.0),
        onValueChange = onWeightChange,
        min = 30,
        max = 250,
        unit = stringResource(R.string.unit_kg)
    )
}

/** Height wheel for its own step: one cm column. */
@Composable
internal fun OnboardingHeightWheels(
    cm: Int,
    onHeightChange: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        WheeledColumn(label = stringResource(R.string.onboarding_height), modifier = Modifier.fillMaxWidth(0.6f)) {
            NumericWheelPicker(value = cm, onValueChange = onHeightChange, min = 100, max = 250, unit = stringResource(R.string.unit_cm), showEdgePlaceholders = true)
        }
    }
}
