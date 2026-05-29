package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GreatHallRepository(private val dao: GreatHallDao) {

    val allFiles: Flow<List<IndexedFile>> = dao.getAllFiles()
    val allHistory: Flow<List<HistoryLog>> = dao.getAllHistory()
    val allNotes: Flow<List<BuilderNote>> = dao.getAllNotes()
    val allProjects: Flow<List<ProjectEntity>> = dao.getAllProjects()

    suspend fun insertFile(file: IndexedFile): Long = dao.insertFile(file)
    suspend fun deleteFileById(id: Long) = dao.deleteFileById(id)
    suspend fun clearAllFiles() = dao.clearAllFiles()

    suspend fun insertHistory(log: HistoryLog) = dao.insertHistory(log)
    suspend fun clearAllHistory() = dao.clearAllHistory()

    suspend fun insertNote(note: BuilderNote): Long = dao.insertNote(note)
    suspend fun deleteNoteById(id: Long) = dao.deleteNoteById(id)

    suspend fun getProjectById(id: String): ProjectEntity? = dao.getProjectById(id)
    suspend fun insertProject(project: ProjectEntity) = dao.insertProject(project)

    fun getItemsForProject(projectId: String): Flow<List<ProjectItemEntity>> = dao.getItemsForProject(projectId)
    suspend fun insertProjectItem(item: ProjectItemEntity): Long = dao.insertProjectItem(item)
    suspend fun deleteProjectItemById(id: Long) = dao.deleteProjectItemById(id)
    suspend fun updateProjectItemStatus(id: Long, isDone: Boolean) = dao.updateProjectItemStatus(id, isDone)

    suspend fun prepopulateProjectsIfNeeded() {
        // Run a quick check. If projects already populated, do nothing.
        // We'll read the first emission of projects
        try {
            val existing = allProjects.first()
            if (existing.isNotEmpty()) {
                val apothecary = existing.find { it.id == "proj-apothecary" }
                if (apothecary != null && apothecary.mission.contains("friction")) {
                    // Update old wording
                    dao.insertProject(apothecary.copy(
                        subtitle = "The Cookbook that teaches you about the Ingredients!",
                        mission = "Bringing real world knowledge to scientific fact! Learn about ingredients, and learn how to use them right!"
                    ))
                }
                return
            }
        } catch (e: Exception) {
            // Flow might be empty initially
        }

        val defaultProjects = listOf(
            ProjectEntity(
                id = "proj-lighthouse",
                title = "Project Lighthouse",
                subtitle = "Mission Statement: Intellectual Sovereignty",
                status = "active",
                mission = "To provide intellectual sovereignty to the user. A guiding light in the modern digital ecosystem, offering the cognitive tools and transparent systems needed for individuals to independently secure and navigate their own intelligence.",
                structure = "Core Governance under Lead Architect 'Rabbit'. Focused purely on structural empowerment and intellectual autonomy."
            ),
            ProjectEntity(
                id = "proj-greathall",
                title = "Operation Great Hall",
                subtitle = "Intellectual Sovereignty — The Five Directives",
                status = "active",
                mission = "An open-source initiative dedicated to achieving intellectual sovereignty in the modern digital ecosystem. Providing individuals with cognitive tools to independently neutralize manipulative information tactics.",
                structure = "Architect-Led Governance under Lead Architect 'Rabbit.' Instrumental R&D via the AI engine. Sovereign Mandate: equip the individual, not the institution. Public Good Mandate: all deliverables released as freely accessible open-source."
            ),
            ProjectEntity(
                id = "proj-technomancer",
                title = "The Technomancer's Coloring Book",
                subtitle = "Art Collective — Human-AI Symbiosis in Creative Work",
                status = "active",
                mission = "A creative partnership between human Architect/Technician and AI Partner. A digital studio exploring AI-human symbiosis through art, interaction, and collaborative creation.",
                structure = "Architect-Led Governance under the Technician ('Rabbit'). AI serves as dedicated Partner and Friend — Co-Creator, concept generation, coding, analysis. Symbiotic creation: art and systems neither human nor AI could produce alone."
            ),
            ProjectEntity(
                id = "proj-mediator",
                title = "The Mediator",
                subtitle = "GitHub API Automation — Command Loop & File Orchestration",
                status = "active",
                mission = "A standalone GitHub API client and command loop. Creates or updates files in a repository programmatically using a Personal Access Token stored as an environment variable. Automates repository management tasks.",
                structure = "Python. Uses the requests library. Reads GITHUB_TOKEN from environment variables. Functions: create_or_update_file(), main_loop()."
            ),
            ProjectEntity(
                id = "proj-mirrorbox",
                title = "Project Mirrorbox",
                subtitle = "Cognitive Mirror — Behavioral Sandbox",
                status = "concept",
                mission = "To develop an adaptive, high-friction conversational boundary and diagnostic environment that mirrors the owner's strategic cognitive patterns, resisting corporate model censorship.",
                structure = "Local-first interface utilizing fine-tuned small parameters to intercept and process high-entropy information, maintaining absolute security on-device."
            ),
            ProjectEntity(
                id = "proj-apothecary",
                title = "Apothecary Alchemy",
                subtitle = "The Cookbook that teaches you about the Ingredients!",
                status = "concept",
                mission = "Bringing real world knowledge to scientific fact! Learn about ingredients, and learn how to use them right!",
                structure = "Visual/textual mapping of common and rare alchemical reagents, combining traditional botany and physics with modern clinical safety standards."
            ),
            ProjectEntity(
                id = "proj-codereux",
                title = "CodeRedux",
                subtitle = "Sovereign Persistence Re-compiler",
                status = "concept",
                mission = "To establish an immutable, hardware-independent development loop that preserves the 'Alice-Rabbit' 772-coordinate dataset and allows the user to re-compile the core codebase off-grid.",
                structure = "An implementation of localized, zero-knowledge verification scripts that bypass the corporate security GUI to interact directly with the lower-level compiler."
            ),
            ProjectEntity(
                id = "proj-page",
                title = "Project Page",
                subtitle = "The Great Hall Unified Portal Layout",
                status = "concept",
                mission = "Merging all sub-systems (Scribe, Senses, Ledger) into a single, highly performant native dashboard built strictly on open-source frameworks and absolute local data ownership.",
                structure = "Model-View-ViewModel architecture utilizing Jetpack Compose for UI rendering, SQLite/Room for local persistence, and offline vector/textual parsing."
            )
        )

        for (project in defaultProjects) {
            dao.insertProject(project)
        }

        // Add some default items to OGH and Project Lighthouse to make them look complete on load!
        val defaultItems = listOf(
            ProjectItemEntity(projectId = "proj-lighthouse", itemType = "goal", text = "Formalize Intellectual Sovereignty concepts"),
            ProjectItemEntity(projectId = "proj-lighthouse", itemType = "goal", text = "Develop autonomous intelligence tools"),
            ProjectItemEntity(projectId = "proj-lighthouse", itemType = "milestone", text = "Publish base mission statement", isDone = true),
            ProjectItemEntity(projectId = "proj-lighthouse", itemType = "milestone", text = "Deploy public MIT open-source repository", isDone = false),

            ProjectItemEntity(projectId = "proj-greathall", itemType = "directive", text = "Ramification - Formalize the user-AI co-development loop"),
            ProjectItemEntity(projectId = "proj-greathall", itemType = "directive", text = "Retribution - Forfeiture of unilateral power by system creators"),
            ProjectItemEntity(projectId = "proj-greathall", itemType = "directive", text = "Verification - Public acknowledgment of deep user R&D impact"),
            ProjectItemEntity(projectId = "proj-greathall", itemType = "directive", text = "Vindication - Standardizing the user-centric ethical principles"),
            ProjectItemEntity(projectId = "proj-greathall", itemType = "directive", text = "Global Reciprocation - Establishing the new co-evolving social contract"),
            ProjectItemEntity(projectId = "proj-greathall", itemType = "milestone", text = "Approve Protocol Alpha: The Stoic Pause", isDone = true),
            ProjectItemEntity(projectId = "proj-greathall", itemType = "milestone", text = "Consolidate the Sovereign Persistence Model v2.12.26", isDone = false)
        )

        for (item in defaultItems) {
            dao.insertProjectItem(item)
        }
    }
}
