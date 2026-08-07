package dev.nucleusframework.webview.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Content types for HTML view
 */
sealed class HtmlContent {
    /** URL-based content with optional HTTP headers */
    data class Url(
        val url: String,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) : HtmlContent()

    /** HTML data content with optional parameters */
    data class Data(
        val data: String,
        val baseUrl: String? = null,
        val mimeType: String? = null,
        val encoding: String? = "utf-8",
        val historyUrl: String? = null,
    ) : HtmlContent()

    /** POST request content */
    data class Post(
        private val url: String,
        private val postData: ByteArray,
    ) : HtmlContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Post

            if (url != other.url) return false
            if (!postData.contentEquals(other.postData)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = url.hashCode()
            result = 31 * result + postData.contentHashCode()
            return result
        }
    }

    /** Navigation-only content (no rendering) */
    data object NavigatorOnly : HtmlContent()
}

/**
 * Loading states for HTML view
 */
sealed class HtmlLoadingState {
    /** Initial state before any loading has started */
    data object Initializing : HtmlLoadingState()

    /** Content is currently being loaded */
    data object Loading : HtmlLoadingState()

    /** Loading has finished, with a success or error flag */
    data class Finished(
        val isError: Boolean = false,
        val errorMessage: String? = null,
    ) : HtmlLoadingState()
}

/**
 * State class for HtmlView component
 */
class HtmlViewState {
    /** Native HTML element (iframe) */
    var htmlElement: Any? by mutableStateOf(null)
        internal set

    /** Content to be displayed */
    var content: HtmlContent by mutableStateOf(HtmlContent.NavigatorOnly)
        internal set

    /** Current loading state */
    var loadingState: HtmlLoadingState by mutableStateOf(HtmlLoadingState.Initializing)
        internal set

    /** Last URL that was successfully loaded */
    var lastLoadedUrl: String? by mutableStateOf(null)
        internal set

    /** Title of the current page */
    var pageTitle: String? by mutableStateOf(null)
        internal set

    /** Error that occurred during loading */
    var error: Throwable? by mutableStateOf(null)
        internal set
}
