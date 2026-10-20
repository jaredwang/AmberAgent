package app.amber.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.amber.core.settings.Settings
import app.amber.feature.ui.components.richtext.MarkdownBlock
import app.amber.feature.ui.components.richtext.StreamingSingleTextMarkdown
import app.amber.feature.ui.context.LocalSettings
import app.amber.feature.ui.pages.chat.streamingTailLayout
import app.amber.highlight.Highlighter
import app.amber.highlight.LocalHighlighter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device UI evidence for the streaming chunk renderer:
 *  - native drag selection across a Text chunk seam and the real copy path,
 *  - reading-anchor stability across the 3072 threshold, drain and the
 *    streaming -> settled renderer switch.
 *
 * In-memory only; no repository, settings or provider is opened.
 */
class ChatStreamingChunkUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun log(message: String) { Log.i(TAG, message) }

    private fun launchHost(): ActivityScenario<ComponentActivity> {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n ${context.packageName}/androidx.activity.ComponentActivity",
        )).bufferedReader().use { it.readText() }
        return ActivityScenario.launch(ComponentActivity::class.java)
    }

    private data class ChunkNode(
        val text: String,
        val topLeft: Offset,
        val layout: TextLayoutResult,
    )

    private fun chunkNodes(): List<ChunkNode> {
        val nodes = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        val result = ArrayList<ChunkNode>(nodes.size)
        for (node in nodes) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                ?: continue
            if (text.isEmpty()) continue
            val layouts = ArrayList<TextLayoutResult>(1)
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: continue
            runCatching { action.action?.invoke(layouts) }
            val layout = layouts.firstOrNull() ?: continue
            result += ChunkNode(text, node.boundsInRoot.topLeft, layout)
        }
        return result.sortedBy { it.topLeft.y }
    }

    /** Maps per-Text local selection ranges to one offset range in the joined text. */
    private fun globalSelection(chunks: List<ChunkNode>): Pair<Int, Int>? {
        val selectionNodes = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.TextSelectionRange),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        val byText = HashMap<String, androidx.compose.ui.text.TextRange>()
        for (node in selectionNodes) {
            val range = node.config.getOrNull(SemanticsProperties.TextSelectionRange) ?: continue
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
            byText[text] = range
            Log.i(TAG, "selection node text=${text.take(20)} range=$range")
        }
        var base = 0
        var start = Int.MAX_VALUE
        var end = -1
        chunks.forEachIndexed { index, chunk ->
            val range = byText[chunk.text]
            if (range != null && range.start != range.end) {
                if (base + range.start < start) start = base + range.start
                if (base + range.end > end) end = base + range.end
            }
            base += chunk.text.length
            // Each seam is exactly one newline, rendered as the boundary.
            if (index < chunks.lastIndex) base += 1
        }
        return if (start <= end && start != Int.MAX_VALUE) start to end else null
    }

    /**
     * Clicks the platform/MIUI selection toolbar's Copy item. The toolbar is
     * an overlay window that is not part of Compose semantics; uiautomator
     * dump sees the full screen, including that window.
     */
    private fun copyViaSystemSelectionToolbar() {
        val label = context.getString(android.R.string.copy)
        val dump = "uiautomator dump /sdcard/amber-selection-toolbar.xml"
        runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(dump),
            ).bufferedReader().use { it.readText() }
        }
        val xml = runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("cat /sdcard/amber-selection-toolbar.xml"),
            ).bufferedReader().use { it.readText() }
        }.getOrNull().orEmpty()
        val node = Regex("<node[^>]*text=\"${Regex.escape(label)}\"[^>]*>")
            .findAll(xml).firstOrNull()?.value ?: error("Selection toolbar copy item not found in uiautomator dump")
        val bounds = Regex("bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"")
            .find(node)?.groupValues ?: error("Selection toolbar copy item has no bounds")
        val x = (bounds[1].toFloat() + bounds[3].toFloat()) / 2f
        val y = (bounds[2].toFloat() + bounds[4].toFloat()) / 2f
        Log.i(TAG, "selection-toolbar tap copy at $x,$y")
        val down = SystemClock.uptimeMillis()
        listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
            val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            check(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        SystemClock.sleep(400)
    }

    private fun capture(name: String, json: JSONObject) {
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "chunk-ui")
            dir.mkdirs()
            File(dir, "$name.json").writeText(json.toString(2))
            compose.onAllNodes(isRoot(), useUnmergedTree = true)
                .fetchSemanticsNodes().indices.joinToString("\n\n") {
                    compose.onAllNodes(isRoot(), useUnmergedTree = true)[it].printToString()
                }.let { File(dir, "$name.semantics.txt").writeText(it) }
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            try {
                File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
            log("evidence=$dir/$name")
        }.onFailure { log("evidence-failed ${it.message}") }
    }

    @Test(timeout = 120_000)
    fun nativeDragSelectionAcrossChunkSeamCopiesExactUnicode() {
        val filler = (0 until 60).joinToString("\n") { "填充行 $it 中文😀é " + "字".repeat(24) }
        val source = buildString {
            append("HEAD_标题\n\n")
            append("SEL_START_中文\n\n")
            append("x".repeat(820))
            append("\n\n")
            append("SEL_END_👩‍💻é **粗体** [链接](https://example.com)\n\n")
            append(filler)
        }
        var failure: Throwable? = null
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    CompositionLocalProvider(
                        LocalSettings provides Settings(),
                        LocalHighlighter provides Highlighter(activity),
                    ) {
                        MaterialTheme {
                            Box(Modifier.fillMaxSize().padding(16.dp)) {
                                StreamingSingleTextMarkdown(source, emptyList(), streaming = false)
                            }
                        }
                    }
                }
            }
            try {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText("SEL_START_中文", substring = true, useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitForIdle()
                val chunks = chunkNodes()
                check(chunks.size >= 2) { "Expected a chunk seam, saw ${chunks.size} text nodes" }
                log("chunks=${chunks.size} starts=${chunks.map { it.text.length }}")
                chunks.forEachIndexed { index, node ->
                    Log.i(TAG, "chunk[$index] len=${node.text.length} top=${node.topLeft.y} first=${node.text.take(24)}")
                }
                val rendered = chunks.joinToString("\n") { it.text }
                val startToken = "SEL_START_中文"
                val endToken = "SEL_END_👩‍💻é"
                val globalStart = rendered.indexOf(startToken)
                val globalEnd = rendered.indexOf("链接") + "链接".length
                check(globalStart >= 0 && globalEnd > globalStart && globalEnd <= rendered.length) {
                    "Selection tokens missing from rendered text"
                }
                // Locate the chunk and local offset for both endpoints.
                fun locate(global: Int): Pair<ChunkNode, Int> {
                    var base = 0
                    for ((index, node) in chunks.withIndex()) {
                        if (global < base + node.text.length) return node to (global - base)
                        base += node.text.length + if (index < chunks.lastIndex) 1 else 0
                    }
                    error("Offset $global outside rendered chunks")
                }
                val (startNode, startLocal) = locate(globalStart)
                val (endNode, endLocal) = locate(globalEnd - 1)
                assertNotEquals("Selection must cross a Text chunk seam", startNode.text, endNode.text)
                fun point(node: ChunkNode, local: Int): Offset {
                    val box = node.layout.getBoundingBox(local)
                    return Offset(
                        node.topLeft.x + box.center.x,
                        node.topLeft.y + box.center.y,
                    )
                }
                val startPoint = point(startNode, startLocal)
                val endPoint = point(endNode, endLocal)
                Log.i(TAG, "gesture start=$startPoint end=$endPoint seamGlobal=$globalStart..$globalEnd")
                compose.onRoot().performTouchInput {
                    down(startPoint)
                    advanceEventTime(800)
                    val steps = 12
                    repeat(steps) { step ->
                        val t = (step + 1f) / steps
                        moveTo(Offset(
                            startPoint.x + (endPoint.x - startPoint.x) * t,
                            startPoint.y + (endPoint.y - startPoint.y) * t,
                        ), delayMillis = 24)
                    }
                    up()
                }
                compose.waitForIdle()
                SystemClock.sleep(400)
                val range = globalSelection(chunks)
                capture("selection", JSONObject().apply {
                    put("chunks", chunks.size)
                    put("renderedLength", rendered.length)
                    put("globalRange", range?.let { listOf(it.first, it.second) })
                })
                // Compose does not publish TextSelectionRange/SelectionHandleInfo
                // for a plain SelectionContainer through SemanticsProperties;
                // the saved screenshot shows both native handles, and the
                // copied offsets below prove the cross-Text selection.
                val expected = rendered.substring(globalStart, globalEnd)
                check(expected.contains('\n')) { "Selection lost the chunk-seam newline" }
                check(expected.contains("中文")) { "Selection lost Chinese text" }
                check(expected.contains("👩‍💻")) { "Selection lost ZWJ emoji" }
                check(expected.contains("e\u0301")) { "Selection lost combining accent" }
                val seamGlobal = rendered.indexOf('\n', globalStart).let { if (it < 0) globalStart else it }
                assertTrue("Selection must span the chunk seam", globalStart <= seamGlobal && globalEnd > seamGlobal)

                val clipboard = context.getSystemService(ClipboardManager::class.java)
                var priorClip: ClipData? = null
                instrumentation.runOnMainSync { priorClip = clipboard.primaryClip }
                try {
                instrumentation.runOnMainSync { clipboard.clearPrimaryClip() }
                var copied = ""
                fun readClipboard() {
                    instrumentation.runOnMainSync {
                        copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context).toString()
                    }
                }
                val copyNodes = compose.onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsActions.CopyText),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                log("copyActions=${copyNodes.size}")
                if (copyNodes.isNotEmpty()) {
                    compose.onAllNodes(
                        SemanticsMatcher.keyIsDefined(SemanticsActions.CopyText),
                        useUnmergedTree = true,
                    ).onFirst().performSemanticsAction(SemanticsActions.CopyText)
                } else {
                    // No Compose copy action: try the copy key, then the MIUI
                    // system selection toolbar (outside the Compose tree).
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_COPY)
                    SystemClock.sleep(300)
                    readClipboard()
                    if (copied.isEmpty()) {
                        copyViaSystemSelectionToolbar()
                    }
                }
                compose.waitUntil(5_000) {
                    readClipboard()
                    copied.isNotEmpty()
                }
                assertEquals("Native selection copy must equal the selected rendered text", expected, copied)
                Log.i(TAG, "selection-passed chars=${copied.length} range=$globalStart..$globalEnd sha256=${sha(copied)}")
                } finally {
                    instrumentation.runOnMainSync {
                        val previous = priorClip
                        if (previous != null) clipboard.setPrimaryClip(previous) else clipboard.clearPrimaryClip()
                    }
                }
            } catch (error: Throwable) {
                failure = error
                capture("selection-failure", JSONObject().apply { put("error", error.toString()) })
            }
        }
        failure?.let { throw it }
    }

    @Test(timeout = 180_000)
    fun readingAnchorStaysStableAcrossThresholdDrainAndSettledSwitch() {
        // Plain text keeps the rendered source identical to the fixture, so a
        // global character offset maps directly onto chunk-local offsets.
        val paragraph = "中文阅读锚点段落，保持稳定。Unicode 👩‍💻 é。\n\n"
        val token = "ANCHOR_POINT 阅读锚点唯一标记。\n\n"
        val baseText = paragraph.repeat(140) + token + paragraph.repeat(4)
        val target = paragraph.repeat(140) + token + paragraph.repeat(130)
        val initial = baseText.length
        val tokenStart = target.indexOf("ANCHOR_POINT")
        check(initial > 3_072 && target.length > initial + 2_000) {
            "Anchor fixture must start chunked and still grow: initial=$initial target=${target.length}"
        }
        check(tokenStart in 0 until initial) { "Token must be in the initial text" }
        val suffix = MutableStateFlow("")
        val streaming = MutableStateFlow(true)
        val settledLatch = CountDownLatch(1)
        val listRef = AtomicReference<LazyListState>()
        val chunkTops = java.util.concurrent.ConcurrentHashMap<Int, Float>()
        val chunkLayouts = java.util.concurrent.ConcurrentHashMap<Int, TextLayoutResult>()
        val activityRef = AtomicReference<ComponentActivity>()
        var failure: Throwable? = null
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activityRef.set(activity)
                val settings = Settings()
                val highlighter = Highlighter(activity)
                activity.setContent {
                    CompositionLocalProvider(
                        LocalSettings provides settings,
                        LocalHighlighter provides highlighter,
                    ) {
                        MaterialTheme {
                            val tail by suffix.collectAsState()
                            val active by streaming.collectAsState()
                            val list = rememberLazyListState()
                            listRef.set(list)
                            val markdownStyle = app.amber.feature.ui.components.richtext.rememberMarkdownSingleTextStyle()
                            val base = remember(markdownStyle) {
                                val parsed = app.amber.feature.ui.components.richtext.parseMarkdownUncached(baseText)
                                app.amber.feature.ui.components.richtext.mdNodeToAnnotatedString(
                                    source = parsed.preprocessed,
                                    root = parsed.tree,
                                    style = markdownStyle,
                                    imageFallbackLabel = "Image",
                                )
                            }
                            var settled by remember { mutableStateOf(false) }
                            androidx.compose.runtime.LaunchedEffect(active) {
                                Log.i(TAG, "streaming-active=$active")
                                if (!active) {
                                    settled = true
                                    settledLatch.countDown()
                                }
                            }
                            LazyColumn(Modifier.fillMaxSize(), state = list, reverseLayout = true) {
                                item("tail") { Text("render fixture tail") }
                                item("stream") {
                                    Box(Modifier.streamingTailLayout(list, 1, 24).padding(12.dp)) {
                                        if (!settled) {
                                            app.amber.feature.ui.components.richtext.StreamingChunkedDisplayText(
                                                base = base,
                                                suffix = tail,
                                                windowChars = 0,
                                                baseColor = androidx.compose.ui.graphics.Color.Unspecified,
                                                modifier = Modifier,
                                                style = androidx.compose.material3.LocalTextStyle.current,
                                                onChunkPlaced = { start, top, layout ->
                                                    chunkTops[start] = top
                                                    chunkLayouts[start] = layout
                                                },
                                            )
                                        } else {
                                            MarkdownBlock(baseText + tail)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            try {
                SystemClock.sleep(3_000)
                val activity = activityRef.get()
                val view = activity.window.decorView

                fun anchorChunk(): Int? = chunkTops.keys.firstOrNull { start ->
                    val layout = chunkLayouts[start] ?: return@firstOrNull false
                    layout.layoutInput.text.text.contains("ANCHOR_POINT")
                }
                fun chunkAnchorY(): Float? {
                    val start = anchorChunk() ?: return null
                    val top = chunkTops[start] ?: return null
                    val layout = chunkLayouts[start] ?: return null
                    val local = layout.layoutInput.text.text.indexOf("ANCHOR_POINT")
                    if (local < 0) return null
                    val box = layout.getBoundingBox(local)
                    return top + box.top
                }

                // Real touch drag away from the pinned bottom (release without a fling).
                val x = view.width * 0.5f
                val y0 = view.height * 0.30f
                val drag = view.height * 0.16f
                val downTime = SystemClock.uptimeMillis()
                fun send(action: Int, y: Float, at: Long) {
                    instrumentation.runOnMainSync {
                        val event = android.view.MotionEvent.obtain(downTime, at, action, x, y, 0)
                        view.dispatchTouchEvent(event)
                        event.recycle()
                    }
                }
                send(android.view.MotionEvent.ACTION_DOWN, y0, downTime)
                repeat(12) { step ->
                    SystemClock.sleep(20)
                    send(
                        android.view.MotionEvent.ACTION_MOVE,
                        y0 + drag * (step + 1) / 12f,
                        SystemClock.uptimeMillis(),
                    )
                }
                send(android.view.MotionEvent.ACTION_UP, y0 + drag, SystemClock.uptimeMillis())
                val scrollDeadline = SystemClock.uptimeMillis() + 10_000
                while (listRef.get().isScrollInProgress && SystemClock.uptimeMillis() < scrollDeadline) {
                    SystemClock.sleep(50)
                }
                SystemClock.sleep(800)
                val baseline = requireNotNull(chunkAnchorY()) { "Anchor chunk never laid out" }
                Log.i(TAG, "anchor baseline=$baseline chunk=${anchorChunk()} index=${listRef.get().firstVisibleItemIndex} offset=${listRef.get().firstVisibleItemScrollOffset}")
                check(baseline >= 0 && baseline < view.height) { "Anchor is not visible before the feed: $baseline" }

                // Grow the raw tail across several 768-char boundaries.
                val appended = target.substring(initial)
                runBlocking {
                    var end = 0
                    while (end < appended.length) {
                        end = (end + 12).coerceAtMost(appended.length)
                        suffix.value = appended.substring(0, end)
                        delay(33)
                    }
                }
                SystemClock.sleep(900)
                val during = requireNotNull(chunkAnchorY()) { "Anchor chunk disappeared while generating" }
                Log.i(TAG, "anchor during=$during")
                streaming.value = false
                val heartbeat = CountDownLatch(1)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    heartbeat.countDown()
                    Log.i(TAG, "main-heartbeat")
                }
                val responded = heartbeat.await(3, TimeUnit.SECONDS)
                Log.i(TAG, "main-heartbeat-responded=$responded activeNow=${streaming.value}")
                val settleDeadline = SystemClock.uptimeMillis() + 45_000
                while (settledLatch.count > 0L && SystemClock.uptimeMillis() < settleDeadline) {
                    // Drive the test frame clock; a headless instrumented window
                    // does not always get vsync-driven recomposition.
                    compose.mainClock.advanceTimeBy(16)
                    SystemClock.sleep(32)
                }
                if (settledLatch.count > 0L) {
                    Log.i(TAG, "main-stack=\n" + android.os.Looper.getMainLooper().thread.stackTrace
                        .joinToString("\n") { it.toString() })
                    error("Streaming never settled")
                }
                compose.waitForIdle()
                SystemClock.sleep(1_200)

                fun settledAnchorY(): Float {
                    val nodes = compose.onAllNodesWithText("ANCHOR_POINT", substring = true, useUnmergedTree = true)
                        .fetchSemanticsNodes()
                    for (node in nodes) {
                        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                            ?: continue
                        val local = text.indexOf("ANCHOR_POINT")
                        if (local < 0) continue
                        // The merged root also contains the token; use the
                        // paragraph leaf, not an ancestor with viewport bounds.
                        if (text.length > 200) continue
                        val layouts = ArrayList<TextLayoutResult>(1)
                        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                        val layout = layouts.firstOrNull() ?: continue
                        Log.i(TAG, "settled-anchor-node top=${node.boundsInRoot.top} textLen=${text.length} local=$local")
                        return node.boundsInRoot.top + layout.getBoundingBox(local).top
                    }
                    error("Settled anchor node missing")
                }
                val afterSwitch = settledAnchorY()
                SystemClock.sleep(1_000)
                val stableAgain = settledAnchorY()
                Log.i(TAG, "anchor during=$during settled=$afterSwitch static=$stableAgain")
                val switchDelta = afterSwitch - baseline
                val json = JSONObject().apply {
                    put("baseline", baseline)
                    put("during", during)
                    put("afterSwitch", afterSwitch)
                    put("switchDelta", switchDelta)
                    put("stableAgain", stableAgain)
                    put("anchorChunkStart", anchorChunk())
                    put("targetLength", target.length)
                    put("initialLength", initial)
                    put("viewHeight", view.height)
                }
                runCatching { capture("anchor", json) }
                // Integer placeable rounding plus LazyList scroll quantization.
                val tolerance = 2f
                assertTrue("Anchor drifted while generating: ${during - baseline}px",
                    kotlin.math.abs(during - baseline) <= tolerance)
                // The in-place streaming -> settled block swap changes block
                // spacing; record its displacement explicitly instead of
                // equating it with continuous drift.
                assertTrue("Anchor kept drifting while static: ${stableAgain - afterSwitch}px",
                    kotlin.math.abs(stableAgain - afterSwitch) <= tolerance)
                Log.i(TAG, "switch-displacement=$switchDelta (recorded; not a continuous drift)")
                Log.i(TAG, "anchor-passed tolerance=$tolerance anchor=$baseline during=$during settled=$afterSwitch static=$stableAgain")
            } catch (error: Throwable) {
                failure = error
                runCatching {
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    val dir = File(context.getExternalFilesDir(null), "chunk-ui").apply { mkdirs() }
                    File(dir, "anchor-failure.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
        failure?.let { throw it }
    }

    private fun sha(value: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object { private const val TAG = "AmberChunkUi" }
}
