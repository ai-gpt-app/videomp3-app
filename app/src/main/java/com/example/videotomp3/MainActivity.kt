package com.example.videotomp3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()
        val urlInput = findViewById<EditText>(R.id.urlInput)
        val convertButton = findViewById<Button>(R.id.convertButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val qualityGroup = findViewById<RadioGroup>(R.id.qualityGroup)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

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

            // Request notification permission on Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1
                )
            }

            // Start background download service
            val intent = Intent(this, DownloadService::class.java)
            intent.putExtra(DownloadService.EXTRA_URL, url)
            intent.putExtra(DownloadService.EXTRA_QUALITY, quality)
            startForegroundService(intent)
            statusText.text = "Download started ($quality quality)! Check notification bar."
        }
    }

    private suspend fun runConversion(url: String, statusText: TextView) {
        withContext(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val ytdlp = py.getModule("yt_dlp")

                val outputDir = applicationContext.getExternalFilesDir(
                    android.os.Environment.DIRECTORY_MUSIC
                )?.absolutePath ?: applicationContext.filesDir.absolutePath

                updateUI(statusText, "Downloading...\nSaving to:\n$outputDir")

                val builtins = py.builtins
                val dict = builtins.callAttr("dict")
                dict.callAttr("__setitem__", "format", "bestaudio/best")
                dict.callAttr("__setitem__", "outtmpl", "$outputDir/%(title)s.%(ext)s")
                dict.callAttr("__setitem__", "noplaylist", true)
                dict.callAttr("__setitem__", "quiet", false)
                dict.callAttr("__setitem__", "no_warnings", false)

                // Add headers to mimic browser
                val headers = builtins.callAttr("dict")
                headers.callAttr("__setitem__", "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                headers.callAttr("__setitem__", "Accept-Language", "en-US,en;q=0.9")
                dict.callAttr("__setitem__", "http_headers", headers)

                // Use android client instead of web
                dict.callAttr("__setitem__", "extractor_args",
                    builtins.callAttr("dict").also { d ->
                        val clientList = builtins.callAttr("list")
                        clientList.callAttr("append", "android")
                        d.callAttr("__setitem__", "youtube",
                            builtins.callAttr("dict").also { inner ->
                                inner.callAttr("__setitem__", "player_client", clientList)
                            }
                        )
                    }
                )

                val ydl = ytdlp.callAttr("YoutubeDL", dict)
                ydl.callAttr("__enter__")

                try {
                    val pyList = builtins.callAttr("list")
                    pyList.callAttr("append", url)
                    ydl.callAttr("download", pyList)
                    updateUI(statusText, "✅ Done! File saved to:\n$outputDir")
                } finally {
                    ydl.callAttr("__exit__", null, null, null)
                }

            } catch (e: Exception) {
                updateUI(statusText, "❌ Error:\n${e.message}")
            }
        }
    }

    private suspend fun updateUI(textView: TextView, msg: String) {
        withContext(Dispatchers.Main) {
            textView.text = msg
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // Only needed on Android 13+
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