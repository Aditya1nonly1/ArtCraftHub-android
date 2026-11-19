//! `Hub`: the object Kotlin talks to. Owns sources, the catalogue cache, icons and downloads.

use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};

use serde::Serialize;
use serde::de::DeserializeOwned;

use crate::HubError;
use crate::catalog::{AppEntry, build_entry};
use crate::download::{DownloadProgress, DownloadedApk, download_apk};
use crate::github::{GhRepo, GithubClient};
use crate::http::{Http, UreqHttp};
use crate::sources::{Source, default_sources, parse_link, parse_obtainium_export};

#[derive(uniffi::Object)]
pub struct Hub {
    http: Arc<dyn Http>,
    github: GithubClient,
    data_dir: PathBuf,
    cache_dir: PathBuf,
    abis: Vec<String>,
    sources: Mutex<Vec<Source>>,
    catalog: Mutex<Vec<AppEntry>>,
    cancels: Mutex<HashMap<String, Arc<AtomicBool>>>,
}

#[uniffi::export]
impl Hub {
    #[uniffi::constructor]
    pub fn new(data_dir: String, cache_dir: String, abis: Vec<String>) -> Arc<Self> {
        Self::with_http(Path::new(&data_dir), Path::new(&cache_dir), abis, Arc::new(UreqHttp::new()))
    }

    pub fn sources(&self) -> Vec<Source> {
        lock(&self.sources).clone()
    }

    /// Adds a pasted link; if it is already a source, returns the existing one.
    pub fn add_source(&self, link: String) -> Result<Source, HubError> {
        let source = parse_link(&link)?;
        let mut sources = lock(&self.sources);
        if let Some(existing) = sources.iter().find(|s| s.id == source.id) {
            return Ok(existing.clone());
        }
        sources.push(source.clone());
        self.save_sources(&sources)?;
        Ok(source)
    }

    pub fn remove_source(&self, id: String) {
        let mut sources = lock(&self.sources);
        sources.retain(|s| s.id != id);
        let _ = self.save_sources(&sources);
    }

    pub fn reset_sources(&self) -> Vec<Source> {
        let mut sources = lock(&self.sources);
        *sources = default_sources();
        let _ = self.save_sources(&sources);
        sources.clone()
    }

    /// Adds the ArtCraft repos from an Obtainium export; returns only the newly added ones.
    pub fn import_obtainium(&self, json: String) -> Result<Vec<Source>, HubError> {
        let found = parse_obtainium_export(&json)?;
        let mut sources = lock(&self.sources);
        let added: Vec<Source> = found.into_iter().filter(|f| !sources.iter().any(|s| s.id == f.id)).collect();
        sources.extend(added.iter().cloned());
        self.save_sources(&sources)?;
        Ok(added)
    }

    pub fn cached_catalog(&self) -> Vec<AppEntry> {
        lock(&self.catalog).clone()
    }

    /// Fetches every source's repos and their latest releases. Offline or rate-limited aborts the
    /// refresh (the cached catalogue stays); any other per-repo error just skips that repo.
    pub fn refresh_catalog(&self) -> Result<Vec<AppEntry>, HubError> {
        let mut repos: Vec<GhRepo> = Vec::new();
        for source in self.sources() {
            for repo in skip_soft(self.github.repos(&source))?.unwrap_or_default() {
                if !repos.iter().any(|r| r.name == repo.name) {
                    repos.push(repo);
                }
            }
        }
        let mut apps = Vec::new();
        for repo in &repos {
            if let Some(release) = skip_soft(self.github.latest_release(&repo.name))?.flatten() {
                apps.push(build_entry(repo, &release, &self.abis));
            }
        }
        apps.sort_by_key(|a| a.name.to_lowercase());
        *lock(&self.catalog) = apps.clone();
        let _ = write_json(&self.cache_dir.join(CATALOG), &apps);
        Ok(apps)
    }

    /// Downloads the APK the catalogue picked for this device. Blocking; cancel from another thread.
    pub fn download(&self, app_id: String, progress: Arc<dyn DownloadProgress>) -> Result<DownloadedApk, HubError> {
        let apk = lock(&self.catalog).iter().find(|a| a.id == app_id).and_then(|a| a.apk.clone());
        let apk = apk.ok_or(HubError::NotFound)?;
        let cancel = Arc::new(AtomicBool::new(false));
        lock(&self.cancels).insert(app_id.clone(), cancel.clone());
        let result =
            download_apk(self.http.as_ref(), &apk, &self.cache_dir.join("downloads"), progress.as_ref(), &cancel);
        lock(&self.cancels).remove(&app_id);
        result
    }

    pub fn cancel_download(&self, app_id: String) {
        if let Some(flag) = lock(&self.cancels).get(&app_id) {
            flag.store(true, Ordering::Relaxed);
        }
    }

    /// The app's icon PNG, fetched once and kept in the cache. `None` if the repo has none.
    pub fn icon(&self, app_id: String) -> Option<Vec<u8>> {
        let url = lock(&self.catalog).iter().find(|a| a.id == app_id).map(|a| a.icon_url.clone())?;
        let path = self.cache_dir.join("icons").join(format!("{app_id}.png"));
        if let Ok(bytes) = std::fs::read(&path) {
            return Some(bytes);
        }
        let (reader, _) = self.http.open(&url).ok()?;
        let mut bytes = Vec::new();
        reader.take(MAX_ICON).read_to_end(&mut bytes).ok()?;
        if let Some(dir) = path.parent() {
            let _ = std::fs::create_dir_all(dir).and_then(|()| std::fs::write(&path, &bytes));
        }
        Some(bytes)
    }

    /// Deletes everything under the cache dir; sources stay.
    pub fn clear_cache(&self) {
        lock(&self.catalog).clear();
        if let Ok(entries) = std::fs::read_dir(&self.cache_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                let _ = if path.is_dir() { std::fs::remove_dir_all(&path) } else { std::fs::remove_file(&path) };
            }
        }
    }
}

const SOURCES: &str = "sources.json";
const CATALOG: &str = "catalog.json";
const MAX_ICON: u64 = 2 * 1024 * 1024;

impl Hub {
    pub(crate) fn with_http(data_dir: &Path, cache_dir: &Path, abis: Vec<String>, http: Arc<dyn Http>) -> Arc<Self> {
        let sources = read_json(&data_dir.join(SOURCES)).unwrap_or_else(default_sources);
        let catalog = read_json(&cache_dir.join(CATALOG)).unwrap_or_default();
        Arc::new(Self {
            github: GithubClient::new(http.clone(), cache_dir),
            http,
            data_dir: data_dir.to_owned(),
            cache_dir: cache_dir.to_owned(),
            abis,
            sources: Mutex::new(sources),
            catalog: Mutex::new(catalog),
            cancels: Mutex::new(HashMap::new()),
        })
    }

    fn save_sources(&self, sources: &[Source]) -> Result<(), HubError> {
        write_json(&self.data_dir.join(SOURCES), sources)
    }
}

/// Offline and rate-limit errors stop a refresh; anything else (a deleted repo, a bad release) is skipped.
fn skip_soft<T>(r: Result<T, HubError>) -> Result<Option<T>, HubError> {
    match r {
        Ok(v) => Ok(Some(v)),
        Err(e @ (HubError::Offline | HubError::RateLimited { .. })) => Err(e),
        Err(_) => Ok(None),
    }
}

/// A poisoned lock only means another thread panicked mid-update; the data is still usable.
fn lock<T>(m: &Mutex<T>) -> MutexGuard<'_, T> {
    m.lock().unwrap_or_else(PoisonError::into_inner)
}

fn read_json<T: DeserializeOwned>(path: &Path) -> Option<T> {
    serde_json::from_slice(&std::fs::read(path).ok()?).ok()
}

/// Writes via a temp file + rename so a crash never leaves half a file.
fn write_json<T: Serialize + ?Sized>(path: &Path, value: &T) -> Result<(), HubError> {
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir)?;
    }
    let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec(value)?)?;
    std::fs::rename(&tmp, path)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::AtomicUsize;

    use super::*;
    use crate::github::tests::FakeHttp;

    const ORG_URL: &str = "https://api.github.com/orgs/artcraft-android/repos?per_page=100";
    const PROCRAFT_REPO: &str = "https://api.github.com/repos/artcraft-android/procraft";
    const REPOS: &str = include_str!("../tests/fixtures/org-repos.json");
    const RELEASES: &str = include_str!("../tests/fixtures/procraft-releases.json");

    fn rel_url(repo: &str) -> String {
        format!("https://api.github.com/repos/artcraft-android/{repo}/releases?per_page=10")
    }

    struct Env {
        data: tempfile::TempDir,
        cache: tempfile::TempDir,
        http: Arc<FakeHttp>,
    }
    impl Env {
        fn new() -> Self {
            Self { data: tempfile::tempdir().unwrap(), cache: tempfile::tempdir().unwrap(), http: Arc::default() }
        }
        fn hub(&self) -> Arc<Hub> {
            Hub::with_http(self.data.path(), self.cache.path(), vec!["arm64-v8a".into()], self.http.clone())
        }
        /// One good refresh round: org lists 7 repos, only procraft and pdfcraft have releases.
        fn serve_refresh(&self) {
            self.http.ok(ORG_URL, REPOS, None);
            self.http.ok(&rel_url("procraft"), RELEASES, None);
            self.http.ok(&rel_url("pdfcraft-android"), RELEASES, None);
        }
    }

    fn ids(s: &[Source]) -> Vec<&str> {
        s.iter().map(|s| s.id.as_str()).collect()
    }

    #[test]
    fn first_run_has_default() {
        assert_eq!(ids(&Env::new().hub().sources()), ["artcraft-android"]);
    }

    #[test]
    fn sources_persist() {
        let env = Env::new();
        let hub = env.hub();
        hub.add_source("github.com/artcraft-android/procraft".into()).unwrap();
        hub.add_source("https://github.com/artcraft-android/procraft/".into()).unwrap();
        drop(hub);
        assert_eq!(ids(&env.hub().sources()), ["artcraft-android", "artcraft-android/procraft"]);
    }

    #[test]
    fn add_bad_link_is_error_and_changes_nothing() {
        let env = Env::new();
        let hub = env.hub();
        assert!(matches!(hub.add_source("https://github.com/evil/x".into()), Err(HubError::NotArtcraft)));
        assert_eq!(hub.sources().len(), 1);
    }

    #[test]
    fn remove_and_reset() {
        let env = Env::new();
        let hub = env.hub();
        hub.add_source("github.com/artcraft-android/procraft".into()).unwrap();
        hub.remove_source("artcraft-android".into());
        assert_eq!(ids(&hub.sources()), ["artcraft-android/procraft"]);
        assert_eq!(ids(&hub.reset_sources()), ["artcraft-android"]);
        assert_eq!(ids(&env.hub().sources()), ["artcraft-android"]);
    }

    #[test]
    fn import_returns_only_new() {
        let env = Env::new();
        let hub = env.hub();
        hub.add_source("github.com/artcraft-android/procraft".into()).unwrap();
        let added = hub.import_obtainium(include_str!("../tests/fixtures/obtainium-export.json").into()).unwrap();
        assert_eq!(ids(&added), ["artcraft-android/pdfcraft-android"]);
        assert_eq!(hub.sources().len(), 3);
    }

    #[test]
    fn refresh_merges_sources_and_dedups() {
        let env = Env::new();
        let hub = env.hub();
        hub.add_source("github.com/artcraft-android/procraft".into()).unwrap();
        env.serve_refresh();
        let one = serde_json::to_string(
            &serde_json::from_str::<Vec<serde_json::Value>>(REPOS)
                .unwrap()
                .into_iter()
                .find(|r| r["name"] == "procraft")
                .unwrap(),
        )
        .unwrap();
        env.http.ok(PROCRAFT_REPO, &one, None);
        env.http.ok(&rel_url("procraft"), RELEASES, None);
        let apps = hub.refresh_catalog().unwrap();
        assert_eq!(apps.iter().map(|a| a.name.as_str()).collect::<Vec<_>>(), ["PdfCraft", "ProCraft"]);
        assert_eq!(hub.cached_catalog(), apps);
    }

    #[test]
    fn one_repo_failing_does_not_hide_others() {
        let env = Env::new();
        env.serve_refresh();
        // FakeHttp answers NotFound for the 5 repos without releases.
        assert_eq!(env.hub().refresh_catalog().unwrap().len(), 2);
    }

    #[test]
    fn refresh_failure_keeps_cache() {
        let env = Env::new();
        let hub = env.hub();
        env.serve_refresh();
        hub.refresh_catalog().unwrap();
        env.http.push(ORG_URL, Err(HubError::Offline));
        assert!(matches!(hub.refresh_catalog(), Err(HubError::Offline)));
        assert_eq!(hub.cached_catalog().len(), 2);
        assert_eq!(env.hub().cached_catalog().len(), 2);
    }

    #[test]
    fn rate_limit_aborts_refresh() {
        let env = Env::new();
        env.http.ok(ORG_URL, REPOS, None);
        env.http.push(&rel_url("procraft"), Err(HubError::RateLimited { reset_at: 7 }));
        env.http.ok(&rel_url("pdfcraft-android"), RELEASES, None);
        assert!(matches!(env.hub().refresh_catalog(), Err(HubError::RateLimited { reset_at: 7 })));
    }

    #[test]
    fn icon_404_is_none_and_cached_bytes_reused() {
        let env = Env::new();
        let hub = env.hub();
        env.serve_refresh();
        let apps = hub.refresh_catalog().unwrap();
        let pro = apps.iter().find(|a| a.id == "procraft").unwrap();
        env.http.file(&pro.icon_url, b"PNG");
        assert_eq!(hub.icon("procraft".into()).as_deref(), Some(&b"PNG"[..]));
        env.http.files.lock().unwrap().clear();
        assert_eq!(hub.icon("procraft".into()).as_deref(), Some(&b"PNG"[..]));
        assert_eq!(hub.icon("pdfcraft-android".into()), None);
        assert_eq!(hub.icon("unknown".into()), None);
    }

    #[derive(Default)]
    struct Count(AtomicUsize);
    impl DownloadProgress for Count {
        fn on_progress(&self, _: u64, _: u64) {
            self.0.fetch_add(1, Ordering::Relaxed);
        }
    }

    #[test]
    fn download_uses_catalog_apk() {
        let env = Env::new();
        let hub = env.hub();
        env.serve_refresh();
        let apps = hub.refresh_catalog().unwrap();
        let apk = apps.iter().find(|a| a.id == "procraft").unwrap().apk.clone().unwrap();
        env.http.file(&apk.url, b"apk bytes");
        let progress = Arc::new(Count::default());
        let got = hub.download("procraft".into(), progress.clone()).unwrap();
        assert!(got.path.starts_with(&*env.cache.path().join("downloads").to_string_lossy()));
        assert!(!got.verified, "FakeHttp serves no SHA256SUMS.txt, so the APK is unverified");
        assert!(progress.0.load(Ordering::Relaxed) > 0);
        assert!(matches!(hub.download("nope".into(), progress), Err(HubError::NotFound)));
    }

    #[test]
    fn clear_cache_keeps_sources() {
        let env = Env::new();
        let hub = env.hub();
        hub.add_source("github.com/artcraft-android/procraft".into()).unwrap();
        env.serve_refresh();
        hub.refresh_catalog().unwrap();
        hub.clear_cache();
        assert!(hub.cached_catalog().is_empty());
        assert!(env.hub().cached_catalog().is_empty());
        assert_eq!(hub.sources().len(), 2);
        assert_eq!(std::fs::read_dir(env.cache.path()).unwrap().count(), 0);
    }
}
