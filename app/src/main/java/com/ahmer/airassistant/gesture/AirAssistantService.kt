package com.ahmer.airassistant.gesture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ahmer.airassistant.AirAssistantApp
import com.ahmer.airassistant.MainActivity
import com.ahmer.airassistant.R
import com.ahmer.airassistant.assistant.AssistantLauncher
import com.ahmer.airassistant.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AirAssistantService : Service() {

    private val tag = "AirAssistantService"
    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var mediaSession: MediaSessionCompat? = null
    private lateinit var gestureDetector: GestureDetector
    private lateinit var assistantLauncher: AssistantLauncher
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: Any? = null // AudioFocusRequest on API26+
    private var silentAudioTrack: android.media.AudioTrack? = null

    // Fallback direct MEDIA_BUTTON receiver for devices where MediaSession routing fails
    private val mediaButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null) {
                Log.d(tag, "Direct BroadcastReceiver got key: $keyEvent")
                gestureDetector.processKeyEvent(keyEvent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service onCreate")

        assistantLauncher = AssistantLauncher(this)
        gestureDetector = GestureDetector(this, assistantLauncher)
        settingsRepository = SettingsRepository(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        settingsRepository.userSettingsFlow.onEach { settings ->
            gestureDetector.setConfiguredGesture(settings.triggerGesture)
        }.launchIn(scope)

        setupMediaSession()
        requestAudioFocus()
        startSilentAudioPlayback()
        registerMediaButtonReceiver()
        MediaEventBus.setServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Service onStartCommand action=${intent?.action}")

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Let AndroidX MediaButtonReceiver handle the intent first
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
        }

        return START_STICKY
    }

    private fun setupMediaSession() {
        val session = MediaSessionCompat(this, "AirAssistantSession").apply {
            @Suppress("DEPRECATION")
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            val mediaButtonReceiverIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, this@AirAssistantService, androidx.media.session.MediaButtonReceiver::class.java)
            val mediaButtonPendingIntent = PendingIntent.getBroadcast(
                this@AirAssistantService,
                0,
                mediaButtonReceiverIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setMediaButtonReceiver(mediaButtonPendingIntent)

            // Set real metadata so Android treats this as a genuine active media session
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "AirAssistant")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Listening for gestures...")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .build()
            setMetadata(metadata)

            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_STOP
                )
                // STATE_PLAYING is essential — Android only routes media buttons to PLAYING sessions
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
            setPlaybackState(stateBuilder.build())

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent != null) {
                        Log.d(tag, "MediaSession.Callback key: $keyEvent")
                        return gestureDetector.processKeyEvent(keyEvent)
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                override fun onPlay() {
                    Log.d(tag, "onPlay")
                    gestureDetector.processKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                }

                override fun onPause() {
                    Log.d(tag, "onPause")
                    gestureDetector.processKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
                }

                override fun onSkipToNext() {
                    Log.d(tag, "onSkipToNext")
                    gestureDetector.processKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
                }

                override fun onSkipToPrevious() {
                    Log.d(tag, "onSkipToPrevious")
                    gestureDetector.processKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                }
            })

            isActive = true
        }

        mediaSession = session
        Log.d(tag, "MediaSession active: ${session.isActive}")
    }

    private fun registerMediaButtonReceiver() {
        val filter = IntentFilter(Intent.ACTION_MEDIA_BUTTON).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        ContextCompat.registerReceiver(
            this,
            mediaButtonReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus() {
        // Request transient audio focus so Android routes media buttons to us
        // AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = we intercept buttons, music keeps playing quietly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { change ->
                    Log.d(tag, "AudioFocus change: $change")
                }
                .build()
            audioFocusRequest = req
            val result = audioManager.requestAudioFocus(req)
            Log.d(tag, "AudioFocus request result: $result")
        } else {
            audioManager.requestAudioFocus(
                { change -> Log.d(tag, "AudioFocus change (legacy): $change") },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (audioFocusRequest as? android.media.AudioFocusRequest)?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        }
    }

    private fun createNotification(): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, AirAssistantApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running_title))
            .setContentText(getString(R.string.service_running_desc))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        Log.d(tag, "Service onDestroy")
        serviceJob.cancel()
        try { unregisterReceiver(mediaButtonReceiver) } catch (e: Exception) { }
        stopSilentAudioPlayback()
        abandonAudioFocus()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        MediaEventBus.setServiceRunning(false)
        super.onDestroy()
    }

    private fun startSilentAudioPlayback() {
        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val bufferSize = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                if (bufferSize <= 0) return@launch

                val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.media.AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            android.media.AudioFormat.Builder()
                                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    android.media.AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        android.media.AudioTrack.MODE_STREAM
                    )
                }

                track.play()
                silentAudioTrack = track
                val silence = ByteArray(bufferSize)

                // Write silence so AudioFlinger marks this as an active playback session
                while (silentAudioTrack != null && silentAudioTrack?.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(silence, 0, silence.size)
                    kotlinx.coroutines.delay(1000)
                }
            } catch (e: Exception) {
                Log.w(tag, "Silent AudioTrack setup failed", e)
            }
        }
    }

    private fun stopSilentAudioPlayback() {
        try {
            silentAudioTrack?.let {
                if (it.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (ignored: Exception) {}
        silentAudioTrack = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
