package com.jetbrains.kmpapp.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.AttendanceStatus
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.DefaultSubjectColors
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.lessonVisitKey
import com.jetbrains.kmpapp.data.model.statusFor
import com.jetbrains.kmpapp.data.model.effectiveStatusFor
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import com.jetbrains.kmpapp.screens.components.swipeToDismissBack
import com.jetbrains.kmpapp.screens.schedule.abbreviateSubjectName

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    modifier: Modifier = Modifier
) {
    val selectedKey by viewModel.selectedSubjectKey.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val subjectColorOverrides by viewModel.subjectColorOverrides.collectAsState()

    val selected = subjects.firstOrNull { it.key == selectedKey }

    if (selected != null) {
        SubjectProgressDetailScreen(
            subject = selected,
            viewModel = viewModel,
            subjectColorOverrides = subjectColorOverrides,
            onBack = { viewModel.selectSubject(null) }
        )
    } else {
        ProgressMainContent(
            subjects = subjects,
            viewModel = viewModel,
            subjectColorOverrides = subjectColorOverrides,
            modifier = modifier
        )
    }
}

@Composable
private fun ProgressMainContent(
    subjects: List<SubjectProgress>,
    viewModel: ProgressViewModel,
    subjectColorOverrides: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val target by viewModel.selectedTarget.collectAsState()
    val showAbbreviated by viewModel.showAbbreviatedNames.collectAsState()
    var showExportDialog by remember { mutableStateOf(false) }
    val shareAttendanceEnabled by viewModel.shareAttendanceEnabled.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Прогресс посещений",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (target != null) {
                    IconButton(onClick = { viewModel.importScheduleFromFile() }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Импорт расписания из файла"
                        )
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Экспорт/поделиться расписанием"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (target != null) target!!.targetTitle else "Выберите расписание",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (subjects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Нет данных для прогресса",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp
                    )
                ) {
                    items(subjects, key = { it.key }) { subject ->
                        val color = subjectColor(subject.displayName, subjectColorOverrides)
                        val progress = if (subject.totalCount > 0) {
                            subject.visitedCount.toFloat() / subject.totalCount.toFloat()
                        } else 0f
                        val name = if (showAbbreviated) abbreviateSubjectName(subject.displayName) else subject.displayName

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectSubject(subject.key) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${subject.visitedCount}/${subject.totalCount}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = color,
                                    trackColor = color.copy(alpha = 0.18f),
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        val selection = remember(showExportDialog, subjects) {
            mutableStateMapOf<String, Boolean>().apply {
                subjects.forEach { put(it.key, true) }
            }
        }
        val selectedKeys = selection.filterValues { it }.keys

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Экспорт / поделиться") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Выберите предметы:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { subjects.forEach { selection[it.key] = true } }) { Text("Все") }
                        TextButton(onClick = { subjects.forEach { selection[it.key] = false } }) { Text("Ничего") }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        subjects.forEach { subject ->
                            val checked = selection[subject.key] == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selection[subject.key] = !checked }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { selection[subject.key] = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = subject.displayName,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Экспорт расписания в .txt файл (для обмена и импорта).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Планы посещений",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Включите, чтобы можно было поделиться отметками «пойду / пропущу».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Делиться планами", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = shareAttendanceEnabled,
                            onCheckedChange = { viewModel.setShareAttendanceEnabled(it) }
                        )
                    }
                    TextButton(
                        onClick = { viewModel.shareAttendancePlans() },
                        enabled = shareAttendanceEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Поделиться планами посещений") }
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.exportSelectedSubjects(selectedKeys, share = false)
                                showExportDialog = false
                            },
                            enabled = selectedKeys.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Сохранить", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                        TextButton(
                            onClick = {
                                viewModel.exportSelectedSubjects(selectedKeys, share = true)
                                showExportDialog = false
                            },
                            enabled = selectedKeys.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Поделиться", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                    }
                    TextButton(
                        onClick = { showExportDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Отмена") }
                }
            },
            dismissButton = {}
        )
    }
}

private enum class ProgressMarkMode(val title: String) {
    VIEW("Просмотр"),
    WAS("Был(а)"),
    WAS_NOT("Пропустил(а)"),
    WILL_GO("Пойду"),
    WILL_NOT_GO("Пропущу")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectProgressDetailScreen(
    subject: SubjectProgress,
    viewModel: ProgressViewModel,
    subjectColorOverrides: Map<String, String>,
    onBack: () -> Unit
) {
    PlatformBackHandler(onBack = onBack)
    val target by viewModel.selectedTarget.collectAsState()
    val visits by viewModel.lessonVisits.collectAsState()
    val rules by viewModel.attendanceRules.collectAsState()
    val targetId = target?.id ?: 0
    val color = subjectColor(subject.displayName, subjectColorOverrides)
    val today = DateUtils.today()
    var mode by remember { mutableStateOf(ProgressMarkMode.VIEW) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .swipeToDismissBack(requireEdge = true, onBack = onBack)
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Посещено ${subject.visitedCount} из ${subject.totalCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val progress = if (subject.totalCount > 0) subject.visitedCount.toFloat() / subject.totalCount.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Режим отметки",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text("Факт:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mode == ProgressMarkMode.WAS,
                    onClick = { mode = if (mode == ProgressMarkMode.WAS) ProgressMarkMode.VIEW else ProgressMarkMode.WAS },
                    label = { Text("Был(а)", maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = mode == ProgressMarkMode.WAS_NOT,
                    onClick = { mode = if (mode == ProgressMarkMode.WAS_NOT) ProgressMarkMode.VIEW else ProgressMarkMode.WAS_NOT },
                    label = { Text("Пропустил(а)", maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("План:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mode == ProgressMarkMode.WILL_GO,
                    onClick = { mode = if (mode == ProgressMarkMode.WILL_GO) ProgressMarkMode.VIEW else ProgressMarkMode.WILL_GO },
                    label = { Text("Пойду", maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = mode == ProgressMarkMode.WILL_NOT_GO,
                    onClick = { mode = if (mode == ProgressMarkMode.WILL_NOT_GO) ProgressMarkMode.VIEW else ProgressMarkMode.WILL_NOT_GO },
                    label = { Text("Пропущу", maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (mode != ProgressMarkMode.VIEW) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Выбрано: ${mode.title} — нажмите на дату.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Даты пар (дд.мм). По умолчанию — «пойду».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subject.lessons.forEach { lesson ->
                            val vKey = lessonVisitKey(targetId, lesson.date, lesson.bellNumber)
                            val visit = visits[vKey]
                            val factAttended = visit?.attended == true
                            val factMissed = visit?.attended == false
                            val intention = rules.effectiveStatusFor(lesson)
                            val intentAttend = intention == AttendanceStatus.ATTENDING
                            val intentSkip = intention == AttendanceStatus.SKIPPED

                            val bg = when {
                                factAttended -> color
                                factMissed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                intentSkip -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                intentAttend -> color.copy(alpha = 0.22f)
                                else -> Color.Transparent
                            }
                            val borderColor = when {
                                factAttended -> color
                                factMissed -> MaterialTheme.colorScheme.error
                                intentSkip -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                intentAttend -> color.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            val textColor = when {
                                factAttended -> Color.White
                                factMissed -> MaterialTheme.colorScheme.error
                                intentSkip -> MaterialTheme.colorScheme.error
                                intentAttend -> color
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            val day = lesson.date.day.toString().padStart(2, '0')
                            val mon = (lesson.date.month.ordinal + 1).toString().padStart(2, '0')
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                    .clickable(enabled = targetId != 0 && mode != ProgressMarkMode.VIEW) {
                                        when (mode) {
                                            ProgressMarkMode.VIEW -> Unit
                                            ProgressMarkMode.WAS -> viewModel.setLessonVisited(targetId, lesson, true)
                                            ProgressMarkMode.WAS_NOT -> viewModel.setLessonVisited(targetId, lesson, false)
                                            ProgressMarkMode.WILL_GO -> viewModel.markLessonAttendanceIntent(targetId, lesson, true)
                                            ProgressMarkMode.WILL_NOT_GO -> viewModel.markLessonAttendanceIntent(targetId, lesson, false)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$day.$mon",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    LegendRow(color = color)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendItem(color = color, label = "Был(а) — факт")
        LegendItem(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f), label = "Пропустил(а) — факт", border = MaterialTheme.colorScheme.error)
        LegendItem(color = color.copy(alpha = 0.22f), label = "Пойду — план (по умолчанию)", border = color.copy(alpha = 0.45f))
        LegendItem(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f), label = "Пропущу — план", border = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
    }
}

@Composable
private fun LegendItem(color: Color, label: String, border: Color = color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
                .then(if (border != color || color == Color.Transparent) Modifier.border(1.dp, border, RoundedCornerShape(5.dp)) else Modifier)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun parseHexColor(hexRaw: String, fallback: Long = 0xFF6750A4): Color {
    val hex = hexRaw.trim().removePrefix("#")
    return try {
        when (hex.length) {
            6 -> Color(("FF$hex").toLong(16))
            8 -> Color(hex.toLong(16))
            else -> Color(fallback)
        }
    } catch (_: Exception) {
        Color(fallback)
    }
}

private fun subjectColor(subject: String, overrides: Map<String, String> = emptyMap()): Color {
    val key = subject.trim().lowercase()
    val override = overrides[key] ?: overrides.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    if (!override.isNullOrBlank()) return parseHexColor(override)
    val idx = (subject.hashCode().and(0x7fffffff)) % DefaultSubjectColors.size
    return parseHexColor(DefaultSubjectColors[idx])
}
