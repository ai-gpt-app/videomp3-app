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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val convertButton = findViewById<Button>(R.id.convertButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val qualityGroup = findViewById<RadioGroup>(R.id.qualityGroup)
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

            val intent = Intent(this, DownloadService::class.java)
            intent.putExtra(DownloadService.EXTRA_URL, url)
            intent.putExtra(DownloadService.EXTRA_QUALITY, quality)
            startForegroundService(intent)

            statusText.text = "Download started ($quality quality)!\nCheck notification bar."
        }
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