import java.util.*

data class Suffix(var node: Node, var text: String, var probability: Double)

data class WordInfo(val word: String, val frequency: Double)

abstract class Node(var frequency: Double) {
    abstract val prefix: String

    val next = hashMapOf<Char, IntermediateNode>()

    val children: Collection<IntermediateNode> = next.values

    val subtree: Sequence<Pair<Node, Int>> by lazy {
        children.asSequence().map { it.subtree.map { (n, d) -> Pair(n, d + 1) } }.flatten() + Pair(this, 0)
    }

    fun search(suffix: String): Node? {
        var cur = this
        suffix.forEach { c ->
            cur = cur.next[c] ?: return null
        }
        return cur
    }


    open fun insert(info: WordInfo) {
        val (word, wordFrequency) = info
        var cur = this
        word.forEach { c ->
            cur.frequency += wordFrequency
            cur = cur.next.getOrPut(c) { IntermediateNode(cur, c, 0.0) }
        }
        cur.frequency += wordFrequency
    }

    fun insertMultiple(vararg infos: WordInfo) {
        infos.forEach(this::insert)
    }

    fun getSuffixes(maxDepth: Int) = sequence<Suffix> {
        fun suffix(n: Node) = Suffix(
            node = n,
            text = n.prefix.removePrefix(prefix),
            probability = n.frequency / this@Node.frequency
        )

        yield(suffix(this@Node))

        val queue = ArrayDeque<Pair<Node, Int>>(children.size)
        queue += children.map { Pair(it, 1) }

        while (queue.isNotEmpty()) {
            val (cur, depth) = queue.pop()
            if (depth > maxDepth) continue
            yield(suffix(cur))
            queue += cur.children.map { Pair(it, depth + 1) }
        }
    }

}

open class RootNode : Node(0.0) {
    override val prefix = ""
}

class IntermediateNode(val parent: Node, val symbol: Char, frequency: Double) : Node(frequency) {
    val parents: List<IntermediateNode> by lazy {
        var cur: Node = this@IntermediateNode
        val res = mutableListOf<IntermediateNode>()
        while (cur is IntermediateNode) {
            res += cur
            cur = cur.parent
        }
        res
    }

    override val prefix: String by lazy { parents.joinToString(separator = "") { it.symbol.toString() }.reversed() }
}

val Node.isLeaf get() = this is IntermediateNode && this.symbol == END_SYMBOL

class Trie : RootNode() {

    fun estimatedProbability(word: String) = search(word)?.let { it.frequency / this.frequency }

    fun contains(word: String) = search(word) != null
}