package com.example

import android.content.Context
import java.io.File

object BinaryExtractor {

    fun extractBinary(context: Context, assetPath: String, outputName: String): File {
        val outFile = File(context.filesDir, outputName)

        context.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Runtime.getRuntime().exec(arrayOf("chmod", "755", outFile.absolutePath)).waitFor()

        return outFile
    }
}