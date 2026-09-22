# RotaPro MVP 0.6

Android delivery-route planner prototype.

## New in 0.6
- Road-network route optimization through OSRM Trip API.
- Real road distance and travel-time estimate.
- Route geometry displayed on an in-app OpenStreetMap/Leaflet map.
- Numbered optimized stops.
- Button to open each stop in Google navigation / browser fallback.
- GPS/current-location support (uses the most recent device location when permission is granted).
- CSV import and manual delivery entry retained from earlier versions.

## Important
This MVP uses the public OSRM demo server and public OpenStreetMap tiles. They are appropriate for development/testing, not a commercial high-volume production deployment. A production version should use a contracted routing/map provider or self-hosted routing infrastructure.

## Build
Open the project in a current Android Studio, sync Gradle, then Build > Build APK(s). Minimum Android version: API 26 (Android 8).
