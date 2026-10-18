package app.amber.feature.ui.pages.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.amber.agent.R
import app.amber.feature.ui.components.ui.workspaceColors
import app.amber.feature.ui.context.LocalNavController
import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.LocalAmberType
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft


// Retained legacy loading helper; the full-screen reader owns chapter load presentation.
@Composable
private fun ChapterBodyLoading() {
    val workspace = workspaceColors()
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = workspace.ink,
        )
    }
}

@Composable
internal fun NovelWorkspaceSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAmberTokens.current
    val type = LocalAmberType.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("//", style = type.eyebrow, color = tokens.accent)
        Text(text, style = type.eyebrow, color = tokens.ink2)
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(tokens.line),
        )
    }
}

// Retained legacy chapter action helper; directory actions now live in its menus.
@Composable
private fun NovelChapterActionChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    val tokens = LocalAmberTokens.current
    val type = LocalAmberType.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(shape)
                .background(
                    when {
                        !enabled -> tokens.surface2.copy(alpha = 0.55f)
                        accent -> tokens.accent.copy(alpha = 0.13f)
                        else -> tokens.surface2
                    },
                )
                .border(
                    1.dp,
                    when {
                        !enabled -> tokens.line
                        accent -> tokens.accent.copy(alpha = 0.34f)
                        else -> tokens.line
                    },
                    shape,
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = type.meta.copy(fontWeight = FontWeight.SemiBold),
                color = when {
                    !enabled -> tokens.ink4
                    accent -> tokens.accent
                    else -> tokens.ink2
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun NovelEditorSaveButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalAmberTokens.current
    val type = LocalAmberType.current
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(shape)
                .background(if (enabled) tokens.accent else tokens.surface2)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = type.meta.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) tokens.accentInk else tokens.ink4,
                maxLines = 1,
            )
        }
    }
}

/** Header back shares the compact visible control and its larger touch target. */
@Composable
internal fun WorkspaceBackButton(onClick: (() -> Unit)? = null) {
    val navController = LocalNavController.current
    NovelWorkspaceNavButton(
        icon = Lucide.ArrowLeft,
        contentDescription = stringResource(R.string.back),
        onClick = { onClick?.invoke() ?: navController.popBackStack() },
    )
}
