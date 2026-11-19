//! ArtCraft Hub core: sources, GitHub catalogue, APK choice, downloads.

uniffi::setup_scaffolding!();

pub mod assets;
pub mod catalog;
pub mod download;
pub mod error;
pub mod github;
pub mod http;
pub mod hub;
pub mod sources;
pub mod version;

pub use error::HubError;
pub use hub::Hub;
pub use version::is_newer;
