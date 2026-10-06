package com.creole.translator.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorder(private val context: Context) {

    companion object {
        // Accuracy falls off with clip length: across 3,632 voice samples, translations
        // scored confidence <=3 for 21% of <10s clips, 43% at 10-20s, 57%+ past 20s.
        // So recordings stop at MAX_DURATION_MS, and the bar warns after WARN_AFTER_MS.
        // Mirrors iOS AudioRecorder.maxDuration / warnAfter / minDuration.
        const val MAX_DURATION_MS = 30_000L
        const val WARN_AFTER_MS = 20_000L
        // Shorter clips are almost always accidental taps; don't send them.
        const val MIN_DURATION_MS = 600L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startedAtMs = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    /**
     * Called on the main thread when a phone call or another app takes the mic or
     * audio focus mid-recording. The clip has already been discarded by then.
     */
    var onInterrupted: (() -> Unit)? = null

    val isRecording: Boolean get() = mediaRecorder != null

    /** Milliseconds since the current recording started, or 0 when not recording. */
    val elapsedMs: Long get() = if (isRecording) SystemClock.elapsedRealtime() - startedAtMs else 0L

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        // A ducking request (navigation prompt, notification) doesn't stop capture;
        // anything else means a call or another recorder/player wants the audio.
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            interrupt()
        }
    }

    fun startRecording(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(context.cacheDir, "recording_$timestamp.m4a")
        currentOutputFile = outputFile

        requestAudioFocus()
        val recorder = createMediaRecorder()
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Release immediately so a failed prepare()/start() doesn't leak the mic
            // and block every subsequent attempt until this object is GC'd.
            recorder.release()
            abandonAudioFocus()
            currentOutputFile = null
            throw e
        }

        mediaRecorder = recorder
        startedAtMs = SystemClock.elapsedRealtime()
        registerSilencedCallback(recorder)
        return outputFile
    }

    /**
     * Stops the recording and returns the clip, or null if nothing was recording,
     * the recorder failed, or the clip was shorter than MIN_DURATION_MS (that file
     * is deleted).
     */
    fun stopRecording(): File? {
        val recorder = mediaRecorder ?: return null
        val duration = elapsedMs
        val file = currentOutputFile
        val stopped = try {
            recorder.stop()
            true
        } catch (e: Exception) {
            // stop() throws when no audio was captured (e.g. an instant tap).
            false
        } finally {
            releaseRecorder(recorder)
        }
        if (!stopped || duration < MIN_DURATION_MS) {
            file?.delete()
            return null
        }
        return file
    }

    fun cancelRecording() {
        val recorder = mediaRecorder ?: return
        try { recorder.stop() } catch (_: Exception) {}
        releaseRecorder(recorder)
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    fun deleteRecording(file: File) {
        file.delete()
    }

    private fun interrupt() {
        if (!isRecording) return
        cancelRecording()
        onInterrupted?.invoke()
    }

    // Every stop path goes through here so the mic, audio focus, and the silenced
    // callback are always released together.
    private fun releaseRecorder(recorder: MediaRecorder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recordingCallback?.let { recorder.unregisterAudioRecordingCallback(it) }
        }
        recordingCallback = null
        recorder.release()
        mediaRecorder = null
        startedAtMs = 0L
        abandonAudioFocus()
    }

    // Android 10+ silences (rather than stops) our capture when a call or a
    // higher-priority app takes the mic, so a clip would just go quiet.
    private fun registerSilencedCallback(recorder: MediaRecorder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                if (recorder.activeRecordingConfiguration?.isClientSilenced == true) interrupt()
            }
        }
        recordingCallback = callback
        recorder.registerAudioRecordingCallback(ContextCompat.getMainExecutor(context), callback)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
