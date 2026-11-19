//! Downloading an APK: streamed to disk, hashed on the way, checked against `SHA256SUMS.txt`.

use std::io::{Read, Write};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};

use sha2::{Digest, Sha256};

use crate::HubError;
use crate::assets::parse_sha256sums;
use crate::catalog::ApkInfo;
use crate::http::{Http, HttpResponse};

const CHUNK: usize = 64 * 1024;

/// Kotlin callback for download progress.
#[uniffi::export(with_foreign)]
pub trait DownloadProgress: Send + Sync {
    fn on_progress(&self, downloaded: u64, total: u64);
}

/// A finished download.
#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct DownloadedApk {
    pub path: String,
    /// Whether the file matched the release's `SHA256SUMS.txt`.
    pub verified: bool,
}

/// Copies `reader` to `writer`, returning the lower-case hex SHA-256 of the bytes.
pub fn copy_hashing(
    reader: &mut dyn Read,
    writer: &mut dyn Write,
    total: u64,
    progress: &dyn DownloadProgress,
    cancel: &AtomicBool,
) -> Result<String, HubError> {
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; CHUNK];
    let mut done: u64 = 0;
    loop {
        if cancel.load(Ordering::Relaxed) {
            return Err(HubError::Cancelled);
        }
        let n = match reader.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => n,
            Err(e) if e.kind() == std::io::ErrorKind::Interrupted => continue,
            Err(e) => return Err(e.into()),
        };
        let chunk = buf.get(..n).unwrap_or_default();
        hasher.update(chunk);
        writer.write_all(chunk)?;
        done += n as u64;
        progress.on_progress(done, total.max(done));
    }
    writer.flush()?;
    Ok(hex::encode(hasher.finalize()))
}

/// Downloads `apk` into `dir` and verifies it when the release publishes checksums.
pub fn download_apk(
    http: &dyn Http,
    apk: &ApkInfo,
    dir: &Path,
    progress: &dyn DownloadProgress,
    cancel: &AtomicBool,
) -> Result<DownloadedApk, HubError> {
    let name = &apk.file_name;
    if name.is_empty() || name.contains(['/', '\\']) || name.starts_with('.') {
        return Err(HubError::InvalidLink { reason: format!("bad file name {name:?}") });
    }
    std::fs::create_dir_all(dir)?;
    let part = dir.join(format!("{name}.part"));
    let target = dir.join(name);
    let result = fetch_to(http, apk, &part, progress, cancel).and_then(|hash| {
        let verified = match expected_hash(http, apk)? {
            Some(want) if want == hash => true,
            Some(_) => return Err(HubError::ChecksumMismatch),
            None => false,
        };
        std::fs::rename(&part, &target)?;
        Ok(DownloadedApk { path: target.to_string_lossy().into_owned(), verified })
    });
    if result.is_err() {
        let _ = std::fs::remove_file(&part);
    }
    result
}

/// Streams the APK into `part`, returning its SHA-256.
fn fetch_to(
    http: &dyn Http,
    apk: &ApkInfo,
    part: &Path,
    progress: &dyn DownloadProgress,
    cancel: &AtomicBool,
) -> Result<String, HubError> {
    if cancel.load(Ordering::Relaxed) {
        return Err(HubError::Cancelled);
    }
    let (mut reader, len) = http.open(&apk.url)?;
    let mut file = std::io::BufWriter::new(std::fs::File::create(part)?);
    let hash = copy_hashing(&mut reader, &mut file, len.unwrap_or(apk.size), progress, cancel)?;
    file.into_inner().map_err(|e| HubError::from(e.into_error()))?.sync_all()?;
    Ok(hash)
}

/// The hash `SHA256SUMS.txt` lists for this APK, if the release has one that mentions it.
fn expected_hash(http: &dyn Http, apk: &ApkInfo) -> Result<Option<String>, HubError> {
    let Some(url) = &apk.sums_url else { return Ok(None) };
    match http.get(url, None) {
        Ok(HttpResponse::Ok { body, .. }) => Ok(parse_sha256sums(&body).remove(&apk.file_name)),
        Ok(HttpResponse::NotModified) | Err(HubError::NotFound) => Ok(None),
        Err(e) => Err(e),
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use super::*;
    use crate::github::tests::FakeHttp;

    const HELLO_SHA: &str = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    const APK_URL: &str = "https://github.com/x/releases/download/v1/app.apk";
    const SUMS_URL: &str = "https://github.com/x/releases/download/v1/SHA256SUMS.txt";

    #[derive(Default)]
    struct Recorder(Mutex<Vec<(u64, u64)>>);
    impl DownloadProgress for Recorder {
        fn on_progress(&self, downloaded: u64, total: u64) {
            self.0.lock().unwrap().push((downloaded, total));
        }
    }

    fn apk(sums: bool) -> ApkInfo {
        ApkInfo { file_name: "app.apk".into(), url: APK_URL.into(), size: 5, sums_url: sums.then(|| SUMS_URL.into()) }
    }
    fn http_with(sums: Option<&str>) -> FakeHttp {
        let http = FakeHttp::default();
        http.file(APK_URL, b"hello");
        if let Some(s) = sums {
            http.ok(SUMS_URL, s, None);
        }
        http
    }

    #[test]
    fn copy_hashing_hashes_writes_and_reports() {
        let (mut out, rec) = (Vec::new(), Recorder::default());
        let hash = copy_hashing(&mut &b"hello"[..], &mut out, 5, &rec, &AtomicBool::new(false)).unwrap();
        assert_eq!(hash, HELLO_SHA);
        assert_eq!(out, b"hello");
        assert_eq!(rec.0.lock().unwrap().last(), Some(&(5, 5)));
    }
    #[test]
    fn cancelled_before_start() {
        let r = copy_hashing(&mut &b"hello"[..], &mut Vec::new(), 5, &Recorder::default(), &AtomicBool::new(true));
        assert!(matches!(r, Err(HubError::Cancelled)));
    }
    #[test]
    fn verified_download() {
        let dir = tempfile::tempdir().unwrap();
        let http = http_with(Some(&format!("{HELLO_SHA}  app.apk\n")));
        let got = download_apk(&http, &apk(true), dir.path(), &Recorder::default(), &AtomicBool::new(false)).unwrap();
        assert!(got.verified);
        assert_eq!(got.path, dir.path().join("app.apk").to_string_lossy());
        assert_eq!(std::fs::read(dir.path().join("app.apk")).unwrap(), b"hello");
        assert!(!dir.path().join("app.apk.part").exists());
    }
    #[test]
    fn wrong_hash_deletes_file() {
        let dir = tempfile::tempdir().unwrap();
        let http = http_with(Some(&format!("{}  app.apk\n", "0".repeat(64))));
        let r = download_apk(&http, &apk(true), dir.path(), &Recorder::default(), &AtomicBool::new(false));
        assert!(matches!(r, Err(HubError::ChecksumMismatch)));
        assert_eq!(std::fs::read_dir(dir.path()).unwrap().count(), 0);
    }
    #[test]
    fn no_sums_url_is_unverified() {
        let dir = tempfile::tempdir().unwrap();
        let got =
            download_apk(&http_with(None), &apk(false), dir.path(), &Recorder::default(), &AtomicBool::new(false))
                .unwrap();
        assert!(!got.verified);
    }
    #[test]
    fn sums_without_our_file_is_unverified() {
        let dir = tempfile::tempdir().unwrap();
        let http = http_with(Some(&format!("{HELLO_SHA}  other.apk\n")));
        let got = download_apk(&http, &apk(true), dir.path(), &Recorder::default(), &AtomicBool::new(false)).unwrap();
        assert!(!got.verified);
    }
    #[test]
    fn missing_sums_file_is_unverified() {
        let dir = tempfile::tempdir().unwrap();
        let got = download_apk(&http_with(None), &apk(true), dir.path(), &Recorder::default(), &AtomicBool::new(false))
            .unwrap();
        assert!(!got.verified);
    }
    #[test]
    fn cancelled_download_leaves_nothing() {
        let dir = tempfile::tempdir().unwrap();
        let r = download_apk(&http_with(None), &apk(false), dir.path(), &Recorder::default(), &AtomicBool::new(true));
        assert!(matches!(r, Err(HubError::Cancelled)));
        assert_eq!(std::fs::read_dir(dir.path()).unwrap().count(), 0);
    }
    #[test]
    fn rejects_path_in_file_name() {
        let dir = tempfile::tempdir().unwrap();
        let mut bad = apk(false);
        bad.file_name = "../evil.apk".into();
        let r = download_apk(&http_with(None), &bad, dir.path(), &Recorder::default(), &AtomicBool::new(false));
        assert!(matches!(r, Err(HubError::InvalidLink { .. })));
    }
}
