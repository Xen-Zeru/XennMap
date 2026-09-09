package com.xennmap.data.maps.bathymetry.download

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.xennmap.data.maps.bathymetry.model.BathymetryRegion
import com.xennmap.data.maps.bathymetry.source.BathymetryBinaryGridReader
import com.xennmap.data.maps.bathymetry.source.BathymetryMbtilesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import androidx.work.workDataOf
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * WorkManager worker for downloading bathymetry data.
 * Handles both MBTiles and binary grid downloads with progress reporting.
 * Implements real ZIP extraction using ZipFile (not ZipInputStream) to avoid
 * Kotlin type inference issues with ZipInputStream.nextEntry().
 * 
 * If download URLs are not configured (not production), fails gracefully
 * with "Bathymetry package unavailable" message.
 */
class BathymetryDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val mbtilesProvider = BathymetryMbtilesProvider(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val regionId = inputData.getString(KEY_REGION_ID) ?: return@withContext Result.failure()
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL)
        val gridDownloadUrl = inputData.getString(KEY_GRID_DOWNLOAD_URL)
        val wifiOnly = inputData.getBoolean(KEY_WIFI_ONLY, false)

        val region = BathymetryRegion.values().firstOrNull { it.id == regionId }
            ?: return@withContext Result.failure()

        // Check if URLs are configured for production
        if (downloadUrl.isNullOrBlank() || gridDownloadUrl.isNullOrBlank()) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Bathymetry package unavailable. Production download URLs not configured.")
            )
        }

        if (wifiOnly && !isWifiConnected()) {
            return@withContext Result.retry()
        }

        val mbtilesDir = File(applicationContext.filesDir, "bathymetry/mbtiles")
        val gridDir = File(applicationContext.filesDir, "bathymetry/gebco2024_tiles")
        mbtilesDir.mkdirs()
        gridDir.mkdirs()

        val mbtilesFile = File(mbtilesDir, "$regionId.mbtiles")
        val gridZipFile = File(gridDir, "$regionId.zip")

        try {
            // Phase 1: Download MBTiles (0-50%)
            setForegroundAsync(createForegroundInfo(WorkProgress(0, 0, "Downloading bathymetry tiles...")))
            downloadFile(downloadUrl, mbtilesFile) { downloaded, total ->
                val progress = if (total > 0) (downloaded * 50 / total).toInt() else 0
                setForegroundAsync(createForegroundInfo(WorkProgress(progress, downloaded, "Downloading tiles: ${formatBytes(downloaded)}/${formatBytes(total)}")))
            }

            // Phase 2: Download binary grid ZIP (50-90%)
            gridDownloadUrl?.let { gridUrl ->
                setForegroundAsync(createForegroundInfo(WorkProgress(50, mbtilesFile.length(), "Downloading query grid...")))
                downloadFile(gridUrl, gridZipFile) { downloaded, total ->
                    val progress = 50 + (if (total > 0) (downloaded * 40 / total).toInt() else 0)
                    setForegroundAsync(createForegroundInfo(WorkProgress(progress, mbtilesFile.length() + downloaded, "Downloading grid: ${formatBytes(downloaded)}/${formatBytes(total)}")))
                }

                // Phase 3: Extract ZIP using ZipFile (90-95%)
                setForegroundAsync(createForegroundInfo(WorkProgress(90, mbtilesFile.length() + gridZipFile.length(), "Extracting query grid...")))
                extractZipUsingZipFile(gridZipFile, gridDir, regionId) { extracted, total ->
                    val progress = 90 + (if (total > 0) (extracted * 5 / total).toInt() else 0)
                    setForegroundAsync(createForegroundInfo(WorkProgress(progress, mbtilesFile.length() + gridZipFile.length(), "Extracting: $extracted/$total files")))
                }

                // Clean up ZIP after successful extraction
                gridZipFile.delete()
            }

            // Phase 4: Verify (95-100%)
            setForegroundAsync(createForegroundInfo(WorkProgress(95, mbtilesFile.length(), "Verifying downloaded data...")))
            if (!verifyDownloads(mbtilesFile, region)) {
                return@withContext Result.failure()
            }

            setForegroundAsync(createForegroundInfo(WorkProgress(100, mbtilesFile.length(), "Complete")))
            return@withContext Result.success(
                workDataOf(
                    KEY_REGION_ID to regionId,
                    KEY_SIZE_BYTES to mbtilesFile.length(),
                )
            )
        } catch (e: Exception) {
            // Clean up partial files on failure
            mbtilesFile.delete()
            gridZipFile.delete()
            return@withContext Result.failure()
        }
    }

    private suspend fun downloadFile(urlString: String, destination: File, progress: (Long, Long) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection()
        connection.connect()
        val fileSize = connection.contentLengthLong
        val input = connection.getInputStream()
        val output = FileOutputStream(destination)

        val buffer = ByteArray(8192)
        var downloaded = 0L
        var lastReported = 0L

        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                downloaded += read
                if (downloaded - lastReported > 1024 * 1024) {
                    progress(downloaded, fileSize)
                    lastReported = downloaded
                }
            }
            progress(downloaded, fileSize)
        } finally {
            input.close()
            output.close()
        }
    }

    /**
     * Extract ZIP file using ZipFile (not ZipInputStream) to avoid Kotlin type inference issues.
     * Uses Enumeration to iterate entries, which avoids the generic type inference issues
     * with ZipInputStream.nextEntry().
     */
    private fun extractZipUsingZipFile(zipFile: File, destDir: File, regionId: String, progress: (Int, Int) -> Unit) {
        if (!zipFile.exists()) {
            throw IOException("ZIP file not found: ${zipFile.absolutePath}")
        }

        val zipFileReader = ZipFile(zipFile)
        val entries = zipFileReader.entries()
        val entryList = mutableListOf<ZipEntry>()
        while (entries.hasMoreElements()) {
            entryList.add(entries.nextElement())
        }

        val entryCount = entryList.size
        if (entryCount == 0) {
            zipFileReader.close()
            throw IOException("ZIP file is empty: ${zipFile.absolutePath}")
        }

        var processedCount = 0
        val buffer = ByteArray(8192)

        try {
            for (entry in entryList) {
                if (isStopped) {
                    throw InterruptedException("Download cancelled")
                }

                val entryName = entry.name
                if (entryName == null || entryName.isEmpty()) {
                    continue
                }

                // Security: prevent directory traversal
                val canonicalDestDir = destDir.canonicalPath
                val targetFile = File(destDir, entryName).canonicalFile
                if (!targetFile.path.startsWith(canonicalDestDir + File.separator) && targetFile.path != canonicalDestDir) {
                    throw IOException("Invalid ZIP entry (directory traversal): $entryName")
                }

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()

                    val input = zipFileReader.getInputStream(entry)
                    val output = FileOutputStream(targetFile)
                    try {
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    } finally {
                        output.close()
                        input.close()
                    }
                }

                processedCount++
                progress(processedCount, entryCount)
            }

            if (processedCount == 0) {
                throw IOException("No files extracted from ZIP: ${zipFile.absolutePath}")
            }

        } finally {
            zipFileReader.close()
        }
    }

    private fun verifyDownloads(mbtilesFile: File, region: BathymetryRegion): Boolean {
        if (!mbtilesFile.exists() || mbtilesFile.length() < 1024 * 1024) {
            return false
        }

        val gridDir = File(applicationContext.filesDir, "bathymetry/gebco2024_tiles")
        val expectedTiles = BathymetryRegion.values().first { it.id == region.id }
            .let { region ->
                val latCount = (ceil(region.north).toInt() - floor(region.south).toInt())
                val lonCount = (ceil(region.east).toInt() - floor(region.west).toInt())
                latCount * lonCount
            }.toInt()

        val actualTiles = gridDir.listFiles()?.count { it.extension == "bin" } ?: 0
        return actualTiles >= expectedTiles * 80 / 100
    }

    private fun isWifiConnected(): Boolean = true

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    private fun createForegroundInfo(progress: WorkProgress): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, "bathymetry_download")
            .setContentTitle("Downloading Bathymetry")
            .setContentText(progress.status)
            .setProgress(100, progress.percent, false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        return ForegroundInfo(1, notification)
    }

    companion object {
        const val KEY_REGION_ID = "region_id"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_GRID_DOWNLOAD_URL = "grid_download_url"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_SIZE_BYTES = "size_bytes"
        const val KEY_ERROR_MESSAGE = "error_message"
    }
}

data class WorkProgress(
    val percent: Int,
    val bytesDownloaded: Long,
    val status: String,
)