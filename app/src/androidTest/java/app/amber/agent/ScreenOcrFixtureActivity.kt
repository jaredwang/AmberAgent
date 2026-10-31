package app.amber.agent

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Isolated OCR fixture: WebView HTML or a custom [onDraw] canvas, both with
 * descendants hidden from Accessibility so bitmap OCR cannot be faked by AX labels.
 */
class ScreenOcrFixtureActivity : Activity() {
    @Volatile
    var pageReady: Boolean = false
        private set

    lateinit var targetView: View
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        instance = this
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        targetView = when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_CANVAS -> createCanvasView(this).also { view ->
                view.post { pageReady = true }
            }
            else -> createWebView(this) { pageReady = true }
        }
        root.addView(
            targetView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(root)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private class OcrCanvasView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.SANS_SERIF
            textAlign = Paint.Align.LEFT
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.WHITE)
            val size = minOf(48f * resources.displayMetrics.scaledDensity, width / 14f).coerceAtLeast(32f)
            paint.textSize = size
            val left = 32f * resources.displayMetrics.density
            var y = 120f * resources.displayMetrics.density
            val gap = size * 1.6f
            canvas.drawText(CHINESE, left, y, paint)
            y += gap
            canvas.drawText(LATIN, left, y, paint)
            y += gap
            canvas.drawText(CLICKABLE, left, y, paint)
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_WEBVIEW = "webview"
        const val MODE_CANVAS = "canvas"
        const val CHINESE = "琥珀验证完成"
        const val LATIN = "OCR-LATIN-MARK"
        const val CLICKABLE = "立即查看详情"
        @Volatile
        var instance: ScreenOcrFixtureActivity? = null
            private set

        fun clearInstance() {
            instance = null
        }

        fun createCanvasView(context: android.content.Context): View =
            OcrCanvasView(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                contentDescription = null
            }

        fun createWebView(context: android.content.Context, onReady: () -> Unit): WebView =
            WebView(context).apply {
                settings.javaScriptEnabled = false
                setBackgroundColor(Color.WHITE)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                contentDescription = null
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        postDelayed({ onReady() }, 250)
                    }
                }
                loadDataWithBaseURL(
                    "https://ocr.fixture.invalid/",
                    WEB_HTML,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        private val WEB_HTML = """
            <!doctype html>
            <html>
            <head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:48px;background:#ffffff;color:#000000;font-family:sans-serif">
              <p style="font-size:56px;font-weight:700;margin:0 0 24px">$CHINESE</p>
              <p style="font-size:48px;margin:0 0 24px">$LATIN</p>
              <a href="#click" style="font-size:48px;color:#0000cc">$CLICKABLE</a>
            </body>
            </html>
        """.trimIndent()
    }
}
