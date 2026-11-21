# Security policy

## Supported versions

Only the latest release gets security fixes. Update through the
[releases page](https://github.com/l41n-pr0t0/ArtCraftHub-android/releases) or Obtainium.

| Version | Supported |
|---|---|
| 1.0.x | Yes |
| < 1.0 | No |

## Reporting a vulnerability

Please **don't open a public issue**. Report it privately through GitHub:
[Security → Report a vulnerability](https://github.com/l41n-pr0t0/ArtCraftHub-android/security/advisories/new).

Include what an attacker could do, the steps to reproduce, and the hub version. You should hear
back within a week. Once a fix is released, the advisory is published with credit to you, unless
you'd rather stay anonymous.

## What's in scope

The hub installs software, so these matter most:

- Installing an APK that doesn't match what the release published: checksum checks skipped or
  bypassed, a download swapped, or the wrong repository trusted.
- Accepting sources outside `github.com/artcraft-android`.
- Anything in the hub's own code that leaks data or runs code it shouldn't.

Out of scope: vulnerabilities in the ArtCraft apps themselves (report those to
[their repositories](https://github.com/artcraft-android)), and GitHub's API rate limits.

## How releases are protected

- Every release APK is signed with the same release key, so Android refuses an "update" signed
  by anyone else.
- `SHA256SUMS.txt` is published with every release.
- The signing key is kept out of the repository; the release workflow gets it from encrypted
  repository secrets.
