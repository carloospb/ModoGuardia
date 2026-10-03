package es.carlospb.modoguardia.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import es.carlospb.modoguardia.domain.HistoryLog
import es.carlospb.modoguardia.domain.HistoryType
import kotlinx.coroutines.flow.Flow

/** Una línea del historial. Solo lo necesario: hora, tipo, título y un detalle corto. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val title: String,
    val detail: String?,
) {
    val historyType: HistoryType
        get() = HistoryType.entries.firstOrNull { it.name == type } ?: HistoryType.WARNING
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): HistoryDatabase =
            Room.databaseBuilder(context, HistoryDatabase::class.java, "historial.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

class HistoryRepository(private val dao: HistoryDao) : HistoryLog {
    fun observe(limit: Int = 500): Flow<List<HistoryEntity>> = dao.observe(limit)

    override suspend fun log(type: HistoryType, title: String, detail: String?, time: Long) {
        dao.insert(HistoryEntity(timestamp = time, type = type.name, title = title, detail = detail))
        dao.trim(MAX_ENTRIES)
    }

    suspend fun clear() = dao.clear()

    private companion object {
        const val MAX_ENTRIES = 1000
    }
}
