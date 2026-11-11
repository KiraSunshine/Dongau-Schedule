package com.jetbrains.kmpapp.screens.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.AttendanceStatus
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.intervalWeeksLabel

/**
 * Нижняя панель отметки посещаемости занятия.
 *
 * Пользователь выбирает статус («Буду ходить» / «Не буду») и периодичность
 * повторения отметки: только этот день, каждую неделю, каждые 2 недели и т. д.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceBottomSheet(
    lesson: Lesson,
    currentStatus: AttendanceStatus?,
    currentIntervalWeeks: Int,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onApply: (AttendanceStatus, Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var status by remember {
        mutableStateOf(currentStatus ?: AttendanceStatus.SKIPPED)
    }
    var intervalWeeks by remember {
        mutableIntStateOf(if (currentStatus == null) 0 else currentIntervalWeeks)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Отметка посещаемости",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${lesson.bellNumber} пара • ${lesson.startTime} — ${lesson.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = lesson.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (lesson.teachers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lesson.teachers.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${lesson.date.day} ${DateUtils.formatMonthRu(lesson.date.month)} • ${DateUtils.formatDayOfWeekShort(lesson.date.dayOfWeek)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Статус",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttendanceStatus.entries.forEach { option ->
                    FilterChip(
                        selected = status == option,
                        onClick = { status = option },
                        label = {
                            Text(
                                text = option.displayName,
                                fontWeight = if (status == option) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (option == AttendanceStatus.SKIPPED) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = if (option == AttendanceStatus.SKIPPED) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Повторение",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                (0..4).forEach { weeks ->
                    FilterChip(
                        selected = intervalWeeks == weeks,
                        onClick = { intervalWeeks = weeks },
                        label = {
                            Text(
                                text = intervalWeeksLabel(weeks),
                                fontSize = 12.sp,
                                fontWeight = if (intervalWeeks == weeks) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = { onApply(status, intervalWeeks) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Сохранить отметку", fontWeight = FontWeight.Bold)
            }

            if (currentStatus != null) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Сбросить отметку",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
