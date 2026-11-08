package com.billpro.app.data

import android.content.Context
import android.content.SharedPreferences

data class StoreProfile(
    val storeName: String = "BillPro Store",
    val phone: String = "+91 98765 43210",
    val email: String = "support@billpro.local",
    val address: String = "123 Business Avenue, Commercial Hub",
    val gstin: String = "33ABCDE1234F1Z5",
    val termsAndConditions: String = "1. Goods once sold will not be taken back.\n2. Invoices due within terms."
) {
    companion object {
        private const val BASE_PREF_NAME = "billpro_store_profile"
        private const val KEY_NAME = "store_name"
        private const val KEY_PHONE = "store_phone"
        private const val KEY_EMAIL = "store_email"
        private const val KEY_ADDRESS = "store_address"
        private const val KEY_GSTIN = "store_gstin"
        private const val KEY_TERMS = "store_terms"

        private fun getPrefName(businessId: String): String {
            return if (businessId == "default_biz" || businessId.isBlank()) {
                BASE_PREF_NAME
            } else {
                "${BASE_PREF_NAME}_$businessId"
            }
        }

        fun load(context: Context, businessId: String = "default_biz"): StoreProfile {
            val prefName = getPrefName(businessId)
            val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)

            // If business-specific prefs are empty, check if legacy base prefs have data
            val defaultStoreName = sp.getString(KEY_NAME, null)
            if (defaultStoreName == null && prefName != BASE_PREF_NAME) {
                val legacySp = context.getSharedPreferences(BASE_PREF_NAME, Context.MODE_PRIVATE)
                if (legacySp.contains(KEY_NAME)) {
                    return StoreProfile(
                        storeName = legacySp.getString(KEY_NAME, "BillPro Store") ?: "BillPro Store",
                        phone = legacySp.getString(KEY_PHONE, "+91 98765 43210") ?: "+91 98765 43210",
                        email = legacySp.getString(KEY_EMAIL, "support@billpro.local") ?: "support@billpro.local",
                        address = legacySp.getString(KEY_ADDRESS, "123 Business Avenue, Commercial Hub") ?: "123 Business Avenue, Commercial Hub",
                        gstin = legacySp.getString(KEY_GSTIN, "33ABCDE1234F1Z5") ?: "33ABCDE1234F1Z5",
                        termsAndConditions = legacySp.getString(KEY_TERMS, "1. Goods once sold will not be taken back.\n2. Subject to local jurisdiction.") ?: "1. Goods once sold will not be taken back.\n2. Subject to local jurisdiction."
                    )
                }
            }

            return StoreProfile(
                storeName = sp.getString(KEY_NAME, "BillPro Store") ?: "BillPro Store",
                phone = sp.getString(KEY_PHONE, "+91 98765 43210") ?: "+91 98765 43210",
                email = sp.getString(KEY_EMAIL, "support@billpro.local") ?: "support@billpro.local",
                address = sp.getString(KEY_ADDRESS, "123 Business Avenue, Commercial Hub") ?: "123 Business Avenue, Commercial Hub",
                gstin = sp.getString(KEY_GSTIN, "33ABCDE1234F1Z5") ?: "33ABCDE1234F1Z5",
                termsAndConditions = sp.getString(KEY_TERMS, "1. Goods once sold will not be taken back.\n2. Subject to local jurisdiction.") ?: "1. Goods once sold will not be taken back.\n2. Subject to local jurisdiction."
            )
        }

        fun save(context: Context, profile: StoreProfile, businessId: String = "default_biz") {
            val sp = context.getSharedPreferences(getPrefName(businessId), Context.MODE_PRIVATE)
            sp.edit()
                .putString(KEY_NAME, profile.storeName)
                .putString(KEY_PHONE, profile.phone)
                .putString(KEY_EMAIL, profile.email)
                .putString(KEY_ADDRESS, profile.address)
                .putString(KEY_GSTIN, profile.gstin)
                .putString(KEY_TERMS, profile.termsAndConditions)
                .apply()
        }
    }
}
