package app.amber.feature.ui.components.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

@Composable
fun MarkdownNew(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onClickCitation: (String) -> Unit = {},
    paragraphSpacing: Dp = 0.dp,
) {
    var document by remember {
        mutableStateOf<org.jsoup.nodes.Document?>(null)
    }

    val updatedContent by rememberUpdatedState(content)
    LaunchedEffect(Unit) {
        snapshotFlow { updatedContent }
            .distinctUntilChanged()
            .mapLatest {
                parseMarkdownHtmlDocument(it)
            }
            .catch { it.printStackTrace() }
            .flowOn(Dispatchers.Default)
            .collect { document = it }
    }

    ProvideTextStyle(style) {
        Column(
            modifier = modifier.padding(start = 4.dp),
            verticalArrangement = if (paragraphSpacing > 0.dp) {
                Arrangement.spacedBy(paragraphSpacing)
            } else {
                Arrangement.Top
            },
        ) {
            val prepared = document
            if (prepared == null) {
                androidx.compose.material3.Text(content, style = style)
            } else {
                prepared.body().childNodes().fastForEach { node ->
                    HtmlBodyNode(node = node, onClickCitation = onClickCitation)
                }
            }
        }
    }
}
