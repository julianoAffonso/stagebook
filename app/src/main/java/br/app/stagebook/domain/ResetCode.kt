package br.app.stagebook.domain

import kotlin.random.Random

/** Code the user types to enable "Erase everything" (four digits, new each time the window opens). */
object ResetCode {
    fun generate(random: Random = Random.Default): String = (1000 + random.nextInt(9000)).toString()

    fun matches(code: String, typed: String): Boolean = typed.trim() == code
}
