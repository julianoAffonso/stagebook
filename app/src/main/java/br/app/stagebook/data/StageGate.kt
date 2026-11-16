package br.app.stagebook.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Stage open now? With it open (show) there's no sync: no download and no lyrics swap. */
@Singleton
class StageGate @Inject constructor() {
    // counter: going from one stage to another, the new one enters before the old one leaves
    private val open = MutableStateFlow(0)
    private val _onStage = MutableStateFlow(false)
    val onStage: StateFlow<Boolean> = _onStage.asStateFlow()
    // counts the stage exits: entering and leaving quickly isn't lost (onStage would come back the same)
    private val _exits = MutableStateFlow(0)
    val exits: StateFlow<Int> = _exits.asStateFlow()

    // the side menu is open over the stage: the person is not playing, so notices may show (see MainActivity)
    private val _menuOpen = MutableStateFlow(false)
    val menuOpen: StateFlow<Boolean> = _menuOpen.asStateFlow()
    fun setMenuOpen(open: Boolean) { _menuOpen.value = open; replay() }

    // the person asked, on the pinned menu, to answer a request waiting: shown now even with the stage in view
    private val _attend = MutableStateFlow(false)
    val attend: StateFlow<Boolean> = _attend.asStateFlow()
    fun setAttend(on: Boolean) { _attend.value = on }

    // the stage is the screen in view (not Settings, an editor or the importer over it, whose stage is still open under them).
    // True until the navigation says otherwise: a stage that opens is in view
    private val _stageInView = MutableStateFlow(true)
    val stageInView: StateFlow<Boolean> = _stageInView.asStateFlow()
    fun setStageInView(inView: Boolean) { _stageInView.value = inView; replay() }

    /**
     * The show is on screen: a stage open, in view, and the menu closed. This is what "on stage" means to whatever waits
     * for the person to stop playing (the new version's notice, a link or a file that arrived, the band's search and its
     * sync over the internet, a stranger's request to join). [onStage] alone no longer says it: the stage is the app's base
     * and stays open under the menu, Settings, an editor and the importer, so what waited for it to close waited for
     * ever (help review, 16/11).
     */
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private fun replay() { _playing.value = quiet(_onStage.value, _stageInView.value, _menuOpen.value) }

    companion object {
        /**
         * Requests to join (band, computer) wait, with nothing on top, only while the show is on screen: the stage open and in
         * view, and the menu closed. Settings or an editor over an open stage, or the menu open, are not the show.
         */
        fun quiet(onStage: Boolean, stageInView: Boolean, menuOpen: Boolean): Boolean = onStage && stageInView && !menuOpen
    }

    fun enter() { open.update { it + 1 }; _onStage.value = open.value > 0; replay() }
    fun leave() {
        open.update { (it - 1).coerceAtLeast(0) }
        _onStage.value = open.value > 0
        if (!_onStage.value) _exits.update { it + 1 }
        replay()
    }
}
