package dev.nucleusframework.webview.web

sealed class LoadingState {
    data object Initializing : LoadingState()

    data class Loading(
        val progress: Float,
    ) : LoadingState()

    data object Finished : LoadingState()
}

