package com.xennmap.data.maps.bathymetry.source

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Provisions bundled GEBCO bathymetry assets from APK to internal storage.
 * Runs automatically on first launch - idempotent and safe to run multiple times.
 * Exposes provisioning state via StateFlow for reactive UI updates.
 */
class BathymetryAssetProvisioner(private val context: Context) {

    companion object {
        private const val TAG = "XennMap/Bathymetry"
        private const val MBTILES_ASSET = "bathymetry/mbtiles/gebco2024_ph_whole.mbtiles"
        private const val GRID_ASSET_DIR = "bathymetry/gebco2024_tiles/"
        private const val REGION_ID = "gebco2024_ph_whole"
        private const val EXPECTED_TILE_COUNT = 198
    }

    private val _isProvisioned = MutableStateFlow(false)
    val isProvisioned: kotlinx.coroutines.flow.StateFlow<Boolean> = _isProvisioned.asStateFlow()

    private var contourLabelGenerator: BathymetryContourLabelGenerator? = null

    init {
        android.util.Log.i(TAG, "BathymetryAssetProvisioner created")
        // Initialize state immediately
        _isProvisioned.value = isProvisioned()
    }

    fun setContourLabelGenerator(generator: BathymetryContourLabelGenerator) {
        contourLabelGenerator = generator
    }

    /**
     * Provision all GEBCO assets to internal storage.
     * Returns true if provisioning succeeded or assets already exist and are valid.
     * Updates isProvisioned StateFlow on completion.
     */
    suspend fun provision(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting GEBCO asset provisioning...")
        try {
            val mbtilesDir = File(context.filesDir, "bathymetry/mbtiles")
            val gridDir = File(context.filesDir, "bathymetry/gebco2024_tiles")
            mbtilesDir.mkdirs()
            gridDir.mkdirs()

            val mbtilesFile = File(mbtilesDir, "$REGION_ID.mbtiles")
            val gridProvisioned = File(gridDir, ".provisioned")

            // Check if already provisioned and valid
            if (isValid(mbtilesFile, gridDir)) {
                Log.i(TAG, "GEBCO assets already provisioned and valid")
                _isProvisioned.value = true
                return@withContext true
            }

            // Provision MBTiles
            Log.i(TAG, "Provisioning GEBCO MBTiles...")
            if (!provisionMbtiles(mbtilesFile)) {
                Log.e(TAG, "Failed to provision MBTiles")
                return@withContext false
            }

            // Provision binary grid tiles
            Log.i(TAG, "Provisioning GEBCO binary grid tiles...")
            if (!provisionGridTiles(gridDir)) {
                Log.e(TAG, "Failed to provision binary grid tiles")
                return@withContext false
            }

            // Mark grid as provisioned
            gridProvisioned.writeText("")

            Log.i(TAG, "GEBCO assets provisioned successfully")
            _isProvisioned.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Provisioning failed with exception", e)
            false
        }
    }

    private fun isValid(mbtilesFile: File, gridDir: File): Boolean {
        if (!mbtilesFile.exists()) return false
        // Just check that the file has reasonable size (> 10MB) and grid tiles exist
        if (mbtilesFile.length() < 10_000_000) {
            Log.w(TAG, "MBTiles size too small: ${mbtilesFile.length()}")
            return false
        }
        val files = gridDir.listFiles()
        if (files == null) return false
        val gridFiles = files.filter { it.name.endsWith(".bin") }
        if (gridFiles.size < EXPECTED_TILE_COUNT) {
            Log.w(TAG, "Grid tile count mismatch: ${gridFiles.size} < $EXPECTED_TILE_COUNT")
            return false
        }
        return true
    }

    private fun provisionMbtiles(destFile: File): Boolean {
        return try {
            context.assets.open(MBTILES_ASSET).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, 8192)
                }
            }
            destFile.length() > 10_000_000
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy MBTiles", e)
            destFile.delete()
            false
        }
    }

    private fun provisionGridTiles(gridDir: File): Boolean {
        return try {
            Log.d(TAG, "Listing assets in $GRID_ASSET_DIR")
            val assetFiles = context.assets.list(GRID_ASSET_DIR) ?: return false
            Log.d(TAG, "Found ${assetFiles.size} assets in grid directory")
            var copied = 0
            for (assetFile in assetFiles) {
                if (!assetFile.endsWith(".bin")) continue
                Log.d(TAG, "Copying grid tile: $assetFile")
                val input = context.assets.open("$GRID_ASSET_DIR$assetFile")
                val outputFile = File(gridDir, assetFile)
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output, 8192)
                }
                input.close()
                copied++
            }
            Log.i(TAG, "Copied $copied grid tiles")
            copied >= EXPECTED_TILE_COUNT
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy grid tiles", e)
            false
        }
    }

    /**
     * Check if GEBCO assets are already provisioned and valid.
     */
    fun isProvisioned(): Boolean = isValid(
        File(context.filesDir, "bathymetry/mbtiles/$REGION_ID.mbtiles"),
        File(context.filesDir, "bathymetry/gebco2024_tiles")
    )

    /**
     * Blocking provision for use in non-coroutine contexts (e.g., Application.onCreate).
     */
    fun provisionBlocking(): Boolean {
        return try {
            Log.i(TAG, "Starting GEBCO asset provisioning (blocking)...")
            val mbtilesDir = File(context.filesDir, "bathymetry/mbtiles")
            val gridDir = File(context.filesDir, "bathymetry/gebco2024_tiles")
            mbtilesDir.mkdirs()
            gridDir.mkdirs()

            val mbtilesFile = File(mbtilesDir, "$REGION_ID.mbtiles")
            val gridProvisioned = File(gridDir, ".provisioned")

            // Check if already provisioned and valid
            if (isValid(mbtilesFile, gridDir)) {
                Log.i(TAG, "GEBCO assets already provisioned and valid")
                return true
            }

            // Provision MBTiles
            Log.i(TAG, "Provisioning GEBCO MBTiles...")
            if (!provisionMbtiles(mbtilesFile)) {
                Log.e(TAG, "Failed to provision MBTiles")
                return false
            }

            // Provision binary grid tiles
            Log.i(TAG, "Provisioning GEBCO binary grid tiles...")
            if (!provisionGridTiles(gridDir)) {
                Log.e(TAG, "Failed to provision binary grid tiles")
                return false
            }

            // Mark grid as provisioned
            gridProvisioned.writeText("")

            Log.i(TAG, "GEBCO assets provisioned successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Provisioning failed with exception", e)
            false
        }
    }
}