package br.app.stagebook.domain

/**
 * Where a link or file tapped outside the app has to wait instead of opening (user, 09/11). The stage: the show is never
 * interrupted. The song editor: opening goes back to Home, which would drop what was typed without the "discard?"
 * question. Anywhere else it opens right away.
 */
enum class HoldPlace {
    NONE, STAGE, EDITOR;

    val holds: Boolean get() = this != NONE

    companion object {
        /** [editing]: a song editor is anywhere in the back stack (one below another screen is dropped too). */
        fun of(onStage: Boolean, editing: Boolean): HoldPlace = when {
            onStage -> STAGE
            editing -> EDITOR
            else -> NONE
        }
    }
}
