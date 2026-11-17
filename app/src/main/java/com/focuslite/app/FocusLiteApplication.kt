package com.focuslite.app

import android.app.Application
import com.focuslite.app.data.local.datastore.FocusPreferencesManager
import com.focuslite.app.data.local.db.FocusDatabase
import com.focuslite.app.data.repository.FocusRepository

class FocusLiteApplication : Application() {
    lateinit var repository: FocusRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = FocusDatabase.getInstance(this)
        val prefsManager = FocusPreferencesManager(this)
        repository = FocusRepository(prefsManager, database.blockEventDao())
    }
}
