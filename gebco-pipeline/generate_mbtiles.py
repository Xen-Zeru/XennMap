#!/usr/bin/env python3
"""
Generate RGB MBTiles from GEBCO NetCDF for MapLibre.

Output: .mbtiles file with raster tiles (zoom 0-13)
Each tile contains bathymetry visualization as RGB PNG with baked color ramp.
"""

import argparse
import os
import sys
import sqlite3
from pathlib import Path
import numpy as np
from netCDF4 import Dataset
from PIL import Image
import io

# Constants
TILE_SIZE = 256
MIN_ZOOM = 0
MAX_ZOOM = 13
NO_DATA = -32768

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

# Bathymetry color ramp (dark theme - nautical chart style)
# Depth bands: 0-5, 5-10, 10-20, 20-50, 50-100, 100-200, 200+
BATHY_RAMP = np.array([
    [0x17, 0x60, 0x7C, 0xFF],  # 0-5 m
    [0x13, 0x51, 0x63, 0xFF],  # 5-10 m
    [0x10, 0x42, 0x52, 0xFF],  # 10-20 m
    [0x0D, 0x35, 0x44, 0xFF],  # 20-50 m
    [0x0B, 0x2A, 0x38, 0xFF],  # 50-100 m
    [0x09, 0x21, 0x2D, 0xFF],  # 100-200 m
    [0x07, 0x18, 0x23, 0xFF],  # 200+ m
], dtype=np.uint8)

# Depth band breaks (meters, negative = depth)
BAND_BREAKS = [-5, -10, -20, -50, -100, -200, -11000]

# Land color (transparent for land - let base map show through)
# Using transparent black
LAND_COLOR = np.array([0, 0, 0, 0], dtype=np.uint8)

# NoData color (transparent)
NODATA_COLOR = np.array([0, 0, 0, 0], dtype=np.uint8)


def parse_args():
    parser = argparse.ArgumentParser(description="Generate RGB MBTiles from GEBCO NetCDF")
    parser.add_argument("--input", required=True, help="Input NetCDF file")
    parser.add_argument("--output", required=True, help="Output .mbtiles file")
    parser.add_argument("--region", choices=REGIONS.keys(), default="ph_whole")
    return parser.parse_args()


def read_gebco_netcdf(nc_path):
    """Read GEBCO NetCDF."""
    ds = Dataset(nc_path, 'r')
    elev = ds.variables['elevation'][:]
    return elev, ds


def lat_lon_to_tile(lat, lon, zoom):
    """Convert lat/lon to tile x/y at given zoom."""
    n = 2 ** zoom
    x = int((lon + 180) / 360 * n)
    y = int((1 - np.log(np.tan(np.radians(lat)) + 1/np.cos(np.radians(lat))) / np.pi) / 2 * n)
    return x, y


def tile_to_bounds(x, y, zoom):
    """Convert tile x/y/z to lat/lon bounds."""
    n = 2 ** zoom
    lon_west = x / n * 360 - 180
    lon_east = (x + 1) / n * 360 - 180
    lat_north = np.degrees(np.arctan(np.sinh(np.pi * (1 - 2 * y / n))))
    lat_south = np.degrees(np.arctan(np.sinh(np.pi * (1 - 2 * (y + 1) / n))))
    return lat_south, lon_west, lat_north, lon_east


def generate_tile_image(elev, ds, x, y, zoom):
    """Generate a 256x256 RGB image for the tile with baked bathymetry color ramp."""
    lat_south, lon_west, lat_north, lon_east = tile_to_bounds(x, y, zoom)
    
    # Get coordinate arrays from dataset
    lat_array = ds.variables['lat'][:]
    lon_array = ds.variables['lon'][:]
    
    # Create 1D lat/lon arrays for tile center pixels
    py_indices = np.arange(TILE_SIZE) + 0.5
    px_indices = np.arange(TILE_SIZE) + 0.5
    
    lats = lat_north - py_indices / TILE_SIZE * (lat_north - lat_south)  # (256,)
    lons = lon_west + px_indices / TILE_SIZE * (lon_east - lon_west)   # (256,)
    
    # Find nearest indices in coordinate arrays for each lat/lon
    lat_idx_1d = np.abs(lat_array[:, None] - lats[None, :]).argmin(axis=0)  # (256,)
    lon_idx_1d = np.abs(lon_array[:, None] - lons[None, :]).argmin(axis=0)  # (256,)
    
    # Check bounds
    lat_idx_1d = np.clip(lat_idx_1d, 0, elev.shape[0] - 1)
    lon_idx_1d = np.clip(lon_idx_1d, 0, elev.shape[1] - 1)
    
    # Sample elevation using meshgrid (vectorized)
    lat_idx_grid, lon_idx_grid = np.meshgrid(lat_idx_1d, lon_idx_1d, indexing='ij')
    tile_data = elev[lat_idx_grid, lon_idx_grid]
    
    # Create RGBA output
    rgba = np.zeros((TILE_SIZE, TILE_SIZE, 4), dtype=np.uint8)
    
    # Classify each pixel
    nodata_mask = (tile_data == NO_DATA)
    land_mask = (tile_data != NO_DATA) & (tile_data >= 0)
    water_mask = (tile_data != NO_DATA) & (tile_data < 0)
    
    # NoData -> transparent
    rgba[nodata_mask] = NODATA_COLOR
    
    # Land -> transparent (let base map show through)
    rgba[land_mask] = LAND_COLOR
    
    # Water -> apply bathymetry color ramp based on depth
    if np.any(water_mask):
        water_depths = tile_data[water_mask]  # negative values
        # Assign band index based on depth
        # BAND_BREAKS: [-5, -10, -20, -50, -100, -200, -11000]
        # depth >= -5 -> band 0, depth >= -10 -> band 1, etc.
        band_indices = np.searchsorted(BAND_BREAKS, water_depths, side='right')
        band_indices = np.clip(band_indices, 0, len(BATHY_RAMP) - 1)
        
        # Apply colors
        water_colors = BATHY_RAMP[band_indices]
        rgba[water_mask] = water_colors
    
    # Convert to RGB PNG (drop alpha for smaller tiles, or keep RGBA)
    # Use RGBA to preserve transparency for land/NoData
    img = Image.fromarray(rgba, mode='RGBA')
    buf = io.BytesIO()
    img.save(buf, format='PNG', optimize=True)
    return buf.getvalue()


def init_mbtiles(mbtiles_path):
    """Initialize MBTiles database."""
    conn = sqlite3.connect(mbtiles_path)
    cursor = conn.cursor()
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS metadata (
            name TEXT PRIMARY KEY,
            value TEXT
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS tiles (
            zoom_level INTEGER,
            tile_column INTEGER,
            tile_row INTEGER,
            tile_data BLOB,
            PRIMARY KEY (zoom_level, tile_column, tile_row)
        )
    """)
    
    cursor.execute("CREATE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row)")
    
    conn.commit()
    return conn


def tile_exists(cursor, zoom, x, y):
    """Check if tile already exists."""
    cursor.execute("SELECT 1 FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?", (zoom, x, y))
    return cursor.fetchone() is not None


def insert_tile(cursor, zoom, x, y, tile_data):
    """Insert tile into MBTiles."""
    cursor.execute(
        "INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)",
        (zoom, x, y, tile_data)
    )


def main():
    parser = argparse.ArgumentParser(description="Generate RGB MBTiles from GEBCO NetCDF")
    parser.add_argument("--input", required=True, help="Input NetCDF file")
    parser.add_argument("--output", required=True, help="Output .mbtiles file")
    parser.add_argument("--region", choices=REGIONS.keys(), default="ph_whole")
    parser.add_argument("--min-zoom", type=int, default=MIN_ZOOM)
    parser.add_argument("--max-zoom", type=int, default=MAX_ZOOM)
    parser.add_argument("--resume", action="store_true", help="Resume from existing MBTiles")
    args = parser.parse_args()
    
    if not os.path.exists(args.input):
        print(f"ERROR: Input file not found: {args.input}")
        sys.exit(1)
    
    south, west, north, east = REGIONS[args.region]
    
    print(f"Reading {args.input}...")
    elev, ds = read_gebco_netcdf(args.input)
    print(f"Elevation shape: {elev.shape}, dtype: {elev.dtype}")
    
    # Initialize MBTiles
    conn = init_mbtiles(args.output)
    cursor = conn.cursor()
    
    # Insert metadata
    metadata = [
        ("name", f"GEBCO 2024 Bathymetry - {args.region}"),
        ("description", f"GEBCO 2024 Grid bathymetry visualization for Philippines region"),
        ("version", "202407"),
        ("format", "png"),
        ("type", "overlay"),
        ("minzoom", str(args.min_zoom)),
        ("maxzoom", str(args.max_zoom)),
        ("bounds", f"{west},{south},{east},{north}"),
        ("center", f"{(west+east)/2},{(south+north)/2},8"),
        ("attribution", "GEBCO Compilation Group (2024) GEBCO 2024 Grid (doi:10.5285/1c44ce99-0a0d-5f4f-e063-7086abc0ea0f)"),
    ]
    
    for name, value in metadata:
        cursor.execute("INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?)", (name, value))
    
    conn.commit()
    
    # Generate tiles for each zoom level
    total_tiles = 0
    
    for zoom in range(args.min_zoom, args.max_zoom + 1):
        n = 2 ** zoom
        
        # Calculate tile range for region
        # Note: Web Mercator y increases southward (toward equator)
        # north boundary has smaller y, south boundary has larger y
        x_north, y_north = lat_lon_to_tile(north, west, zoom)
        x_south, y_south = lat_lon_to_tile(south, east, zoom)
        
        x_min = min(x_north, x_south)
        x_max = max(x_north, x_south)
        y_min = min(y_north, y_south)
        y_max = max(y_north, y_south)
        
        # Clamp to valid range
        x_min = max(0, min(x_min, n - 1))
        x_max = max(0, min(x_max, n - 1))
        y_min = max(0, min(y_min, n - 1))
        y_max = max(0, min(y_max, n - 1))
        
        tiles_in_zoom = (x_max - x_min + 1) * (y_max - y_min + 1)
        print(f"\nZoom {zoom}: {tiles_in_zoom} tiles (x={x_min}-{x_max}, y={y_min}-{y_max})")
        
        zoom_tiles = 0
        for x in range(x_min, x_max + 1):
            for y in range(y_min, y_max + 1):
                if args.resume and tile_exists(cursor, zoom, x, y):
                    continue
                
                try:
                    tile_data = generate_tile_image(elev, ds, x, y, zoom)
                    insert_tile(cursor, zoom, x, y, tile_data)
                    total_tiles += 1
                    zoom_tiles += 1
                except Exception as e:
                    print(f"  ERROR generating tile {zoom}/{x}/{y}: {e}")
                    continue
                
                if zoom_tiles % 100 == 0:
                    conn.commit()
                    print(f"  Zoom {zoom}: {zoom_tiles}/{tiles_in_zoom} tiles...")
        
        conn.commit()
        print(f"  Zoom {zoom} complete: {zoom_tiles} tiles generated")
    
    conn.close()
    print(f"\nDone! Total tiles: {total_tiles}")
    print(f"Output: {args.output}")


if __name__ == "__main__":
    main()