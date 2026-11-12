package com.jetbrains.kmpapp.screens.other

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import com.jetbrains.kmpapp.screens.components.swipeToDismissBack
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

@Composable
fun CompareSchedulesScreen(
    viewModel: OtherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)

    val savedTargets by viewModel.savedTargets.collectAsState()
    val scope = rememberCoroutineScope()

    var targetA by remember { mutableStateOf<ScheduleTarget?>(null) }
    var targetB by remember { mutableStateOf<ScheduleTarget?>(null) }
    var lessonsA by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var lessonsB by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var loadingA by remember { mutableStateOf(false) }
    var loadingB by remember { mutableStateOf(false) }

    LaunchedEffect(targetA) {
        val t = targetA
        if (t != null) {
            loadingA = true
            lessonsA = viewModel.fetchLessonsForTarget(t)
            loadingA = false
        } else {
            lessonsA = emptyList()
        }
    }
    LaunchedEffect(targetB) {
        val t = targetB
        if (t != null) {
            loadingB = true
            lessonsB = viewModel.fetchLessonsForTarget(t)
            loadingB = false
        } else {
            lessonsB = emptyList()
        }
    }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("Сравнение расписаний", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Расписание A", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (savedTargets.isEmpty()) {
                Text("Нет сохранённых расписаний", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    savedTargets.take(4).forEach { t ->
                        FilterChip(
                            selected = targetA?.id == t.id,
                            onClick = { targetA = if (targetA?.id == t.id) null else t },
                            label = { Text(t.targetTitle, fontSize = 12.sp, maxLines = 1) }
                        )
                    }
                }
            }

            Text("Расписание B", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (savedTargets.isEmpty()) {
                Text("Нет сохранённых расписаний", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    savedTargets.take(4).forEach { t ->
                        FilterChip(
                            selected = targetB?.id == t.id,
                            onClick = { targetB = if (targetB?.id == t.id) null else t },
                            label = { Text(t.targetTitle, fontSize = 12.sp, maxLines = 1) }
                        )
                    }
                }
            }

            if (loadingA || loadingB) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Загрузка…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (targetA != null && targetB != null && !loadingA && !loadingB) {
                val today = DateUtils.today()
                val dates = (0 until 7).map { today.plus(DatePeriod(days = it)) }
                dates.forEach { date ->
                    val a = lessonsA.filter { it.date == date }
                    val b = lessonsB.filter { it.date == date }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${date.day} ${DateUtils.formatMonthRu(date.month)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("A", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    if (a.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    a.sortedBy { it.bellNumber }.forEach { l ->
                                        Text("${l.bellNumber}. ${l.subject}", fontSize = 12.sp)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("B", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    if (b.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    b.sortedBy { it.bellNumber }.forEach { l ->
                                        Text("${l.bellNumber}. ${l.subject}", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
