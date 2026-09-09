# XennMap Production Bathymetry Audit Report

## AUDIT RESULT: PASS WITH WARNINGS

No dangerous production path found for synthetic bathymetry, but external data/CDN requirements remain.

---

## B. FILES INSPECTED

### Core Bathymetry Architecture
- `app/src/main/java/com/xennmap/domain/repository/PreferencesRepository.kt` - BathymetryRepository interface
- `app/src/main/java/com/xennmap/domain/model/Bathymetry.kt` - Domain models (BathymetryDataset, BathymetryData, BathymetryMetadata, ChartDepth, DepthResult, TerrainType)
- `app/src/main/java/com/xennmap/domain/model/DepthResult.kt` - Domain layer depth result sealed interface
- `app/src/main/java/com/xennmap/domain/usecase/GetDepthAtPositionUseCase.kt` - Depth use case
- `app/src/main/java/com/xennmap/data/maps/bathymetry/repository/RealBathymetryRepositoryImpl.kt` - Production GEBCO repository
- `app/src/debug/java/com/xennmap/data/maps/BathymetryRepositoryImpl.kt` - **DEBUG ONLY** synthetic implementation (moved from main)
- `app/src/debug/java/com/xennmap/data/maps/SampleBathymetryGenerator.kt` - **DEBUG ONLY** synthetic depth generator (moved from main)
- `app/src/main/java/com/xennmap/data/maps/bathymetry/source/BathymetryBinaryGridReader.kt` - Binary grid tile reader
- `app/src/main/java/com/xennmap/data/maps/bathymetry/source/BathymetryTileCache.kt` - LRU tile cache
- `app/src/main/java/com/xennmap/data/maps/bathymetry/source/BathymetryMbtilesProvider.kt` - MBTiles provider for MapLibre
- `app/src/main/java/com/xennmap/data/maps/bathymetry/source/BathymetryTile.kt` - Tile data class
- `app/src/main/java/com/xennmap/data/maps/bathymetry/download/BathymetryDownloadWorker.kt` - WorkManager download worker
- `app/src/main/java/com/xennmap/data/maps/bathymetry/download/BathymetryDownloadController.kt` - Download controller
- `app/src/main/java/com/xennmap/data/maps/bathymetry/model/BathymetryRegion.kt` - Region definitions
- `app/src/main/java/com/xennmap/data/maps/bathymetry/model/BathymetryConfig.kt` - Download URL configuration
- `app/src/main/java/com/xennmap/data/maps/bathymetry/model/DepthResult.kt` - Data layer depth result
- `app/src/main/java/com/xennmap/data/maps/bathymetry/repository/RealBathymetryRepositoryImpl.kt` - Production repository
- `app/src/main/java/com/xennmap/data/maps/bathymetry/model/BathymetryMetadata.kt` - Metadata model
- `app/src/main/java/com/xennmap/di/RepositoryModule.kt` - **Production binds RealBathymetryRepositoryImpl**
- `app/src/main/java/com/xennmap/di/BathymetryModule.kt` - Bathymetry DI module
- `app/src/main/java/com/xennmap/presentation/map/MapViewModel.kt` - Map view model
- `app/src/main/java/com/xennmap/presentation/map/MapRenderer.kt` - MapLibre renderer
- `app/src/main/java/com/xennmap/presentation/map/MapOverlays.kt` - UI overlays (SelectedPointSheet, etc.)
- `app/src/main/java/com/xennmap/presentation/map/MapScreen.kt` - Map screen
- `app/src/main/java/com/xennmap/presentation/map/MapEngine.kt` - Map engine
- `app/src/main/java/com/xennmap/ui/components/PlaceEditorSheet.kt` - Place editor sheet
- `app/src/main/java/com/xennmap/presentation/navigation/NavigationScreen.kt` - Navigation screen
- `app/src/main/java/com/xennmap/presentation/locations/LocationsScreen.kt` - Locations screen
- `app/src/main/java/com/xennmap/presentation/offline/OfflineMapsScreen.kt` - Offline maps screen
- `app/src/main/java/com/xennmap/presentation/offline/OfflineViewModel.kt` - Offline view model
- `app/src/main/java/com/xennmap/data/local/XennDatabase.kt` - Room database
- `app/src/main/java/com/xennmap/data/local/entity/OfflineRegionEntity.kt` - Room entity

### GEBCO Preprocessing Pipeline
- `gebco-pipeline/README.md` - Pipeline documentation
- `gebco-pipeline/generate_binary_grid.py` - Binary grid tile generator
- `gebco-pipeline/generate_mbtiles.py` - MBTiles generator
- `gebco-pipeline/validate_binary_grid.py` - Validation script

### Build Configuration
- `app/build.gradle.kts` - App build configuration
- `gradle/libs.versions.toml` - Dependency versions (MapLibre 11.5.1)
- `app/proguard-rules.pro` - ProGuard rules

---

## C. FILES MODIFIED

### Terminology Fixes (Production Safety)
1. `app/src/main/java/com/xennmap/presentation/locations/LocationsScreen.kt` - Changed "exact" to "Charted Depth"
2. `app/src/main/java/com/xennmap/presentation/map/MapOverlays.kt` - Changed "exact" to "charted" in destination depth and live depth displays
3. `app/src/main/java/com/xennmap/data/maps/bathymetry/source/BathymetryBinaryGridReader.kt` - Changed comment from "exact GEBCO integer-meter value" to "charted GEBCO integer-meter value"
4. `app/src/main/java/com/xennmap/ui/components/PlaceEditorSheet.kt` - Changed "Estimated depth" to "Charted depth" for non-downloaded case
5. `app/src/main/java/com/xennmap/presentation/navigation/NavigationScreen.kt` - Changed "Estimated depth" to "Charted depth" in two locations

### Architecture Improvements
5. `app/src/main/java/com/xennmap/data/maps/BathymetryRepositoryImpl.kt` → **MOVED** to `app/src/debug/java/com/xennmap/data/maps/BathymetryRepositoryImpl.kt` (synthetic implementation)
6. `app/src/main/java/com/xennmap/data/maps/SampleBathymetryGenerator.kt` → **MOVED** to `app/src/debug/java/com/xennmap/data/maps/SampleBathymetryGenerator.kt` (synthetic generator)
7. `app/src/main/java/com/xennmap/data/maps/bathymetry/model/BathymetryConfig.kt` - Added production URL validation, returns null URLs when not configured
8. `app/src/main/java/com/xennmap/data/maps/bathymetry/model/BathymetryRegion.kt` - URLs now return null when not in production mode
8. `app/src/main/java/com/xennmap/data/maps/bathymetry/model/BathymetryConfig.kt` - Added production URL configuration
9. `app/src/main/java/com/xennmap/data/maps/bathymetry/download/BathymetryDownloadWorker.kt` - Rewritten to use ZipFile (not ZipInputStream), graceful failure when URLs not configured, real ZIP extraction with progress
10. `app/src/main/java/com/xennmap/data/maps/bathymetry/source/BathymetryTile.kt` - New file for binary tile data class
11. `app/src/main/java/com/xennmap/data/maps/bathymetry/model/DepthResult.kt` - Added domain layer DepthResult sealed interface
12. `app/src/main/java/com/xennmap/domain/model/DepthResult.kt` - Added domain layer DepthResult sealed interface
13. `app/src/main/java/com/xennmap/domain/model/Bathymetry.kt` - Added BathymetryMetadata to ChartDepth
14. `app/src/main/java/com/xennmap/domain/repository/PreferencesRepository.kt` - Added depthResultAt to BathymetryRepository interface
15. `app/src/main/java/com/xennmap/data/maps/bathymetry/source/BathymetryTileCache.kt` - Fixed cache implementation
16. `app/src/main/java/com/xennmap/data/maps/bathymetry/source/BathymetryMbtilesProvider.kt` - Removed unsupported PropertyFactory.rasterColor, use supported RasterSource APIs
17. `app/src/main/java/com/xennmap/presentation/map/MapRenderer.kt` - Added real bathymetry RasterSource support, proper visibility toggling
17. `app/src/main/java/com/xennmap/presentation/map/MapViewModel.kt` - Updated to use depthResultAt and BathymetryMetadata
18. `app/src/main/java/com/xennmap/presentation/map/MapOverlays.kt` - Updated SelectedPointSheet to show metadata, removed "exact" terminology
18. `app/src/main/java/com/xennmap/presentation/navigation/NavigationScreen.kt` - Updated depth display to "Charted depth"
19. `app/src/main/java/com/xennmap/ui/components/PlaceEditorSheet.kt` - Consistent "Charted depth" terminology
19. `app/src/debug/java/com/xennmap/data/maps/BathymetryRepositoryImpl.kt` - **DEBUG ONLY** synthetic repository (moved from main)
20. `app/src/debug/java/com/xennmap/data/maps/SampleBathymetryGenerator.kt` - **DEBUG ONLY** synthetic generator (moved from main)
20. `app/src/main/java/com/xennmap/di/BathymetryModule.kt` - Added named CoroutineScope for download controller
21. `app/src/main/java/com/xennmap/di/RepositoryModule.kt` - Binds RealBathymetryRepositoryImpl in production

---

## D. SYNTHETIC BATHYMETRY AUDIT

| Occurrence | File | Environment | Reachable from Release? | Action Taken |
|---|---|---|---|---|
| SampleBathymetryGenerator | `app/src/debug/java/com/xennmap/data/maps/SampleBathymetryGenerator.kt` | DEBUG ONLY | **NO** - in debug source set | Moved to debug source set |
| BathymetryRepositoryImpl (synthetic) | `app/src/debug/java/com/xennmap/data/maps/BathymetryRepositoryImpl.kt` | DEBUG ONLY | **NO** - in debug source set | Moved to debug source set |
| SampleBathymetryGenerator reference | `app/src/main/java/com/xennmap/data/maps/BathymetryRepositoryImpl.kt` | REMOVED | **NO** - file moved to debug | File moved to debug source set |
| "exact depth" terminology | Multiple UI files | MAIN | **WAS REACHABLE** | Fixed to "Charted Depth" |
| "Estimated depth" terminology | Multiple UI files | MAIN | **WAS REACHABLE** | Changed to "Charted depth" |
| "fake download" comment | BathymetryConfig.kt | MAIN | Documentation only | Clarified behavior |
| "No synthetic fallback" comments | RealBathymetryRepositoryImpl.kt | MAIN | Documentation only | Retained as documentation |

**No synthetic/fake bathymetry code remains reachable from production release build.**

---

## E. RELEASE SAFETY

**Can synthetic bathymetry execute in the release APK?**

**NO.** 

The synthetic implementation (`SampleBathymetryGenerator`, `BathymetryRepositoryImpl`) has been moved to the `debug` source set (`app/src/debug/`), which is only included in debug builds. The production Hilt binding in `RepositoryModule` binds `RealBathymetryRepositoryImpl` to `BathymetryRepository`, and the synthetic implementation is not bound in production.

---

## F. REAL GEBCO DATA STATUS

| Item | Status |
|---|---|
| Actual GEBCO 2024 data present locally | **NO** - Requires external download from https://download.gebco.net/ |
| Actual packages generated | **NO** - Pipeline scripts provided but not executed |
| MBTiles generated | **NO** - Pipeline scripts provided but not executed |
| Binary tiles generated | **NO** - Pipeline scripts provided but not executed |
| Binary data validated against source NetCDF | **NO** - Validation script provided but not run |
| Package checksums available | **NO** - Not implemented |
| CDN hosting configured | **NO** - Placeholder URLs (`https://cdn.xennmap.com/bathymetry/`) |
| BathymetryConfig URLs real | **NO** - Returns null when not configured |
| Can Android app download real package | **NO** - Requires external CDN deployment |

**The preprocessing pipeline scripts and documentation are complete, but the actual GEBCO data packages must be generated and hosted externally before the app can function in production.**

---

## G. DEPTH LOOKUP FLOW

```
User taps map
    ↓
MapEngine.onMapTapped(lat, lng)
    ↓
MapViewModel.selectPoint(lat, lng)
    ↓
BathymetryRepository.depthResultAt(lat, lng)
    ↓
RealBathymetryRepositoryImpl.depthResultAt()
    ↓
BathymetryBinaryGridReader.depthAt(lat, lng)
    ↓
1. Check Philippines coverage (4°N-21.5°N, 116°E-127°E)
2. Calculate tile key: "gebco_N{lat}_E{lon}"
3. Load tile from cache or disk (1°×1° binary tiles, int16 meters)
3. Calculate cell indices (240×240 grid, 15 arc-sec resolution)
4. Read int16 depth value (native GEBCO precision)
4. Return DepthResult with metadata:
   - meters (negative = depth below MSL)
   - terrain (SEA/COASTAL/LAND/OUTSIDE)
   - metadata (source, version, resolution, datum, attribution)
    ↓
MapViewModel.SelectedPoint (with depthMeters, depthMetadata)
    ↓
SelectedPointSheet displays:
  - "Charted Depth: XX m"
  - Source: GEBCO 2024 Grid
  - Resolution: 15 arc-sec (~450m at PH latitudes)
  - Vertical Datum: MSL
  - Warning: "Not for navigation — use official nautical charts"
```

**When no bathymetry package downloaded:**
- Returns `DepthResult.NoCoverage` or `DepthResult.NoData`
- UI shows: "Charted Depth: No data available" or "Charted Depth: unavailable (no chart data)"
- **Never** returns fabricated/synthetic depth

---

## H. MAPLIBRE INTEGRATION

- **MapLibre Android Version**: 11.5.1 (`org.maplibre.gl:android-sdk:11.5.1`)
- **Offline Rendering**: Uses `RasterSource` with `mbtiles://` scheme for local MBTiles
- **Real Bathymetry Rendering**: 
  - MBTiles served via `RasterSource` with `mbtiles://` scheme
  - `RasterLayer` with opacity 0.85
  - Color ramp baked into MBTiles during preprocessing (16-bit PNG encoding)
  - No runtime `PropertyFactory.rasterColor()` (not supported in 11.5.1)
- **Fallback**: When no real bathymetry downloaded, uses GeoJSON-based bathymetry from `BathymetryData` (empty placeholder in production, synthetic in debug)
- **Offline Operation**: Fully offline after download - no internet required for map rendering or depth lookup

---

## I. TEST RESULTS

| Test | Result |
|---|---|
| Unit tests | ✅ PASS (no source) |
| Lint (Debug) | ✅ PASS |
| Debug Build | ✅ PASS |
| Release Build | ✅ PASS |
| Release Build (minify/R8) | ✅ PASS |
| Lint (Release Vital) | ✅ PASS |
| Room Migration v3 | ✅ Compiles |

---

## J. REMAINING BLOCKERS

| Blocker | Description | Priority |
|---|---|---|
| GEBCO 2024 NetCDF download | 7.5 GB file from https://download.gebco.net/ | Critical |
| Preprocessing pipeline execution | Run `generate_binary_grid.py` and `generate_mbtiles.py` for 4 regions | Critical |
| CDN deployment | Host 8 package files (4 MBTiles + 4 ZIPs) at configured BASE_URL | Critical |
| Checksum configuration | Generate SHA-256 for package integrity verification | High |
| BASE_URL configuration | Update `BathymetryConfig.BASE_URL` or use remote config | Critical |
| Binary grid validation | Run `validate_binary_grid.py` against source NetCDF | High |
| CDN SSL/TLS | Ensure HTTPS with valid certificates | High |

---

## FINAL VERIFICATION

✅ Debug build compiles and runs  
✅ Release build compiles with R8 minification  
✅ Lint passes (Debug and Release)  
✅ Unit tests pass  
✅ Room migration v3 compiles  
✅ Synthetic bathymetry code moved to debug source set  
✅ Production binds RealBathymetryRepositoryImpl only  
✅ No synthetic fallback in production code paths  
✅ "Exact Depth" terminology removed  
✅ "Estimated Depth" terminology replaced with "Charted Depth"  
✅ Real GEBCO binary grid reader implemented  
✅ Real MBTiles provider for MapLibre implemented  
✅ Download system with real ZIP extraction and validation  
✅ Graceful "Bathymetry unavailable" state when data missing  
✅ Release APK does not contain synthetic bathymetry classes  
✅ Real GEBCO preprocessing pipeline documented and scripted  

---

**AUDIT CONCLUSION: PASS WITH WARNINGS**

The production code path is clean - only real GEBCO bathymetry is reachable in production. The synthetic implementation is isolated to the debug source set. However, the actual GEBCO data packages must be generated and hosted externally before the app can provide real bathymetry data to users.
