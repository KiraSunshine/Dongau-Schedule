package com.jetbrains.kmpapp.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.TaskRepository
import com.jetbrains.kmpapp.data.model.AssessmentType
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.DefaultSubjectColors
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonNote
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import com.jetbrains.kmpapp.data.model.Subject
import com.jetbrains.kmpapp.data.model.SubjectImportance
import com.jetbrains.kmpapp.data.model.SubjectNote
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import com.jetbrains.kmpapp.screens.components.swipeToDismissBack
import org.koin.compose.koinInject
import kotlin.time.Clock

@Composable
fun LessonDetailScreen(
    lesson: Lesson,
    targetId: Int = 0,
    lessonNote: LessonNote? = null,
    subjectNote: SubjectNote? = null,
    isCustom: Boolean = false,
    onBack: () -> Unit,
    onOpenEntity: (name: String, type: ScheduleTargetType) -> Unit = { _, _ -> },
    onSaveLessonNote: (text: String) -> Unit = {},
    onSaveSubjectNote: (text: String) -> Unit = {},
    onEditCustom: () -> Unit = {},
    onDeleteCustom: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)
    val taskRepository: TaskRepository = koinInject()
    val subjects by taskRepository.subjects.collectAsState()
    val isAlreadyAdded = subjects.any { it.name.trim().equals(lesson.subject.trim(), ignoreCase = true) }

    val (typeBg, typeTextColor) = getTypeBadgeColors(lesson.lessonType)

    val weekInfo = DateUtils.getWeekInfo(lesson.date)
    val parityStr = if (weekInfo.isEven) "Чётная неделя" else "Нечётная неделя"

    var lessonNoteText by remember(lessonNote?.text, targetId, lesson.id) { mutableStateOf(lessonNote?.text.orEmpty()) }
    var subjectNoteText by remember(subjectNote?.text, targetId, lesson.subject) { mutableStateOf(subjectNote?.text.orEmpty()) }
    var editLessonNote by remember(targetId, lesson.id) { mutableStateOf(false) }
    var editSubjectNote by remember(targetId, lesson.subject) { mutableStateOf(false) }

    LaunchedEffect(lessonNote?.text) { lessonNoteText = lessonNote?.text.orEmpty() }
    LaunchedEffect(subjectNote?.text) { subjectNoteText = subjectNote?.text.orEmpty() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "О занятии",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
            .swipeToDismissBack(requireEdge = true, onBack = onBack)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main card with subject and type
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(typeBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = lesson.lessonType.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeTextColor
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = lesson.subject,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Time & Date card
            DetailSectionCard(
                title = "Время и дата",
                icon = Icons.Default.AccessTime
            ) {
                DetailItem(
                    label = "Пара",
                    value = "${lesson.bellNumber} пара (${lesson.startTime} — ${lesson.endTime})"
                )
                Spacer(modifier = Modifier.height(10.dp))
                DetailItem(
                    label = "Дата",
                    value = "${lesson.date.day} ${DateUtils.formatMonthRu(lesson.date.month)} ${lesson.date.year} (${DateUtils.formatDayOfWeekShort(lesson.date.dayOfWeek)})"
                )
                Spacer(modifier = Modifier.height(10.dp))
                DetailItem(
                    label = "Неделя",
                    value = "${weekInfo.weekNumber} неделя • $parityStr"
                )
            }

            // Teachers card
            if (lesson.teachers.isNotEmpty()) {
                DetailSectionCard(
                    title = if (lesson.teachers.size > 1) "Преподаватели" else "Преподаватель",
                    icon = Icons.Default.Person
                ) {
                    lesson.teachers.forEachIndexed { index, teacher ->
                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                        ClickableEntityText(
                            text = teacher,
                            onClick = { onOpenEntity(teacher, ScheduleTargetType.TEACHER) }
                        )
                    }
                }
            }

            // Classrooms card
            if (lesson.classrooms.isNotEmpty()) {
                DetailSectionCard(
                    title = if (lesson.classrooms.size > 1) "Аудитории" else "Аудитория",
                    icon = Icons.Default.LocationOn
                ) {
                    lesson.classrooms.forEachIndexed { index, room ->
                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                        ClickableEntityText(
                            text = room,
                            onClick = { onOpenEntity(room, ScheduleTargetType.AUDITORIUM) }
                        )
                    }
                }
            }

            // Groups card
            if (lesson.groups.isNotEmpty()) {
                DetailSectionCard(
                    title = if (lesson.groups.size > 1) "Группы" else "Группа",
                    icon = Icons.Default.Group
                ) {
                    lesson.groups.forEachIndexed { index, group ->
                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                        ClickableEntityText(
                            text = group,
                            onClick = { onOpenEntity(group, ScheduleTargetType.GROUP) }
                        )
                    }
                }
            }

            // Notes
            if (targetId != 0) {
                DetailSectionCard(
                    title = "Заметка к этой паре",
                    icon = Icons.Default.EditNote
                ) {
                    if (editLessonNote || lessonNoteText.isNotBlank()) {
                        OutlinedTextField(
                            value = lessonNoteText,
                            onValueChange = { lessonNoteText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Например: принести отчёт, подготовить вопросы…") },
                            minLines = 2,
                            maxLines = 6
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                onSaveLessonNote(lessonNoteText)
                                editLessonNote = false
                            }) { Text("Сохранить") }
                            if (lessonNoteText.isNotBlank()) {
                                TextButton(onClick = {
                                    lessonNoteText = ""
                                    onSaveLessonNote("")
                                    editLessonNote = false
                                }) { Text("Удалить") }
                            }
                        }
                    } else {
                        Text(
                            text = "Нет заметки. Нажмите, чтобы добавить.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { editLessonNote = true }
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                DetailSectionCard(
                    title = "Заметка к предмету",
                    icon = Icons.Default.EditNote
                ) {
                    if (editSubjectNote || subjectNoteText.isNotBlank()) {
                        OutlinedTextField(
                            value = subjectNoteText,
                            onValueChange = { subjectNoteText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Общая заметка для всех пар этого предмета…") },
                            minLines = 2,
                            maxLines = 6
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                onSaveSubjectNote(subjectNoteText)
                                editSubjectNote = false
                            }) { Text("Сохранить") }
                            if (subjectNoteText.isNotBlank()) {
                                TextButton(onClick = {
                                    subjectNoteText = ""
                                    onSaveSubjectNote("")
                                    editSubjectNote = false
                                }) { Text("Удалить") }
                            }
                        }
                    } else {
                        Text(
                            text = "Нет заметки к предмету. Нажмите, чтобы добавить.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { editSubjectNote = true }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            if (isCustom) {
                Text(
                    text = "Пользовательская пара",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onEditCustom,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Редактировать пару", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Button(
                    onClick = {
                        onDeleteCustom()
                        onBack()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Удалить пару", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    if (!isAlreadyAdded) {
                        val words = lesson.subject.split(" ", "-", "_").filter { it.isNotBlank() }
                        val shortCode = if (words.size > 1) {
                            words.mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(4).joinToString("")
                        } else {
                            lesson.subject.take(3).uppercase()
                        }
                        val colorIdx = (lesson.subject.hashCode().and(0x7fffffff)) % DefaultSubjectColors.size
                        val newSubject = Subject(
                            id = Clock.System.now().toEpochMilliseconds().toString(),
                            name = lesson.subject.trim(),
                            shortCode = shortCode,
                            colorHex = DefaultSubjectColors[colorIdx],
                            importance = SubjectImportance.MEDIUM,
                            assessmentType = when (lesson.lessonType) {
                                LessonType.EXAM -> AssessmentType.EXAM
                                LessonType.CREDIT -> AssessmentType.TEST
                                LessonType.DIFF_CREDIT -> AssessmentType.DIFF_TEST
                                LessonType.CONSULTATION -> AssessmentType.TEST
                                LessonType.LAB -> AssessmentType.CREDIT
                                LessonType.PRACTICE -> AssessmentType.TEST
                                LessonType.LECTURE, LessonType.OTHER -> AssessmentType.EXAM
                            },
                            teacherName = lesson.teachers.joinToString(", "),
                            roomOrLink = lesson.classrooms.joinToString(", "),
                            notes = "Добавлено из расписания"
                        )
                        taskRepository.addSubject(newSubject)
                    }
                },
                enabled = !isAlreadyAdded,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = if (isAlreadyAdded) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAlreadyAdded) "Предмет уже в задачах" else "Добавить предмет в задачи",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun ClickableEntityText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    )
}

@Composable
private fun DetailSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
