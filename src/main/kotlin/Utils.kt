import kotlin.math.max

const val END_SYMBOL = '$'
const val START_SYMBOL = '#'
const val NONE_SYMBOL = 'ø'

val PATH_FREQ_RATIO = 0.7

val MAX_TRANSFORMATION_RATIO = 0.4

val MAX_TRANSFORMATION_COUNT = 1

val MAX_ADDITIONS_SIZE = 10

val TRIAL_CORRECTIONS_COUNT = 10

val DEFAULT_EM_RATE = 1.0 / 3.0

val ALPHA = DEFAULT_EM_RATE
val BETA = DEFAULT_EM_RATE
val GAMMA = DEFAULT_EM_RATE

val WORD_IMPORTANCE = 2.0

val CHANGE_PENALTY = 60.0
val CHANGES_OK = 1

val QUEUE_SIZE_LIMIT = 300000

val OPT_TRESHOLD_RATIO = 4.0


//5100, 46:36
//29k   47:47

val LONG_QUERY = 12

fun changePenalty(changes: Int) = -(CHANGE_PENALTY * max(0, changes - CHANGES_OK))

val IN_DICTIONARY_BONUCE = 15.0

val ENDING_BONUS = 10.0

val ENDINGS_CNT = 20

val SHORD_LENGTH = 6

val LONG_LENGTH = 12

val TOO_LONG = 18

fun inDictionaryBonuce(word: String) = IN_DICTIONARY_BONUCE * (1.0 - 1.0 / Math.pow(word.length - 1.0, 1.2))

fun Boolean.toInt() = if (this) 1 else 0

fun String.withBounds() = "$START_SYMBOL$this$END_SYMBOL"

fun String.withoutBounds() = this.removePrefix(START_SYMBOL.toString()).removeSuffix(END_SYMBOL.toString())



var DEBUG = false

val RUSSIAN = "ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЁЯЧСМИТЬБЮ"

fun String.isRussian() = this.all { it in RUSSIAN }


var MORE_PRECISION = false