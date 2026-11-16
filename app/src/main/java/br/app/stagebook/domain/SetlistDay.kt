package br.app.stagebook.domain

/**
 * The setlists in the order of their show day: the newest day first, the ones with no day ("") last; the order given
 * (newest created first) breaks ties. The lists on the device and the editor on the computer share it.
 */
fun <T> List<T>.byShowDay(date: (T) -> String): List<T> =
    sortedWith(compareByDescending<T> { date(it).isNotEmpty() }.thenByDescending { date(it) })
