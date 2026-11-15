package br.app.stagebook.domain

/**
 * With the library drawer open over the stage, the pedal must not turn pages or songs of the stage behind it (the
 * person is looking at a list, maybe typing a name). A press that does something on the stage is taken and does
 * nothing; any other key (a Bluetooth keyboard typing in the search) goes on to the text field.
 */
fun pedalTakenByDrawer(press: PedalPress, custom: Map<Int, PedalAction>): Boolean =
    PedalAction.hit(press, custom).action != PedalAction.NONE
