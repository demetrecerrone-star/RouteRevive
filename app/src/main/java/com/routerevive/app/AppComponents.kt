package com.routerevive.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RouteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content)
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun FeatureIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = RoundedCornerShape(13.dp), color = tint.copy(alpha = 0.10f)) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun RouteEmptyState(
    icon: ImageVector, title: String, message: String,
    modifier: Modifier = Modifier,
    action: String? = null, onAction: () -> Unit = {}
) {
    Column(modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FeatureIcon(icon)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

/** Stack at narrow widths or large system font sizes instead of squeezing text. */
@Composable
fun AdaptivePair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        if (maxWidth < 300.dp || fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
fun StatusPill(label: String, attention: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(8.dp),
        color = if (attention) colors.tertiaryContainer else colors.primaryContainer) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (attention) colors.onTertiaryContainer else colors.onPrimaryContainer)
    }
}

@Composable
fun DateInput(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minDate: java.time.LocalDate? = null, maxDate: java.time.LocalDate? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var dialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<android.app.DatePickerDialog?>(null)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { dialog?.dismiss() } }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth(),
        supportingText = { Text("YYYY-MM-DD") },
        trailingIcon = {
            IconButton(onClick = {
                focus.clearFocus()
                val initial = runCatching { java.time.LocalDate.parse(value) }.getOrDefault(java.time.LocalDate.now())
                dialog = android.app.DatePickerDialog(context, { _, year, month, day ->
                    onChange(java.time.LocalDate.of(year, month + 1, day).toString())
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                    val zone = java.time.ZoneId.systemDefault()
                    minDate?.let { datePicker.minDate = it.atStartOfDay(zone).toInstant().toEpochMilli() }
                    maxDate?.let { datePicker.maxDate = it.atStartOfDay(zone).toInstant().toEpochMilli() }
                    show()
                }
            }) { Icon(androidx.compose.material.icons.Icons.Default.CalendarMonth, "Choose $label") }
        })
}

@Composable
fun TimeInput(label: String, value: String, onChange: (String) -> Unit,
              modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var dialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<android.app.TimePickerDialog?>(null)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { dialog?.dismiss() } }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth(), supportingText = { Text("24-hour time · HH:mm") },
        trailingIcon = {
            IconButton(onClick = {
                focus.clearFocus()
                val initial = runCatching { java.time.LocalTime.parse(value) }.getOrDefault(java.time.LocalTime.of(9, 0))
                dialog = android.app.TimePickerDialog(context, { _, hour, minute ->
                    onChange(java.time.LocalTime.of(hour, minute).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                }, initial.hour, initial.minute, true).apply { show() }
            }) { Icon(androidx.compose.material.icons.Icons.Default.Schedule, "Choose $label") }
        })
}
