//! Release tag vs installed version comparison.

/// Whether the release `latest_tag` (e.g. `v0.0.7-beta`) is newer than the installed `versionName`.
#[uniffi::export]
pub fn is_newer(latest_tag: &str, installed: &str) -> bool {
    let latest = strip_v(latest_tag);
    let installed = strip_v(installed);
    match (parse(latest), parse(installed)) {
        (Some(l), Some(i)) => l > i,
        _ => latest != installed,
    }
}

fn strip_v(s: &str) -> &str {
    s.trim().trim_start_matches(['v', 'V'])
}

/// Parses `1`, `1.2`, `1.2.3` with an optional `-pre` suffix, padding missing parts with `.0`.
fn parse(s: &str) -> Option<semver::Version> {
    let (core, pre) = match s.split_once('-') {
        Some((c, p)) => (c, Some(p)),
        None => (s, None),
    };
    let parts = core.split('.').count();
    let padded = match parts {
        1 => format!("{core}.0.0"),
        2 => format!("{core}.0"),
        _ => core.to_owned(),
    };
    let full = match pre {
        Some(p) => format!("{padded}-{p}"),
        None => padded,
    };
    semver::Version::parse(&full).ok()
}

#[cfg(test)]
mod tests {
    use super::is_newer;

    #[test]
    fn beta_tag_newer_than_older_beta() {
        assert!(is_newer("v0.0.7-beta", "0.0.6-beta"));
    }
    #[test]
    fn same_version_not_newer() {
        assert!(!is_newer("v0.0.7-beta", "0.0.7-beta"));
    }
    #[test]
    fn stable_newer_than_its_beta() {
        assert!(is_newer("v1.0.0", "1.0.0-beta"));
    }
    #[test]
    fn older_not_newer() {
        assert!(!is_newer("v0.0.6-beta", "0.0.7-beta"));
    }
    #[test]
    fn two_part_versions_parse() {
        assert!(is_newer("v1.2", "1.1.9"));
    }
    #[test]
    fn unparsable_installed_counts_as_update_when_different() {
        assert!(is_newer("v0.0.7-beta", "nightly-3"));
    }
    #[test]
    fn unparsable_equal_strings_not_newer() {
        assert!(!is_newer("weird", "weird"));
    }
}
