//! Errors returned to Kotlin.

/// Everything that can go wrong in the hub core.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum HubError {
    #[error("invalid link: {reason}")]
    InvalidLink { reason: String },
    #[error("only github.com/artcraft-android links are accepted")]
    NotArtcraft,
    #[error("no internet connection")]
    Offline,
    #[error("GitHub rate limit reached until {reset_at}")]
    RateLimited { reset_at: u64 },
    #[error("not found")]
    NotFound,
    #[error("checksum mismatch")]
    ChecksumMismatch,
    #[error("cancelled")]
    Cancelled,
    #[error("I/O error: {message}")]
    Io { message: String },
    #[error("parse error: {message}")]
    Parse { message: String },
}

impl From<std::io::Error> for HubError {
    fn from(e: std::io::Error) -> Self {
        Self::Io { message: e.to_string() }
    }
}

impl From<serde_json::Error> for HubError {
    fn from(e: serde_json::Error) -> Self {
        Self::Parse { message: e.to_string() }
    }
}
