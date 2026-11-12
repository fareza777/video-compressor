package com.f7developer.videofit.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f7developer.videofit.core.data.HistoryEntry
import com.f7developer.videofit.core.data.HistoryRepository
import com.f7developer.videofit.core.util.Format
import com.f7developer.videofit.ui.components.Pill
import com.f7developer.videofit.ui.components.SectionLabel
import com.f7developer.videofit.ui.components.TopBar
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    SUCCESS("Success"),
    SKIPPED("Skipped"),
    FAILED("Failed"),
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    repository: HistoryRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { repository.load() }
    }

    val visible = entries
        .filter { e ->
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.SUCCESS -> !e.failed && !e.skipped
                HistoryFilter.SKIPPED -> e.skipped
                HistoryFilter.FAILED -> e.failed
            }
        }
        .filter { query.isBlank() || it.displayName.contains(query, ignoreCase = true) }

    val totalSaved = entries.filterNot { it.failed }.sumOf { it.savedBytes }
    val successful = entries.count { !it.failed }
    val average = entries.filterNot { it.failed || it.skipped }
        .map { it.savedPercent }
        .takeIf { it.isNotEmpty() }
        ?.average() ?: 0.0

    Box(Modifier.fillMaxSize().background(VF.Background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopBar(
                title = "History",
                onBack = onBack,
                action = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { exportCsv(context, entries) }) {
                            Icon(Icons.Rounded.IosShare, "Export CSV", tint = VF.TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.Delete, "Clear history", tint = VF.TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                },
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                item {
                    VFCard(Modifier.fillMaxWidth(), backgroundColor = VF.SurfaceElevated) {
                        Column(Modifier.padding(Space.lg)) {
                            Text(
                                Format.bytes(totalSaved),
                                style = MaterialTheme.typography.displayMedium,
                                color = VF.Accent,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "saved across $successful ${if (successful == 1) "compression" else "compressions"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VF.TextSecondary,
                            )
                            Spacer(Modifier.height(Space.md))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MiniStat("Runs", entries.size.toString())
                                MiniStat("Avg saved", if (average > 0) "${average.toInt()}%" else "—")
                                MiniStat(
                                    "Last",
                                    entries.firstOrNull()?.let {
                                        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it.at))
                                    } ?: "—",
                                )
                            }
                            if (entries.isNotEmpty()) {
                                Spacer(Modifier.height(Space.md))
                                WeekChart(entries)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search by file name") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = VF.TextTertiary) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, "Clear", tint = VF.TextTertiary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VF.Accent,
                            unfocusedBorderColor = VF.Outline,
                            focusedTextColor = VF.TextPrimary,
                            unfocusedTextColor = VF.TextPrimary,
                            cursorColor = VF.Accent,
                            focusedPlaceholderColor = VF.TextTertiary,
                            unfocusedPlaceholderColor = VF.TextTertiary,
                        ),
                    )
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        HistoryFilter.entries.forEach { option ->
                            Pill(
                                text = option.label,
                                selected = filter == option,
                                onClick = { filter = option },
                            )
                        }
                    }
                }

                if (visible.isEmpty()) {
                    item {
                        VFCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(Space.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    if (entries.isEmpty()) "No compressions yet" else "Nothing matches",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = VF.TextPrimary,
                                )
                                Spacer(Modifier.height(Space.xs))
                                Text(
                                    if (entries.isEmpty()) {
                                        "Every run is recorded here with its size and quality."
                                    } else {
                                        "Try another name or filter."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VF.TextTertiary,
                                )
                            }
                        }
                    }
                }

                items(visible, key = { it.id }) { entry ->
                    VFCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            entry.failed -> VF.Danger.copy(alpha = 0.14f)
                                            entry.skipped -> VF.Warning.copy(alpha = 0.16f)
                                            else -> VF.Accent.copy(alpha = 0.14f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    when {
                                        entry.failed -> "!"
                                        entry.skipped -> "="
                                        else -> "${entry.savedPercent}%"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        entry.failed -> VF.Danger
                                        entry.skipped -> VF.Warning
                                        else -> VF.Accent
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VF.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (entry.failed) {
                                        entry.failure ?: "Compression failed"
                                    } else if (entry.skipped) {
                                        "${Format.bytes(entry.sourceBytes)} · already under target"
                                    } else {
                                        "${Format.bytes(entry.sourceBytes)} → ${Format.bytes(entry.outputBytes)} · ${entry.codec} · ${entry.width}x${entry.height}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (entry.failed) VF.Danger else VF.TextTertiary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        append(SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.at)))
                                        if (!entry.failed) {
                                            append(" · ${entry.elapsedMs / 1000}s")
                                            if (entry.ssim != null) append(" · SSIM ${String.format(Locale.US, "%.3f", entry.ssim)}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VF.TextTertiary,
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repository.remove(entry.id) }
                                    entries = withContext(Dispatchers.IO) { repository.load() }
                                }
                            }) {
                                Icon(Icons.Rounded.Close, "Remove", tint = VF.TextTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (entries.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "History is stored on this device only. Deleting an entry never deletes the video file.",
                            style = MaterialTheme.typography.labelSmall,
                            color = VF.TextTertiary,
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("The record disappears; the compressed files stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.clear() }
                        entries = emptyList()
                        confirmClear = false
                    }
                }) { Text("Clear", color = VF.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            containerColor = VF.Surface,
            titleContentColor = VF.TextPrimary,
            textContentColor = VF.TextSecondary,
        )
    }
}

@Composable
private fun WeekChart(entries: List<HistoryEntry>) {
    val days = remember(entries) {
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val labelFormat = SimpleDateFormat("EE", Locale.US)
        (6 downTo 0).map { offset ->
            val cal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -offset)
            }
            val dayMs = cal.timeInMillis
            val saved = entries
                .filter { dayFormat.format(Date(it.at)) == dayFormat.format(Date(dayMs)) }
                .sumOf { it.savedBytes }
            labelFormat.format(Date(dayMs)).take(1) to saved
        }
    }
    val max = days.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    Row(
        Modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { (label, saved) ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((6 + 34 * (saved.toFloat() / max)).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (saved > 0) VF.Accent else VF.Outline),
                )
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = VF.TextTertiary)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = VF.TextTertiary, letterSpacing = 1.sp)
        Text(value, style = MaterialTheme.typography.titleMedium, color = VF.TextPrimary)
    }
}

private fun exportCsv(context: android.content.Context, entries: List<HistoryEntry>) {
    runCatching {
        val header = "file,source_bytes,output_bytes,target_bytes,codec,width,height,ssim,verdict,seconds,timestamp,skipped,failed\n"
        val body = entries.joinToString("\n") { e ->
            listOf(
                "\"${e.displayName.replace("\"", "'")}\"",
                e.sourceBytes, e.outputBytes, e.targetBytes, e.codec,
                e.width, e.height, e.ssim ?: "", "\"${e.verdict}\"",
                e.elapsedMs / 1000,
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(e.at)),
                e.skipped, e.failed,
            ).joinToString(",")
        }
        val file = File(com.f7developer.videofit.core.media.OutputStore.workingDirectory(context), "videofit-history.csv")
        file.writeText(header + body)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Export history"))
    }
}
