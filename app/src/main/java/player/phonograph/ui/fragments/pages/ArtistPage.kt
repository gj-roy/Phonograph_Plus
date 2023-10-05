/*
 * Copyright (c) 2022 chr_56 & Abou Zeid (kabouzeid) (original author)
 */

package player.phonograph.ui.fragments.pages

import player.phonograph.App
import player.phonograph.R
import player.phonograph.model.Artist
import player.phonograph.model.sort.SortRef
import player.phonograph.repo.loader.Artists
import player.phonograph.ui.adapter.ConstDisplayConfig
import player.phonograph.ui.adapter.DisplayAdapter
import player.phonograph.ui.fragments.pages.adapter.ArtistDisplayAdapter
import player.phonograph.ui.fragments.pages.util.DisplayConfig
import player.phonograph.ui.fragments.pages.util.DisplayConfigTarget
import androidx.fragment.app.viewModels
import android.content.Context
import kotlinx.coroutines.CoroutineScope

class ArtistPage : AbsDisplayPage<Artist, DisplayAdapter<Artist>>() {

    override val viewModel: AbsDisplayPageViewModel<Artist> get() = _viewModel

    private val _viewModel: ArtistPageViewModel by viewModels()

    class ArtistPageViewModel : AbsDisplayPageViewModel<Artist>() {
        override suspend fun loadDataSetImpl(context: Context, scope: CoroutineScope): Collection<Artist> {
            return Artists.all(App.instance)
        }

        override val headerTextRes: Int get() = R.plurals.item_artists
    }

    override val displayConfigTarget get() = DisplayConfigTarget.ArtistPage

    override fun initAdapter(): DisplayAdapter<Artist> {
        val displayConfig = DisplayConfig(displayConfigTarget)

        val layoutType = displayConfig.layoutType(displayConfig.gridSize)

        return ArtistDisplayAdapter(
            hostFragment.mainActivity,
            ConstDisplayConfig(layoutType, displayConfig.colorFooter),
        )
    }

    override val availableSortRefs: Array<SortRef>
        get() = arrayOf(
            SortRef.ARTIST_NAME,
            SortRef.ALBUM_COUNT,
            SortRef.SONG_COUNT,
        )

    companion object {
        const val TAG = "ArtistPage"
    }
}
