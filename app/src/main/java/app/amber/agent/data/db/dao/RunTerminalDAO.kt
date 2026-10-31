package app.amber.agent.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amber.agent.data.db.entity.ConversationActivityBaselineEntity
import app.amber.agent.data.db.entity.ConversationActivitySeenEntity
import app.amber.agent.data.db.entity.RunTerminalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Non-terminal states of [run_terminal] — the single source of truth for
 * "still live (running or paused)". Shared by every conditional write guard
 * and by the unfinished/active SELECTs below.
 */
internal const val RUN_TERMINAL_LIVE_STATES =
    "('RUNNING', 'WAITING_USER', 'WAITING_EXTERNAL', 'RESUMABLE', 'OUTCOME_UNKNOWN')"

@Dao
interface RunTerminalDAO {
    @Query("SELECT * FROM run_terminal WHERE run_id = :runId")
    suspend fun getByRunId(runId: String): RunTerminalEntity?

    @Query(
        "SELECT * FROM run_terminal WHERE conversation_id = :conversationId " +
            "AND state IN " + RUN_TERMINAL_LIVE_STATES + " " +
            "ORDER BY started_at_ms DESC LIMIT 1"
    )
    suspend fun activeByConversation(conversationId: String): RunTerminalEntity?

    @Query(
        "SELECT * FROM run_terminal WHERE state IN " + RUN_TERMINAL_LIVE_STATES
    )
    suspend fun listUnfinished(): List<RunTerminalEntity>

    /**
     * Insert a fresh RUNNING row; a no-op when [runId] already exists (returns
     * -1). Begin's create path — never overwrites an existing row.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(run: RunTerminalEntity): Long

    /**
     * CAS resume of an existing live (running or paused) row back to RUNNING.
     * A terminal row (finished_at_ms set or terminal state) matches nothing,
     * so a finished run can never be re-opened.
     */
    @Query(
        "UPDATE run_terminal SET state = 'RUNNING', pause_reason = NULL, finished_at_ms = NULL, " +
            "conversation_id = :conversationId, assistant_id = :assistantId, updated_at_ms = :nowMs " +
            "WHERE run_id = :runId AND finished_at_ms IS NULL AND state IN " + RUN_TERMINAL_LIVE_STATES
    )
    suspend fun resumeIfLive(
        runId: String,
        conversationId: String,
        assistantId: String?,
        nowMs: Long,
    ): Int

    /**
     * CAS pause of a live row. Terminal rows are never resurrected.
     */
    @Query(
        "UPDATE run_terminal SET state = :state, pause_reason = :reason, updated_at_ms = :nowMs " +
            "WHERE run_id = :runId AND finished_at_ms IS NULL AND state IN " + RUN_TERMINAL_LIVE_STATES
    )
    suspend fun pauseIfLive(
        runId: String,
        state: String,
        reason: String?,
        nowMs: Long,
    ): Int

    /**
     * CAS finish of a live row. Guards mirror the store contract:
     * finished_at_ms must be NULL (write-once) and a STEP_LIMIT /
     * OUTPUT_LIMIT / GUARD_STOPPED row must never be mapped to COMPLETED.
     */
    @Query(
        "UPDATE run_terminal SET state = :state, pause_reason = :reason, " +
            "updated_at_ms = :nowMs, finished_at_ms = :nowMs " +
            "WHERE run_id = :runId AND finished_at_ms IS NULL " +
            "AND NOT (state IN ('STEP_LIMIT', 'OUTPUT_LIMIT', 'GUARD_STOPPED') AND :state = 'COMPLETED')"
    )
    suspend fun finishIfLive(
        runId: String,
        state: String,
        reason: String?,
        nowMs: Long,
    ): Int

    /**
     * Atomically stop a persisted approval pause. The in-memory ownership
     * entry is intentionally released when the generation flow pauses, so a
     * notification Stop needs this durable ownership path as a fallback.
     */
    @Query(
        "UPDATE run_terminal SET state = 'CANCELLED', pause_reason = 'USER_STOP', " +
            "updated_at_ms = :nowMs, finished_at_ms = :nowMs " +
            "WHERE run_id = :runId AND conversation_id = :conversationId " +
            "AND state = 'WAITING_USER' AND finished_at_ms IS NULL"
    )
    suspend fun cancelWaitingUser(
        runId: String,
        conversationId: String,
        nowMs: Long,
    ): Int

    /**
     * Global activity projection: live runs plus attention terminals.
     * CANCELLED is user-initiated and never a cross-chat prompt.
     */
    @Query(
        "SELECT * FROM run_terminal WHERE state IN " +
            "('RUNNING', 'WAITING_USER', 'WAITING_EXTERNAL', 'RESUMABLE', 'OUTCOME_UNKNOWN', " +
            "'FAILED', 'COMPLETED', 'STEP_LIMIT', 'OUTPUT_LIMIT', 'GUARD_STOPPED', 'INTERRUPTED')"
    )
    fun observeActivityCandidates(): Flow<List<RunTerminalEntity>>

    @Query("SELECT * FROM conversation_activity_seen")
    fun observeActivitySeen(): Flow<List<ConversationActivitySeenEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivityBaselineIfAbsent(row: ConversationActivityBaselineEntity): Long

    @Query("SELECT established_at_ms FROM conversation_activity_baseline WHERE id = 1")
    suspend fun getActivityBaselineMs(): Long?

    @Query(
        "UPDATE conversation_activity_seen SET conversation_id = :conversationId, version_ms = :versionMs " +
            "WHERE run_id = :runId AND version_ms < :versionMs"
    )
    suspend fun updateActivitySeenIfNewer(
        runId: String,
        conversationId: String,
        versionMs: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivitySeenIgnore(row: ConversationActivitySeenEntity): Long

    @Query("SELECT * FROM conversation_activity_seen WHERE run_id = :runId")
    suspend fun getActivitySeen(runId: String): ConversationActivitySeenEntity?
}
