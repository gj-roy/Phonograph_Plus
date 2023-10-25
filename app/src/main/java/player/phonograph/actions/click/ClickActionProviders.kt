/*
 *  Copyright (c) 2022~2023 chr_56
 */

package player.phonograph.actions.click

import player.phonograph.R
import player.phonograph.actions.actionEnqueue
import player.phonograph.actions.actionPlay
import player.phonograph.actions.actionPlayNext
import player.phonograph.actions.actionPlayNow
import player.phonograph.actions.click.mode.SongClickMode
import player.phonograph.actions.click.mode.SongClickMode.FLAG_MASK_GOTO_POSITION_FIRST
import player.phonograph.actions.click.mode.SongClickMode.FLAG_MASK_PLAY_QUEUE_IF_EMPTY
import player.phonograph.actions.click.mode.SongClickMode.QUEUE_APPEND_QUEUE
import player.phonograph.actions.click.mode.SongClickMode.QUEUE_PLAY_NEXT
import player.phonograph.actions.click.mode.SongClickMode.QUEUE_PLAY_NOW
import player.phonograph.actions.click.mode.SongClickMode.QUEUE_SHUFFLE
import player.phonograph.actions.click.mode.SongClickMode.QUEUE_SWITCH_TO_BEGINNING
import player.phonograph.actions.click.mode.SongClickMode.QUEUE_SWITCH_TO_POSITION
import player.phonograph.actions.click.mode.SongClickMode.SONG_APPEND_QUEUE
import player.phonograph.actions.click.mode.SongClickMode.SONG_PLAY_NEXT
import player.phonograph.actions.click.mode.SongClickMode.SONG_PLAY_NOW
import player.phonograph.actions.click.mode.SongClickMode.SONG_SINGLE_PLAY
import player.phonograph.actions.click.mode.SongClickMode.resetBaseMode
import player.phonograph.model.Album
import player.phonograph.model.Artist
import player.phonograph.model.Genre
import player.phonograph.model.PlayRequest
import player.phonograph.model.Song
import player.phonograph.model.file.FileEntity
import player.phonograph.model.playlist.Playlist
import player.phonograph.repo.loader.Songs
import player.phonograph.service.MusicPlayerRemote
import player.phonograph.service.queue.ShuffleMode
import player.phonograph.settings.Keys
import player.phonograph.settings.Setting
import player.phonograph.util.NavigationUtil
import player.phonograph.util.testBit
import androidx.core.util.Pair
import android.content.Context
import android.widget.ImageView
import kotlin.random.Random

object ClickActionProviders {

    object EmptyClickActionProvider : ClickActionProvider<Any> {
        override fun listClick(
            list: List<Any>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean = true
    }

    class SongClickActionProvider : ClickActionProvider<Song> {
        override fun listClick(
            list: List<Song>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean {
            val setting = Setting(context)
            val base = setting[Keys.songItemClickMode].data
            val extra = setting[Keys.songItemClickExtraFlag].data
            return songClick(list, position, base, extra)
        }

        private fun songClick(
            list: List<Song>,
            position: Int,
            baseMode: Int,
            extraFlag: Int,
        ): Boolean {
            var base = baseMode

            // pre-process extra mode
            if (MusicPlayerRemote.playingQueue.isEmpty() && extraFlag.testBit(FLAG_MASK_PLAY_QUEUE_IF_EMPTY)) {
                if (base in 100..109) {
                    base += 100
                } else {
                    base = SongClickMode.QUEUE_SWITCH_TO_POSITION
                }
            }

            if (extraFlag.testBit(FLAG_MASK_GOTO_POSITION_FIRST) && list == MusicPlayerRemote.playingQueue) {
                // same queue, jump
                MusicPlayerRemote.playSongAt(position)
                return true
            }


            // base mode
            when (base) {
                SONG_PLAY_NEXT            -> list[position].actionPlayNext()
                SONG_PLAY_NOW             -> list[position].actionPlayNow()
                SONG_APPEND_QUEUE         -> list[position].actionEnqueue()
                SONG_SINGLE_PLAY          -> listOf(list[position]).actionPlay(null, 0)
                QUEUE_PLAY_NOW            -> list.actionPlayNow()
                QUEUE_PLAY_NEXT           -> list.actionPlayNext()
                QUEUE_APPEND_QUEUE        -> list.actionEnqueue()
                QUEUE_SWITCH_TO_BEGINNING -> list.actionPlay(ShuffleMode.NONE, 0)
                QUEUE_SWITCH_TO_POSITION  -> list.actionPlay(ShuffleMode.NONE, position)
                QUEUE_SHUFFLE             -> list.actionPlay(
                    ShuffleMode.SHUFFLE,
                    Random.nextInt(list.size)
                )

                else  /* invalided */     -> {
                    resetBaseMode()
                    return false
                }
            }
            return true
        }

    }

    class AlbumClickActionProvider : ClickActionProvider<Album> {
        override fun listClick(
            list: List<Album>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean {
            if (imageView != null) {
                NavigationUtil.goToAlbum(
                    context,
                    list[position].id,
                    Pair(
                        imageView,
                        imageView.resources.getString(R.string.transition_album_art)
                    )
                )
            } else {
                NavigationUtil.goToAlbum(
                    context,
                    list[position].id
                )
            }
            return true
        }

    }

    class ArtistClickActionProvider : ClickActionProvider<Artist> {
        override fun listClick(
            list: List<Artist>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean {
            if (imageView != null) {
                NavigationUtil.goToArtist(
                    context,
                    list[position].id,
                    Pair(
                        imageView,
                        imageView.resources.getString(R.string.transition_artist_image)
                    )
                )
            } else {
                NavigationUtil.goToArtist(context, list[position].id)
            }
            return true
        }

    }

    class PlaylistClickActionProvider : ClickActionProvider<Playlist> {
        override fun listClick(
            list: List<Playlist>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean {
            NavigationUtil.goToPlaylist(context, list[position])
            return true
        }

    }

    class GenreClickActionProvider : ClickActionProvider<Genre> {
        override fun listClick(
            list: List<Genre>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean {
            NavigationUtil.goToGenre(context, list[position])
            return true
        }

    }

    class FileEntityClickActionProvider : ClickActionProvider<FileEntity> {
        override fun listClick(
            list: List<FileEntity>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean {
            val setting = Setting(context)
            val base = setting[Keys.songItemClickMode].data
            val extra = setting[Keys.songItemClickExtraFlag].data
            return fileClick(list, position, base, extra, context)
        }

        /**
         * @param list entire list including folder
         * @param position in-list position
         */
        private fun fileClick(
            list: List<FileEntity>,
            position: Int,
            baseMode: Int,
            extraFlag: Int,
            context: Context,
        ): Boolean {
            var base = baseMode
            val songRequest by lazy(LazyThreadSafetyMode.NONE) { filter(list, position, context) }

            // pre-process extra mode
            if (MusicPlayerRemote.playingQueue.isEmpty() && extraFlag.testBit(FLAG_MASK_PLAY_QUEUE_IF_EMPTY)) {
                if (base in 100..109) {
                    base += 100
                } else {
                    base = QUEUE_SWITCH_TO_POSITION
                }
            }

            if (extraFlag.testBit(FLAG_MASK_GOTO_POSITION_FIRST) && songRequest.songs == MusicPlayerRemote.playingQueue) {
                // same queue, jump
                MusicPlayerRemote.playSongAt(songRequest.position)
                return true
            }

            when (base) {
                SONG_PLAY_NEXT,
                SONG_PLAY_NOW,
                SONG_APPEND_QUEUE,
                SONG_SINGLE_PLAY,
                     -> {
                    val fileEntity = list[position] as? FileEntity.File ?: return false
                    val song = Songs.searchByFileEntity(context, fileEntity)
                    when (base) {
                        SONG_PLAY_NEXT    -> song.actionPlayNext()
                        SONG_PLAY_NOW     -> song.actionPlayNow()
                        SONG_APPEND_QUEUE -> song.actionEnqueue()
                        SONG_SINGLE_PLAY  -> listOf(song).actionPlay(null, 0)
                    }
                }

                QUEUE_PLAY_NOW,
                QUEUE_PLAY_NEXT,
                QUEUE_APPEND_QUEUE,
                QUEUE_SWITCH_TO_BEGINNING,
                QUEUE_SWITCH_TO_POSITION,
                QUEUE_SHUFFLE,
                     -> {
                    val songs = songRequest.songs
                    val actualPosition = songRequest.position

                    when (base) {
                        QUEUE_PLAY_NOW            -> songs.actionPlayNow()
                        QUEUE_PLAY_NEXT           -> songs.actionPlayNext()
                        QUEUE_APPEND_QUEUE        -> songs.actionEnqueue()
                        QUEUE_SWITCH_TO_BEGINNING -> songs.actionPlay(ShuffleMode.NONE, 0)
                        QUEUE_SWITCH_TO_POSITION  -> songs.actionPlay(ShuffleMode.NONE, actualPosition)
                        QUEUE_SHUFFLE             ->
                            if (songs.isNotEmpty()) songs.actionPlay(ShuffleMode.SHUFFLE, Random.nextInt(songs.size))
                    }
                }

                else -> {
                    resetBaseMode()
                    return false
                }
            }
            return true
        }

        /**
         * filter folders and relocate position
         */
        private fun filter(list: List<FileEntity>, position: Int, context: Context): PlayRequest.SongsRequest {
            var actualPosition: Int = position
            val actualFileList = ArrayList<Song>(position)
            for ((index, item) in list.withIndex()) {
                if (item is FileEntity.File) {
                    actualFileList.add(Songs.searchByFileEntity(context, item))
                } else {
                    if (index < position) actualPosition--
                }
            }
            return PlayRequest.SongsRequest(actualFileList, actualPosition)
        }
    }

    interface ClickActionProvider<T> {
        /**
         * involve item click
         * @param list      a list that this Displayable is among
         * @param position  position where selected
         * @param context  relative context
         * @param imageView (optional) item's imagine for SceneTransitionAnimation
         * @return true if action have been processed
         */
        fun listClick(
            list: List<T>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean
    }
}
