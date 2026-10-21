package app.amber.feature.ui.components.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.placeholder
import coil3.size.Size
import app.amber.agent.BuildConfig
import app.amber.agent.R
import app.amber.feature.ui.components.ui.ImagePreviewDialog
import app.amber.feature.ui.components.ui.LocalExportContext
import app.amber.feature.ui.modifier.shimmer
import app.amber.feature.ui.theme.LocalDarkMode
import java.io.File

/**
 * Coil 3's OkHttp fetcher rejects `file://` strings. Map those to [File] so
 * FileFetcher decodes them. Production `file://` sources:
 * generate_image output under `filesDir/chat_images/{conversationId}/`,
 * user-uploaded images copied into `filesDir/upload/`, image-edit sources,
 * and device-fixture PNGs. `http` / `https` / `content://` stay the original
 * String so OkHttp and the content-URI fetchers are unchanged.
 */
internal fun coilImageData(model: String?): Any? {
    if (model.isNullOrBlank()) return model
    val trimmed = model.trim().removePrefix("<").removeSuffix(">")
    if (trimmed.startsWith("file:", ignoreCase = true)) {
        val path = trimmed.substringAfter(":").removePrefix("//")
        if (path.isNotBlank()) return File(path)
    }
    return model
}

@Composable
fun ZoomableAsyncImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
) {
    var showImageViewer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val placeholder = if(LocalDarkMode.current) R.drawable.placeholder_dark else R.drawable.placeholder
    val export = LocalExportContext.current
    // 必须记住请求：流式渲染每帧都会重组，每次新建 ImageRequest 会让 Coil 把它当成
    // 新请求重启加载，图还在屏幕里就退回占位/水波纹——这正是"滚回屏幕前一直是空白"。
    // data 是稳定 String，remember 后同一个请求复用内存缓存，命中即绘制。
    val coilModel = remember(model, export, context, placeholder) {
        ImageRequest.Builder(context)
            .data(coilImageData(model))
            .placeholder(placeholder)
            .size(Size.ORIGINAL)
            .crossfade(false)
            .allowHardware(!export)
            .build()
    }
    var loading by remember(model) { mutableStateOf(model.isNullOrBlank()) }
    // 一旦这张图成功画过，就再也不用水波纹盖住它：水波纹是"还没有图"的提示，
    // 覆盖已画好的内容只会看起来像图片又变空白了。
    var loaded by remember(model) { mutableStateOf(false) }
    // Zero layout size keeps Coil from starting the request. Reserve a 3:2
    // slot (typical landscape photo / fixture PNG) until decode reports the
    // real intrinsic aspect, so the first laid-out size is already non-zero.
    var aspect by remember(model) { mutableStateOf(1.5f) }
    AsyncImage(
        model = coilModel,
        contentDescription = contentDescription,
        modifier = modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = 120.dp)
            .aspectRatio(aspect)
            .shimmer(isLoading = loading && !loaded)
            .clickable {
                showImageViewer = true
            },
        contentScale = contentScale,
        alpha = alpha,
        alignment = alignment,
        onLoading = {
            loading = true
        },
        onSuccess = { state ->
            loading = false
            loaded = true
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                aspect = size.width / size.height
            }
        },
        onError = {
            loading = false
            if (BuildConfig.DEBUG) {
                android.util.Log.w(
                    "AmberMarkdownImage",
                    "coil error model=$model data=${coilImageData(model)}",
                )
            }
        },
    )
    if (showImageViewer) {
        ImagePreviewDialog(images = listOf(model ?: "")) {
            showImageViewer = false
        }
    }
}
