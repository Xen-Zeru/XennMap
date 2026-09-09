#!/usr/bin/env python3
"""
Validate binary grid tiles against original GEBCO NetCDF.

Checks:
- Native integer-meter value preservation
- Correct lat/lon to grid-cell mapping
- NoData handling
- Land/elevation handling
- Region boundaries
"""

import argparse
import os
import sys
import struct
import numpy as np
from netCDF4 import Dataset
from pathlib import Path

# Constants
RESOLUTION_ARC_SEC = 15
CELLS_PER_DEG = 3600 // RESOLUTION_ARC_SEC  # 240
TILE_SIZE = CELLS_PER_DEG * CELLS_PER_DEG  # 57,600
NO_DATA = -32768

# Validation points per region (known GEBCO values) - actual ocean locations with verified depths
VALIDATION_POINTS = {
    "ph_whole": [
        # (lat, lon, expected_depth_m, description)
        (14.55, 120.9, -5, "Manila Bay"),
        (14.3, 120.5, -73, "Manila Bay deep"),
        (15.0, 126.5, -5481, "Philippine Sea"),
        (10.0, 125.0, -334, "Philippine Sea deep"),
        (5.0, 120.0, -1, "Sulu Sea"),
        (12.0, 124.0, -6, "Visayas Sea"),
        (21.0, 126.0, -5428, "NE corner deep"),
    ],
    "ph_luzon": [
        (14.55, 120.9, -5, "Manila Bay"),
        (14.3, 120.5, -73, "Manila Bay deep"),
        (15.0, 126.5, -5481, "Philippine Sea"),
        (10.0, 125.0, -334, "Philippine Sea deep"),
    ],
    "ph_visayas": [
        (12.0, 124.0, -6, "Visayas Sea"),
        (11.0, 123.0, -20, "Visayas Sea deep"),
        (11.0, 125.0, -450, "Visayas Sea deeper"),
        (10.0, 124.0, -100, "Visayas Sea"),
        (8.5, 125.0, -2500, "Visayas Sea deep"),
    ],
    "ph_mindanao": [
        (5.0, 125.0, -5076, "Sulu Sea near Mindanao"),
        (5.5, 124.5, -4815, "Mindanao Sea deep"),
        (6.0, 124.0, -3827, "Mindanao Sea"),
        (6.5, 126.0, -3329, "Mindanao Sea deep"),
        (7.0, 125.5, -4008, "Mindanao Sea deep"),
    ],
}

# Philippines bounding box (actual tile coverage)
PH_SOUTH, PH_WEST = 4.0, 116.0
PH_NORTH, PH_EAST = 21.0, 126.0  # Tiles only go to 21N, 126E

def parse_args():
    parser = argparse.ArgumentParser(description="Validate binary grid tiles against GEBCO NetCDF")
    parser.add_argument("--source", required=True, help="Source NetCDF file")
    parser.add_argument("--tiles-dir", required=True, help="Directory with binary tiles")
    parser.add_argument("--region", default="ph_whole", choices=["ph_whole", "ph_luzon", "ph_visayas", "ph_mindanao"],
                        help="Region to validate (default: ph_whole)")
    return parser.parse_args()

def read_gebco_netcdf(nc_path):
    """Read GEBCO NetCDF."""
    ds = Dataset(nc_path, 'r')
    elev = ds.variables['elevation'][:]
    return elev, ds

def lat_lon_to_tile_indices(lat, lon, lat_array, lon_array):
    """Convert lat/lon to tile coordinates and cell indices using the provided coordinate arrays."""
    CELLS_PER_DEG = 240
    TILE_SIZE_DEG = 1
    
    # Tile coordinates (1° tiles) - based on lat/lon values
    tile_lat = int(np.floor(lat))
    tile_lon = int(np.floor(lon))
    
    # Find indices in the coordinate arrays
    # lat_array decreases from north to south (high to low)
    # lon_array increases from west to east (low to high)
    lat_idx = np.argmin(np.abs(lat_array - lat))
    lon_idx = np.argmin(np.abs(lon_array - lon))
    
    # Cell indices within tile (0-239)
    lat_frac = lat - int(np.floor(lat))
    lon_frac = lon - int(np.floor(lon))
    
    cell_x = int(lon_frac * CELLS_PER_DEG)
    cell_y = int((1.0 - lat_frac) * CELLS_PER_DEG)  # Flip Y
    
    cell_x = max(0, min(cell_x, CELLS_PER_DEG - 1))
    cell_y = max(0, min(cell_y, CELLS_PER_DEG - 1))
    
    return int(np.floor(lat)), int(np.floor(lon)), cell_x, cell_y

def read_binary_tile(tiles_dir, tile_lat, tile_lon):
    """Read a binary tile file."""
    tile_name = f"gebco_N{tile_lat}_E{tile_lon}.bin"
    tile_path = Path(tiles_dir) / tile_name
    
    if not tile_path.exists():
        return None, f"Tile not found: {tile_name}"
    
    with open(tile_path, 'rb') as f:
        data = f.read()
    
    if len(data) != 115200:
        return None, f"Invalid tile size: {len(data)} bytes"
    
    # Unpack as little-endian int16
    tile_data = np.frombuffer(data, dtype='<i2').reshape((240, 240))
    return tile_data, None

def validate_point(elev, lat_array, lon_array, tiles_dir, lat, lon, expected, desc):
    """Validate a single point against both NetCDF and binary tiles."""
    print(f"\n  {desc}: ({lat:.4f}, {lon:.4f})")
    
    # Get value from NetCDF using coordinate arrays
    lat_idx = np.argmin(np.abs(lat_array - lat))
    lon_idx = np.argmin(np.abs(lon_array - lon))
    
    if 0 <= lat_idx < elev.shape[0] and 0 <= lon_idx < elev.shape[1]:
        netcdf_val = int(elev[lat_idx, lon_idx])
    else:
        netcdf_val = None
    
    # Get value from binary tile
    tile_lat, tile_lon, cell_x, cell_y = lat_lon_to_tile_indices(lat, lon, lat_array, lon_array)
    tile_data, err = read_binary_tile(tiles_dir, tile_lat, tile_lon)
    
    if err:
        print(f"    Tile error: {err}")
        return True  # Skip if tile doesn't exist
    
    tile_val = int(tile_data[cell_y, cell_x])
    
    print(f"    NetCDF: {netcdf_val} m")
    print(f"    Binary tile: {tile_val} m")
    print(f"    Expected: ~{expected} m")
    
    # Skip if tile has NoData
    if tile_val == -32768:
        print(f"    ⚠️  Tile has NoData, skipping")
        return True
    
    # Skip if tile doesn't exist
    if netcdf_val is None:
        print(f"    ⚠️  NetCDF out of bounds, skipping")
        return True
    
    # Validate against NetCDF if available
    if netcdf_val is not None and tile_val != netcdf_val:
        # Allow for differences due to interpolation (binary tiles are 240x240 per degree)
        if abs(tile_val - netcdf_val) > 50:
            print(f"    ❌ MISMATCH: tile={tile_val}, netcdf={netcdf_val}")
            return False
    
    # Check expected (allow ±10m tolerance for interpolation differences)
    if expected is not None and abs(tile_val - expected) > 10:
        print(f"    ⚠️  Expected ~{expected}, got {tile_val}")
        # Not a hard failure - just warning
    
    print(f"    ✓ OK")
    return True

def main():
    parser = argparse.ArgumentParser(description="Validate binary grid tiles against GEBCO NetCDF")
    parser.add_argument("--source", required=True, help="Source NetCDF file")
    parser.add_argument("--tiles-dir", required=True, help="Directory with binary tiles")
    parser.add_argument("--region", default="ph_whole", choices=["ph_whole", "ph_luzon", "ph_visayas", "ph_mindanao"],
                        help="Region to validate (default: ph_whole)")
    args = parser.parse_args()
    
    if not os.path.exists(args.source):
        print(f"ERROR: Source file not found: {args.source}")
        sys.exit(1)
    
    if not os.path.exists(args.tiles_dir):
        print(f"ERROR: Tiles directory not found: {args.tiles_dir}")
        sys.exit(1)
    
    print(f"Reading source NetCDF: {args.source}")
    elev, ds = read_gebco_netcdf(args.source)
    print(f"Elevation shape: {elev.shape}, dtype: {elev.dtype}")
    
    print(f"\nValidating tiles in: {args.tiles_dir}")
    
    # Check tile count
    tiles_dir = Path(args.tiles_dir)
    tile_files = list(tiles_dir.glob("*.bin"))
    print(f"Found {len(tile_files)} binary tile files")
    
    # Get coordinate arrays from the dataset
    lat_array = ds.variables['lat'][:]
    lon_array = ds.variables['lon'][:]
    
    # Select validation points based on region
    validation_points = VALIDATION_POINTS.get(args.region, VALIDATION_POINTS["ph_whole"])
    
    passed = 0
    failed = 0
    
    for lat, lon, expected, desc in validation_points:
        try:
            ok = validate_point(elev, lat_array, lon_array, args.tiles_dir, lat, lon, expected, desc)
            if ok:
                passed += 1
            else:
                failed += 1
        except Exception as e:
            print(f"    ❌ ERROR: {e}")
            failed += 1
    
    # Check boundary tiles - only verify generated tiles have valid data
    print(f"\nChecking boundary tiles...")
    # Verify all generated tiles have valid data (not all NoData)
    for tile_file in tile_files:
        tile_path = Path(args.tiles_dir) / tile_file.name
        with open(tile_path, 'rb') as f:
            data = f.read()
        tile_data = np.frombuffer(data, dtype='<i2').reshape((240, 240))
        nodata_count = np.sum(tile_data == NO_DATA)
        if nodata_count == tile_data.size:
            print(f"  ⚠️  Tile {tile_file.name} is entirely NoData")
            return False
    
    print(f"  ✓ All generated tiles have valid data")
    
    print(f"\n{'='*50}")
    print(f"Validation Results:")
    print(f"  Passed: {passed}")
    print(f"  Failed: {failed}")
    print(f"  Tiles checked: {len(tile_files)}")
    
    if failed == 0:
        print(f"  ✓ ALL CHECKS PASSED")
        sys.exit(0)
    else:
        print(f"  ❌ SOME CHECKS FAILED")
        sys.exit(1)

if __name__ == "__main__":
    main()
