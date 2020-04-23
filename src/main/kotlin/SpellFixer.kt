import java.util.*
import kotlin.collections.HashMap


class SpellFixer(
    private val trie: Trie,
    private val errorModel: ErrorModel,
    frequencies: FrequenciesMap,
    val expectedWords: Set<String>,
    val trainData: HashMap<String, String>
) {
    var totalWords: Double = 0.0

    val wordProbabilities = mutableMapOf<String, Double>()
    var averageProb: Double
    val LOWEST_PROBABILITY_POSSIBLE: Double

    val treshold: Double


    init {
        println("learning half-words")
        frequencies.forEach { (w, freqW) ->
            totalWords += freqW

            wordProbabilities.compute(w) { _, old -> (old ?: 0.0) + freqW }
            (3 until w.length - 3).forEach { i ->
                val first = w.substring(0, i)
                val second = w.substring(i, w.length)

                wordProbabilities.compute(first) { _, old -> (old ?: 0.0) + freqW }
                wordProbabilities.compute(second) { _, old -> (old ?: 0.0) + freqW }
            }
        }
        println(totalWords)
        wordProbabilities.keys.forEach { wordProbabilities[it] = wordProbabilities[it]!! / totalWords }
        println("finished half-words")
        averageProb = wordProbabilities.values.average()
        println("min probability: ${wordProbabilities.values.min()}")
        println("avg probability: $averageProb (total: $totalWords)")
        LOWEST_PROBABILITY_POSSIBLE = 1.0 / totalWords

        treshold = trainData
            .filter { it.key != it.value }
            .map {
                errorModel.correctionProbability(
                    from = it.key,
                    to = it.value,
                    probabilities = wordProbabilities,
                    defaultProbability = LOWEST_PROBABILITY_POSSIBLE,
                    isInDictionary = it.value in expectedWords
                )
            }
            .average() * OPT_TRESHOLD_RATIO

        println(treshold)
    }

    internal class Path(
        var pos: Int,
        var node: Node,
        var histProb: Double,
        var prob: Double,
        var transformationCount: Int
    ) : Comparable<Path> {
        override fun compareTo(other: Path) = java.lang.Double.compare(other.prob, this.prob)

        fun isNotTooLongFor(q: String): Boolean {
            return transformationCount.toDouble() <= MAX_TRANSFORMATION_RATIO * q.length.toDouble()
        }
    }

    private fun getTransformations(pi: Path, q: String): List<Edit> {
        val t = mutableListOf<Edit>()
        val bound = pi.node.children.map { it.frequency }.average() * PATH_FREQ_RATIO
        val prev = q.prev(pi.pos)
        pi.node.children.forEach {
            val differs = it.symbol != q[pi.pos]
            if (!differs) {
                t += Edit.None(curChar = it.symbol, prevChar = prev)
            }
            if (it.frequency >= bound) {
                t += Edit.Insert(newChar = it.symbol, prevChar = prev)

                if (differs) t += Edit.Replace(
                    newChar = it.symbol,
                    curChar = q[pi.pos],
                    prevChar = prev
                )
            }
        }
        t += Edit.Remove(curChar = q[pi.pos], prevChar = prev)

        return t
    }

    private fun getExtensions(pi: Path, q: String): List<Edit> {
        val bound = PATH_FREQ_RATIO * pi.node.frequency
        val prev = q.prev(pi.pos)

        return pi.node.children.mapNotNull {
            if (it.frequency >= bound)
                Edit.Insert(newChar = it.symbol, prevChar = prev)
            else
                null
        }
    }

    private val reachedWords = hashMapOf<Pair<String, Int>, Double>()

    private fun Collection<Path>.filterProb(): Collection<Path> = this.filter {
        //        true
        val word = it.node.prefix

//        if (word !in reachedWords) {
//            reachedWords += word
//            val edits = calcDp(from = q.substring(0, word.length), to = word)
//            val h = edits.map { Math.log(errorModel.getProbability(it)) }.sum()
//            val p =
//            Path(it.pos, it.node, )
//        } else null
        val key = word to it.pos
        val bestProb = reachedWords[key]
        if (bestProb == null) {
            if (word.debug()) {
                println("prefix : $word -- OK")
            }
            reachedWords[key] = it.prob
            true
        } else if (bestProb < it.prob) {
            if (word.debug()) {
                println("prefix : $word -- OK (upd)")
            }
            reachedWords[key] = it.prob
            true
        } else {
            if (word.debug()) {
                println("prefix : $word -- NOT-OK")
            }
            false
        }
    }

    fun String.debug() = false //this == "#ЧЕЛЮСТНОЙ\$____________________".substring(0, this.length)

    private fun List<Edit>.process(pi: Path, q: String): Collection<Path> {
        val additions = TreeSet<Path>()

        this.forEach { t ->
            val i = pi.pos + (t !is Edit.Insert).toInt()
            val n = when (t) {
                is Edit.None -> pi.node.next[q[pi.pos]]
                is Edit.Remove -> pi.node
                is Edit.Insert -> pi.node.next[t.newChar]
                is Edit.Replace -> pi.node.next[t.newChar]
            }
            val h = pi.histProb + Math.log(errorModel.getProbability(t))
            val p = pi.prob + Math.log(n!!.frequency / pi.node.frequency) + h
            val newPath = Path(i, n, h, p, pi.transformationCount + (t !is Edit.None).toInt())


            if (n.prefix.debug()) {
                println("prefix : ${n.prefix} , ${newPath.isNotTooLongFor(q)}, ${additions.size}")
            }

            if (newPath.isNotTooLongFor(q)) {
                additions += newPath
                if (additions.size > MAX_ADDITIONS_SIZE) additions.remove(additions.max())
            }
        }

        return additions
    }

    private fun getCorrections(k: Int, q: String): List<String> {
        reachedWords.clear()
        val l = mutableListOf<String>()
        val pq = PriorityQueue<Path>()
        pq += Path(0, trie, Math.log(1.0), Math.log(1.0), 0)

        while (!pq.isEmpty() && pq.size < QUEUE_SIZE_LIMIT) {
            val pi = pq.poll()
            if (pi.pos < q.length) {
                pq += getTransformations(pi, q).process(pi, q).filterProb()
            } else {
                if (pi.node.isLeaf) {
                    l += pi.node.prefix
                    if (l.size >= k) return l
                } else {
                    pq += getExtensions(pi, q).process(pi, q).filterProb()
                }
            }
        }
        return l
    }

    private fun getCorrectionsSubworded(q: String) = (3 until q.length - 3).mapNotNull { i ->
        val first = "${q.substring(0, i)}$END_SYMBOL"
        val delimeter = q[i]
        val second = "$START_SYMBOL${q.substring(i + 1, q.length)}"

        var (qualityFirst, firstFixed) = fix_with_simple_edits(first) ?: return@mapNotNull null
        if (qualityFirst < treshold) return@mapNotNull null
        firstFixed = firstFixed.withoutBounds()

        var (qualitySecond, secondFixed) = fix_with_simple_edits(second) ?: return@mapNotNull null
        if (qualitySecond < treshold) return@mapNotNull null
        secondFixed = secondFixed.withoutBounds()

        val to = "$firstFixed$delimeter$secondFixed".withBounds()
        if (wordProbabilities.getOrDefault(to, 0.0) >= averageProb * 8
            && errorModel.endings.none { (suf, _) -> q.endsWith(suf) && !to.endsWith(suf) }
            && dist(q, to) <= MAX_TRANSFORMATION_COUNT
        )
            to
        else
            null
    }

    private fun chooseCorrection(from: String, tos: Collection<String>, withEndings: Boolean = false) = tos
        .map { to ->
            if (withEndings)
                errorModel.endings.keys.mapNotNull {
                    var newWord = to.removeSuffix(END_SYMBOL.toString())
                    if (it == "")
                        newWord += END_SYMBOL
                    else if (newWord.endsWith(it[0]))
                        newWord = newWord + it.substring(1) + END_SYMBOL

                    Pair(
                        correctionProbability(from, newWord), newWord
                    )
                }
            else
                listOf(Pair(correctionProbability(from, to), to))

        }
        .flatten()
        .filter { (_, to) ->
            wordProbabilities.getOrDefault(to, 0.0) >= averageProb * 8
                    && errorModel.endings.none { (suf, _) -> from.endsWith(suf) && !to.endsWith(suf) }
                    && dist(from, to) <= MAX_TRANSFORMATION_COUNT
        }
        .maxBy { it.first }

    private fun fixNoRecursion(query: String): Pair<Double, String>? = when {
        query in expectedWords -> Pair(1.0, query)
        query in trainData -> Pair(1.0, trainData[query]!!)
        query.withoutBounds().toIntOrNull() != null -> Pair(1.0, "")
        else -> chooseCorrection(
            from = query,
            tos = getCorrections(TRIAL_CORRECTIONS_COUNT / 3, query) + query
        )
    }

//    fun fix(query: String) =
//        chooseCorrection(
//            from = query,
//            tos = getAllCorrections(query).toSet().also { println("getCorrections(\"$query\") = $it") }
//        ).also {
//            if (it != query) {
//                println("$query -> $it")
//            }
//        }

    fun correctionProbability(from: String, to: String) = errorModel.correctionProbability(
        from, to,
        wordProbabilities,
        LOWEST_PROBABILITY_POSSIBLE,
        to in expectedWords
    )

    fun tree_corrections(query: String) = getCorrections(TRIAL_CORRECTIONS_COUNT, query)
    fun tree_corrections_with_halfs(query: String) = when {
        query.length >= LONG_LENGTH && query.length < TOO_LONG -> getCorrectionsSubworded(query)
        else -> listOf()
    }

    fun simple_edits(query: String): Set<String> {
        val res = mutableSetOf<String>()

        fun upd(fixed: String) {
            if (fixed in expectedWords) {
                res += fixed
            }
        }
        for (i in 1 until query.length - 1) {
            OperationType.values().forEach { op ->
                when (op) {
                    OperationType.NONE -> {
                    }
                    OperationType.INSERT -> {
                        for (c in RUSSIAN) {
                            val fixed = "${query.substring(0, i)}$c${query.substring(i, query.length)}"
                            upd(fixed)
                        }
                    }
                    OperationType.REMOVE -> {
                        val fixed = "${query.substring(0, i - 1)}${query.substring(i, query.length)}"
                        upd(fixed)
                    }
                    OperationType.REPLACE -> {
                        for (c in RUSSIAN) {
                            val fixed = "${query.substring(0, i - 1)}$c${query.substring(i, query.length)}"
                            upd(fixed)
                        }
                    }
                }
            }
        }

        return res
    }

    fun fix_with_simple_edits(query: String) = chooseCorrection(query, simple_edits(query))

    fun fix_with_tree(query: String): String {
        val corrections = getCorrections(TRIAL_CORRECTIONS_COUNT, query) + query

        val (quality, bestCorr) = chooseCorrection(
            from = query,
            tos = corrections,
            withEndings = false
        ) ?: return query

        val result = if (quality < treshold && query.length >= LONG_QUERY) {
            chooseCorrection(
                from = query,
                tos = getCorrectionsSubworded(query) + bestCorr
            )?.second ?: query

        } else bestCorr


        return result
    }

    fun fix_optimized(query: String): String =
        when {
            query in expectedWords -> query
            query in trainData -> trainData[query]!!
            query.withoutBounds().toIntOrNull() != null -> query
            !query.withoutBounds().isRussian() -> query
            wordProbabilities.getOrDefault(query, 0.0) > averageProb / 4 -> query
            query.length <= SHORD_LENGTH -> query
            else -> //chooseCorrection(query, simple_edits(query))?.second ?:
                chooseCorrection(query, tree_corrections(query))?.second
                    ?: (if (MORE_PRECISION) chooseCorrection(query, tree_corrections_with_halfs(query))?.second else null)
                    ?: query
        }

}