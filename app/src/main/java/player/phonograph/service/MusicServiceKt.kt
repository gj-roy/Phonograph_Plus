/*
 * Copyright (c) 2022 chr_56 & Abou Zeid (kabouzeid) (original author)
 */

package player.phonograph.service

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.widget.Toast
import player.phonograph.App
import player.phonograph.R
import player.phonograph.appwidgets.AppWidgetBig
import player.phonograph.appwidgets.AppWidgetCard
import player.phonograph.appwidgets.AppWidgetClassic
import player.phonograph.appwidgets.AppWidgetSmall
import player.phonograph.model.Song
import player.phonograph.model.playlist.Playlist
import player.phonograph.service.queue.SHUFFLE_MODE_NONE
import player.phonograph.service.queue.ShuffleMode
import player.phonograph.util.MusicUtil.getSongFileUri

class MusicServiceKt(val musicService: MusicService) {

    var mediaStoreObserver: MediaStoreObserver? = null

    fun setUpMediaStoreObserver(
        context: Context,
        playerHandler: Handler, // todo
        handleAndSendChangeInternalCallback: (String) -> Unit
    ) {
        mediaStoreObserver = MediaStoreObserver(playerHandler, handleAndSendChangeInternalCallback)
        with(context) {
            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )

            contentResolver.registerContentObserver(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Albums.INTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Artists.INTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Genres.INTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
            contentResolver.registerContentObserver(
                MediaStore.Audio.Playlists.INTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver!!
            )
        }
    }

    fun unregisterMediaStoreObserver(context: Context) {
        context.contentResolver.unregisterContentObserver(mediaStoreObserver!!)
    }

    class MediaStoreObserver(
        private val mHandler: Handler,
        private val handleAndSendChangeInternalCallback: (String) -> Unit
    ) : ContentObserver(mHandler), Runnable {

        override fun onChange(selfChange: Boolean) {
            // if a change is detected, remove any scheduled callback
            // then post a new one. This is intended to prevent closely
            // spaced events from generating multiple refresh calls
            mHandler.removeCallbacks(this)
            mHandler.postDelayed(this, REFRESH_DELAY)
        }

        override fun run() {
            // actually call refresh when the delayed callback fires
            // do not send a sticky broadcast here
            handleAndSendChangeInternalCallback(MusicService.MEDIA_STORE_CHANGED)
        }

        companion object {
            // milliseconds to delay before calling refresh to aggregate events
            private const val REFRESH_DELAY: Long = 500
        }
    }

    @JvmField val appWidgetBig = AppWidgetBig.instance

    @JvmField val appWidgetClassic = AppWidgetClassic.instance

    @JvmField val appWidgetSmall = AppWidgetSmall.instance

    @JvmField val appWidgetCard = AppWidgetCard.instance

    @JvmField
    val widgetIntentReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val command = intent.getStringExtra(MusicService.EXTRA_APP_WIDGET_NAME)
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                when (command) {
                    AppWidgetClassic.NAME -> {
                        appWidgetClassic.performUpdate(musicService, ids)
                    }
                    AppWidgetSmall.NAME -> {
                        appWidgetSmall.performUpdate(musicService, ids)
                    }
                    AppWidgetBig.NAME -> {
                        appWidgetBig.performUpdate(musicService, ids)
                    }
                    AppWidgetCard.NAME -> {
                        appWidgetCard.performUpdate(musicService, ids)
                    }
                }
            }
        }

    companion object {
        private const val ANDROID_MUSIC_PACKAGE_NAME = "com.android.music"

        @JvmStatic
        fun sendPublicIntent(service: MusicService, what: String) {
            service.sendStickyBroadcast(
                Intent(
                    what.replace(MusicService.PHONOGRAPH_PACKAGE_NAME, ANDROID_MUSIC_PACKAGE_NAME)
                ).apply {
                    val song: Song = App.instance.queueManager.currentSong
                    putExtra("id", song.id)
                    putExtra("artist", song.artistName)
                    putExtra("album", song.albumName)
                    putExtra("track", song.title)
                    putExtra("duration", song.duration)
                    putExtra("position", service.songProgressMillis.toLong())
                    putExtra("playing", service.isPlaying)
                    putExtra("scrobbling_source", MusicService.PHONOGRAPH_PACKAGE_NAME)
                }
            )
        }

        @JvmStatic
        fun getTrackUri(song: Song): Uri {
            return getSongFileUri(song.id)
        }

        @JvmStatic
        fun parsePlaylistAndPlay(intent: Intent, service: MusicService) {
            val playlist: Playlist? = intent.getParcelableExtra(
                MusicService.INTENT_EXTRA_PLAYLIST
            )
            val playlistSongs = playlist?.getSongs(service)
            val shuffleMode = ShuffleMode.deserialize(
                intent.getIntExtra(MusicService.INTENT_EXTRA_SHUFFLE_MODE, SHUFFLE_MODE_NONE)
            )
            if (playlistSongs.isNullOrEmpty()) {
                Toast.makeText(service, R.string.playlist_is_empty, Toast.LENGTH_LONG).show()
            } else {
                val queueManager = App.instance.queueManager
                queueManager.switchShuffleMode(shuffleMode)
                // TODO: keep the queue intact
                val queue =
                    if (shuffleMode == ShuffleMode.SHUFFLE) playlistSongs.toMutableList().apply { shuffle() } else playlistSongs
                service.openQueue(queue, 0, true)
            }
        }

        @JvmStatic
        fun copy(bitmap: Bitmap): Bitmap? {
            var config = bitmap.config
            if (config == null) {
                config = Bitmap.Config.RGB_565
            }
            return try {
                bitmap.copy(config, false)
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                null
            }
        }
    }
}
