package br.app.stagebook.domain

import kotlin.random.Random

/** Código que o usuário digita para liberar "Apagar tudo" (quatro números, novo a cada vez que a janela abre). */
object ResetCode {
    fun generate(random: Random = Random.Default): String = (1000 + random.nextInt(9000)).toString()

    fun matches(code: String, typed: String): Boolean = typed.trim() == code
}
