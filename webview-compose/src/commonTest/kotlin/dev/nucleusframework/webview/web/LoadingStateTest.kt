package dev.nucleusframework.webview.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Shared multiplatform suite — must pass on JVM, Android host, iOS simulator, Wasm.
 */
class LoadingStateTest {
    @Test
    fun loadingHoldsProgress() {
        val state = LoadingState.Loading(0.42f)
        assertIs<LoadingState.Loading>(state)
        assertEquals(0.42f, state.progress, absoluteTolerance = 0.0001f)
    }

    @Test
    fun finishedAndInitializingAreDistinct() {
        assertTrue(LoadingState.Finished != LoadingState.Initializing)
        assertIs<LoadingState.Finished>(LoadingState.Finished)
        assertIs<LoadingState.Initializing>(LoadingState.Initializing)
    }
}
