package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileCategory: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val direction: String, // "SENT" or "RECEIVED"
    val peerDeviceName: String,
    val sha256Hash: String,
    val status: String, // "COMPLETED", "CANCELLED", "FAILED", "INTEGRITY_MISMATCH"
    val localFilePath: String?,
    val timestampMillis: Long = System.currentTimeMillis(),
    val averageSpeedBps: Long = 0L
)

@Dao
interface TransferHistoryDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestampMillis DESC")
    fun observeAllHistory(): Flow<List<TransferHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: TransferHistoryEntity): Long

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAllHistory()
}

@Database(
    entities = [TransferHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TransferHistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): TransferHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: TransferHistoryDatabase? = null

        fun getInstance(context: Context): TransferHistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TransferHistoryDatabase::class.java,
                    "wassil_transfer_history.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class TransferHistoryRepository(private val dao: TransferHistoryDao) {
    val allHistory: Flow<List<TransferHistoryEntity>> = dao.observeAllHistory()

    suspend fun recordTransfer(entry: TransferHistoryEntity): Long {
        return dao.insertHistory(entry)
    }

    suspend fun removeEntry(id: Long) {
        dao.deleteHistoryById(id)
    }

    suspend fun clearAll() {
        dao.clearAllHistory()
    }
}
