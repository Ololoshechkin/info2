import java.util.*
import kotlin.math.max

typealias FrequenciesMap = MutableMap<String, Double>

class ErrorModel(
    private val alpha: Double = ALPHA,
    private val beta: Double = BETA,
    private val gamma: Double = GAMMA
) {

    private val rate0 = hashMapOf<OperationType, Double>()
    private val rate1 = hashMapOf<OperationType, HashMap<String, Double>>()
    private val rate2 = hashMapOf<OperationType, HashMap<String, HashMap<Char, Double>>>()

    val endings = hashMapOf<String, Int>()

    init {
        OperationType.values().forEach { op ->
            rate0[op] = 0.0
            rate1[op] = hashMapOf()
            rate2[op] = hashMapOf()
        }
    }

    val Edit.newCh
        get() = when (this) {
            is Edit.None -> '_'
            is Edit.Insert -> this.newChar
            is Edit.Remove -> '<'
            is Edit.Replace -> this.newChar
        }

    val Edit.repl get() = "${newCh}${curChar}"

    private fun processEdit(repl: String, prevChar: Char, type: OperationType, cnt: Double) {
        rate0.compute(type) { _, old -> (old ?: 0.0) + cnt }
        rate1
            .getOrPut(type, ::hashMapOf)
            .compute(repl) { _, old -> (old ?: 0.0) + cnt }
        rate2
            .getOrPut(type, ::hashMapOf)
            .getOrPut(repl, ::hashMapOf)
            .compute(prevChar) { _, old -> (old ?: 0.0) + cnt }
    }

    val Edit.type
        get() = when (this) {
            is Edit.None -> OperationType.NONE
            is Edit.Replace -> OperationType.REPLACE
            is Edit.Remove -> OperationType.REMOVE
            is Edit.Insert -> OperationType.INSERT
        }

    private fun train(from: String, to: String, cnt: Double) {
        val edits = calcDp(from, to)

        edits.forEach { edit ->
            processEdit(edit.repl, edit.prevChar, edit.type, cnt)
        }

        if (to.length >= 6)
            for (len in 2..3)
                endings.compute(to.substring(to.length - 1 - len, to.length - 1)) { _, old -> (old ?: 0) + len }
    }

    fun train(trainData: Collection<Correction>) = trainData.forEach { (from, to, cnt) -> train(from, to, cnt) }
        .also {
            while (endings.size > ENDINGS_CNT && endings.isNotEmpty()) {
                endings -= endings.minBy { (_, cnt) -> cnt }!!.key
            }
            endings[""] = Int.MAX_VALUE
        }

    private fun getProbabilityLvl0(edit: Edit) = rate0.getOrDefault(edit.type, 0.0)
    private fun getProbabilityLvl1(edit: Edit) = rate1
        .getOrDefault(edit.type, hashMapOf())
        .getOrDefault(edit.repl, 0.0)

    private fun getProbabilityLvl2(edit: Edit) = rate2
        .getOrDefault(edit.type, hashMapOf())
        .getOrDefault(edit.repl, hashMapOf())
        .getOrDefault(edit.prevChar, 0.0)

    val totalLvl0: Double by lazy { rate0.values.sum() }
    val totalLvl1: Double by lazy { rate1.values.map { it.values.sum() }.sum() }
    val totalLvl2: Double by lazy { rate2.values.map { it.values.map { it.values.sum() }.sum() }.sum() }

    fun getProbability(edit: Edit) =
        getProbabilityLvl0(edit) / totalLvl0 * alpha +
                getProbabilityLvl1(edit) / totalLvl1 * beta +
                getProbabilityLvl2(edit) / totalLvl2 * gamma

    fun correctionProbability(
        from: String,
        to: String,
        probabilities: Map<String, Double>,
        defaultProbability: Double,
        isInDictionary: Boolean
    ): Double {
        val toProb = WORD_IMPORTANCE * Math.log(probabilities[to] ?: defaultProbability)

        val dp = calcDp(from, to)

        val correctionProb = dp
            .fold(1.0) { p, edit ->
                p + Math.log(getProbability(edit))
            }

        val change = changePenalty(dp.sumBy { (it.type != OperationType.NONE).toInt() })

        val dictBonus = (2 * isInDictionary.toInt() - 1) * inDictionaryBonuce(to)

        val properlyEndsBonus = ENDING_BONUS * endings.keys.any { to.endsWith(it + END_SYMBOL) }.toInt()

        val res =  toProb + correctionProb + change + dictBonus + properlyEndsBonus

//        println("correctionProbability ($from -> $to) = $res")

        return res

//        val toProb = probabilities[to] ?: defaultProbability
//        val dp = calcDp(from, to)
//
//        if (dp.sumBy { (it.type != OperationType.NONE).toInt() } > MAX_TRANSFORMATION_COUNT)
//            return -100000.0
//
//        val correctionProb = dp
//            .fold(1.0) { p, edit ->
//                p + Math.log(getProbability(edit))
//            }
//
//        return toProb + Math.log(correctionProb)
    }
}
//-13,7987121995
//-17,0389300824