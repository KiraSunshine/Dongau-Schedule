package com.jetbrains.kmpapp.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.AttendanceRule
import com.jetbrains.kmpapp.data.model.AttendanceStatus
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.LessonVisit
import com.jetbrains.kmpapp.data.model.lessonVisitKey
import com.jetbrains.kmpapp.data.model.ScheduleSlot
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import com.jetbrains.kmpapp.data.model.statusFor
import com.jetbrains.kmpapp.data.model.effectiveStatusFor

@Composable
fun ScheduleSlotCard(
    slot: ScheduleSlot,
    onLessonClick: (Lesson) -> Unit,
    isToday: Boolean = false,
    currentMinutes: Int = com.jetbrains.kmpapp.data.model.DateUtils.currentTimeMinutes(),
    showLessonProgress: Boolean = true,
    showAbbreviatedNames: Boolean = false,
    scheduleTargetType: ScheduleTargetType = ScheduleTargetType.GROUP,
    attendanceRules: List<AttendanceRule> = emptyList(),
    lessonVisits: Map<String, LessonVisit> = emptyMap(),
    targetId: Int = 0,
    onAttendanceClick: (Lesson) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (slot) {
        is ScheduleSlot.Active -> {
            if (slot.lessons.size == 1) {
                val lesson = slot.lessons.first()
                LessonCard(
                    lesson = lesson,
                    onClick = { onLessonClick(lesson) },
                    isToday = isToday,
                    currentMinutes = currentMinutes,
                    showLessonProgress = showLessonProgress,
                    showAbbreviatedNames = showAbbreviatedNames,
                    scheduleTargetType = scheduleTargetType,
                    attendanceStatus = attendanceRules.effectiveStatusFor(lesson),
                    visit = if (targetId != 0) lessonVisits[com.jetbrains.kmpapp.data.model.lessonVisitKey(targetId, lesson.date, lesson.bellNumber)] else null,
                    onAttendanceClick = { onAttendanceClick(lesson) },
                    modifier = modifier
                )
            } else {
                MultiLessonCard(
                    bellNumber = slot.bellNumber,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    lessons = slot.lessons,
                    onLessonClick = onLessonClick,
                    isToday = isToday,
                    currentMinutes = currentMinutes,
                    showLessonProgress = showLessonProgress,
                    showAbbreviatedNames = showAbbreviatedNames,
                    scheduleTargetType = scheduleTargetType,
                    attendanceRules = attendanceRules,
                    lessonVisits = lessonVisits,
                    targetId = targetId,
                    onAttendanceClick = onAttendanceClick,
                    modifier = modifier
                )
            }
        }
        is ScheduleSlot.Empty -> {
            EmptyLessonCard(
                bellNumber = slot.bellNumber,
                startTime = slot.startTime,
                endTime = slot.endTime,
                modifier = modifier
            )
        }
    }
}

@Composable
fun LessonCard(
    lesson: Lesson,
    onClick: () -> Unit,
    isToday: Boolean = false,
    currentMinutes: Int = com.jetbrains.kmpapp.data.model.DateUtils.currentTimeMinutes(),
    showLessonProgress: Boolean = true,
    showAbbreviatedNames: Boolean = false,
    scheduleTargetType: ScheduleTargetType = ScheduleTargetType.GROUP,
    attendanceStatus: AttendanceStatus? = null,
    visit: LessonVisit? = null,
    onAttendanceClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    pageIndicator: Pair<Int, Int>? = null,
    horizontalMargin: androidx.compose.ui.unit.Dp = 16.dp
) {
    val (typeBg, _) = getTypeBadgeColors(lesson.lessonType)
    val cardOnBg = Color.White
    val cardOnBgDim = Color.White.copy(alpha = 0.7f)
    val cardOnBgSubtle = Color.White.copy(alpha = 0.45f)

    val effectiveIntent = attendanceStatus ?: AttendanceStatus.ATTENDING
    val isSkipped = effectiveIntent == AttendanceStatus.SKIPPED
    val factAttended = visit?.attended == true
    val factMissed = visit?.attended == false
    val cardAlpha = if (isSkipped && !factAttended && !factMissed) 0.45f else 1f

    val progress = if (isToday && showLessonProgress) {
        com.jetbrains.kmpapp.data.model.DateUtils.getLessonProgress(lesson.startTime, lesson.endTime, currentMinutes)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .padding(horizontal = horizontalMargin, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = typeBg, contentColor = cardOnBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header: Pair number, time, type badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${lesson.bellNumber} пара",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardOnBg
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${lesson.startTime} — ${lesson.endTime}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = cardOnBgDim
                        )
                    }

                                        Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = lesson.lessonType.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardOnBg
                            )
                        }

                        val markIcon = when {
                            factAttended -> Icons.Default.Check
                            factMissed -> Icons.Default.Close
                            isSkipped -> Icons.Default.EventBusy
                            else -> Icons.Default.EventAvailable
                        }
                        val markBg = when {
                            factAttended -> Color(0xFF2E7D32).copy(alpha = 0.85f)
                            factMissed -> Color(0xFFC62828).copy(alpha = 0.85f)
                            else -> Color.White.copy(alpha = 0.2f)
                        }
                        val markDesc = when {
                            factAttended -> "Был(а)"
                            factMissed -> "Не был(а)"
                            isSkipped -> "Не пойду"
                            else -> "Пойду"
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(markBg)
                                .then(if (onAttendanceClick != null) Modifier.clickable(onClick = onAttendanceClick) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = markIcon,
                                contentDescription = markDesc,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Subject name
                Text(
                    text = if (showAbbreviatedNames) abbreviateSubjectName(lesson.subject) else lesson.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cardOnBg
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Details: show the useful entity for the selected schedule target.
                val groupsText = lesson.groups.joinToString(", ")
                val showGroupsAsTeacherReplacement = scheduleTargetType == ScheduleTargetType.TEACHER && lesson.groups.isNotEmpty()

                if (showGroupsAsTeacherReplacement) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Группы",
                            tint = cardOnBgDim,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = groupsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cardOnBgDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (lesson.teachers.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Преподаватель",
                            tint = cardOnBgDim,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = lesson.teachers.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cardOnBgDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (lesson.classrooms.isNotEmpty() || lesson.groups.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (scheduleTargetType == ScheduleTargetType.AUDITORIUM && lesson.groups.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Группы",
                                tint = cardOnBgDim,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = groupsText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cardOnBgDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else if (lesson.classrooms.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Аудитория",
                                tint = cardOnBg,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = lesson.classrooms.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cardOnBg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        if (scheduleTargetType == ScheduleTargetType.GROUP && lesson.groups.size > 1) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Группы",
                                tint = cardOnBgSubtle,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = groupsText,
                                style = MaterialTheme.typography.bodySmall,
                                color = cardOnBgSubtle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                pageIndicator?.let { (total, current) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(
                            text = "$total ${if (total in 2..4) "пары" else "пар"} в то же время",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = cardOnBgDim
                        )
                        repeat(total) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == current) 6.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == current) cardOnBg
                                        else cardOnBgSubtle
                                    )
                            )
                        }
                    }
                }
            }

            if (progress != null) {
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = progress,
                    animationSpec = androidx.compose.animation.core.tween(500)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = if (animatedProgress >= 0.98f) 20.dp else 0.dp))
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
fun MultiLessonCard(
    bellNumber: Int,
    startTime: String,
    endTime: String,
    lessons: List<Lesson>,
    onLessonClick: (Lesson) -> Unit,
    isToday: Boolean = false,
    currentMinutes: Int = com.jetbrains.kmpapp.data.model.DateUtils.currentTimeMinutes(),
    showLessonProgress: Boolean = true,
    showAbbreviatedNames: Boolean = false,
    scheduleTargetType: ScheduleTargetType = ScheduleTargetType.GROUP,
    attendanceRules: List<AttendanceRule> = emptyList(),
    lessonVisits: Map<String, LessonVisit> = emptyMap(),
    targetId: Int = 0,
    onAttendanceClick: (Lesson) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { lessons.size })

    // Gallery of full lesson blocks: each pair is its own card, the next peeks from the right
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 4.dp,
        beyondViewportPageCount = 1
    ) { page ->
        LessonCard(
            lesson = lessons[page],
            onClick = { onLessonClick(lessons[page]) },
            isToday = isToday,
            currentMinutes = currentMinutes,
            showLessonProgress = showLessonProgress,
            showAbbreviatedNames = showAbbreviatedNames,
            scheduleTargetType = scheduleTargetType,
            attendanceStatus = attendanceRules.effectiveStatusFor(lessons[page]),
            visit = if (targetId != 0) lessonVisits[com.jetbrains.kmpapp.data.model.lessonVisitKey(targetId, lessons[page].date, lessons[page].bellNumber)] else null,
            onAttendanceClick = { onAttendanceClick(lessons[page]) },
            pageIndicator = lessons.size to pagerState.currentPage,
            horizontalMargin = 0.dp
        )
    }
}

@Composable
fun EmptyLessonCard(
    bellNumber: Int,
    startTime: String,
    endTime: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$bellNumber пара • $startTime — $endTime",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
            Text(
                text = "Нет пары",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun LessonBreakIndicator(
    breakMinutes: Int,
    modifier: Modifier = Modifier
) {
    if (breakMinutes <= 0) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "••• перемена $breakMinutes мин •••",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 0.2.sp
        )
    }
}


@Composable
internal fun getTypeBadgeColors(lessonType: LessonType): Pair<Color, Color> {
    return when (lessonType) {
        LessonType.LECTURE -> Color(0xFF4CAF50) to Color.White
        LessonType.PRACTICE -> Color(0xFFFF9800) to Color.White
        LessonType.LAB -> Color(0xFF9C27B0) to Color.White
        LessonType.EXAM -> Color(0xFFE53935) to Color.White
        LessonType.CREDIT -> Color(0xFF1E88E5) to Color.White
        LessonType.DIFF_CREDIT -> Color(0xFF00897B) to Color.White
        LessonType.CONSULTATION -> Color(0xFF5C6BC0) to Color.White
        LessonType.OTHER -> Color(0xFF9E9E9E) to Color.White
    }
}
