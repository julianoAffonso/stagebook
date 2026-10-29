package com.juliano.stagebook.domain

import com.juliano.stagebook.domain.Chords.Seventh

/**
 * Campo harmônico (T21, "Detalhes" do acorde). Maior: I ii iii IV V vi vii°; menor (natural): i ii° III iv v VI VII, e
 * o V maior da menor harmônica (E e E7 em Am) também conta. Com sétima, o campo é de tétrades (G7M Am7 Bm7 C7M D7 Em7
 * F#m7(b5)) e o grau só vale se a sétima também bater: em G, C7M é o IV, C7 não é do campo.
 */
object HarmonicField {
    data class Degree(val roman: String, val chord: String)

    /** Um tom onde o acorde aparece: o nome do tom e o grau. */
    data class Appearance(val key: String, val roman: String, val minorKey: Boolean)

    private data class Step(val semis: Int, val minor: Boolean, val dim: Boolean, val roman: String, val seventh: Seventh, val roman7: String, val shown: Boolean = true)

    private val major = listOf(
        Step(0, false, false, "I", Seventh.MAJOR, "I7M"), Step(2, true, false, "ii", Seventh.MINOR, "ii7"),
        Step(4, true, false, "iii", Seventh.MINOR, "iii7"), Step(5, false, false, "IV", Seventh.MAJOR, "IV7M"),
        Step(7, false, false, "V", Seventh.MINOR, "V7"), Step(9, true, false, "vi", Seventh.MINOR, "vi7"),
        Step(11, false, true, "vii°", Seventh.MINOR, "viiø"),
    )
    private val minor = listOf(
        Step(0, true, false, "i", Seventh.MINOR, "i7"), Step(2, false, true, "ii°", Seventh.MINOR, "iiø"),
        Step(3, false, false, "III", Seventh.MAJOR, "III7M"), Step(5, true, false, "iv", Seventh.MINOR, "iv7"),
        Step(7, true, false, "v", Seventh.MINOR, "v7"), Step(8, false, false, "VI", Seventh.MAJOR, "VI7M"),
        Step(10, false, false, "VII", Seventh.MINOR, "VII7"),
        // V da menor harmônica: não aparece na fileira, mas conta como do campo
        Step(7, false, false, "V", Seventh.MINOR, "V7", shown = false),
    )

    /** Os 7 acordes do campo do tom [key]; [sevenths] = tétrades (G7M, Am7…). Vazio se o tom não for entendido. */
    fun field(key: String, sevenths: Boolean = false): List<Degree> {
        val k = Chords.triad(key) ?: return emptyList()
        val name = Chords.keyName(k.root, k.minor)
        return steps(k.minor).filter { it.shown }.map { st ->
            val note = Chords.noteIn(name, k.root + st.semis)
            if (!sevenths) Degree(st.roman, note + if (st.dim) "°" else if (st.minor) "m" else "")
            else Degree(st.roman7, note + suffix7(st))
        }
    }

    /** O grau do acorde no tom [key] (ex.: C em G → "IV"; C7M em G → "IV7M"), ou null se não é do campo. */
    fun degreeIn(chord: String, key: String): String? {
        val c = Chords.triad(chord) ?: return null
        val k = Chords.triad(key) ?: return null
        return steps(k.minor).firstOrNull { st -> (k.root + st.semis) % 12 == c.root && matches(st, c) }?.let { label(it, c) }
    }

    /** Os tons em que o acorde aparece, menos o dele mesmo (o principal), maiores primeiro. */
    fun appearances(chord: String): List<Appearance> {
        val c = Chords.triad(chord) ?: return emptyList()
        val out = ArrayList<Appearance>()
        for (minorKey in listOf(false, true)) for (tonic in 0 until 12) {
            val st = steps(minorKey).firstOrNull { (tonic + it.semis) % 12 == c.root && matches(it, c) } ?: continue
            if (st.semis == 0) continue   // o campo em que ele é o I já aparece à parte
            out += Appearance(Chords.keyName(tonic, minorKey), label(st, c), minorKey)
        }
        return out
    }

    // a tríade tem de bater; a sétima, se o acorde tiver uma
    private fun matches(st: Step, c: Chords.Triad) =
        st.minor == c.minor && st.dim == c.dim && (c.seventh == Seventh.NONE || c.seventh == st.seventh)

    private fun label(st: Step, c: Chords.Triad) = if (c.seventh == Seventh.NONE) st.roman else st.roman7

    private fun suffix7(st: Step) = when {
        st.dim -> "m7(b5)"
        st.minor -> "m7"
        st.seventh == Seventh.MAJOR -> "7M"
        else -> "7"
    }

    private fun steps(minorKey: Boolean) = if (minorKey) minor else major
}
