package com.billpro.app.auth

import android.content.Context
import com.billpro.app.data.BillProDao
import com.billpro.app.data.BusinessEntity
import com.billpro.app.data.StoreProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class AuthResult {
    data class Success(val session: UserSession) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthService(
    private val context: Context,
    private val dao: BillProDao,
    private val sessionManager: SessionManager = SessionManager.getInstance(context)
) {
    private val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    suspend fun register(
        ownerName: String,
        businessName: String,
        email: String,
        phone: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanOwner = ownerName.trim()
        val cleanBusiness = businessName.trim()
        val cleanPhone = phone.trim()

        if (cleanOwner.isBlank()) return@withContext AuthResult.Error("Owner name cannot be empty.")
        if (cleanBusiness.isBlank()) return@withContext AuthResult.Error("Business name cannot be empty.")
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters.")
        }

        // Generate unique business ID: e.g. BIZ_20261108_4821
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val randomSuffix = (1000..9999).random()
        val generatedBusinessId = "BIZ_${datePart}_$randomSuffix"

        var uid = UUID.randomUUID().toString()

        // 1. Firebase Authentication Registration
        if (isFirebaseAvailable) {
            try {
                val auth = FirebaseAuth.getInstance()
                val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                uid = result.user?.uid ?: uid
            } catch (e: Exception) {
                // If email already in use or Firebase throws, report clearly
                val msg = e.localizedMessage ?: "Registration failed."
                if (msg.contains("already in use", ignoreCase = true)) {
                    return@withContext AuthResult.Error("This email is already registered. Please log in.")
                }
                // If offline or network issue, continue with local creation fallback
            }
        }

        // 2. Cloud Firestore Business Profile Registration
        if (isFirebaseAvailable) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val bizMap = hashMapOf<String, Any>(
                    "businessId" to generatedBusinessId,
                    "ownerUid" to uid,
                    "ownerEmail" to cleanEmail,
                    "ownerName" to cleanOwner,
                    "businessName" to cleanBusiness,
                    "phone" to cleanPhone,
                    "createdAtEpochMs" to System.currentTimeMillis(),
                    "updatedAtEpochMs" to System.currentTimeMillis()
                )
                firestore.collection("businesses").document(generatedBusinessId).set(bizMap).await()

                val userMap = hashMapOf<String, Any>(
                    "uid" to uid,
                    "email" to cleanEmail,
                    "name" to cleanOwner,
                    "businessId" to generatedBusinessId,
                    "phone" to cleanPhone,
                    "createdAtEpochMs" to System.currentTimeMillis()
                )
                firestore.collection("users").document(uid).set(userMap).await()
            } catch (_: Exception) {
                // Offline fallback - will be synced by BusinessSyncManager when connectivity returns
            }
        }

        // 3. Save Business Entity to local Room database
        val businessEntity = BusinessEntity(
            businessId = generatedBusinessId,
            ownerUid = uid,
            ownerEmail = cleanEmail,
            ownerName = cleanOwner,
            businessName = cleanBusiness,
            phone = cleanPhone,
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.upsertBusiness(businessEntity)

        // 4. Initialize Store Profile for this business
        StoreProfile.save(
            context = context,
            profile = StoreProfile(
                storeName = cleanBusiness,
                phone = cleanPhone,
                email = cleanEmail
            ),
            businessId = generatedBusinessId
        )

        // 5. Save session
        sessionManager.saveSession(
            businessId = generatedBusinessId,
            ownerUid = uid,
            ownerEmail = cleanEmail,
            ownerName = cleanOwner,
            businessName = cleanBusiness
        )

        val session = UserSession(
            businessId = generatedBusinessId,
            ownerUid = uid,
            ownerEmail = cleanEmail,
            ownerName = cleanOwner,
            businessName = cleanBusiness
        )
        AuthResult.Success(session)
    }

    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext AuthResult.Error("Please enter a valid email address.")
        }
        if (password.isBlank()) {
            return@withContext AuthResult.Error("Password cannot be blank.")
        }

        var uid: String? = null
        var resolvedBusinessId: String? = null
        var ownerName: String = ""
        var businessName: String = ""

        // 1. Attempt Firebase Authentication
        var firebaseAuthSuccess = false
        if (isFirebaseAvailable) {
            try {
                val auth = FirebaseAuth.getInstance()
                val authResult = auth.signInWithEmailAndPassword(cleanEmail, password).await()
                uid = authResult.user?.uid
                firebaseAuthSuccess = true
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Authentication failed."
                if (msg.contains("user-not-found", ignoreCase = true) ||
                    msg.contains("wrong-password", ignoreCase = true) ||
                    msg.contains("invalid-credential", ignoreCase = true)) {
                    // Check if exists locally before throwing
                    val localBiz = dao.getBusinessByOwnerEmail(cleanEmail)
                    if (localBiz == null) {
                        return@withContext AuthResult.Error("Invalid email or password.")
                    }
                }
            }
        }

        // 2. Fetch business metadata from Firestore if online
        if (firebaseAuthSuccess && uid != null) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val userDoc = firestore.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    resolvedBusinessId = userDoc.getString("businessId")
                    ownerName = userDoc.getString("name") ?: ""
                }

                if (resolvedBusinessId != null) {
                    val bizDoc = firestore.collection("businesses").document(resolvedBusinessId).get().await()
                    if (bizDoc.exists()) {
                        businessName = bizDoc.getString("businessName") ?: ""
                        val phone = bizDoc.getString("phone") ?: ""
                        val gstin = bizDoc.getString("gstin") ?: ""
                        val address = bizDoc.getString("address") ?: ""

                        // Cache in Room
                        dao.upsertBusiness(
                            BusinessEntity(
                                businessId = resolvedBusinessId,
                                ownerUid = uid,
                                ownerEmail = cleanEmail,
                                ownerName = ownerName.ifBlank { "Owner" },
                                businessName = businessName.ifBlank { "Business Store" },
                                phone = phone,
                                gstin = gstin,
                                address = address,
                                updatedAtEpochMs = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Offline fallback
            }
        }

        // 3. Fallback to Local Room Data (Offline capability requirement)
        if (resolvedBusinessId == null) {
            val localBiz = dao.getBusinessByOwnerEmail(cleanEmail)
                ?: if (uid != null) dao.getBusinessByOwnerUid(uid) else null

            if (localBiz != null) {
                resolvedBusinessId = localBiz.businessId
                ownerName = localBiz.ownerName
                businessName = localBiz.businessName
                uid = localBiz.ownerUid
            }
        }

        if (resolvedBusinessId == null) {
            return@withContext AuthResult.Error("No business found for this account. Please register.")
        }

        val finalUid = uid ?: UUID.randomUUID().toString()
        val finalOwnerName = ownerName.ifBlank { "Owner" }
        val finalBusinessName = businessName.ifBlank { "BillPro Business" }

        sessionManager.saveSession(
            businessId = resolvedBusinessId,
            ownerUid = finalUid,
            ownerEmail = cleanEmail,
            ownerName = finalOwnerName,
            businessName = finalBusinessName
        )

        val session = UserSession(
            businessId = resolvedBusinessId,
            ownerUid = finalUid,
            ownerEmail = cleanEmail,
            ownerName = finalOwnerName,
            businessName = finalBusinessName
        )
        AuthResult.Success(session)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext Result.failure(Exception("Please enter a valid email address."))
        }

        if (isFirebaseAvailable) {
            try {
                val auth = FirebaseAuth.getInstance()
                auth.sendPasswordResetEmail(cleanEmail).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception(e.localizedMessage ?: "Password reset failed."))
            }
        } else {
            // Offline fallback confirmation
            Result.success(Unit)
        }
    }

    fun logout() {
        if (isFirebaseAvailable) {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (_: Exception) { }
        }
        sessionManager.clearSession()
    }
}

