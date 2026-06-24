package com.quietping.domain.rules

/**
 * Case-insensitive, word-boundary-aware multi-pattern string matcher built on the
 * Aho-Corasick automaton.
 *
 * One automaton is compiled from a set of keyword/phrase patterns; afterwards a
 * single linear scan over any input text reports every pattern that occurs — total
 * cost O(textLength + totalMatches), independent of how many patterns were added.
 * This is what lets the [RuleEngine] evaluate dozens of keyword rules against each
 * incoming notification without per-rule regex passes.
 *
 * Matching semantics:
 *  - **Case-insensitive.** Both patterns and input are folded to lowercase.
 *  - **Word-boundary aware.** A hit only counts when the character immediately
 *    before and after the match is a non-word character (or text edge), so the
 *    pattern "ann" does not match inside "annual". Word characters are
 *    `[A-Za-z0-9_]`. Multi-token phrases (containing spaces/punctuation) are
 *    matched verbatim with the same boundary rule on their outer edges.
 *  - **Fail safe.** Blank patterns are ignored; an automaton with no usable
 *    patterns matches nothing.
 *
 * Instances are immutable after [build] and safe to reuse across threads.
 */
class AhoCorasick private constructor(
    private val goto: Array<HashMap<Char, Int>>,
    private val fail: IntArray,
    /** For each state, the lengths of patterns that end there (via output links). */
    private val outputLengths: Array<IntArray>,
    private val hasAnyPattern: Boolean
) {

    /**
     * @return true if any compiled pattern occurs in [text] respecting word
     * boundaries. Returns false for an empty automaton or blank [text].
     */
    fun containsMatch(text: String): Boolean {
        if (!hasAnyPattern || text.isEmpty()) return false
        val lower = text.lowercase()
        var state = ROOT
        for (i in lower.indices) {
            val c = lower[i]
            state = step(state, c)
            val ends = outputLengths[state]
            if (ends.isNotEmpty()) {
                for (len in ends) {
                    val start = i - len + 1
                    if (isWordBoundaryMatch(lower, start, i)) return true
                }
            }
        }
        return false
    }

    /**
     * @return the distinct set of matched pattern lengths' end indices is not
     * exposed; instead this returns true once any match is found. Provided as a
     * convenience alias mirroring [containsMatch] for call-site readability.
     */
    fun matches(text: String): Boolean = containsMatch(text)

    /** Follow goto edges, falling back along the failure links on a miss. */
    private fun step(from: Int, c: Char): Int {
        var state = from
        while (state != ROOT && goto[state][c] == null) {
            state = fail[state]
        }
        return goto[state][c] ?: ROOT
    }

    private fun isWordBoundaryMatch(text: String, start: Int, endInclusive: Int): Boolean {
        val before = if (start - 1 >= 0) text[start - 1] else null
        val after = if (endInclusive + 1 < text.length) text[endInclusive + 1] else null
        // Boundary only matters where the pattern edge is itself a word char;
        // a pattern that starts/ends with punctuation imposes no boundary there.
        val leftOk = !isWordChar(text[start]) || before == null || !isWordChar(before)
        val rightOk =
            !isWordChar(text[endInclusive]) || after == null || !isWordChar(after)
        return leftOk && rightOk
    }

    companion object {
        private const val ROOT = 0

        private fun isWordChar(c: Char): Boolean =
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

        /**
         * Compile an automaton from [patterns]. Blank entries are dropped and
         * patterns are de-duplicated (after lowercasing). The result is immutable.
         */
        fun build(patterns: Collection<String>): AhoCorasick {
            val cleaned = patterns
                .asSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()

            // Trie nodes grow dynamically; index 0 is the root.
            val gotoList = ArrayList<HashMap<Char, Int>>()
            gotoList.add(HashMap())
            // Pattern lengths terminating exactly at each node (pre-output-merge).
            val endLengths = ArrayList<MutableList<Int>>()
            endLengths.add(mutableListOf())

            for (pat in cleaned) {
                var cur = ROOT
                for (ch in pat) {
                    val next = gotoList[cur][ch]
                    if (next == null) {
                        val created = gotoList.size
                        gotoList.add(HashMap())
                        endLengths.add(mutableListOf())
                        gotoList[cur][ch] = created
                        cur = created
                    } else {
                        cur = next
                    }
                }
                endLengths[cur].add(pat.length)
            }

            val n = gotoList.size
            val fail = IntArray(n) { ROOT }
            // Output set per node = own terminations ∪ output(fail(node)).
            val output = Array(n) { mutableSetOf<Int>() }
            for (i in 0 until n) output[i].addAll(endLengths[i])

            // BFS over the trie to wire failure links and merge outputs.
            val queue = ArrayDeque<Int>()
            for ((_, child) in gotoList[ROOT]) {
                fail[child] = ROOT
                queue.add(child)
            }
            while (queue.isNotEmpty()) {
                val state = queue.removeFirst()
                for ((ch, child) in gotoList[state]) {
                    queue.add(child)
                    var f = fail[state]
                    while (f != ROOT && gotoList[f][ch] == null) {
                        f = fail[f]
                    }
                    fail[child] = gotoList[f][ch]?.takeIf { it != child } ?: ROOT
                    output[child].addAll(output[fail[child]])
                }
            }

            val outputLengths = Array(n) { output[it].toIntArray() }
            val goto = Array(n) { gotoList[it] }
            return AhoCorasick(goto, fail, outputLengths, cleaned.isNotEmpty())
        }
    }
}
