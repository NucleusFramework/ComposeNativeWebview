@file:OptIn(ExperimentalUuidApi::class, ExperimentalWasmJsInterop::class)
package dev.nucleusframework.webview.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.round
import dev.nucleusframework.webview.util.KLogger
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.Node
import org.w3c.dom.events.Event
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A Composable that renders HTML content using an iframe
 *
 * @param state The state of the HTML view
 * @param modifier The modifier for this composable
 * @param navigator The navigator for HTML navigation events
 * @param onCreated Callback invoked when the view is created
 * @param onDispose Callback invoked when the view is disposed
 */
@Composable
fun HtmlView(
    state: HtmlViewState,
    modifier: Modifier = Modifier,
    navigator: HtmlViewNavigator = rememberHtmlViewNavigator(),
    onCreated: (HTMLIFrameElement) -> Unit = {},
    onDispose: (HTMLIFrameElement) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val element = remember { mutableStateOf<HTMLIFrameElement?>(null) }
    val root: Node = document.body?.shadowRoot ?: document.body!!
    val density = LocalDensity.current.density
    val focusManager = LocalFocusManager.current
    val htmlViewFocusRequester = remember { FocusRequester() }

    val componentInfo = remember { ComponentInfo<HTMLIFrameElement>() }
    val focusSwitcher = remember { FocusSwitcher(componentInfo, focusManager) }
    val eventsInitialized = remember { mutableStateOf(false) }
    val componentReady = remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .focusRequester(htmlViewFocusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    element.value?.let(::requestFocus)
                }
            }
            .focusTarget()
            .onGloballyPositioned { coordinates ->
                val location = coordinates.positionInWindow().round()
                val size = coordinates.size
                if (componentReady.value) {
                    val container = componentInfo.container as HTMLDivElement
                    container.style.width = "${size.width / density}px"
                    container.style.height = "${size.height / density}px"
                    container.style.left = "${location.x / density}px"
                    container.style.top = "${location.y / density}px"
                }
            }
    ) {
        focusSwitcher.Content()
    }

    DisposableEffect(Unit) {
        componentInfo.container = document.createElement("div") as HTMLDivElement
        componentInfo.component = document.createElement("iframe") as HTMLIFrameElement
        componentInfo.component.tabIndex = 0
        componentReady.value = true
        val container = componentInfo.container as HTMLDivElement

        root.appendChild(container)
        container.append(componentInfo.component)

        container.style.position = "absolute"
        container.style.margin = "0px"
        container.style.padding = "0px"
        container.style.overflowX = "hidden"
        container.style.overflowY = "hidden"
        container.style.zIndex = "1000"

        componentInfo.component.style.position = "absolute"
        componentInfo.component.style.left = "0px"
        componentInfo.component.style.top = "0px"
        componentInfo.component.style.margin = "0px"
        element.value = componentInfo.component
        state.htmlElement = componentInfo.component
        onCreated(componentInfo.component)

        var lastAppliedContent: HtmlContent? = null

        componentInfo.updater = Updater(componentInfo.component) { iframe: HTMLIFrameElement ->
            if (!eventsInitialized.value) {
                eventsInitialized.value = true

                val syncFocusToIframe = {
                    try {
                        htmlViewFocusRequester.requestFocus()
                        requestFocus(iframe)
                        iframe.contentWindow?.focus()
                    } catch (_: Throwable) {
                    }
                }

                val loadCallback: (Event) -> Unit = {
                    state.loadingState = HtmlLoadingState.Finished()

                    try {
                        registerDomListener(iframe.contentWindow, "focus") {
                            htmlViewFocusRequester.requestFocus()
                        }

                        registerDomListener(iframe.contentDocument, "focusin") {
                            htmlViewFocusRequester.requestFocus()
                        }

                        registerDomListener(iframe.contentDocument, "pointerdown") {
                            syncFocusToIframe()
                        }

                        registerDomListener(iframe.contentDocument, "mousedown") {
                            syncFocusToIframe()
                        }
                    } catch (_: Throwable) {
                        // Cross-origin iframe: cannot access contentDocument/contentWindow listeners
                    }

                    when (val content = state.content) {
                        is HtmlContent.Url -> {
                            // Safe: use the URL requested, do not inspect iframe internals
                            if (content.url != "about:blank") {
                                state.lastLoadedUrl = content.url
                            }
                            state.pageTitle = null
                        }

                        is HtmlContent.Data -> {
                            // srcdoc / inline HTML is usually same-origin unless sandboxing removes it
                            try {
                                iframe.contentDocument?.title?.let { state.pageTitle = it }
                                val href = iframe.contentWindow?.location?.href ?: iframe.src
                                if (href != "about:blank") {
                                    state.lastLoadedUrl = href
                                }
                            } catch (t: Throwable) {
                                KLogger.e(t = t, tag = "HtmlView") {
                                    "Failed to get URL or title"
                                }
                            }
                        }

                        else -> Unit
                    }
                }

                val errorCallback: (Event) -> Unit = {
                    state.loadingState = HtmlLoadingState.Finished(
                        isError = true,
                        errorMessage = "Failed to load content",
                    )
                }

                registerDomListener(iframe, "focus") {
                    htmlViewFocusRequester.requestFocus()
                }

                registerDomListener(iframe, "pointerdown") {
                    syncFocusToIframe()
                }
                iframe.addEventListener("load", loadCallback)
                iframe.addEventListener("error", errorCallback)

                scope.launch {
                    navigator.handleNavigationEvents(iframe)
                }
            }

            val content = state.content
            if (content != lastAppliedContent) {
                lastAppliedContent = content
                when (content) {
                    is HtmlContent.Url -> {
                        iframe.src = content.url
                        state.loadingState = HtmlLoadingState.Loading
                    }

                    is HtmlContent.Data -> {
                        iframe.srcdoc = content.data
                        state.loadingState = HtmlLoadingState.Loading
                        addContentIdentifierJs(iframe)
                    }

                    is HtmlContent.Post -> {
                        // POST requests not directly supported in iframe
                    }

                    HtmlContent.NavigatorOnly -> {
                        // No content update needed
                    }
                }
            }

            iframe.style.border = "none"
            iframe.style.width = "100%"
            iframe.style.height = "100%"
            iframe.style.overflowX = "auto"
            iframe.style.overflowY = "auto"
            iframe.style.backgroundColor = "white"
        }

        onDispose {
            root.removeChild(componentInfo.container)
            componentInfo.updater.dispose()
            element.value?.let { onDispose(it) }
            state.htmlElement = null
            state.loadingState = HtmlLoadingState.Initializing
        }
    }

    SideEffect {
        if (element.value != null) {
            componentInfo.updater.update(componentInfo.component)
        }
    }
}

/**
 * Helper class to manage component information
 */
class ComponentInfo<T : Element> {
    lateinit var container: Element
    lateinit var component: T
    lateinit var updater: Updater<T>
}

/**
 * Helper class to manage focus switching
 */
class FocusSwitcher<T : Element>(
    private val info: ComponentInfo<T>,
    private val focusManager: FocusManager,
) {
    private val backwardRequester = FocusRequester()
    private val forwardRequester = FocusRequester()
    private var isRequesting = false

    private fun moveBackward() {
        try {
            isRequesting = true
            backwardRequester.requestFocus()
        } finally {
            isRequesting = false
        }
        focusManager.moveFocus(FocusDirection.Previous)
    }

    private fun moveForward() {
        try {
            isRequesting = true
            forwardRequester.requestFocus()
        } finally {
            isRequesting = false
        }
        focusManager.moveFocus(FocusDirection.Next)
    }

    @Composable
    fun Content() {
        Box(
            Modifier
                .focusRequester(backwardRequester)
                .onFocusChanged {
                    if (it.isFocused && !isRequesting) {
                        focusManager.clearFocus(force = true)
                        val component = info.container.firstElementChild
                        if (component != null) {
                            requestFocus(component)
                        } else {
                            moveForward()
                        }
                    }
                }.focusTarget()
        )
        Box(
            Modifier
                .focusRequester(forwardRequester)
                .onFocusChanged {
                    if (it.isFocused && !isRequesting) {
                        focusManager.clearFocus(force = true)

                        val component = info.container.lastElementChild
                        if (component != null) {
                            requestFocus(component)
                        } else {
                            moveBackward()
                        }
                    }
                }.focusTarget()
        )
    }
}

/**
 * A utility class for updating a component's view in response to state changes
 */
class Updater<T : Element>(
    private val component: T,
    update: (T) -> Unit
) {
    private var isDisposed = false

    private val snapshotObserver =
        SnapshotStateObserver { command ->
            command()
        }

    private val scheduleUpdate = { _: T ->
        if (isDisposed.not()) {
            performUpdate()
        }
    }

    var update: (T) -> Unit = update
        set(value) {
            if (field != value) {
                field = value
                performUpdate()
            }
        }

    private fun performUpdate() {
        snapshotObserver.observeReads(
            scope = component,
            onValueChangedForScope = scheduleUpdate
        ) {
            update(component)
        }
    }

    init {
        snapshotObserver.start()
        performUpdate()
    }

    fun dispose() {
        snapshotObserver.stop()
        snapshotObserver.clear()
        isDisposed = true
    }
}

/**
 * Composable for displaying a URL in an HtmlView
 */
@Composable
fun HtmlViewUrl(
    url: String,
    modifier: Modifier = Modifier,
    headers: Map<String, String> = emptyMap(),
    navigator: HtmlViewNavigator = rememberHtmlViewNavigator(),
) {
    val state = rememberHtmlViewState()

    LaunchedEffect(url, headers) {
        state.content = HtmlContent.Url(url, headers)
    }

    HtmlView(
        state = state,
        modifier = modifier,
        navigator = navigator
    )
}

/**
 * Composable for displaying HTML content in an HtmlView
 */
@Composable
fun HtmlViewContent(
    htmlContent: String,
    modifier: Modifier = Modifier,
    baseUrl: String? = null,
    navigator: HtmlViewNavigator = rememberHtmlViewNavigator(),
) {
    val state = rememberHtmlViewState()

    LaunchedEffect(htmlContent, baseUrl) {
        state.content = HtmlContent.Data(htmlContent, baseUrl)
    }

    HtmlView(
        state = state,
        modifier = modifier,
        navigator = navigator,
        onCreated = {},
        onDispose = {}
    )
}

/**
 * Create and remember an HtmlViewState instance
 */
@Composable
fun rememberHtmlViewState(): HtmlViewState = remember { HtmlViewState() }

// Container for HTML elements
val LocalLayerContainer = staticCompositionLocalOf {
    document.body!!
}
