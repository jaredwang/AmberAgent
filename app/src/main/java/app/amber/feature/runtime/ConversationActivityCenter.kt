package app.amber.feature.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/** 其他对话正在发生、但当前对话界面看不到的事。 */
enum class ConversationActivityKind {
    /** 该对话的生成 run 仍在运行（含 WAITING_EXTERNAL / RESUMABLE）。 */
    RUNNING,

    /** 需要用户处理：WAITING_USER 与 OUTCOME_UNKNOWN。 */
    AWAITING_USER,

    /** 失败及需要关注的异常终态。 */
    FAILED,

    /** 正常完成。 */
    COMPLETED,
}

/**
 * 一条跨对话活动提示。
 *
 * @param title 对话标题；查不到时回落为 id 前缀，绝不显示空白条目。
 * @param versionMs 已读水位比较用的 run 版本（updatedAt / finishedAt）。
 */
data class ConversationActivityNotice(
    val conversationId: String,
    val runId: String,
    val title: String,
    val kind: ConversationActivityKind,
    val sinceMs: Long,
    val versionMs: Long,
)

/**
 * 跨对话活动中心：合并 [RunOwnershipRegistry] 的进程内 active runs 与
 * [RunTerminalStore] 的全局持久化投影。不另起一套 run 状态库。
 *
 * 已读水位按 runId + versionMs CAS 落盘；首次 [ensureActivityBaseline] 之后
 * 的 FAILED/COMPLETED 才作为未读终态，避免升级时历史终态轰炸。
 */
class ConversationActivityCenter(
    private val registry: RunOwnershipRegistry,
    private val terminals: RunTerminalStore,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * 标题解析：注入而不是直接依赖仓库，便于在没有数据库的单测里验证
     * "跳过当前对话 / 同对话去重 / 标题回落"这三条规则。
     */
    private val titleProvider: suspend (conversationId: String) -> String?,
) {
    private val pendingOpen = ConcurrentHashMap<String, Pair<String, Long>>()

    fun notices(excluding: String?): Flow<List<ConversationActivityNotice>> =
        candidateFlow().map { candidates ->
            val picked = pickConversationActivityNotices(candidates, excluding)
            buildList(picked.size) {
                for (candidate in picked) {
                    add(candidate.toNotice(titleFor(candidate.conversationId)))
                }
            }
        }.distinctUntilChanged()

    /**
     * Remember the notice the user tapped so [consumeWhileOpen] does not
     * markSeen a newer version that raced in during navigation.
     */
    fun rememberOpenedNotice(notice: ConversationActivityNotice) {
        pendingOpen[notice.conversationId] = notice.runId to notice.versionMs
    }

    suspend fun markSeen(notice: ConversationActivityNotice): Boolean =
        terminals.markActivitySeen(notice.runId, notice.conversationId, notice.versionMs)

    suspend fun markSeen(runId: String, conversationId: String, versionMs: Long): Boolean =
        terminals.markActivitySeen(runId, conversationId, versionMs)

    /**
     * While [conversationId] is the open chat, consume its activity as seen.
     *
     * If [rememberOpenedNotice] ran for this conversation, the first snapshot
     * is only marked at that runId+version; a newer first snapshot stays unread.
     * Later emissions while the page stays open are marked — the user is looking
     * at the chat.
     */
    suspend fun consumeWhileOpen(conversationId: String) {
        val pending = pendingOpen.remove(conversationId)
        var sawFirst = false
        var initialRunId: String? = null
        var initialVersionMs: Long? = null
        candidateFlow()
            .map { candidates ->
                candidates
                    .filter { it.conversationId == conversationId }
                    .minWithOrNull(conversationActivityComparator)
            }
            .collect { candidate ->
                if (!sawFirst) {
                    sawFirst = true
                    if (pending != null) {
                        terminals.markActivitySeen(pending.first, conversationId, pending.second)
                    } else if (candidate != null) {
                        terminals.markActivitySeen(
                            candidate.runId,
                            candidate.conversationId,
                            candidate.versionMs,
                        )
                    }
                    initialRunId = candidate?.runId
                    initialVersionMs = candidate?.versionMs
                    return@collect
                }
                if (candidate?.runId == initialRunId && candidate?.versionMs == initialVersionMs) {
                    return@collect
                }
                if (candidate != null) {
                    terminals.markActivitySeen(
                        candidate.runId,
                        candidate.conversationId,
                        candidate.versionMs,
                    )
                }
            }
    }

    private fun candidateFlow(): Flow<List<ConversationActivityCandidate>> = flow {
        val baselineMs = terminals.ensureActivityBaseline(now())
        emitAll(
            combine(
                registry.activeRuns,
                terminals.observeActivity(),
                terminals.observeActivitySeen(),
            ) { runs, activity, seen ->
                applyConversationActivityReadState(
                    mergeConversationActivityCandidates(runs, activity),
                    seen.associateBy { it.runId },
                    baselineMs,
                )
            },
        )
    }

    /** `Flow.map` 的 lambda 本身是挂起的：这里直接 await，不要 runBlocking 阻塞收集方。 */
    private suspend fun titleFor(conversationId: String): String = try {
        titleProvider(conversationId)?.takeIf { it.isNotBlank() }
            ?: conversationActivityFallbackTitle(conversationId)
    } catch (error: kotlinx.coroutines.CancellationException) {
        // 取消必须继续向上传播；吞掉会让被取消的收集协程把剩下的条目也跑完。
        throw error
    } catch (_: Exception) {
        conversationActivityFallbackTitle(conversationId)
    }

    private suspend fun ConversationActivityCandidate.toNotice(title: String) =
        ConversationActivityNotice(
            conversationId = conversationId,
            runId = runId,
            title = title,
            kind = kind,
            sinceMs = sinceMs,
            versionMs = versionMs,
        )
}

/** 拿不到标题时的回落：id 前缀，绝不显示空白条目。 */
internal fun conversationActivityFallbackTitle(conversationId: String): String =
    conversationId.take(8)

/**
 * Explicit RunTerminalState → activity kind.
 *
 * - WAITING_USER → AWAITING_USER
 * - OUTCOME_UNKNOWN → AWAITING_USER：用户必须确认重试/放弃（与 ImageGenerationContinueSource 一致）
 * - WAITING_EXTERNAL / RESUMABLE → RUNNING：仍在飞行中，沿用现有运行提示
 * - FAILED / STEP_LIMIT / OUTPUT_LIMIT / GUARD_STOPPED / INTERRUPTED → FAILED
 * - COMPLETED → COMPLETED
 * - CANCELLED → 不投影（用户主动停止，不跨对话提醒）
 */
internal fun conversationActivityKindFor(state: RunTerminalState): ConversationActivityKind? =
    when (state) {
        RunTerminalState.RUNNING,
        RunTerminalState.WAITING_EXTERNAL,
        RunTerminalState.RESUMABLE,
        -> ConversationActivityKind.RUNNING

        RunTerminalState.WAITING_USER,
        RunTerminalState.OUTCOME_UNKNOWN,
        -> ConversationActivityKind.AWAITING_USER

        RunTerminalState.FAILED,
        RunTerminalState.STEP_LIMIT,
        RunTerminalState.OUTPUT_LIMIT,
        RunTerminalState.GUARD_STOPPED,
        RunTerminalState.INTERRUPTED,
        -> ConversationActivityKind.FAILED

        RunTerminalState.COMPLETED -> ConversationActivityKind.COMPLETED
        RunTerminalState.CANCELLED -> null
    }

internal fun ConversationActivityKind.rank(): Int = when (this) {
    ConversationActivityKind.AWAITING_USER -> 0
    ConversationActivityKind.FAILED -> 1
    ConversationActivityKind.COMPLETED -> 2
    ConversationActivityKind.RUNNING -> 3
}

internal data class ConversationActivityCandidate(
    val conversationId: String,
    val runId: String,
    val kind: ConversationActivityKind,
    val sinceMs: Long,
    val versionMs: Long,
    val unread: Boolean = true,
)

internal val conversationActivityComparator =
    compareBy<ConversationActivityCandidate> { it.kind.rank() }
        .thenByDescending { it.versionMs }
        .thenBy { it.runId }

/**
 * Registry wins for the same runId so an in-process RUNNING row and its
 * terminal write cannot both appear (no duplicate / flicker at handoff).
 */
internal fun mergeConversationActivityCandidates(
    activeRuns: List<ActiveRun>,
    terminals: List<RunTerminal>,
): List<ConversationActivityCandidate> {
    val byRunId = LinkedHashMap<String, ConversationActivityCandidate>()
    for (terminal in terminals) {
        val kind = conversationActivityKindFor(terminal.state) ?: continue
        val versionMs = terminal.activityVersionMs
        byRunId[terminal.runId] = ConversationActivityCandidate(
            conversationId = terminal.conversationId,
            runId = terminal.runId,
            kind = kind,
            sinceMs = if (kind == ConversationActivityKind.RUNNING) {
                terminal.startedAtMs
            } else {
                versionMs
            },
            versionMs = versionMs,
        )
    }
    for (run in activeRuns) {
        byRunId[run.runId] = ConversationActivityCandidate(
            conversationId = run.conversationId,
            runId = run.runId,
            kind = ConversationActivityKind.RUNNING,
            sinceMs = run.registeredAtMs,
            versionMs = run.registeredAtMs,
        )
    }
    return byRunId.values.toList()
}

internal fun isConversationActivityUnread(
    kind: ConversationActivityKind,
    runId: String,
    versionMs: Long,
    seenByRunId: Map<String, ConversationActivitySeen>,
    baselineMs: Long,
): Boolean {
    val seen = seenByRunId[runId]
    if (seen != null && seen.versionMs >= versionMs) return false
    return when (kind) {
        ConversationActivityKind.RUNNING,
        ConversationActivityKind.AWAITING_USER,
        -> true
        ConversationActivityKind.FAILED,
        ConversationActivityKind.COMPLETED,
        -> versionMs > baselineMs
    }
}

internal fun applyConversationActivityReadState(
    candidates: List<ConversationActivityCandidate>,
    seenByRunId: Map<String, ConversationActivitySeen>,
    baselineMs: Long,
): List<ConversationActivityCandidate> = candidates.map { candidate ->
    candidate.copy(
        unread = isConversationActivityUnread(
            kind = candidate.kind,
            runId = candidate.runId,
            versionMs = candidate.versionMs,
            seenByRunId = seenByRunId,
            baselineMs = baselineMs,
        ),
    )
}

internal fun pickConversationActivityNotices(
    candidates: List<ConversationActivityCandidate>,
    excluding: String?,
): List<ConversationActivityCandidate> =
    candidates
        .asSequence()
        .filter { it.unread && it.conversationId != excluding }
        .groupBy { it.conversationId }
        .values
        .map { group -> group.minWith(conversationActivityComparator) }
        .sortedWith(conversationActivityComparator)
