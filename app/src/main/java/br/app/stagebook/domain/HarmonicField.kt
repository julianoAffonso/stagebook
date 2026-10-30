package br.app.stagebook.domain

import br.app.stagebook.domain.Chords.Seventh

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

    /** Onde o acorde cai num tom: a posição na escala (0 = I … 6 = VII) e o nome do grau com a cifra (ex.: "V7(9)"). */
    data class Place(val index: Int, val label: String)

    private val majorScale = listOf(0, 2, 4, 5, 7, 9, 11)
    private val naturalMinor = listOf(0, 2, 3, 5, 7, 8, 10)
    private val harmonicMinor = listOf(0, 2, 3, 5, 7, 8, 11)   // o V maior (E7 em Am)
    private val numerals = listOf("I", "II", "III", "IV", "V", "VI", "VII")

    /**
     * O acorde no tom [key]: só é do campo se **todas** as notas dele (tríade, sétima, extensões, sus, alterações e o baixo)
     * estão na escala do tom (pedido do usuário, 29/10: olhar só a tríade, ou só a sétima, dava informação errada). Em G:
     * C7M e Csus2 são do campo; C7 (Bb) não é. Na menor vale a natural ou a harmônica.
     */
    fun placeIn(chord: String, key: String): Place? {
        val n = ChordTheory.notes(chord) ?: return null
        val k = Chords.triad(key) ?: return null
        val rel = (n.tones + n.bass).map { (it - k.root + 12) % 12 }.toSet()
        val scale = when {
            !k.minor -> majorScale.takeIf { it.containsAll(rel) }
            naturalMinor.containsAll(rel) -> naturalMinor
            harmonicMinor.containsAll(rel) -> harmonicMinor
            else -> null
        } ?: return null
        val index = scale.indexOf((n.root - k.root + 12) % 12).takeIf { it >= 0 } ?: return null
        return Place(index, label(index, chord, n))
    }

    /** O nome do grau do acorde no tom [key] (ex.: C em G → "IV"; C7M → "IV7M"; Dsus4 → "Vsus4"), ou null se não é do campo. */
    fun degreeIn(chord: String, key: String): String? = placeIn(chord, key)?.label

    /** Os tons em que o acorde aparece, menos os em que ele é o I (o principal já aparece à parte), maiores primeiro. */
    fun appearances(chord: String): List<Appearance> {
        val out = ArrayList<Appearance>()
        for (minorKey in listOf(false, true)) for (tonic in 0 until 12) {
            val key = Chords.keyName(tonic, minorKey)
            val place = placeIn(chord, key) ?: continue
            if (place.index == 0) continue
            out += Appearance(key, place.label, minorKey)
        }
        return out
    }

    // numeral romano: maiúsculo com terça maior (ou sem terça), minúsculo com terça menor; ° diminuto, ø meio-diminuto;
    // depois o resto da cifra (7M, 7, (9), sus4, 6…), sem o "m" que o minúsculo já diz
    private fun label(index: Int, chord: String, n: ChordTheory.Notes): String {
        val r = n.root
        val minorThird = (r + 3) % 12 in n.tones && (r + 4) % 12 !in n.tones
        val dimFifth = (r + 6) % 12 in n.tones && (r + 7) % 12 !in n.tones
        val numeral = numerals[index].let { if (minorThird) it.lowercase() else it }
        val rest = chord.trim().substringBefore('/').drop(1).dropWhile { it == '#' || it == 'b' || it == '♯' || it == '♭' }
            .replace("maj7", "7M").replace("M7", "7M").replace("Δ", "7M")
        return when {
            minorThird && dimFifth && (r + 10) % 12 in n.tones -> numeral + "ø"
            minorThird && dimFifth -> numeral + "°"
            else -> numeral + (if (minorThird) rest.removePrefix("m").removePrefix("in") else rest)
        }
    }

    private fun suffix7(st: Step) = when {
        st.dim -> "m7(b5)"
        st.minor -> "m7"
        st.seventh == Seventh.MAJOR -> "7M"
        else -> "7"
    }

    private fun steps(minorKey: Boolean) = if (minorKey) minor else major
}
