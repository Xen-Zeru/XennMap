package com.xennmap.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Core palette (dark ocean, from the design spec) ----
val OceanBackground = Color(0xFF06131D)
val OceanSurface = Color(0xFF0B1E2A)
val OceanSurfaceHigh = Color(0xFF10293A)
val OceanSurfaceGlass = Color(0xE60B1E2A) // surface with slight translucency
val Primary = Color(0xFF38A8FF)
val Secondary = Color(0xFF27D3C2)
val Safe = Color(0xFF35C98A)
val Warning = Color(0xFFFFB547)
val Danger = Color(0xFFFF5C5C)
val TextPrimary = Color(0xFFF5FAFF)
val TextSecondary = Color(0xFF91A7B5)
val OutlineDark = Color(0xFF1C3B4F)

// ---- Light theme counterparts ----
val LightBackground = Color(0xFFEEF4F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFE1EBF3)
val LightPrimary = Color(0xFF0B6BC2)
val LightSecondary = Color(0xFF0E9C90)
val LightTextPrimary = Color(0xFF0A1B28)
val LightTextSecondary = Color(0xFF527085)
val LightOutline = Color(0xFFC4D5E2)
val LightOceanWater = Color(0xFFA9D2E8)

// ---- Land / water colors for the map style ----
val MapWaterDark = Color(0xFF06131D)
val MapLandDark = Color(0xFF10293A)
val MapLandOutlineDark = Color(0xFF1E4258)
val MapWaterLight = Color(0xFFA9D2E8)
val MapLandLight = Color(0xFFF5F1E6)
val MapLandOutlineLight = Color(0xFFCFC4A8)

// ---- Bathymetry depth ramps (shallow -> deep) ----
// Dark theme: shallow cyan-teal, deep blends into the ocean background.
val BathyDarkRamp = listOf(
    Color(0xFF17607C), // 0–5 m
    Color(0xFF135163), // 5–10 m
    Color(0xFF104252), // 10–20 m
    Color(0xFF0D3544), // 20–50 m
    Color(0xFF0B2A38), // 50–100 m
    Color(0xFF09212D), // 100–200 m
    Color(0xFF071823), // 200 m+
)
// Light theme: nautical-paper style ramp.
val BathyLightRamp = listOf(
    Color(0xFFBCE5EF), // 0–5 m
    Color(0xFF9FD7E6), // 5–10 m
    Color(0xFF82C7DB), // 10–20 m
    Color(0xFF63AFCA), // 20–50 m
    Color(0xFF4A94B6), // 50–100 m
    Color(0xFF3978A0), // 100–200 m
    Color(0xFF2C5E86), // 200 m+
)

val BathyContourDark = Color(0xFF3BA3CC)
val BathyContourLight = Color(0xFF2C5E86)

// ---- Category colors for saved places ----
val CategoryColorFishing = Color(0xFF27D3C2)
val CategoryColorHome = Color(0xFF38A8FF)
val CategoryColorDock = Color(0xFF7FA8FF)
val CategoryColorDanger = Color(0xFFFF5C5C)
val CategoryColorWaypoint = Color(0xFFFFB547)
val CategoryColorFavorite = Color(0xFFFF7EB3)
val CategoryColorOther = Color(0xFF91A7B5)
