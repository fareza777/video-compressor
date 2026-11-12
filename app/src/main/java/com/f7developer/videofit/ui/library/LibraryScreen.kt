package com.f7developer.videofit.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.f7developer.videofit.core.data.LibraryItem
import com.f7developer.videofit.core.data.LibraryRepository
import com.f7developer.videofit.core.media.OutputStore
import com.f7developer.videofit.core.util.Format
import com.f7developer.videofit.ui.components.Pill
import com.f7developer.videofit.ui.components.SectionLabel
import com.f7developer.videofit.ui.components.TopBar
import com.f7developer.videofit.ui.components.VFCard
import com.f7developer.videofit.ui.components.VideoThumbnail
import com.f7developer.videofit.ui.theme.Space
import com.f7developer.videofit.ui.theme.VF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibrarySort(val label: String) {
    NEWEST("Newest"),
    LARGEST("Largest"),
    SMALLEST("Smallest"),
    NAME("Name"),
}

@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    repository: LibraryRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<LibraryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.NEWEST) }
    var playing by remember { mutableStateOf<LibraryItem?>(null) }
    var pendingDelete by remember { mutableStateOf<LibraryItem?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        items = withContext(Dispatchers.IO) { repository.list() }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val visible = items
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .let { list ->
            when (sort) {
                LibrarySort.NEWEST -> list.sortedByDescending { it.modifiedAt }
                LibrarySort.LARGEST -> list.sortedByDescending { it.bytes }
                LibrarySort.SMALLEST -> list.sortedBy { it.bytes }
                LibrarySort.NAME -> list.sortedBy { it.name.lowercase(Locale.US) }
            }
        }

    Box(Modifier.fillMaxSize().background(VF.Background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopBar(title = "Library", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Space.lg, end = Space.lg, bottom = Space.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                item {
                    VFCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Space.lg)) {
                            Text(
                                "${items.size} ${if (items.size == 1) "video" else "videos"}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = VF.TextPrimary,
                            )
                            Text(
                                "${Format.bytes(items.sumOf { it.bytes })} stored on this device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VF.TextTertiary,
                            )
                            if (items.isNotEmpty()) {
                                Spacer(Modifier.height(Space.sm))
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    Text(
                                        "Save all to gallery",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = VF.Accent,
                                        modifier = Modifier
                                            .clickable {
                                                scope.launch {
                                                    var saved = 0
                                                    withContext(Dispatchers.IO) {
                                                        items.forEach {
                                                            if (OutputStore.saveToGallery(context, it.file, it.name)) saved++
                                                        }
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        "$saved of ${items.size} saved to Movies/VideoFit",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                            .padding(vertical = Space.xs),
                                    )
                                    Text(
                                        "Free up space",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = VF.Danger,
                                        modifier = Modifier
                                            .clickable { confirmWipe = true }
                                            .padding(vertical = Space.xs),
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search your library") },
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
                        LibrarySort.entries.forEach { option ->
                            Pill(
                                text = option.label,
                                selected = sort == option,
                                onClick = { sort = option },
                            )
                        }
                    }
                }

                if (loading) {
                    items(3) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(VF.SurfaceElevated),
                        )
                    }
                }

                if (!loading && visible.isEmpty()) {
                    item {
                        VFCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(Space.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    if (items.isEmpty()) "Nothing here yet" else "No matches",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = VF.TextPrimary,
                                )
                                Spacer(Modifier.height(Space.xs))
                                Text(
                                    if (items.isEmpty()) {
                                        "Compressed videos land here automatically."
                                    } else {
                                        "Try a different name."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VF.TextTertiary,
                                )
                            }
                        }
                    }
                }

                items(visible, key = { it.file.absolutePath }) { item ->
                    VFCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Space.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    VideoThumbnail(
                                        uri = Uri.fromFile(item.file),
                                        modifier = Modifier.size(width = 84.dp, height = 56.dp),
                                    )
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { playing = item },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(Space.md))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VF.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${Format.bytes(item.bytes)} · ${item.resolution} · ${Format.durationFromMillis(item.durationMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VF.TextTertiary,
                                    )
                                    Text(
                                        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                                            .format(Date(item.modifiedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VF.TextTertiary,
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = Space.sm),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { shareFile(context, item) }) {
                                    Icon(Icons.Rounded.Share, "Share", tint = VF.TextSecondary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            OutputStore.saveToGallery(context, item.file, item.name)
                                        }
                                        Toast.makeText(
                                            context,
                                            if (ok) "Saved to Movies/VideoFit" else "Could not save",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }) {
                                    Icon(Icons.Rounded.Download, "Save to gallery", tint = VF.TextSecondary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { pendingDelete = item }) {
                                    Icon(Icons.Rounded.Delete, "Delete", tint = VF.Danger, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    playing?.let { item ->
        PlayerDialog(item = item, onDismiss = { playing = null })
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all ${items.size} files?") },
            text = { Text("They are removed from the app. Anything already saved to the gallery stays there.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { items.forEach { repository.delete(it) } }
                        confirmWipe = false
                        refresh()
                    }
                }) { Text("Delete all", color = VF.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
            containerColor = VF.Surface,
            titleContentColor = VF.TextPrimary,
            textContentColor = VF.TextSecondary,
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this file?") },
            text = { Text("${item.name} will be removed from the app. Files already saved to the gallery are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.delete(item) }
                        pendingDelete = null
                        refresh()
                    }
                }) { Text("Delete", color = VF.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
            containerColor = VF.Surface,
            titleContentColor = VF.TextPrimary,
            textContentColor = VF.TextSecondary,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerDialog(item: LibraryItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(item.file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(item.file)))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(Icons.Rounded.Close, "Close", tint = Color.White)
            }
        }
    }
}

private fun shareFile(context: Context, item: LibraryItem) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            item.file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share video"))
    }.onFailure {
        Toast.makeText(context, "Could not share this file", Toast.LENGTH_SHORT).show()
    }
}
