package com.example.videotomp3

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import java.io.File

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIF_ID = 1
        const val EXTRA_URL = "url"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_FORMAT = "format"
    }

    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: run {
            android.util.Log.e("DownloadService", "No URL provided!")
            return START_NOT_STICKY
        }
        val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "low"
        val selectedFormat = intent.getStringExtra(EXTRA_FORMAT) // optional exact format id
        android.util.Log.d("DownloadService", "Service started with URL: $url, quality: $quality, format: $selectedFormat")

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Preparing download...", 0))
        android.util.Log.d("DownloadService", "Foreground started, launching coroutine")

        scope.launch {
            try {
                android.util.Log.d("DownloadService", "Starting download coroutine")
                val finalPath = download(url, quality, selectedFormat)
                showCompletedNotification(finalPath)
                kotlinx.coroutines.delay(1000)
            } catch (e: Exception) {
                android.util.Log.e("DownloadService", "Download failed: ${e.message}", e)
                showErrorNotification(e.message ?: "Unknown error")
                kotlinx.coroutines.delay(1000)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun download(url: String, quality: String, selectedFormat: String?): String {
        val py = Python.getInstance()
        val ytdlp = py.getModule("yt_dlp")
        val builtins = py.builtins
        val hooks = py.getModule("hooks")

        val outputDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        ).absolutePath

        val dateStr = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd_HHmmss"))

        val dict = builtins.callAttr("dict")

        // If caller provided an exact yt-dlp format id, use it. Otherwise use quality-preferred format string.
        if (!selectedFormat.isNullOrEmpty()) {
            dict.callAttr("__setitem__", "format", selectedFormat)
        } else {
            val fmt = when (quality) {
                "low"  -> "worstaudio[vcodec=none][ext=webm]/worstaudio/worst"
                else   -> "bestaudio[vcodec=none][ext=m4a]/bestaudio[vcodec=none][ext=mp4]/bestaudio[vcodec=none][ext=webm]/bestaudio/best"
            }
            dict.callAttr("__setitem__", "format", fmt)
        }

        dict.callAttr("__setitem__", "outtmpl", "$outputDir/%(title).30s_${dateStr}.%(ext)s")
        dict.callAttr("__setitem__", "noplaylist", true)
        dict.callAttr("__setitem__", "quiet", true)

        val headers = builtins.callAttr("dict")
        headers.callAttr("__setitem__", "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        dict.callAttr("__setitem__", "http_headers", headers)

        // Progress hook
        val callback = object : ProgressCallback {
            override fun onProgress(percent: Int) {
                updateProgress(percent, "Downloading... $percent%")
            }
        }
        val hook = hooks.callAttr("make_progress_hook", callback)
        val hookList = builtins.callAttr("list")
        hookList.callAttr("append", hook)
        dict.callAttr("__setitem__", "progress_hooks", hookList)

        try { py.getModule("patch") } catch (e: Exception) { }
        val ydl = ytdlp.callAttr("YoutubeDL", dict)
        ydl.callAttr("__enter__")

        try {
            val pyList = builtins.callAttr("list")
            pyList.callAttr("append", url)
            ydl.callAttr("download", pyList)
        } finally {
            ydl.callAttr("__exit__", null, null, null)
        }

        updateProgress(100, "Converting to MP3...")
        val downloadedFile = File(outputDir)
            .listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }

        var finalPath = outputDir
        if (downloadedFile != null) {
            val actualAudioFile = if (downloadedFile.name.endsWith(".mp3", ignoreCase = true)) {
                downloadedFile
            } else {
                val mp3File = File(outputDir, downloadedFile.nameWithoutExtension + ".mp3")
                val converter = py.getModule("convert_audio")
                val convertedPath = converter.callAttr("convert_to_mp3", downloadedFile.absolutePath, mp3File.absolutePath, quality)
                val convertedFile = File(convertedPath.toString())
                if (!convertedFile.exists()) {
                    throw IllegalStateException("FFmpeg conversion did not create output file")
                }
                downloadedFile.delete()
                convertedFile
            }

            android.util.Log.d("DownloadService", "Saved as: ${actualAudioFile.absolutePath}")
            scanFile(actualAudioFile.absolutePath)
            finalPath = actualAudioFile.absolutePath
        }

        return finalPath
    }

    fun updateProgress(percent: Int, title: String = "Downloading...") {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(title, percent))
    }

    private fun buildNotification(text: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video to MP3")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()

    private fun showCompletedNotification(finalPath: String) {
        val manager = getSystemService(NotificationManager::class.java)

        val file = File(finalPath)
        val intent = try {
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            // Fallback to open Downloads view
            Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("✅Saved to: $finalPath\n\nTap to open file")
            )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(2, notif)
    }

    private fun showErrorNotification(error: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video to MP3 - Failed")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("❌ Failed: $error\n\nTap to retry or open app for details")
            )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(false)
            .setOngoing(false)
            .build()
        manager.notify(NOTIF_ID, notif)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MP3 Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun scanFile(filePath: String) {
        val mimeType = when {
            filePath.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            filePath.endsWith(".webm", ignoreCase = true) -> "audio/webm"
            filePath.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            filePath.endsWith(".opus", ignoreCase = true) -> "audio/opus"
            else -> "audio/mpeg"
        }

        android.media.MediaScannerConnection.scanFile(
            applicationContext,
            arrayOf(filePath),
            arrayOf(mimeType),
            null
        )
    }

    fun fetchVideoInfo(url: String): Map<String, String> {
        val py = Python.getInstance()
        val ytdlp = py.getModule("yt_dlp")
        val builtins = py.builtins

        val dict = builtins.callAttr("dict")
        dict.callAttr("__setitem__", "quiet", true)
        dict.callAttr("__setitem__", "skip_download", true)
        dict.callAttr("__setitem__", "no_color", true)

        val ydl = ytdlp.callAttr("YoutubeDL", dict)
        val info = ydl.callAttr("extract_info", url, false)

        val title = try { info.callAttr("get", "title").toString() } catch (e: Exception) { "Unknown" }
        val duration = try { info.callAttr("get", "duration").toString().toIntOrNull() ?: 0 } catch (e: Exception) { 0 }
        val thumbnail = try { info.callAttr("get", "thumbnail").toString() } catch (e: Exception) { "" }
        val uploader = try { info.callAttr("get", "uploader").toString() } catch (e: Exception) { "Unknown" }

        val minutes = (duration as Int) / 60
        val seconds = duration % 60
        val durationStr = "%d:%02d".format(minutes, seconds)

        return mapOf(
            "title" to title,
            "duration" to durationStr,
            "thumbnail" to thumbnail,
            "uploader" to uploader
        )
    }
}