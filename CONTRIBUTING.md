# Contributing to ArtCraft Hub

Thanks for helping! Whether you fix a typo, report a bug or add a feature, you're welcome here,
including if this is your first open-source contribution.

## Where to start

- Issues labelled [`good first issue`](https://github.com/l41n-pr0t0/ArtCraftHub-android/labels/good%20first%20issue)
  are small and come with the files to look at and how to test.
- [`help wanted`](https://github.com/l41n-pr0t0/ArtCraftHub-android/labels/help%20wanted) issues are bigger
  pieces of work the maintainer would like help with.
- Before starting on something that isn't an issue yet, open one (or a
  [discussion](https://github.com/l41n-pr0t0/ArtCraftHub-android/discussions)) so we can agree on the approach.
- Comment on an issue to say you're working on it, so nobody duplicates your work.

## How the project fits together

```
crates/hub-core      Rust: GitHub client, release parsing, APK choice, downloads, sources
crates/hub-bindgen   the uniffi-bindgen binary that generates the Kotlin bindings
android/app          Kotlin + Jetpack Compose app
  ui/                screens (CatalogScreen, DetailScreen, SourcesScreen, SettingsScreen),
                     shared pieces (Components.kt, Glyphs.kt) and HubViewModel
  ui/theme/          colours, type and the glass panel style
  system/            Android installer, package events, Obtainium links
  core/              the gateway to the Rust core
```

The Gradle build compiles the Rust core with `cargo-ndk` and generates its Kotlin bindings, so
you need both toolchains even for a UI change.

## Setting up

**With Nix:** `nix develop` gives you Rust with the Android targets, `cargo-ndk` and JDK 21. You
still need the Android SDK (see below).

**Without Nix:**

1. **JDK 21** (a full JDK with `javac`, not only a runtime). Set `JAVA_HOME` to it.
2. **Rust** through [rustup](https://rustup.rs), then:
   ```sh
   rustup target add aarch64-linux-android x86_64-linux-android
   cargo install cargo-ndk --version 4.1.2 --locked
   ```
3. **Android SDK** (Android Studio, or the command-line tools) with platform 37, build-tools and
   NDK `27.2.12479018`:
   ```sh
   sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools" "ndk;27.2.12479018"
   ```
   Point the build at it with `ANDROID_HOME`, or `sdk.dir=/path/to/sdk` in `android/local.properties`.

Then:

```sh
cargo test --workspace                                  # Rust core tests
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug         # Kotlin tests and Android lint
./gradlew :app:installDebug -Phub.abis=arm64-v8a        # build and install on a connected phone
```

`-Phub.abis=arm64-v8a` builds the Rust library for one ABI only, which is much faster. The debug
build installs next to the release as "ArtCraft Hub" with the package
`io.github.l41n_pr0t0.artcrafthub.debug`.

## Making a change

1. Fork the repository and create a branch: `git checkout -b fix/tab-overflow`.
2. Keep the change focused: one fix or feature per pull request.
3. Add or update tests where it makes sense: Rust tests live next to the code in
   `crates/hub-core/src`, Kotlin tests in `android/app/src/test`.
4. Run the checks:
   ```sh
   cargo fmt --all
   cargo clippy --workspace --all-targets -- -D warnings
   cargo test --workspace
   (cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug)
   ```
5. For a UI change, try it on a phone or emulator, ideally with a large font size too
   (Settings → Display → Font size), and add before/after screenshots to the pull request.
6. Add a line to [CHANGELOG.md](CHANGELOG.md) under "Unreleased" if users will notice the change.
7. Open the pull request and fill in the template. CI runs the same checks.

### Commit messages

Short, in the present tense, with a type and an optional area, as in the existing history:

```
feat(ui): readable release notes
fix(core): a failed refresh no longer empties the catalogue
docs: screenshots of the new design
```

Types: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `ci`, `chore`.

## Style

- Rust: `rustfmt` and `clippy` settings are in the repository (`rustfmt.toml`, `clippy.toml`).
- Kotlin: the official Kotlin style (`kotlin.code.style=official`). Keep composables small, and
  put shared pieces in `Components.kt`.
- UI: the app is monochrome glass on a near-black ground (`ui/theme/Theme.kt`). Use the `glass()`
  modifier, `Paper`/`Muted`/`Faint` colours and the line icons in `Glyphs.kt`, not new colours or
  an icon library.
- User-facing text: plain and specific ("No internet connection."), not technical.

## Releases

The maintainer releases by setting `hub.version` and `hub.versionCode` in
`android/gradle.properties` and pushing a `v<version>` tag; see the README.

## Code of conduct

Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md).
