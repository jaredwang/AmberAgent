package app.amber.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.amber.core.automation.DefaultScreenOcrEngine
import app.amber.core.automation.ScreenCaptureManager
import app.amber.core.automation.ScreenOcrCapabilities
import app.amber.core.automation.ScreenOcrMode
import app.amber.core.automation.ScreenOcrOutcome
import app.amber.core.automation.ScreenOcrSource
import app.amber.core.automation.getActiveAccessibilityController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production OCR path on an isolated fixture Activity:
 * foreground pixels → [DefaultScreenOcrEngine] `(context, ScreenCaptureManager(context))` →
 * [app.amber.core.automation.AmberAccessibilityService.takeScreenshotBitmap] → owned ML Kit.
 *
 * This is not the offscreen `view.draw` path in [ScreenOcrDeviceTest].
 */
@RunWith(AndroidJUnit4::class)
class ScreenOcrProductionCaptureDeviceTest {
    @Test
    fun canvasForegroundFreshScreenshotIsBitmapOcrMappedToUniqueTreeTarget() {
        recognizeForeground(ScreenOcrFixtureActivity.MODE_CANVAS, "production-canvas")
    }

    @Test
    fun webViewForegroundFreshScreenshotIsBitmapOcrMappedToUniqueTreeTarget() {
        recognizeForeground(ScreenOcrFixtureActivity.MODE_WEBVIEW, "production-webview")
    }

    private fun recognizeForeground(mode: String, evidenceName: String) {
        AmberS9DeviceSupport.assumeUnlocked()
        AmberS9DeviceSupport.uiAutomation()
        AmberS9DeviceSupport.withAmberAccessibility {
            val start = AmberS9DeviceSupport.launchTestPackageActivity(
                "app.amber.agent.ScreenOcrFixtureActivity",
                "--es mode $mode",
            )
            runBlocking {
                withTimeout(15_000) {
                    while (true) {
                        val current = withContext(Dispatchers.Main) {
                            getActiveAccessibilityController()?.captureScreenSnapshot()
                        }
                        if (current?.packageName == AmberS9DeviceSupport.testPackage) return@withTimeout
                        delay(250)
                    }
                }
            }
            if (mode == ScreenOcrFixtureActivity.MODE_WEBVIEW) Thread.sleep(1_500)
            val controller = checkNotNull(getActiveAccessibilityController())
            val snapshot = runBlocking {
                withContext(Dispatchers.Main) { controller.captureScreenSnapshot()!! }
            }
            val dump = runBlocking { withContext(Dispatchers.Main) { controller.dumpUiTree(200) } }
            val axDump = AmberS9DeviceSupport.dumpAxText(AmberS9DeviceSupport.uiAutomation().rootInActiveWindow)
            AmberS9DeviceSupport.writeEvidence("$evidenceName-start.txt", start)
            AmberS9DeviceSupport.writeEvidence("$evidenceName-ax.txt", axDump)
            AmberS9DeviceSupport.writeEvidence("$evidenceName-tree.txt", dump)
            assertFalse("AX dump must not supply Chinese fixture text", dump.contains(ScreenOcrFixtureActivity.CHINESE) || axDump.contains(ScreenOcrFixtureActivity.CHINESE))
            assertFalse("AX dump must not supply Latin fixture text", dump.contains(ScreenOcrFixtureActivity.LATIN) || axDump.contains(ScreenOcrFixtureActivity.LATIN))
            assertFalse("AX dump must not supply clickable fixture text", dump.contains(ScreenOcrFixtureActivity.CLICKABLE) || axDump.contains(ScreenOcrFixtureActivity.CLICKABLE))

            val engine = DefaultScreenOcrEngine(
                AmberS9DeviceSupport.targetContext,
                ScreenCaptureManager(AmberS9DeviceSupport.targetContext),
            )
            try {
                val observation = runBlocking {
                    withTimeout(25_000) {
                        engine.observe(
                            snapshot = snapshot,
                            uiTreeDump = dump,
                            mode = ScreenOcrMode.ON,
                        )
                    }
                }
                val joined = observation.blocks
                    .filter { it.source == ScreenOcrSource.BITMAP_OCR }
                    .joinToString("\n") { it.text }
                AmberS9DeviceSupport.writeEvidence(
                    "$evidenceName-ocr.txt",
                    buildString {
                        appendLine("source=${observation.source}")
                        appendLine("outcome=${observation.outcome}")
                        appendLine("screenshotSource=${observation.screenshotSource}")
                        appendLine("recognizer=${observation.recognizer}")
                        appendLine("diagnostics=${observation.diagnostics}")
                        appendLine("snapshotPkg=${snapshot.packageName}")
                        appendLine("snapshotNodes=${snapshot.nodes.size}")
                        appendLine("blocks=${observation.blocks.size}")
                        observation.blocks.forEach { block ->
                            appendLine("${block.source}\t${block.left},${block.top},${block.right},${block.bottom}\t${block.text}")
                        }
                        appendLine("joined=$joined")
                    },
                )
                AmberS9DeviceSupport.takeUiScreenshot("$evidenceName-screen.png")
                assertEquals(ScreenOcrSource.BITMAP_OCR, observation.source)
                assertEquals(ScreenOcrOutcome.RECOGNIZED, observation.outcome)
                assertEquals("accessibility_screenshot", observation.screenshotSource)
                assertEquals(ScreenOcrCapabilities.RECOGNIZER_ID, observation.recognizer)
                assertTrue("Chinese missing in $joined", joined.contains("琥珀") && joined.contains("验证"))
                assertTrue("Latin missing in $joined", joined.contains("OCR-LATIN-MARK") || joined.contains("LATIN"))
                val clickable = observation.blocks.firstOrNull { block ->
                    block.text.contains("立即") || block.text.contains("查看") || block.text.contains("详情")
                }
                assertTrue("clickable region missing in $joined", clickable != null)
                val fresh = runBlocking {
                    withContext(Dispatchers.Main) { controller.captureScreenSnapshot() }
                }
                checkNotNull(fresh)
                assertEquals(AmberS9DeviceSupport.testPackage, fresh.packageName)
                val target = AmberS9DeviceSupport.uniqueTreeTarget(fresh, clickable!!)
                AmberS9DeviceSupport.writeEvidence(
                    "$evidenceName-target.txt",
                    "ref=${target.ref} label=${target.label} viewId=${target.viewId} " +
                        "clickable=${target.clickable} bounds=${target.left},${target.top},${target.right},${target.bottom}\n",
                )
            } finally {
                engine.close()
            }
        }
    }
}
