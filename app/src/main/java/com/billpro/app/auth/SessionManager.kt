package com.billpro.app.auth

import android.content.Context
import android.content.SharedPreferences

data class UserSession(
    val businessId: String,
    val ownerUid: String,
    val ownerEmail: String,
    val ownerName: String,
    val businessName: String,
    val loginEpochMs: Long = System.currentTimeMillis()
)

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "billpro_user_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_BUSINESS_ID = "business_id"
        private const val KEY_OWNER_UID = "owner_uid"
        private const val KEY_OWNER_EMAIL = "owner_email"
        private const val KEY_OWNER_NAME = "owner_name"
        private const val KEY_BUSINESS_NAME = "business_name"
        private const val KEY_LOGIN_TIME = "login_epoch_ms"

        @Volatile private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getActiveBusinessId(): String = prefs.getString(KEY_BUSINESS_ID, "default_biz") ?: "default_biz"

    fun getSession(): UserSession? {
        if (!isLoggedIn()) return null
        val businessId = prefs.getString(KEY_BUSINESS_ID, null) ?: return null
        val ownerUid = prefs.getString(KEY_OWNER_UID, "") ?: ""
        val ownerEmail = prefs.getString(KEY_OWNER_EMAIL, "") ?: ""
        val ownerName = prefs.getString(KEY_OWNER_NAME, "") ?: ""
        val businessName = prefs.getString(KEY_BUSINESS_NAME, "") ?: ""
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0L)
        return UserSession(
            businessId = businessId,
            ownerUid = ownerUid,
            ownerEmail = ownerEmail,
            ownerName = ownerName,
            businessName = businessName,
            loginEpochMs = loginTime
        )
    }

    fun saveSession(
        businessId: String,
        ownerUid: String,
        ownerEmail: String,
        ownerName: String,
        businessName: String
    ) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_BUSINESS_ID, businessId)
            .putString(KEY_OWNER_UID, ownerUid)
            .putString(KEY_OWNER_EMAIL, ownerEmail)
            .putString(KEY_OWNER_NAME, ownerName)
            .putString(KEY_BUSINESS_NAME, businessName)
            .putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}

