package com.etkallem.english

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.net.Uri
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import android.speech.tts.Voice
import java.util.Locale

/** HTML lesson content is bundled locally. No remote pages are loaded. */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: Speech? = null
    @Volatile private var voicesJson = "[]"
    private var selectedVoiceName: String? = null
    private val recognitionRequest = 3142
    private val transcriptRequest = 3143
    private var pendingFiles: ValueCallback<Array<Uri>>? = null

    private data class Speech(val phrase: String, val speed: Float, val token: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 32, 58)
        window.navigationBarColor = android.graphics.Color.rgb(246, 248, 251)
        if (Build.VERSION.SDK_INT >= 26) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = false
        web.settings.setSupportZoom(false)
        web.settings.allowFileAccessFromFileURLs = false
        web.settings.allowUniversalAccessFromFileURLs = false
        web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                pendingFiles?.onReceiveValue(null)
                pendingFiles = filePathCallback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES,
                        arrayOf("text/plain", "text/vtt", "application/x-subrip", "application/octet-stream"))
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, transcriptRequest)
                    true
                } catch (e: Exception) {
                    pendingFiles?.onReceiveValue(null)
                    pendingFiles = null
                    false
                }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return request?.url?.toString()?.startsWith("file:///android_asset/") != true
            }
        }
        web.addJavascriptInterface(VoiceBridge(), "AndroidTTS")
        setContentView(web)
        tts = TextToSpeech(applicationContext, this)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langStatus = tts?.setLanguage(Locale.US)
            ttsReady = langStatus != TextToSpeech.LANG_MISSING_DATA &&
                langStatus != TextToSpeech.LANG_NOT_SUPPORTED
            if (ttsReady) {
                val available = (tts?.voices ?: emptySet<Voice>())
                    .filter { it.locale.language == "en" }
                    .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { if (it.locale.country == "US") 0 else 1 }
                        .thenBy { it.name })
                val chosen = getSharedPreferences("voice_preferences", MODE_PRIVATE)
                    .getString("chosenVoice", null)
                val match = available.firstOrNull { it.name == chosen }
                    ?: available.firstOrNull()
                if (match != null && tts?.setVoice(match) == TextToSpeech.SUCCESS)
                    selectedVoiceName = match.name
                val voicesArray = JSONArray()
                available.forEach { voice ->
                    val item = JSONObject()
                    item.put("id", voice.name)
                    item.put("locale", voice.locale.toLanguageTag())
                    item.put("quality", voice.quality)
                    item.put("network", voice.isNetworkConnectionRequired)
                    voicesArray.put(item)
                }
                voicesJson = voicesArray.toString()
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { notifyComplete(it) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { notifyComplete(it) }
                }
            })
            if (ttsReady) pending?.let { doSpeak(it) }
            else pending?.let { notifyComplete(it.token) }
        } else {
            pending?.let { notifyComplete(it.token) }
        }
    }

    private fun notifyComplete(token: String) {
        runOnUiThread {
            if (::web.isInitialized) {
                web.evaluateJavascript(
                    "window.onNativeTtsDone && window.onNativeTtsDone(" +
                        JSONObject.quote(token) + ")", null)
            }
        }
    }

    private fun doSpeak(speech: Speech) {
        if (!ttsReady) {
            pending = speech
            return
        }
        pending = null
        tts?.setSpeechRate(speech.speed.coerceIn(0.5f, 1.5f))
        val result = tts?.speak(speech.phrase, TextToSpeech.QUEUE_FLUSH, Bundle(), speech.token)
        if (result != TextToSpeech.SUCCESS) notifyComplete(speech.token)
    }

    inner class VoiceBridge {
        @JavascriptInterface
        fun playDriveEpisode(id: String, title: String) {
            if (!Regex("^[A-Za-z0-9_-]{10,80}$").matches(id)) return
            runOnUiThread {
                val intent = Intent(this@MainActivity, VideoActivity::class.java)
                intent.putExtra("drive_id", id)
                intent.putExtra("video_title", title.take(100))
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun playEpisode(id: String, title: String) {
            if (!Regex("^[A-Za-z0-9_-]{11}$").matches(id)) return
            runOnUiThread {
                val intent = Intent(this@MainActivity, VideoActivity::class.java)
                intent.putExtra("video_id", id)
                intent.putExtra("video_title", title.take(100))
                startActivity(intent)
            }
        }

        private fun transcriptFile() = File(filesDir, "etkallem_imported_dialogues_v1.json")

        @JavascriptInterface
        fun loadScripts(): String = try {
            val file = transcriptFile()
            if (file.exists() && file.length() <= 16_000_000) file.readText(Charsets.UTF_8)
            else "[]"
        } catch (e: Exception) {
            "[]"
        }

        @JavascriptInterface
        fun saveScripts(value: String): Boolean = try {
            if (value.length > 14_000_000 || !value.trimStart().startsWith("[")) false
            else {
                org.json.JSONArray(value)
                val file = transcriptFile()
                val temp = File(filesDir, "etkallem_imported_dialogues_tmp.json")
                temp.writeText(value, Charsets.UTF_8)
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
                true
            }
        } catch (e: Exception) {
            false
        }
        @JavascriptInterface
        fun getVoices(): String = voicesJson

        @JavascriptInterface
        fun currentVoice(): String = selectedVoiceName ?: ""

        @JavascriptInterface
        fun chooseVoice(name: String) {
            runOnUiThread {
                val found = tts?.voices?.firstOrNull { it.name == name && it.locale.language == "en" }
                if (found != null && tts?.setVoice(found) == TextToSpeech.SUCCESS) {
                    selectedVoiceName = name
                    getSharedPreferences("voice_preferences", MODE_PRIVATE)
                        .edit().putString("chosenVoice", name).apply()
                }
            }
        }
        @JavascriptInterface
        fun speak(text: String, rate: Double, token: String) {
            runOnUiThread {
                doSpeak(Speech(text.take(800), rate.toFloat(), token))
            }
        }
        @JavascriptInterface
        fun stop() {
            runOnUiThread {
                tts?.stop()
                pending = null
            }
        }
        @JavascriptInterface
        fun listen() {
            runOnUiThread {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                             RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak English now / اتكلم بالإنجليزي")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, recognitionRequest)
                } catch (e: ActivityNotFoundException) {
                    recognitionError("خدمة التعرف على الكلام غير مثبتة على الجهاز")
                } catch (e: Exception) {
                    recognitionError("تعذر تشغيل التدريب الصوتي")
                }
            }
        }
    }

    @Deprecated("Uses Activity result for compatibility with API 24+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == transcriptRequest) {
            val picked = mutableListOf<Uri>()
            if (resultCode == RESULT_OK) {
                data?.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) picked.add(clip.getItemAt(i).uri)
                }
                data?.data?.let { if (it !in picked) picked.add(it) }
            }
            pendingFiles?.onReceiveValue(if (picked.isEmpty()) null else picked.toTypedArray())
            pendingFiles = null
            return
        }
        if (requestCode != recognitionRequest) return
        if (resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            web.evaluateJavascript("window.onNativeRecognition(" +
                JSONObject.quote(result) + ")", null)
        } else {
            recognitionError("لم يتم تسجيل صوت. حاول مرة أخرى")
        }
    }

    private fun recognitionError(message: String) {
        web.evaluateJavascript("window.onNativeRecognitionError(" +
            JSONObject.quote(message) + ")", null)
    }

    @Deprecated("Uses JS back handler for lesson navigation")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onSystemBack ? window.onSystemBack() : false") {
            if (it == "false") finish()
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        pendingFiles?.onReceiveValue(null)
        pendingFiles = null
        if (::web.isInitialized) {
            web.removeJavascriptInterface("AndroidTTS")
            web.destroy()
        }
        super.onDestroy()
    }
}
