//! Choosing the right APK from a release and reading `SHA256SUMS.txt`.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

/// One file attached to a GitHub release.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct ReleaseAsset {
    pub name: String,
    pub url: String,
    pub size: u64,
}

/// Picks the APK for a device whose supported ABIs are `abis` (most preferred first).
///
/// Order: an APK built for the primary ABI, then an APK with no ABI in its name (ArtCraft uses
/// the plain name for the main 64-bit build), then APKs for the remaining ABIs.
pub fn pick_apk<'a>(assets: &'a [ReleaseAsset], abis: &[String]) -> Option<&'a ReleaseAsset> {
    let apks: Vec<(&ReleaseAsset, Option<&str>)> =
        assets.iter().filter(|a| a.name.to_ascii_lowercase().ends_with(".apk")).map(|a| (a, abi_of(&a.name))).collect();
    let for_abi = |abi: &str| apks.iter().find(|(_, c)| *c == Some(abi)).map(|(a, _)| *a);
    let (primary, rest) = match abis.split_first() {
        Some((p, r)) => (Some(p), r),
        None => (None, abis),
    };
    primary
        .and_then(|p| for_abi(p))
        .or_else(|| apks.iter().find(|(_, c)| c.is_none()).map(|(a, _)| *a))
        .or_else(|| rest.iter().find_map(|abi| for_abi(abi)))
}

/// The ABI an APK name says it was built for, or `None` for a universal/plain build.
fn abi_of(name: &str) -> Option<&'static str> {
    let n = name.to_ascii_lowercase();
    if n.contains("arm64") || n.contains("aarch64") {
        Some("arm64-v8a")
    } else if n.contains("armeabi-v7a") || n.contains("arm32") || n.contains("armv7") {
        Some("armeabi-v7a")
    } else if n.contains("x86_64") {
        Some("x86_64")
    } else {
        None
    }
}

/// Parses `sha256sum` output: file name → lower-case hex digest.
pub fn parse_sha256sums(text: &str) -> HashMap<String, String> {
    text.lines()
        .map(str::trim)
        .filter(|l| !l.is_empty() && !l.starts_with('#'))
        .filter_map(|l| {
            let (hash, name) = l.split_once(char::is_whitespace)?;
            let name = name.trim_start().trim_start_matches('*');
            (!name.is_empty()).then(|| (name.to_owned(), hash.to_ascii_lowercase()))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    const PROCRAFT: [&str; 5] = [
        "procraft-0.0.7-beta-android-arm64.apk",
        "procraft-0.0.7-beta-android-legacy-arm32.apk",
        "procraft-0.0.7-beta-linux-x86_64.AppImage",
        "procraft-0.0.7-beta-windows-x64.msi",
        "SHA256SUMS.txt",
    ];
    const PDFCRAFT: [&str; 2] = ["pdfcraft-android-0.0.7-beta.apk", "pdfcraft-android-0.0.7-beta-armeabi-v7a.apk"];
    const PHONE64: [&str; 3] = ["arm64-v8a", "armeabi-v7a", "armeabi"];
    const PHONE32: [&str; 2] = ["armeabi-v7a", "armeabi"];

    fn assets(names: &[&str]) -> Vec<ReleaseAsset> {
        names.iter().map(|n| ReleaseAsset { name: (*n).to_owned(), url: format!("https://x/{n}"), size: 1 }).collect()
    }
    fn pick(names: &[&str], abis: &[&str]) -> Option<String> {
        let abis: Vec<String> = abis.iter().map(|s| (*s).to_owned()).collect();
        pick_apk(&assets(names), &abis).map(|a| a.name.clone())
    }

    #[test]
    fn arm64_phone_gets_arm64() {
        assert_eq!(pick(&PROCRAFT, &PHONE64).as_deref(), Some("procraft-0.0.7-beta-android-arm64.apk"));
    }
    #[test]
    fn old_32bit_phone_gets_legacy() {
        assert_eq!(pick(&PROCRAFT, &PHONE32).as_deref(), Some("procraft-0.0.7-beta-android-legacy-arm32.apk"));
    }
    #[test]
    fn pdfcraft_arm64_gets_plain() {
        assert_eq!(pick(&PDFCRAFT, &PHONE64).as_deref(), Some("pdfcraft-android-0.0.7-beta.apk"));
    }
    #[test]
    fn pdfcraft_32bit_gets_v7a() {
        assert_eq!(pick(&PDFCRAFT, &PHONE32).as_deref(), Some("pdfcraft-android-0.0.7-beta-armeabi-v7a.apk"));
    }
    #[test]
    fn single_apk_for_everyone() {
        assert_eq!(
            pick(&["capcraft-android-0.0.4-beta.apk"], &["x86_64"]).as_deref(),
            Some("capcraft-android-0.0.4-beta.apk")
        );
    }
    #[test]
    fn no_apk_at_all() {
        assert_eq!(pick(&["a.AppImage", "b.msi"], &PHONE64), None);
    }
    #[test]
    fn arm64_only_release_on_32bit_phone_is_none() {
        assert_eq!(pick(&["procraft-android-arm64.apk"], &["armeabi-v7a"]), None);
    }
    #[test]
    fn apk_suffix_case_insensitive() {
        assert_eq!(pick(&["X.APK"], &PHONE64).as_deref(), Some("X.APK"));
    }
    #[test]
    fn sums_parse_both_formats() {
        let sums = parse_sha256sums("abc123  procraft.apk\nDEF456 *other.apk\n\n# c\n");
        let want: HashMap<String, String> =
            [("procraft.apk", "abc123"), ("other.apk", "def456")].map(|(k, v)| (k.to_owned(), v.to_owned())).into();
        assert_eq!(sums, want);
    }
}
