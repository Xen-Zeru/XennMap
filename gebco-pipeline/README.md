# GEBCO 2024 Bathymetry Preprocessing Pipeline

## Overview
This pipeline converts the GEBCO 2024 NetCDF grid into two Android-friendly formats:
1. **MBTiles** - For MapLibre raster-dem/raster rendering
2. **Binary grid tiles** - For fast offline point queries

## Prerequisites

### Fedora/RHEL Setup
```bash
sudo dnf install gdal python3-gdal python3-numpy python3-netcdf4 unzip
```

### Ubuntu/Debian Setup
```bash
sudo apt-get install gdal-bin python3-gdal python3-numpy python3-netcdf4 unzip
```

### Verify GDAL Version
```bash
gdalinfo --version
# Should be 3.5+ for NetCDF-4 support
```

## Source Data

Download GEBCO 2024 Grid from https://download.gebco.net/
- File: `gebco_2024.nc` (~7.5 GB)
- Format: NetCDF-4, CF-1.6 compliant
- Resolution: 15 arc-second (43200 × 86400 grid)
- Values: int16 meters (negative = depth below MSL)
- NoData: -32768

## Pipeline Steps

### Step 1: Extract Philippines Region

```bash
# Philippines bounding box: 4°N-21.5°N, 116°E-127°E
gdal_translate \
    -projwin 116 21.5 127 4 \
    -of NetCDF \
    gebco_2024.nc gebco_2024_ph.nc
```

### Step 2: Generate Regional Subsets

```bash
# Whole Philippines
gdal_translate -projwin 116 21.5 127 4 -of NetCDF gebco_2024.nc gebco_ph_whole.nc

# Luzon (12°N-21.5°N, 116°E-127°E)
gdal_translate -projwin 116 21.5 127 12 -of NetCDF gebco_2024.nc gebco_ph_luzon.nc

# Visayas (8°N-12.5°N, 121°E-127°E)
gdal_translate -projwin 121 12.5 127 8 -of NetCDF gebco_2024.nc gebco_ph_visayas.nc

# Mindanao (4°N-9°N, 116°E-127°E)
gdal_translate -projwin 116 9 127 4 -of NetCDF gebco_2024.nc gebco_ph_mindanao.nc
```

### Step 3: Generate MBTiles (for MapLibre rendering)

```bash
# Install gdal2tiles.py (part of GDAL)
# Generate MBTiles with zoom levels 0-13

# Whole Philippines
gdal2tiles.py \
    --zoom=0-13 \
    --processes=4 \
    --xyz \
    --webviewer=none \
    gebco_ph_whole.nc gebco_ph_whole.mbtiles

# Luzon
gdal2tiles.py \
    --zoom=0-13 \
    --processes=4 \
    --xyz \
    --webviewer=none \
    gebco_ph_luzon.nc gebco_ph_luzon.mbtiles

# Visayas
gdal2tiles.py \
    --zoom=0-13 \
    --processes=4 \
    --xyz \
    --webviewer=none \
    gebco_ph_visayas.nc gebco_ph_visayas.mbtiles

# Mindanao
gdal2tiles.py \
    --zoom=0-13 \
    --processes=4 \
    --xyz \
    --webviewer=none \
    gebco_ph_mindanao.nc gebco_ph_mindanao.mbtiles
```

**Note**: gdal2tiles.py generates PNG tiles by default. For depth data, we need single-channel data.
Use the Python script below instead for proper single-channel MBTiles generation.

### Step 4: Generate Binary Grid Tiles

```bash
# Run the Python script to generate 1°×1° binary tiles
python3 generate_binary_grid.py \
    --input gebco_2024_ph.nc \
    --output-dir gebco2024_tiles \
    --region ph_whole
```

This creates:
- `gebco_N{lat}_E{lon}.bin` files (115,200 bytes each)
- 1° × 1° tiles covering the Philippine bounding box
- Little-endian int16 meters (native GEBCO precision)
- NoData = -32768

### Step 5: Package for Download

```bash
# Create ZIP for binary grid tiles
zip -r gebco2024_ph_whole_grid.zip gebco2024_tiles/

# Verify sizes
ls -lh *.mbtiles *.zip
```

## Validation

### Validate Binary Grid Against Source NetCDF

```bash
python3 validate_binary_grid.py \
    --source gebco_2024_ph.nc \
    --tiles-dir gebco2024_tiles/
```

### Sample Validation Points (Philippines)

| Location | Lat | Lon | Expected GEBCO Value |
|----------|-----|-----|---------------------|
| Manila Bay | 14.5995 | 120.9842 | ~ -15m |
| Cebu Strait | 10.3157 | 123.8854 | ~ -42m |
| Philippine Sea | 15.0 | 127.0 | ~ -5000m |
| Manila (land) | 14.5995 | 120.9842 | > 0 (elevation) |

## Package Structure

```
download/
├── gebco2024_ph_whole.mbtiles      # ~250 MB
├── gebco2024_ph_whole_grid.zip     # ~80 MB
├── gebco2024_ph_luzon.mbtiles      # ~100 MB
├── gebco2024_ph_luzon_grid.zip     # ~30 MB
├── gebco2024_ph_visayas.mbtiles    # ~80 MB
├── gebco2024_ph_visayas_grid.zip   # ~25 MB
├── gebco2024_ph_mindanao.mbtiles   # ~90 MB
└── gebco2024_ph_mindanao_grid.zip  # ~28 MB
```

## Deployment

Upload to CDN and configure in app:
- `bathymetry.base_url` in build config or remote config
- Format: `{base_url}/{region_id}.mbtiles` and `{base_url}/{region_id}_grid.zip`

## Attribution

Required in app and any redistribution:
```
GEBCO Compilation Group (2024) GEBCO 2024 Grid 
(doi:10.5285/1c44ce99-0a0d-5f4f-e063-7086abc0ea0f)
License: CC-BY 4.0
```

## Disclaimer

GEBCO explicitly states:
> "The accuracy and completeness of The GEBCO Grid cannot be guaranteed. 
> No responsibility can be accepted by GEBCO, IHO, IOC, or those involved 
> in its creation or publication for any consequential loss, injury or 
> damage arising from its use or for determining the fitness of The 
> GEBCO Grid for any particular use."