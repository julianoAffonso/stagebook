package com.juliano.stagebook.domain

/**
 * Quais bolinhas de página o rodapé do palco mostra, como a paginação do Google: cabendo, todas; não
 * cabendo, a primeira, a última, a atual com as vizinhas e "…" (null) no lugar das que ficam de fora
 * (pedido do usuário, 23/10: com muitas páginas o celular escondia o relógio e cortava as últimas; o
 * palco usa o mesmo número no celular e no tablet).
 */
object PageSlots {
    /** [current] e o resultado são índices de 0; [capacity] = quantas bolinhas (ou "…") cabem. */
    fun of(current: Int, count: Int, capacity: Int): List<Int?> {
        if (count <= capacity) return (0 until count).toList()
        // espaço para primeira, "…", vizinhas, "…", última: com menos que isso, só a atual
        if (capacity < 5) return listOf(current)
        val window = capacity - 4
        return when {
            // perto do começo: um bloco corrido desde a 1ª, "…" e a última
            current <= capacity - 4 -> (0..capacity - 3).toList() + null + (count - 1)
            // perto do fim: a 1ª, "…" e um bloco corrido até a última
            current >= count - (capacity - 3) -> listOf(0, null) + (count - (capacity - 2) until count)
            else -> {
                val start = current - (window - 1) / 2
                listOf(0, null) + (start until start + window) + null + (count - 1)
            }
        }
    }
}
