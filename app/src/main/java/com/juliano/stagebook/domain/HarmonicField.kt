package com.juliano.stagebook.domain

/**
 * Campo harmônico (T21, "Detalhes" do acorde). Maior: I ii iii IV V vi vii°; menor (natural): i ii° III iv v VI VII.
 * [field] monta os 7 acordes de um tom; [degreeIn] diz o grau que o acorde ocupa num tom; [appearances] lista os tons
 * em que o acorde aparece (os maiores primeiro), para o painel "onde mais ele aparece".
 */
object HarmonicField {
    data class Degree(val roman: String, val chord: String)

    /** Um tom onde o acorde aparece: o nome do tom e o grau. */
    data class Appearance(val key: String, val roman: String, val minorKey: Boolean)

    private data class Step(val semis: Int, val minor: Boolean, val dim: Boolean, val roman: String)

    private val major = listOf(
        Step(0, false, false, "I"), Step(2, true, false, "ii"), Step(4, true, false, "iii"), Step(5, false, false, "IV"),
        Step(7, false, false, "V"), Step(9, true, false, "vi"), Step(11, false, true, "vii°"),
    )
    private val minor = listOf(
        Step(0, true, false, "i"), Step(2, false, true, "ii°"), Step(3, false, false, "III"), Step(5, true, false, "iv"),
        Step(7, true, false, "v"), Step(8, false, false, "VI"), Step(10, false, false, "VII"),
    )

    /** Os 7 acordes do campo do tom [key] (ex.: "G" → G Am Bm C D Em F#°). Vazio se o tom não for entendido. */
    fun field(key: String): List<Degree> {
        val k = Chords.triad(key) ?: return emptyList()
        val name = Chords.keyName(k.root, k.minor)
        return steps(k.minor).map { st ->
            val note = Chords.noteIn(name, k.root + st.semis)
            Degree(st.roman, note + if (st.dim) "°" else if (st.minor) "m" else "")
        }
    }

    /** O grau do acorde no tom [key] (ex.: C em G → "IV"), ou null se não é do campo. */
    fun degreeIn(chord: String, key: String): String? {
        val c = Chords.triad(chord) ?: return null
        val k = Chords.triad(key) ?: return null
        return steps(k.minor).firstOrNull { st -> (k.root + st.semis) % 12 == c.root && st.minor == c.minor && st.dim == c.dim }?.roman
    }

    /** Os tons em que o acorde aparece, menos o dele mesmo (o principal), maiores primeiro. */
    fun appearances(chord: String): List<Appearance> {
        val c = Chords.triad(chord) ?: return emptyList()
        val out = ArrayList<Appearance>()
        for (minorKey in listOf(false, true)) for (tonic in 0 until 12) {
            val st = steps(minorKey).firstOrNull { (tonic + it.semis) % 12 == c.root && it.minor == c.minor && it.dim == c.dim } ?: continue
            if (st.semis == 0) continue   // o campo em que ele é o I já aparece à parte
            out += Appearance(Chords.keyName(tonic, minorKey), st.roman, minorKey)
        }
        return out
    }

    private fun steps(minorKey: Boolean) = if (minorKey) minor else major
}
