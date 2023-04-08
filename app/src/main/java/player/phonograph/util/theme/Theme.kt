/*
 *  Copyright (c) 2022~2023 chr_56
 */

package player.phonograph.util.theme

import mt.pref.ThemeColor
import player.phonograph.R
import player.phonograph.settings.Setting
import player.phonograph.mechanism.setting.StyleConfig
import android.content.Context
import android.content.res.Configuration
import android.os.Build


/**
 * adjust color settings from Dynamic Color of Material You if available
 */
fun applyMonet(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Setting.instance.enableMonet) {
        ThemeColor.editTheme(context)
            .primaryColor(context.getColor(android.R.color.system_accent1_300))
            .accentColor(context.getColor(android.R.color.system_accent1_600))
            .commit()
    }
}

val Context.nightMode: Boolean
    get() =
        when (StyleConfig.generalTheme(this)) {
            R.style.Theme_Phonograph_Black -> true
            R.style.Theme_Phonograph_Dark  -> true
            R.style.Theme_Phonograph_Light -> false
            R.style.Theme_Phonograph_Auto  ->
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> true
                    Configuration.UI_MODE_NIGHT_NO  -> false
                    else                            -> false
                }
            else                           -> false
        }

fun backgroundColorByTheme(context: Context): Int = context.resources.getColor(
    when (StyleConfig.generalTheme(context)) {
        R.style.Theme_Phonograph_Auto  -> R.color.cardBackgroundColor
        R.style.Theme_Phonograph_Light -> R.color.md_white_1000
        R.style.Theme_Phonograph_Black -> R.color.md_black_1000
        R.style.Theme_Phonograph_Dark  -> R.color.md_grey_800
        else                           -> R.color.md_grey_700
    },
    context.theme
)

