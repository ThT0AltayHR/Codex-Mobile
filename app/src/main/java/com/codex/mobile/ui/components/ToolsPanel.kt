package com.codex.mobile.ui.components

/**
 * The right-edge tools panel: Files / Artifacts / Workflow / Shell.
 * Everything shown here reads from real, already-tracked state —
 * the conversation's actual workspace directory, the real completed-step
 * history, and the real stdout/stderr the running codex process produced.
 * Nothing in this file invents data.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.mobile.engine.SamuraiStep
import com.codex.mobile.engine.SamuraiStepEntry
import com.codex.mobile.ui.theme.*
import com.codex.mobile.viewmodel.ShellLogLine
import com.codex.mobile.viewmodel.WorkspaceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ToolsPanelOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    stepHistory: List<SamuraiStepEntry>,
    shellLog: List<ShellLogLine>,
    listFiles: () -> List<WorkspaceEntry>,
    readFile: (String) -> String
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.medium)),
        exit = fadeOut(tween(Motion.fast))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss)
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(Motion.medium)),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(Motion.fast))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight()
                    .background(NearBlack)
            ) {
                ToolsPanelContent(stepHistory, shellLog, listFiles, readFile, onDismiss)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ToolsPanelContent(
    stepHistory: List<SamuraiStepEntry>,
    shellLog: List<ShellLogLine>,
    listFiles: () -> List<WorkspaceEntry>,
    readFile: (String) -> String,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Dimens.lg, end = Dimens.sm, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CodexIcon(CIcon.PanelRight, tint = OffWhite, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Çalışma Alanı", color = OffWhite, fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(PanelBlack)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) { CodexIcon(CIcon.Close, tint = FaintWhite, modifier = Modifier.size(15.dp)) }
    }

    Box(Modifier.padding(horizontal = Dimens.lg)) {
        SegmentedTabRow(
            labels = listOf("Dosyalar", "Yapıtlar", "Akış", "Kabuk"),
            icons = listOf(CIcon.Folder, CIcon.Artifact, CIcon.Workflow, CIcon.Shell),
            selected = tab,
            onSelect = { tab = it }
        )
    }
    Spacer(Modifier.height(Dimens.md))

    Box(Modifier.weight(1f).fillMaxWidth()) {
        when (tab) {
            0 -> FilesTab(listFiles, readFile)
            1 -> ArtifactsTab(stepHistory, readFile)
            2 -> WorkflowTab(stepHistory)
            3 -> ShellTab(shellLog)
        }
    }
}

@Composable
private fun FilesTab(listFiles: () -> List<WorkspaceEntry>, readFile: (String) -> String) {
    var loading by remember { mutableStateOf(true) }
    var files by remember { mutableStateOf<List<WorkspaceEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        loading = true
        files = withContext(Dispatchers.IO) { listFiles() }
        loading = false
    }
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FaintWhite, strokeWidth = 2.dp, modifier = Modifier.size(22.dp)) }
        return
    }
    if (files.isEmpty()) {
        EmptyState(CIcon.Folder, "Henüz dosya yok", "Codex bu sohbette bir dosya oluşturduğunda burada listelenecek.")
        return
    }
    FileListWithPreview(files, readFile)
}

@Composable
private fun ArtifactsTab(stepHistory: List<SamuraiStepEntry>, readFile: (String) -> String) {
    val artifacts = remember(stepHistory) {
        stepHistory
            .filter { it.step in setOf(SamuraiStep.CREATING_FILE, SamuraiStep.EDITING_FILE, SamuraiStep.DELETING_FILE) && it.filePath != null }
            .distinctBy { it.filePath }
            .sortedByDescending { it.timestampMillis }
    }
    if (artifacts.isEmpty()) {
        EmptyState(CIcon.Artifact, "Henüz yapıt yok", "Bu oturumda oluşturulan veya düzenlenen dosyalar burada görünecek.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.sm)) {
        items(artifacts) { entry ->
            val icon = when (entry.step) {
                SamuraiStep.CREATING_FILE -> CIcon.Add
                SamuraiStep.DELETING_FILE -> CIcon.Trash
                else -> CIcon.Edit
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RowScopeIconBadge(icon, tint = OffWhite)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.filePath?.substringAfterLast('/') ?: "", color = OffWhite, fontFamily = TerminalMonoFamily, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(entry.filePath ?: "", color = FaintWhite, fontFamily = BodyFamily, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WorkflowTab(stepHistory: List<SamuraiStepEntry>) {
    if (stepHistory.isEmpty()) {
        EmptyState(CIcon.Workflow, "Akış boş", "Codex bir görev üzerinde çalışmaya başladığında adımlar burada listelenecek.")
        return
    }
    Box(Modifier.padding(horizontal = Dimens.lg)) {
        SamuraiTimeline(stepHistory, maxHeight = 2000.dp)
    }
}

@Composable
private fun ShellTab(shellLog: List<ShellLogLine>) {
    if (shellLog.isEmpty()) {
        EmptyState(CIcon.Shell, "Kabuk boş", "codex süreci çalışırken ham stdout/stderr çıktısı burada akacak.")
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(shellLog.size) {
        if (shellLog.isNotEmpty()) listState.animateScrollToItem(shellLog.size - 1)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.sm),
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        items(shellLog) { line ->
            Text(
                line.text,
                color = if (line.isError) ErrorRed.copy(alpha = 0.9f) else MutedWhite,
                fontFamily = TerminalMonoFamily,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun FileListWithPreview(files: List<WorkspaceEntry>, readFile: (String) -> String) {
    var expandedPath by remember { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.sm)) {
        items(files) { entry ->
            val isExpanded = expandedPath == entry.relativePath
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(if (isExpanded) PanelBlack else Color.Transparent)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            if (isExpanded) { expandedPath = null } else {
                                expandedPath = entry.relativePath
                                previewText = readFile(entry.relativePath)
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RowScopeIconBadge(iconForFileName(entry.relativePath), tint = MutedWhite)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.relativePath.substringAfterLast('/'), color = OffWhite, fontFamily = TerminalMonoFamily, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(formatSize(entry.sizeBytes), color = FaintWhite, fontFamily = BodyFamily, fontSize = 11.sp)
                    }
                    CodexIcon(if (isExpanded) CIcon.ChevronDown else CIcon.ChevronRight, tint = FaintWhite, modifier = Modifier.size(14.dp))
                }
                AnimatedVisibility(visible = isExpanded, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(Dimens.radiusSm))
                            .background(PureBlack)
                            .padding(10.dp)
                    ) {
                        Text(
                            previewText.ifBlank { "(boş dosya)" },
                            color = MutedWhite,
                            fontFamily = TerminalMonoFamily,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

private fun iconForFileName(path: String): CIcon {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png", "jpg", "jpeg", "webp", "gif", "svg" -> CIcon.FileImage
        "zip", "tar", "gz", "7z", "rar" -> CIcon.FileArchive
        "json", "xml", "yaml", "yml", "csv" -> CIcon.FileData
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "rs", "go", "swift", "html", "css", "sh" -> CIcon.CodeBrackets
        else -> CIcon.FileGeneric
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
}
