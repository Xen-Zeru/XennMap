#!/usr/bin/env python3
"""
Generate 1°×1° binary grid tiles from GEBCO NetCDF.

Output: gebco_N{lat}_E{lon}.bin (115,200 bytes each)
- 240×240 cells per tile (15 arc-second resolution)
- Little-endian int16 meters (native GEBCO precision)
- NoData = -32768 (int16 min)
"""

import argparse
import os
import struct
import sys
import numpy as np
from netCDF4 import Dataset
from pathlib import Path

# Constants
RESOLUTION_ARC_SEC = 15
CELLS_PER_DEG = 3600 // RESOLUTION_ARC_SEC  # 240
TILE_SIZE = CELLS_PER_DEG * CELLS_PER_DEG  # 57,600
BYTES_PER_CELL = 2  # int16
TILE_BYTES = TILE_SIZE * 2  # 115,200
NO_DATA = -32768  # int16 min

# Philippines bounding box
PH_SOUTH, PH_WEST = 4.0, 116.0
PH_NORTH, PH_EAST = 21.5, 127.0

# Region definitions
REGIONS = {
    "ph_whole": (PH_SOUTH, PH_WEST, PH_NORTH, PH_EAST),
    "ph_luzon": (12.0, 116.0, 21.5, 127.0),
    "ph_visayas": (8.0, 121.0, 12.5, 127.0),
    "ph_mindanao": (4.0, 116.0, 9.0, 127.0),
}

def parse_args():
    parser = argparse.ArgumentParser(description="Generate binary grid tiles from GEBCO NetCDF")
    parser.add_argument("--input", required=True, help="Input NetCDF file")
    parser.add_argument("--output-dir", required=True, help="Output directory for tiles")
    parser.add_argument("--region", choices=REGIONS.keys(), default="ph_whole")
    return parser.parse_args()

def read_gebco_netcdf(nc_path):
    """Read GEBCO NetCDF and return elevation array and geotransform."""
    ds = Dataset(nc_path, 'r')
    # GEBCO 2024 uses 'elevation' variable
    elev = ds.variables['elevation'][:]
    # NetCDF is typically [lat, lon] with lat decreasing (north to south)
    # GEBCO: lat from 89.979... to -89.979... (decreasing)
    # lon from -179.979... to 179.979... (increasing)
    return elev, ds

def lat_lon_to_indices(lat, lon, ds):
    """Convert lat/lon to array indices in the NetCDF grid."""
    # GEBCO grid: 15 arc-sec = 0.0041666667 degrees
    res = 15 / 3600.0  # 0.0041666667 degrees
    # Latitude: 89.979... to -89.979... (decreasing)
    # Array index 0 = 89.979... (north)
    lat_idx = int((90 - lat) / res)
    # Longitude: -180 to 180 (increasing)
    # Array index 0 = -180
    lon_idx = int((lon + 180) / res)
    return lat_idx, lon_idx

def extract_tile(elev, ds, tile_lat, tile_lon):
    """Extract a 1°×1° tile from the elevation array using the dataset's coordinate variables."""
    # Get the lat/lon coordinate arrays from the dataset
    lat_var = ds.variables['lat'][:]
    lon_var = ds.variables['lon'][:]
    
    # Tile covers [tile_lat, tile_lat+1) × [tile_lon, tile_lon+1)
    # Find indices in the coordinate arrays
    lat_indices = np.where((lat_var >= tile_lat) & (lat_var < tile_lat + 1))[0]
    lon_indices = np.where((lon_var >= tile_lon) & (lon_var < tile_lon + 1))[0]
    
    if len(lat_indices) == 0 or len(lon_indices) == 0:
        # Tile is outside the dataset bounds
        return np.full((CELLS_PER_DEG, CELLS_PER_DEG), NO_DATA, dtype=np.int16)
    
    # Extract the tile (lat_var decreases northward, so we need to reverse lat)
    lat_start = lat_indices[0]
    lat_end = lat_indices[-1] + 1
    lon_start = lon_indices[0]
    lon_end = lon_indices[-1] + 1
    
    # Extract the tile (lat_var decreases northward, so we need to reverse)
    tile_data = elev[lat_start:lat_end, lon_start:lon_end]
    
    # The lat array is decreasing (north to south), so we need to flip vertically
    tile_data = tile_data[::-1, :]
    
    # Ensure correct shape (240x240)
    if tile_data.shape != (CELLS_PER_DEG, CELLS_PER_DEG):
        # Pad or crop if needed
        padded = np.full((CELLS_PER_DEG, CELLS_PER_DEG), NO_DATA, dtype=np.int16)
        h, w = min(tile_data.shape[0], CELLS_PER_DEG), min(tile_data.shape[1], CELLS_PER_DEG)
        padded[:h, :w] = tile_data[:h, :w]
        tile_data = padded
    
    return tile_data

def write_binary_tile(tile_data, output_path):
    """Write tile as little-endian int16 binary file."""
    # Ensure int16
    tile_data = tile_data.astype(np.int16)
    # Write as little-endian binary
    with open(output_path, 'wb') as f:
        f.write(tile_data.tobytes(order='C'))
    
    # Verify size
    actual_size = os.path.getsize(output_path)
    if actual_size != TILE_BYTES:
        print(f"WARNING: {output_path} size={actual_size}, expected={TILE_BYTES}")
    return actual_size

def main():
    args = parse_args()
    
    if not os.path.exists(args.input):
        print(f"ERROR: Input file not found: {args.input}")
        sys.exit(1)
    
    south, west, north, east = REGIONS[args.region]
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"Reading {args.input}...")
    elev, ds = read_gebco_netcdf(args.input)
    print(f"Elevation shape: {elev.shape}, dtype: {elev.dtype}")
    
    # Determine tile range
    tile_lat_start = int(np.floor(south))
    tile_lat_end = int(np.ceil(north))
    tile_lon_start = int(np.floor(west))
    tile_lon_end = int(np.ceil(east))
    
    print(f"Generating tiles for region {args.region}: lat {tile_lat_start}-{tile_lat_end}, lon {tile_lon_start}-{tile_lon_end}")
    
    total_tiles = 0
    for tile_lat in range(tile_lat_start, tile_lat_end):
        for tile_lon in range(tile_lon_start, tile_lon_end):
            # Skip tiles outside Philippines
            if tile_lat < PH_SOUTH or tile_lat >= PH_NORTH or tile_lon < PH_WEST or tile_lon >= PH_EAST:
                continue
            
            tile_data = extract_tile(elev, ds, tile_lat, tile_lon)
            
            # Create filename: gebco_N{lat}_E{lon}.bin
            tile_name = f"gebco_N{tile_lat}_E{tile_lon}.bin"
            output_path = output_dir / tile_name
            
            size = write_binary_tile(tile_data, output_path)
            total_tiles += 1
            
            if total_tiles % 50 == 0:
                print(f"  Generated {total_tiles} tiles...")
    
    print(f"\nDone! Generated {total_tiles} tiles in {output_dir}")
    print(f"Expected size per tile: {TILE_BYTES} bytes")
    
    # Print summary
    total_size = sum(f.stat().st_size for f in output_dir.glob("*.bin"))
    print(f"Total size: {total_size / (1024*1024):.1f} MB")

if __name__ == "__main__":
    main()