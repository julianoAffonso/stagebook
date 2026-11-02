package br.app.stagebook.domain

import br.app.stagebook.domain.Chords.Seventh

/**
 * Harmonic field (T21, the chord's "Details"). Major: I ii iii IV V vi vii°; minor (natural): i ii° III iv v VI VII,
 * and the harmonic minor's major V (E and E7 in Am) also counts. With a seventh, the field uses seventh chords (G7M Am7
 * Bm7 C7M D7 Em7 F#m7(b5)) and the degree only counts if the seventh matches too: in G, C7M is the IV, C7 isn't in the
 * field.
 */
object HarmonicField {
    data class Degree(val roman: String, val chord: String)

    /** A key where the chord shows up: the key's name and the degree. */
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
        // harmonic minor V: not in the row, but counts as part of the field
        Step(7, false, false, "V", Seventh.MINOR, "V7", shown = false),
    )

    /**
     * The 7 chords of key [key]'s field; [sevenths] = seventh chords (G7M, Am7…). Empty if the key isn't understood.
     */
    fun field(key: String, sevenths: Boolean = false): List<Degree> {
        val k = Chords.triad(key) ?: return emptyList()
        val name = Chords.keyName(k.root, k.minor)
        return steps(k.minor).filter { it.shown }.map { st ->
            val note = Chords.noteIn(name, k.root + st.semis)
            if (!sevenths) Degree(st.roman, note + if (st.dim) "°" else if (st.minor) "m" else "")
            else Degree(st.roman7, note + suffix7(st))
        }
    }

    /**
     * Where the chord falls in a key: the scale position (0 = I … 6 = VII) and the degree name with the chord symbol
     * (e.g. "V7(9)").
     */
    data class Place(val index: Int, val label: String)

    private val majorScale = listOf(0, 2, 4, 5, 7, 9, 11)
    private val naturalMinor = listOf(0, 2, 3, 5, 7, 8, 10)
    private val harmonicMinor = listOf(0, 2, 3, 5, 7, 8, 11)   // the major V (E7 in Am)
    private val numerals = listOf("I", "II", "III", "IV", "V", "VI", "VII")

    /**
     * The chord in key [key]: it's only in the field if **all** its notes (triad, seventh, extensions, sus, alterations
     * and the bass) are in the key's scale (user's request, 29/10: looking only at the triad, or only the seventh, gave
     * wrong information). In G: C7M and Csus2 are in the field; C7 (Bb) isn't. In minor, natural or harmonic counts.
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

    /**
     * The chord's degree name in key [key] (e.g. C in G → "IV"; C7M → "IV7M"; Dsus4 → "Vsus4"), or null if it isn't in
     * the field.
     */
    fun degreeIn(chord: String, key: String): String? = placeIn(chord, key)?.label

    /**
     * The keys where the chord shows up, minus the ones where it's the I (the main one already shows separately), major
     * first.
     */
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

    // Roman numeral: uppercase with a major third (or no third), lowercase with a minor third; ° diminished, ø
    // half-diminished; then the rest of the chord symbol (7M, 7, (9), sus4, 6…), without the "m" the lowercase already
    // says
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
