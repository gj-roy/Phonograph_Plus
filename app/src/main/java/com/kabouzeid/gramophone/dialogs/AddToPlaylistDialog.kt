package com.kabouzeid.gramophone.dialogs

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.kabouzeid.gramophone.R
import com.kabouzeid.gramophone.loader.PlaylistLoader
import com.kabouzeid.gramophone.model.Song
import com.kabouzeid.gramophone.util.PlaylistsUtil
/*TODO review*/

/**
 * @author Karim Abou Zeid (kabouzeid), Aidan Follestad (afollestad)
 */
class AddToPlaylistDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val songs: List<Song> = requireArguments().getParcelableArrayList(SONG)!!
        val playlists = PlaylistLoader.getAllPlaylists(requireActivity())

        val playlistNames: List<CharSequence> = List<CharSequence>(playlists.size + 1) {
            if (it == 0) {
                requireActivity().resources.getString(R.string.action_new_playlist)
            } else {
                if (playlists[it - 1].name.isNullOrEmpty()) {
                    Log.w("AddToPlaylistDialog","A playlist has null name!!!")
                    Toast.makeText(requireContext(),"There is/are (a )playlist(s) with null name, please check playlist!🤔", Toast.LENGTH_SHORT).show()
                    "Null Name Playlist ?!🤔"
                } else {
                    playlists[it - 1].name
                }
            }
        }
        return MaterialDialog(requireActivity())
            .title(R.string.add_playlist_title)
            .listItemsSingleChoice(items = playlistNames) { materialDialog: MaterialDialog, index: Int, _: CharSequence ->
                if (index == 0) {
                    materialDialog.dismiss()
                    CreatePlaylistDialog.create(songs).show(requireActivity().supportFragmentManager, "ADD_TO_PLAYLIST")
                } else {
                    materialDialog.dismiss()
                    PlaylistsUtil.addToPlaylist(requireActivity(), songs, playlists[index - 1].id, true)
                }
            }
    }

    companion object {
        @JvmStatic
        fun create(songs: List<Song>): AddToPlaylistDialog {
            val dialog = AddToPlaylistDialog()
            val args = Bundle()
            args.putParcelableArrayList(SONG, ArrayList(songs))
            dialog.arguments = args
            return dialog
        }

        const val SONG: String = "songs"
    }
}
