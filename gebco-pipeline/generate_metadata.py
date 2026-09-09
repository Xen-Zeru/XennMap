#!/usr/bin/env python3
"""
Generate metadata JSON for bathymetry packages.
"""
import json
import hashlib
from pathlib import Path

BASE_DIR = Path("/home/xenn-jan/XennMap/gebco-pipeline")

regions = {
    "ph_whole": {
        "id": "gebco2024_ph_whole",
        "display_name": "Philippines (Whole Country)",
        "description": "GEBCO 2024 bathymetry for entire Philippine archipelago",
        "south": 4.0, "west": 116.0, "north": 21.5, "east": 127.0,
        "mbtiles_file": "gebco2024_ph_whole.mbtiles",
        "mbtiles_zip": "gebco2024_ph_whole_mbtiles.zip",
        "grid_zip": "gebco2024_ph_whole_grid.zip",
        "binary_dir": "gebco2024_tiles",
    },
    "ph_luzon": {
        "id": "gebco2024_ph_luzon",
        "display_name": "Luzon",
        "description": "GEBCO 2024 bathymetry for Luzon and surrounding waters",
        "south": 12.0, "west": 116.0, "north": 21.5, "east": 127.0,
        "mbtiles_file": "gebco2024_ph_luzon.mbtiles",
        "mbtiles_zip": "gebco2024_ph_luzon_mbtiles.zip",
        "grid_zip": "gebco2024_ph_luzon_grid.zip",
        "binary_dir": "gebco2024_tiles_luzon",
    },
    "ph_visayas": {
        "id": "gebco2024_ph_visayas",
        "display_name": "Visayas",
        "description": "GEBCO 2024 bathymetry for Visayas region",
        "south": 8.0, "west": 121.0, "north": 12.5, "east": 127.0,
        "mbtiles_file": "gebco2024_ph_visayas.mbtiles",
        "mbtiles_zip": "gebco2024_ph_visayas_mbtiles.zip",
        "grid_zip": "gebco2024_ph_visayas_grid.zip",
        "binary_dir": "gebco2024_tiles_visayas",
    },
    "ph_mindanao": {
        "id": "gebco2024_ph_mindanao",
        "display_name": "Mindanao",
        "description": "GEBCO 2024 bathymetry for Mindanao and surrounding waters",
        "south": 4.0, "west": 116.0, "north": 9.0, "east": 127.0,
        "mbtiles_file": "gebco2024_ph_mindanao.mbtiles",
        "mbtiles_zip": "gebco2024_ph_mindanao_mbtiles.zip",
        "grid_zip": "gebco2024_ph_mindanao_grid.zip",
        "binary_dir": "gebco2024_tiles_mindanao",
    },
}

def file_sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()

def get_file_size(path):
    return Path(path).stat().st_size

def count_tiles_in_mbtiles(mbtiles_path):
    import sqlite3
    conn = sqlite3.connect(mbtiles_path)
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM tiles")
    count = cursor.fetchone()[0]
    conn.close()
    return count

def get_zoom_levels(mbtiles_path):
    import sqlite3
    conn = sqlite3.connect(mbtiles_path)
    cursor = conn.cursor()
    cursor.execute("SELECT DISTINCT zoom_level FROM tiles ORDER BY zoom_level")
    return [row[0] for row in cursor.fetchall()]

def count_binary_tiles(tiles_dir):
    return len(list(Path(tiles_dir).glob("*.bin")))

def get_binary_tile_size(tiles_dir):
    files = list(Path(tiles_dir).glob("*.bin"))
    if files:
        return files[0].stat().st_size
    return 0

def count_binary_tiles_total(tiles_dir):
    return len(list(Path(tiles_dir).glob("*.bin")))

import hashlib
import sqlite3

for region_key, region in regions.items():
    print(f"\nProcessing {region['display_name']}...")
    
    metadata = {
        "id": region["id"],
        "display_name": region["display_name"],
        "description": region["description"],
        "source": "GEBCO 2024 Grid",
        "version": "202407",
        "resolution_arcsec": 15,
        "resolution_meters": 450,
        "vertical_datum": "MSL",
        "horizontal_datum": "WGS84",
        "attribution": "GEBCO Compilation Group (2024) GEBCO 2024 Grid (doi:10.5285/1c44ce99-0a0d-5f4f-e063-7086abc0ea0f)",
        "license": "Public Domain (GEBCO conditions of use apply \u2014 see https://www.gebco.net/)",
        "coverage": {
            "south": region["south"],
            "west": region["west"],
            "north": region["north"],
            "east": region["east"],
        },
        "warning": "Not for navigation — use official nautical charts. Charted depth from GEBCO 2024 Grid (15 arc-sec resolution, ~450m at Philippine latitudes). Vertical datum: MSL. Not for navigation — use official nautical charts.",
    }
    
    # MBTiles info
    mbtiles_path = BASE_DIR / region["mbtiles_file"]
    if mbtiles_path.exists():
        metadata["mbtiles"] = {
            "file": region["mbtiles_file"],
            "size_bytes": get_file_size(mbtiles_path),
            "sha256": file_sha256(mbtiles_path),
            "zoom_levels": get_zoom_levels(mbtiles_path),
            "total_tiles": count_tiles_in_mbtiles(mbtiles_path),
        }
        # ZIP
        mbtiles_zip_path = BASE_DIR / region["mbtiles_zip"]
        if mbtiles_zip_path.exists():
            metadata["mbtiles_zip"] = {
                "file": region["mbtiles_zip"],
                "size_bytes": get_file_size(mbtiles_zip_path),
                "sha256": file_sha256(mbtiles_zip_path),
            }
    
    # Binary grid info
    grid_zip_path = BASE_DIR / region["grid_zip"]
    if grid_zip_path.exists():
        metadata["binary_grid_zip"] = {
            "file": region["grid_zip"],
            "size_bytes": get_file_size(grid_zip_path),
            "sha256": file_sha256(grid_zip_path),
        }
        tiles_dir = BASE_DIR / region["binary_dir"]
        if tiles_dir.exists():
            metadata["binary_grid"] = {
                "tile_count": count_binary_tiles_total(tiles_dir),
                "tile_size_bytes": get_binary_tile_size(tiles_dir),
                "total_uncompressed_bytes": count_binary_tiles_total(tiles_dir) * get_binary_tile_size(tiles_dir),
            }
    
    # Save metadata
    metadata_path = BASE_DIR / f"{region['id']}_metadata.json"
    with open(metadata_path, "w") as f:
        json.dump(metadata, f, indent=2)
    print(f"  Metadata saved to {metadata_path}")

# Generate checksums file
checksums = {}
for region_key, region in regions.items():
    for file_key in ["mbtiles_file", "mbtiles_zip", "grid_zip"]:
        if file_key in region:
            file_path = BASE_DIR / region[file_key]
            if file_path.exists():
                checksums[f"{region['id']}/{region[file_key]}"] = file_sha256(file_path)
    # Also add binary grid ZIPs
    if "grid_zip" in region:
        grid_zip_path = BASE_DIR / region["grid_zip"]
        if grid_zip_path.exists():
            checksums[f"{region['id']}/{region['grid_zip']}"] = file_sha256(grid_zip_path)

checksums_path = BASE_DIR / "CHECKSUMS.sha256"
with open(checksums_path, "w") as f:
    for file, checksum in sorted(checksums.items()):
        f.write(f"{checksum}  {file}\n")
print(f"\nChecksums saved to {checksums_path}")

print("\nDone!")
