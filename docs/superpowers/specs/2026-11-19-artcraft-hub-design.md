# ArtCraft Hub for Android — Design

Date: 2026-11-19 · Status: draft, awaiting approval · Licence: MIT

## 1. Goal

One Android app where people discover, install, update, open and uninstall every
app of the ArtCraft Android ecosystem (GitHub org `artcraft-android`). It is fast,
light on battery and needs no server of its own.

Non-goals for v1: accounts, a backend, Google Play distribution, AI features,
desktop builds, apps outside the `artcraft-android` org.

## 2. Decisions (agreed)

| Topic | Decision | Why |
|---|---|---|
| App list | User-managed **Sources**: pasted GitHub links (an org or a single repo). The `artcraft-android` org is pre-added; only `github.com/artcraft-android/...` links are accepted | The hub only checks what the user chose; new apps in the org still appear automatically; restricting to the org keeps it safe |
| Obtainium | Import sources from an Obtainium export file, and a "Track in Obtainium" button per app. The hub still installs and updates by itself | Users already use Obtainium; importing saves typing, the button keeps both tools in sync; not depending on Obtainium keeps the hub fully working without it |
| v1 features | Browse & install, update check, open, uninstall/manage | User choice |
| UI | Kotlin + Jetpack Compose screens, Rust core via UniFFI | Fastest start-up, native feel, battery-friendly; same proven structure as ProCraft |
| Betas | Prereleases are shown (every ArtCraft release is `-beta` today) | Otherwise the hub would be empty |

## 3. Architecture

```
┌──────────────────────── Android app (Kotlin) ────────────────────────┐
│ Compose UI  ──►  HubViewModel (StateFlow)                            │
│                    │             │                                    │
│                    ▼             ▼                                    │
│           Rust core (UniFFI)   AndroidBridge                         │
│           libhub_core.so       PackageManager / PackageInstaller /   │
│                                launch & uninstall intents            │
└──────────────────────────────────────────────────────────────────────┘
          │ HTTPS (rustls)
          ▼
   api.github.com  +  release asset downloads
```

Rule of thumb: **Rust decides, Kotlin touches Android.** Everything that is pure
logic or networking lives in Rust and is unit-tested on the desktop; Kotlin only
draws screens and calls Android system APIs that have no Rust equivalent.

### 3.1 Repository layout

```
Cargo.toml              workspace (edition 2024, ProCraft's strict lints)
crates/hub-core/        cdylib + lib, UniFFI exports
crates/hub-bindgen/     uniffi-bindgen binary (same pinned uniffi version)
android/                Gradle project (settings, app module)
android/app/            Kotlin sources, manifest, resources
docs/                   specs and plans
flake.nix               dev shell (done)
```

The Gradle build compiles Rust with `cargo ndk` and generates the Kotlin bindings
automatically (pattern adapted from ProCraft's `android/app/build.gradle.kts`; its
licence notice is kept on the adapted part).

### 3.2 Rust core (`hub-core`)

Modules:

- `github` — HTTP client (`ureq` + `rustls` + `webpki-roots`, no OpenSSL).
  Lists the repos behind each source (org → all its repos, repo → just that one)
  and their releases. Sends `If-None-Match` with stored ETags so
  unchanged data costs nothing against the 60 requests/hour anonymous limit.
- `sources` — parses and validates pasted links. Accepts
  `https://github.com/artcraft-android`, `https://github.com/artcraft-android/<repo>`
  (with or without `https://`, `www.`, trailing `/`, `.git`, `/releases...`) and
  `obtainium://add/<url>` links. Rejects anything outside the `artcraft-android`
  org with a clear message. Stores the list (default: the org) in the data dir.
  Also parses an Obtainium export JSON (`apps[].url`) and returns the ArtCraft
  repos found in it.
- `catalog` — turns repos + releases into `AppEntry` records: id, name,
  description, package name, latest version, prerelease flag, release notes,
  chosen asset. Skips repos with no APK asset and the hub itself.
- `assets` — picks the right APK for the phone from `Build.SUPPORTED_ABIS`
  (passed in from Kotlin). Handles the org's mixed naming: `*-android-arm64.apk`,
  `*-android-legacy-arm32.apk`, `*-armeabi-v7a.apk`, plain `*.apk` (universal /
  arm64). Ignores desktop and non-APK files. Pure function, heavily tested.
- `version` — parses tags such as `v0.0.7-beta` (semver, leading `v` optional)
  and compares them with the installed `versionName`.
- `download` — streams an asset to the app cache dir with progress callbacks and
  cancellation; verifies SHA-256 against `SHA256SUMS.txt` when the release has
  one, otherwise reports "unverified" to the UI.
- `cache` — stores the catalogue JSON and ETags in the app's files dir so the hub
  opens instantly offline and refreshes in the background.

Package name: `io.github.artcraftandroid.<repo name without "-android">`
(verified for all seven current apps). The hub also reads the package name from
the downloaded APK before installing, so a future app with a different name still
installs correctly.

UniFFI surface (sketch):

```rust
#[uniffi::export] fn hub_new(data_dir: String, cache_dir: String, abis: Vec<String>) -> Arc<Hub>;
impl Hub {
    fn sources(&self) -> Vec<Source>;
    fn add_source(&self, link: String) -> Result<Source, HubError>;     // validates
    fn remove_source(&self, id: String);
    fn import_obtainium(&self, json: String) -> Result<Vec<Source>, HubError>;
    fn obtainium_link(&self, app_id: String) -> String;   // obtainium://add/<repo url>
    fn cached_catalog(&self) -> Vec<AppEntry>;              // instant, offline
    fn refresh_catalog(&self) -> Result<Vec<AppEntry>, HubError>;
    fn download(&self, app_id: String, progress: Box<dyn DownloadProgress>)
        -> Result<DownloadedApk, HubError>;
    fn cancel_download(&self, app_id: String);
    fn update_available(&self, app_id: String, installed_version: String) -> bool;
}
```

Blocking calls run on Kotlin's `Dispatchers.IO`; the UI thread never waits on
Rust.

### 3.3 Kotlin app

- **Screens:** Catalogue (cards with icon, name, version, state chip and a primary
  button: Install / Update / Open), App detail (description, release notes,
  size, version, Uninstall, "Track in Obtainium"), **Sources** (list of links,
  paste box with Add, remove, "Import from Obtainium" file picker, reset to
  default), Settings (about, licences, clear cache).
- **AndroidBridge:**
  - *Installed state:* `PackageManager.getPackageInfo` per catalogue package.
    Package visibility on Android 11+ is granted with a `<queries>` block that
    lists the known package names plus a launcher-intent query, so no
    `QUERY_ALL_PACKAGES`.
  - *Install/update:* `PackageInstaller` session API; permission
    `REQUEST_INSTALL_PACKAGES`. The first time, Android sends the user to
    "Install unknown apps" for the hub.
  - *Open:* `getLaunchIntentForPackage`.
  - *Uninstall:* `Intent.ACTION_DELETE` (permission `REQUEST_DELETE_PACKAGES`).
  - *Obtainium:* "Track in Obtainium" opens `obtainium://add/<repo url>`; if
    Obtainium isn't installed, the button opens its download page. Import uses
    the system file picker (`ACTION_OPEN_DOCUMENT`), no storage permission.
  - Listens for package added/removed broadcasts to refresh state live.
- **Icons:** installed apps use their real icon from `PackageManager`; others use
  `fastlane/metadata/android/en-US/images/icon.png` from the repo when present,
  otherwise a generated letter tile.

### 3.4 Speed

- Baseline profile and R8 shrinking for the release build.
- Rust release profile: `lto = "fat"`, `codegen-units = 1`, `opt-level = 3`,
  `panic = "abort"`, stripped.
- Cold start shows the cached catalogue first; the network refresh happens after the
  first frame.
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64` (emulator); per-ABI APKs so each
  download stays small.

## 4. Error handling

Rust returns typed `HubError` variants (`InvalidLink`, `NotArtcraft`, `Offline`, `RateLimited { reset_at }`,
`NotFound`, `ChecksumMismatch`, `Io`, `Parse`); no panics (lints forbid
`unwrap`/`expect`/`panic`). Kotlin maps each one to a plain message with a retry.
Installer failures are surfaced too, notably *signature mismatch* ("this app was
installed from another source; uninstall it first to switch to the hub's copy").

## 5. Testing

- Rust unit tests: link parsing and validation (accepted and rejected forms),
  Obtainium export parsing, asset selection against the real asset names listed above,
  version comparison, catalogue building from recorded GitHub JSON fixtures,
  checksum parsing. Run with `cargo test` on the desktop.
- Kotlin: ViewModel tests with a fake core/bridge; one instrumented smoke test on
  the emulator (catalogue renders, install intent fires).
- CI (GitHub Actions): `cargo fmt --check`, `clippy -D warnings`, `cargo test`,
  Gradle `assembleRelease`.

## 6. Release

GitHub Releases on `l41n-pr0t0/ArtCraftHub-android`, per-ABI APKs and
`SHA256SUMS.txt` (same scheme as ProCraft) so the hub can also be tracked by
Obtainium. Signing key provided through environment variables, never committed.

## 7. Open item

- Application ID of the hub itself. It must not start with
  `io.github.artcraftandroid.` (that namespace belongs to the ArtCraft apps).
  Proposed: `io.github.l41n_pr0t0.artcrafthub` (matches the GitHub account that
  hosts the repo; `-` is not allowed in IDs so it becomes `_`; internal, never
  shown to users, cannot change after the first release).
