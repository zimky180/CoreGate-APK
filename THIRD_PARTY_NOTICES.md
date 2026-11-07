# Third-party notices

- AndroidX / Jetpack Compose: https://android.googlesource.com/platform/frameworks/support/ (Apache-2.0).
- Miuix: https://github.com/compose-miuix-ui/miuix (Apache-2.0).
- AndroidLiquidGlass: https://github.com/Kyant0/AndroidLiquidGlass (Apache-2.0). Adapted liquid components retain source headers.
- Material Kolor: https://github.com/jordond/MaterialKolor (MIT).

Dependency versions are recorded in gradle/libs.versions.toml. Upstream copyrights and licenses apply to third-party code; this project's MIT license applies to original project code.

## Duck-Detector-Refactoring contributor wall

Copyright 2026 Duck Apps Contributor. Apache License 2.0.
Source: https://github.com/eltavine/Duck-Detector-Refactoring/tree/8b365ba9ba2f0e434189c6b8c0d52dbddc161392/feature/settings/ui/src/main/kotlin/com/eltavine/duckdetector/features/settings/ui/components

Adapted ContributorWallCanvas, ContributorWallGeometry, ContributorWallMotion and ContributorWallEdgeFade into `ui/contributors/`. Original copyright and license headers are retained. Changes: CoreGate package, Miuix theme tokens, fixed local contributor order, local avatars and QQ profile actions; removed the upstream pager integration. Contributor details and update prompt follow the reference Miuix layout using CoreGate components. License: `LICENSES/Apache-2.0-Duck-Detector.txt`.