package br.app.stagebook.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A screen that can pull a band follower to the leader's song (the setlist) says here when it's busy (a dialog open,
 * a drag going on): the song then waits and opens when it's done, so a rename or a reorder isn't thrown away (audit
 * 02/11).
 */
val LocalFollowHold = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }
