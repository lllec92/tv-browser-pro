package com.tvbrowser.pro

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Активность полноэкранного воспроизведения видео через Media3 ExoPlayer
 * с использованием аппаратного декодера устройства (MediaCodec) и с
 * fallback на программный декодер, если аппаратный декодер не смог
 * инициализироваться для конкретного потока.
 */
class VideoPlayerActivity : AppCompatActivity(), RemoteController.Callback {

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_MIME_TYPE = "extra_mime_type"
        private const val TAG = "VideoPlayerActivity"
        private const val SEEK_STEP_MS = 10_000L
    }

    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private var player: ExoPlayer? = null
    private lateinit var remoteController: RemoteController

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        progressBar = findViewById(R.id.player_progress)
        remoteController = RemoteController(this)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE)

        if (videoUrl.isNullOrBlank()) {
            Log.e(TAG, "URL видео не передан в VideoPlayerActivity")
            finish()
            return
        }

        initializePlayer(videoUrl, mimeType)
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer(url: String, mimeType: String?) {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory).build()
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.requestFocus()

        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        val resolvedMime = mimeType ?: VideoInterceptor.guessMimeType(url)
        if (resolvedMime != null) {
            mediaItemBuilder.setMimeType(resolvedMime)
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                    Player.STATE_READY -> progressBar.visibility = View.GONE
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Воспроизведение завершено")
                        finish()
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    TAG,
                    "Ошибка ExoPlayer: код=${error.errorCode}, сообщение=${error.message}",
                    error
                )
                progressBar.visibility = View.GONE
            }
        })

        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return remoteController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onDpadLeft() {
        val currentPlayer = player ?: return
        val newPosition = (currentPlayer.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)
        currentPlayer.seekTo(newPosition)
    }

    override fun onDpadRight() {
        val currentPlayer = player ?: return
        val duration = currentPlayer.duration
        val newPosition = if (duration > 0) {
            (currentPlayer.currentPosition + SEEK_STEP_MS).coerceAtMost(duration)
        } else {
            currentPlayer.currentPosition + SEEK_STEP_MS
        }
        currentPlayer.seekTo(newPosition)
    }

    override fun onSelect() {
        togglePlayPause()
    }

    override fun onPlayPause() {
        togglePlayPause()
    }

    private fun togglePlayPause() {
        val currentPlayer = player ?: return
        currentPlayer.playWhenReady = !currentPlayer.playWhenReady
    }

    override fun onRemoteBackPressed(): Boolean {
        finish()
        return true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
