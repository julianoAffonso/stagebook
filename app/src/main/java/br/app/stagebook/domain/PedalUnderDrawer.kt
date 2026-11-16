package br.app.stagebook.domain

/**
 * With the library drawer open over the stage, the pedal must not turn pages or songs of the stage behind it (the
 * person is looking at a list, maybe typing a name). A press that does something on the stage is taken and does
 * nothing; any other key (a Bluetooth keyboard typing in the search) goes on to the text field.
 */
fun pedalTakenByDrawer(press: PedalPress, custom: Map<Int, PedalAction>, typing: Boolean = false): Boolean =
    // someone is typing in a field of the menu (the search): the keys that write and erase go to the field. Backspace, Space
    // and Enter are page keys on the stage, and taken here they never reached the search: the text could not be erased
    // (user, 16/11: "backspace doesn't work when searching for a song to add to the setlist")
    if (typing && press.code in TYPING_KEYS) false else PedalAction.hit(press, custom).action != PedalAction.NONE

// Android's key codes of what writes, erases and moves inside a text field: Backspace and Delete, Space, Enter, the letters,
// the digits and the left and right arrows
private val TYPING_KEYS: Set<Int> = setOf(67, 112, 62, 66, 160, 21, 22) + (29..54) + (7..16)
