package br.app.stagebook.domain

/**
 * Lines equal in two versions of a text, in order (longest common subsequence). One function for the lyrics view and
 * the song merge (it was written twice), with the lines equal at the start and at the end matched directly: a single
 * keystroke compares only the changed stretch instead of every line against every line (audit 30/10). A changed
 * stretch too big for the table ([MAX_CELLS]) counts as replaced, never as an out-of-memory crash.
 */
object LineDiff {
    const val MAX_CELLS = 4_000_000L   // ~16 MB of ints, far above any real song

    fun pairs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) { endA--; endB-- }
        val out = ArrayList<Pair<Int, Int>>()
        for (k in 0 until start) out += k to k
        val n = endA - start
        val m = endB - start
        if (n > 0 && m > 0 && n.toLong() * m <= MAX_CELLS) {
            val t = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
                t[i][j] = if (a[start + i] == b[start + j]) t[i + 1][j + 1] + 1 else maxOf(t[i + 1][j], t[i][j + 1])
            }
            var i = 0
            var j = 0
            while (i < n && j < m) {
                when {
                    a[start + i] == b[start + j] -> { out += (start + i) to (start + j); i++; j++ }
                    t[i + 1][j] >= t[i][j + 1] -> i++
                    else -> j++
                }
            }
        }
        for (k in 0 until a.size - endA) out += (endA + k) to (endB + k)
        return out
    }
}
