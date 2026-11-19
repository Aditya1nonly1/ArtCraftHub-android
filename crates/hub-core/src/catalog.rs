//! Catalogue entries: what the UI shows for one ArtCraft app.

use serde::{Deserialize, Serialize};

use crate::assets::pick_apk;
use crate::github::{GhRelease, GhRepo};
use crate::sources::ORG;

/// The APK this device should install from a release.
#[derive(uniffi::Record, Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ApkInfo {
    pub file_name: String,
    pub url: String,
    pub size: u64,
    /// The release's `SHA256SUMS.txt`, if it has one.
    pub sums_url: Option<String>,
}

/// One app in the hub.
#[derive(uniffi::Record, Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct AppEntry {
    /// The repo name, e.g. `pdfcraft-android`.
    pub id: String,
    pub name: String,
    pub description: String,
    pub package_name: String,
    pub repo_url: String,
    pub latest_version: String,
    pub prerelease: bool,
    pub release_notes: String,
    /// `None` when the release has no APK for this device.
    pub apk: Option<ApkInfo>,
    pub icon_url: String,
}

pub fn build_entry(repo: &GhRepo, release: &GhRelease, abis: &[String]) -> AppEntry {
    let sums_url = release.assets.iter().find(|a| a.name.eq_ignore_ascii_case("SHA256SUMS.txt")).map(|a| a.url.clone());
    let apk = pick_apk(&release.assets, abis).map(|a| ApkInfo {
        file_name: a.name.clone(),
        url: a.url.clone(),
        size: a.size,
        sums_url: sums_url.clone(),
    });
    AppEntry {
        id: repo.name.clone(),
        name: display_name(&repo.name),
        description: repo.description.clone().unwrap_or_default(),
        package_name: package_name(&repo.name),
        repo_url: repo.html_url.clone(),
        latest_version: release.tag_name.clone(),
        prerelease: release.prerelease,
        release_notes: release.body.clone().unwrap_or_default(),
        apk,
        icon_url: format!(
            "https://raw.githubusercontent.com/{ORG}/{}/HEAD/fastlane/metadata/android/en-US/images/icon.png",
            repo.name
        ),
    }
}

fn short_name(repo: &str) -> &str {
    repo.strip_suffix("-android").unwrap_or(repo)
}

/// `pdfcraft-android` → `PdfCraft`.
pub fn display_name(repo: &str) -> String {
    let short = short_name(repo);
    let (stem, craft) = match short.strip_suffix("craft") {
        Some(stem) if !stem.is_empty() => (stem, "Craft"),
        _ => (short, ""),
    };
    let mut chars = stem.chars();
    let first: String = chars.next().map(|c| c.to_uppercase().collect()).unwrap_or_default();
    format!("{first}{}{craft}", chars.as_str())
}

/// `pdfcraft-android` → `io.github.artcraftandroid.pdfcraft`.
pub fn package_name(repo: &str) -> String {
    format!("io.github.artcraftandroid.{}", short_name(repo))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn procraft() -> (GhRepo, GhRelease) {
        let repos: Vec<GhRepo> = serde_json::from_str(include_str!("../tests/fixtures/org-repos.json")).unwrap();
        let repo = repos.into_iter().find(|r| r.name == "procraft").unwrap();
        let releases: Vec<GhRelease> =
            serde_json::from_str(include_str!("../tests/fixtures/procraft-releases.json")).unwrap();
        (repo, releases.into_iter().find(|r| r.tag_name == "v0.0.7-beta").unwrap())
    }
    fn arm64() -> Vec<String> {
        vec!["arm64-v8a".to_owned()]
    }

    #[test]
    fn display_names() {
        assert_eq!(display_name("procraft"), "ProCraft");
        assert_eq!(display_name("lightcraft-android"), "LightCraft");
        assert_eq!(display_name("pdfcraft-android"), "PdfCraft");
    }
    #[test]
    fn package_names() {
        assert_eq!(package_name("pdfcraft-android"), "io.github.artcraftandroid.pdfcraft");
        assert_eq!(package_name("procraft"), "io.github.artcraftandroid.procraft");
    }
    #[test]
    fn entry_from_procraft_fixture() {
        let (repo, rel) = procraft();
        let e = build_entry(&repo, &rel, &arm64());
        assert_eq!(e.id, "procraft");
        assert_eq!(e.name, "ProCraft");
        assert_eq!(e.package_name, "io.github.artcraftandroid.procraft");
        assert_eq!(e.repo_url, "https://github.com/artcraft-android/procraft");
        assert_eq!(e.latest_version, "v0.0.7-beta");
        assert!(e.prerelease);
        let apk = e.apk.unwrap();
        assert!(apk.file_name.ends_with("-android-arm64.apk"));
        assert!(apk.size > 0);
        let sums = rel.assets.iter().find(|a| a.name == "SHA256SUMS.txt").unwrap();
        assert_eq!(apk.sums_url.as_deref(), Some(sums.url.as_str()));
        assert_eq!(
            e.icon_url,
            "https://raw.githubusercontent.com/artcraft-android/procraft/HEAD/fastlane/metadata/android/en-US/images/icon.png"
        );
        assert!(!e.release_notes.is_empty());
    }
    #[test]
    fn release_without_apk_still_listed() {
        let (repo, mut rel) = procraft();
        rel.assets.retain(|a| !a.name.ends_with(".apk"));
        let e = build_entry(&repo, &rel, &arm64());
        assert_eq!(e.apk, None);
        assert_eq!(e.id, "procraft");
    }
    #[test]
    fn missing_description_is_empty() {
        let (mut repo, rel) = procraft();
        repo.description = None;
        assert_eq!(build_entry(&repo, &rel, &arm64()).description, "");
    }
}
