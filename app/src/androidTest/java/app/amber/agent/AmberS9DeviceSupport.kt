package app.amber.agent

import android.Manifest
import android.app.KeyguardManager
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.amber.core.automation.ScreenNode
import app.amber.core.automation.ScreenOcrBlock
import app.amber.core.automation.ScreenSnapshot
import app.amber.core.automation.getActiveAccessibilityController
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue

/**
 * Isolated S9 device helpers. No production bypass, no PIN handling, no user-app
 * automation. Accessibility mutations always restore the exact original
 * `enabled_accessibility_services` string.
 */
object AmberS9DeviceSupport {
    const val AMBER_ACCESSIBILITY = "app.amber.core.automation.AmberAccessibilityService"

    private val captured = AtomicBoolean(false)
    @Volatile private var originalEnabledServices: String = ""
    @Volatile private var identityAdopted: Boolean = false

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val targetContext get() = instrumentation.targetContext
    val testPackage get() = instrumentation.context.packageName
    val targetPackage get() = targetContext.packageName

    fun amberComponent(): String = "$targetPackage/$AMBER_ACCESSIBILITY"

    fun uiAutomation(): UiAutomation =
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    fun assumeUnlocked() {
        val keyguard = targetContext.getSystemService(KeyguardManager::class.java)
        assumeFalse(
            "device is keyguard-locked; skip until the owner unlocks and keeps the screen on",
            keyguard.isKeyguardLocked || keyguard.isDeviceLocked,
        )
    }

    fun assumeS9FixtureApplication() {
        assumeTrue(
            "requires ChatDeviceFixtureRunner so ChatDeviceFixtureApplication starts isolated Koin",
            targetContext.applicationContext is ChatDeviceFixtureApplication,
        )
    }

    fun isKeyguardLocked(): Boolean {
        val keyguard = targetContext.getSystemService(KeyguardManager::class.java)
        return keyguard.isKeyguardLocked || keyguard.isDeviceLocked
    }

    fun shell(command: String): String {
        val fd = uiAutomation().executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    fun evidenceDir(): File {
        val dir = targetContext.getExternalFilesDir("amber-s9")
            ?: File(targetContext.filesDir, "amber-s9")
        if (!dir.exists()) check(dir.mkdirs()) { "cannot create evidence dir $dir" }
        return dir
    }

    fun writeEvidence(name: String, text: String): File {
        val file = File(evidenceDir(), name)
        file.writeText(text)
        println("AmberS9 evidence=${file.absolutePath}")
        return file
    }

    fun savePng(bitmap: Bitmap, name: String): File {
        val file = File(evidenceDir(), name)
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        println("AmberS9 png=${file.absolutePath}")
        return file
    }

    fun takeUiScreenshot(name: String): File? {
        val bitmap = runCatching { uiAutomation().takeScreenshot() }.getOrNull() ?: return null
        return try {
            savePng(bitmap, name)
        } finally {
            bitmap.recycle()
        }
    }

    fun launchTestPackageActivity(componentClass: String, extras: String = ""): String {
        val cmd = buildString {
            append("am start -W -n $testPackage/$componentClass")
            append(" --activity-clear-task --activity-new-task")
            if (extras.isNotBlank()) append(' ').append(extras)
        }
        return shell(cmd)
    }

    fun launchTargetComposeHost(): ActivityScenario<ComponentActivity> {
        assumeUnlocked()
        assumeS9FixtureApplication()
        shell(
            "am start -W -n $targetPackage/androidx.activity.ComponentActivity " +
                "--activity-clear-task --activity-new-task",
        )
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        return scenario
    }

    fun <T> withZeroAnimations(block: () -> T): T {
        val keys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        val original = keys.associateWith { key -> shell("settings get global $key").trim() }
        return try {
            keys.forEach { key -> shell("settings put global $key 0") }
            block()
        } finally {
            original.forEach { (key, value) ->
                if (value.isBlank() || value == "null") {
                    shell("settings delete global $key")
                } else {
                    shell("settings put global $key $value")
                }
            }
        }
    }

    fun <T> withAmberAccessibility(block: () -> T): T {
        enableAmberAccessibility()
        try {
            return block()
        } finally {
            restoreAmberAccessibility()
        }
    }

    suspend fun <T> withAmberAccessibilitySuspending(block: suspend () -> T): T {
        enableAmberAccessibility()
        try {
            return block()
        } finally {
            restoreAmberAccessibility()
        }
    }

    fun enableAmberAccessibility() {
        val automation = uiAutomation()
        val resolver = targetContext.contentResolver
        val key = android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val component = amberComponent()
        if (!identityAdopted) {
            automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
            identityAdopted = true
        }
        if (captured.compareAndSet(false, true)) {
            originalEnabledServices = android.provider.Settings.Secure.getString(resolver, key).orEmpty()
        }
        val current = android.provider.Settings.Secure.getString(resolver, key).orEmpty()
        val items = current.split(':').filter { it.isNotBlank() }
        if (component !in items) {
            writeEnabledServices((items + component).joinToString(":"))
        }
        if (getActiveAccessibilityController() == null) {
            val withAmber = readEnabledItems()
            writeEnabledServices(withAmber.filter { it != component }.joinToString(":"))
            Thread.sleep(250)
            writeEnabledServices((readEnabledItems().filter { it != component } + component).joinToString(":"))
        }
        runBlocking {
            withTimeout(20_000) {
                while (getActiveAccessibilityController() == null) delay(250)
            }
        }
    }

    fun restoreAmberAccessibility() {
        if (!captured.get()) return
        try {
            writeEnabledServices(originalEnabledServices)
            val restored = android.provider.Settings.Secure.getString(
                targetContext.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            check(restored == originalEnabledServices) {
                "failed to restore enabled_accessibility_services: got '$restored' expected '$originalEnabledServices'"
            }
        } finally {
            captured.set(false)
            originalEnabledServices = ""
            if (identityAdopted) {
                runCatching { uiAutomation().dropShellPermissionIdentity() }
                identityAdopted = false
            }
        }
    }

    fun dumpAxText(node: AccessibilityNodeInfo?): String {
        if (node == null) return "(no-root)"
        val out = StringBuilder()
        fun walk(current: AccessibilityNodeInfo, depth: Int) {
            val text = listOfNotNull(
                current.text?.toString(),
                current.contentDescription?.toString(),
                current.hintText?.toString(),
            ).filter { it.isNotBlank() }
            if (text.isNotEmpty()) {
                out.append("  ".repeat(depth)).append(current.className).append(' ')
                    .append(text.joinToString(" | ")).append('\n')
            }
            for (index in 0 until current.childCount) {
                val child = current.getChild(index) ?: continue
                walk(child, depth + 1)
                child.recycle()
            }
        }
        walk(node, 0)
        return out.toString()
    }

    fun uniqueTreeTarget(snapshot: ScreenSnapshot, block: ScreenOcrBlock): ScreenNode {
        val cx = (block.left + block.right) / 2
        val cy = (block.top + block.bottom) / 2
        val containing = snapshot.nodes.filter { node ->
            cx >= node.left && cx < node.right && cy >= node.top && cy < node.bottom
        }
        check(containing.isNotEmpty()) {
            "OCR block '${block.text}' @${block.left},${block.top}-${block.right},${block.bottom} " +
                "is not inside any current-tree node. pkg=${snapshot.packageName} nodes=${snapshot.nodes.size}"
        }
        val pool = containing.filter { it.clickable }.ifEmpty { containing }
        val smallestArea = pool.minOf { node ->
            (node.right - node.left).coerceAtLeast(0) * (node.bottom - node.top).coerceAtLeast(0)
        }
        val ties = pool.filter { node ->
            (node.right - node.left).coerceAtLeast(0) * (node.bottom - node.top).coerceAtLeast(0) == smallestArea
        }
        check(ties.size == 1) {
            "OCR clickable region maps to ${ties.size} equal-area tree targets: " +
                ties.joinToString { "${it.ref}/${it.label}/${it.viewId}" }
        }
        return ties.single()
    }

    private fun readEnabledItems(): List<String> {
        val raw = android.provider.Settings.Secure.getString(
            targetContext.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return raw.split(':').filter { it.isNotBlank() }
    }

    private fun writeEnabledServices(value: String) {
        val resolver = targetContext.contentResolver
        val key = android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        android.provider.Settings.Secure.putString(resolver, key, value)
        val viaSettings = android.provider.Settings.Secure.getString(resolver, key).orEmpty()
        if (viaSettings != value) {
            // executeShellCommand is not a shell: do not wrap the value in quotes.
            if (value.isEmpty()) {
                shell("settings delete secure enabled_accessibility_services")
            } else {
                shell("settings put secure enabled_accessibility_services $value")
            }
        }
    }
}
