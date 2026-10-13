package com.etkallem.english

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView

/**
 * A separate activity for external video playback. No JavascriptInterface is attached:
 * third-party frames never have access to the student's offline lesson data.
 * Online videos are hosted by their original publishers and are not copied into the APK.
 */
class VideoActivity : Activity() {
    private var videoId: String? = null
    private var driveId: String? = null
    private var webVideo: WebView? = null
    private var localVideo: VideoView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var root: FrameLayout
    private lateinit var slot: FrameLayout
    private val localVideoPicker = 6014

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    private fun uiText(value: String, size: Float, color: Int = Color.BLACK): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(value: String, action: () -> Unit): Button =
        Button(this).apply { text = value; isAllCaps = false; setOnClickListener { action() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inputId = intent.getStringExtra("video_id")
        videoId = inputId?.takeIf { Regex("^[A-Za-z0-9_-]{11}$").matches(it) }
        driveId = intent.getStringExtra("drive_id")?.takeIf { Regex("^[A-Za-z0-9_-]{10,80}$").matches(it) }
        val episodeTitle = intent.getStringExtra("video_title")?.take(90) ?: "تعلم الإنجليزية بالفيديو"

        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(248, 250, 253)) }
        val scroller = ScrollView(this).apply { isFillViewport = true }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(20))
        }
        scroller.addView(column)
        root.addView(scroller)
        setContentView(root)

        column.addView(uiText("🎬 $episodeTitle", 20f, Color.rgb(12, 35, 65)))
        column.addView(uiText(if (driveId != null) "هذا ملفك الأصلي على Google Drive. تشغيل الفيديو يحتاج الإنترنت والوصول إلى حساب Google الذي يملك الملف." else "الفيديو من يوتيوب ويحتاج اتصالًا بالإنترنت. العرض داخل التطبيق متاح عندما يسمح صاحب الفيديو بالتضمين.", 13f, Color.rgb(82, 100, 115)))

        slot = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        column.addView(slot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(252)))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (driveId != null) {
            buttons.addView(button("📁 افتح الحلقة الأصلية في تطبيق Google Drive") { openInDrive() })
        } else {
            buttons.addView(button("🔗 افتح الفيديو على يوتيوب إذا لم يعمل هنا") { openInYouTube() })
        }
        buttons.addView(button("📁 شغّل فيديو عندك على الهاتف (MP4 / WebM)") { selectLocalVideo() })
        buttons.addView(button("🎬 شغّل الحلقة أونلاين مجددًا") { showYouTubePlayer() })
        buttons.addView(button("⬅️ العودة للدروس") { finish() })
        column.addView(buttons)
        column.addView(uiText("يمكنك إيقاف الفيديو وتكرار أي مشهد. ارفع نص الترجمة SRT/VTT من قسم الحوارات في التطبيق للتدرب على جمل الحلقة.", 13f, Color.rgb(82, 100, 115)))

        if (videoId == null && driveId == null) {
            slot.addView(uiText("اختر فيديو من جهازك لتشغيله.", 17f, Color.WHITE))
        } else {
            showYouTubePlayer()
        }
    }

    private fun showYouTubePlayer() {
        localVideo?.stopPlayback()
        localVideo = null
        webVideo?.destroy()
        webVideo = null
        slot.removeAllViews()
        val video = videoId
        val drive = driveId
        if (video == null && drive == null) return
        val browser = WebView(this)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.mediaPlaybackRequiresUserGesture = true
        browser.settings.allowFileAccess = false
        browser.settings.allowContentAccess = false
        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || fullscreenView != null) return
                fullscreenView = view
                fullscreenCallback = callback
                browser.visibility = View.INVISIBLE
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
            }
            override fun onHideCustomView() { closeFullscreen() }
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null || !request.isForMainFrame) return false
                val uri = request.url
                if (uri.host == "www.youtube-nocookie.com" && uri.path?.startsWith("/embed/") == true) return false
                if (drive != null && uri.scheme == "https" && uri.host in listOf("drive.google.com", "accounts.google.com", "docs.google.com")) return false
                if (uri.scheme == "https" && uri.host in listOf("www.youtube.com", "m.youtube.com")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
                }
                return true
            }
        }
        slot.addView(browser, FrameLayout.LayoutParams(-1, -1))
        webVideo = browser
        if (drive != null) browser.loadUrl("https://drive.google.com/file/d/$drive/preview")
        else browser.loadUrl("https://www.youtube-nocookie.com/embed/$video?playsinline=1&rel=0")
    }

    private fun openInDrive() {
        val id = driveId ?: return
        val uri = Uri.parse("https://drive.google.com/file/d/$id/view")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
    }

    private fun openInYouTube() {
        val id = videoId ?: return
        val uri = Uri.parse("https://www.youtube.com/watch?v=$id")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: Exception) { }
    }

    private fun selectLocalVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, localVideoPicker)
        } catch (_: Exception) { }
    }

    @Deprecated("Uses ACTION_OPEN_DOCUMENT with activity results on Android 7+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != localVideoPicker || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        webVideo?.onPause()
        webVideo?.destroy()
        webVideo = null
        slot.removeAllViews()
        val player = VideoView(this).apply {
            val controller = MediaController(this@VideoActivity)
            controller.setAnchorView(this)
            setMediaController(controller)
            setVideoURI(uri)
            setOnPreparedListener { start() }
            setOnErrorListener { _, _, _ ->
                android.widget.Toast.makeText(this@VideoActivity, "تعذر تشغيل صيغة الفيديو على هذا الهاتف", android.widget.Toast.LENGTH_LONG).show()
                true
            }
        }
        slot.addView(player, FrameLayout.LayoutParams(-1, -1))
        localVideo = player
    }

    private fun closeFullscreen() {
        fullscreenView?.let { root.removeView(it) }
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        webVideo?.visibility = View.VISIBLE
    }

    @Deprecated("Native full-screen support")
    override fun onBackPressed() {
        if (fullscreenView != null) closeFullscreen() else super.onBackPressed()
    }

    override fun onDestroy() {
        closeFullscreen()
        localVideo?.stopPlayback()
        webVideo?.let {
            it.stopLoading()
            it.loadUrl("about:blank")
            it.destroy()
        }
        super.onDestroy()
    }
}
