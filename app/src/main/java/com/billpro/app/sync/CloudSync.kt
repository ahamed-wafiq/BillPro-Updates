package com.billpro.app.sync

/**
 * Backend-neutral seam for optional cloud sync.
 *
 * Production implementation should use authenticated APIs, TLS, per-user access
 * control, idempotent writes, conflict resolution, and retry/backoff. Never put
 * service-account secrets or private API keys in the Android app.
 */
interface CloudSync {
    suspend fun pushPendingChanges(): SyncResult
    suspend fun pullRemoteChanges(): SyncResult
}

data class SyncResult(
    val success: Boolean,
    val syncedRecords: Int = 0,
    val message: String = ""
)
