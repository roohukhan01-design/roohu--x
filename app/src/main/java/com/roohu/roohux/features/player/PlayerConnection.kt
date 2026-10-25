package com.roohu.roohux.features.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single bridge between the UI and [PlaybackService]. Both the full player and the mini-player read this,
 * so there is exactly one MediaController. It is connected in Activity.onStart and released in onStop
 * (playback itself lives in the service), which prevents controller leaks.
 */
class PlayerConnection(context: Context, private val history: PlaybackHistory) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var future: ListenableFuture<MediaController>? = null
    private var pending: Request? = null
    private var sleepJob: Job? = null
    private var saveJob: Job? = null

    var controller by mutableStateOf<MediaController?>(null); private set
    var currentUri by mutableStateOf<String?>(null); private set
    var title by mutableStateOf(""); private set
    var isVideo by mutableStateOf(false); private set
    var isPlaying by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** Wall-clock time the audio sleep timer fires, or 0 when off. */
    var sleepEndsAt by mutableLongStateOf(0L); private set

    private class Request(val uri: String, val title: String, val isVideo: Boolean, val autoplay: Boolean)

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { syncFrom(controller) }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerConnection.isPlaying = isPlaying
            if (!isPlaying) saveNow()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) error = null
            if (playbackState == Player.STATE_ENDED) saveNow()
        }

        override fun onPlayerError(error: PlaybackException) { this@PlayerConnection.error = describe(error) }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                syncFrom(c)
                startSaving()
                pending?.let { pending = null; start(c, it) }
            } catch (e: Exception) {
                future = null
                error = "Couldn't connect to the player."
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    /** Videos pause when the app leaves the foreground; audio keeps playing in the service. */
    fun release() {
        pauseIfVideo()
        saveNow()
        saveJob?.cancel()
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    /** Starts [uri], or just re-attaches if it is already the loaded item (no restart, no duplicate playback). */
    fun play(uri: String, title: String, isVideo: Boolean, autoplay: Boolean) {
        error = null
        val request = Request(uri, title, isVideo, autoplay)
        val c = controller
        if (c == null) { pending = request; connect(); return }
        start(c, request)
    }

    private fun start(c: MediaController, r: Request) {
        if (c.currentMediaItem?.mediaId == r.uri && c.playbackState != Player.STATE_IDLE) {
            if (c.playbackState == Player.STATE_ENDED) { c.seekTo(0); c.play() }
            syncFrom(c)
            return
        }
        setSleepTimer(0) // the sleep timer is audio-only and never carries over to a new item
        scope.launch {
            val startMs = history.positionFor(r.uri)
            val item = MediaItem.Builder()
                .setMediaId(r.uri)
                .setUri(r.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(r.title)
                        .setExtras(Bundle().apply { putBoolean(EXTRA_VIDEO, r.isVideo) })
                        .build(),
                )
                .build()
            title = r.title
            isVideo = r.isVideo
            currentUri = r.uri
            c.setMediaItem(item, startMs)
            c.prepare()
            c.playWhenReady = r.autoplay
        }
    }

    /** Loads an actual Media3 audio queue. Next/previous and shuffle work through the service. */
    fun playAudioQueue(tracks: List<Pair<String, String>>, autoplay: Boolean) {
        val valid = tracks.filter { it.first.isNotBlank() }.distinctBy { it.first }
        if (valid.isEmpty()) return
        val c = controller
        if (c == null) {
            pending = Request(valid.first().first, valid.first().second, false, autoplay)
            connect()
            return
        }
        setSleepTimer(0)
        val media = valid.map { (uri, name) ->
            MediaItem.Builder().setMediaId(uri).setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name)
                    .setExtras(Bundle().apply { putBoolean(EXTRA_VIDEO, false) }).build()).build()
        }
        c.setMediaItems(media)
        c.prepare()
        c.playWhenReady = autoplay
        syncFrom(c)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun retry() {
        error = null
        controller?.let { it.prepare(); it.play() }
    }

    fun pauseIfVideo() { if (isVideo) controller?.pause() }

    /** Stops audio and clears the queue so the mini-player disappears. */
    fun stop() {
        val c = controller ?: return
        saveNow()
        setSleepTimer(0)
        c.stop()
        c.clearMediaItems()
        syncFrom(c)
    }

    /** Audio-only feature: the UI never offers this for video. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0 || isVideo) { sleepEndsAt = 0L; return }
        sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            sleepEndsAt = 0L
        }
    }

    fun saveNow() {
        val c = controller ?: return
        val uri = c.currentMediaItem?.mediaId?.takeIf { it.startsWith("content://") || it.startsWith("file://") } ?: return
        val duration = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val item = PlayedItem(uri, title, isVideo, c.currentPosition, duration, System.currentTimeMillis())
        scope.launch { history.record(item) }
    }

    private fun startSaving() {
        saveJob?.cancel()
        saveJob = scope.launch {
            while (isActive) {
                delay(5_000)
                if (controller?.isPlaying == true) saveNow()
            }
        }
    }

    private fun syncFrom(c: MediaController?) {
        val item = c?.currentMediaItem
        currentUri = item?.mediaId?.takeIf { it.isNotEmpty() }
        title = item?.mediaMetadata?.title?.toString().orEmpty()
        isVideo = item?.mediaMetadata?.extras?.getBoolean(EXTRA_VIDEO) ?: false
        isPlaying = c?.isPlaying == true
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "This file is missing or can't be read any more."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network problem. Check your connection and retry."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The server refused this media (it may need sign-in or the link expired)."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This format isn't supported on this phone, or the file is damaged."
        else -> "Playback failed (${e.errorCodeName})."
    }

    private companion object { const val EXTRA_VIDEO = "roohux_video" }
}
