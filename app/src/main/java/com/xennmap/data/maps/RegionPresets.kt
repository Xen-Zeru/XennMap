package com.xennmap.data.maps

import com.xennmap.domain.model.RegionPreset

/**
 * Suggested download areas covering the coastline around the entire Philippines.
 * Bounding boxes are approximate and sizes are estimates (actual size depends
 * on the tile source and zoom range).
 */
object RegionPresets {

    val all: List<RegionPreset> = listOf(
        RegionPreset(
            id = "philippines", name = "Philippines (Whole Country)",
            description = "Offline base map for the entire archipelago",
            south = 4.0, west = 116.0, north = 21.5, east = 127.0,
            estimatedSizeMb = 1800, minZoom = 5, maxZoom = 9,
        ),
        RegionPreset(
            id = "batanes", name = "Batanes & Babuyan Islands",
            description = "Northernmost islands and Balintang Channel",
            south = 19.9, west = 121.0, north = 21.0, east = 122.2, estimatedSizeMb = 150,
        ),
        RegionPreset(
            id = "ilocos", name = "Ilocos Coast",
            description = "Ilocos Norte and Ilocos Sur coastline",
            south = 17.0, west = 120.1, north = 18.7, east = 121.1, estimatedSizeMb = 200,
        ),
        RegionPreset(
            id = "lingayen", name = "Lingayen Gulf",
            description = "Lingayen Gulf, Bolinao and La Union coast",
            south = 15.6, west = 119.6, north = 16.6, east = 120.9, estimatedSizeMb = 230,
        ),
        RegionPreset(
            id = "zambales", name = "Zambales & Subic Bay",
            description = "Zambales coast, Subic Bay and Bataan",
            south = 14.0, west = 119.5, north = 15.9, east = 120.8, estimatedSizeMb = 260,
        ),
        RegionPreset(
            id = "manila", name = "Manila Bay & Approaches",
            description = "Manila Bay, Bataan and Cavite coasts",
            south = 13.95, west = 120.30, north = 15.05, east = 121.40, estimatedSizeMb = 260,
        ),
        RegionPreset(
            id = "batangas", name = "Batangas & Verde Passage",
            description = "Batangas ports and Verde Island Passage",
            south = 13.3, west = 120.4, north = 14.4, east = 121.7, estimatedSizeMb = 240,
        ),
        RegionPreset(
            id = "mindoro", name = "Mindoro Island",
            description = "Mindoro, Puerto Galera and Apo Reef",
            south = 11.9, west = 119.9, north = 13.6, east = 121.8, estimatedSizeMb = 300,
        ),
        RegionPreset(
            id = "marinduque", name = "Marinduque & Romblon",
            description = "Marinduque, Romblon and Tablas islands",
            south = 11.6, west = 121.0, north = 13.3, east = 123.1, estimatedSizeMb = 280,
        ),
        RegionPreset(
            id = "masbate", name = "Masbate & Ticao Pass",
            description = "Masbate island, Ticao Pass and Burias",
            south = 10.9, west = 123.0, north = 12.7, east = 124.6, estimatedSizeMb = 300,
        ),
        RegionPreset(
            id = "bicol", name = "Bicol & Catanduanes",
            description = "Albay, Legazpi and Catanduanes",
            south = 12.4, west = 123.0, north = 14.3, east = 124.7, estimatedSizeMb = 300,
        ),
        RegionPreset(
            id = "palawan", name = "Palawan: El Nido–Coron",
            description = "Northern Palawan corridor and island hopping waters",
            south = 10.80, west = 117.00, north = 12.60, east = 120.00, estimatedSizeMb = 420,
        ),
        RegionPreset(
            id = "puertoprincesa", name = "Puerto Princesa",
            description = "Puerto Princesa, Ulugan Bay and Honda Bay",
            south = 8.6, west = 116.8, north = 10.9, east = 119.4, estimatedSizeMb = 320,
        ),
        RegionPreset(
            id = "panay", name = "Panay & Boracay",
            description = "Iloilo, Kalibo and Boracay waters",
            south = 10.6, west = 121.4, north = 12.1, east = 123.2, estimatedSizeMb = 300,
        ),
        RegionPreset(
            id = "iloilo", name = "Iloilo Strait & Guimaras",
            description = "Iloilo, Guimaras and the Panay Gulf",
            south = 10.20, west = 122.20, north = 11.60, east = 123.10, estimatedSizeMb = 240,
        ),
        RegionPreset(
            id = "cebu", name = "Cebu Coastal Area",
            description = "Cebu island, Bantayan and the Camotes Sea",
            south = 9.60, west = 123.35, north = 11.40, east = 124.35, estimatedSizeMb = 380,
        ),
        RegionPreset(
            id = "bohol", name = "Bohol Coastal Area",
            description = "Bohol, Panglao and the Cebu Strait",
            south = 9.35, west = 123.55, north = 10.25, east = 124.85, estimatedSizeMb = 300,
        ),
        RegionPreset(
            id = "leyte", name = "Leyte Gulf & Samar",
            description = "Leyte Gulf, Tacloban and western Samar",
            south = 10.40, west = 124.60, north = 12.30, east = 126.00, estimatedSizeMb = 350,
        ),
        RegionPreset(
            id = "surigao", name = "Surigao & Siargao",
            description = "Surigao Strait, Dinagat and Siargao",
            south = 9.0, west = 125.1, north = 10.5, east = 126.9, estimatedSizeMb = 260,
        ),
        RegionPreset(
            id = "cagayandeoro", name = "Cagayan de Oro",
            description = "Macajalar Bay and the Misamis coast",
            south = 8.0, west = 124.1, north = 9.3, east = 125.4, estimatedSizeMb = 220,
        ),
        RegionPreset(
            id = "dipolog", name = "Dipolog & Dapitan",
            description = "Zamboanga del Norte coast and Sibugay",
            south = 7.5, west = 121.6, north = 8.9, east = 123.7, estimatedSizeMb = 260,
        ),
        RegionPreset(
            id = "davao", name = "Davao Gulf",
            description = "Davao Gulf and Samal Island",
            south = 5.60, west = 125.20, north = 7.50, east = 126.30, estimatedSizeMb = 340,
        ),
        RegionPreset(
            id = "gensan", name = "General Santos & Sarangani",
            description = "Sarangani Bay and the GenSan coast",
            south = 5.2, west = 124.4, north = 6.9, east = 126.3, estimatedSizeMb = 280,
        ),
        RegionPreset(
            id = "ilana", name = "Illana Bay & Cotabato",
            description = "Illana Bay, Pagadian and Cotabato coast",
            south = 5.9, west = 122.9, north = 7.9, east = 124.9, estimatedSizeMb = 260,
        ),
        RegionPreset(
            id = "sulu", name = "Sulu Archipelago",
            description = "Jolo, Basilan and the Sulu Sea island chain",
            south = 4.8, west = 119.3, north = 7.5, east = 122.6, estimatedSizeMb = 320,
        ),
    )

    fun byId(id: String): RegionPreset? = all.firstOrNull { it.id == id }

    /** The whole-country preset, used to detect full-depth coverage. */
    const val WHOLE_COUNTRY_ID = "philippines"
}
