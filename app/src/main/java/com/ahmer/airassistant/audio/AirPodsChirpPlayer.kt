package com.ahmer.airassistant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin

enum class ChirpTarget {
    LEFT_ONLY,
    RIGHT_ONLY,
    BOTH
}

class AirPodsChirpPlayer {

    private val tag = "AirPodsChirpPlayer"
    private var isPlaying = false
    private var audioTrack: AudioTrack? = null
    private var chirpJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun isChirping(): Boolean = isPlaying

    /**
     * Starts playing a pulsating high-frequency beep through the selected earbud channel(s).
     */
    fun startChirp(target: ChirpTarget = ChirpTarget.BOTH, onFinished: (() -> Unit)? = null) {
        if (isPlaying) stopChirp()
        isPlaying = true

        chirpJob = scope.launch {
            try {
                val sampleRate = 44100
                val durationSeconds = 30 // Auto stop after 30 seconds for safety
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        android.media.AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                audioTrack?.play()

                // Generate audio samples: high frequency chirp (3000 Hz) with beep cadence (300ms on, 200ms off)
                val buffer = ShortArray(sampleRate / 2) // 0.5 second buffer
                val startTime = System.currentTimeMillis()

                while (isActive && isPlaying && (System.currentTimeMillis() - startTime) < (durationSeconds * 1000L)) {
                    for (i in buffer.indices step 2) {
                        val time = i.toDouble() / sampleRate
                        val isBeepActive = (time % 0.5) < 0.25 // Beep on for 250ms, off for 250ms
                        val sampleValue = if (isBeepActive) {
                            // Frequency modulated chirp between 2800 Hz and 3800 Hz
                            val freq = 3300.0 + 500.0 * sin(2.0 * Math.PI * 8.0 * time)
                            (sin(2.0 * Math.PI * freq * time) * Short.MAX_VALUE * 0.9).toInt().toShort()
                        } else {
                            0.toShort()
                        }

                        // Route to Left, Right, or Both channels
                        val leftSample = if (target == ChirpTarget.RIGHT_ONLY) 0.toShort() else sampleValue
                        val rightSample = if (target == ChirpTarget.LEFT_ONLY) 0.toShort() else sampleValue

                        buffer[i] = leftSample
                        if (i + 1 < buffer.size) {
                            buffer[i + 1] = rightSample
                        }
                    }

                    audioTrack?.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during audio chirp playback", e)
            } finally {
                stopChirp()
                withContext(Dispatchers.Main) {
                    onFinished?.invoke()
                }
            }
        }
    }

    fun stopChirp() {
        isPlaying = false
        chirpJob?.cancel()
        chirpJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping AudioTrack", e)
        }
        audioTrack = null
    }
}

