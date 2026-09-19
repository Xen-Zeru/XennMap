# XennMap

**Your Offline Sea Map Companion**

XennMap is an Android offline-first marine/coastal mapping application designed for
Filipino fishermen, boat operators, coastal travelers, and sea travelers who need
useful map and marine information even when internet or cellular connectivity is
unavailable.

[![Platform](https://img.shields.io/badge/platform-Android-green)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-orange)](https://kotlinlang.org/)
[![Release](https://img.shields.io/badge/release-v1.0.1-blue)](../../releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

---

## Download

**[Download the latest XennMap APK](../../releases/latest)** -- grab `app-release.apk`
from the release page, install it, and you're ready to sail.

> Android may ask for permission to install apps from your browser or file manager
> the first time. Allow it once and install as usual.

## What is XennMap?

Fishermen head out before dawn, far from cell towers. Maps that need data are
useless at sea -- and knowing the water depth where you drop anchor matters.

XennMap is an **offline-first marine companion**: the chart, depth data, saved spots
and navigation assistance keep working with **zero signal**. It works two ways:

* **"Where am I?"** -- live GPS position, charted depth under the boat, breadcrumb trails.
* **"Where do I want to go?"** -- pan the map, tap any point, save it, and navigate there tomorrow.

## Features

* **Offline-first marine maps** -- MapLibre-based chart with OpenStreetMap tiles; Philippine coastline and depth data bundled in the app
* **GPS location** -- position, accuracy, speed and heading, with or without Play Services
* **Real GEBCO bathymetry** -- charted depth visualization and depth queries based on GEBCO 2024 data (clearly labeled as charted depth, never live sonar)
* **Save any location** -- from your GPS position or by tapping anywhere on the map; fishing spots, home ports, docks, dangers, waypoints, favorites
* **Navigation assistance** -- distance, bearing, compass direction and estimated charted depth at your destination
* **Track recording** -- breadcrumb trails with distance and duration
* **Notify list** -- pin planned destinations to the Dashboard
* **Dark & Light modes** -- dark ocean theme and light paper-chart theme, plus system default
* **Private by design** -- everything stays on your device; nothing is uploaded, ever
* **Units** -- km/nautical miles, meters/feet/fathoms, km/h/knots

## GEBCO Bathymetry

XennMap uses **GEBCO 2024 bathymetry-derived data** for charted depth visualization
and depth queries. The data is based on real GEBCO measurements and models -- it is
**not** synthetic, random, or placeholder data.

**Important:**

* The bathymetry is based on real GEBCO data. It is not a live measurement.
* **GPS does not measure sea depth.** Your phone's GPS shows your position, not water depth.
* **GEBCO chart data is not live sonar data.** It is a pre-computed dataset from
  the GEBCO 2024 Grid (15 arc-second resolution).
* **Charted depth is not exact depth.** Real-world depth varies due to tides, sediment,
  seasonal changes, and other factors not captured in the GEBCO model.
* Always use the term **"Charted Depth"** when referring to depth data from XennMap.
* GEBCO data must **not** be described as exact, live, or measured depth.

The GEBCO 2024 dataset covers the entire Philippine archipelago (4N-21N, 116E-127E)
and is automatically provisioned when you first open the app -- no manual download
is required.

**GEBCO Attribution:**

> GEBCO Compilation Group (2024) GEBCO 2024 Grid
> (doi:10.5285/1c44ce99-0a0d-5f4f-e063-7086abc0ea0f)
>
> GEBCO data is released under the
> [Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/).
> See [GEBCO Conditions of Use](https://www.gebco.net/about_us/gridded_bathymetry_data/)
> for details.

## Offline Architecture

XennMap is designed to work fully offline after initial setup:

* **Map tiles** -- downloaded via MapLibre offline packs (OpenStreetMap-based)
* **Charted depth data** -- GEBCO 2024 binary grid tiles are bundled in the app
  and automatically provisioned to internal storage on first launch
* **Depth visualization** -- MBTiles-based raster overlay rendered by MapLibre

The GEBCO bathymetry data is provisioned internally by the application. Users do not
need to manually download bathymetry packages.

Works fully offline:

* Map rendering, GPS position, charted depth, saved places, navigation assistance, and track recording

Needs internet once:

* Downloading map tile areas (OpenStreetMap-based tiles)

## Navigation & Safety

XennMap is **navigation assistance only**. It does **not** replace:

* official nautical charts
* professional marine navigation systems
* safety equipment
* local maritime regulations
* weather information
* your own judgment at sea

The bathymetry shows charted depth data -- never a live sonar measurement from your
phone. Charted depth is for informational purposes and should not be treated as
live navigation-grade depth measurement. Always prioritize safety at sea and consult
official maritime sources for navigation decisions.

## Privacy

Saved fishing spots, waypoints, favorites, tracks and settings are **stored locally on
your device**. XennMap has no accounts, no analytics and no cloud sync -- your secret
spots stay yours.

## Technology

| | |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Map | MapLibre Native (no Google Maps dependency) |
| Architecture | MVVM + Clean Architecture (data / domain / presentation) |
| Async | Kotlin Coroutines & Flow |
| DI | Hilt |
| Storage | Room + DataStore |
| Background | WorkManager |
| Location | Play Services FusedLocation + AOSP LocationManager fallback |

## Architecture

```
app/src/main/java/com/xennmap/
  data/           Room, DataStore, location, offline maps, bathymetry, tracking
  domain/         models, repository interfaces, use cases
  presentation/   Compose screens, ViewModels, theme-driven shell
  ui/             theme + shared components
  di/             Hilt modules
  utils/          geodesy, formatting, GeoJSON
```

## Development

### Prerequisites

* JDK 17+
* Android Studio (latest stable recommended)
* Android SDK with platform 35

### Clone and Build

```bash
git clone https://github.com/Xen-Zeru/XennMap.git
cd XennMap
./gradlew assembleDebug
```

### Install Debug APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Run Tests

```bash
./gradlew test
```

No API keys are needed -- the map uses MapLibre with OpenStreetMap-based tiles from
[OpenFreeMap](https://openfreemap.org/) (respect their usage policy and the OSM
ODbL license).

Release builds read `keystore.properties` (gitignored) for signing. See
`app/build.gradle.kts` for the expected file format.

## Repository Structure

```
XennMap/
  app/                          Android application module
  gebco-pipeline/               Python preprocessing pipeline for GEBCO 2024 data
  gradle/                       Gradle wrapper + version catalog
  screenshots/                  App screenshots for documentation
  LICENSE                       MIT License (XennMap source code)
  README.md                     This file
  RELEASE_NOTES.md              Release notes
```

## License

**XennMap source code:** [MIT](LICENSE)

**Third-party data and assets:**

| Data | License |
|---|---|
| OpenStreetMap tiles | [ODbL](https://www.openstreetmap.org/copyright) (via OpenFreeMap) |
| GEBCO 2024 bathymetry | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| Philippine coastline | geoBoundaries (CC BY 4.0) |

The MIT license applies to XennMap source code only. Third-party datasets and assets
are subject to their own licenses as listed above.

---

*XennMap is a navigation-assistance app and does not replace official nautical charts
or professional marine navigation equipment. Always prioritize safety at sea.*
