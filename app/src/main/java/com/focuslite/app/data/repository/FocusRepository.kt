package com.focuslite.app.data.repository

import androidx.datastore.preferences.core.Preferences
import com.focuslite.app.data.local.datastore.FocusPreferences
import com.focuslite.app.data.local.datastore.FocusPreferencesManager
import com.focuslite.app.data.local.db.BlockEventDao
import com.focuslite.app.data.local.db.BlockEventEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class FocusRepository(
    private val prefsManager: FocusPreferencesManager,
    private val blockEventDao: BlockEventDao
) {
    val preferencesFlow: Flow<FocusPreferences> = prefsManager.preferencesFlow
    val totalBlockedFlow: Flow<Int> = blockEventDao.getTotalBlockedCount()

    fun getTodayBlockedFlow(): Flow<Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return blockEventDao.getBlockedCountSince(calendar.timeInMillis)
    }

    suspend fun setToggle(key: Preferences.Key<Boolean>, enabled: Boolean) {
        prefsManager.updateBoolean(key, enabled)
    }

    suspend fun activatePauseForMinutes(minutes: Int) {
        val targetTime = if (minutes <= 0) 0L else System.currentTimeMillis() + (minutes * 60_000L)
        prefsManager.setPauseUntil(targetTime)
    }

    suspend fun recordBlockEvent(packageName: String, reason: String, timestamp: Long) {
        blockEventDao.insertEvent(
            BlockEventEntity(packageName = packageName, reason = reason, timestamp = timestamp)
        )
    }
}
