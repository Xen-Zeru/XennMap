# GEBCO 2024 Bathymetry Data Download Report

## Summary
The GEBCO 2024 NetCDF file download from CEDA was initiated but remains incomplete due to extremely slow download speeds from the CEDA archive (~20-70 KB/s). The file is 7.466 GB but only ~1.2 MB was downloaded before the connection stalled.

## Download Source
- **Source**: CEDA Archive (https://dap.ceda.ac.uk)
- **File**: `GEBCO_2024_CF.nc` (GEBCO 2024 Ice Surface Elevation)
- **Source URL**: `https://dap.ceda.ac.uk/thredds/fileServer/bodc/gebco/global/gebco_2024/ice_surface_elevation/netcdf/GEBCO_2024_CF.nc`
- **Expected Size**: 7.466 GB (7,466,018,396 bytes)
- **Downloaded**: ~1.2 MB (0.016% complete)
- **Format**: NetCDF-4, CF-1.6 compliant
- **Resolution**: 15 arc-second (43200 × 86400 grid)
- **Values**: int16 meters (negative = depth below MSL)
- **NoData**: -32768

## Pipeline Requirements Met
✅ Synthetic bathymetry moved to debug-only source set (`app/src/debug/`)
✅ Production binds `RealBathymetryRepositoryImpl` only
✅ No synthetic fallback in production code paths
✅ "Exact Depth" terminology replaced with "Charted Depth" throughout UI
✅ Real GEBCO binary grid reader implemented (`BathymetryBinaryGridReader.kt`)
✅ Real MBTiles provider for MapLibre (`BathymetryMbtilesProvider.kt`)
✅ Download system with real ZIP extraction and validation
✅ MapLibre depth color-ramp rendering (raster-color baked in preprocessing)
✅ All builds pass (debug, release, lint, unit tests)
✅ Room migration v3 for bathymetry columns

## OPeNDAP Subsetting Status
❌ **NOT WORKING** - THREDDS OPeNDAP subsetting not functional
- Tested: `https://dap.ceda.ac.uk/thredds/dodsC/bodc/gebco/global/gebco_2024/ice_surface_elevation/netcdf/GEBCO_2024_CF.nc?elevation[16440:1:20640][71040:1:73680]`
- Result: No data returned, connection hangs
- DDS/DAS endpoints work, but subsetting returns no data

## Required External Actions (Blocking Production)
| Blocker | Description | Priority |
|---------|-------------|----------|
| GEBCO 2024 NetCDF download | Download 7.466 GB file from CEDA | Critical |
| Preprocessing pipeline | Run `generate_binary_grid.py` and `generate_mbtiles.py` for 4 regions | Critical |
| CDN deployment | Host 8 package files at configured BASE_URL | Critical |
| BASE_URL configuration | Update `BathymetryConfig.BASE_URL` | Critical |
| Binary grid validation | Run `validate_binary_grid.py` against source NetCDF | High |

## Next Steps Required
1. **Download complete GEBCO 2024 NetCDF** from CEDA (7.466 GB)
2. **Run preprocessing pipeline**:
   ```bash
   python3 gebco-pipeline/generate_binary_grid.py --input GEBCO_2024_CF.nc --output-dir gebco2024_tiles --region ph_whole
   python3 gebco-pipeline/generate_mbtiles.py --input GEBCO_2024_CF.nc --output gebco2024_ph_whole.mbtiles --region ph_whole
   # Repeat for ph_luzon, ph_visayas, ph_mindanao
   ```
3. **Validate outputs**: `python3 gebco-pipeline/validate_binary_grid.py --source GEBCO_2024_CF.nc --tiles-dir gebco2024_tiles/`
4. **Package for download**: Create ZIP files for binary grids
5. **Deploy to CDN**: Upload 8 files (4 MBTiles + 4 ZIPs) to CDN
5. **Configure app**: Set `BathymetryConfig.BASE_URL` to CDN URL

## Verification Checklist
- [ ] Download complete GEBCO_2024_CF.nc (7.466 GB)
- [ ] Validate SHA-256 checksum
- [ ] Generate binary grid tiles for 4 regions
- [ ] Generate MBTiles for 4 regions
- [ ] Validate binary tiles against source NetCDF
- [ ] Create ZIP packages for binary grids
- [ ] Upload all 8 packages to CDN
- [ ] Configure `BathymetryConfig.BASE_URL` in build config
- [ ] Test download in app (debug + release)
- [ ] Verify "Charted Depth" terminology in UI
- [ ] Verify "No charted depth available" when package missing
- [ ] Test offline mode with downloaded package

## File Inventory
```
gebco-pipeline/
├── README.md                      # Pipeline documentation
├── generate_binary_grid.py        # Binary tile generator
├── generate_mbtiles.py            # MBTiles generator
├── validate_binary_grid.py        # Validation script
├── GEBCO_2024_CF.nc              # INCOMPLETE (1.2MB / 7.466GB)
├── GEBCO_2024_PH.nc              # Not generated
├── gebco2024_tiles/              # Not generated
├── gebco2024_ph_whole.mbtiles    # Not generated
├── gebco2024_ph_luzon.mbtiles    # Not generated
├── gebco2024_ph_visayas.mbtiles  # Not generated
└── gebco2024_ph_mindanao.mbtiles # Not generated
```

## AUDIT RESULT: PASS WITH WARNINGS
Production code path is clean (no synthetic bathymetry reachable). External data deployment required before production use.
