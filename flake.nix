{
  description = "ArtCraft Hub for Android: development shell (Rust with Android targets, cargo-ndk, JDK)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { nixpkgs, rust-overlay, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        overlays = [ rust-overlay.overlays.default ];
      };
      rust = pkgs.rust-bin.stable.latest.default.override {
        extensions = [ "rust-src" "clippy" "rustfmt" "rust-analyzer" ];
        targets = [
          "aarch64-linux-android"   # modern 64-bit phones and tablets
          "armv7-linux-androideabi" # older 32-bit devices
          "x86_64-linux-android"    # the Android emulator on a PC
        ];
      };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ rust pkgs.cargo-ndk pkgs.jdk21 ];

        # The Android SDK lives outside Nix (installed by sdkmanager / Android Studio);
        # nix-ld lets its prebuilt NDK tools run on NixOS.
        shellHook = ''
          export ANDROID_HOME="$HOME/Android/Sdk"
          export ANDROID_SDK_ROOT="$ANDROID_HOME"
          export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
          export JAVA_HOME="${pkgs.jdk21}"
          export PATH="$ANDROID_HOME/platform-tools:$PATH"
        '';
      };
    };
}
