package com.billpro.app.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class AppUpdateInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSize: Long,
    val expectedSha256: String?
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateState
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState
    object VerifyingChecksum : UpdateState
    data class ReadyToInstall(val apkFile: File, val info: AppUpdateInfo) : UpdateState
    data class Error(val message: String, val canRetry: Boolean = true) : UpdateState
}

object UpdateManager {

    const val GITHUB_OWNER = "ahamed-wafiq"
    const val GITHUB_REPO = "BillPro-Updates"
    const val TARGET_APK_NAME = "BillPro.apk"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }

    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".", "-").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun checkForUpdates(context: Context): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "BillPro-Android-Updater")
            }

            val responseCode = connection.responseCode
            if (responseCode == 404) {
                return@withContext Result.failure(Exception("No releases found on GitHub ($GITHUB_OWNER/$GITHUB_REPO)."))
            }
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("GitHub API returned error code $responseCode: ${connection.responseMessage}"))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseJson = JSONObject(responseBody)

            val tagName = releaseJson.optString("tag_name", "")
            val releaseName = releaseJson.optString("name", tagName)
            val releaseNotes = releaseJson.optString("body", "")
            val assets = releaseJson.optJSONArray("assets")

            var apkUrl: String? = null
            var apkSize = 0L
            var checksumUrl: String? = null

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.equals(TARGET_APK_NAME, ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", 0L)
                    } else if (name.endsWith(".sha256", ignoreCase = true) || name.equals("sha256.txt", ignoreCase = true)) {
                        checksumUrl = asset.optString("browser_download_url")
                    }
                }
            }

            if (apkUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Release $tagName exists, but no asset named '$TARGET_APK_NAME' was found."))
            }

            val currentVersion = getCurrentVersionName(context)
            if (!isNewerVersion(tagName, currentVersion)) {
                return@withContext Result.success(null)
            }

            // Extract published SHA-256
            var expectedSha256: String? = null
            if (!checksumUrl.isNullOrBlank()) {
                try {
                    val shaConn = (URL(checksumUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "BillPro-Android-Updater")
                    }
                    if (shaConn.responseCode in 200..299) {
                        val shaText = shaConn.inputStream.bufferedReader().use { it.readText() }
                        expectedSha256 = extractSha256FromText(shaText)
                    }
                } catch (_: Exception) {
                    // Fall back to notes
                }
            }

            if (expectedSha256 == null) {
                expectedSha256 = extractSha256FromText(releaseNotes)
            }

            Result.success(
                AppUpdateInfo(
                    tagName = tagName,
                    releaseName = releaseName.ifBlank { "Version $tagName" },
                    releaseNotes = releaseNotes.ifBlank { "No release notes provided." },
                    apkDownloadUrl = apkUrl,
                    apkSize = apkSize,
                    expectedSha256 = expectedSha256
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractSha256FromText(text: String): String? {
        val regex = Regex("\\b([a-fA-F0-9]{64})\\b")
        return regex.find(text)?.value?.lowercase(Locale.US)
    }

    suspend fun downloadApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        var connection: HttpURLConnection? = null
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, TARGET_APK_NAME)

        try {
            if (targetFile.exists()) {
                targetFile.delete()
            }

            var downloadUrl = info.apkDownloadUrl
            var redirectCount = 0
            while (redirectCount < 5) {
                val url = URL(downloadUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "BillPro-Android-Updater")

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl != null) {
                        downloadUrl = newUrl
                        redirectCount++
                        continue
                    }
                }
                break
            }

            val totalBytes = if (connection?.contentLengthLong != null && connection.contentLengthLong > 0) {
                connection.contentLengthLong
            } else {
                info.apkSize
            }

            input = connection?.inputStream ?: throw Exception("Failed to open connection stream")
            output = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                val progress = if (totalBytes > 0) {
                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                onProgress(progress, downloadedBytes, totalBytes)
            }
            output.flush()

            // Verify SHA-256 if provided
            if (!info.expectedSha256.isNullOrBlank()) {
                val computedSha256 = calculateSha256(targetFile)
                if (!computedSha256.equals(info.expectedSha256, ignoreCase = true)) {
                    targetFile.delete()
                    return@withContext Result.failure(
                        SecurityException(
                            "SHA-256 verification failed!\nExpected: ${info.expectedSha256}\nCalculated: $computedSha256\nThe update file was deleted for security."
                        )
                    )
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            Result.failure(e)
        } finally {
            try { output?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { isStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (isStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}

