# Changelog

User-visible changes to ArtCraft Hub. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- The Obtainium badge now follows the hub's stable releases only.

## [1.0.0] - 2026-11-21

First stable release.

### Added

- A dark, monochrome glass design. Every app orbits a counter of what's installed and what has
  updates, with filter tabs (All / Installed / Updates / Available) above a grid of apps.
- Apps you haven't installed are shown in grey; they take on their colours once installed.
- The ArtCraft logo as the launcher icon, with a themed-icon version for Android 13+.
- The app opens on the bare logo on a dark background, with no rounded tile.
- Layouts for small phones, foldables, tablets, landscape and large system fonts.

### Changed

- Release notes are shown without Markdown marks (`##`, `**`, link syntax).
- The details screen's header says "Details" instead of repeating the app's name.

### Fixed

- "No apps yet" no longer flashes while the saved list loads.
- When GitHub's secondary rate limit interrupts a refresh, the saved app list is kept instead of
  being replaced by an empty one.

## [0.1.0-beta] - 2026-11-20

First public build.

### Added

- Catalogue of the ArtCraft apps from GitHub releases, with icons, descriptions and release notes.
- Install, update, open and uninstall through Android's own installer. Downloads are checked
  against the release's `SHA256SUMS.txt` when there is one.
- The right APK for the phone's architecture (arm64-v8a, armeabi-v7a, x86_64).
- Sources: which GitHub links the hub checks, with import from an Obtainium export.
- "Track in Obtainium" for any app.

[Unreleased]: https://github.com/l41n-pr0t0/ArtCraftHub-android/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/l41n-pr0t0/ArtCraftHub-android/compare/v0.1.0-beta...v1.0.0
[0.1.0-beta]: https://github.com/l41n-pr0t0/ArtCraftHub-android/releases/tag/v0.1.0-beta
