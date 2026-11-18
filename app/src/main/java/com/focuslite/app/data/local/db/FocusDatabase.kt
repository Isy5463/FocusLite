package com.focuslite.app.data.local.db
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Entity(tableName = "block_events") data class BlockEventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String, val reason: String, val timestamp: Long)
@Dao interface BlockEventDao {
    @Insert suspend fun insertEvent(event: BlockEventEntity)
    @Query("SELECT COUNT(*) FROM block_events WHERE timestamp >= :startOfDayTimestamp") fun getBlockedCountSince(startOfDayTimestamp: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM block_events") fun getTotalBlockedCount(): Flow<Int>
}
@Database(entities = [BlockEventEntity::class], version = 1, exportSchema = false) abstract class FocusDatabase : RoomDatabase() {
    abstract fun blockEventDao(): BlockEventDao
    companion object {
        @Volatile private var INSTANCE: FocusDatabase? = null
        fun getInstance(context: Context): FocusDatabase = INSTANCE ?: synchronized(this) { Room.databaseBuilder(context.applicationContext, FocusDatabase::class.java, "focus_lite_statistics.db").fallbackToDestructiveMigration().build().also { INSTANCE = it } }
    }
}