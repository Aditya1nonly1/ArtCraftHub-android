//! Sources: the GitHub org or repos the hub watches, parsed from pasted links or an Obtainium export.

use serde::{Deserialize, Serialize};

use crate::HubError;

/// The only GitHub owner the hub accepts.
pub const ORG: &str = "artcraft-android";

/// One watched location: the whole org or a single repo in it.
#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Source {
    /// `artcraft-android` or `artcraft-android/<repo>`.
    pub id: String,
    /// The repo name, if this source is one repo.
    pub repo: Option<String>,
    /// Canonical `https://github.com/artcraft-android[/<repo>]`.
    pub url: String,
}

/// Parses a pasted GitHub, Obtainium, or Obtainium redirect link.
#[uniffi::export]
pub fn parse_link(input: &str) -> Result<Source, HubError> {
    let mut rest = input.trim();
    if let Some((_, after)) = rest.split_once("obtainium://add/") {
        rest = after;
    }
    if rest.is_empty() || (!rest.contains('/') && !rest.contains('.')) {
        return Err(HubError::InvalidLink { reason: format!("not a link: {input:?}") });
    }
    let lower = rest.to_ascii_lowercase();
    for scheme in ["https://", "http://"] {
        if lower.starts_with(scheme) {
            rest = rest.get(scheme.len()..).unwrap_or_default();
            break;
        }
    }
    if rest.to_ascii_lowercase().starts_with("www.") {
        rest = rest.get(4..).unwrap_or_default();
    }
    let mut parts = rest.split(['/', '?', '#']).filter(|p| !p.is_empty());
    let host = parts.next().unwrap_or_default();
    if !host.eq_ignore_ascii_case("github.com") {
        return Err(HubError::NotArtcraft);
    }
    let Some(owner) = parts.next() else {
        return Err(HubError::InvalidLink { reason: "the link has no GitHub owner".to_owned() });
    };
    if !owner.eq_ignore_ascii_case(ORG) {
        return Err(HubError::NotArtcraft);
    }
    match parts.next().map(|r| r.strip_suffix(".git").unwrap_or(r)) {
        Some(repo) if !repo.is_empty() => Ok(repo_source(repo)),
        _ => Ok(org_source()),
    }
}

fn org_source() -> Source {
    Source { id: ORG.to_owned(), repo: None, url: format!("https://github.com/{ORG}") }
}

fn repo_source(repo: &str) -> Source {
    Source { id: format!("{ORG}/{repo}"), repo: Some(repo.to_owned()), url: format!("https://github.com/{ORG}/{repo}") }
}

/// Reads the sources out of an Obtainium export JSON, keeping only ArtCraft repos.
#[uniffi::export]
pub fn parse_obtainium_export(json: &str) -> Result<Vec<Source>, HubError> {
    #[derive(Deserialize)]
    struct Export {
        apps: Vec<App>,
    }
    #[derive(Deserialize)]
    struct App {
        url: String,
    }
    let export: Export = serde_json::from_str(json)?;
    let mut out: Vec<Source> = Vec::new();
    for source in export.apps.iter().filter_map(|a| parse_link(&a.url).ok()) {
        if !out.iter().any(|s| s.id == source.id) {
            out.push(source);
        }
    }
    Ok(out)
}

/// The sources a fresh install starts with: the whole org.
#[uniffi::export]
pub fn default_sources() -> Vec<Source> {
    vec![org_source()]
}

/// Deep link that imports an app into Obtainium with "Include prereleases" on.
///
/// `obtainium://add/<url>` would leave that setting off, and Obtainium then reports "Could not
/// find a suitable release" for ArtCraft apps, which only publish pre-releases so far.
#[uniffi::export]
pub fn obtainium_link(repo_url: &str, package_name: &str, name: &str) -> String {
    let author = repo_url.trim_end_matches('/').rsplit('/').nth(1).unwrap_or_default();
    let settings = serde_json::json!({ "includePrereleases": true, "fallbackToOlderReleases": true });
    let app = serde_json::json!({
        "id": package_name,
        "url": repo_url,
        "author": author,
        "name": name,
        "additionalSettings": settings.to_string(),
    });
    format!("obtainium://app/{}", percent_encode(&app.to_string()))
}

/// Encodes every byte except RFC 3986's unreserved ones, so the JSON survives as one URI segment.
fn percent_encode(s: &str) -> String {
    let mut out = String::with_capacity(s.len() * 3);
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || b"-_.~".contains(&b) {
            out.push(char::from(b));
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_link_forms() {
        for (input, id) in [
            ("https://github.com/artcraft-android", "artcraft-android"),
            ("github.com/artcraft-android/", "artcraft-android"),
            ("  https://www.github.com/artcraft-android/procraft.git ", "artcraft-android/procraft"),
            (
                "http://github.com/artcraft-android/pdfcraft-android/releases/tag/v0.0.7-beta",
                "artcraft-android/pdfcraft-android",
            ),
            ("https://github.com/ArtCraft-Android/procraft", "artcraft-android/procraft"),
            ("obtainium://add/https://github.com/artcraft-android/procraft", "artcraft-android/procraft"),
            (
                "https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/artcraft-android/procraft",
                "artcraft-android/procraft",
            ),
        ] {
            assert_eq!(parse_link(input).unwrap().id, id, "{input}");
        }
    }
    #[test]
    fn canonical_url() {
        assert_eq!(
            parse_link("github.com/artcraft-android/procraft/").unwrap().url,
            "https://github.com/artcraft-android/procraft"
        );
    }
    #[test]
    fn repo_field_set_for_repo_links_only() {
        assert_eq!(parse_link("github.com/artcraft-android/procraft").unwrap().repo.as_deref(), Some("procraft"));
        assert_eq!(parse_link("github.com/artcraft-android").unwrap().repo, None);
    }
    #[test]
    fn rejects_other_owner() {
        assert!(matches!(parse_link("https://github.com/evil/artcraft-android"), Err(HubError::NotArtcraft)));
    }
    #[test]
    fn rejects_other_host() {
        assert!(matches!(parse_link("https://gitlab.com/artcraft-android/procraft"), Err(HubError::NotArtcraft)));
    }
    #[test]
    fn rejects_garbage() {
        assert!(matches!(parse_link("hello"), Err(HubError::InvalidLink { .. })));
        assert!(matches!(parse_link(""), Err(HubError::InvalidLink { .. })));
    }
    #[test]
    fn obtainium_export_keeps_only_artcraft_and_dedups() {
        let s = parse_obtainium_export(include_str!("../tests/fixtures/obtainium-export.json")).unwrap();
        assert_eq!(
            s.iter().map(|s| s.id.as_str()).collect::<Vec<_>>(),
            ["artcraft-android/procraft", "artcraft-android/pdfcraft-android"]
        );
    }
    #[test]
    fn obtainium_export_bad_json_is_parse_error() {
        assert!(matches!(parse_obtainium_export("{"), Err(HubError::Parse { .. })));
    }
    #[test]
    fn default_is_org() {
        assert_eq!(default_sources()[0].id, "artcraft-android");
    }
    #[test]
    fn obtainium_link_carries_app_with_prereleases_on() {
        let link = obtainium_link(
            "https://github.com/artcraft-android/procraft",
            "io.github.artcraftandroid.procraft",
            "ProCraft",
        );
        let json = link.strip_prefix("obtainium://app/").unwrap();
        assert!(json.bytes().all(|b| b.is_ascii_alphanumeric() || b"-_.~%".contains(&b)), "{json}");
        let app: serde_json::Value = serde_json::from_str(&percent_decode(json)).unwrap();
        assert_eq!(app["id"], "io.github.artcraftandroid.procraft");
        assert_eq!(app["url"], "https://github.com/artcraft-android/procraft");
        assert_eq!(app["author"], "artcraft-android");
        assert_eq!(app["name"], "ProCraft");
        let settings: serde_json::Value = serde_json::from_str(app["additionalSettings"].as_str().unwrap()).unwrap();
        assert_eq!(settings["includePrereleases"], true);
    }

    fn percent_decode(s: &str) -> String {
        let b = s.as_bytes();
        let mut out = Vec::new();
        let mut i = 0;
        while i < b.len() {
            if b[i] == b'%' {
                out.push(u8::from_str_radix(&s[i + 1..i + 3], 16).unwrap());
                i += 3;
            } else {
                out.push(b[i]);
                i += 1;
            }
        }
        String::from_utf8(out).unwrap()
    }
}
