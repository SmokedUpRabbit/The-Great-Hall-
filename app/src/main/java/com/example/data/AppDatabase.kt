package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// -------------------------------------------------------------
// ENTITIES
// -------------------------------------------------------------

@Entity(tableName = "indexed_files")
data class IndexedFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val size: Long,
    val type: String,
    val content: String,
    val tags: String, // Comma-separated tags
    val added: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_logs")
data class HistoryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "upload", "search", "build", "canvas", "project"
    val title: String,
    val desc: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "builder_notes")
data class BuilderNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val type: String, // "code", "note"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val status: String, // "active", "paused", "concept", "complete"
    val mission: String,
    val structure: String,
    val notes: String = ""
)

@Entity(tableName = "project_items")
data class ProjectItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val itemType: String, // "goal", "milestone", "directive", "concept"
    val text: String,
    val isDone: Boolean = false
)

// -------------------------------------------------------------
// DAOS
// -------------------------------------------------------------

@Dao
interface GreatHallDao {

    // --- Files ---
    @Query("SELECT * FROM indexed_files ORDER BY added DESC")
    fun getAllFiles(): Flow<List<IndexedFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: IndexedFile): Long

    @Query("DELETE FROM indexed_files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("DELETE FROM indexed_files")
    suspend fun clearAllFiles()

    // --- History ---
    @Query("SELECT * FROM history_logs ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(log: HistoryLog)

    @Query("DELETE FROM history_logs")
    suspend fun clearAllHistory()

    // --- Builder Notes ---
    @Query("SELECT * FROM builder_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<BuilderNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: BuilderNote): Long

    @Query("DELETE FROM builder_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    // --- Projects ---
    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    // --- Project Items ---
    @Query("SELECT * FROM project_items WHERE projectId = :projectId")
    fun getItemsForProject(projectId: String): Flow<List<ProjectItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectItem(item: ProjectItemEntity): Long

    @Query("DELETE FROM project_items WHERE id = :id")
    suspend fun deleteProjectItemById(id: Long)

    @Query("UPDATE project_items SET isDone = :isDone WHERE id = :id")
    suspend fun updateProjectItemStatus(id: Long, isDone: Boolean)
}

// -------------------------------------------------------------
// DATABASE
// -------------------------------------------------------------

@Database(
    entities = [
        IndexedFile::class,
        HistoryLog::class,
        BuilderNote::class,
        ProjectEntity::class,
        ProjectItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val dao: GreatHallDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "great_hall_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
