import junit.framework.TestCase
import org.junit.Test
import java.io.File
import java.lang.Exception

class SomeTest : TestCase() {

    init {
        DEBUG = true
    }


    @Test
    fun testTrie() {
        val w2f = hashMapOf(
            "koko" to 2,
            "ololo" to 10,
            "lol" to 15,
            "abacaba" to 1
        )

        val prt = Trie()

        prt.insertMultiple(*w2f.map { (w, f) -> WordInfo(w, f.toDouble()) }.toTypedArray())

        w2f.forEach { (w, f) ->
            assertEquals(true, prt.contains(w))

            assertEquals(f, prt.search(w)?.frequency)
            assertEquals(f / w2f.values.sum(), prt.estimatedProbability(w))
        }

    }

    // SHOULD FAIL
    @Test
    fun testEM() {
        val trainData = hashMapOf<String, String>()

        File("train.csv").forEachLineWithout("Id,Expected") { line ->
            val arr = line.split(',')
            trainData[arr[0].withBounds()] = arr[1].withBounds()
        }

        val word2Frequency = hashMapOf<String, Double>()
        File("words.csv").forEachLineWithout("Id,Freq") { line ->
            val arr = line.split(',')
            val word = arr[0].withBounds()
            val frequency = arr[1].toDouble()

            word2Frequency[word] = frequency
        }

        val model = ErrorModel()
        model.train(trainData.map { (from, to) -> Correction(from, to, word2Frequency[from]!!) })

        assertEquals(
            Math.log(model.getProbability(Edit.Replace(newChar = 'А', curChar = 'Ц', prevChar = 'З'))),
            Math.log(model.getProbability(Edit.Replace(newChar = 'У', curChar = 'Ц', prevChar = 'З')))
        )
    }

    // FAILS
    @Test
    fun testZUB() {
        val trainData = hashMapOf<String, String>()

        File("train.csv").forEachLineWithout("Id,Expected") { line ->
            val arr = line.split(',')
            trainData[arr[0].withBounds()] = arr[1].withBounds()
        }

        val expectedWords = trainData.values.toSet()

        println("#ІСІН is expected: ${"#ІСІН$" in expectedWords}")

        val wordFrequencies = mutableListOf<WordInfo>()
        File("words.csv").forEachLineWithout("Id,Freq") { line ->
            val arr = line.split(',')
            val word = arr[0].withBounds()
            val frequency = arr[1].toDouble()
            wordFrequencies += WordInfo(word, frequency)
        }

        val trie = Trie()

        trie.insertMultiple(*wordFrequencies.filter { it.word in expectedWords }.toTypedArray())

        val word2Frequency = wordFrequencies.associate { Pair(it.word, it.frequency) }.toMutableMap()

        val model = ErrorModel()
        model.train(trainData.map { (from, to) ->
            Correction(from, to, 1.0)//word2Frequency[from] ?: throw Exception("FUCK: $from"))
        })

        println("ENDS: " + model.endings)

        val fixer = SpellFixer(trie, model, word2Frequency, expectedWords, trainData)

        listOf(
            "ІШІН" to "ІШІН",
            "МАЛИТВЫ" to "МОЛИТВЫ",
            "РАТИО" to "РАДИО",
            "АЛЬФ" to "ЭЛЬФ",
            "ЗАЯВИЛ" to "ЗАЯВИЛ",
            "МАШИРО" to "МАШИНО",
            "ЛУЩИТЬ" to "ЛУДИТЬ",
            "FEIQ" to "FEIQ",
            "NNV" to "NNV",
            "ВГАПС" to "ГАПС",
            "БОЛЕЗНЕНЫЕ" to "БОЛЕЗНЕННО",
            "МАЙКОПУ" to "МАЙКОПЕ",
            "ПОЛИТОЛОГИ" to "ПОЛИТОЛОГИЯ",
            "ЧЕЛЮСНОЙ" to "ЧЕЛЮСТНОЙ",
            "ЗУУБОЧЕЛЮСНОЙ" to "ЗУБОЧЕЛЮСТНОЙ",
            "3771" to "37"
        ).forEach {
            val query = it.first.withBounds()

            val result = fixer.fix_optimized(query)

            assertEquals(it.second, result.withoutBounds())
        }
    }

    @Test
    fun testLang() {
        val w = "ІШІН"

        assertTrue(w.all { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC })
        assertTrue(!w.isRussian())
    }


}