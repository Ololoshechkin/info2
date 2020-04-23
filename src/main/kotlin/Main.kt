import java.io.File

data class Correction(val from: String, val to: String, val cnt: Double)

fun File.forEachLineWithout(exception: String, block: (String) -> Unit) = forEachLine { line ->
    if (line != exception) {
        block(line)
    }
}

fun main() {
    /**
     * Note: set to true to enable subwording heuristics to increase precision of predictions.
     * Caveat: this will slow down all the calculations by ~ 5-10 times.
    */
    MORE_PRECISION = false


    val trainData = hashMapOf<String, String>()

    File("train.csv").forEachLineWithout("Id,Expected") { line ->
        val arr = line.split(',')
        trainData[arr[0].withBounds()] = arr[1].withBounds()
    }

    val expectedWords = trainData.values.toSet()

    val wordFrequencies = mutableListOf<WordInfo>()
    File("words.csv").forEachLineWithout("Id,Freq") { line ->
        val arr = line.split(',')
        val word = arr[0].withBounds()
        val frequency = arr[1].toDouble()
        wordFrequencies += WordInfo(word, frequency)
    }

    val trie = Trie()

    trie.insertMultiple(*wordFrequencies.filter {
        it.word in expectedWords && it.word.withoutBounds().isRussian()
    }.toTypedArray())

    val word2Frequency = wordFrequencies.associate { Pair(it.word, it.frequency) }.toMutableMap()

    val model = ErrorModel()
    model.train(trainData.map { (from, to) ->
        Correction(from, to, word2Frequency[from] ?: 1.0)//word2Frequency[from] ?: throw Exception("FUCK: $from"))
    })

    println("ENDS: " + model.endings)

    val fixer = SpellFixer(trie, model, word2Frequency, expectedWords, trainData)

    var totalFixed = 0
    var totalWords = 0

    File("with_fix.submission_11.csv").bufferedWriter().use { output ->
        output.write("Id,Predicted\n")

        println("wordFrequencies ${wordFrequencies.size}")
        var cnt = 0
        File("no_fix.submission.csv").forEachLineWithout("Id,Predicted") { line ->
            val query = line.split(',')[0].withBounds()
            totalWords++

            val result = fixer.fix_optimized(query).also {
                if (it != query) totalFixed++
            }
            output.write("${query.withoutBounds()},${result.withoutBounds()}\n")
            if (cnt % 100 == 0)
                println("$cnt (${100.0 * totalFixed.toDouble() / totalWords.toDouble()}%)")
            cnt++
        }
    }

    println("fixed: $totalFixed out of $totalWords (${100.0 * totalFixed.toDouble() / totalWords.toDouble()}%)")
}

