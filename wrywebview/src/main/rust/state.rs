//! WebView state management and registry.

use std::collections::VecDeque;
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread::ThreadId;

use wry::WebView;

use crate::error::WebViewError;

/// Tracks the loading state and current URL of a WebView.
pub struct WebViewState {
    pub is_loading: AtomicBool,
    pub current_url: Mutex<String>,
    pub page_title: Mutex<String>,
    history: Mutex<Vec<String>>,
    history_index: Mutex<isize>,
    ipc_messages: Mutex<VecDeque<String>>,
}

impl WebViewState {
    /// Creates a new WebViewState with the given initial URL.
    pub fn new(url: String) -> Self {
        Self {
            is_loading: AtomicBool::new(true),
            current_url: Mutex::new(url),
            page_title: Mutex::new(String::new()),
            history: Mutex::new(Vec::new()),
            history_index: Mutex::new(-1),
            ipc_messages: Mutex::new(VecDeque::new()),
        }
    }

    pub fn update_current_url(&self, url: String) -> Result<(), WebViewError> {
        {
            let mut current = self
                .current_url
                .lock()
                .map_err(|_| WebViewError::Internal("url lock poisoned".to_string()))?;
            *current = url.clone();
        }
        self.update_history(url)
    }

    pub fn update_page_title(&self, title: String) -> Result<(), WebViewError> {
        let mut page_title = self
            .page_title
            .lock()
            .map_err(|_| WebViewError::Internal("title lock poisoned".to_string()))?;
        *page_title = title;
        Ok(())
    }

    pub fn push_ipc_message(&self, message: String) -> Result<(), WebViewError> {
        let mut queue = self
            .ipc_messages
            .lock()
            .map_err(|_| WebViewError::Internal("ipc queue lock poisoned".to_string()))?;
        queue.push_back(message);
        Ok(())
    }

    pub fn drain_ipc_messages(&self) -> Result<Vec<String>, WebViewError> {
        let mut queue = self
            .ipc_messages
            .lock()
            .map_err(|_| WebViewError::Internal("ipc queue lock poisoned".to_string()))?;
        Ok(queue.drain(..).collect())
    }

    pub fn can_go_back(&self) -> Result<bool, WebViewError> {
        let history = self
            .history
            .lock()
            .map_err(|_| WebViewError::Internal("history lock poisoned".to_string()))?;
        let index = self
            .history_index
            .lock()
            .map_err(|_| WebViewError::Internal("history index lock poisoned".to_string()))?;
        Ok(*index > 0 && !history.is_empty())
    }

    pub fn can_go_forward(&self) -> Result<bool, WebViewError> {
        let history = self
            .history
            .lock()
            .map_err(|_| WebViewError::Internal("history lock poisoned".to_string()))?;
        let index = self
            .history_index
            .lock()
            .map_err(|_| WebViewError::Internal("history index lock poisoned".to_string()))?;
        if history.is_empty() || *index < 0 {
            return Ok(false);
        }
        let idx = *index as usize;
        Ok(idx < history.len().saturating_sub(1))
    }

    fn update_history(&self, new_url: String) -> Result<(), WebViewError> {
        let mut history = self
            .history
            .lock()
            .map_err(|_| WebViewError::Internal("history lock poisoned".to_string()))?;
        let mut index = self
            .history_index
            .lock()
            .map_err(|_| WebViewError::Internal("history index lock poisoned".to_string()))?;

        if *index >= 0 {
            let idx = *index as usize;
            if history.get(idx).is_some_and(|url| url == &new_url) {
                return Ok(());
            }
            let back_url = if idx > 0 { history.get(idx - 1) } else { None };
            let forward_url = history.get(idx + 1);
            if back_url.is_some_and(|url| url == &new_url) {
                *index -= 1;
                return Ok(());
            }
            if forward_url.is_some_and(|url| url == &new_url) {
                *index += 1;
                return Ok(());
            }

            if idx + 1 < history.len() {
                history.truncate(idx + 1);
            }
        } else {
            history.clear();
        }

        history.push(new_url);
        *index = (history.len() as isize) - 1;
        Ok(())
    }
}

/// Entry in the WebView registry containing the pointer and metadata.
pub struct WebViewEntry {
    pub ptr: *mut WebView,
    pub thread_id: ThreadId,
    pub state: Arc<WebViewState>,
    #[allow(dead_code)]
    pub context: Option<wry::WebContext>,
}

// The raw pointer is only dereferenced on the creating thread (checked at runtime).
unsafe impl Send for WebViewEntry {}
unsafe impl Sync for WebViewEntry {}

static NEXT_ID: AtomicU64 = AtomicU64::new(1);
static WEBVIEWS: OnceLock<Mutex<HashMap<u64, WebViewEntry>>> = OnceLock::new();

/// Returns the global WebView registry.
pub fn webviews() -> &'static Mutex<HashMap<u64, WebViewEntry>> {
    WEBVIEWS.get_or_init(|| Mutex::new(HashMap::new()))
}

/// Generates a new unique WebView ID.
pub fn next_id() -> u64 {
    NEXT_ID.fetch_add(1, Ordering::Relaxed)
}

/// Executes a closure with access to the WebView, ensuring thread safety.
pub fn with_webview<F, R>(id: u64, f: F) -> Result<R, WebViewError>
where
    F: FnOnce(&WebView) -> Result<R, WebViewError>,
{
    let (ptr, thread_id) = {
        let map = webviews()
            .lock()
            .map_err(|_| WebViewError::Internal("webview registry lock poisoned".to_string()))?;
        let entry = map.get(&id).ok_or(WebViewError::WebViewNotFound(id))?;
        (entry.ptr, entry.thread_id)
    };

    if thread_id != std::thread::current().id() {
        return Err(WebViewError::WrongThread(id));
    }

    let webview = unsafe { &*ptr };
    f(webview)
}

/// Retrieves the state for a WebView by ID.
pub fn get_state(id: u64) -> Result<Arc<WebViewState>, WebViewError> {
    let map = webviews()
        .lock()
        .map_err(|_| WebViewError::Internal("webview registry lock poisoned".to_string()))?;
    let entry = map.get(&id).ok_or(WebViewError::WebViewNotFound(id))?;
    Ok(Arc::clone(&entry.state))
}

/// Registers a new WebView in the global registry.
pub fn register(
    webview: WebView,
    state: Arc<WebViewState>,
    context: Option<wry::WebContext>,
) -> Result<u64, WebViewError> {
    let id = next_id();
    let entry = WebViewEntry {
        ptr: Box::into_raw(Box::new(webview)),
        thread_id: std::thread::current().id(),
        state,
        context,
    };

    let mut map = webviews()
        .lock()
        .map_err(|_| WebViewError::Internal("webview registry lock poisoned".to_string()))?;
    map.insert(id, entry);
    Ok(id)
}

/// Removes and destroys a WebView from the registry.
pub fn unregister(id: u64) -> Result<(), WebViewError> {
    let entry = {
        let mut map = webviews()
            .lock()
            .map_err(|_| WebViewError::Internal("webview registry lock poisoned".to_string()))?;

        let Some(entry) = map.get(&id) else {
            return Ok(());
        };

        if entry.thread_id != std::thread::current().id() {
            return Err(WebViewError::WrongThread(id));
        }

        map.remove(&id)
    };

    if let Some(entry) = entry {
        unsafe {
            drop(Box::from_raw(entry.ptr));
        }
    }

    Ok(())
}
