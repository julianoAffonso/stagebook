package br.app.stagebook.domain

import br.app.stagebook.data.model.Instrument

/**
 * How to play a chord on the instrument: string positions (acoustic/electric guitar, ukulele, cavaquinho) or keyboard
 * notes, from easiest to hardest. Generated from the chord's notes ([ChordTheory]), with no ready-made table.
 */
object Voicings {
    /** A shape on the neck: each string's fret, from lowest to highest (-1 = not played, 0 = open). */
    data class Shape(val frets: List<Int>) {
        /** First fret of the diagram: 1 if it fits in the first 4 frets, otherwise the lowest fretted one. */
        val baseFret: Int get() = frets.filter { it > 0 }.let { f -> if (f.isEmpty() || f.max() <= 4) 1 else f.min() }
    }

    /**
     * How a shape sits on the neck with a [capo]. The capo gets a fret of its own on top of the grid and the shape comes
     * right below it ([fromCapo]; [base] 0, so a shape's fret is its row); a shape too high for that keeps its own start.
     * [label]: the real fret of the first row, the capo's when it is there.
     */
    data class NeckView(val base: Int, val fromCapo: Boolean, val label: Int?)

    fun neckView(shape: Shape, capo: Int, rows: Int): NeckView {
        val base = shape.baseFret
        val fromCapo = capo > 0 && (base == 1 || shape.frets.max() <= rows)
        return when {
            fromCapo -> NeckView(0, true, capo)
            base > 1 -> NeckView(base, false, base + capo)
            else -> NeckView(base, false, null)
        }
    }

    // tunings in MIDI notes, from the lowest string (on the neck) to the highest
    private val tunings = mapOf(
        Instrument.ACOUSTIC_GUITAR to intArrayOf(40, 45, 50, 55, 59, 64),     // E A D G B E
        Instrument.ELECTRIC_GUITAR to intArrayOf(40, 45, 50, 55, 59, 64),
        Instrument.UKULELE to intArrayOf(67, 60, 64, 69),            // G C E A (high G)
        Instrument.CAVAQUINHO to intArrayOf(62, 67, 71, 74),         // D G B D
    )

    fun strings(i: Instrument): Int = tunings[i]?.size ?: 0

    /**
     * The notes (MIDI) the diagram sounds, from low string to high; [capo] raises everything (the real note with capo).
     */
    fun midi(shape: Shape, instrument: Instrument, capo: Int = 0): List<Int> {
        val tuning = tunings[instrument] ?: return emptyList()
        return shape.frets.mapIndexedNotNull { i, f -> if (f < 0 || i >= tuning.size) null else tuning[i] + f + capo }
    }

    /**
     * The chord's shapes on a string instrument, from easiest to hardest (empty on keyboard). Remembered: the stage's
     * chord list searched every chord again on each song, on the main thread (audit 01/11).
     */
    fun shapes(chord: String, instrument: Instrument, max: Int = MAX_SHAPES): List<Shape> {
        if (max > MAX_SHAPES) return search(chord, instrument, max)
        val key = "$chord|$instrument"
        val all = synchronized(cache) { cache[key] } ?: search(chord, instrument, MAX_SHAPES).also { synchronized(cache) { cache[key] = it } }
        return if (max >= all.size) all else all.take(max)
    }

    private const val MAX_SHAPES = 12
    private val cache = object : LinkedHashMap<String, List<Shape>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Shape>>) = size > 128
    }

    private fun search(chord: String, instrument: Instrument, max: Int): List<Shape> {
        val tuning = tunings[instrument] ?: return emptyList()
        val notes = ChordTheory.notes(chord) ?: return emptyList()
        val six = tuning.size == 6
        val found = HashMap<List<Int>, Double>()
        val current = IntArray(tuning.size)
        for (window in 1..10) {
            val options = tuning.map { open ->
                buildList {
                    if (six) add(-1)
                    if ((open % 12) in notes.tones) add(0)
                    for (f in window..window + 3) if ((open + f) % 12 in notes.tones) add(f)
                }
            }
            fun walk(s: Int) {
                if (s == tuning.size) {
                    score(current, tuning, notes, six)?.let { sc ->
                        val key = current.toList()
                        if (sc < (found[key] ?: Double.MAX_VALUE)) found[key] = sc
                    }
                    return
                }
                for (o in options[s]) { current[s] = o; walk(s + 1) }
            }
            walk(0)
        }
        return found.entries.sortedBy { it.value }.take(max).map { Shape(it.key) }
    }

    /** Is each shape's note playable? Returns the difficulty (lower = easier) or null if it won't work. */
    private fun score(f: IntArray, tuning: IntArray, notes: ChordTheory.Notes, six: Boolean): Double? {
        val n = f.size
        val sounding = f.indices.filter { f[it] >= 0 }
        if (sounding.size < (if (six) 4 else n)) return null
        // muted strings only on the low side, in a row (nobody mutes a middle string)
        if (six) {
            val firstSounding = sounding.first()
            if (sounding.size != n - firstSounding) return null
        }
        val pcs = sounding.map { (tuning[it] + f[it]) % 12 }.toSet()
        if (!pcs.containsAll(notes.essential)) return null
        // on guitar the lowest note is the chord's bass (the root, or the bass after the slash)
        if (six && (tuning[sounding.first()] + f[sounding.first()]) % 12 != notes.bass) return null
        val fretted = sounding.filter { f[it] > 0 }
        val lo = fretted.minOfOrNull { f[it] } ?: 0
        val hi = fretted.maxOfOrNull { f[it] } ?: 0
        if (hi - lo > 3) return null
        // barre only when fingers run out (more than 4 fretted notes): the lowest fret across several strings, with no
        // open string in between, becomes a single finger
        val fingers = if (fretted.size <= 4) fretted.size else if (needsBarre(f)) 1 + fretted.count { f[it] > lo } else fretted.size
        if (fingers > 4) return null
        val muted = n - sounding.size
        val open = sounding.count { f[it] == 0 }
        val noFifth = if ((notes.root + 7) % 12 in notes.tones && (notes.root + 7) % 12 !in pcs) 0.5 else 0.0
        // finger stretch (lowest to highest fret) counts: x50232 is playable, but nobody plays D that way
        return lo * 3.0 + muted * 2.0 + fingers + (hi - lo) - open * 0.5 + noFifth
    }

    /**
     * Does the shape need a barre? More than 4 fretted notes and the lowest fret across several strings, with no open
     * string in between.
     */
    fun needsBarre(f: IntArray): Boolean {
        val fretted = f.indices.filter { f[it] > 0 }
        if (fretted.size <= 4) return false
        val lo = fretted.minOf { f[it] }
        val atLo = fretted.filter { f[it] == lo }
        return atLo.size >= 2 && (atLo.first()..atLo.last()).none { f[it] == 0 }
    }

    /** Keyboard: the chord's notes (MIDI), in root position and inversions. */
    fun keys(chord: String): List<List<Int>> {
        val notes = ChordTheory.notes(chord) ?: return emptyList()
        val upper = (notes.tones - notes.bass).sortedBy { (it - notes.root + 12) % 12 }
        val base = listOf(notes.bass) + upper
        return base.indices.take(3).map { rot ->
            val order = base.drop(rot) + base.take(rot)
            var last = 59
            order.map { pc -> var m = 60 + pc; while (m <= last) m += 12; last = m; m }.map { it - if (order.first() >= 5) 12 else 0 }
        }.distinct()
    }

    // ---- ChordPro {define}: the chosen shape saved in the song ----

    /** `{define-guitar: G base-fret 1 frets 3 2 0 0 0 3}` (frets relative to the base, x = not played). */
    fun define(instrument: Instrument, chord: String, shape: Shape): String {
        val base = shape.baseFret
        val frets = shape.frets.joinToString(" ") { if (it < 0) "x" else if (it == 0) "0" else (it - base + 1).toString() }
        return "{define-${instrument.chordPro}: $chord base-fret $base frets $frets}"
    }

    /** `{define-keyboard: C keys 0 4 7}` (notes from the first one, in semitones). */
    fun defineKeys(chord: String, keys: List<Int>): String =
        "{define-keyboard: $chord keys ${keys.joinToString(" ") { (it - keys.first()).toString() }}}"

    private val SPACES = Regex("\\s+")
    private val defineLine = Regex("""^\s*\{\s*define-(\w+)\s*:\s*(\S+)\s+(.*)\}\s*$""")

    /** The shape saved in the song for the chord, if any (strings); null if there isn't one. */
    fun savedShape(text: String, instrument: Instrument, chord: String): Shape? {
        val m = text.lineSequence().mapNotNull { defineLine.matchEntire(it) }
            .firstOrNull { it.groupValues[1] == instrument.chordPro && it.groupValues[2] == chord }
            // no shape picked on stage: the one written in the song after the chord ("B/F#  224442"), on guitar
            ?: return if (instrument.chordPro == "guitar") Chords.shapeHint(text, chord)?.let(::Shape) else null
        val parts = m.groupValues[3].split(SPACES)
        val base = parts.getOrNull(parts.indexOf("base-fret") + 1)?.toIntOrNull() ?: 1
        val i = parts.indexOf("frets")
        if (i < 0) return null
        val frets = parts.drop(i + 1).take(strings(instrument)).map { if (it == "x" || it == "X") -1 else it.toIntOrNull()?.let { r -> if (r == 0) 0 else r + base - 1 } ?: return null }
        return if (frets.size == strings(instrument)) Shape(frets) else null
    }

    /** The notes saved in the song for the chord on keyboard, if any. */
    fun savedKeys(text: String, chord: String): List<Int>? {
        val m = text.lineSequence().mapNotNull { defineLine.matchEntire(it) }
            .firstOrNull { it.groupValues[1] == "keyboard" && it.groupValues[2] == chord } ?: return null
        val parts = m.groupValues[3].split(SPACES)
        val i = parts.indexOf("keys")
        if (i < 0) return null
        val rel = parts.drop(i + 1).mapNotNull { it.toIntOrNull() }
        val first = 60 + (ChordTheory.notes(chord)?.bass ?: 0)
        return rel.map { first + it }
    }

    /** Saves (replaces) the chosen shape in the song, at the start of the text. */
    fun saveDefine(text: String, instrument: Instrument, chord: String, line: String): String {
        val lines = text.split('\n').filterNot { l ->
            defineLine.matchEntire(l)?.let { it.groupValues[1] == instrument.chordPro && it.groupValues[2] == chord } == true
        }
        return (listOf(line) + lines).joinToString("\n")
    }
}
