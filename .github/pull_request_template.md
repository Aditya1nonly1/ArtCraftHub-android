<!-- Small, focused pull requests are easier to review: one fix or feature per PR. -->

## Summary

<!-- What does this change? -->

## Why

<!-- The problem it solves. Link the issue: "Fixes #123". -->

Fixes #

## How

<!-- Anything a reviewer should know about the approach. Delete if obvious. -->

## Testing

<!-- What you ran and on which device/emulator. -->

- [ ] `cargo test --workspace` (if the Rust core changed)
- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug` (from `android/`)
- [ ] Tried it on a phone or emulator

## Screenshots

<!-- For UI changes: before and after. Delete if not a UI change. -->

## Breaking changes

<!-- Anything users or other code must change? Otherwise "None". -->

None

## Checklist

- [ ] The PR does one thing
- [ ] `cargo fmt --all` and `cargo clippy --workspace --all-targets -- -D warnings` are clean (Rust changes)
- [ ] `CHANGELOG.md` has a line under "Unreleased" for user-visible changes
- [ ] No secrets, keys or personal data in the diff
