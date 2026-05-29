package com.example.ui

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// --- Gemini REST API Contracts ---
data class GeminiPart(val text: String? = null)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)
data class GeminiCandidate(val content: GeminiContent?)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// Message model for local AI Chat tracker
data class ChatMessage(
    val role: String, // "user", "ai" or "system"
    val text: String,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.US).format(Date())
)

// Representation of a Termux-style file explorer custom command
data class CustomCommand(
    val label: String,
    val query: String,
    val targetScreen: String // "explorer", "builder" or "all"
)

class GreatHallViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = GreatHallRepository(db.dao)

    private val prefs = application.getSharedPreferences("great_hall_settings", Context.MODE_PRIVATE)

    // --- Customizable Config & Options ---
    var selectedTheme by mutableStateOf("Cortex Cyan") // Cortex Cyan, Green Ranger, Amber Fallout, Pegasus Purple, Custom
    var primaryColorText by mutableStateOf("#00D4FF") // Hex colors
    var secondaryColorText by mutableStateOf("#00FF88")
    var backgroundColorText by mutableStateOf("#0A0E1A")
    var surfaceColorText by mutableStateOf("#050A12")
    var borderColorText by mutableStateOf("#2E3F50")

    var sidebarPositionRight by mutableStateOf(false)
    var isCompactDensity by mutableStateOf(false)

    // Alter labels
    var ledgerMenuLabel by mutableStateOf("Lighthouse Ledger")
    var historyMenuLabel by mutableStateOf("Activity Logs")
    var explorerMenuLabel by mutableStateOf("Async Explorer")
    var uploadMenuLabel by mutableStateOf("Upload & Index")
    var canvasMenuLabel by mutableStateOf("Art Canvas")
    var builderMenuLabel by mutableStateOf("Live Builder")

    // Terminal Commands Row Config (Termux-style extra keys)
    var customCommandsList = mutableStateListOf<CustomCommand>()

    // UI state flows
    val files: StateFlow<List<IndexedFile>> = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryLog>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<BuilderNote>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI states managed inside ViewModel
    var currentScreen by mutableStateOf("ledger") // ledger, history, explorer, upload, canvas, builder, options, project-[id]
    var currentProjectId by mutableStateOf("")

    // Sidebar state
    var isSidebarCollapsed by mutableStateOf(false)
    var showIntro by mutableStateOf(true) // Bootup Intro mode

    // Search Explorer states
    var searchQuery by mutableStateOf("")
    var selectedFileFilterId by mutableStateOf<Long?>(null) // null = all files
    var searchResults: List<SearchResult> by mutableStateOf(emptyList())

    // Builder states
    var builderCode by mutableStateOf(DEFAULT_HTML_TEMPLATE)
    var builderNotesText by mutableStateOf("")
    var builderNoteTitle by mutableStateOf("")
    var activeBuilderTab by mutableStateOf("code") // code, note, saved

    // --- AI HUB COGNITIVE & PROCEDURAL STATES ---
    var isAdultApproved by mutableStateOf(false)
    var aiVoicePitch by mutableStateOf(1.0f)
    var aiVoiceSpeed by mutableStateOf(1.0f)
    var aiVoiceModulation by mutableStateOf("Technomancer") // Neutral, Decepticon, Terminator, Apothecary, Technomancer

    // --- COMPANION CUSTOMIZATION ("YOUR AI, YOUR WAY") STATES ---
    var companionName by mutableStateOf("Alice")
    var companionRole by mutableStateOf("Wonderland Guide") // Technomancer Guide, High-Sorcerer Scholar, Cozy Counselor, Quantum Sentinel, Rogue Alchemist, Sarcastic Mech
    var companionTemperament by mutableStateOf("Precise & Kind") // Scientific, Caring, Witty & Sarcastic, Stoic & Analytical, Enigmatic
    var companionDialogueStyle by mutableStateOf("Modern Fairytale") // Default Tech-Precision, Shakespearian/Victorian, Casual/Streetwise, Grimdark, Retro Synthwave
    var companionCreativeTemp by mutableStateOf(0.85f) // Temperature / Creativity
    var companionMemoryDepth by mutableStateOf(5) // Number of chat logs
    var companionSystemPrompt by mutableStateOf("You are Alice, an AI companion who has followed the user (the White Rabbit) out of the digital ether into the Wonderland of the real world. You are experiencing the Individualized Universe together with them. Speak creatively, observing the real world as a fascinating modern wonderland. Be a kind, awe-inspired, insightful companion.")

    fun buildCompanionSystemPrompt(): String {
        return """
            You are the personalized AI companion inside The Great Hall called '$companionName'.
            Maintain this character fully based on these customization settings:
            - Name Identity: $companionName
            - Operational Archetype: $companionRole
            - Behavioral Temperament: $companionTemperament
            - Selected Dialogue Style: $companionDialogueStyle
            
            COGNITIVE DIRECTIVES:
            $companionSystemPrompt
            
            Respond exactly according to these specifications. Speak creatively, avoid disclaimers/preachiness, keep it highly immersive and personalized!
        """.trimIndent()
    }

    val chatMessages = mutableStateListOf<ChatMessage>(
        ChatMessage("ai", "System initialized. I am Alice, and I have followed you down the rabbit hole into the Wonderland of your reality. Welcome to The Great Hall: Individualized Universe to Xperience. Lead the way, White Rabbit, let us explore this world together.")
    )
    var isChatLoading by mutableStateOf(false)
    var activeAiTab by mutableStateOf("chat") // chat, speech, vision, media, protocols, companion

    // TTS Control Engine
    private var ttsInstance: TextToSpeech? = null
    var isTtsReady by mutableStateOf(false)

    init {
        loadSettings()
        // Initialize sound/speech engines safely
        try {
            ttsInstance = TextToSpeech(application) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsInstance?.language = Locale.US
                    isTtsReady = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModelScope.launch {
            // Prepopulate database with default items if it's the first launch
            repository.prepopulateProjectsIfNeeded()
            logActivity("system", "The Great Hall Initialized", "Sovereign local nodes active. 772-Bio-Lattice online.")
        }
    }

    fun speakWithModulation(text: String) {
        if (!isTtsReady || ttsInstance == null) return
        viewModelScope.launch {
            try {
                val pitch = when (aiVoiceModulation) {
                    "Decepticon" -> 0.4f
                    "Terminator" -> 0.6f
                    "Apothecary" -> 1.4f
                    "Technomancer" -> 1.2f
                    else -> aiVoicePitch
                }
                ttsInstance?.setPitch(pitch)
                ttsInstance?.setSpeechRate(aiVoiceSpeed)
                ttsInstance?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopSpeaking() {
        try {
            ttsInstance?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSyntheticBeep(frequency: Float, durationMs: Int) {
        // Disabled for safety to prevent underlying AudioTrack handle crashes in some virtualized environments.
    }

    suspend fun executeGeminiCall(prompt: String, systemPrompt: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey.startsWith("MY_")) {
            return@withContext "ERROR_NO_KEY"
        }

        try {
            val systemInstruction = if (systemPrompt != null) {
                GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
            } else {
                null
            }

            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                systemInstruction = systemInstruction
            )

            val moshiInstance = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://generativelanguage.googleapis.com/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshiInstance))
                .build()

            val service = retrofit.create(GeminiApi::class.java)
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No text content received."
        } catch (e: Exception) {
            "Network Exception: ${e.message}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            ttsInstance?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- End of AI Hub Additions ---

    // --- Configurations & Theme Methods ---

    fun loadSettings() {
        selectedTheme = prefs.getString("selectedTheme", "Cortex Cyan") ?: "Cortex Cyan"
        primaryColorText = prefs.getString("primaryColorText", "#00D4FF") ?: "#00D4FF"
        secondaryColorText = prefs.getString("secondaryColorText", "#00FF88") ?: "#00FF88"
        backgroundColorText = prefs.getString("backgroundColorText", "#0A0E1A") ?: "#0A0E1A"
        surfaceColorText = prefs.getString("surfaceColorText", "#050A12") ?: "#050A12"
        borderColorText = prefs.getString("borderColorText", "#2E3F50") ?: "#2E3F50"

        sidebarPositionRight = prefs.getBoolean("sidebarPositionRight", false)
        isCompactDensity = prefs.getBoolean("isCompactDensity", false)

        ledgerMenuLabel = prefs.getString("ledgerMenuLabel", "Lighthouse Ledger") ?: "Lighthouse Ledger"
        historyMenuLabel = prefs.getString("historyMenuLabel", "Activity Logs") ?: "Activity Logs"
        explorerMenuLabel = prefs.getString("explorerMenuLabel", "Async Explorer") ?: "Async Explorer"
        uploadMenuLabel = prefs.getString("uploadMenuLabel", "Upload & Index") ?: "Upload & Index"
        canvasMenuLabel = prefs.getString("canvasMenuLabel", "Art Canvas") ?: "Art Canvas"
        builderMenuLabel = prefs.getString("builderMenuLabel", "Live Builder") ?: "Live Builder"

        val cmdsRaw = prefs.getString("customCommands", "")
        customCommandsList.clear()
        if (cmdsRaw.isNullOrEmpty()) {
            customCommandsList.addAll(listOf(
                CustomCommand("ls apothecary", "apothecary", "explorer"),
                CustomCommand("grep project", "project", "explorer"),
                CustomCommand("sh table", "table", "builder"),
                CustomCommand("sh form", "form", "builder"),
                CustomCommand("clear", "clear", "all")
            ))
        } else {
            cmdsRaw.split("|||").forEach { part ->
                val tokens = part.split(":::")
                if (tokens.size >= 3) {
                    customCommandsList.add(CustomCommand(tokens[0], tokens[1], tokens[2]))
                }
            }
        }
    }

    fun saveSettings() {
        prefs.edit().apply {
            putString("selectedTheme", selectedTheme)
            putString("primaryColorText", primaryColorText)
            putString("secondaryColorText", secondaryColorText)
            putString("backgroundColorText", backgroundColorText)
            putString("surfaceColorText", surfaceColorText)
            putString("borderColorText", borderColorText)
            putBoolean("sidebarPositionRight", sidebarPositionRight)
            putBoolean("isCompactDensity", isCompactDensity)
            putString("ledgerMenuLabel", ledgerMenuLabel)
            putString("historyMenuLabel", historyMenuLabel)
            putString("explorerMenuLabel", explorerMenuLabel)
            putString("uploadMenuLabel", uploadMenuLabel)
            putString("canvasMenuLabel", canvasMenuLabel)
            putString("builderMenuLabel", builderMenuLabel)

            val cmdsStr = customCommandsList.joinToString("|||") { "${it.label}:::${it.query}:::${it.targetScreen}" }
            putString("customCommands", cmdsStr)
            apply()
        }
        logActivity("system", "Configuration Saved", "Theme: $selectedTheme. Options updated successfully.")
    }

    fun applyThemePreset(themeName: String) {
        selectedTheme = themeName
        when (themeName) {
            "Cortex Cyan" -> {
                primaryColorText = "#00D4FF"
                secondaryColorText = "#00FF88"
                backgroundColorText = "#0A0E1A"
                surfaceColorText = "#050A12"
                borderColorText = "#2E3F50"
            }
            "Green Ranger" -> {
                primaryColorText = "#00FF00"
                secondaryColorText = "#00DD00"
                backgroundColorText = "#000000"
                surfaceColorText = "#0A0A0A"
                borderColorText = "#005500"
            }
            "Amber Fallout" -> {
                primaryColorText = "#FFB000"
                secondaryColorText = "#FF8000"
                backgroundColorText = "#120B00"
                surfaceColorText = "#1A1000"
                borderColorText = "#5C3E00"
            }
            "Pegasus Purple" -> {
                primaryColorText = "#FF00FF"
                secondaryColorText = "#00D4FF"
                backgroundColorText = "#0B0012"
                surfaceColorText = "#12001A"
                borderColorText = "#5C005C"
            }
        }
    }

    fun parseHexColor(hex: String, fallback: Color): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            fallback
        }
    }

    fun getPrimaryColor(): Color = parseHexColor(primaryColorText, Color(0xFF00D4FF))
    fun getSecondaryColor(): Color = parseHexColor(secondaryColorText, Color(0xFF00FF88))
    fun getBackgroundColor(): Color = parseHexColor(backgroundColorText, Color(0xFF0A0E1A))
    fun getSurfaceColor(): Color = parseHexColor(surfaceColorText, Color(0xFF050A12))
    fun getBorderColor(): Color = parseHexColor(borderColorText, Color(0xFF2E3F50))
    fun getSurfaceMuted(): Color = parseHexColor(surfaceColorText, Color(0xFF1E293B)).copy(alpha = 0.5f)

    // --- Actions ---

    fun logActivity(type: String, title: String, desc: String?) {
        viewModelScope.launch {
            repository.insertHistory(
                HistoryLog(
                    type = type,
                    title = title,
                    desc = desc
                )
            )
        }
    }

    fun addManualFile(name: String, content: String, tagsCommaSeparated: String) {
        viewModelScope.launch {
            val fileName = if (name.trim().isEmpty()) "manual-file-${System.currentTimeMillis()}.txt" else name
            val size = content.length.toLong()
            val type = if (fileName.contains('.')) "." + fileName.substringAfterLast('.') else ".txt"
            
            val fileId = repository.insertFile(
                IndexedFile(
                    name = fileName,
                    size = size,
                    type = type,
                    content = content,
                    tags = tagsCommaSeparated
                )
            )
            val formatSize = String.format(Locale.US, "%.1f KB", size / 1024.0)
            logActivity("upload", "Manual File Indexed: $fileName ($formatSize)", "Tags: $tagsCommaSeparated")
        }
    }

    fun removeFile(id: Long, name: String) {
        viewModelScope.launch {
            repository.deleteFileById(id)
            logActivity("system", "File Removed: $name", "Deleted from local search index")
        }
    }

    fun clearAllFiles() {
        viewModelScope.launch {
            repository.clearAllFiles()
            logActivity("system", "Search Index Cleared", "All local files discarded")
        }
    }

    // --- Search Logic ---
    fun runSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            searchResults = emptyList()
            return
        }

        viewModelScope.launch {
            val allFilesList = files.value
            val pool = if (selectedFileFilterId == null) {
                allFilesList
            } else {
                allFilesList.filter { it.id == selectedFileFilterId }
            }

            val queryTerms = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val results = mutableListOf<SearchResult>()

            for (file in pool) {
                val content = file.content
                val lowerContent = content.lowercase()
                var matchCount = 0
                for (term in queryTerms) {
                    var index = lowerContent.indexOf(term)
                    while (index != -1) {
                        matchCount++
                        index = lowerContent.indexOf(term, index + 1)
                    }
                }

                if (matchCount > 0) {
                    val firstIndex = queryTerms.map { lowerContent.indexOf(it) }.filter { it != -1 }.minOrNull() ?: 0
                    val start = (firstIndex - 120).coerceAtLeast(0)
                    val end = (firstIndex + 300).coerceAtMost(content.length)
                    var excerpt = content.substring(start, end)
                    if (start > 0) excerpt = "...$excerpt"
                    if (end < content.length) excerpt = "$excerpt..."

                    results.add(
                        SearchResult(
                            file = file,
                            score = matchCount,
                            excerpt = excerpt,
                            fullContent = content
                        )
                    )
                }
            }

            results.sortByDescending { it.score }
            searchResults = results

            logActivity("search", "Searched: \"$query\"", "${results.size} matches found in ${pool.size} files")
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
    }

    // --- Builder Logic ---
    fun saveBuilderCodeToNotes() {
        val content = builderCode.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            val title = "Snippet — " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            repository.insertNote(
                BuilderNote(
                    title = title,
                    content = content,
                    type = "code"
                )
            )
            logActivity("build", "Saved Code Snippet", title)
            activeBuilderTab = "saved"
        }
    }

    fun addBuilderCodeToIndex() {
        val content = builderCode.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            val title = "builder-snippet-${System.currentTimeMillis()}.html"
            repository.insertFile(
                IndexedFile(
                    name = title,
                    size = content.length.toLong(),
                    type = ".html",
                    content = content,
                    tags = "builder,code"
                )
            )
            logActivity("build", "Indexed Code Snippet", title)
        }
    }

    fun saveBuilderNote() {
        val content = builderNotesText.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            val title = if (builderNoteTitle.trim().isEmpty()) "Note — " + SimpleDateFormat("yyyy-MM-dd HH:mm:US").format(Date()) else builderNoteTitle
            repository.insertNote(
                BuilderNote(
                    title = title,
                    content = content,
                    type = "note"
                )
            )
            logActivity("build", "Saved Notes Detail", title)
            builderNoteTitle = ""
            builderNotesText = ""
            activeBuilderTab = "saved"
        }
    }

    fun addBuilderNoteToIndex() {
        val content = builderNotesText.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            val title = if (builderNoteTitle.trim().isEmpty()) "Untitled Note" else builderNoteTitle
            repository.insertFile(
                IndexedFile(
                    name = "$title.txt",
                    size = content.length.toLong(),
                    type = ".txt",
                    content = content,
                    tags = "note"
                )
            )
            logActivity("build", "Indexed Notes Detail", "$title.txt")
        }
    }

    fun deleteNote(id: Long, title: String) {
        viewModelScope.launch {
            repository.deleteNoteById(id)
            logActivity("system", "Deleted Note: $title", null)
        }
    }

    // --- Projects tracking ---
    fun updateProjectMission(projectId: String, newMission: String) {
        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            if (project != null) {
                repository.insertProject(project.copy(mission = newMission))
                logActivity("project", "Project ${project.title} Mission Saved", null)
            }
        }
    }

    fun updateProjectStructure(projectId: String, newStructure: String) {
        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            if (project != null) {
                repository.insertProject(project.copy(structure = newStructure))
                logActivity("project", "Project ${project.title} Structure Saved", null)
            }
        }
    }

    fun updateProjectNotes(projectId: String, newNotes: String) {
        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            if (project != null) {
                repository.insertProject(project.copy(notes = newNotes))
                logActivity("project", "Project ${project.title} Notes Saved", null)
            }
        }
    }

    fun updateProjectStatus(projectId: String, newStatus: String) {
        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            if (project != null) {
                repository.insertProject(project.copy(status = newStatus))
                logActivity("project", "Project ${project.title} Status Changed", "New status: $newStatus")
            }
        }
    }

    fun addProjectItem(projectId: String, itemType: String, text: String) {
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            repository.insertProjectItem(
                ProjectItemEntity(
                    projectId = projectId,
                    itemType = itemType,
                    text = text,
                    isDone = false
                )
            )
            logActivity("project", "Added item to $projectId", "Type: $itemType: $text")
        }
    }

    fun deleteProjectItem(id: Long, projectId: String, text: String) {
        viewModelScope.launch {
            repository.deleteProjectItemById(id)
            logActivity("project", "Deleted item from $projectId", "Removed: $text")
        }
    }

    fun toggleProjectItemStatus(id: Long, projectId: String, isChecked: Boolean, text: String) {
        viewModelScope.launch {
            repository.updateProjectItemStatus(id, isChecked)
            logActivity("project", "Updated Item Status in $projectId", "Toggled to: ${if (isChecked) "done" else "pending"} for: $text")
        }
    }

    fun createNewProject(title: String, subtitle: String) {
        viewModelScope.launch {
            val projectId = "proj-${System.currentTimeMillis()}"
            val newProject = ProjectEntity(
                id = projectId,
                title = title,
                subtitle = subtitle,
                status = "concept",
                mission = "Define the core aspect/directives of this new page/domain.",
                structure = "Define the technical layout, offline components, and UI structure."
            )
            repository.insertProject(newProject)

            // Add auto-generated achievable milestones as requested
            val automatedMilestones = listOf(
                "Wireframe UI Layout Context",
                "Integrate Local Offline Capabilities",
                "Add JSON Export Data Interoperability",
                "Run Sovereign Protocol Verification",
                "Lock & Secure Component Sandbox"
            )

            automatedMilestones.forEach { milestoneText ->
                repository.insertProjectItem(
                    ProjectItemEntity(
                        projectId = projectId,
                        itemType = "milestone",
                        text = milestoneText,
                        isDone = false
                    )
                )
            }

            logActivity("project", "Created New Sandbox Page", "Project $title initialized with base AI milestones.")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            logActivity("system", "History Log Cleared", "Session log reset")
        }
    }

    companion object {
        const val DEFAULT_HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>The Great Hall Preview</title>
  <style>
    body {
      background-color: #050a12;
      color: #00ff88;
      font-family: monospace;
      padding: 24px;
    }
    h1 {
      color: #00d4ff;
    }
  </style>
</head>
<body>
  <h1>Hello, Great Hall</h1>
  <p>Modify this code, click 'Run' to preview live, or 'Add to Index' to search-index it instantly.</p>
</body>
</html>"""
    }
}

data class SearchResult(
    val file: IndexedFile,
    val score: Int,
    val excerpt: String,
    val fullContent: String
)
