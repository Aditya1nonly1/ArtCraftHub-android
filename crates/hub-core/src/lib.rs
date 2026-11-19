//! ArtCraft Hub core: sources, GitHub catalogue, APK choice, downloads.

uniffi::setup_scaffolding!();

pub mod error;
pub mod version;

pub use error::HubError;
pub use version::is_newer;
