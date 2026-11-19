//! The HTTP seam: a small trait so the GitHub client and downloads can be tested without a network.

use std::io::Read;
use std::time::Duration;

use crate::HubError;

/// Result of a conditional GET.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum HttpResponse {
    Ok { body: String, etag: Option<String> },
    NotModified,
}

/// Blocking HTTP used by the core.
pub trait Http: Send + Sync {
    /// GET `url`, sending `If-None-Match: etag` when given.
    fn get(&self, url: &str, etag: Option<&str>) -> Result<HttpResponse, HubError>;
    /// Streams `url`; returns the body reader and the content length if known.
    fn open(&self, url: &str) -> Result<(Box<dyn Read + Send>, Option<u64>), HubError>;
}

/// Maps an HTTP status and GitHub rate-limit headers to an error, or `None` for success/304.
pub fn classify(status: u16, remaining: Option<&str>, reset: Option<&str>) -> Option<HubError> {
    match status {
        200..=299 | 304 => None,
        403 | 429 if remaining.map(str::trim) == Some("0") => {
            Some(HubError::RateLimited { reset_at: reset.and_then(|r| r.trim().parse().ok()).unwrap_or(0) })
        }
        404 => Some(HubError::NotFound),
        code => Some(HubError::Io { message: format!("HTTP {code}") }),
    }
}

fn header<'a>(res: &'a ureq::http::Response<ureq::Body>, name: &str) -> Option<&'a str> {
    res.headers().get(name).and_then(|v| v.to_str().ok())
}

/// Sends a GET and turns a non-success status into a `HubError`.
fn send(req: ureq::RequestBuilder<ureq::typestate::WithoutBody>) -> Result<ureq::http::Response<ureq::Body>, HubError> {
    let res = req.call().map_err(|e| match e {
        ureq::Error::Io(io) => HubError::Io { message: io.to_string() },
        _ => HubError::Offline,
    })?;
    match classify(res.status().as_u16(), header(&res, "x-ratelimit-remaining"), header(&res, "x-ratelimit-reset")) {
        Some(err) => Err(err),
        None => Ok(res),
    }
}

/// The real client, backed by `ureq`.
pub struct UreqHttp {
    agent: ureq::Agent,
}

impl Default for UreqHttp {
    fn default() -> Self {
        Self::new()
    }
}

impl UreqHttp {
    pub fn new() -> Self {
        let config = ureq::Agent::config_builder()
            .http_status_as_error(false)
            .timeout_connect(Some(Duration::from_secs(30)))
            .timeout_recv_response(Some(Duration::from_secs(30)))
            .user_agent("ArtCraftHub-android")
            .build();
        Self { agent: ureq::Agent::new_with_config(config) }
    }
}

impl Http for UreqHttp {
    fn get(&self, url: &str, etag: Option<&str>) -> Result<HttpResponse, HubError> {
        let mut req = self.agent.get(url);
        if url.starts_with("https://api.github.com/") {
            req = req.header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28");
        }
        if let Some(etag) = etag {
            req = req.header("If-None-Match", etag);
        }
        let mut res = send(req)?;
        if res.status().as_u16() == 304 {
            return Ok(HttpResponse::NotModified);
        }
        let etag = header(&res, "etag").map(str::to_owned);
        let body = res.body_mut().read_to_string().map_err(|e| HubError::Io { message: e.to_string() })?;
        Ok(HttpResponse::Ok { body, etag })
    }

    fn open(&self, url: &str) -> Result<(Box<dyn Read + Send>, Option<u64>), HubError> {
        let res = send(self.agent.get(url))?;
        let len = res.body().content_length();
        Ok((Box::new(res.into_body().into_reader()), len))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rate_limit_when_none_remaining() {
        assert!(matches!(
            classify(403, Some("0"), Some("1700000000")),
            Some(HubError::RateLimited { reset_at: 1_700_000_000 })
        ));
        assert!(matches!(classify(429, Some("0"), Some("5")), Some(HubError::RateLimited { reset_at: 5 })));
    }
    #[test]
    fn forbidden_with_quota_left_is_io() {
        assert!(matches!(classify(403, Some("5"), None), Some(HubError::Io { .. })));
    }
    #[test]
    fn not_found() {
        assert!(matches!(classify(404, None, None), Some(HubError::NotFound)));
    }
    #[test]
    fn success_and_not_modified_are_not_errors() {
        assert!(classify(200, None, None).is_none());
        assert!(classify(304, None, None).is_none());
    }
    #[test]
    fn server_error_is_io() {
        assert!(matches!(classify(502, None, None), Some(HubError::Io { message }) if message == "HTTP 502"));
    }
}
