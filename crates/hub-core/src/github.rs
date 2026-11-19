//! GitHub REST client: org/repo listing and latest release, with an on-disk ETag cache.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use serde::{Deserialize, Serialize};

use crate::HubError;
use crate::assets::ReleaseAsset;
use crate::http::{Http, HttpResponse};
use crate::sources::{ORG, Source};

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct GhRepo {
    pub name: String,
    pub description: Option<String>,
    pub html_url: String,
    pub archived: bool,
    pub fork: bool,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct GhRelease {
    pub tag_name: String,
    pub body: Option<String>,
    pub prerelease: bool,
    pub draft: bool,
    #[serde(deserialize_with = "github_assets")]
    pub assets: Vec<ReleaseAsset>,
}

/// GitHub's asset JSON has an API `url` *and* the `browser_download_url` we want, so map by hand.
fn github_assets<'de, D: serde::Deserializer<'de>>(d: D) -> Result<Vec<ReleaseAsset>, D::Error> {
    #[derive(Deserialize)]
    struct GhAsset {
        name: String,
        browser_download_url: String,
        size: u64,
    }
    let raw: Vec<GhAsset> = Vec::deserialize(d)?;
    Ok(raw.into_iter().map(|a| ReleaseAsset { name: a.name, url: a.browser_download_url, size: a.size }).collect())
}

/// One cached response: its ETag and body.
#[derive(Serialize, Deserialize)]
struct CacheEntry {
    etag: String,
    body: String,
}

type Cache = std::collections::HashMap<String, CacheEntry>;

const API: &str = "https://api.github.com";

pub struct GithubClient {
    http: Arc<dyn Http>,
    cache_path: PathBuf,
}

impl GithubClient {
    pub fn new(http: Arc<dyn Http>, cache_dir: &Path) -> Self {
        Self { http, cache_path: cache_dir.join("github-cache.json") }
    }

    /// The live (not archived, not forked) repos a source covers.
    pub fn repos(&self, source: &Source) -> Result<Vec<GhRepo>, HubError> {
        let repos: Vec<GhRepo> = match &source.repo {
            None => serde_json::from_str(&self.fetch(&format!("{API}/orgs/{ORG}/repos?per_page=100"))?)?,
            Some(repo) => vec![serde_json::from_str(&self.fetch(&format!("{API}/repos/{ORG}/{repo}"))?)?],
        };
        Ok(repos.into_iter().filter(|r| !r.archived && !r.fork).collect())
    }

    /// The newest non-draft release of `artcraft-android/<repo>`, prereleases included.
    pub fn latest_release(&self, repo: &str) -> Result<Option<GhRelease>, HubError> {
        // Not `/releases/latest`: GitHub skips prereleases there, and every ArtCraft release is a beta.
        let releases: Vec<GhRelease> =
            serde_json::from_str(&self.fetch(&format!("{API}/repos/{ORG}/{repo}/releases?per_page=10"))?)?;
        Ok(releases.into_iter().find(|r| !r.draft))
    }

    /// Conditional GET through the ETag cache; a 304 answers from the cached body.
    fn fetch(&self, url: &str) -> Result<String, HubError> {
        let mut cache = self.load_cache();
        let etag = cache.get(url).map(|e| e.etag.as_str());
        match self.http.get(url, etag)? {
            HttpResponse::NotModified => match cache.remove(url) {
                Some(entry) => Ok(entry.body),
                // We sent no ETag, so a 304 is unexpected; ask again plainly.
                None => match self.http.get(url, None)? {
                    HttpResponse::Ok { body, .. } => Ok(body),
                    HttpResponse::NotModified => Err(HubError::Io { message: "unexpected 304".to_owned() }),
                },
            },
            HttpResponse::Ok { body, etag } => {
                if let Some(etag) = etag {
                    cache.insert(url.to_owned(), CacheEntry { etag, body: body.clone() });
                    // A cache that fails to save only costs a re-download next time.
                    let _ = self.save_cache(&cache);
                }
                Ok(body)
            }
        }
    }

    fn load_cache(&self) -> Cache {
        std::fs::read_to_string(&self.cache_path).ok().and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default()
    }

    fn save_cache(&self, cache: &Cache) -> Result<(), HubError> {
        if let Some(dir) = self.cache_path.parent() {
            std::fs::create_dir_all(dir)?;
        }
        let tmp = self.cache_path.with_extension("json.tmp");
        std::fs::write(&tmp, serde_json::to_vec(cache)?)?;
        std::fs::rename(&tmp, &self.cache_path)?;
        Ok(())
    }
}

#[cfg(test)]
pub(crate) mod tests {
    use std::collections::{HashMap, VecDeque};
    use std::io::Read;
    use std::sync::Mutex;

    use super::*;
    use crate::sources::{default_sources, parse_link};

    /// Fake HTTP: queued responses per URL; records `(url, etag)` for each `get`.
    #[derive(Default)]
    pub(crate) struct FakeHttp {
        pub responses: Mutex<HashMap<String, VecDeque<Result<HttpResponse, HubError>>>>,
        pub files: Mutex<HashMap<String, Vec<u8>>>,
        pub calls: Mutex<Vec<(String, Option<String>)>>,
    }

    impl FakeHttp {
        pub fn push(&self, url: &str, r: Result<HttpResponse, HubError>) {
            self.responses.lock().unwrap().entry(url.to_owned()).or_default().push_back(r);
        }
        pub fn ok(&self, url: &str, body: &str, etag: Option<&str>) {
            self.push(url, Ok(HttpResponse::Ok { body: body.to_owned(), etag: etag.map(str::to_owned) }));
        }
        #[allow(dead_code, reason = "used by the download tests")]
        pub fn file(&self, url: &str, bytes: &[u8]) {
            self.files.lock().unwrap().insert(url.to_owned(), bytes.to_vec());
        }
        pub fn etags_sent(&self) -> Vec<Option<String>> {
            self.calls.lock().unwrap().iter().map(|(_, e)| e.clone()).collect()
        }
    }

    impl Http for FakeHttp {
        fn get(&self, url: &str, etag: Option<&str>) -> Result<HttpResponse, HubError> {
            self.calls.lock().unwrap().push((url.to_owned(), etag.map(str::to_owned)));
            self.responses.lock().unwrap().get_mut(url).and_then(VecDeque::pop_front).unwrap_or(Err(HubError::NotFound))
        }
        fn open(&self, url: &str) -> Result<(Box<dyn Read + Send>, Option<u64>), HubError> {
            let bytes = self.files.lock().unwrap().get(url).cloned().ok_or(HubError::NotFound)?;
            let len = bytes.len() as u64;
            Ok((Box::new(std::io::Cursor::new(bytes)), Some(len)))
        }
    }

    const REPOS: &str = include_str!("../tests/fixtures/org-repos.json");
    const RELEASES: &str = include_str!("../tests/fixtures/procraft-releases.json");
    const ORG_URL: &str = "https://api.github.com/orgs/artcraft-android/repos?per_page=100";
    const REL_URL: &str = "https://api.github.com/repos/artcraft-android/procraft/releases?per_page=10";

    fn client(http: &Arc<FakeHttp>, dir: &Path) -> GithubClient {
        GithubClient::new(http.clone(), dir)
    }

    #[test]
    fn org_source_lists_repos() {
        let dir = tempfile::tempdir().unwrap();
        let http = Arc::new(FakeHttp::default());
        http.ok(ORG_URL, REPOS, None);
        let names: Vec<String> =
            client(&http, dir.path()).repos(&default_sources()[0]).unwrap().into_iter().map(|r| r.name).collect();
        assert!(names.contains(&"procraft".to_owned()));
        assert_eq!(names.len(), 7);
        assert!(!names.iter().any(|n| n == "old-thing" || n == "forked-lib"));
    }

    #[test]
    fn repo_source_fetches_one() {
        let dir = tempfile::tempdir().unwrap();
        let http = Arc::new(FakeHttp::default());
        let one = serde_json::to_string(&serde_json::from_str::<Vec<serde_json::Value>>(REPOS).unwrap()[5]).unwrap();
        http.ok("https://api.github.com/repos/artcraft-android/procraft", &one, None);
        let repos =
            client(&http, dir.path()).repos(&parse_link("github.com/artcraft-android/procraft").unwrap()).unwrap();
        assert_eq!(repos.iter().map(|r| r.name.as_str()).collect::<Vec<_>>(), ["procraft"]);
    }

    #[test]
    fn prerelease_is_latest() {
        let dir = tempfile::tempdir().unwrap();
        let http = Arc::new(FakeHttp::default());
        http.ok(REL_URL, RELEASES, None);
        let rel = client(&http, dir.path()).latest_release("procraft").unwrap().unwrap();
        assert_eq!(rel.tag_name, "v0.0.7-beta");
        assert!(rel.prerelease);
        assert!(rel.assets.iter().any(|a| a.name == "SHA256SUMS.txt" && a.url.starts_with("https://github.com/")));
    }

    #[test]
    fn no_releases_is_none() {
        let dir = tempfile::tempdir().unwrap();
        let http = Arc::new(FakeHttp::default());
        http.ok(REL_URL, "[]", None);
        assert_eq!(client(&http, dir.path()).latest_release("procraft").unwrap(), None);
    }

    #[test]
    fn second_call_sends_etag_and_uses_cache_on_304() {
        let dir = tempfile::tempdir().unwrap();
        let http = Arc::new(FakeHttp::default());
        http.ok(REL_URL, RELEASES, Some("\"x\""));
        http.push(REL_URL, Ok(HttpResponse::NotModified));
        http.push(REL_URL, Ok(HttpResponse::NotModified));
        let c = client(&http, dir.path());
        let first = c.latest_release("procraft").unwrap();
        let second = c.latest_release("procraft").unwrap();
        assert_eq!(first, second);
        let third = client(&http, dir.path()).latest_release("procraft").unwrap();
        assert_eq!(first, third);
        assert_eq!(http.etags_sent(), [None, Some("\"x\"".to_owned()), Some("\"x\"".to_owned())]);
    }

    #[test]
    fn not_modified_without_cache_is_refetched_without_etag() {
        let dir = tempfile::tempdir().unwrap();
        let http = Arc::new(FakeHttp::default());
        http.ok(REL_URL, RELEASES, Some("\"x\""));
        http.ok(REL_URL, RELEASES, Some("\"x\""));
        client(&http, dir.path()).latest_release("procraft").unwrap();
        std::fs::remove_file(dir.path().join("github-cache.json")).unwrap();
        client(&http, dir.path()).latest_release("procraft").unwrap();
        assert_eq!(http.etags_sent(), [None, None]);
    }

    /// Hits the real GitHub API: `cargo test -p hub-core -- --ignored live_`.
    #[test]
    #[ignore = "network"]
    fn live_github_round_trip() {
        let dir = tempfile::tempdir().unwrap();
        let c = GithubClient::new(Arc::new(crate::http::UreqHttp::new()), dir.path());
        let repos = c.repos(&default_sources()[0]).unwrap();
        assert!(repos.iter().any(|r| r.name == "procraft"));
        let first = c.latest_release("procraft").unwrap().unwrap();
        assert_eq!(c.latest_release("procraft").unwrap().unwrap(), first);
        assert!(matches!(c.latest_release("no-such-repo-here"), Err(HubError::NotFound)));
    }

    #[test]
    fn errors_pass_through() {
        let dir = tempfile::tempdir().unwrap();
        let http = Arc::new(FakeHttp::default());
        http.push(REL_URL, Err(HubError::RateLimited { reset_at: 9 }));
        assert!(matches!(
            client(&http, dir.path()).latest_release("procraft"),
            Err(HubError::RateLimited { reset_at: 9 })
        ));
    }
}
