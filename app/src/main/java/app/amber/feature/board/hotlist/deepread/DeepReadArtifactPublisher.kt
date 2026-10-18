package app.amber.feature.board.hotlist.deepread

import android.util.Log
import app.amber.agent.data.workspace.ArtifactRepository
import app.amber.core.utils.JsonInstant

internal class DeepReadArtifactPublisher(private val artifactRepository: ArtifactRepository?) {
    suspend fun publish(
        topicId: String,
        topicTitle: String,
        output: DeepReadOutput,
    ) {
        if (!output.isComplete()) return
        val repository = artifactRepository ?: return
        runCatching {
            repository.saveDeepRead(
                topicId = topicId,
                title = topicTitle,
                content = JsonInstant.encodeToString(DeepReadOutput.serializer(), output),
            )
        }.onFailure { error ->
            // Workspace sync must not turn an otherwise completed DeepRead
            // into a failed generation; the registry can be retried from the
            // cached completion on the next open/run.
            Log.w(TAG, "completed deep read workspace sync failed", error)
        }
    }

    private companion object { const val TAG = "DeepReadAgentRunManager" }
}
