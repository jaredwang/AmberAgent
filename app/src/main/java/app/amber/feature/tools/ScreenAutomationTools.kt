package app.amber.feature.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import app.amber.ai.core.InputSchema
import app.amber.ai.core.Tool
import app.amber.ai.ui.UIMessagePart
import app.amber.feature.runtime.AgentToolActivityStore
import app.amber.core.automation.AccessibilityController
import app.amber.core.automation.getActiveAccessibilityController
import app.amber.core.automation.ScreenCaptureManager
import app.amber.core.automation.ScreenNode
import app.amber.core.jev.JevScreenGoalRunner
import app.amber.core.settings.prefs.SettingsAggregator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

class ScreenAutomationTools(
    private val context: Context,
    private val screenCaptureManager: ScreenCaptureManager,
    private val activityStore: AgentToolActivityStore,
    private val jevScreenGoalRunner: JevScreenGoalRunner? = null,
    private val settingsStore: SettingsAggregator? = null,
) {
    private val screenOwner = Mutex()

    /**
     * 已由 screen_stop 主动结束的 run。按 run 键控：本工具是 Koin 单例，
     * 进程级布尔会把一次 stop 变成"App 级永久关闭屏幕自控"。
     */
    private val stoppedRuns: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun runKey(runId: String?): String? = runId?.takeIf { it.isNotBlank() }

    private fun isStopped(runId: String?): Boolean = runKey(runId)?.let { it in stoppedRuns } ?: false

    fun getTools(runId: String? = null): List<Tool> = listOf(
        screenStatusTool(runId),
        screenAppsTool,
        screenAddAppTool(runId),
        screenRemoveAppTool,
        screenStopTool(runId),
        screenGoalTool(runId),
        screenClickTool,
        screenLongClickTool,
        screenSwipeTool,
        screenInputTextTool,
        screenBackTool,
        screenHomeTool,
        screenOpenAppTool,
        screenOpenUrlTool,
        screenReadUiTool,
        screenFindTextTool,
        screenTapTextTool,
        screenWaitForTextTool,
        screenScrollUntilTool,
        screenRunFlowTool(runId),
        screenCollectListTool,
        screenScreenshotTool,
        vlmTaskTool,
    )

    private fun screenStatusTool(runId: String?) = Tool(
        name = "screen_status",
        description = "Report AmberAgent screen-control readiness: whether the Accessibility " +
            "service is enabled, the persisted app allow-list that screen_run_goal may operate in, " +
            "whether another screen operation is currently running, and whether this run was " +
            "ended by screen_stop. Read-only: never starts or re-pairs the control service, never " +
            "launches an app. When the allow-list is empty, call screen_apps then screen_add_app first.",
        parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
        execute = {
            val allowed = allowedPackages()
            val serviceAvailable = getActiveAccessibilityController() != null
            textJson {
                put("accessibility_enabled", serviceAvailable)
                put("session_stopped", isStopped(runId))
                put("stop_scope", if (runKey(runId) != null) "run" else "unbound")
                put("operation_active", screenOwner.isLocked)
                put("run_bound", runId != null)
                put("allowed_packages", buildJsonArray { allowed.forEach { add(it) } })
                put("scope", if (allowed.isEmpty()) "no_scope" else "scoped")
                if (!serviceAvailable) {
                    put("remedy", "enable AmberAgent Accessibility in system accessibility settings")
                } else if (allowed.isEmpty()) {
                    put("remedy", "call screen_apps then screen_add_app with the exact package name")
                }
            }
        },
    )

    private val screenAppsTool = Tool(
        name = "screen_apps",
        description = "List launchable apps installed on this device with their exact package names, " +
            "from the host's own package index. Read-only: does not launch anything and does not " +
            "start the control service. Returns items plus total, has_more and the current " +
            "allow-list; use the exact package name with screen_add_app. Names may be English; " +
            "page with offset when the query misses, do not guess a package name.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", stringProp("Optional name or package substring; empty lists apps."))
                    put("offset", integerProp("Offset into the filtered list. Defaults to 0."))
                    put("limit", integerProp("Page size 1..100. Defaults to 50."))
                },
            )
        },
        execute = { input ->
            val query = input.string("query").orEmpty().trim()
            val offset = (input.int("offset") ?: 0).coerceAtLeast(0)
            val limit = (input.int("limit") ?: 50).coerceIn(1, 100)
            val allowed = allowedPackages()
            val matches = installedLaunchableApps()
                .filter { (pkg, label) ->
                    query.isEmpty() || pkg.contains(query, ignoreCase = true) ||
                        label.contains(query, ignoreCase = true)
                }
            val page = matches.drop(offset).take(limit)
            textJson {
                put("items", buildJsonArray {
                    page.forEach { (pkg, label) ->
                        add(buildJsonObject {
                            put("package", pkg)
                            put("label", label)
                            put("selected", pkg in allowed)
                        })
                    }
                })
                put("total", matches.size)
                put("offset", offset)
                put("has_more", offset + page.size < matches.size)
                put("allowed_packages", buildJsonArray { allowed.forEach { add(it) } })
            }
        },
    )

    private fun screenAddAppTool(runId: String?) = Tool(
        name = "screen_add_app",
        description = "Add one exact package name (from screen_apps) to the persisted app allow-list " +
            "that screen_run_goal is allowed to operate in. Takes effect immediately for the " +
            "current run and is kept for later runs. Does not open the app and does not click or " +
            "type; add the app first, then use screen_run_goal or screen_open_app. Only add apps " +
            "the user actually asked to control.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("package_name", stringProp("Exact package name returned by screen_apps."))
                },
                required = listOf("package_name"),
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            check(!isStopped(runId)) {
                "Screen control was ended by screen_stop for this run; the allow-list is frozen."
            }
            val store = settingsStore ?: error("screen allow-list unavailable")
            val packageName = input.requiredString("package_name").trim()
            require(packageName.isNotBlank()) { "package_name is required" }
            require(installedLaunchableApps().any { it.first == packageName }) {
                "Unknown or non-launchable package: $packageName. Use screen_apps to find the exact id."
            }
            store.update { settings ->
                val current = settings.agentRuntime.screenAutomation
                if (current.allows(packageName)) settings else settings.copy(
                    agentRuntime = settings.agentRuntime.copy(
                        screenAutomation = current.copy(
                            allowedPackages = (current.allowedPackages + packageName).distinct(),
                        ),
                    ),
                )
            }
            val allowed = allowedPackages()
            textJson {
                put("package_name", packageName)
                put("allowed_packages", buildJsonArray { allowed.forEach { add(it) } })
                put("run_bound", runId != null)
            }
        },
    )

    /**
     * 与 screen_add_app 对称的撤销入口。只收窄授权，不发放任何东西，因此不需要审批；
     * 撤销后同 run 的 screen_run_goal 立即拒绝该应用。
     */
    private val screenRemoveAppTool = Tool(
        name = "screen_remove_app",
        description = "Remove one package from the persisted app allow-list, narrowing what " +
            "screen_run_goal may operate in. Takes effect immediately. Does not close the app, " +
            "does not stop an action already in flight, and grants nothing; use it when the user " +
            "asks to revoke an app they previously authorized.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("package_name", stringProp("Exact package name currently in the allow-list."))
                },
                required = listOf("package_name"),
            )
        },
        execute = { input ->
            val store = settingsStore ?: error("screen allow-list unavailable")
            val packageName = input.requiredString("package_name").trim()
            require(packageName.isNotBlank()) { "package_name is required" }
            val before = allowedPackages()
            if (packageName !in before) {
                textJson {
                    put("removed", false)
                    put("reason", "not_in_allow_list")
                    put("allowed_packages", buildJsonArray { before.forEach { add(it) } })
                }
            } else {
                store.update { settings ->
                    val scope = settings.agentRuntime.screenAutomation
                    settings.copy(
                        agentRuntime = settings.agentRuntime.copy(
                            screenAutomation = scope.copy(allowedPackages = scope.allowedPackages - packageName),
                        ),
                    )
                }
                val after = allowedPackages()
                textJson {
                    put("removed", true)
                    put("package_name", packageName)
                    put("allowed_packages", buildJsonArray { after.forEach { add(it) } })
                }
            }
        },
    )

    private fun screenStopTool(runId: String?) = Tool(
        name = "screen_stop",
        description = "End screen control for this run: no later screen_run_goal call and no later allow-list " +
            "change from this run is dispatched. Individual low-level actions still need their own " +
            "approval each time, so they cannot continue silently either. Cannot undo clicks, " +
            "swipes or text already sent to the system. Safe to call twice; it does not interrupt " +
            "an action that is already in flight.",
        parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
        execute = {
            val key = runKey(runId)
            if (key == null) {
                // 没有宿主拥有的 run 就无法界定"本次"，拒绝落成进程级开关。
                textJson {
                    put("stopped", false)
                    put("reason", "no_run_scope")
                    put("note", "screen_stop ends one host-owned run; it needs a run-bound tool set")
                }
            } else {
                val wasStopped = key in stoppedRuns
                stoppedRuns += key
                textJson {
                    put("stopped", true)
                    put("already_stopped", wasStopped)
                    // 不谎称已中断：在途动作由 owner 锁持有，需等它自然结束。
                    put("operation_active", screenOwner.isLocked)
                }
            }
        },
    )

    private fun allowedPackages(): List<String> =
        settingsStore?.settingsFlow?.value?.agentRuntime?.screenAutomation?.allowedPackages.orEmpty()

    /** launcher 可见的应用：走 queryIntentActivities，与 screen_open_app 同一可见性面。 */
    private fun installedLaunchableApps(): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                pkg to info.loadLabel(pm).toString()
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    private fun screenGoalTool(runId: String?) = Tool(
        name = "screen_run_goal",
        description = "Use Jev to accelerate Android Accessibility: open the specified app, then execute a bounded goal internally with fresh UI-node validation. Prefer this over repeated screen_read_ui/click/swipe for read-only navigation, locating content, scrolling, or filling supplied search text. Jev is a decision service, not JavaScript. Requires Jev screen automation and screen-content consent. Returns engine, jev_decisions, actions_dispatched, steps and visible_text; report those facts, never infer Jev usage from tool names. No sending, purchases, account changes or permission grants. On handback use the returned screen evidence and low-level tools. Each call requires approval.",
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("package_name", stringProp("Android package to open and stay within, e.g. tv.danmaku.bili."))
                put("goal", stringProp("One concrete screen goal with a visible completion condition; at most 1000 characters."))
                put("texts", buildJsonObject {
                    put("type", "array")
                    put("items", stringProp("Exact permitted search/draft text, at most 300 characters."))
                    put("description", "Up to 8 text candidates. Without these, typing is unavailable.")
                })
                put("max_steps", integerProp("Jev decision limit, default 15, hard maximum 30."))
                put("max_duration_ms", integerProp("Deadline, default 180000ms, hard maximum 180000ms."))
                put("dry_run", buildJsonObject { put("type", "boolean"); put("description", "Only inspect the currently open target app and judge; do not launch or execute actions.") })
            }, required = listOf("package_name", "goal"))
        },
        needsApproval = true,
        allowsAutoApproval = false,
        mandatoryApproval = true,
        execute = { input ->
            trackScreenTool(
                "screen_run_goal", "Jev 无障碍目标执行", input,
                runtime = "Jev + Android Accessibility", runId = runId,
            ) {
                val runner = jevScreenGoalRunner ?: error("Jev screen runner unavailable")
                val ownerRun = runId?.takeIf { it.isNotBlank() } ?: error("screen_run_goal requires a host-owned run")
                val packageName = input.requiredString("package_name")
                val allowed = allowedPackages()
                if (packageName !in allowed) {
                    // 多步自控只进用户显式授权过的应用；未授权时引导先查再授权，而不是回退到别的方式。
                    return@trackScreenTool textJson {
                        put("status", "no_scope")
                        put("package_name", packageName)
                        put("reason", if (allowed.isEmpty()) "no_app_scope" else "package_not_allowed")
                        put("allowed_packages", buildJsonArray { allowed.forEach { add(it) } })
                        put("remedy", "call screen_apps then screen_add_app with the exact package name")
                    }
                }
                val goal = input.requiredString("goal")
                val texts = (input.jsonObject["texts"] as? kotlinx.serialization.json.JsonArray)
                    ?.map { it.jsonPrimitive.content } ?: emptyList()
                require(goal.isNotBlank() && goal.length <= 1000 && texts.size <= 8 && texts.all { it.length <= 300 })
                val dryRun = input.string("dry_run") == "true"
                val service = requireService()
                // The approval UI can have brought Amber to the front. Restore only the explicitly named app.
                val alreadyOpen = if (!dryRun && runner.canExecute()) withContext(Dispatchers.Main.immediate) {
                    service.captureScreenSnapshot()?.packageName == packageName
                } else false
                if (!dryRun && runner.canExecute() && !alreadyOpen) {
                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                        ?: error("No launch intent for package: $packageName")
                    withContext(Dispatchers.Main.immediate) {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    delay(600)
                }
                val outcome = runner.run(
                    controller = service, packageName = packageName, goal = goal, texts = texts,
                    runKey = ownerRun, maxSteps = input.int("max_steps") ?: 15,
                    maxDurationMs = input.long("max_duration_ms") ?: 180_000, dryRun = dryRun,
                )
                listOf(UIMessagePart.Text(outcome.toString()))
            }
        },
    )

    private val screenClickTool = Tool(
        name = "screen_click",
        description = "Tap screen coordinates using AmberAgent Accessibility after user approval. " +
            "Optional verify_text polls the UI tree post-action so the tap effect is confirmed, " +
            "not just injected.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("x", numberProp("X coordinate."))
                    put("y", numberProp("Y coordinate."))
                    put("verify_text", stringProp("Optional text expected to appear after the tap; result reports verify_text_found."))
                    put("verify_timeout_ms", integerProp("Post-action verify poll timeout. Defaults to 2000."))
                    put("expectation", expectationProp())
                },
                required = listOf("x", "y")
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_click", "点击屏幕", input) {
                val service = requireService()
                val point = requireDisplayPoint(
                    x = input.requiredFloat("x"),
                    y = input.requiredFloat("y"),
                    label = "screen_click"
                )
                val targetVisibleBefore = wasAbsenceTargetVisible(service, input)
                val ok = service.tap(point.x, point.y)
                verifiedResult(service, input, ok, targetVisibleBefore)
            }
        }
    )

    private val screenLongClickTool = Tool(
        name = "screen_long_click",
        description = "Long-press screen coordinates using AmberAgent Accessibility after user approval.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("x", numberProp("X coordinate."))
                    put("y", numberProp("Y coordinate."))
                    put("duration_ms", integerProp("Press duration in milliseconds. Defaults to 600."))
                    put("expectation", expectationProp())
                },
                required = listOf("x", "y")
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_long_click", "长按屏幕", input) {
                val service = requireService()
                val point = requireDisplayPoint(
                    x = input.requiredFloat("x"),
                    y = input.requiredFloat("y"),
                    label = "screen_long_click"
                )
                val targetVisibleBefore = wasAbsenceTargetVisible(service, input)
                val ok = service.longPress(
                    x = point.x,
                    y = point.y,
                    durationMillis = input.long("duration_ms") ?: 600L
                )
                verifiedResult(service, input, ok, targetVisibleBefore)
            }
        }
    )

    private val screenSwipeTool = Tool(
        name = "screen_swipe",
        description = "Swipe between screen coordinates using AmberAgent Accessibility after user approval.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("from_x", numberProp("Start X coordinate."))
                    put("from_y", numberProp("Start Y coordinate."))
                    put("to_x", numberProp("End X coordinate."))
                    put("to_y", numberProp("End Y coordinate."))
                    put("duration_ms", integerProp("Swipe duration in milliseconds. Defaults to 350."))
                    put("verify_text", stringProp("Optional text expected to appear after the swipe; result reports verify_text_found."))
                    put("verify_timeout_ms", integerProp("Post-action verify poll timeout. Defaults to 2000."))
                },
                required = listOf("from_x", "from_y", "to_x", "to_y")
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_swipe", "滑动屏幕", input) {
                val service = requireService()
                val from = requireDisplayPoint(
                    x = input.requiredFloat("from_x"),
                    y = input.requiredFloat("from_y"),
                    label = "screen_swipe.from"
                )
                val to = requireDisplayPoint(
                    x = input.requiredFloat("to_x"),
                    y = input.requiredFloat("to_y"),
                    label = "screen_swipe.to"
                )
                val ok = service.swipe(
                    fromX = from.x,
                    fromY = from.y,
                    toX = to.x,
                    toY = to.y,
                    durationMillis = input.long("duration_ms") ?: 350L
                )
                verifiedResult(service, input, ok)
            }
        }
    )

    private val screenInputTextTool = Tool(
        name = "screen_input_text",
        description = "Set text on the currently focused editable field using Accessibility.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", stringProp("Text to enter."))
                },
                required = listOf("text")
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_input_text", "输入文字", input) {
                val service = requireService()
                result(service.setFocusedText(input.requiredString("text")))
            }
        }
    )

    private val screenBackTool = Tool(
        name = "screen_back",
        description = "Press Android Back using Accessibility.",
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        needsApproval = true,
        execute = { input ->
            trackScreenTool("screen_back", "返回", input) {
                result(requireService().back())
            }
        }
    )

    private val screenHomeTool = Tool(
        name = "screen_home",
        description = "Press Android Home using Accessibility.",
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        needsApproval = true,
        execute = { input ->
            trackScreenTool("screen_home", "回到桌面", input) {
                result(requireService().home())
            }
        }
    )

    private val screenOpenAppTool = Tool(
        name = "screen_open_app",
        description = "Open an installed Android app by package name.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("package_name", stringProp("Android package name to launch."))
                },
                required = listOf("package_name")
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_open_app", "打开应用", input) {
                val packageName = input.requiredString("package_name")
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: error("No launch intent for package: $packageName")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                result(true)
            }
        }
    )

    private val screenOpenUrlTool = Tool(
        name = "screen_open_url",
        description = "Open an http/https URL with Android ACTION_VIEW. Prefer webview_open for in-app visual preview; use this when the user wants the system browser/app.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", stringProp("http or https URL to open."))
                },
                required = listOf("url")
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_open_url", "打开 URL", input, runtime = "Android Intent") {
                val url = input.requiredString("url")
                require(url.startsWith("http://") || url.startsWith("https://")) {
                    "Only http and https URLs are allowed"
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                result(true)
            }
        }
    )

    private val screenReadUiTool = Tool(
        name = "screen_read_ui",
        description = "Read the current Accessibility UI tree for screen reasoning. For a multi-step phone goal, first discover screen_run_goal: when enabled it uses Jev inside Accessibility to select and execute validated actions, reducing model round trips. Use this raw tree tool for inspection or when that tool hands back.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("max_nodes", integerProp("Maximum nodes to include. Defaults to 120."))
                }
            )
        },
        execute = { input ->
            trackScreenTool("screen_read_ui", "读取 UI 树", input) {
                val service = requireService()
                textJson {
                    put("ui_tree", service.dumpUiTree(input.int("max_nodes") ?: 120))
                }
            }
        }
    )

    private val screenFindTextTool = Tool(
        name = "screen_find_text",
        description = "Find text in the current Accessibility UI tree and return matching node bounds.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", stringProp("Text to find."))
                    put("max_nodes", integerProp("Maximum nodes to scan. Defaults to 160."))
                },
                required = listOf("text")
            )
        },
        execute = { input ->
            trackScreenTool("screen_find_text", "查找屏幕文字", input) {
                val matches = requireService().findTextNodes(
                    query = input.requiredString("text"),
                    maxNodes = input.int("max_nodes") ?: 160,
                )
                textJson {
                    put("matches", buildJsonArray {
                        matches.forEachIndexed { index, match ->
                            add(match.toJson(index))
                        }
                    })
                }
            }
        }
    )

    private val screenTapTextTool = Tool(
        name = "screen_tap_text",
        description = "Tap the first Accessibility node containing the requested text.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", stringProp("Text to tap."))
                    put("max_nodes", integerProp("Maximum nodes to scan. Defaults to 160."))
                },
                required = listOf("text")
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_tap_text", "点击文字", input) {
                val service = requireService()
                val match = service.findTextNodes(
                    query = input.requiredString("text"),
                    maxNodes = input.int("max_nodes") ?: 160,
                ).firstOrNull() ?: error("Text not found on screen: ${input.requiredString("text")}")
                val ok = service.tap(match.bounds.exactCenterX(), match.bounds.exactCenterY())
                textJson {
                    put("success", ok)
                    put("match", match.toJson(0))
                }
            }
        }
    )

    private val screenWaitForTextTool = Tool(
        name = "screen_wait_for_text",
        description = "Wait until text appears in the Accessibility UI tree.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", stringProp("Text to wait for."))
                    put("timeout_ms", integerProp("Timeout in milliseconds. Defaults to 10000."))
                    put("interval_ms", integerProp("Polling interval in milliseconds. Defaults to 500."))
                },
                required = listOf("text")
            )
        },
        execute = { input ->
            trackScreenTool("screen_wait_for_text", "等待屏幕文字", input) {
                val deadline = System.currentTimeMillis() + (input.long("timeout_ms") ?: 10_000L).coerceIn(500L, 60_000L)
                val interval = (input.long("interval_ms") ?: 500L).coerceIn(100L, 3_000L)
                val service = requireService()
                var matches = emptyList<app.amber.core.automation.AccessibilityTextMatch>()
                while (System.currentTimeMillis() < deadline) {
                    matches = service.findTextNodes(input.requiredString("text"), maxNodes = 200)
                    if (matches.isNotEmpty()) break
                    delay(interval)
                }
                textJson {
                    put("found", matches.isNotEmpty())
                    put("matches", buildJsonArray { matches.take(5).forEachIndexed { index, match -> add(match.toJson(index)) } })
                }
            }
        }
    )

    private val screenScrollUntilTool = Tool(
        name = "screen_scroll_until",
        description = "Swipe repeatedly until target text appears in the Accessibility UI tree.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", stringProp("Text to find."))
                    put("direction", stringProp("Swipe direction: down, up, left, or right. Defaults to down."))
                    put("max_swipes", integerProp("Maximum swipes. Defaults to 8."))
                },
                required = listOf("text")
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_scroll_until", "滚动查找文字", input) {
                val service = requireService()
                val maxSwipes = (input.int("max_swipes") ?: 8).coerceIn(1, 20)
                val direction = input.string("direction") ?: "down"
                val query = input.requiredString("text")
                var matches = service.findTextNodes(query, maxNodes = 200)
                var swipes = 0
                var stalled = false
                var lastTree = service.dumpUiTree(maxNodes = 200)
                while (matches.isEmpty() && swipes < maxSwipes) {
                    swipeByDirection(service, direction)
                    swipes++
                    delay(450)
                    val tree = service.dumpUiTree(maxNodes = 200)
                    matches = service.findTextNodes(query, maxNodes = 200)
                    if (tree == lastTree && matches.isEmpty()) {
                        // 滑到底/无位移：树完全没变，不再空滑到上限。
                        stalled = true
                        break
                    }
                    lastTree = tree
                }
                textJson {
                    put("found", matches.isNotEmpty())
                    put("swipes", swipes)
                    if (stalled) put("stalled", true)
                    put("matches", buildJsonArray { matches.take(5).forEachIndexed { index, match -> add(match.toJson(index)) } })
                }
            }
        }
    )

    /**
     * 固定语义流程：每步目标与验收条件都已确定、不需要判断服务介入的场景
     * （导航、搜索、填写草稿）。逐步从新观察重新定位，不保存 ref 或坐标；
     * 任何歧义、页面偏离或验收不满足都交回主模型，不预猜后续。
     */
    private fun screenRunFlowTool(runId: String?) = Tool(
        name = "screen_run_flow",
        description = "Run 1-12 fixed, already-decided steps in the authorized app without asking the " +
            "judgment service at each step. Use it for known navigation, search, or filling a draft " +
            "supplied by the user. Every step re-reads a fresh observation and re-locates the target " +
            "by exact text; refs and coordinates are never stored between steps. A step is dispatched " +
            "at most once. Any ambiguity, missing target, app switch, or unmet acceptance condition " +
            "hands the remaining work back to you with the evidence, never a replay. Final send, " +
            "publish, pay, delete, login and permission grants are not supported here — use screen_act " +
            "style single actions for those.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("package_name", stringProp("Authorized package the flow stays inside."))
                    put("steps", buildJsonObject {
                        put("type", "array")
                        put("minItems", 1)
                        put("maxItems", 12)
                        put("description", "Ordered steps; each one is dispatched once, in order.")
                        put("items", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("target", stringProp("Exact visible text or label to act on."))
                                put("operation", buildJsonObject {
                                    put("type", "string")
                                    put("enum", buildJsonArray { add("click"); add("type") })
                                })
                                put("text", stringProp("Text to type; required when operation=type."))
                                put("expectation", expectationProp())
                            })
                            put("required", buildJsonArray { add("target"); add("operation") })
                        })
                    })
                },
                required = listOf("package_name", "steps"),
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        mandatoryApproval = true,
        execute = { input ->
            trackScreenTool(
                "screen_run_flow", "固定流程执行", input,
                runtime = "Android Accessibility", runId = runId,
            ) {
                if (runKey(runId) == null) {
                    return@trackScreenTool textJson {
                        put("status", "handback")
                        put("reason", "no_run_scope")
                    }
                }
                val packageName = input.requiredString("package_name")
                val allowed = allowedPackages()
                if (packageName !in allowed) {
                    return@trackScreenTool textJson {
                        put("status", "no_scope")
                        put("package_name", packageName)
                        put("reason", if (allowed.isEmpty()) "no_app_scope" else "package_not_allowed")
                        put("allowed_packages", buildJsonArray { allowed.forEach { add(it) } })
                        put("remedy", "call screen_apps then screen_add_app with the exact package name")
                    }
                }
                val steps = (input.jsonObject["steps"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                    .orEmpty()
                if (steps.isEmpty() || steps.size > MAX_FLOW_STEPS) {
                    return@trackScreenTool textJson {
                        put("status", "rejected")
                        put("reason", "steps_must_be_1_to_${MAX_FLOW_STEPS}")
                    }
                }
                val service = requireService()
                val deadline = System.currentTimeMillis() + FLOW_BUDGET_MS
                val executed = mutableListOf<kotlinx.serialization.json.JsonObject>()

                var handback: String? = null
                for ((index, step) in steps.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    if (System.currentTimeMillis() >= deadline) { handback = "budget_exhausted"; break }
                    val target = step.string("target").orEmpty().trim()
                    val operation = step.string("operation").orEmpty()
                    val text = step.string("text")
                    if (target.isBlank()) { handback = "missing_target"; break }
                    if (operation != "click" && operation != "type") { handback = "unsupported_operation"; break }
                    if (operation == "type" && text.isNullOrEmpty()) { handback = "missing_text"; break }
                    // 与 screen_run_goal 同一条红线：提交类目标不在固定流程里执行。
                    if (BLOCKED_FLOW_TARGET.containsMatchIn(target)) { handback = "blocked_target"; break }

                    val snapshot = withContext(Dispatchers.Main.immediate) { service.captureScreenSnapshot() }
                        ?: return@trackScreenTool textJson {
                            put("status", "handback"); put("reason", "screen_unavailable")
                        }
                    if (snapshot.packageName != packageName) { handback = "foreground_package_changed"; break }
                    val candidates = snapshot.nodes.filter { it.label == target }
                    if (candidates.isEmpty()) { handback = "target_not_found"; break }
                    // 同名控件不猜：交给主模型用更具体的目标重新规划。
                    if (candidates.size > 1) { handback = "ambiguous_target"; break }
                    val node = candidates.single()
                    if (operation == "type" && !node.editable) { handback = "target_not_editable"; break }
                    if (operation == "click" && !node.clickable) { handback = "target_not_clickable"; break }

                    val expectationStep = step["expectation"] as? kotlinx.serialization.json.JsonObject
                    // absent 需要"动作前确实可见"作为前提，否则永远不可能通过。
                    val targetVisibleBefore = if (expectationStep?.string("kind") == "absent") {
                        val absentTarget = expectationStep.string("target").orEmpty()
                        absentTarget.isNotBlank() &&
                            snapshot.nodes.any { it.hasOwnLabel && it.label == absentTarget }
                    } else {
                        false
                    }
                    val action = app.amber.core.automation.ScreenAction(
                        kind = if (operation == "click") {
                            app.amber.core.automation.ScreenActionKind.CLICK
                        } else {
                            app.amber.core.automation.ScreenActionKind.TYPE
                        },
                        ref = node.ref,
                        text = text,
                    )
                    val receipt = withContext(Dispatchers.Main.immediate) {
                        service.performScreenAction(snapshot, action)
                    }
                    val expectation = expectationStep
                    val verdict = if (receipt.dispatched && expectation != null && receipt.status == "ok") {
                        awaitExpectation(AccessibilityExpectationProbe(service), expectation, targetVisibleBefore)
                    } else {
                        null
                    }
                    executed += buildJsonObject {
                        put("step", index)
                        put("target", target)
                        put("operation", operation)
                        put("receipt", receipt.status)
                        put("dispatched", receipt.dispatched)
                        verdict?.let { put("verification", it.toJson()) }
                    }
                    // 回执 unknown / rejected 一律停手：可能已执行，禁止重放。
                    if (receipt.status != "ok") { handback = "receipt_${receipt.status}"; break }
                    if (verdict != null && !verdict.met) {
                        handback = if (verdict.ambiguous) "verification_ambiguous" else "verification_${verdict.detail}"
                        break
                    }
                    // 下一步从新观察开始：给界面一点稳定时间，避免读到过渡态。
                    delay(FLOW_STEP_SETTLE_MS)
                }
                val remaining = steps.size - executed.size
                textJson {
                    put("status", if (handback == null) "completed" else "handback")
                    handback?.let { put("reason", it) }
                    if (handback != null) {
                        put(
                            "next",
                            if (handback == "receipt_unknown") {
                                "never replay an unknown action; verify on screen and re-plan."
                            } else {
                                "re-plan the remaining steps from the returned evidence; do not replay executed ones."
                            },
                        )
                    }
                    put("steps_executed", executed.size)
                    put("steps_remaining", remaining.coerceAtLeast(0))
                    put("steps", buildJsonArray { executed.forEach { add(it) } })
                }
            }
        },
    )

    /**
     * 只读列表采集：在唯一滚动容器里逐屏读取可见行，仅按"两屏都唯一的稳定 id"
     * 合并相邻页重叠；覆盖范围只代表已观察屏幕，绝不宣称列表完整。
     */
    private val screenCollectListTool = Tool(
        name = "screen_collect_list",
        description = "Read visible rows of one scrollable list, swiping screen by screen, and return " +
            "them in reading order. Only stable identifiers seen uniquely on both adjacent screens are " +
            "merged as overlap; anonymous rows are kept as-is and may repeat. Coverage always means " +
            "'observed screens only' — it never claims the list is complete. Never taps a row. Hands " +
            "back on ambiguity, no progress, app switch, truncation or budget exhaustion.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("container", stringProp("Exact text or label of the list's scroll container."))
                    put("direction", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("up"); add("down") })
                        put("description", "Finger movement direction.")
                    })
                    put("max_swipes", integerProp("Swipe budget 0..8. Defaults to 3; 0 reads the current screen only."))
                    put("max_items", integerProp("Item cap 1..200. Defaults to 50."))
                },
                required = listOf("container", "direction"),
            )
        },
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { input ->
            trackScreenTool("screen_collect_list", "读取列表", input) {
                val containerLabel = input.requiredString("container")
                val direction = input.string("direction") ?: "down"
                val maxSwipes = (input.int("max_swipes") ?: 3).coerceIn(0, MAX_COLLECT_SWIPES)
                val maxItems = (input.int("max_items") ?: 50).coerceIn(1, 200)
                val service = requireService()
                val deadline = System.currentTimeMillis() + COLLECT_BUDGET_MS
                val items = mutableListOf<kotlinx.serialization.json.JsonObject>()
                // 上一屏的行身份；只有"两屏都唯一且完全一致"的行才视为重叠合并。
                // 不能用 viewId 去重：同一列表所有行共用同一个资源 id。
                var previousScreenRows: Set<String> = emptySet()
                var swipeCount = 0
                var stopReason = "max_swipes"
                var lastSignature: String? = null
                var packageName: String? = null

                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (System.currentTimeMillis() >= deadline) { stopReason = "budget_exhausted"; break }
                    val snapshot = withContext(Dispatchers.Main.immediate) { service.captureScreenSnapshot() }
                        ?: run { stopReason = "screen_unavailable"; break }
                    packageName = packageName ?: snapshot.packageName
                    if (snapshot.packageName != packageName) { stopReason = "foreground_package_changed"; break }
                    val containers = snapshot.nodes.filter { it.scrollable && it.label == containerLabel }
                    if (containers.size != 1) {
                        stopReason = if (containers.isEmpty()) "container_not_found" else "container_ambiguous"
                        break
                    }
                    val container = containers.single()
                    val rows = snapshot.nodes
                        .filter { it.ref.startsWith("${container.ref}/") && it.hasOwnLabel && it.label.isNotBlank() }
                        .sortedWith(compareBy({ it.top }, { it.left }))
                    // 行身份 = 文本 + 位置。同一资源 id 会被所有行共享，不能用它判断重复。
                    fun rowKey(node: app.amber.core.automation.ScreenNode): String =
                        "${node.label}|${node.top}|${node.bottom}|${node.left}|${node.right}"
                    val currentKeys = rows.map(::rowKey).toSet()
                    for (row in rows) {
                        if (items.size >= maxItems) { stopReason = "max_items"; break }
                        // 只有相邻两屏都出现的同一行才算滚动重叠；同名不同位置的行各自保留。
                        if (swipeCount > 0 && rowKey(row) in previousScreenRows) continue
                        items += buildJsonObject {
                            put("label", row.label.take(400))
                            if (row.viewId.isNotBlank()) put("identifier", row.viewId)
                            put("screen", swipeCount)
                        }
                    }
                    previousScreenRows = currentKeys
                    if (stopReason == "max_items") break

                    val signature = rows.joinToString("|") { "${it.viewId}:${it.label}" }
                    if (signature == lastSignature) { stopReason = "no_progress"; break }
                    lastSignature = signature
                    if (swipeCount >= maxSwipes) { stopReason = "max_swipes"; break }

                    val receipt = withContext(Dispatchers.Main.immediate) {
                        service.performScreenAction(
                            snapshot,
                            app.amber.core.automation.ScreenAction(
                                kind = if (direction == "up") {
                                    app.amber.core.automation.ScreenActionKind.SCROLL_BACKWARD
                                } else {
                                    app.amber.core.automation.ScreenActionKind.SCROLL_FORWARD
                                },
                                ref = container.ref,
                            ),
                        )
                    }
                    if (!receipt.dispatched) { stopReason = "scroll_${receipt.status}"; break }
                    swipeCount++
                    // 界面确实变了但没有新行：仍算前进（可能只是整屏重复），继续到滑动预算。
                    delay(SCROLL_SETTLE_MS)
                }

                textJson {
                    put("items", buildJsonArray { items.forEach { add(it) } })
                    put("count", items.size)
                    put("swipe_count", swipeCount)
                    put("stop_reason", stopReason)
                    put("coverage", "observed_screens_only")
                }
            }
        },
    )

    private val screenScreenshotTool = Tool(
        name = "screen_screenshot",
        description = "Capture one screen frame for VLM reasoning using Android MediaProjection after user approval.",
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        needsApproval = true,
        execute = { input ->
            trackScreenTool("screen_screenshot", "截取屏幕", input, runtime = "MediaProjection") {
                val result = screenCaptureManager.capture()
                textJson {
                    put("status", "captured")
                    put("path", result.file.absolutePath)
                    put("width", result.width)
                    put("height", result.height)
                    put("size_bytes", result.sizeBytes)
                    put("created_at", result.createdAt.toString())
                }
            }
        }
    )

    private val vlmTaskTool = Tool(
        name = "vlm_task",
        description = "Plan a phone automation task with VLM-style trace semantics. Stage 0 records the " +
            "intent only — it performs no actions; execute via screen_read_ui plus low-level screen tools.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("goal", stringProp("Natural language phone automation goal."))
                },
                required = listOf("goal")
            )
        },
        needsApproval = true,
        execute = { input ->
            trackScreenTool("vlm_task", "规划 VLM 任务", input, runtime = "vlm-contract-stage0") {
                textJson {
                    put("status", "planned")
                    put("goal", input.requiredString("goal"))
                    put("message", "VLM orchestration contract is connected. Use screen_read_ui plus low-level screen actions until remote VLM screenshot reasoning is wired.")
                }
            }
        }
    )

    private suspend fun trackScreenTool(
        toolName: String,
        title: String,
        input: JsonElement,
        runtime: String = "Android Accessibility",
        runId: String? = null,
        block: suspend () -> List<UIMessagePart>,
    ): List<UIMessagePart> {
        // 只有能界定"本次 run"的入口才受 screen_stop 约束；低级手势由每次审批把关。
        check(!isStopped(runId)) {
            "Screen control was ended by screen_stop for this run; no further action is dispatched."
        }
        check(screenOwner.tryLock()) { "Another screen operation is active; wait for it to finish before observing again." }
        try {
            val toolCallId = activityStore.startTool(
                toolName = toolName,
                title = title,
                inputPreview = input.activityInputPreview(toolName),
                runtime = runtime,
            )
            return try {
                val result = block()
                activityStore.complete(toolCallId, result.previewText())
                result
            } catch (error: Throwable) {
                activityStore.fail(toolCallId, error)
                throw error
            }
        } finally {
            screenOwner.unlock()
        }
    }

    private fun requireService(): AccessibilityController =
        getActiveAccessibilityController()
            ?: error("AmberAgent Accessibility is not enabled. Open ${Settings.ACTION_ACCESSIBILITY_SETTINGS} and enable AmberAgent Accessibility.")

    private fun requireDisplayPoint(x: Float, y: Float, label: String): ScreenPoint {
        val metrics = context.resources.displayMetrics
        return requirePointInDisplayBounds(
            x = x,
            y = y,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            label = label
        )
    }

    private fun coordinateSchema(xName: String, yName: String) = InputSchema.Obj(
        properties = buildJsonObject {
            put(xName, numberProp("X coordinate."))
            put(yName, numberProp("Y coordinate."))
        },
        required = listOf(xName, yName)
    )

    private fun result(success: Boolean) = textJson {
        put("success", success)
    }

    /**
     * 动作后验证：手势注入 ≠ 目标生效。
     *
     * 两种入口共用一次「动作后重读」：
     * - `verify_text`（兼容既有调用）：文本出现即算看到；
     * - `expectation`：visible / enabled / absent / value_equals 四类条件，
     *   最多 4 次观察、继续观察预算 2 秒。**等待不会重放动作**——动作只发送一次，
     *   这里只读界面，条件不满足时如实报告 unmet，而不是再点一次。
     *
     * @param targetWasVisibleBefore 动作前目标是否可见；仅 `absent` 用它排除
     *   "目标从未出现过"的假通过。
     */
    private suspend fun verifiedResult(
        service: AccessibilityController,
        input: JsonElement,
        success: Boolean,
        targetWasVisibleBefore: Boolean = false,
    ): List<UIMessagePart> {
        val verifyText = input.string("verify_text")
        val expectation = (input.jsonObject["expectation"] as? kotlinx.serialization.json.JsonObject)
        if (!success || (verifyText == null && expectation == null)) return result(success)

        if (expectation != null) {
            val probe = AccessibilityExpectationProbe(service)
            val verdict = awaitExpectation(probe, expectation, targetWasVisibleBefore)
            return textJson {
                put("success", success)
                put("verification", verdict.toJson())
            }
        }

        val deadline = System.currentTimeMillis() +
            (input.long("verify_timeout_ms") ?: 2_000L).coerceIn(200L, 15_000L)
        var found = false
        while (System.currentTimeMillis() < deadline) {
            if (service.findTextNodes(verifyText!!, maxNodes = 200).isNotEmpty()) {
                found = true
                break
            }
            delay(250)
        }
        return textJson {
            put("success", success)
            put("verify_text_found", found)
        }
    }

    /** 最多 4 次观察、总预算 2 秒；条件一旦满足（或不唯一）立即返回，不做额外轮询。 */
    private suspend fun awaitExpectation(
        probe: ScreenExpectationProbe,
        expectation: kotlinx.serialization.json.JsonObject,
        targetWasVisibleBefore: Boolean,
    ): ExpectationVerdict {
        val deadline = System.currentTimeMillis() + EXPECTATION_BUDGET_MS
        val startedAt = System.currentTimeMillis()
        var verdict = ExpectationVerdict(kind = expectation.string("kind").orEmpty(), met = false, detail = "not_observed")
        var observations = 0
        while (observations < EXPECTATION_MAX_OBSERVATIONS) {
            observations++
            verdict = evaluateExpectation(probe, expectation, targetWasVisibleBefore)
            if (verdict.met || verdict.ambiguous) break
            if (observations == EXPECTATION_MAX_OBSERVATIONS) break
            if (System.currentTimeMillis() + EXPECTATION_INTERVAL_MS >= deadline) break
            delay(EXPECTATION_INTERVAL_MS)
        }
        return verdict.copy(
            observations = observations,
            elapsedMs = System.currentTimeMillis() - startedAt,
        )
    }

    private suspend fun evaluateExpectation(
        probe: ScreenExpectationProbe,
        expectation: kotlinx.serialization.json.JsonObject,
        targetWasVisibleBefore: Boolean,
    ): ExpectationVerdict = withContext(Dispatchers.Main.immediate) {
        evaluateScreenExpectation(
            probe = probe,
            kind = expectation.string("kind").orEmpty(),
            target = expectation.string("target").orEmpty(),
            anchor = expectation.string("anchor").orEmpty(),
            value = expectation.string("value").orEmpty(),
            targetWasVisibleBefore = targetWasVisibleBefore,
        )
    }

    private fun ExpectationVerdict.toJson(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("kind", kind)
        put("met", met)
        put("detail", detail)
        if (ambiguous) put("ambiguous", true)
        put("observations", observations)
        put("elapsed_ms", elapsedMs)
    }

    /** 动作前目标是否可见；只有 absent 需要，其它 kind 不额外读树。 */
    private suspend fun wasAbsenceTargetVisible(service: AccessibilityController, input: JsonElement): Boolean {
        val target = (input.jsonObject["expectation"] as? kotlinx.serialization.json.JsonObject)
            ?.takeIf { it.string("kind") == "absent" }
            ?.string("target")
            ?.takeIf { it.isNotBlank() }
            ?: return false
        return withContext(Dispatchers.Main.immediate) {
            AccessibilityExpectationProbe(service).matchingTexts(target).isNotEmpty()
        }
    }

    /** expectation 的形状；四类条件都在动作后的新界面上判定。 */
    private fun expectationProp() = buildJsonObject {
        put("type", "object")
        put("description", "Optional post-action acceptance check, evaluated on the re-read UI. " +
            "The action is sent once; a failed check reports unmet and never replays it.")
        put("properties", buildJsonObject {
            put("kind", buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("visible"); add("enabled"); add("absent"); add("value_equals")
                })
                put("description", "visible = target text is present; enabled = the single visible " +
                    "target is enabled; absent = target gone while anchor still visible (target must " +
                    "have been visible before the action); value_equals = exactly one non-password " +
                    "editable field now holds value.")
            })
            put("target", stringProp("Exact text or accessibility label to check. Required for " +
                "visible/enabled/absent; ignored by value_equals."))
            put("anchor", stringProp("Required for absent: text that must still be visible."))
            put("value", stringProp("Required for value_equals: full expected text of the field."))
        })
        put("required", buildJsonArray { add("kind") })
    }


    private suspend fun swipeByDirection(service: AccessibilityController, direction: String): Boolean {
        val width = context.resources.displayMetrics.widthPixels.toFloat()
        val height = context.resources.displayMetrics.heightPixels.toFloat()
        return when (direction) {
            "up" -> service.swipe(width * 0.5f, height * 0.35f, width * 0.5f, height * 0.75f)
            "left" -> service.swipe(width * 0.75f, height * 0.5f, width * 0.25f, height * 0.5f)
            "right" -> service.swipe(width * 0.25f, height * 0.5f, width * 0.75f, height * 0.5f)
            else -> service.swipe(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.35f)
        }
    }

    private fun app.amber.core.automation.AccessibilityTextMatch.toJson(index: Int) = buildJsonObject {
        put("index", index)
        put("text", text)
        put("content_description", contentDescription)
        put("class_name", className)
        put("view_id", viewId.orEmpty())
        put("clickable", clickable)
        put("enabled", enabled)
        put("bounds", buildJsonObject {
            put("left", bounds.left)
            put("top", bounds.top)
            put("right", bounds.right)
            put("bottom", bounds.bottom)
            put("center_x", bounds.exactCenterX().toDouble())
            put("center_y", bounds.exactCenterY().toDouble())
        })
    }

    private fun stringProp(description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun numberProp(description: String) = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    private fun integerProp(description: String) = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun List<UIMessagePart>.previewText(): String =
        joinToString("\n") { part ->
            when (part) {
                is UIMessagePart.Text -> part.text
                else -> part.toString()
            }
        }.takeLast(1_600)

    private fun JsonElement.activityInputPreview(toolName: String): String =
        when (toolName) {
            "screen_run_goal" -> buildJsonObject {
                put("package_name", string("package_name").orEmpty())
                put("goal_chars", string("goal")?.length ?: 0)
            }.toString()
            "screen_input_text" -> buildJsonObject {
                put("text_chars", string("text")?.length ?: 0)
            }.toString()
            else -> toString()
        }
}

/** 动作后验收的判定结果；ambiguous 表示界面无法唯一确定目标，需交回而不是重试。 */
internal data class ExpectationVerdict(
    val kind: String,
    val met: Boolean,
    val detail: String,
    val ambiguous: Boolean = false,
    val observations: Int = 0,
    val elapsedMs: Long = 0,
)

/**
 * 验收所需的最小界面读取面。抽成接口是为了让四类条件能在 JVM 单测里
 * 直接验证，而不必启动 Android 框架或真机无障碍服务。
 */
internal interface ScreenExpectationProbe {
    fun matchingTexts(query: String): List<ProbeTextMatch>
    fun editableValues(): List<String>
}

internal data class ProbeTextMatch(val enabled: Boolean)

/** 生产实现：读 live Accessibility 树，不改任何状态。 */
private class AccessibilityExpectationProbe(
    private val service: AccessibilityController,
) : ScreenExpectationProbe {
    override fun matchingTexts(query: String): List<ProbeTextMatch> =
        service.captureScreenSnapshot()?.nodes.orEmpty()
            .filter { it.hasOwnLabel && it.label == query }
            .map { ProbeTextMatch(enabled = it.enabled) }

    override fun editableValues(): List<String> =
        service.captureScreenSnapshot()?.nodes
            ?.mapNotNull { it.editableValue }
            .orEmpty()
}

/** 密码节点的展示遮罩；其 editableValue 为空，不进入等值验收。 */
internal const val MASKED_EDITABLE_LABEL = "[password]"

/**
 * 四类验收条件的纯判定。只读 probe，绝不重放动作。
 *
 * - visible：目标文本存在；
 * - enabled：目标唯一可见且 enabled；
 * - absent：目标此前可见、现在消失，且 anchor 仍可见；
 * - value_equals：恰好一个非密码可编辑框的文本等于 value。
 */
internal fun evaluateScreenExpectation(
    probe: ScreenExpectationProbe,
    kind: String,
    target: String,
    anchor: String,
    value: String,
    targetWasVisibleBefore: Boolean,
): ExpectationVerdict = when (kind) {
    "visible" -> {
        if (target.isBlank()) ExpectationVerdict(kind, false, "missing_target")
        else {
            val found = probe.matchingTexts(target).isNotEmpty()
            ExpectationVerdict(kind, found, if (found) "visible" else "not_visible")
        }
    }
    "enabled" -> {
        val matches = if (target.isBlank()) emptyList() else probe.matchingTexts(target)
        when {
            target.isBlank() -> ExpectationVerdict(kind, false, "missing_target")
            matches.isEmpty() -> ExpectationVerdict(kind, false, "not_visible")
            // 多命中时无法唯一确定目标，交回而不是猜。
            matches.size > 1 -> ExpectationVerdict(kind, false, "ambiguous_target", ambiguous = true)
            else -> ExpectationVerdict(kind, matches.single().enabled, if (matches.single().enabled) "enabled" else "disabled")
        }
    }
    "absent" -> when {
        target.isBlank() || anchor.isBlank() -> ExpectationVerdict(kind, false, "missing_anchor")
        // 目标从未出现过时"消失"没有意义，不能给出假通过。
        !targetWasVisibleBefore -> ExpectationVerdict(kind, false, "target_not_visible_before_action")
        else -> {
            val targetGone = probe.matchingTexts(target).isEmpty()
            val anchorVisible = probe.matchingTexts(anchor).isNotEmpty()
            ExpectationVerdict(
                kind,
                met = targetGone && anchorVisible,
                detail = when {
                    !targetGone -> "target_still_visible"
                    !anchorVisible -> "anchor_not_visible"
                    else -> "absent"
                },
            )
        }
    }
    "value_equals" -> when {
        value.isBlank() -> ExpectationVerdict(kind, false, "missing_value")
        else -> {
            val matching = probe.editableValues()
                .filter { it == value }
            when {
                matching.size == 1 -> ExpectationVerdict(kind, true, "value_equals")
                matching.size > 1 -> ExpectationVerdict(kind, false, "ambiguous_target", ambiguous = true)
                else -> ExpectationVerdict(kind, false, "value_mismatch")
            }
        }
    }
    else -> ExpectationVerdict(kind, false, "unsupported_kind")
}

/** 固定流程与列表采集的边界：步骤数、滑动次数、总预算。 */
private const val MAX_FLOW_STEPS = 12
private const val FLOW_BUDGET_MS = 60_000L
private const val MAX_COLLECT_SWIPES = 8
private const val COLLECT_BUDGET_MS = 60_000L

/** 一次滑动后等界面稳定的时间；稳定不等于业务成功，仅用于下一屏观察。 */
private const val SCROLL_SETTLE_MS = 350L

/** 固定流程两步之间的稳定时间。 */
private const val FLOW_STEP_SETTLE_MS = 250L

/**
 * 固定流程不得点击/输入的目标。与 JevScreenGoalRunner 的候选排除同一个红线，
 * 只是这里由宿主自己判：提交、支付、删除、授权、登录类目标一律交回。
 */
private val BLOCKED_FLOW_TARGET = Regex(
    "发送|发布|支付|付款|购买|下单|删除|清空|授权|允许|登录|登出|退出登录|密码|验证码|充值|转账|提交|确认|" +
        "\\b(send|publish|post|submit|confirm|pay|buy|purchase|delete|authorize|allow|login|password|" +
        "verification code|subscribe|donate)\\b",
    RegexOption.IGNORE_CASE,
)

/** 一次验收最多 4 次观察，继续观察预算 2 秒（与 iOS 的 phone_act 一致）。 */
private const val EXPECTATION_MAX_OBSERVATIONS = 4
private const val EXPECTATION_INTERVAL_MS = 250L
private const val EXPECTATION_BUDGET_MS = 2_000L

private fun JsonElement.requiredFloat(name: String): Float =
    jsonObject[name]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: error("$name is required")

internal data class ScreenPoint(val x: Float, val y: Float)

internal fun requirePointInDisplayBounds(
    x: Float,
    y: Float,
    width: Int,
    height: Int,
    label: String,
): ScreenPoint {
    require(x.isFinite() && y.isFinite()) {
        "$label point out of bounds: coordinates must be finite"
    }
    require(width > 0 && height > 0) {
        "$label point out of bounds: invalid display bounds ${width}x$height"
    }
    require(x >= 0f && x < width && y >= 0f && y < height) {
        "$label point out of bounds: ($x, $y) outside ${width}x$height"
    }
    return ScreenPoint(x, y)
}
