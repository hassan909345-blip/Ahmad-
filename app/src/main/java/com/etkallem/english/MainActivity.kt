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
        web.webChromeClient = WebChromeClient()
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
        if (::web.isInitialized) {
            web.removeJavascriptInterface("AndroidTTS")
            web.destroy()
        }
        super.onDestroy()
    }
}
