package com.example.videotomp3

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var fetchJob: Job? = null
    private var debounceJob: Job? = null
    // Cached audio formats from yt-dlp extract_info; each map contains keys: format_id, ext, abr, acodec, vcodec, language, format_note
    private var availableAudioFormats: List<Map<String, String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val convertButton = findViewById<Button>(R.id.convertButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val qualityGroup = findViewById<RadioGroup>(R.id.qualityGroup)
        val languageGroup = findViewById<RadioGroup>(R.id.languageGroup)
        val previewCard = findViewById<CardView>(R.id.previewCard)
        val videoThumbnail = findViewById<ImageView>(R.id.videoThumbnail)
        val videoTitle = findViewById<TextView>(R.id.videoTitle)
        val videoUploader = findViewById<TextView>(R.id.videoUploader)
        val videoDuration = findViewById<TextView>(R.id.videoDuration)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Auto-fetch video info with debouncing
        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                debounceJob?.cancel()
                debounceJob = lifecycleScope.launch {
                    delay(800) // Wait for user to stop typing
                    val url = s?.toString()?.trim() ?: ""
                    if (url.isNotEmpty()) {
                        fetchVideoInfo(url, statusText, previewCard, videoThumbnail, videoTitle, videoUploader, videoDuration)
                    }
                }
            }
        })

        // Download
        convertButton.setOnClickListener {
            val url = urlInput.text.toString().trim()

            if (url.isEmpty()) {
                statusText.text = "Please enter a video URL"
                return@setOnClickListener
            }

            val quality = when (qualityGroup.checkedRadioButtonId) {
                R.id.qualityLow    -> "low"
                R.id.qualityMedium -> "medium"
                else               -> "high"
            }

            val selectedLanguage = when (languageGroup.checkedRadioButtonId) {
                R.id.languageEnglish -> "English"
                else -> "Original source"
            }
            val chosenFormatId = pickFormatForLanguage(selectedLanguage, availableAudioFormats, quality)

            val intent = Intent(this, DownloadService::class.java)
            intent.putExtra(DownloadService.EXTRA_URL, url)
            intent.putExtra(DownloadService.EXTRA_QUALITY, quality)
            if (!chosenFormatId.isNullOrEmpty()) intent.putExtra(DownloadService.EXTRA_FORMAT, chosenFormatId)
            startForegroundService(intent)

            statusText.text = "Download started (${if (chosenFormatId == null) quality else selectedLanguage})!\nCheck notification bar."
        }
    }

    private fun updateLanguageRadioVisibility(formats: List<Map<String, String>>?) {
        val englishRadio = findViewById<android.widget.RadioButton>(R.id.languageEnglish)
        val originalRadio = findViewById<android.widget.RadioButton>(R.id.languageOriginal)
        val englishAvailable = formats.orEmpty().any { format ->
            val language = format["language"] ?: ""
            language.startsWith("en", ignoreCase = true) || language.startsWith("English", ignoreCase = true)
        }
        val originalLanguage = detectOriginalLanguage(formats)

        englishRadio.visibility = if (englishAvailable && originalLanguage != null && !originalLanguage.startsWith("en", ignoreCase = true)) View.VISIBLE else View.GONE
        if (englishRadio.visibility == View.GONE) {
            originalRadio.isChecked = true
        }
    }

    private fun detectOriginalLanguage(formats: List<Map<String, String>>?): String? {
        if (formats.isNullOrEmpty()) return null

        val audioFormats = formats.filter {
            val language = it["language"] ?: ""
            language.isNotBlank() && (it["acodec"] ?: "") != "none"
        }
        if (audioFormats.isEmpty()) return null

        val nonEnglish = audioFormats.filterNot { it["language"].orEmpty().startsWith("en", ignoreCase = true) }
        val selected = if (nonEnglish.isNotEmpty()) {
            nonEnglish.maxByOrNull { (it["abr"] ?: "0").toDoubleOrNull() ?: 0.0 }
        } else {
            audioFormats.maxByOrNull { (it["abr"] ?: "0").toDoubleOrNull() ?: 0.0 }
        }

        return selected?.get("language")
    }

    private fun pickFormatForLanguage(selection: String, formats: List<Map<String, String>>?, quality: String): String? {
        if (formats.isNullOrEmpty()) return null

        val originalLanguage = detectOriginalLanguage(formats)
        val targetLanguage = when (selection) {
            "English" -> "en"
            else -> originalLanguage ?: "en"
        }

        val preferredExtOrder = listOf("m4a", "mp4", "webm")
        var candidates = formats.filter {
            val language = it["language"] ?: ""
            val acodec = it["acodec"] ?: ""
            acodec != "none" && language.startsWith(targetLanguage, ignoreCase = true)
        }

        if (candidates.isEmpty()) return null

        // Exclude m3u8 HLS formats (233, 234, etc.) - they're fragmented and slow
        // Prefer formats with direct HTTP/HTTPS downloads (139-140, 249-251, etc.)
        val nonHlsFormats = candidates.filter { 
            val formatId = (it["format_id"] ?: "").toIntOrNull() ?: 999
            // Exclude 233-234 range (HLS), keep 139-140, 249-251, etc.
            formatId !in (233..234)
        }
        
        if (nonHlsFormats.isNotEmpty()) {
            candidates = nonHlsFormats
        }

        val selected = when (quality) {
            "low" -> candidates.minByOrNull {
                val bitrate = (it["abr"] ?: "0").toDoubleOrNull() ?: 0.0
                val extIndex = preferredExtOrder.indexOf(it["ext"] ?: "")
                val extWeight = if (extIndex == -1) 999 else extIndex
                bitrate * 1000 + extWeight
            }
            "medium" -> candidates.minByOrNull {
                val bitrate = (it["abr"] ?: "0").toDoubleOrNull() ?: 0.0
                val extIndex = preferredExtOrder.indexOf(it["ext"] ?: "")
                val extWeight = if (extIndex == -1) 999 else extIndex
                kotlin.math.abs(bitrate - 128.0) * 1000 + extWeight
            }
            else -> candidates.maxByOrNull {
                val bitrate = (it["abr"] ?: "0").toDoubleOrNull() ?: 0.0
                val extIndex = preferredExtOrder.indexOf(it["ext"] ?: "")
                val extWeight = if (extIndex == -1) 999 else extIndex
                bitrate * 1000 - extWeight
            }
        }

        val selected_id = selected?.get("format_id") ?: "none"
        android.util.Log.d("MainActivity", "Selected format ID: $selected_id (language: $targetLanguage, quality: $quality)")
        
        return selected?.get("format_id")
    }

    private fun fetchVideoInfo(
        url: String,
        statusText: TextView,
        previewCard: CardView,
        videoThumbnail: ImageView,
        videoTitle: TextView,
        videoUploader: TextView,
        videoDuration: TextView
    ) {
        // Cancel previous fetch
        fetchJob?.cancel()
        
        statusText.text = "Fetching video info..."
        previewCard.visibility = View.GONE

        fetchJob = lifecycleScope.launch {
            try {
                val formatsList = mutableListOf<Map<String, String>>()
                val info = withContext(Dispatchers.IO) {
                    val py = Python.getInstance()
                    val ytdlp = py.getModule("yt_dlp")
                    val builtins = py.builtins

                    val dict = builtins.callAttr("dict")
                    dict.callAttr("__setitem__", "quiet", true)
                    dict.callAttr("__setitem__", "skip_download", true)
                    dict.callAttr("__setitem__", "no_color", true)

                    val ydl = ytdlp.callAttr("YoutubeDL", dict)
                    val rawInfo = ydl.callAttr("extract_info", url, false)

                    val title = try { rawInfo.callAttr("get", "title").toString() } catch (e: Exception) { "Unknown" }
                    val duration = try { rawInfo.callAttr("get", "duration").toString().toIntOrNull() ?: 0 } catch (e: Exception) { 0 }
                    val thumbnail = try { rawInfo.callAttr("get", "thumbnail").toString() } catch (e: Exception) { "" }
                    val uploader = try { rawInfo.callAttr("get", "uploader").toString() } catch (e: Exception) { "Unknown" }

                    // Extract formats list (iterate Python list)
                    try {
                        val pyFormats = rawInfo.callAttr("get", "formats")
                        val len = try { pyFormats.callAttr("__len__").toString().toInt() } catch (e: Exception) { 0 }
                        for (i in 0 until len) {
                            try {
                                val f = pyFormats.callAttr("__getitem__", i)
                                val fid = try { f.callAttr("get", "format_id").toString() } catch (e: Exception) { "" }
                                val ext = try { f.callAttr("get", "ext").toString() } catch (e: Exception) { "" }
                                val vcodec = try { f.callAttr("get", "vcodec").toString() } catch (e: Exception) { "" }
                                val acodec = try { f.callAttr("get", "acodec").toString() } catch (e: Exception) { "" }
                                val abr = try { f.callAttr("get", "abr").toString() } catch (e: Exception) { "" }
                                val language = try { f.callAttr("get", "language").toString() } catch (e: Exception) { "" }
                                val note = try { f.callAttr("get", "format_note").toString() } catch (e: Exception) { "" }

                                // Only keep audio-only or audio-dominant formats
                                if (acodec != "none") {
                                    formatsList.add(mapOf(
                                        "format_id" to fid,
                                        "ext" to ext,
                                        "vcodec" to vcodec,
                                        "acodec" to acodec,
                                        "abr" to abr,
                                        "language" to language,
                                        "format_note" to note
                                    ))
                                }
                            } catch (_: Exception) { }
                        }
                    } catch (_: Exception) { }

                    val minutes = duration / 60
                    val seconds = duration % 60
                    val durationStr = "%d:%02d".format(minutes, seconds)

                    mapOf(
                        "title" to title,
                        "duration" to durationStr,
                        "thumbnail" to thumbnail,
                        "uploader" to uploader
                    )
                }

                // Cache available audio formats for chooser
                availableAudioFormats = formatsList

                updateLanguageRadioVisibility(formatsList)

                // Show preview card
                previewCard.visibility = View.VISIBLE
                videoTitle.text = info["title"]
                videoUploader.text = "📺 ${info["uploader"]}"
                videoDuration.text = "⏱ ${info["duration"]}"

                Glide.with(this@MainActivity)
                    .load(info["thumbnail"])
                    .into(videoThumbnail)

                statusText.text = "Ready to download!"

            } catch (e: Exception) {
                statusText.text = "❌ Error: ${e.message}"
                previewCard.visibility = View.GONE
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.all {
                it == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (!allGranted) {
                findViewById<TextView>(R.id.statusText).text =
                    "⚠️ Notification permission denied. Downloads will work but you won't see progress."
            }
        }
    }
}