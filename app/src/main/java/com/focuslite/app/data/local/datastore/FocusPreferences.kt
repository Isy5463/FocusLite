package com.focuslite.app.data.local.datastore

data class FocusPreferences(
    val igBlockReels: Boolean = true,
    val igBlockExplore: Boolean = true,
    val igAllowSingleReelFromDm: Boolean = true,
    val ytBlockShorts: Boolean = true,
    val ttBlockForYou: Boolean = true,
    val strictFrictionMode: Boolean = true,
    val pauseUntilTimestamp: Long = 0L
)
