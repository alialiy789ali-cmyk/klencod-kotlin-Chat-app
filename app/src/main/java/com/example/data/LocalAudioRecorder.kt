package com.example.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/** Records a voice note into the app's private files directory. */
class LocalAudioRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val outputDirectory = File(appContext.filesDir, "voice-notes").apply { mkdirs() }
    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null

    @Synchronized
    fun start(): Boolean {
        if (recorder != null) return false
        val file = File(outputDirectory, "voice-${UUID.randomUUID()}.m4a")
        var instance: MediaRecorder? = null
        return try {
            val activeRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            instance = activeRecorder
            activeRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            activeRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            activeRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            activeRecorder.setAudioEncodingBitRate(96_000)
            activeRecorder.setAudioSamplingRate(44_100)
            activeRecorder.setOutputFile(file.absolutePath)
            activeRecorder.prepare()
            activeRecorder.start()
            recorder = activeRecorder
            activeFile = file
            true
        } catch (_: Exception) {
            runCatching { instance?.release() }
            file.delete()
            false
        }
    }

    @Synchronized
    fun stop(): String? {
        val instance = recorder ?: return null
        val file = activeFile
        recorder = null
        activeFile = null
        return try {
            instance.stop()
            instance.release()
            file?.takeIf { it.exists() && it.length() > 0L }?.absolutePath
        } catch (_: Exception) {
            runCatching { instance.release() }
            file?.delete()
            null
        }
    }

    @Synchronized
    fun cancel() {
        val instance = recorder
        val file = activeFile
        recorder = null
        activeFile = null
        if (instance != null) {
            runCatching { instance.stop() }
            runCatching { instance.release() }
        }
        file?.delete()
    }

}
