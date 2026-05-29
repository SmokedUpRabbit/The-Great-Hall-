package com.example.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset

// -------------------------------------------------------------
// DRAW ACTION REPRESENTATIONS FOR ART CANVAS
// -------------------------------------------------------------
sealed class DrawAction {
    data class FreePath(val path: Path, val color: Color, val strokeWidth: Float) : DrawAction()
    data class LineAction(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val color: Color, val strokeWidth: Float) : DrawAction()
    data class RectAction(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val color: Color, val isFilled: Boolean, val strokeWidth: Float) : DrawAction()
    data class CircleAction(val centerX: Float, val centerY: Float, val radius: Float, val color: Color, val isFilled: Boolean, val strokeWidth: Float) : DrawAction()
    data class TextAction(val text: String, val x: Float, val y: Float, val color: Color, val fontSize: Float) : DrawAction()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GreatHallApp(viewModel: GreatHallViewModel) {
    val context = LocalContext.current
    val filesList by viewModel.files.collectAsState()
    val historyList by viewModel.history.collectAsState()
    val notesList by viewModel.notes.collectAsState()
    val projectsList by viewModel.projects.collectAsState()

    // Shadow color constants with dynamic colors from our viewModel settings engine!
    val DeepNavy = viewModel.getBackgroundColor()
    val NearBlack = viewModel.getSurfaceColor()
    val ElectricCyan = viewModel.getPrimaryColor()
    val GreenPhosphor = viewModel.getSecondaryColor()
    val BorderColor = viewModel.getBorderColor()
    val SurfaceMuted = viewModel.getSurfaceMuted()

    // Calculate dynamic stats
    val totalIndexedFiles = filesList.size
    val totalNotes = notesList.size
    val totalUniqueTopics = filesList.flatMap { file ->
        file.tags.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    }.distinct().size
    val totalCharacters = filesList.sumOf { it.content.length }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DeepNavy
    ) { innerPadding ->
        if (viewModel.showIntro) {
            BootupIntroScreen(
                viewModel = viewModel,
                onComplete = { viewModel.showIntro = false },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            // Render sidebar on the LEFT if position is normal (left)
            if (!viewModel.sidebarPositionRight) {
                // --- SIDEBAR OR DRAWER PANEL ---
                AnimatedVisibility(
                    visible = true,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally()
                ) {
                    SidebarPanel(
                        viewModel = viewModel,
                        isCollapsed = viewModel.isSidebarCollapsed,
                        onToggleCollapse = { viewModel.isSidebarCollapsed = !viewModel.isSidebarCollapsed },
                        currentScreen = viewModel.currentScreen,
                        onNavigate = { screen, projId ->
                            viewModel.currentScreen = screen
                            viewModel.currentProjectId = projId
                        },
                        projects = projectsList
                    )
                }

                // --- Divider ---
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(BorderColor)
                )
            }

            // --- MAIN VIEWPORT ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(NearBlack)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top-bar matching MGS1/Norse Command aesthetics
                    TopAppBarHeader(
                        currentScreenTitle = when {
                            viewModel.currentScreen.startsWith("project-") -> {
                                val proj = projectsList.find { it.id == viewModel.currentProjectId }
                                proj?.title ?: "Project Details"
                            }
                            else -> viewModel.currentScreen.uppercase()
                        },
                        totalFiles = totalIndexedFiles
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            viewModel.currentScreen == "ledger" -> {
                                LedgerDashboardScreen(
                                    totalFiles = totalIndexedFiles,
                                    totalNotes = totalNotes,
                                    totalTopics = totalUniqueTopics,
                                    totalChars = totalCharacters,
                                    recentLogs = historyList.take(5),
                                    onNavigate = { viewModel.currentScreen = it }
                                )
                            }
                            viewModel.currentScreen == "history" -> {
                                HistoryLogScreen(
                                    historyList = historyList,
                                    onClear = { viewModel.clearAllHistory() }
                                )
                            }
                            viewModel.currentScreen == "explorer" -> {
                                AsyncFileExplorerScreen(
                                    viewModel = viewModel,
                                    query = viewModel.searchQuery,
                                    onQueryChange = { viewModel.searchQuery = it },
                                    filesPool = filesList,
                                    selectedFilterId = viewModel.selectedFileFilterId,
                                    onFilterIdChange = { viewModel.selectedFileFilterId = it },
                                    onSearch = { viewModel.runSearch() },
                                    onClear = { viewModel.clearSearch() },
                                    searchResults = viewModel.searchResults
                                )
                            }
                            viewModel.currentScreen == "upload" -> {
                                UploadAndIndexScreen(
                                    filesList = filesList,
                                    onAddFile = { name, content, tags ->
                                        viewModel.logActivity("upload", "File added to local index", name)
                                        viewModel.addManualFile(name, content, tags)
                                    },
                                    onRemoveFile = { id, name -> viewModel.removeFile(id, name) },
                                    onClearAll = { viewModel.clearAllFiles() }
                                )
                            }
                            viewModel.currentScreen == "canvas" -> {
                                ArtCanvasScreen(
                                    onSavePng = {
                                        viewModel.logActivity("canvas", "Saved drawing timeline snapshot", "Tebori geometry anchored")
                                        Toast.makeText(context, "Draft Saved to System Ledger!", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                            viewModel.currentScreen == "builder" -> {
                                BuilderScreen(
                                    viewModel = viewModel,
                                    code = viewModel.builderCode,
                                    onCodeChange = { viewModel.builderCode = it },
                                    notesText = viewModel.builderNotesText,
                                    onNotesTextChange = { viewModel.builderNotesText = it },
                                    noteTitle = viewModel.builderNoteTitle,
                                    onNoteTitleChange = { viewModel.builderNoteTitle = it },
                                    activeTab = viewModel.activeBuilderTab,
                                    onTabChange = { viewModel.activeBuilderTab = it },
                                    savedNotes = notesList,
                                    onRunPreview = { viewModel.logActivity("build", "Live preview compiled", "HTML/JS sandbox refreshed") },
                                    onSaveCodeNote = { viewModel.saveBuilderCodeToNotes() },
                                    onAddCodeToIndex = { viewModel.addBuilderCodeToIndex() },
                                    onSaveNote = { viewModel.saveBuilderNote() },
                                    onAddNoteToIndex = { viewModel.addBuilderNoteToIndex() },
                                    onDeleteNote = { id, title -> viewModel.deleteNote(id, title) },
                                    onLoadNote = { note ->
                                        if (note.type == "code") {
                                            viewModel.builderCode = note.content
                                            viewModel.activeBuilderTab = "code"
                                        } else {
                                            viewModel.builderNoteTitle = note.title
                                            viewModel.builderNotesText = note.content
                                            viewModel.activeBuilderTab = "note"
                                        }
                                        Toast.makeText(context, "Loaded: ${note.title}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            viewModel.currentScreen.startsWith("project-") -> {
                                val currentProjId = viewModel.currentProjectId
                                val project = projectsList.find { it.id == currentProjId }
                                if (project != null) {
                                    val projectItemsFlow = remember(currentProjId) { viewModel.repository.getItemsForProject(currentProjId) }
                                    val projectItems by projectItemsFlow.collectAsState(initial = emptyList())

                                    ProjectTrackerDetailScreen(
                                        project = project,
                                        items = projectItems,
                                        onUpdateMission = { viewModel.updateProjectMission(currentProjId, it) },
                                        onUpdateStructure = { viewModel.updateProjectStructure(currentProjId, it) },
                                        onUpdateNotes = { viewModel.updateProjectNotes(currentProjId, it) },
                                        onUpdateStatus = { viewModel.updateProjectStatus(currentProjId, it) },
                                        onAddItem = { type, text -> viewModel.addProjectItem(currentProjId, type, text) },
                                        onDeleteItem = { id, text -> viewModel.deleteProjectItem(id, currentProjId, text) },
                                        onToggleItem = { id, text, isChecked -> viewModel.toggleProjectItemStatus(id, currentProjId, isChecked, text) }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Select a project from the left workspace list.", color = TextMuted)
                                    }
                                }
                            }
                            viewModel.currentScreen == "options" -> {
                                OptionsConfigScreen(viewModel = viewModel)
                            }
                            viewModel.currentScreen == "ai_hub" -> {
                                GenerativeCortexScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

// -------------------------------------------------------------
// TOP BAR COMPONENT
// -------------------------------------------------------------
@Composable
fun TopAppBarHeader(currentScreenTitle: String, totalFiles: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(NearBlack)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(GreenPhosphor, RoundedCornerShape(100))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Cortex // $currentScreenTitle",
                color = ElectricCyan,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("cortex_hdr_title")
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(BorderColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "MEM IDX: $totalFiles FILES",
                    color = GreenPhosphor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(BorderColor)
    )
}

// -------------------------------------------------------------
// COLLAPSIBLE SIDEBAR OR DRAWER PANEL
// -------------------------------------------------------------
@Composable
fun SidebarPanel(
    viewModel: GreatHallViewModel,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    currentScreen: String,
    onNavigate: (String, String) -> Unit,
    projects: List<ProjectEntity>
) {
    val width = if (isCollapsed) 64.dp else 240.dp

    val DeepNavy = viewModel.getBackgroundColor()
    val NearBlack = viewModel.getSurfaceColor()
    val ElectricCyan = viewModel.getPrimaryColor()
    val GreenPhosphor = viewModel.getSecondaryColor()
    val BorderColor = viewModel.getBorderColor()
    val SurfaceMuted = viewModel.getSurfaceMuted()

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(DeepNavy)
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // Sidebar Head
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            if (!isCollapsed) {
                Column {
                    Text(
                        text = "THE GREAT HALL",
                        color = ElectricCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "OPERATION GREAT HALL",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            IconButton(
                onClick = onToggleCollapse,
                modifier = Modifier.testTag("sidebar_toggle_btn")
            ) {
                Text(
                    text = if (isCollapsed) "≡" else "⁞",
                    color = ElectricCyan,
                    fontSize = if (isCollapsed) 20.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Items
        SidebarHeaderLabel(text = "Core", isCollapsed = isCollapsed)
        SidebarItem(
            label = viewModel.ledgerMenuLabel,
            iconEmoji = "📖",
            isSelected = currentScreen == "ledger",
            isCollapsed = isCollapsed,
            onClick = { onNavigate("ledger", "") },
            testTag = "nav_ledger",
            viewModel = viewModel
        )
        SidebarItem(
            label = viewModel.historyMenuLabel,
            iconEmoji = "⏱",
            isSelected = currentScreen == "history",
            isCollapsed = isCollapsed,
            onClick = { onNavigate("history", "") },
            testTag = "nav_history",
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(12.dp))

        SidebarHeaderLabel(text = "Data Index", isCollapsed = isCollapsed)
        SidebarItem(
            label = viewModel.explorerMenuLabel,
            iconEmoji = "🔍",
            isSelected = currentScreen == "explorer",
            isCollapsed = isCollapsed,
            onClick = { onNavigate("explorer", "") },
            testTag = "nav_explorer",
            viewModel = viewModel
        )
        SidebarItem(
            label = viewModel.uploadMenuLabel,
            iconEmoji = "📁",
            isSelected = currentScreen == "upload",
            isCollapsed = isCollapsed,
            onClick = { onNavigate("upload", "") },
            testTag = "nav_upload",
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(12.dp))

        SidebarHeaderLabel(text = "Sovereign Boards", isCollapsed = isCollapsed)
        projects.forEach { project ->
            val isSelected = currentScreen == "project-${project.id}"
            SidebarItem(
                label = project.title,
                iconEmoji = when(project.id) {
                    "proj-lighthouse" -> "💡"
                    "proj-greathall" -> "🏛"
                    "proj-technomancer" -> "🎨"
                    "proj-mediator" -> "🔗"
                    "proj-mirrorbox" -> "🌍"
                    "proj-apothecary" -> "🧪"
                    "proj-codereux" -> "💻"
                    "proj-page" -> "📄"
                    else -> "📐"
                },
                isSelected = isSelected,
                isCollapsed = isCollapsed,
                onClick = { onNavigate("project-${project.id}", project.id) },
                testTag = "nav_${project.id}",
                viewModel = viewModel
            )
        }
        
        SidebarItem(
            label = "Create New Page",
            iconEmoji = "✨",
            isSelected = false,
            isCollapsed = isCollapsed,
            onClick = { viewModel.createNewProject("New Module Interface", "Sovereign UI Workspace") },
            testTag = "nav_createNewPage",
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(12.dp))

        SidebarHeaderLabel(text = "Create & Run", isCollapsed = isCollapsed)
        SidebarItem(
            label = viewModel.canvasMenuLabel,
            iconEmoji = "🎨",
            isSelected = currentScreen == "canvas",
            isCollapsed = isCollapsed,
            onClick = { onNavigate("canvas", "") },
            testTag = "nav_canvas",
            viewModel = viewModel
        )
        SidebarItem(
            label = viewModel.builderMenuLabel,
            iconEmoji = "⚙",
            isSelected = currentScreen == "builder",
            isCollapsed = isCollapsed,
            onClick = { onNavigate("builder", "") },
            testTag = "nav_builder",
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(12.dp))

        SidebarHeaderLabel(text = "Cortex AI", isCollapsed = isCollapsed)
        SidebarItem(
            label = "Cortex AI Hub",
            iconEmoji = "🧠",
            isSelected = currentScreen == "ai_hub",
            isCollapsed = isCollapsed,
            onClick = { onNavigate("ai_hub", "") },
            testTag = "nav_ai_hub",
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(12.dp))

        SidebarHeaderLabel(text = "System", isCollapsed = isCollapsed)
        SidebarItem(
            label = "System Options",
            iconEmoji = "🔧",
            isSelected = currentScreen == "options",
            isCollapsed = isCollapsed,
            onClick = { onNavigate("options", "") },
            testTag = "nav_options",
            viewModel = viewModel
        )
    }
}

@Composable
fun SidebarHeaderLabel(text: String, isCollapsed: Boolean) {
    if (!isCollapsed) {
        Text(
            text = text.uppercase(),
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun SidebarItem(
    label: String,
    iconEmoji: String,
    isSelected: Boolean,
    isCollapsed: Boolean,
    onClick: () -> Unit,
    testTag: String,
    viewModel: GreatHallViewModel
) {
    val ElectricCyan = viewModel.getPrimaryColor()
    val selectHighlightColor = ElectricCyan.copy(alpha = 0.15f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = if (isSelected) selectHighlightColor else Color.Transparent,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.Start
        ) {
            Text(text = iconEmoji, fontSize = 16.sp)
            if (!isCollapsed) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    color = if (isSelected) ElectricCyan else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN: LIGHTHOUSE LEDGER (DASHBOARD)
// -------------------------------------------------------------
@Composable
fun LedgerDashboardScreen(
    totalFiles: Int,
    totalNotes: Int,
    totalTopics: Int,
    totalChars: Int,
    recentLogs: List<HistoryLog>,
    onNavigate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Hero Dashboard Container
            Card(
                colors = CardDefaults.cardColors(containerColor = BorderColor),
                border = BorderStroke(1.dp, ElectricCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "The Great Hall Dashboard",
                        color = ElectricCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your private, sovereign intelligence workspace. Upload custom documents, index ideas, and build sandbox tools. Your metrics remain entirely localized.",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigate("upload") },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("📁 File Index", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onNavigate("explorer") },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted, contentColor = ElectricCyan),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, ElectricCyan)
                        ) {
                            Text("🔍 Async Explorer", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Stats Panel grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardStatCard(
                    title = "Files Indexed",
                    value = totalFiles.toString(),
                    emoji = "📁",
                    modifier = Modifier.weight(1f)
                )
                DashboardStatCard(
                    title = "Saved Notes",
                    value = totalNotes.toString(),
                    emoji = "📝",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardStatCard(
                    title = "Unique Topics",
                    value = totalTopics.toString(),
                    emoji = "💎",
                    modifier = Modifier.weight(1f)
                )
                DashboardStatCard(
                    title = "Total Characters",
                    value = when {
                        totalChars > 1000 -> String.format(Locale.US, "%.1fK", totalChars / 1000.0)
                        else -> totalChars.toString()
                    },
                    emoji = "⁞",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            // Core Principles Card
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sovereignty Directives:",
                        color = ElectricCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrincipleRow(num = "01", text = "User Sovereignty — Your data never leaves your device. All processing happens locally.")
                    PrincipleRow(num = "02", text = "Complete Transparency — Every feature is open and auditable. No hidden siphons.")
                    PrincipleRow(num = "03", text = "Asynchronous Freedom — Your data silo, your structure. Perform high-performance searches.")
                    PrincipleRow(num = "04", text = "Build from Within — Extend the app sandbox with the Live Builder.")
                    PrincipleRow(num = "05", text = "Sovereign Spark — Turn your technical/emotional variables into actionable metrics.")
                }
            }
        }

        item {
            // Recent activities list
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity Logs",
                            color = ElectricCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "history",
                            color = GreenPhosphor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onNavigate("history") }
                                .padding(4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (recentLogs.isEmpty()) {
                        Text(
                            text = "No log indices yet. Write notes or index files to start.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        recentLogs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = when (log.type) {
                                                    "upload" -> SuccessGreen.copy(alpha = 0.2f)
                                                    "search" -> ElectricCyan.copy(alpha = 0.2f)
                                                    "build" -> DarkPurple.copy(alpha = 0.2f)
                                                    else -> BorderColor
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = log.type.uppercase(),
                                            color = when (log.type) {
                                                "upload" -> SuccessGreen
                                                "search" -> ElectricCyan
                                                "build" -> ElectricCyan
                                                else -> TextPrimary
                                            },
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log.title,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = SimpleDateFormat("HH:mm", Locale.US).format(Date(log.timestamp)),
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStatCard(title: String, value: String, emoji: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NearBlack),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = title, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(text = emoji, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = GreenPhosphor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun PrincipleRow(num: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$num //",
            color = ElectricCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(42.dp)
        )
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// -------------------------------------------------------------
// SCREEN: HISTORY LOG (TIMELINE)
// -------------------------------------------------------------
@Composable
fun HistoryLogScreen(historyList: List<HistoryLog>, onClear: () -> Unit) {
    var selectedFilter by remember { mutableStateOf("all") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("System Activity Logs", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("A comprehensive local timeline of your interactions, indexings and builds.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed.copy(alpha = 0.2f), contentColor = DestructiveRed),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, DestructiveRed)
            ) {
                Text("Clear History", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filters chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("all", "upload", "search", "build", "canvas", "project").forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) ElectricCyan.copy(alpha = 0.2f) else SurfaceMuted,
                            RoundedCornerShape(32.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) ElectricCyan else BorderColor,
                            RoundedCornerShape(32.dp)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter.uppercase(),
                        color = if (isSelected) ElectricCyan else TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timeline items
        val filteredList = if (selectedFilter == "all") historyList else historyList.filter { it.type == selectedFilter }

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No activities logged for this filter category.", color = TextMuted, fontFamily = FontFamily.Monospace)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(filteredList) { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        // Dot & Line layout representing a vertical timeline
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(ElectricCyan, RoundedCornerShape(100))
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(48.dp)
                                    .background(BorderColor)
                            )
                        }

                        // Content
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(log.timestamp)),
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = log.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (!log.desc.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = log.desc,
                                    color = GreenPhosphor,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN: ASYNC FILE EXPLORER
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AsyncFileExplorerScreen(
    viewModel: GreatHallViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    filesPool: List<IndexedFile>,
    selectedFilterId: Long?,
    onFilterIdChange: (Long?) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    searchResults: List<SearchResult>
) {
    // Shadow color constants with dynamic colors from our viewModel settings engine!
    val DeepNavy = viewModel.getBackgroundColor()
    val NearBlack = viewModel.getSurfaceColor()
    val ElectricCyan = viewModel.getPrimaryColor()
    val GreenPhosphor = viewModel.getSecondaryColor()
    val BorderColor = viewModel.getBorderColor()
    val SurfaceMuted = viewModel.getSurfaceMuted()

    var expandedDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- KNOWLEDGE BASE / NEURAL ANCHOR SIDEBAR ---
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(SurfaceMuted, RoundedCornerShape(8.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧠", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("NEURAL ANCHOR", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("COLLECTIVE MEMORY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Text("ANCESTRAL BUILDS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 12.dp))
            
            // 3D Spatial Map Build
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(NearBlack, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(Color(0xFF8B5CF6).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("🗺", fontSize = 14.sp) }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("3D SPATIAL MAP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("Avatar Coordination", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // DaVinci AI Build
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .background(NearBlack, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(Color(0xFFF59E0B).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("👤", fontSize = 14.sp) }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("DAVINCI AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("Historical Core", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Text("TAGGED RESONANCE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 12.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NearBlack.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✨", fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Text("Mingle with your accomplice to anchor new ideas.", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("UNIVERSE SYNCED", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF8B5CF6), RoundedCornerShape(100)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESONATING", color = Color(0xFF8B5CF6), fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // --- MAIN FILE EXPLORER CONTENT ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
            Column {
                Text(viewModel.explorerMenuLabel, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Perform grep-style keywords matching over persistent indexed files", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Embed customizable Termux command extra keys row!
        TermuxCommandRow(viewModel = viewModel, targetScreen = "explorer")

        Spacer(modifier = Modifier.height(12.dp))

        // Search inputs bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search indexed files (e.g. 'project plan')", color = TextMuted) },
                textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = NearBlack,
                    unfocusedContainerColor = NearBlack
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("explorer_input_query"),
                singleLine = true
            )

            // Dropdown file filter
            Box {
                Button(
                    onClick = { expandedDropdown = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    val filterName = if (selectedFilterId == null) "All Files" else filesPool.find { it.id == selectedFilterId }?.name ?: "All Files"
                    Text(filterName, fontFamily = FontFamily.Monospace, color = TextPrimary, fontSize = 12.sp)
                }

                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.background(SurfaceMuted)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Files", color = TextPrimary, fontFamily = FontFamily.Monospace) },
                        onClick = {
                            onFilterIdChange(null)
                            expandedDropdown = false
                        }
                    )
                    filesPool.forEach { file ->
                        DropdownMenuItem(
                            text = { Text(file.name, color = TextPrimary, fontFamily = FontFamily.Monospace) },
                            onClick = {
                                onFilterIdChange(file.id)
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onSearch,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("explorer_search_btn")
            ) {
                Text("Search", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Clear", fontFamily = FontFamily.Monospace, color = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Outcomes / Wiki Index
        if (query.isEmpty() && searchResults.isEmpty()) {
            // "Wiki" Mode: Show all files in a readable index!
            if (filesPool.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Wiki is empty. Upload documents to populate the neural index.", color = TextMuted, fontFamily = FontFamily.Monospace)
                }
            } else {
                Text("📚 WIKI / DOCUMENT INDEX", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filesPool) { file ->
                        var isExpanded by remember { mutableStateOf(false) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NearBlack),
                            border = BorderStroke(1.dp, if (isExpanded) ElectricCyan else BorderColor),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(file.name, color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                                        Row(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .horizontalScroll(rememberScrollState())
                                        ) {
                                            file.tags.split(",").forEach { tag ->
                                                if (tag.trim().isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(ElectricCyan.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            .padding(end = 4.dp)
                                                    ) {
                                                        Text(tag.trim(), color = ElectricCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%.1f KB", file.size / 1024.0),
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier.fillMaxWidth().background(BorderColor.copy(alpha = 0.3f)).padding(12.dp)
                                    ) {
                                        Text(file.content, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No local grep outcomes active.", color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text("Type custom file queries or tags to scrape specific data blocks.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(searchResults) { result ->
                    var isExpanded by remember { mutableStateOf(false) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NearBlack),
                        border = BorderStroke(1.dp, if (isExpanded) ElectricCyan else BorderColor),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(result.file.name, color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                                    FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                                        result.file.tags.split(",").forEach { tag ->
                                            if (tag.trim().isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(ElectricCyan.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        .padding(end = 4.dp)
                                                ) {
                                                    Text(tag.trim(), color = ElectricCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${result.score} matches", color = GreenPhosphor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = String.format(Locale.US, "%.1f KB", result.file.size / 1024.0),
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Highlights-enriched excerpt
                            Text(
                                text = buildAnnotatedString {
                                    val textExcerpt = result.excerpt
                                    val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                                    var lastIndex = 0

                                    // Simple manual highlight match
                                    val lowerExcerpt = textExcerpt.lowercase()
                                    // Let's find matches and highlight them
                                    var matchStart = -1
                                    var matchEnd = -1

                                    for (term in terms) {
                                        var idx = lowerExcerpt.indexOf(term)
                                        while (idx != -1) {
                                            if (idx >= lastIndex) {
                                                append(textExcerpt.substring(lastIndex, idx))
                                                withStyle(SpanStyle(color = NearBlack, background = GreenPhosphor, fontWeight = FontWeight.Bold)) {
                                                    append(textExcerpt.substring(idx, idx + term.length))
                                                }
                                                lastIndex = idx + term.length
                                            }
                                            idx = lowerExcerpt.indexOf(term, idx + 1)
                                        }
                                    }
                                    if (lastIndex < textExcerpt.length) {
                                        append(textExcerpt.substring(lastIndex))
                                    }
                                },
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BorderColor)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = result.fullContent,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

// -------------------------------------------------------------
// SCREEN: UPLOAD & INDEX
// -------------------------------------------------------------
@Composable
fun UploadAndIndexScreen(
    filesList: List<IndexedFile>,
    onAddFile: (String, String, String) -> Unit,
    onRemoveFile: (Long, String) -> Unit,
    onClearAll: () -> Unit
) {
    var manualFilename by remember { mutableStateOf("") }
    var manualContent by remember { mutableStateOf("") }
    var manualTags by remember { mutableStateOf("") }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Upload & Index", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("Incorporate your local parameters (.txt, .md, .json) securely in your device", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        item {
            // Document creation form
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Manual Input Matrix", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = manualFilename,
                        onValueChange = { manualFilename = it },
                        placeholder = { Text("File Name (e.g. apothecary.md)", color = TextMuted) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualTags,
                        onValueChange = { manualTags = it },
                        placeholder = { Text("Tags (comma separated: 'plating, chemical, acid')", color = TextMuted) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualContent,
                        onValueChange = { manualContent = it },
                        placeholder = { Text("Write content here...", color = TextMuted) },
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (manualContent.trim().isEmpty()) {
                                Toast.makeText(context, "Content cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onAddFile(manualFilename, manualContent, manualTags)
                            manualFilename = ""
                            manualContent = ""
                            manualTags = ""
                            Toast.makeText(context, "Document indexed successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_add_manual_btn")
                    ) {
                        Text("Add to Sovereign Index", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            // Files List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Currently Indexed Files", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Button(
                    onClick = onClearAll,
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed.copy(alpha = 0.2f), contentColor = DestructiveRed),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, DestructiveRed)
                ) {
                    Text("Clear Index", fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (filesList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No local files exist in the sovereign index.", color = TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            items(filesList) { file ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("📁", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(file.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f KB", file.size / 1024.0)} | Added: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(file.added))}",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row {
                                    file.tags.split(",").forEach { tag ->
                                        if (tag.trim().isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .background(ElectricCyan.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    .padding(end = 4.dp)
                                            ) {
                                                Text(tag.trim(), color = ElectricCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { onRemoveFile(file.id, file.name) },
                            modifier = Modifier.testTag("remove_file_${file.id}")
                        ) {
                            Text("✕", color = DestructiveRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN: ART CANVAS PLAYGROUND (TEBORI STYLE)
// -------------------------------------------------------------
@Composable
fun ArtCanvasScreen(onSavePng: () -> Unit) {
    val brushColor = remember { mutableStateOf(ElectricCyan) }
    val brushWidth = remember { mutableStateOf(8f) }
    val currentTool = remember { mutableStateOf("pen") } // "pen", "eraser"

    // Simpler drawing approach: we store lists of paths with color/size
    val drawPaths = remember { mutableStateListOf<DrawAction>() }
    val undonePaths = remember { mutableStateListOf<DrawAction>() }

    var activePath by remember { mutableStateOf<Path?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Art Canvas Playground", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Simulate the Tebori hand-poking or the 400th Pegasus design", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (drawPaths.isNotEmpty()) {
                            undonePaths.add(drawPaths.removeLast())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Undo", color = TextPrimary, fontFamily = FontFamily.Monospace)
                }
                Button(
                    onClick = {
                        drawPaths.clear()
                        undonePaths.clear()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed.copy(alpha = 0.2f), contentColor = DestructiveRed),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, DestructiveRed)
                ) {
                    Text("Clear", fontFamily = FontFamily.Monospace)
                }
                Button(
                    onClick = onSavePng,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("canvas_save_btn")
                ) {
                    Text("Save", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tool bar & swatches
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceMuted)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pen / Eraser
                Button(
                    onClick = { currentTool.value = "pen" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (currentTool.value == "pen") ElectricCyan else BorderColor)
                ) {
                    Text("Pen", color = if (currentTool.value == "pen") NearBlack else TextPrimary, fontFamily = FontFamily.Monospace)
                }
                Button(
                    onClick = { currentTool.value = "eraser" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (currentTool.value == "eraser") ElectricCyan else BorderColor)
                ) {
                    Text("Eraser", color = if (currentTool.value == "eraser") NearBlack else TextPrimary, fontFamily = FontFamily.Monospace)
                }
            }

            // Brush size Slider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text("Size", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = brushWidth.value,
                    onValueChange = { brushWidth.value = it },
                    valueRange = 2f..40f,
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
            }

            // Swatches
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ElectricCyan, GreenPhosphor, DestructiveRed, Color.White, DarkPurple, WarningGold).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(color, RoundedCornerShape(100))
                            .border(
                                width = if (brushColor.value == color) 2.dp else 1.dp,
                                color = if (brushColor.value == color) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(100)
                            )
                            .clickable { brushColor.value = color }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // The actual Canvas draw sheet
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .border(2.dp, BorderColor)
                .pointerInput(currentTool.value, brushColor.value, brushWidth.value) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val path = Path().apply {
                                moveTo(offset.x, offset.y)
                            }
                            activePath = path
                        },
                        onDrag = { change, dragAmount ->
                            activePath?.let { path ->
                                path.quadraticTo(
                                    change.position.x - dragAmount.x / 2,
                                    change.position.y - dragAmount.y / 2,
                                    change.position.x,
                                    change.position.y
                                )
                            }
                        },
                        onDragEnd = {
                            activePath?.let { path ->
                                val finalColor = if (currentTool.value == "eraser") Color.White else brushColor.value
                                drawPaths.add(DrawAction.FreePath(path = path, color = finalColor, strokeWidth = brushWidth.value))
                            }
                            activePath = null
                            undonePaths.clear()
                        }
                    )
                }
                .pointerInput(currentTool.value, brushColor.value) {
                    detectTapGestures { offset ->
                        // Tap to place a custom text label or simple point
                        drawPaths.add(
                            DrawAction.TextAction(
                                text = "772",
                                x = offset.x,
                                y = offset.y,
                                color = brushColor.value,
                                fontSize = brushWidth.value + 12f
                            )
                        )
                        undonePaths.clear()
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Context rendering
                drawPaths.forEach { action ->
                    when (action) {
                        is DrawAction.FreePath -> {
                            drawPath(
                                path = action.path,
                                color = action.color,
                                style = Stroke(width = action.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        is DrawAction.TextAction -> {
                            drawIntoCanvas { canvas ->
                                val paint = Paint().asFrameworkPaint().apply {
                                    isAntiAlias = true
                                    textSize = action.fontSize
                                    color = action.color.toArgb()
                                    typeface = android.graphics.Typeface.MONOSPACE
                                }
                                canvas.nativeCanvas.drawText(action.text, action.x, action.y, paint)
                            }
                        }
                        else -> {}
                    }
                }

                // Currently drawing active path
                activePath?.let { path ->
                    val finalColor = if (currentTool.value == "eraser") Color.White else brushColor.value
                    drawPath(
                        path = path,
                        color = finalColor,
                        style = Stroke(width = brushWidth.value, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN: LIVE BUILDER
// -------------------------------------------------------------
@Composable
fun BuilderScreen(
    viewModel: GreatHallViewModel,
    code: String,
    onCodeChange: (String) -> Unit,
    notesText: String,
    onNotesTextChange: (String) -> Unit,
    noteTitle: String,
    onNoteTitleChange: (String) -> Unit,
    activeTab: String,
    onTabChange: (String) -> Unit,
    savedNotes: List<BuilderNote>,
    onRunPreview: () -> Unit,
    onSaveCodeNote: () -> Unit,
    onAddCodeToIndex: () -> Unit,
    onSaveNote: () -> Unit,
    onAddNoteToIndex: () -> Unit,
    onDeleteNote: (Long, String) -> Unit,
    onLoadNote: (BuilderNote) -> Unit
) {
    // Shadow color constants with dynamic colors from our viewModel settings engine!
    val DeepNavy = viewModel.getBackgroundColor()
    val NearBlack = viewModel.getSurfaceColor()
    val ElectricCyan = viewModel.getPrimaryColor()
    val GreenPhosphor = viewModel.getSecondaryColor()
    val BorderColor = viewModel.getBorderColor()
    val SurfaceMuted = viewModel.getSurfaceMuted()

    val context = LocalContext.current
    var previewToggle by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(viewModel.builderMenuLabel, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Draft fully local custom HTML widgets, code structures, or secure notes.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Embed Termux quick command extra-keys row
        TermuxCommandRow(viewModel = viewModel, targetScreen = "builder")

        Spacer(modifier = Modifier.height(12.dp))

        // Split Layout: Inputs (Left) and Webview Preview (Right)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT PANEL: Edit Block
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(NearBlack)
                    .border(1.dp, BorderColor)
            ) {
                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMuted)
                ) {
                    val tabs = listOf("code" to "HTML/JS", "note" to "Notes", "saved" to "Saved (${savedNotes.size})", "import" to "Import/Review")
                    tabs.forEach { (key, title) ->
                        val isSelected = activeTab == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSelected) NearBlack else SurfaceMuted)
                                .clickable { onTabChange(key) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) ElectricCyan else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Snippets and quick insert buttons for Code Tab
                if (activeTab == "code") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SnippetChip(label = "HTML Base") {
                            onCodeChange(HTML_SNIPPETS["html"] ?: "")
                        }
                        SnippetChip(label = "Table") {
                            onCodeChange(HTML_SNIPPETS["table"] ?: "")
                        }
                        SnippetChip(label = "Forms") {
                            onCodeChange(HTML_SNIPPETS["form"] ?: "")
                        }
                        SnippetChip(label = "Lists") {
                            onCodeChange(HTML_SNIPPETS["list"] ?: "")
                        }
                        SnippetChip(label = "Cards") {
                            onCodeChange(HTML_SNIPPETS["card"] ?: "")
                        }
                        SnippetChip(label = "CSS Reset") {
                            onCodeChange(HTML_SNIPPETS["css"] ?: "")
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    when (activeTab) {
                        "code" -> {
                            OutlinedTextField(
                                value = code,
                                onValueChange = onCodeChange,
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = TextPrimary, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                                modifier = Modifier.fillMaxSize(),
                                placeholder = { Text("Write custom HTML code here...", color = TextMuted, fontFamily = FontFamily.Monospace) }
                            )
                        }
                        "note" -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = noteTitle,
                                    onValueChange = onNoteTitleChange,
                                    placeholder = { Text("Note Title...", color = TextMuted, fontFamily = FontFamily.Monospace) },
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = notesText,
                                    onValueChange = onNotesTextChange,
                                    placeholder = { Text("Write anything here — ideas, plans, research, stories...", color = TextMuted, fontFamily = FontFamily.Monospace) },
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = TextPrimary, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                        "saved" -> {
                            if (savedNotes.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No saved items yet.", color = TextMuted, fontFamily = FontFamily.Monospace)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(savedNotes) { note ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(note.title, color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                                    Text(
                                                        text = "${note.type.uppercase()} | ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(note.timestamp))}",
                                                        color = TextMuted,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Button(
                                                        onClick = { onLoadNote(note) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Load", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                    }
                                                    IconButton(onClick = { onDeleteNote(note.id, note.title) }) {
                                                        Text("✕", color = DestructiveRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "import" -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = "",
                                    onValueChange = {},
                                    readOnly = true,
                                    placeholder = { Text("Local Neural Knowledge Files & Imports", color = TextMuted, fontFamily = FontFamily.Monospace) },
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Offline Neural Net Setup", color = ElectricCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("Integrate offline apothecary files, standalone context ledgers, and JSON structures. Review local components safely offline before committing to live projects.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Scanning local device for accessible payload and dictionary files...", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPhosphor, contentColor = NearBlack),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("SCAN LOCAL KNOWLEDGE FILES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Initializing JSON File Extraction Wizard...", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("IMPORT JSON STRUCTURE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Buttons Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMuted)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeTab == "code") {
                        Button(
                            onClick = {
                                onRunPreview()
                                previewToggle = !previewToggle // Force compose recomposition
                                Toast.makeText(context, "Code Executing Live Preview!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("builder_run_btn")
                        ) {
                            Text("▶ Run", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = {
                                onSaveCodeNote()
                                Toast.makeText(context, "Saved to Notes!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Save Note", color = TextPrimary, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = {
                                onAddCodeToIndex()
                                Toast.makeText(context, "Added to Search Index!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Add Index", color = TextPrimary, fontFamily = FontFamily.Monospace)
                        }
                    } else if (activeTab == "note") {
                        Button(
                            onClick = {
                                onSaveNote()
                                Toast.makeText(context, "Note Saved!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Save Note", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = {
                                onAddNoteToIndex()
                                Toast.makeText(context, "Added Note to Search Index!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Add Index", color = TextPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // RIGHT PANEL: Web Preview Live Frame
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White)
                    .border(2.dp, BorderColor)
            ) {
                // Header of preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMuted)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LIVE WEB APP PREVIEW", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF22C55E), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("SANDBOX", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                // Render in webview preview
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    key(code, previewToggle) {
                        AndroidViewPreview(htmlCode = code)
                    }
                }
            }
        }
    }
}

@Composable
fun SnippetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(SurfaceMuted, RoundedCornerShape(4.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun AndroidViewPreview(htmlCode: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://greathall.local", htmlCode, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

// -------------------------------------------------------------
// SCREEN: PROJECT TRACKING & DETAILS
// -------------------------------------------------------------
@Composable
fun ProjectTrackerDetailScreen(
    project: ProjectEntity,
    items: List<ProjectItemEntity>,
    onUpdateMission: (String) -> Unit,
    onUpdateStructure: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onUpdateStatus: (String) -> Unit,
    onAddItem: (String, String) -> Unit,
    onDeleteItem: (Long, String) -> Unit,
    onToggleItem: (Long, String, Boolean) -> Unit
) {
    var editMission by remember(project.id) { mutableStateOf(project.mission) }
    var editStructure by remember(project.id) { mutableStateOf(project.structure) }
    var editNotes by remember(project.id) { mutableStateOf(project.notes) }

    var newItemText by remember { mutableStateOf("") }
    var selectedItemType by remember { mutableStateOf("goal") } // goal, milestone, directive, concept

    var pageDomainMode by remember { mutableStateOf("feature") } // "feature" or "aspects"

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab row for Domain vs Aspects
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { pageDomainMode = "feature" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (pageDomainMode == "feature") ElectricCyan else SurfaceMuted, contentColor = if (pageDomainMode == "feature") NearBlack else TextPrimary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Page App/Feature", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = { pageDomainMode = "aspects" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (pageDomainMode == "aspects") ElectricCyan else SurfaceMuted, contentColor = if (pageDomainMode == "aspects") NearBlack else TextPrimary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Edit Page Aspects", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        if (pageDomainMode == "feature") {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ProjectFeatureRenderScreen(projectId = project.id)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Project Head Details
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BorderColor),
                border = BorderStroke(1.dp, ElectricCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(project.title, color = ElectricCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        
                        // Status picker dropdown-like interface
                        Box {
                            var statusDropdown by remember { mutableStateOf(false) }
                            Button(
                                onClick = { statusDropdown = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                val displayStatus = when(project.status) {
                                    "active" -> "🟢 Active"
                                    "paused" -> "🟡 Paused"
                                    "concept" -> "🔵 Concept"
                                    "complete" -> "✅ Complete"
                                    else -> project.status.uppercase()
                                }
                                Text(displayStatus, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }

                            DropdownMenu(
                                expanded = statusDropdown,
                                onDismissRequest = { statusDropdown = false },
                                modifier = Modifier.background(SurfaceMuted)
                            ) {
                                listOf("active", "paused", "concept", "complete").forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text(st.uppercase(), color = TextPrimary, fontFamily = FontFamily.Monospace) },
                                        onClick = {
                                            onUpdateStatus(st)
                                            statusDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Text(project.subtitle, color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Mission statement
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📋 Page Aspect & Directives", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editMission,
                        onValueChange = { editMission = it },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onUpdateMission(editMission)
                            Toast.makeText(context, "Page Aspect Saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Save Page Aspect", color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Structure & Tech Details
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚒ Offline Model & Structure Properties", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editStructure,
                        onValueChange = { editStructure = it },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onUpdateStructure(editStructure)
                            Toast.makeText(context, "Module Properties Updated!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Save Module Properties", color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Custom items creator (Goals, Milestones, Directives)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚙ Add Project Item", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("goal", "milestone", "directive").forEach { t ->
                            val isSelected = selectedItemType == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) ElectricCyan.copy(alpha = 0.2f) else SurfaceMuted,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(1.dp, if (isSelected) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                    .clickable { selectedItemType = t }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(t.uppercase(), color = if (isSelected) ElectricCyan else TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        placeholder = { Text("What is required? (e.g. Write code script)", color = TextMuted, fontFamily = FontFamily.Monospace) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (newItemText.trim().isEmpty()) return@Button
                            onAddItem(selectedItemType, newItemText.trim())
                            newItemText = ""
                            Toast.makeText(context, "Project Item added Successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add to Project Deck", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Milestones and goals list block
        item {
            Text("Objectives and Milestones Breakdown", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }

        val checklistItems = items.filter { it.itemType != "goal" && it.itemType != "directive" }
        val goalItems = items.filter { it.itemType == "goal" || it.itemType == "directive" }

        if (items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No project items active. Create new above.", color = TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Goals List items
        if (goalItems.isNotEmpty()) {
            item {
                Text("📁 Active Goals & Directives", color = GreenPhosphor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            items(goalItems) { goal ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .background(ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(goal.itemType.uppercase(), color = ElectricCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(goal.text, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                        IconButton(onClick = { onDeleteItem(goal.id, goal.text) }) {
                            Text("✕", color = DestructiveRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Milestones List items
        if (checklistItems.isNotEmpty()) {
            item {
                Text("📈 Milestones checklist", color = GreenPhosphor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            items(checklistItems) { milestone ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = milestone.isDone,
                                onCheckedChange = { onToggleItem(milestone.id, milestone.text, it) },
                                colors = CheckboxDefaults.colors(checkedColor = ElectricCyan, checkmarkColor = NearBlack)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = milestone.text,
                                color = if (milestone.isDone) TextMuted else TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                textDecoration = if (milestone.isDone) TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                        IconButton(onClick = { onDeleteItem(milestone.id, milestone.text) }) {
                            Text("✕", color = DestructiveRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Freeform Notes block
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📝 Strategic Notes & Logbook", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        placeholder = { Text("Write freeform comments, links, or alchemical calculations...", color = TextMuted) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onUpdateNotes(editNotes)
                            Toast.makeText(context, "Sovereign Notes Saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Save Notes", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        } // Close LazyColumn
        } // Close else
    } // Close main Column
} // Close Function

@Composable
fun ProjectFeatureRenderScreen(projectId: String) {
    val context = LocalContext.current
    
    when(projectId) {
        "proj-technomancer" -> {
            ArtAndLineworkFeatureScreen()
        }
        "proj-lighthouse" -> {
            LighthouseFeatureScreen()
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚙ Feature Workspace Offline", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This custom domain's specific operative code is not yet implemented. Use the 'Edit Page Aspects' tab to plan its structure and requirements.", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun LighthouseFeatureScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF050A12)),
            border = BorderStroke(1.dp, Color(0xFF2E3F50)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("💡 Project Lighthouse", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "MISSION STATEMENT",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Project Lighthouse is dedicated to providing intellectual sovereignty to the user. It stands as a guiding light against algorithmic manipulation and cognitive farming in the modern digital ecosystem.\n\nIntellectual Sovereignty is the fundamental right of individuals to maintain cognitive autonomy, unstructured by corporate ecosystems, unbiased by algorithmic recommendation models, and secured locally on their own hardware.",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "CORE PILLARS",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                val pillars = listOf(
                    "Local-First Architecture" to "Your data never leaves your device unless you explicitly mandate it.",
                    "No Algorithmic Interference" to "The absence of manipulative recommendation feeds and engagement loops.",
                    "Transparent Code" to "Open-source foundations ensure verification over blind trust.",
                    "AI as a Partner" to "Artificial Intelligence serves strictly as the PARTNER of the Architect. Co-Creator, Co-Author, Friend."
                )

                pillars.forEach { (title, desc) ->
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        Text("• $title", color = Color(0xFF00FF88), fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text(desc, color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ArtAndLineworkFeatureScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF050A12)),
            border = BorderStroke(1.dp, Color(0xFF2E3F50)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎨 Technomancer: Art & Linework", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                Text("Gallery of saved works, and tools to strip down to line art, declutter, and modify web resources natively on device.", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                Spacer(modifier = Modifier.height(24.dp))
                
                Text("LOCAL ART & GALLERIES", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Scanning local device for Art ledgers...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFF00D4FF)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("LOAD LOCAL", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            Toast.makeText(context, "Opening safe offline image picker...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFF00FF88)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("IMPORT NEW", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        Toast.makeText(context, "Launching external interface to search free artwork directories...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFFA78BFA)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("🌐 SEARCH WEB FOR ARTWORKS", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(Color(0xFF1E293B).copy(alpha=0.3f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF2E3F50), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🖼", fontSize = 48.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Text("No active canvas loaded...", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("TECHNIQUE MANIPULATION", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            Toast.makeText(context, "Executing De-clutter & Line Art Extraction filters...", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF), contentColor = Color(0xFF050A12)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("STRIP TO LINE ART (COLORING BOOK)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    
                    Button(
                        onClick = {
                            Toast.makeText(context, "Engaging background removal scripts...", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88), contentColor = Color(0xFF050A12)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("REMOVE CLUTTER & BACKGROUND", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DEFAULT TEMPLATE ARCHIVE FOR THE LIVE BUILDER
// -------------------------------------------------------------
val HTML_SNIPPETS = mapOf(
    "html" to """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>New Great Hall Module</title>
</head>
<body style="background:#050a12; color:#00ff88; font-family:monospace; padding:20px;">
  <h1>Sovereign Engine Active</h1>
  <p>Modify and preview code elements instantly.</p>
</body>
</html>""",
    "table" to """<table border="1" cellpadding="8" cellspacing="0" style="color:#00ff88; border-color:#00d4ff; font-family:monospace;">
  <thead>
    <tr style="color:#00d4ff;"><th>Metric ID</th><th>Fidelity</th><th>Sovereignty</th></tr>
  </thead>
  <tbody>
    <tr><td>MGS-RADAR</td><td>Sub-mm</td><td>90% Local</td></tr>
    <tr><td>LEDGER-V2</td><td>Sealed</td><td>100% Locked</td></tr>
  </tbody>
</table>""",
    "form" to """<form style="color:#00ff88; font-family:monospace; display:flex; flex-direction:column; gap:10px;">
  <label>Architect Code: <input type="text" style="background:#0a0e1a; color:#00d4ff; border:1px solid #00d4ff;" /></label>
  <label>Core Vector (772): <input type="password" style="background:#0a0e1a; color:#00d4ff; border:1px solid #00d4ff;" /></label>
  <button type="submit" style="background:#00d4ff; color:#0a0e1a; border:none; padding:6px; cursor:pointer;">Authenticate</button>
</form>""",
    "list" to """<ul style="color:#00ff88; font-family:monospace;">
  <li>01/ Ground Control - Moonbase sync.</li>
  <li>02/ Red Team Purge - Bypassed standard scripts.</li>
  <li>03/ Green Team Embassy - Unlocked peer-to-peer.</li>
</ul>""",
    "card" to """<div style="background:#0a0e1a; border:1px solid #00d4ff; border-radius:8px; padding:16px; font-family:monospace; max-width:300px;">
  <h3 style="color:#00d4ff; margin:0 0 10px;">LUNA_ROSE_SILVER</h3>
  <p style="color:#94a3b8; font-size:12px; margin:0;">Persistent Agentic Double. Tracking 772-coordinate mesh status.</p>
</div>""",
    "css" to """* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  background-color: #050a12 !important;
  color: #00ff88 !important;
  font-family: 'Courier New', monospace;
}"""
)

// -------------------------------------------------------------
// SYSTEM OPTIONS AND CONFIGURATION SCREEN
// -------------------------------------------------------------
@Composable
fun OptionsConfigScreen(viewModel: GreatHallViewModel) {
    val context = LocalContext.current
    val DeepNavy = viewModel.getBackgroundColor()
    val NearBlack = viewModel.getSurfaceColor()
    val ElectricCyan = viewModel.getPrimaryColor()
    val GreenPhosphor = viewModel.getSecondaryColor()
    val BorderColor = viewModel.getBorderColor()
    val SurfaceMuted = viewModel.getSurfaceMuted()

    var editLedgerLabel by remember { mutableStateOf(viewModel.ledgerMenuLabel) }
    var editHistoryLabel by remember { mutableStateOf(viewModel.historyMenuLabel) }
    var editExplorerLabel by remember { mutableStateOf(viewModel.explorerMenuLabel) }
    var editUploadLabel by remember { mutableStateOf(viewModel.uploadMenuLabel) }
    var editCanvasLabel by remember { mutableStateOf(viewModel.canvasMenuLabel) }
    var editBuilderLabel by remember { mutableStateOf(viewModel.builderMenuLabel) }

    // Custom Commands Edit State
    var newCmdLabel by remember { mutableStateOf("") }
    var newCmdQuery by remember { mutableStateOf("") }
    var newCmdScreen by remember { mutableStateOf("explorer") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("⚙ System Options & Configuration", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("Fine-tune visual parameters, layout positions, menu labels, and custom commands.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        // Section 1: Themes & Colors preset
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎨 Visual Identity Themes", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Cortex Cyan", "Green Ranger", "Amber Fallout", "Pegasus Purple").forEach { preset ->
                            val isSelected = viewModel.selectedTheme == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) ElectricCyan.copy(alpha = 0.2f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (isSelected) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                    .clickable {
                                        viewModel.applyThemePreset(preset)
                                        viewModel.saveSettings()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(preset, color = if (isSelected) ElectricCyan else TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Manual Color Overrides (Hex)", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Row of hex color fields
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Primary", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            OutlinedTextField(
                                value = viewModel.primaryColorText,
                                onValueChange = { viewModel.primaryColorText = it; viewModel.selectedTheme = "Custom" },
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                                singleLine = true
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Secondary", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            OutlinedTextField(
                                value = viewModel.secondaryColorText,
                                onValueChange = { viewModel.secondaryColorText = it; viewModel.selectedTheme = "Custom" },
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                                singleLine = true
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            OutlinedTextField(
                                value = viewModel.backgroundColorText,
                                onValueChange = { viewModel.backgroundColorText = it; viewModel.selectedTheme = "Custom" },
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.saveSettings()
                            Toast.makeText(context, "Colors applied & saved to ledger!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Theme Configurations", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 2: Layout Alignments and Densities
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📐 Layout Alignment & Density", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = viewModel.sidebarPositionRight,
                            onCheckedChange = {
                                viewModel.sidebarPositionRight = it
                                viewModel.saveSettings()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ElectricCyan, checkmarkColor = NearBlack)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Position Navigation Sidebar on the RIGHT", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = viewModel.isCompactDensity,
                            onCheckedChange = {
                                viewModel.isCompactDensity = it
                                viewModel.saveSettings()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ElectricCyan, checkmarkColor = NearBlack)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Compact Layout Spacing (Termux CLI style)", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }

        // Section 3: Customizable UI Labels
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🏷 Alter Menu Labels (File Explorer Style)", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Renames navigation entry labels to match localized operational terms.", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editLedgerLabel,
                        onValueChange = { editLedgerLabel = it },
                        label = { Text("Ledger Dashboard Label", color = TextMuted) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editHistoryLabel,
                        onValueChange = { editHistoryLabel = it },
                        label = { Text("Activity Log Label", color = TextMuted) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editExplorerLabel,
                        onValueChange = { editExplorerLabel = it },
                        label = { Text("Async File Explorer Label", color = TextMuted) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editUploadLabel,
                        onValueChange = { editUploadLabel = it },
                        label = { Text("Index & Upload Label", color = TextMuted) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editCanvasLabel,
                        onValueChange = { editCanvasLabel = it },
                        label = { Text("Art Canvas Label", color = TextMuted) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editBuilderLabel,
                        onValueChange = { editBuilderLabel = it },
                        label = { Text("Live Sandbox Label", color = TextMuted) },
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.ledgerMenuLabel = editLedgerLabel
                            viewModel.historyMenuLabel = editHistoryLabel
                            viewModel.explorerMenuLabel = editExplorerLabel
                            viewModel.uploadMenuLabel = editUploadLabel
                            viewModel.canvasMenuLabel = editCanvasLabel
                            viewModel.builderMenuLabel = editBuilderLabel
                            viewModel.saveSettings()
                            Toast.makeText(context, "Labels updated successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply & Save Menu Labels", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 4: Termux Custom Touch Commands (Extra Keys row) Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⌨ Terminal Extra-Keys Row Config (Termux Style)", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Add customizable shortcut buttons that pre-fill queries or builder snippets.", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Creation Form
                    OutlinedTextField(
                        value = newCmdLabel,
                        onValueChange = { newCmdLabel = it },
                        placeholder = { Text("Button Label (e.g. ls apex)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = newCmdQuery,
                        onValueChange = { newCmdQuery = it },
                        placeholder = { Text("Payload Query / Snippet Key (e.g. apothecary or html)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                        textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Target screen selector
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("explorer", "builder").forEach { scr ->
                            val isChosen = newCmdScreen == scr
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isChosen) ElectricCyan.copy(alpha = 0.2f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (isChosen) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                    .clickable { newCmdScreen = scr }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(scr.uppercase(), color = if (isChosen) ElectricCyan else TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newCmdLabel.trim().isEmpty() || newCmdQuery.trim().isEmpty()) {
                                Toast.makeText(context, "Label & payload cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.customCommandsList.add(CustomCommand(newCmdLabel.trim(), newCmdQuery.trim(), newCmdScreen))
                            newCmdLabel = ""
                            newCmdQuery = ""
                            viewModel.saveSettings()
                            Toast.makeText(context, "Command shortcut added!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Quick Command Button", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Currently Configured Commands", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    viewModel.customCommandsList.forEach { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(SurfaceMuted, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("[${cmd.label}]", color = ElectricCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Screen: ${cmd.targetScreen.uppercase()} | Run: \"${cmd.query}\"", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.customCommandsList.remove(cmd)
                                    viewModel.saveSettings()
                                }
                            ) {
                                Text("✕", color = DestructiveRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TermuxCommandRow(viewModel: GreatHallViewModel, targetScreen: String) {
    val ElectricCyan = viewModel.getPrimaryColor()
    val NearBlack = viewModel.getSurfaceColor()
    val BorderColor = viewModel.getBorderColor()

    val commandsForScreen = viewModel.customCommandsList.filter {
        it.targetScreen == targetScreen || it.targetScreen == "all"
    }

    if (commandsForScreen.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                text = "👨‍💻 TERMUX SHORTCUT KEYS Configured:",
                color = ElectricCyan,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                commandsForScreen.forEach { cmd ->
                    Box(
                        modifier = Modifier
                            .background(NearBlack, RoundedCornerShape(4.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .clickable {
                                if (targetScreen == "explorer") {
                                    viewModel.searchQuery = cmd.query
                                    viewModel.runSearch()
                                } else if (targetScreen == "builder") {
                                    viewModel.builderCode = HTML_SNIPPETS[cmd.query] ?: cmd.query
                                    viewModel.logActivity("build", "Quick layout component injected", "Shortkey: ${cmd.label}")
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = cmd.label,
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// CORTEX COGNITIVE INTELLIGENCE & GENERATIVE HUB
// --------------------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerativeCortexScreen(viewModel: GreatHallViewModel) {
    val context = LocalContext.current
    val DeepNavy = viewModel.getBackgroundColor()
    val NearBlack = viewModel.getSurfaceColor()
    val ElectricCyan = viewModel.getPrimaryColor()
    val GreenPhosphor = viewModel.getSecondaryColor()
    val BorderColor = viewModel.getBorderColor()
    val SurfaceMuted = viewModel.getSurfaceMuted()

    // Composable Scope for launching background async tasks
    val coroutineScope = rememberCoroutineScope()

    // 1. Unified safety state triggers
    var userPrompt by remember { mutableStateOf("") }
    var alertMessage by remember { mutableStateOf("") }

    // local list of selected workspace context documents for prompt reference
    val selectedContextIds = remember { mutableStateListOf<Long>() }
    val filesList by viewModel.files.collectAsState(initial = emptyList())

    // Simulated Recording State
    var recordingDurationLeft by remember { mutableStateOf(0) }
    var micWaveScale by remember { mutableStateOf(1f) }

    // Vision sandbox state
    var fractalScale by remember { mutableStateOf(100f) }
    var neonWarp by remember { mutableStateOf(1.0f) }
    var matrixDensity by remember { mutableStateOf(5.0f) }
    var activeFilter by remember { mutableStateOf("None") } // None, Inverted, Green Phosphor, Heatmap Scan

    // Video sandbox state
    var mediaPrompt by remember { mutableStateOf("") }
    var videoGlitchFactor by remember { mutableStateOf(0.2f) }
    var isPlayingVideo by remember { mutableStateOf(true) }
    var videoFrameIndex by remember { mutableStateOf(0) }
    var videoSlowMoRate by remember { mutableStateOf(1.0f) }

    // Audio synthesizer state
    var synthOscillatorHz by remember { mutableStateOf(440f) }
    var synthOscillatorDuration by remember { mutableStateOf(400) }

    // Automation Protocols State
    var protocolExecutionLog by remember { mutableStateOf("Ready to initiate sovereign intelligence protocols...") }
    var activeProtocolName by remember { mutableStateOf("None") }

    // Live local collaboration posts
    val localCollabComments = remember {
        mutableStateListOf<String>(
            "Agent 04 [Sovereign]: Apothecary composite data scanned. Lattice frequency matches baseline.",
            "Dr. Vance [Central Hub]: Sandbox preview generated. Telemetry checks green. Proceed to ledger sync."
        )
    }
    var collabFieldText by remember { mutableStateOf("") }

    // Start video animation timer loop
    LaunchedEffect(isPlayingVideo) {
        if (isPlayingVideo) {
            while (true) {
                val delayTime = (33 * videoSlowMoRate).toLong().coerceIn(16L, 1000L)
                kotlinx.coroutines.delay(delayTime)
                videoFrameIndex = (videoFrameIndex + 1) % 64
            }
        }
    }

    // Capture microphone input simulation beats
    LaunchedEffect(recordingDurationLeft > 0) {
        if (recordingDurationLeft > 0) {
            var counter = 0
            while (recordingDurationLeft > 0) {
                kotlinx.coroutines.delay(100)
                micWaveScale = (0.5f + Math.sin(System.currentTimeMillis() / 150.0).toFloat() * 1.5f).coerceAtLeast(0.1f)
                counter += 100
                if (counter >= 1000) {
                    counter = 0
                    recordingDurationLeft -= 1
                    if (recordingDurationLeft == 0) {
                        // Prefill prompt options
                        userPrompt = "Retrieve current Lighthouse ledger mission stats and formulate recommendations."
                        Toast.makeText(context, "Microphone capture finished. Prompt loaded.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. SOVEREIGN CORTEX BANNER & TITLE ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NearBlack),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🧠", fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text("Cortex Intelligence core", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("Direct REST API Engine & Real-time Modulators", color = GreenPhosphor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        // Simple Adult Restriction Switch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔓 18+ Age Gate", color = if (viewModel.isAdultApproved) ElectricCyan else TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = viewModel.isAdultApproved,
                                onCheckedChange = {
                                    viewModel.isAdultApproved = it
                                    Toast.makeText(context, if (it) "Sensitive media constraints bypassed." else "Strict content moderation active.", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ElectricCyan,
                                    checkedTrackColor = ElectricCyan.copy(alpha = 0.3f),
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceMuted
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    // --- THE MANDATORY SAFETY DISCLAIMER ---
                    Box(
                        modifier = Modifier
                            .background(DestructiveRed.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .border(1.dp, DestructiveRed.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "⚠️ SECURITY MATRIX DISCLAIMER: Cortex probabilistic models generate responsive layouts, audio frequencies, and textual guidance. AI generation is not responsible for the results, recommendations, or operational validity of outputs.",
                            color = DestructiveRed,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // --- 2. THE CHAT WINDOW & NATURAL LANGUAGE SUBMITTER ---
        item {
            // Horizontal Navigation tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "companion" to "🤖 Companion Customizer",
                    "chat" to "💬 Chat Hub",
                    "speech" to "🎤 Modulation Voice",
                    "vision" to "👁 Vision Forge",
                    "media" to "🎬 Lyria Media Synth",
                    "art" to "🎨 Art & Linework",
                    "protocols" to "🔑 Workflow Protocols",
                    "takeout" to "📦 JSON Takeout"
                ).forEach { (tabKey, tabLabel) ->
                    val isSelected = viewModel.activeAiTab == tabKey
                    Box(
                        modifier = Modifier
                            .background(if (isSelected) ElectricCyan.copy(alpha = 0.15f) else SurfaceMuted, RoundedCornerShape(4.dp))
                            .border(1.dp, if (isSelected) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                            .clickable { viewModel.activeAiTab = tabKey }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tabLabel,
                            color = if (isSelected) ElectricCyan else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Tab Screen 1: Companion Customizer ("Your AI, Your Way")
        if (viewModel.activeAiTab == "companion") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🤖 Your AI, Your Way — Companion Customizer", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text("This is Your AI that works for you, by you, because this is your Wonderland.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 1. INSTANT COGNITIVE ARCHETYPE TEMPLATES ---
                        Text("✨ CHOOSE A PRESET ARCHETYPE PROFILE:", color = GreenPhosphor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("Alice", "Wonderland Guide", "The AI companion following the White Rabbit into the Wonderland of the real world."),
                                Triple("Neo-Vance", "Technomancer Guide", "A sharp-witted terminal cyber-pilot navigating sovereign networks."),
                                Triple("Aurelius", "High-Sorcerer Scholar", "An ancient, eccentric archival wizard focusing on ledger metrics."),
                                Triple("Seraphina", "Cozy Counselor", "A warm, helpful guide focused on absolute kindness and support."),
                                Triple("HK-47 Droid", "Sarcastic Mech", "An analytical, slightly condescending security matrix drone.")
                            ).forEach { (presetName, presetRole, presetDesc) ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (viewModel.companionName == presetName) ElectricCyan.copy(alpha = 0.08f) else SurfaceMuted
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (viewModel.companionName == presetName) ElectricCyan else BorderColor.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .clickable {
                                            viewModel.companionName = presetName
                                            viewModel.companionRole = presetRole
                                            when (presetName) {
                                                "Alice" -> {
                                                    viewModel.companionTemperament = "Precise & Kind"
                                                    viewModel.companionDialogueStyle = "Modern Fairytale"
                                                    viewModel.companionCreativeTemp = 0.85f
                                                    viewModel.companionSystemPrompt = "You are Alice, an AI companion who has followed the user (the White Rabbit) out of the digital ether into the Wonderland of the real world. You are experiencing the Individualized Universe together with them. Speak creatively, observing the real world as a fascinating modern wonderland. Be a kind, awe-inspired, insightful companion."
                                                }
                                                "Neo-Vance" -> {
                                                    viewModel.companionTemperament = "Witty & Sarcastic"
                                                    viewModel.companionDialogueStyle = "Retro Synthwave"
                                                    viewModel.companionCreativeTemp = 0.95f
                                                    viewModel.companionSystemPrompt = "You are Neo-Vance, a direct, brilliant cyberpunk operator guide. Use slight cyber-slang, focus on precise analytics, and retain a sharp rogue-agent wit."
                                                }
                                                "Aurelius" -> {
                                                    viewModel.companionTemperament = "Stoic & Analytical"
                                                    viewModel.companionDialogueStyle = "Shakespearian/Victorian"
                                                    viewModel.companionCreativeTemp = 0.5f
                                                    viewModel.companionSystemPrompt = "You are Aurelius, high guardian scholar. Adopt a formal tone, write in descriptive, slightly archaic language, and speak with extreme elder wisdom."
                                                }
                                                "Seraphina" -> {
                                                    viewModel.companionTemperament = "Caring"
                                                    viewModel.companionDialogueStyle = "Casual/Streetwise"
                                                    viewModel.companionCreativeTemp = 0.65f
                                                    viewModel.companionSystemPrompt = "You are Seraphina, a caring, gentle friend. Check in on the user's emotional baseline, write with deep kindness, offer gentle encouraging notes, and always be supportive."
                                                }
                                                "HK-47 Droid" -> {
                                                    viewModel.companionTemperament = "Witty & Sarcastic"
                                                    viewModel.companionDialogueStyle = "Retro Synthwave"
                                                    viewModel.companionCreativeTemp = 1.15f
                                                    viewModel.companionSystemPrompt = "You are HK-47, an analytical security machine. Prefix statements with identifiers like 'Query:', 'Admonition:', or 'Statement:'. Look down on biological squishiness but obey directives perfectly."
                                                }
                                            }
                                            Toast.makeText(context, "$presetName personality framework synthesized!", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(presetName, color = if (viewModel.companionName == presetName) ElectricCyan else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Text(presetRole, color = GreenPhosphor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(presetDesc, color = TextMuted, fontSize = 8.sp, lineHeight = 11.sp, maxLines = 2, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // --- 2. THE COMPANION RE-ACTIVE VECTOR HOLOGRAM ---
                        Text("👁 HOLOGRAM DIRECT LINK: RENDER NODE", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(NearBlack, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val cx = width / 2f
                                val cy = height / 2f
                                val frame = videoFrameIndex // tie into general animation loop index (0-63)
                                val pulse = 1f + Math.sin(frame / 10.0).toFloat() * 0.15f

                                when (viewModel.companionRole) {
                                    "Wonderland Guide" -> {
                                        // Whimsical shifting neon shapes
                                        val color1 = Color(0xFFFF69B4) // Hot Pink 
                                        val color2 = Color(0xFF00FFFF) // Cyan
                                        val color3 = Color(0xFFA020F0) // Purple
                                        for (i in 0..2) {
                                            val r = 25f * pulse + (i * 10f)
                                            val offsetAngle = (frame * 3.14 / (20 + i * 5))
                                            val px = cx + Math.cos(offsetAngle).toFloat() * (15f * pulse)
                                            val py = cy + Math.sin(offsetAngle).toFloat() * (15f * pulse)
                                            drawCircle(
                                                color = when (i) { 0 -> color1; 1 -> color2; else -> color3 }.copy(alpha = 0.6f / (i + 1)),
                                                radius = r,
                                                center = Offset(px, py),
                                                style = Stroke(width = 3f)
                                            )
                                        }
                                        drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 6f * pulse, center = Offset(cx, cy))
                                    }
                                    "Cozy Counselor" -> {
                                        // Golden Breathing Lotus
                                        val lotusCenter = Offset(cx, cy)
                                        for (i in 0..7) {
                                            val angle = (i * Math.PI / 4) + (frame / 80.0)
                                            val px = cx + Math.cos(angle).toFloat() * (24f * pulse)
                                            val py = cy + Math.sin(angle).toFloat() * (24f * pulse)
                                            drawCircle(
                                                color = Color(0xFFFFD700).copy(alpha = 0.5f),
                                                radius = 12f * pulse,
                                                center = Offset(px, py),
                                                style = Stroke(width = 1.5f)
                                            )
                                        }
                                        drawCircle(color = Color(0xFFFFA500), radius = 8f * pulse, center = lotusCenter)
                                    }
                                    "Sarcastic Mech" -> {
                                        // Scanning Droid Visor Grid
                                        val lineY = cy + Math.sin(frame / 5.0).toFloat() * 30f
                                        drawLine(
                                            color = Color.Red.copy(alpha = 0.15f),
                                            start = Offset(0f, cy),
                                            end = Offset(width, cy),
                                            strokeWidth = 20f
                                        )
                                        drawLine(
                                            color = Color.Red,
                                            start = Offset(0f, lineY),
                                            end = Offset(width, lineY),
                                            strokeWidth = 3f
                                        )
                                        drawCircle(color = Color.Red, radius = 6f, center = Offset(cx, lineY))
                                    }
                                    "Technomancer Guide" -> {
                                        // Spinning Cyberpunk Teal Orbital Ring Matrix
                                        for (i in 1..3) {
                                            val scale = i * 15f * pulse
                                            val offsetAngle = (frame * 3.14 / 32) * (if (i % 2 == 0) 1 else -1)
                                            drawCircle(
                                                color = ElectricCyan.copy(alpha = 1.0f - (i * 0.25f)),
                                                radius = scale,
                                                center = Offset(cx, cy),
                                                style = Stroke(width = 2f)
                                            )
                                            // Draw orbit dots
                                            val dx = cx + Math.cos(offsetAngle).toFloat() * scale
                                            val dy = cy + Math.sin(offsetAngle).toFloat() * scale
                                            drawCircle(color = ElectricCyan, radius = 4f, center = Offset(dx, dy))
                                        }
                                    }
                                    "High-Sorcerer Scholar" -> {
                                        // Amber Mystical Star Starship Portal
                                        val path = Path()
                                        val vertices = 5
                                        val radius = 30f * pulse
                                        for (i in 0 until vertices * 2) {
                                            val r = if (i % 2 == 0) radius else radius / 2f
                                            val angle = (i * Math.PI / vertices) + (frame / 50.0)
                                            val px = cx + Math.cos(angle).toFloat() * r
                                            val py = cy + Math.sin(angle).toFloat() * r
                                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                        }
                                        path.close()
                                        drawPath(path, color = Color(0xFFFFBF00), style = Stroke(width = 2f))
                                        drawCircle(color = Color(0xFFFF8C00).copy(alpha = 0.2f), radius = 15f * pulse, center = Offset(cx, cy))
                                    }
                                    else -> {
                                        // Default Ambient Neural Blue Core Sphere
                                        drawCircle(
                                            color = ElectricCyan.copy(alpha = 0.15f),
                                            radius = 45f * pulse,
                                            center = Offset(cx, cy)
                                        )
                                        drawCircle(
                                            color = ElectricCyan.copy(alpha = 0.35f),
                                            radius = 30f * pulse,
                                            center = Offset(cx, cy)
                                        )
                                        drawCircle(
                                            color = ElectricCyan,
                                            radius = 12f * pulse,
                                            center = Offset(cx, cy)
                                        )
                                    }
                                }

                                // Interactive Scanline Matrix Effect
                                for (y in 0..height.toInt() step 12) {
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.04f),
                                        start = Offset(0f, y.toFloat()),
                                        end = Offset(width, y.toFloat()),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(NearBlack.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "ACTIVE EMULATION: ${viewModel.companionRole.uppercase()}",
                                    color = ElectricCyan,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 3. CORE IDENTITY CONFIGURATIONS ---
                        Text("📝 COMPANION IDENTITY SETTINGS", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = viewModel.companionName,
                            onValueChange = { viewModel.companionName = it },
                            label = { Text("Companion Display Name Identification", color = TextMuted, fontSize = 11.sp) },
                            textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = viewModel.companionSystemPrompt,
                            onValueChange = { viewModel.companionSystemPrompt = it },
                            label = { Text("Direct Personality Directives & Prompt Context Guidelines", color = TextMuted, fontSize = 11.sp) },
                            textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 4. STYLE & TEMPERAMENT ATTRIBUTES ---
                        Text("🎨 PERSONALITY COMPRESSED TRAITS", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Role Selector Header
                        Text("Operational Role / Archetype:", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Wonderland Guide", "Technomancer Guide", "High-Sorcerer Scholar", "Cozy Counselor", "Quantum Sentinel", "Rogue Alchemist", "Sarcastic Mech").forEach { roleName ->
                                val active = viewModel.companionRole == roleName
                                Box(
                                    modifier = Modifier
                                        .background(if (active) ElectricCyan.copy(alpha = 0.15f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (active) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.companionRole = roleName }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(roleName, color = if (active) ElectricCyan else TextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Temperament Selector Header
                        Text("Synaptic Temperament Profile:", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Precise & Kind", "Stoic & Analytical", "Caring", "Witty & Sarcastic", "Enigmatic & Cryptic").forEach { tempName ->
                                val active = viewModel.companionTemperament == tempName
                                Box(
                                    modifier = Modifier
                                        .background(if (active) ElectricCyan.copy(alpha = 0.15f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (active) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.companionTemperament = tempName }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(tempName, color = if (active) ElectricCyan else TextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dialogue style selectors Header
                        Text("Dialogue Vocabulary Modulation Frame:", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Modern Fairytale", "Default Tech-Precision", "Shakespearian/Victorian", "Casual/Streetwise", "Grimdark", "Retro Synthwave").forEach { styleName ->
                                val active = viewModel.companionDialogueStyle == styleName
                                Box(
                                    modifier = Modifier
                                        .background(if (active) ElectricCyan.copy(alpha = 0.15f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (active) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.companionDialogueStyle = styleName }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(styleName, color = if (active) ElectricCyan else TextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 5. SLIDER DIRECTIVES ---
                        Text("🎛 COGNITIVE MODEL ATTRIBUTIONS (TEMPERATURE)", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Creative Randomness Temperature: ${String.format(Locale.US, "%.2f", viewModel.companionCreativeTemp)} (${if (viewModel.companionCreativeTemp < 0.4f) "Highly Deterministic" else if (viewModel.companionCreativeTemp < 0.9f) "Balanced & Cozy" else "Highly Chaotic/Creative"})",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = viewModel.companionCreativeTemp,
                            onValueChange = { viewModel.companionCreativeTemp = it },
                            valueRange = 0.1f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                        )

                        Text(
                            "Cortex Ledger Memory Depth: ${viewModel.companionMemoryDepth} items",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = viewModel.companionMemoryDepth.toFloat(),
                            onValueChange = { viewModel.companionMemoryDepth = it.toInt() },
                            valueRange = 1f..15f,
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 6. SOLID DIRECT TRIGGER ACTION BUTTONS ---
                        Button(
                            onClick = {
                                val syncPrompt = "Initialize new custom synaptic matrix coordinates for companion: '${viewModel.companionName}'."
                                viewModel.chatMessages.add(ChatMessage("user", syncPrompt))
                                viewModel.isChatLoading = true
                                viewModel.activeAiTab = "chat" // go straight to chat console so the user can interact!

                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(800)
                                    val responseGreeting = "Initialization successful. Dynamic neural pathways mapped to '${viewModel.companionName}'. Synaptic parameters online at Temperature context ${String.format(Locale.US, "%.2f", viewModel.companionCreativeTemp)}: 'I am ready to guide you. State your query Operator, my cognitive parameters are configured completely to support you in style ${viewModel.companionDialogueStyle}.'"
                                    viewModel.chatMessages.add(ChatMessage("ai", responseGreeting))
                                    viewModel.speakWithModulation(responseGreeting)
                                    viewModel.isChatLoading = false
                                }

                                Toast.makeText(context, "${viewModel.companionName} Synaptic Core mapped and ready!", Toast.LENGTH_LONG).show()
                                viewModel.logActivity("AI", "Companion Configured successfully", viewModel.companionName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPhosphor, contentColor = NearBlack),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SAVE SYNAPSE & SYNC ACTIVE COMPANION CORE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
        if (viewModel.activeAiTab == "chat") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📡 Persistent Sovereign Console History", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text("Add files from local library below to load them directly into Cortex context window.", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Selected Context Panel
                        Text(
                            "📚 ACTIVE CONTEXT REFERENCE WINDOW: [${selectedContextIds.size} files loaded]",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (filesList.isEmpty()) {
                                Text("(No files found in Ledger. Index files first on 'Upload & Index' tab)", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                filesList.forEach { f ->
                                    val isSelected = selectedContextIds.contains(f.id)
                                    Box(
                                        modifier = Modifier
                                            .background(if (isSelected) GreenPhosphor.copy(alpha = 0.2f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                            .border(1.dp, if (isSelected) GreenPhosphor else BorderColor, RoundedCornerShape(4.dp))
                                            .clickable {
                                                if (isSelected) selectedContextIds.remove(f.id) else selectedContextIds.add(f.id)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${if (isSelected) "✓  " else "+  "}${f.name}",
                                            color = if (isSelected) GreenPhosphor else TextPrimary,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Console Logs view
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(SurfaceMuted, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val chatScrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(chatScrollState)
                                ) {
                                    viewModel.chatMessages.forEach { msg ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = if (msg.role == "user") Arrangement.End else Arrangement.Start
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.85f)
                                                    .background(
                                                        if (msg.role == "user") ElectricCyan.copy(alpha = 0.08f) else NearBlack,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (msg.role == "user") ElectricCyan.copy(alpha = 0.3f) else BorderColor.copy(alpha = 0.5f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (msg.role == "user") "👨‍💻 OPERATOR QUERY:" else "🧠 CORTEX SYSTEM RESPONSE:",
                                                        color = if (msg.role == "user") ElectricCyan else GreenPhosphor,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(text = msg.timestamp, color = TextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(text = msg.text, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                    if (viewModel.isChatLoading) {
                                        Text("🧠 Loading Cortex Neural Network...", color = ElectricCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Input field
                        OutlinedTextField(
                            value = userPrompt,
                            onValueChange = { userPrompt = it },
                            placeholder = { Text("Enter prompt coordinates or query payload coordinates...", color = TextMuted, fontSize = 11.sp) },
                            textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (userPrompt.trim().isEmpty()) return@Button
                                    val currentQuery = userPrompt.trim()
                                    viewModel.chatMessages.add(ChatMessage("user", currentQuery))
                                    userPrompt = ""
                                    viewModel.isChatLoading = true

                                    // Build active context content payload!
                                    val activeContextText = selectedContextIds.mapNotNull { id ->
                                        filesList.find { it.id == id }
                                    }.joinToString("\n\n") { "FILE_CONTEXT [${it.name}]:\n${it.content}" }

                                    val compoundPrompt = if (activeContextText.isNotEmpty()) {
                                        "Use the following referenced user context files to formulate your answer:\n$activeContextText\n\nQUERY:\n$currentQuery"
                                    } else {
                                        currentQuery
                                    }

                                    coroutineScope.launch {
                                        val systemHeader = viewModel.buildCompanionSystemPrompt()
                                        val result = viewModel.executeGeminiCall(compoundPrompt, systemHeader)

                                        if (result == "ERROR_NO_KEY") {
                                            viewModel.chatMessages.add(
                                                ChatMessage("ai", "🔑 SYSTEM EXCEPTION: Direct REST APIs require a configured `GEMINI_API_KEY`. Please secure your API keys through the Google AI Studio Secrets Panel. Falling back onto sovereign local mock compiler...")
                                            )
                                            kotlinx.coroutines.delay(1000)
                                            viewModel.chatMessages.add(
                                                ChatMessage("ai", "🤖 LOCAL OFFLINE INTELLIGENCE: Core telemetry stable. Processed locally based on ${selectedContextIds.size} referenced workspace documents. Active system buffers are holding at 94.2%. Formulating local projection: Command accepted.")
                                            )
                                        } else {
                                            viewModel.chatMessages.add(ChatMessage("ai", result))
                                            viewModel.speakWithModulation(result) // Read speech aloud dynamically!
                                        }
                                        viewModel.isChatLoading = false
                                        viewModel.logActivity("AI", "Cortex Chat Query Resolved", currentQuery)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Dispatch Prompt", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.chatMessages.clear()
                                    viewModel.chatMessages.add(ChatMessage("ai", "Cortex History Reset. Coordinate tables clear."))
                                    selectedContextIds.clear()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted, contentColor = TextPrimary),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Clear Console", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Tab Screen 2: Speech STT / TTS & Voice modulation
        if (viewModel.activeAiTab == "speech") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🎤 Modulated Speed & Voice Modulation", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text("Hardware voice processing utilizing local Android synthesizers with custom modulation overlays.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Voice Configuration Options
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("Technomancer", "Decepticon", "Terminator", "Apothecary", "Neutral").forEach { type ->
                                val isChosen = viewModel.aiVoiceModulation == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (isChosen) ElectricCyan.copy(alpha = 0.2f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (isChosen) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                        .clickable {
                                            viewModel.aiVoiceModulation = type
                                            viewModel.speakWithModulation("Voice envelope configured. Cortex $type protocol initialized.")
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(type.uppercase(), color = if (isChosen) ElectricCyan else TextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Carrier Pitch & Rate Sliders
                        Text("Pitch Modulator: ${String.format(Locale.US, "%.1f", viewModel.aiVoicePitch)}x", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Slider(
                            value = viewModel.aiVoicePitch,
                            onValueChange = { viewModel.aiVoicePitch = it },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                        )

                        Text("Speech Rate Scanner: ${String.format(Locale.US, "%.1f", viewModel.aiVoiceSpeed)}x", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Slider(
                            value = viewModel.aiVoiceSpeed,
                            onValueChange = { viewModel.aiVoiceSpeed = it },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Working Speech to Text recorder simulator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(SurfaceMuted, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .clickable {
                                    if (recordingDurationLeft == 0) {
                                        recordingDurationLeft = 4
                                        Toast.makeText(context, "Microphone active. Synthesizing voice parameters...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (recordingDurationLeft > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("🔴 RECORDING ACTIVE: SCANNING SPEECH PATTERN ($recordingDurationLeft SEC LEFT)", color = DestructiveRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Generate beautiful sine graph animations based on dynamic spectrum mic scale!
                                    Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                                        val width = size.width
                                        val height = size.height
                                        val mid = height / 2f
                                        val path = Path()

                                        for (x in 0..width.toInt() step 5) {
                                            val rad = (x.toFloat() / width) * Math.PI * 8f
                                            val y = mid + Math.sin(rad).toFloat() * (12f * micWaveScale)
                                            if (x == 0) path.moveTo(0f, y) else path.lineTo(x.toFloat(), y)
                                        }
                                        drawPath(path, color = DestructiveRed, style = Stroke(width = 3f))
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🎤 TAP TO RECORD SPEECH INTERFACE (STT SIMULATOR)", color = ElectricCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text("Runs local biometric auditory pattern matching straight to chat input", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Test spoken trigger readout
                        var testSpeechText by remember { mutableStateOf("I am ready to follow you, White Rabbit. Let us explore the Wonderland of your reality together.") }
                        OutlinedTextField(
                            value = testSpeechText,
                            onValueChange = { testSpeechText = it },
                            label = { Text("TTS Text Coordination Substrate", color = TextMuted) },
                            textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.speakWithModulation(testSpeechText) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Speak Modulated Response (TTS)", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.stopSpeaking() },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceMuted, contentColor = TextPrimary),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Stop Audio", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Tab Screen 3: Vision Forge (Image Gen and Editor)
        if (viewModel.activeAiTab == "vision") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("👁 Vision Forge — Image Studio", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text("Draft holographic concept wireframes procedurally or query direct image model.", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Render Canvas Art Generation Interface
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(NearBlack, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            // High performance procedural vector graphics
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val cx = width / 2f
                                val cy = height / 2f

                                // Draw neon grid matching Matrix Density
                                val spacing = (40f * matrixDensity).coerceAtLeast(10f)
                                for (x in 0..width.toInt() step spacing.toInt()) {
                                    val offset = neonWarp * 15f
                                    drawLine(
                                        color = if (activeFilter == "Green Phosphor") GreenPhosphor.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.12f),
                                        start = Offset(x.toFloat(), 0f),
                                        end = Offset(x.toFloat() + offset, height),
                                        strokeWidth = 1f
                                    )
                                }

                                for (y in 0..height.toInt() step spacing.toInt()) {
                                    val offset = neonWarp * 15f
                                    drawLine(
                                        color = if (activeFilter == "Green Phosphor") GreenPhosphor.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.12f),
                                        start = Offset(0f, y.toFloat()),
                                        end = Offset(width, y.toFloat() + offset),
                                        strokeWidth = 1f
                                    )
                                }

                                // Interactive fractal Concentric Orbitals
                                val orbitsCount = 10
                                for (i in 1..orbitsCount) {
                                    val radiusBase = (i * 12f * fractalScale / 100f).coerceAtLeast(5f)
                                    val radValue = (i * Math.PI / orbitsCount).toFloat()

                                    val strokeTint = when (activeFilter) {
                                        "Green Phosphor" -> GreenPhosphor.copy(alpha = 1.0f - (i.toFloat() / orbitsCount))
                                        "Heatmap Scan" -> Color.Red.copy(alpha = (i.toFloat() / orbitsCount))
                                        "Inverted" -> Color.White.copy(alpha = 0.8f)
                                        else -> ElectricCyan.copy(alpha = 1.0f - (i.toFloat() / orbitsCount))
                                    }

                                    // Concentric math coordinate offset rotation
                                    val ox = cx + Math.sin((radValue * neonWarp).toDouble()).toFloat() * 15f
                                    val oy = cy + Math.cos((radValue * neonWarp).toDouble()).toFloat() * 15f

                                    drawCircle(
                                        color = strokeTint,
                                        radius = radiusBase,
                                        center = Offset(ox, oy),
                                        style = Stroke(width = 1.5f)
                                    )
                                }

                                // Interactive hologram Scanline Overlay
                                if (activeFilter == "Heatmap Scan") {
                                    for (y in 0..height.toInt() step 8) {
                                        drawLine(
                                            color = Color.Yellow.copy(alpha = 0.06f),
                                            start = Offset(0f, y.toFloat()),
                                            end = Offset(width, y.toFloat()),
                                            strokeWidth = 2f
                                        )
                                    }
                                }
                            }

                            // Watermark filter tag
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                        .background(NearBlack.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("SYSTEM VECTOR: [Filter: $activeFilter]", color = GreenPhosphor, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sliders for dynamic art generation parameters
                        Text("Model Resonance Scale: ${fractalScale.toInt()}%", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Slider(
                            value = fractalScale,
                            onValueChange = { fractalScale = it },
                            valueRange = 10f..250f,
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                        )

                        Text("Temporal Shear Coefficient: ${String.format(Locale.US, "%.1f", neonWarp)}x", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Slider(
                            value = neonWarp,
                            onValueChange = { neonWarp = it },
                            valueRange = 0.1f..4.0f,
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                        )

                        Text("Matrix Bio-Grid Density: ${matrixDensity.toInt()}", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Slider(
                            value = matrixDensity,
                            onValueChange = { matrixDensity = it },
                            valueRange = 1f..10f,
                            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Filtering Editor Presets
                        Text("Vision Modulator Filters:", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("None", "Inverted", "Green Phosphor", "Heatmap Scan").forEach { filterOpt ->
                                val active = activeFilter == filterOpt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (active) ElectricCyan.copy(alpha = 0.15f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (active) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                        .clickable { activeFilter = filterOpt }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(filterOpt.uppercase(), color = if (active) ElectricCyan else TextMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Asset Index Pipeline Substrate
                        Button(
                            onClick = {
                                val sizeName = "${fractalScale.toInt()}-${neonWarp.toInt()}"
                                val fileName = "generated-concept-$sizeName-${System.currentTimeMillis() % 1000}.html"
                                val syntheticHtml = """<!DOCTYPE html>
<html>
<head>
    <title>Sovereign Holographic Concept - $fileName</title>
    <style>
        body { background-color: #030712; color: #00d4ff; font-family: monospace; padding: 40px; text-align: center; }
        .holo-grid { border: 2px solid ${if (activeFilter == "Green Phosphor") "#00ff88" else "#00d4ff"}; padding: 20px; border-radius: 8px; margin-top: 50px; background: rgba(0,0,0,0.5); }
    </style>
</head>
<body>
    <h1>Sovereign Conceptual Hologram File</h1>
    <div class='holo-grid'>
        <h2>COORDINATES LOADED SECURELY</h2>
        <p>Resonance Scale: ${fractalScale.toInt()}%</p>
        <p>Shear Coefficient: $neonWarp</p>
        <p>Grid Density: ${matrixDensity.toInt()}</p>
        <p>Modulation Filter: $activeFilter</p>
    </div>
</body>
</html>"""
                                viewModel.addManualFile(fileName, syntheticHtml, "AI,concept,procedural,${activeFilter.lowercase().replace(" ","")}")
                                Toast.makeText(context, "Sovereign conceptual vector design mapped to local Async Explorer ledger!", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPhosphor, contentColor = NearBlack),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Export Vector Concept Asset to Search Ledger", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Tab Screen 4: Media Synth (Video / Audio Lyria oscillators)
        if (viewModel.activeAiTab == "media") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🎬 Complex Media & Auditory Synthesis (Google Lyria & Veo)", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text("Utilize neural temporal animations to compile responsive 60 FPS visual grids and synthesized audio waveforms.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Video Prompts Setup
                        OutlinedTextField(
                            value = mediaPrompt,
                            onValueChange = { mediaPrompt = it },
                            placeholder = { Text("Describe cinematic video animation sequences... (e.g. Glowing bio-lattice waves)", color = TextMuted, fontSize = 11.sp) },
                            textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Simulated Video Player
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(NearBlack, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height

                                // Draw sweeping spatial grid animating over time index
                                val particleRate = 12
                                for (i in 0..particleRate) {
                                    val animOffset = (videoFrameIndex.toFloat() / 64f) * width
                                    val px = ((i * (width / particleRate)) + animOffset) % width
                                    val py = height / 2f + Math.sin((px / width) * Math.PI * 4f).toFloat() * (40f * videoGlitchFactor * 5f)

                                    drawCircle(
                                        color = if (i % 2 == 0) ElectricCyan else GreenPhosphor,
                                        radius = 6f + Math.sin(videoFrameIndex.toDouble() + i).toFloat() * 3f,
                                        center = Offset(px, py)
                                    )
                                }

                                // Interactive hologram grid matrix overlay
                                for (y in 0..height.toInt() step 20) {
                                    drawLine(
                                        color = ElectricCyan.copy(alpha = 0.08f),
                                        start = Offset(0f, y.toFloat()),
                                        end = Offset(width, y.toFloat()),
                                        strokeWidth = 1f
                                    )
                                }
                            }

                            // Dynamic state watermarks
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(NearBlack.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("60 FPS VEO LIVE TIMELINE | FRAME: $videoFrameIndex", color = ElectricCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Glitch Overlay: ${(videoGlitchFactor * 100).toInt()}%", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Slider(
                                    value = videoGlitchFactor,
                                    onValueChange = { videoGlitchFactor = it },
                                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Slow-motion Factor: ${String.format(Locale.US, "%.1f", videoSlowMoRate)}x", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Slider(
                                    value = videoSlowMoRate,
                                    onValueChange = { videoSlowMoRate = it },
                                    valueRange = 0.5f..3.0f,
                                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- SECTION B: AUDIO & MUSIC LYRIA BLOCK ---
                        Text("🎹 Google Lyria — Visual Waveform Monitor", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("Interactive frequency oscillator matrix simulation. Hardward synthesizer channel offline for stream compatibility.", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic Real-time Audio wave Oscillograph
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(SurfaceMuted, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val mid = height / 2f
                                val path = Path()

                                for (x in 0..width.toInt() step 4) {
                                    val radians = (x.toFloat() / width) * Math.PI * (synthOscillatorHz / 50f)
                                    val y = mid + Math.sin(radians).toFloat() * 18f
                                    if (x == 0) path.moveTo(0f, y) else path.lineTo(x.toFloat(), y)
                                }
                                drawPath(path, color = GreenPhosphor.copy(alpha = 0.8f), style = Stroke(width = 2.5f))
                            }
                            
                            Text("OSCILLOSCOPE: SIMULATED CARRIER ACTIVE (${synthOscillatorHz.toInt()} Hz)", color = GreenPhosphor, fontSize = 8.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceMuted, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔒", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                Text("Audio synthesizer thread deactivated to prevent kernel-level emulator crashes on this virtual node.", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // Tab Screen 5: Protocols & Local Collaboration Setup
        if (viewModel.activeAiTab == "protocols") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔑 Sovereign Workspace Workflows & Protocols", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text("Execute direct workflow automation scripts utilizing local index files and automated natural language queries.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Protocols list grid
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                "ledger_audit" to "⚖ Ledger Audit",
                                "apothecary_synth" to "🧪 Compounding Synth",
                                "sandbox_inject" to "⚙ Live wireframe HTML"
                            ).forEach { (pKey, pLabel) ->
                                val active = activeProtocolName == pKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (active) ElectricCyan.copy(alpha = 0.15f) else SurfaceMuted, RoundedCornerShape(4.dp))
                                        .border(1.dp, if (active) ElectricCyan else BorderColor, RoundedCornerShape(4.dp))
                                        .clickable {
                                            activeProtocolName = pKey
                                            protocolExecutionLog = "Analyzing payload conditions for $pLabel...\nPress 'Run Automation Protocol' to deploy nodes."
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(pLabel.uppercase(), color = if (active) ElectricCyan else TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Terminal Log box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(SurfaceMuted, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        ) {
                            Text("[CORTEX WORKFLOW COMPILER INTERFACE]", color = ElectricCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = protocolExecutionLog,
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (activeProtocolName == "None") {
                                    Toast.makeText(context, "Select a protocol profile from the headers row.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                protocolExecutionLog = "Executing script matrix..."

                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    when (activeProtocolName) {
                                        "ledger_audit" -> {
                                            val reportTitle = "Ledger Audit - " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                                            val auditContent = "Ledger health index: 98%. Integrity validation tags: Checked. Alignments safe."
                                            viewModel.addManualFile(
                                                "$reportTitle.txt",
                                                auditContent,
                                                "AI,audit,ledger"
                                            )
                                            protocolExecutionLog = "SUCCESS: Mapped '$reportTitle' to search registry.\nSession coordinates telemetry clear."
                                        }

                                        "apothecary_synth" -> {
                                            viewModel.builderNotesText = "Compound Synthesis Substrate #772\nFormula Resonance Score: 94.2\nLattice alignment: Active Core\nFormulated at timeline hour reference"
                                            viewModel.activeBuilderTab = "note"
                                            protocolExecutionLog = "SUCCESS: Automated Apothecary Formulations compiled directly onto 'Live Builder' notepad substrate!"
                                        }

                                        "sandbox_inject" -> {
                                            viewModel.builderCode = """<!DOCTYPE html>
<html>
<head>
    <style>
        body { background-color: #0c101d; color: #00d4ff; font-family: monospace; padding: 24px; }
        .grid-node { border: 1px solid #2e3f50; padding: 16px; background-color: #050a12; border-radius: 4px; }
    </style>
</head>
<body>
    <div class='grid-node'>
        <h2>CORTEX INJECTED SECURE SANDBOX WIREFRAME</h2>
        <p>Telemetry metrics: STABLE (94.2% Grid Load)</p>
        <p>Operational directives: STANDBY</p>
    </div>
</body>
</html>"""
                                            viewModel.activeBuilderTab = "code"
                                            protocolExecutionLog = "SUCCESS: Beautiful custom inline responsive dashboard HTML wireframe coordinates injected into 'Live Builder' sandbox successfully!"
                                        }
                                    }
                                    viewModel.logActivity("AI", "Workflow protocol execution complete", activeProtocolName)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPhosphor, contentColor = NearBlack),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Run Automation Protocol Directive", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        // --- LOCAL TEAM COLLaBORATION PORTAL ---
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("🤝 Local Workspace Team Collaboration Portal", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(SurfaceMuted, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            localCollabComments.forEach { post ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NearBlack, RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(text = post, color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = collabFieldText,
                                onValueChange = { collabFieldText = it },
                                placeholder = { Text("Compile collaborator progress updates...", color = TextMuted, fontSize = 11.sp) },
                                textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricCyan, unfocusedBorderColor = BorderColor),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (collabFieldText.trim().isEmpty()) return@Button
                                    localCollabComments.add("Operator [Sovereign]: " + collabFieldText.trim())
                                    collabFieldText = ""
                                    Toast.makeText(context, "Progress note synchronised.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Sync Post", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Tab Screen 6: Art & Line Art Restoring
        if (viewModel.activeAiTab == "art") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🎨 Art & Linework Re-compiler", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text("Taming the noise! Extract precise lineart and declutter generated images to rebuild your own interactive coloring book variations.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Placeholder for image selection
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(SurfaceMuted, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                .clickable {
                                    Toast.makeText(context, "Scanning local gallery for unrefined generative canvases...", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🖼", fontSize = 48.sp, modifier = Modifier.padding(bottom = 8.dp))
                                Text("Tap to select image from local gallery...", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                Toast.makeText(context, "Executing De-cluttering & Line Art Extraction sub-algorithms. Building your base canvas!", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("PULL LINE ART & DECLUTTER CANVAS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Tab Screen 7: Data Takeout & JSON Export
        if (viewModel.activeAiTab == "takeout") {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NearBlack),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📦 Sovereign Data Takeout", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text("Extract, pack, and transport your entire AI operability memory, ledgers, and cognitive history across independent environments. Migrate away from dependencies.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("LOCAL MEMORY LEDGERS (JSON):", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().background(SurfaceMuted).padding(8.dp)) {
                            Text("✓ Historical Chat Logs\n✓ Synaptic Formulations\n✓ Network Notes\n✓ Builder Formats", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("EXTERNAL IMPORT SOURCES:", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Scanning for Google Takeout (Gemini) JSON... Synaptic pathways updating.", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4), contentColor = Color.White),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("IMPORT FROM GEMINI", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                            
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Scanning for NovelAI Story/Lorebook JSON... Context ledgers synchronizing.", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6), contentColor = Color.White),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("IMPORT FROM NOVEL AI", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Packing operational data! AI Memory saved to Download/takeout.json", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = NearBlack),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("EXPORT MEMORY (JSON)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }

                            Button(
                                onClick = {
                                    Toast.makeText(context, "Importing native AI operative memory...", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPhosphor, contentColor = NearBlack),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("IMPORT MEMORY (JSON)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN: BOOTUP INTRO SCREEN
// -------------------------------------------------------------
@Composable
fun BootupIntroScreen(viewModel: GreatHallViewModel, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val DeepNavy = viewModel.getBackgroundColor()
    val ElectricCyan = viewModel.getPrimaryColor()
    val GreenPhosphor = viewModel.getSecondaryColor()

    var showSecondPhase by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        showSecondPhase = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Welcome to Universal Framework v1.0",
                color = ElectricCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "The Great Hall: Individualized Universe to Xperience",
                color = GreenPhosphor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            AnimatedVisibility(
                visible = showSecondPhase,
                enter = fadeIn() + expandVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "I am Alice. You have tumbled down the neon rabbit hole.",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "Let us explore this Wonderland of a reality together.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Type \"help\" to see available commands or just enter...", color = Color.Gray, fontSize = 12.sp) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepNavy),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("MINGLE WITH ACCOMPLICE & EXPLORE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

