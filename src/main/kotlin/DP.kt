import kotlin.math.min

enum class OperationType {
    NONE, INSERT, REMOVE, REPLACE
}

abstract sealed class Edit(val curChar: Char, val prevChar: Char) {
    class None(curChar: Char, prevChar: Char) : Edit(curChar, prevChar)
    class Insert(val newChar: Char, prevChar: Char) : Edit(NONE_SYMBOL, prevChar)
    class Remove(curChar: Char, prevChar: Char) : Edit(curChar, prevChar)
    class Replace(val newChar: Char, curChar: Char, prevChar: Char) : Edit(curChar, prevChar)
}

fun String.prev(i: Int) = if (i == 0) NONE_SYMBOL else this[i - 1]

fun calcDp(from: String, to: String): List<Edit> {
    val n = from.length
    val m = to.length

    val dp = Array(n) { Array(m) { Int.MAX_VALUE } }
    val operation = Array(n) { Array<Edit?>(m) { null } }

    dp[0][0] = 0
    (1 until m).forEach { j ->
        dp[0][j] = j
        operation[0][j] = Edit.Insert(newChar = to[j - 1], prevChar = NONE_SYMBOL)
    }
    (1 until n).forEach { i ->
        dp[i][0] = i
        operation[i][0] = Edit.Remove(curChar = from[i - 1], prevChar = from.prev(i - 1))
    }
    (1 until n).forEach { i ->
        (1 until m).forEach { j ->
            val notEquals = 1.takeIf { from[i - 1] != to[j - 1] } ?: 0
            val removeDp = dp[i - 1][j] + 1
            val insertDp = dp[i][j - 1] + 1
            val changeIfNeededDp = dp[i - 1][j - 1] + notEquals

            when {
                removeDp <= min(insertDp, changeIfNeededDp) -> {
                    dp[i][j] = removeDp
                    operation[i][j] = Edit.Remove(curChar = from[i - 1], prevChar = from.prev(i - 1))
                }
                insertDp <= min(removeDp, changeIfNeededDp) -> {
                    dp[i][j] = insertDp
                    operation[i][j] =
                        Edit.Insert(newChar = to[j - 1], prevChar = from.prev(i - 1))
                }
                changeIfNeededDp <= min(insertDp, removeDp) -> {
                    dp[i][j] = changeIfNeededDp
                    operation[i][j] =
                        if (from[i - 1] == to[j - 1])
                            Edit.None(curChar = from[i - 1], prevChar = from.prev(i - 1))
                        else
                            Edit.Replace(newChar = to[j - 1], curChar = from[i - 1], prevChar = from.prev(i - 1))
                }
            }
        }
    }

    var i = n - 1
    var j = m - 1
    val result = mutableListOf<Edit>()

    while (i != 0 || j != 0) {
        val edit = operation[i][j]
        when (edit) {
            is Edit.None, is Edit.Replace -> {
                i--
                j--
            }
            is Edit.Insert -> {
                j--
            }
            is Edit.Remove -> {
                i--
            }
        }
        result += edit!!
    }

    return result.reversed()
}

fun dist(from: String, to: String) = calcDp(from, to).sumBy { (it !is Edit.None).toInt() }