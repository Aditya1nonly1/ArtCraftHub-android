# ArtCraft Hub v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that lists the ArtCraft apps found through user-managed GitHub sources and installs, updates, opens and uninstalls them.

**Architecture:** A Rust crate `hub-core` (UniFFI, built with `cargo ndk`) does sources, GitHub, catalogue, asset choice, versions, downloads, checksums and caching. A Kotlin/Compose app draws the screens and calls Android's PackageManager/PackageInstaller. Kotlin reaches Rust only through two small interfaces (`CoreGateway`, `PackageGateway`) so the ViewModel is unit-testable on the JVM.

**Tech Stack:** Rust 1.100 (edition 2024), uniffi `=0.32.2`, ureq 3 (rustls, webpki roots), serde/serde_json, semver, sha2, hex, thiserror; AGP 9.2.1 (built-in Kotlin), Kotlin 2.3.21, Compose BOM 2026.09.00, JNA 5.19.1 (aar), Gradle 9.5.1, NDK 27.2.12479018, JDK 21.

**Spec:** `docs/superpowers/specs/2026-11-19-artcraft-hub-design.md`

## Global Constraints

- Work inside `nix develop` (provides Rust + Android targets, cargo-ndk, JDK 21, `ANDROID_HOME`, `ANDROID_NDK_HOME`).
- Licence MIT; any code adapted from ProCraft (`https://github.com/artcraft-android/procraft`, MIT OR Apache-2.0) keeps a comment `// Adapted from ProCraft (MIT OR Apache-2.0), https://github.com/artcraft-android/procraft`.
- Application ID / Kotlin namespace: `io.github.l41n_pr0t0.artcrafthub`. Generated bindings package: `io.github.l41n_pr0t0.artcrafthub.core`.
- compileSdk 37, targetSdk 36, minSdk 26, NDK `27.2.12479018`, `cargo ndk --platform 26`.
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`; Gradle property `hub.abis`, default `arm64-v8a,x86_64`.
- Rust lints copied from ProCraft's root `Cargo.toml`: `unsafe_code = "deny"`; clippy deny `unwrap_used`, `expect_used`, `panic`, `indexing_slicing`, `todo`, `unimplemented`, `unreachable` (tests may `#[allow]` them).
- Only links under `github.com/artcraft-android` are accepted as sources. Default sources: the org `artcraft-android`.
- Prereleases are shown. Drafts are not.
- ArtCraft package name rule: `io.github.artcraftandroid.` + repo name with a trailing `-android` removed.
- No OpenSSL, no `QUERY_ALL_PACKAGES`, no storage permission.
- Low disk (~11 GB free): never download extra NDKs/system images without asking.

## Review Focus

1. **`/releases/latest` hides prereleases** — every ArtCraft release is a prerelease, so using that endpoint would show an empty hub. Use `/releases?per_page=10` and take the first non-draft. Test in Task 4.
2. **GitHub rate limit (60/h)** — refreshing many times must not burn requests: unchanged responses must come back via ETag/304 from the cache, and a 403 with `x-ratelimit-remaining: 0` must become `RateLimited { reset_at }`, not "offline". Tests in Task 4.
3. **Pasted links in messy forms** — `github.com/artcraft-android/procraft/`, `https://www.github.com/artcraft-android/procraft.git`, `.../releases/tag/v1`, upper-case `ArtCraft-Android`, `obtainium://add/...`, surrounding whitespace — all must resolve; `github.com/evil/artcraft-android` must be rejected. Tests in Task 2.
4. **A release with no APK for this phone** (e.g. only desktop files, or only an arm64 APK on a 32-bit phone) — the app must still be listed with "No APK for this device" instead of disappearing or installing a wrong file. Tests in Tasks 3 and 5.
5. **App installed from elsewhere with another signing key** — updating fails with a conflict; the user must see the "uninstall it first" message, not a generic failure. Test in Task 9.

---

### Task 1: Rust workspace and version comparison

**Files:**
- Create: `Cargo.toml` (workspace, lints, release profile), `crates/hub-core/Cargo.toml`, `crates/hub-core/src/lib.rs`, `crates/hub-core/src/error.rs`, `crates/hub-core/src/version.rs`, `rustfmt.toml` (copy ProCraft's if present, else omit)

**Interfaces:**
- Produces:
  - `pub enum HubError` (`#[derive(Debug, thiserror::Error, uniffi::Error)]`) with variants `InvalidLink { reason: String }`, `NotArtcraft`, `Offline`, `RateLimited { reset_at: u64 }`, `NotFound`, `ChecksumMismatch`, `Cancelled`, `Io { message: String }`, `Parse { message: String }`; `From<std::io::Error>` and `From<serde_json::Error>`.
  - `pub fn is_newer(latest_tag: &str, installed: &str) -> bool` (also `#[uniffi::export]`).
  - `lib.rs` starts with `uniffi::setup_scaffolding!();`.

- [ ] **Step 1:** Root `Cargo.toml`: `[workspace] members = ["crates/*"]`, `resolver = "3"`, `[workspace.package] edition = "2024"`, `rust-version = "1.95"`, `license = "MIT"`; `[workspace.lints]` per Global Constraints; `[profile.release] opt-level = 3, lto = "fat", codegen-units = 1, panic = "abort", strip = true`. `hub-core`: `crate-type = ["cdylib", "lib"]`, `lints.workspace = true`, deps added with `cargo add` (uniffi `=0.32.2`, thiserror, serde+derive, serde_json, semver).
- [ ] **Step 2: Write failing tests** in `version.rs`:

```rust
#[test] fn beta_tag_newer_than_older_beta() { assert!(is_newer("v0.0.7-beta", "0.0.6-beta")); }
#[test] fn same_version_not_newer() { assert!(!is_newer("v0.0.7-beta", "0.0.7-beta")); }
#[test] fn stable_newer_than_its_beta() { assert!(is_newer("v1.0.0", "1.0.0-beta")); }
#[test] fn older_not_newer() { assert!(!is_newer("v0.0.6-beta", "0.0.7-beta")); }
#[test] fn two_part_versions_parse() { assert!(is_newer("v1.2", "1.1.9")); }
#[test] fn unparsable_installed_counts_as_update_when_different() { assert!(is_newer("v0.0.7-beta", "nightly-3")); }
#[test] fn unparsable_equal_strings_not_newer() { assert!(!is_newer("weird", "weird")); }
```

- [ ] **Step 3:** Run `cargo test -p hub-core version` → FAIL (not defined).
- [ ] **Step 4:** Implement: strip leading `v`/`V`; pad missing minor/patch with `.0` before the `-` suffix; parse with `semver::Version`; if either side fails, return `latest_tag.trim_start_matches(['v','V']) != installed`.
- [ ] **Step 5:** `cargo test -p hub-core && cargo clippy -p hub-core --all-targets -- -D warnings` → all pass, no warnings.
- [ ] **Step 6:** Commit `feat(core): workspace, errors, version comparison`.

### Task 2: Sources — link parsing and Obtainium import

**Files:**
- Create: `crates/hub-core/src/sources.rs`, `crates/hub-core/tests/fixtures/obtainium-export.json`

**Interfaces:**
- Consumes: `HubError` (Task 1).
- Produces:
  - `pub const ORG: &str = "artcraft-android";`
  - `#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq, Serialize, Deserialize)] pub struct Source { pub id: String, pub repo: Option<String>, pub url: String }` — `id` is `"artcraft-android"` for the org or `"artcraft-android/<repo>"`; `url` is the canonical `https://github.com/artcraft-android[/<repo>]`; `repo` keeps the repo's case as typed but lower-cased org.
  - `pub fn parse_link(input: &str) -> Result<Source, HubError>`
  - `pub fn parse_obtainium_export(json: &str) -> Result<Vec<Source>, HubError>`
  - `pub fn default_sources() -> Vec<Source>`
  - `pub fn obtainium_link(repo_url: &str) -> String` → `format!("obtainium://add/{repo_url}")`

- [ ] **Step 1: Write failing tests** (table-driven):

```rust
#[test] fn accepts_link_forms() {
    for (input, id) in [
        ("https://github.com/artcraft-android", "artcraft-android"),
        ("github.com/artcraft-android/", "artcraft-android"),
        ("  https://www.github.com/artcraft-android/procraft.git ", "artcraft-android/procraft"),
        ("http://github.com/artcraft-android/pdfcraft-android/releases/tag/v0.0.7-beta", "artcraft-android/pdfcraft-android"),
        ("https://github.com/ArtCraft-Android/procraft", "artcraft-android/procraft"),
        ("obtainium://add/https://github.com/artcraft-android/procraft", "artcraft-android/procraft"),
        ("https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/artcraft-android/procraft", "artcraft-android/procraft"),
    ] { assert_eq!(parse_link(input).unwrap().id, id, "{input}"); }
}
#[test] fn canonical_url() { assert_eq!(parse_link("github.com/artcraft-android/procraft/").unwrap().url, "https://github.com/artcraft-android/procraft"); }
#[test] fn rejects_other_owner() { assert!(matches!(parse_link("https://github.com/evil/artcraft-android"), Err(HubError::NotArtcraft))); }
#[test] fn rejects_other_host() { assert!(matches!(parse_link("https://gitlab.com/artcraft-android/procraft"), Err(HubError::NotArtcraft))); }
#[test] fn rejects_garbage() { assert!(matches!(parse_link("hello"), Err(HubError::InvalidLink { .. }))); assert!(matches!(parse_link(""), Err(HubError::InvalidLink { .. }))); }
#[test] fn obtainium_export_keeps_only_artcraft_and_dedups() {
    let s = parse_obtainium_export(include_str!("../tests/fixtures/obtainium-export.json")).unwrap();
    assert_eq!(s.iter().map(|s| s.id.as_str()).collect::<Vec<_>>(), ["artcraft-android/procraft", "artcraft-android/pdfcraft-android"]);
}
#[test] fn obtainium_export_bad_json_is_parse_error() { assert!(matches!(parse_obtainium_export("{"), Err(HubError::Parse { .. }))); }
#[test] fn default_is_org() { assert_eq!(default_sources()[0].id, "artcraft-android"); }
```

Fixture: `{"apps":[{"id":"io.github.artcraftandroid.procraft","url":"https://github.com/artcraft-android/procraft","name":"ProCraft"},{"id":"org.other","url":"https://github.com/someone/else"},{"id":"x","url":"https://github.com/artcraft-android/pdfcraft-android"},{"id":"dup","url":"https://github.com/artcraft-android/procraft/"}],"settings":{}}` — unknown fields ignored.

- [ ] **Step 2:** Run `cargo test -p hub-core sources` → FAIL.
- [ ] **Step 3:** Implement without a URL crate: trim; if it contains `obtainium://add/`, take the text after it; strip scheme `http(s)://`, then `www.`; require host `github.com` (case-insensitive) else `NotArtcraft` — but input with no `/` and no dot is `InvalidLink`; split path on `/`, drop empty parts; owner must equal `ORG` case-insensitively else `NotArtcraft`; second segment (if any) minus `.git` is the repo; later segments ignored. Export: deserialize `{ apps: Vec<{ url: String }> }`, `parse_link` each, skip errors, dedup by `id` keeping first.
- [ ] **Step 4:** `cargo test -p hub-core sources` → PASS; clippy clean.
- [ ] **Step 5:** Commit `feat(core): source links and Obtainium export import`.

### Task 3: APK choice and checksum lists

**Files:**
- Create: `crates/hub-core/src/assets.rs`

**Interfaces:**
- Produces:
  - `#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)] pub struct ReleaseAsset { pub name: String, pub url: String, pub size: u64 }`
  - `pub fn pick_apk<'a>(assets: &'a [ReleaseAsset], abis: &[String]) -> Option<&'a ReleaseAsset>`
  - `pub fn parse_sha256sums(text: &str) -> std::collections::HashMap<String, String>` (file name → lower-case hex)

- [ ] **Step 1: Write failing tests.** Helper `a(name)` builds an asset. Asset sets are the real names:

```rust
let procraft = ["procraft-0.0.7-beta-android-arm64.apk", "procraft-0.0.7-beta-android-legacy-arm32.apk", "procraft-0.0.7-beta-linux-x86_64.AppImage", "procraft-0.0.7-beta-windows-x64.msi", "SHA256SUMS.txt"];
let pdfcraft = ["pdfcraft-android-0.0.7-beta.apk", "pdfcraft-android-0.0.7-beta-armeabi-v7a.apk"];
let capcraft = ["capcraft-android-0.0.4-beta.apk"];
```

Tests: `arm64_phone_gets_arm64` (procraft, abis `["arm64-v8a","armeabi-v7a","armeabi"]` → `…-android-arm64.apk`); `old_32bit_phone_gets_legacy` (procraft, `["armeabi-v7a","armeabi"]` → `…legacy-arm32.apk`); `pdfcraft_arm64_gets_plain` (→ `pdfcraft-android-0.0.7-beta.apk`); `pdfcraft_32bit_gets_v7a`; `single_apk_for_everyone` (capcraft on `["x86_64"]` → the one APK); `no_apk_at_all` (only `.AppImage` + `.msi` → `None`); `arm64_only_release_on_32bit_phone_is_none` (only `…-android-arm64.apk`, abis `["armeabi-v7a"]` → `None`); `apk_suffix_case_insensitive` (`X.APK` picked). Sums: `"abc123  procraft.apk\nDEF456 *other.apk\n\n# c\n"` → `{"procraft.apk":"abc123","other.apk":"def456"}`.

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. Only names ending `.apk` (case-insensitive) count. Classify by lower-cased name: contains `arm64` or `aarch64` → `arm64-v8a`; contains `armeabi-v7a`, `arm32` or `armv7` → `armeabi-v7a`; contains `x86_64` → `x86_64`; otherwise *universal*. For each device ABI in order, return the first APK of that class; if none matches, return the first universal APK; else `None`.
- [ ] **Step 4:** Run → PASS; clippy clean.
- [ ] **Step 5:** Commit `feat(core): APK choice per ABI and SHA256SUMS parsing`.

### Task 4: GitHub client with ETag cache

**Files:**
- Create: `crates/hub-core/src/github.rs`, `crates/hub-core/src/http.rs`, `crates/hub-core/tests/fixtures/org-repos.json`, `crates/hub-core/tests/fixtures/procraft-releases.json`

**Interfaces:**
- Consumes: `Source`, `ORG` (Task 2); `ReleaseAsset` (Task 3); `HubError`.
- Produces:
  - `pub enum HttpResponse { Ok { body: String, etag: Option<String> }, NotModified }`
  - `pub trait Http: Send + Sync { fn get(&self, url: &str, etag: Option<&str>) -> Result<HttpResponse, HubError>; fn open(&self, url: &str) -> Result<(Box<dyn std::io::Read + Send>, Option<u64>), HubError>; }`
  - `pub struct UreqHttp` implementing `Http` (User-Agent `ArtCraftHub-android`, `Accept: application/vnd.github+json` for api.github.com, 30 s timeouts).
  - `pub struct GhRepo { pub name: String, pub description: Option<String>, pub html_url: String, pub archived: bool, pub fork: bool }`; `pub struct GhRelease { pub tag_name: String, pub body: Option<String>, pub prerelease: bool, pub draft: bool, pub assets: Vec<ReleaseAsset> }` (serde maps `browser_download_url`→`url`; all `Deserialize + Serialize + Clone`).
  - `pub struct GithubClient { http: Arc<dyn Http>, cache_path: PathBuf }` with `pub fn new(http: Arc<dyn Http>, cache_dir: &Path) -> Self`, `pub fn repos(&self, source: &Source) -> Result<Vec<GhRepo>, HubError>`, `pub fn latest_release(&self, repo: &str) -> Result<Option<GhRelease>, HubError>`.

- [ ] **Step 1: Write failing tests** with a `FakeHttp` (map url → queued responses, records requests and the `etag` it was given):
  - `org_source_lists_repos`: `GET https://api.github.com/orgs/artcraft-android/repos?per_page=100` returns the fixture → names include `procraft`; archived and fork repos filtered out.
  - `repo_source_fetches_one`: source `artcraft-android/procraft` → `GET https://api.github.com/repos/artcraft-android/procraft` → one repo.
  - `prerelease_is_latest`: `GET .../repos/artcraft-android/procraft/releases?per_page=10` fixture whose first entry is a draft, second a prerelease `v0.0.7-beta` → returns `v0.0.7-beta`; empty list → `None`.
  - `second_call_sends_etag_and_uses_cache_on_304`: first `Ok{etag:Some("\"x\"")}`, second `NotModified` → second call passes `Some("\"x\"")` and returns the same data; a new `GithubClient` on the same cache dir also sends it (persisted).
  - `not_modified_without_cache_is_refetched_without_etag`: cache file deleted → no etag sent.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. Cache: one JSON file `cache_dir/github-cache.json`, map url → `{etag, body}`, rewritten after each `Ok` with an etag (write to `.tmp` then rename). `UreqHttp` status mapping: 200 → `Ok`; 304 → `NotModified`; 403/429 with header `x-ratelimit-remaining: 0` → `RateLimited { reset_at: x-ratelimit-reset }`; 404 → `NotFound`; other status → `Io { message: "HTTP <code>" }`; transport/DNS/TLS errors → `Offline`. Configure ureq to not treat non-2xx as errors (`http_status_as_error(false)`).
- [ ] **Step 4:** Add unit test for the header mapping as a pure `fn classify(status: u16, remaining: Option<&str>, reset: Option<&str>) -> Option<HubError>`: `(403, Some("0"), Some("1700000000"))` → `RateLimited{reset_at:1700000000}`; `(403, Some("5"), _)` → `Io`; `(404,..)` → `NotFound`.
- [ ] **Step 5:** Run → PASS; clippy clean.
- [ ] **Step 6:** Commit `feat(core): GitHub client with ETag cache`.

### Task 5: Catalogue entries

**Files:**
- Create: `crates/hub-core/src/catalog.rs`

**Interfaces:**
- Consumes: `GhRepo`, `GhRelease` (Task 4), `pick_apk` (Task 3).
- Produces:
  - `#[derive(uniffi::Record, Clone, Debug, PartialEq, Serialize, Deserialize)] pub struct ApkInfo { pub file_name: String, pub url: String, pub size: u64, pub sums_url: Option<String> }`
  - `#[derive(uniffi::Record, ...same)] pub struct AppEntry { pub id: String, pub name: String, pub description: String, pub package_name: String, pub repo_url: String, pub latest_version: String, pub prerelease: bool, pub release_notes: String, pub apk: Option<ApkInfo>, pub icon_url: String }`
  - `pub fn build_entry(repo: &GhRepo, release: &GhRelease, abis: &[String]) -> AppEntry`
  - `pub fn display_name(repo: &str) -> String`, `pub fn package_name(repo: &str) -> String`

- [ ] **Step 1: Write failing tests:** `display_name("procraft") == "ProCraft"`, `display_name("lightcraft-android") == "LightCraft"`, `display_name("pdfcraft-android") == "PdfCraft"`; `package_name("pdfcraft-android") == "io.github.artcraftandroid.pdfcraft"`, `package_name("procraft") == "io.github.artcraftandroid.procraft"`; `entry_from_procraft_fixture` (Task 4 fixture, abis arm64) → `id "procraft"`, `latest_version "v0.0.7-beta"`, `prerelease true`, `apk.file_name` ends `-android-arm64.apk`, `sums_url` is the `SHA256SUMS.txt` asset url, `icon_url == "https://raw.githubusercontent.com/artcraft-android/procraft/HEAD/fastlane/metadata/android/en-US/images/icon.png"`; `release_without_apk_still_listed` → `apk == None`; `missing_description_is_empty`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. `display_name`: strip `-android`, upper-case first char, upper-case the `c` of a trailing `craft`. `sums_url`: asset named `SHA256SUMS.txt` (case-insensitive).
- [ ] **Step 4:** Run → PASS; clippy clean.
- [ ] **Step 5:** Commit `feat(core): catalogue entries`.

### Task 6: Downloads with progress, cancel and checksum

**Files:**
- Create: `crates/hub-core/src/download.rs`

**Interfaces:**
- Consumes: `Http::open` (Task 4), `parse_sha256sums` (Task 3), `ApkInfo` (Task 5).
- Produces:
  - `#[uniffi::export(with_foreign)] pub trait DownloadProgress: Send + Sync { fn on_progress(&self, downloaded: u64, total: u64); }`
  - `#[derive(uniffi::Record)] pub struct DownloadedApk { pub path: String, pub verified: bool }`
  - `pub fn copy_hashing(reader: &mut dyn Read, writer: &mut dyn Write, total: u64, progress: &dyn DownloadProgress, cancel: &AtomicBool) -> Result<String, HubError>` (returns lower-case hex SHA-256)
  - `pub fn download_apk(http: &dyn Http, apk: &ApkInfo, dir: &Path, progress: &dyn DownloadProgress, cancel: &AtomicBool) -> Result<DownloadedApk, HubError>`

- [ ] **Step 1: Write failing tests:** `copy_hashing` of `b"hello"` returns `"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"`, writes the bytes, last progress call is `(5, 5)`; cancel flag set before start → `Err(Cancelled)`; `download_apk` with `FakeHttp` serving the APK bytes and a sums file containing the matching hash → `verified: true`, file exists at `dir/<file_name>`; wrong hash → `Err(ChecksumMismatch)` and the file is deleted; `sums_url: None` → `verified: false`; sums file lacking that name → `verified: false`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement with `sha2::Sha256` + `hex`; 64 KiB buffer; check `cancel` each chunk; report progress at most every 64 KiB and once at the end; write to `<file_name>.part`, rename on success, remove on any error.
- [ ] **Step 4:** Run → PASS; clippy clean.
- [ ] **Step 5:** Commit `feat(core): verified downloads`.

### Task 7: `Hub` facade, persistence, icons, bindgen

**Files:**
- Create: `crates/hub-core/src/hub.rs`, `crates/hub-core/uniffi.toml`, `crates/hub-bindgen/Cargo.toml`, `crates/hub-bindgen/src/main.rs`
- Modify: `crates/hub-core/src/lib.rs` (module declarations, re-exports)

**Interfaces:**
- Consumes: everything above.
- Produces (UniFFI object, all methods blocking — Kotlin calls them on `Dispatchers.IO`):

```rust
#[derive(uniffi::Object)] pub struct Hub { /* sources: Mutex<Vec<Source>>, github, http, dirs, abis, catalog: Mutex<Vec<AppEntry>>, cancels: Mutex<HashMap<String, Arc<AtomicBool>>> */ }
#[uniffi::export] impl Hub {
    #[uniffi::constructor] pub fn new(data_dir: String, cache_dir: String, abis: Vec<String>) -> Arc<Self>;
    pub fn sources(&self) -> Vec<Source>;
    pub fn add_source(&self, link: String) -> Result<Source, HubError>;   // dedups by id; returns the existing one
    pub fn remove_source(&self, id: String);
    pub fn reset_sources(&self) -> Vec<Source>;
    pub fn import_obtainium(&self, json: String) -> Result<Vec<Source>, HubError>; // returns newly added only
    pub fn cached_catalog(&self) -> Vec<AppEntry>;
    pub fn refresh_catalog(&self) -> Result<Vec<AppEntry>, HubError>;
    pub fn download(&self, app_id: String, progress: Arc<dyn DownloadProgress>) -> Result<DownloadedApk, HubError>;
    pub fn cancel_download(&self, app_id: String);
    pub fn icon(&self, app_id: String) -> Option<Vec<u8>>;
    pub fn clear_cache(&self);   // deletes cache_dir contents (catalogue, ETags, icons, downloads); sources stay
}
#[uniffi::export] pub fn obtainium_link(repo_url: String) -> String; // and is_newer from Task 1
```

`uniffi.toml`: `[bindings.kotlin] package_name = "io.github.l41n_pr0t0.artcrafthub.core"`. `hub-bindgen/src/main.rs`: `fn main() { uniffi::uniffi_bindgen_main() }` with uniffi `=0.32.2` feature `cli`.

- [ ] **Step 1: Write failing tests** (temp dirs via `std::env::temp_dir()` + unique name; `Hub` gets a test-only constructor `with_http(…, Arc<dyn Http>)`):
  - `sources_persist`: add `github.com/artcraft-android/procraft`, drop, new `Hub` on same dirs → sources = `[org, procraft]`; adding the same link again keeps 2.
  - `first_run_has_default`: empty data dir → `[org]`.
  - `refresh_merges_sources_and_dedups`: sources org + `artcraft-android/procraft` → `procraft` appears once; results sorted by `name`.
  - `refresh_failure_keeps_cache`: after one good refresh, `FakeHttp` returns `Offline` → `Err(Offline)`, `cached_catalog()` still has the apps (also after re-creating `Hub`).
  - `one_repo_failing_does_not_hide_others`: releases for one repo → `NotFound` → other apps still returned.
  - `icon_404_is_none_and_cached_bytes_reused`.
  - `clear_cache_keeps_sources`: after refresh + `clear_cache()` → `cached_catalog()` empty, `sources()` unchanged.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. Files: `data_dir/sources.json`, `cache_dir/catalog.json`, `cache_dir/icons/<id>.png`, downloads into `cache_dir/downloads/`. `refresh_catalog`: union of repos across sources (dedup by name), `latest_release` per repo, skip repos with no release, per-repo errors other than `RateLimited`/`Offline` are skipped; `RateLimited`/`Offline` abort and return the error. Icons: `Http::open(icon_url)`, `NotFound` → `None`.
- [ ] **Step 4:** Run `cargo test` (whole workspace) → PASS; clippy clean.
- [ ] **Step 5:** Verify Android build + bindings by hand: `cargo ndk -t arm64-v8a --platform 26 -o /tmp/claude-…/scratchpad/jni build --release -p hub-core --lib` then `cargo run -p hub-bindgen -- generate <that>/arm64-v8a/libhub_core.so --language kotlin --out-dir <scratch>/kt` → a `hub_core.kt` containing `class Hub` and `package io.github.l41n_pr0t0.artcrafthub.core`.
- [ ] **Step 6:** Commit `feat(core): Hub facade, persistence, icons, bindgen`.

### Task 8: Android project that builds Rust and shows the catalogue

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/gradle/wrapper/*`, `android/gradlew`, `android/gradlew.bat`, `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/kotlin/io/github/l41n_pr0t0/artcrafthub/MainActivity.kt`, `.../core/CoreGateway.kt`, `android/app/src/main/res/values/strings.xml`, launcher icon (adaptive, simple vector)

**Interfaces:**
- Consumes: generated `io.github.l41n_pr0t0.artcrafthub.core.Hub`, `AppEntry`, `Source`, `HubException` (UniFFI's Kotlin name for `HubError`).
- Produces:

```kotlin
interface CoreGateway {
    fun sources(): List<Source>; fun addSource(link: String): Source; fun removeSource(id: String); fun resetSources(): List<Source>
    fun importObtainium(json: String): List<Source>
    fun cachedCatalog(): List<AppEntry>; fun refreshCatalog(): List<AppEntry>
    fun download(appId: String, onProgress: (Long, Long) -> Unit): DownloadedApk; fun cancelDownload(appId: String)
    fun icon(appId: String): ByteArray?; fun clearCache()
}
class RustCore(context: Context) : CoreGateway  // wraps Hub(filesDir, cacheDir, Build.SUPPORTED_ABIS.toList())
```

- [ ] **Step 1:** Gradle files: copy ProCraft's `settings.gradle.kts` repositories block, `libs.versions.toml` (drop motionprediction; add `androidx-profileinstaller`, `kotlinx-coroutines-test`, `androidx-lifecycle-runtime-compose`), wrapper `gradle-9.5.1-bin.zip` (`gradle wrapper` from `nix shell nixpkgs#gradle` or copy ProCraft's wrapper jar + scripts). `rootProject.name = "ArtCraftHub"`.
- [ ] **Step 2:** `app/build.gradle.kts`: adapt ProCraft's `CargoNdkBuild` and `UniffiBindgen` task classes and `androidComponents.onVariants` wiring (keep the adaptation comment), pointing at `-p hub-core` / `-p hub-bindgen`, workspace root `..`, ABIs from `hub.abis`. `namespace`/`applicationId` per Global Constraints; versionName `0.1.0`, versionCode via ProCraft's ×10 + ABI digit scheme; `ndkVersion = "27.2.12479018"`; JNA `@aar`; release: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt` + `proguard-rules.pro` (keep `com.sun.jna.**`, `* implements com.sun.jna.**`, `io.github.l41n_pr0t0.artcrafthub.core.**`, `-dontwarn java.awt.**`); debug-signed release when no keystore env vars (`HUB_KEYSTORE`, `HUB_KEYSTORE_PASSWORD`, `HUB_KEY_ALIAS`, `HUB_KEY_PASSWORD`).
- [ ] **Step 3:** Manifest: permissions `INTERNET`, `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`; `<queries>` with `<package>` for the seven `io.github.artcraftandroid.{procraft,pdfcraft,lightcraft,gridcraft,wordcraft,capcraft,filmcraft}` and `<intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent>`; one `MainActivity`.
- [ ] **Step 4:** `MainActivity` temporary screen: `LazyColumn` of `cachedCatalog()` then `refreshCatalog()` on `Dispatchers.IO`, showing name + `latestVersion` or the error text.
- [ ] **Step 5:** Run `cd android && ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL; `unzip -l app/build/outputs/apk/debug/*.apk | grep libhub_core.so` lists `arm64-v8a` and `x86_64`.
- [ ] **Step 6:** On a device (ask the user to connect their phone with USB debugging; `adb devices` shows it): `./gradlew :app:installDebug`, open the app → list shows the seven apps with versions. If no device is available, record that and continue.
- [ ] **Step 7:** Commit `feat(android): Gradle project building the Rust core`.

### Task 9: Package gateway (installed state, install, open, uninstall)

**Files:**
- Create: `android/app/src/main/kotlin/io/github/l41n_pr0t0/artcrafthub/system/PackageGateway.kt`, `.../system/AndroidPackageGateway.kt`, `.../system/InstallResultReceiver.kt`, `.../system/PackageEvents.kt`, `android/app/src/test/kotlin/io/github/l41n_pr0t0/artcrafthub/system/InstallResultTest.kt`
- Modify: `AndroidManifest.xml` (register receiver, non-exported)

**Interfaces:**
- Produces:

```kotlin
sealed interface InstallResult { data object Success : InstallResult; data object Cancelled : InstallResult; data object SignatureConflict : InstallResult; data class Failed(val message: String) : InstallResult }
fun installResultFrom(status: Int, message: String?): InstallResult   // pure, maps PackageInstaller.STATUS_*
interface PackageGateway {
    fun installedVersion(packageName: String): String?      // versionName or null
    fun icon(packageName: String): ImageBitmap?
    fun archivePackageName(apkPath: String): String?        // getPackageArchiveInfo
    fun canInstall(): Boolean                               // packageManager.canRequestPackageInstalls()
    fun openInstallPermissionSettings()                     // ACTION_MANAGE_UNKNOWN_APP_SOURCES for our package
    suspend fun install(apkPath: String): InstallResult     // PackageInstaller session
    fun launch(packageName: String): Boolean
    fun uninstall(packageName: String)                      // ACTION_DELETE, package: URI
    fun changes(): Flow<String>                             // package added/replaced/removed, emits package name
}
```

- [ ] **Step 1: Failing JVM test** `InstallResultTest`: `STATUS_SUCCESS`→`Success`; `STATUS_FAILURE_ABORTED`→`Cancelled`; `STATUS_FAILURE_CONFLICT`→`SignatureConflict`; `STATUS_FAILURE_INCOMPATIBLE`→`Failed`; `STATUS_FAILURE` with message `"x"` → `Failed("x")`. (Use the literal ints from the Android docs as constants, since `android.jar` stubs are not loaded in JVM tests: SUCCESS 0, FAILURE 1, BLOCKED 2, ABORTED 3, INVALID 4, CONFLICT 5, STORAGE 6, INCOMPATIBLE 7.)
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest` → FAIL.
- [ ] **Step 3:** Implement. `install`: create session (`MODE_FULL_INSTALL`, `setAppPackageName(archivePackageName)`), copy the file in, commit with a mutable `PendingIntent` to `InstallResultReceiver`; the receiver forwards `STATUS_PENDING_USER_ACTION` by starting `EXTRA_INTENT` (with `FLAG_ACTIVITY_NEW_TASK`) and delivers final statuses to a `CompletableDeferred` keyed by session id. `changes()`: `callbackFlow` registering a receiver for `ACTION_PACKAGE_ADDED/REPLACED/REMOVED` with `package` data scheme.
- [ ] **Step 4:** Tests pass; `assembleDebug` succeeds.
- [ ] **Step 5:** Commit `feat(android): package gateway`.

### Task 10: ViewModel, catalogue and detail screens

**Files:**
- Create: `.../ui/HubViewModel.kt`, `.../ui/AppState.kt`, `.../ui/ErrorText.kt`, `.../ui/CatalogScreen.kt`, `.../ui/DetailScreen.kt`, `.../ui/AppIcon.kt`, `.../ui/theme/Theme.kt`, `android/app/src/test/kotlin/.../ui/AppStateTest.kt`, `.../ui/HubViewModelTest.kt`, `.../ui/ErrorTextTest.kt`, `.../ui/Fakes.kt`
- Modify: `MainActivity.kt` (replace temporary screen)

**Interfaces:**
- Consumes: `CoreGateway` (Task 8), `PackageGateway`, `InstallResult` (Task 9), generated `isNewer`.
- Produces:

```kotlin
sealed interface AppState { data object NotInstalled; data class Installed(val version: String); data class UpdateAvailable(val installed: String); data class Downloading(val fraction: Float); data object Installing; data object NoApk }
fun appState(entry: AppEntry, installedVersion: String?, isNewer: (String, String) -> Boolean): AppState
data class AppUi(val entry: AppEntry, val state: AppState)
data class HubUiState(val apps: List<AppUi> = emptyList(), val sources: List<Source> = emptyList(), val refreshing: Boolean = false, val message: String? = null, val selectedId: String? = null)
class HubViewModel(core: CoreGateway, packages: PackageGateway, io: CoroutineDispatcher, isNewer: (String, String) -> Boolean) : ViewModel {
    val state: StateFlow<HubUiState>
    fun refresh(); fun select(id: String?); fun primaryAction(id: String) /* install, update or open */; fun cancel(id: String); fun uninstall(id: String); fun dismissMessage()
    fun addSource(link: String); fun removeSource(id: String); fun resetSources(); fun importObtainium(json: String)
}
fun errorText(e: Throwable): String
```

- [ ] **Step 1: Failing tests.**
  - `AppStateTest`: `apk == null` and not installed → `NoApk`; not installed → `NotInstalled`; installed `0.0.6-beta`, latest `v0.0.7-beta` → `UpdateAvailable("0.0.6-beta")`; installed equal → `Installed`; installed but `apk == null` → `Installed` (can still open).
  - `ErrorTextTest`: `RateLimited(reset_at)` → `"GitHub limit reached. Try again after HH:mm."` (local time); `Offline` → `"No internet connection."`; `NotArtcraft` → `"Only links from github.com/artcraft-android can be added."`; `InvalidLink` → `"That doesn't look like a GitHub link."`; `ChecksumMismatch` → `"The download was damaged. Please try again."`; signature conflict text (from `InstallResult.SignatureConflict`) → `"This app was installed from another source. Uninstall it first, then install it here."`.
  - `HubViewModelTest` (`StandardTestDispatcher`, `Dispatchers.setMain`, fakes): on init shows cached apps, then refreshed ones; refresh error keeps apps and sets `message`; `primaryAction` on `NotInstalled` downloads then installs and moves through `Downloading`→`Installing`→`Installed`; when `canInstall()` is false it calls `openInstallPermissionSettings()` and does not download; `primaryAction` on `Installed` calls `launch`; `SignatureConflict` sets the conflict message; a `changes()` emission re-reads `installedVersion` for that package; `addSource` with a `NotArtcraft` error sets the message and leaves sources unchanged.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. If `archivePackageName` differs from `entry.packageName`, remember it in `SharedPreferences` `package_overrides` (repo id → package) via a tiny `PackageNames` helper passed into the ViewModel, and use it for state. Screens: Material 3, dark/light by system; Catalogue = `LazyColumn` of cards (icon, name, version, state chip, one primary button: Install/Update/Open/"No APK for this device" disabled, progress bar while downloading); pull-to-refresh; top bar action to Sources. Detail = description, release notes (plain text), size (MB, 1 decimal), version, Uninstall when installed, "Track in Obtainium" (Task 11). Back via `BackHandler` from `selectedId`. `AppIcon`: installed → `packages.icon`, else `core.icon` bytes decoded with `BitmapFactory` off the main thread, else a letter tile.
- [ ] **Step 4:** Tests pass; `assembleDebug`; on device: install, open and uninstall one app (e.g. CapCraft), check the update button appears after installing an older release manually if one exists.
- [ ] **Step 5:** Commit `feat(android): catalogue and detail screens`.

### Task 11: Sources, Settings and Obtainium

**Files:**
- Create: `.../ui/SourcesScreen.kt`, `.../ui/SettingsScreen.kt`, `.../system/Obtainium.kt`
- Modify: `DetailScreen.kt`, `HubViewModel.kt` (if not already complete), `HubViewModelTest.kt`

**Interfaces:**
- Consumes: ViewModel source functions (Task 10), `obtainiumLink` (generated, Task 7).
- Produces: `fun openInObtainium(context: Context, repoUrl: String)` — `ACTION_VIEW` on `obtainiumLink(repoUrl)`; on `ActivityNotFoundException` open `https://github.com/ImranR98/Obtainium/releases`.

- [ ] **Step 1: Failing tests** in `HubViewModelTest`: `importObtainium` adds the returned sources and triggers a refresh; it sets message `"Added N sources from Obtainium."` (and `"No ArtCraft apps found in that file."` for 0); `removeSource` then refresh drops that repo's app unless still covered by the org source.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement. Sources screen: list (id + remove icon, the org shows "All ArtCraft apps"), text field + Add (paste from clipboard button), "Import from Obtainium" using `rememberLauncherForActivityResult(OpenDocument())` with `arrayOf("application/json", "*/*")`, read the text via `contentResolver`, "Reset to default" with a confirm dialog. Detail screen button "Track in Obtainium". Settings screen: app version, link to the repo, licences (MIT for the hub; third-party notices for Rust crates and AndroidX listed in a static `res/raw/licenses.txt`), "Clear cache" (calls `clearCache()` then `refresh()`).
- [ ] **Step 4:** Tests pass; on device: paste `github.com/artcraft-android/procraft` → accepted; paste `github.com/google/whatever` → error message; Obtainium button opens Obtainium (or its page).
- [ ] **Step 5:** Commit `feat(android): sources screen and Obtainium support`.

### Task 12: Speed and release build

**Files:**
- Modify: `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml`, `MainActivity.kt`

- [ ] **Step 1:** Add `androidx.profileinstaller` (ships the Compose libraries' baseline profiles to the device). App-specific profile generation needs a benchmark module and a device; leave it out of v1 and note it in the README "Ideas" list.
- [ ] **Step 2:** Make sure `MainActivity` shows cached data on the first frame: `RustCore` construction and `cachedCatalog()` run on `Dispatchers.IO` inside the ViewModel, never in `onCreate`; call `reportFullyDrawn()` after the first non-empty list.
- [ ] **Step 3:** `./gradlew :app:assembleRelease -Phub.abis=arm64-v8a` → succeeds (R8 on); install it on the device and repeat the Task 10 checks (catches missing R8 keep rules: a crash on start means JNA/bindings got stripped).
- [ ] **Step 4:** Record APK size and cold-start time (`adb shell am start -W -n io.github.l41n_pr0t0.artcrafthub/.MainActivity`, `TotalTime`) in the commit message.
- [ ] **Step 5:** Commit `perf: profile installer, release shrinking`.

### Task 13: CI, release workflow, README

**Files:**
- Create: `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `README.md`

- [ ] **Step 1:** `ci.yml` on push/PR: job `rust` (dtolnay toolchain 1.100 or `rust-toolchain` action; `cargo fmt --check`, `cargo clippy --workspace --all-targets -- -D warnings`, `cargo test --workspace`); job `android` (JDK 21, `android-actions/setup-android`, install `ndk;27.2.12479018`, Rust targets, `cargo install cargo-ndk --locked`, `./gradlew :app:testDebugUnitTest :app:assembleDebug`). Adapt from ProCraft's `.github/workflows/ci.yml` (keep the adaptation comment).
- [ ] **Step 2:** `release.yml` on tag `v*`: build `assembleRelease` once per ABI (`-Phub.abis=arm64-v8a` → `artcrafthub-<ver>-arm64-v8a.apk`, `armeabi-v7a`, `x86_64`), keystore from secrets `HUB_KEYSTORE_BASE64` etc., write `SHA256SUMS.txt`, create the GitHub Release (prerelease if the tag contains `-`). Adapt from ProCraft's `release.yml`.
- [ ] **Step 3:** README: what it is; "Get it on Obtainium" badge (`obtainium://add/https://github.com/l41n-pr0t0/ArtCraftHub-android`); install steps; build steps (`nix develop`, `cd android && ./gradlew :app:assembleDebug`); credits: "ArtCraft apps and names belong to their authors; this is an independent companion"; ProCraft credit for the adapted build code; MIT licence.
- [ ] **Step 4:** Verify locally: `cargo fmt --check && cargo clippy --workspace --all-targets -- -D warnings && cargo test --workspace && (cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug)` → all succeed. Validate workflow YAML with `nix run nixpkgs#actionlint`.
- [ ] **Step 5:** Commit `ci: checks and release workflow; README`. Pushing to GitHub happens only after the user says so.
