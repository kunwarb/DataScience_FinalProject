package edu.unh.cs980.paragraph

import edu.unh.cs980.accumMap
import edu.unh.cs980.language.KernelDist
import edu.unh.cs980.language.KotlinKernelAnalyzer
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_STANDARD
import edu.unh.cs980.misc.KotlinIndexTester
import edu.unh.cs980.sharedRand
import java.io.File
import java.lang.Math.pow
import java.lang.Math.sqrt

// coord 548 (starting at 1)

abstract class SentenceGenerator() {
    abstract fun generate(length: Int): String
}

class CumulativeDensity<A>(val elements: List<Pair<A, Double>>) {
    val total = elements.sumByDouble(Pair<A, Double>::second)
    val cumulativeDensity = elements
        .accumMap(Pair<A, Double>::first) { acc: Double?, pair ->
            acc?.plus(pair.second) ?: pair.second }


    fun weightedPick(): Pair<A, Double> {
//        val pick = ThreadLocalRandom.current().nextDouble(0.0, total)
        val pick = sharedRand.nextDouble() * total
        return cumulativeDensity.find { (_, value) -> value >= pick  }!!
    }
}


class KernelDistSentenceGenerator(val dist: KernelDist) : SentenceGenerator() {
    val densityMap =
            dist.kernels.entries
                .map { (_, kernel) -> kernel to kernel.frequency }
                .mapNotNull { (kernel, frequency ) ->
                    val wordList = kernel.distribution.map(Map.Entry<String, Double>::toPair)
                    val cDensity = CumulativeDensity(wordList)
                    if (wordList.isEmpty() ) null else Pair(kernel, cDensity) to frequency }
                .run { CumulativeDensity(this) }

    val wordMap =
            densityMap.cumulativeDensity.map { entry -> entry.first.first.word to entry }
                .toMap()


    override fun generate(length: Int): String {
        val words = ArrayList<String>()
        var curPick = densityMap.weightedPick().first.first.word
//        curPick = wordMap[curPick]!!.first.second.weightedPick().first

        words += curPick

        (0 until length).forEach { _ ->
//            curPick = densityMap.weightedPick().first.first.word
            curPick = wordMap[curPick]!!.first.second.weightedPick().first
            words += curPick
        }

        return words.joinToString(" ")
    }

}

class RandomSentenceGenerator(text: String) : SentenceGenerator() {

    private val filterPattern = "[\\d+]".toRegex()

    private val words =
            filterPattern.replace(text, "")
                .let { filteredText ->
                    AnalyzerFunctions.createTokenList(filteredText, analyzerType = ANALYZER_ENGLISH)
                }



    override fun generate(length: Int) =
        (0 until length)
//            .map { words[ThreadLocalRandom.current().nextInt(words.size)] }
            .map { words[sharedRand.nextInt(words.size)] }
            .joinToString(" ") + ". "

}


class DocumentGenerator(val gen: SentenceGenerator, val nSentences: Int, val sentenceLength: Int) {

    fun generateSentence(): String =
        gen.generate(sentenceLength) + ". "

    fun generateDocuments(nDocuments: Int, replaceAll: Boolean = false): ArrayList<String> {
        val documents = ArrayList<String>()
        val sentences = ArrayList<String>()

        (0 until nSentences).mapTo(sentences) { generateSentence() }

        (0 until nDocuments).forEach {
            documents += sentences.joinToString("\n")
            if (replaceAll) {
                sentences.clear()
                (0 until nSentences).mapTo(sentences) { generateSentence() }
            } else {
//                val nextIndex = ThreadLocalRandom.current().nextInt(nSentences)
                val nextIndex = sharedRand.nextInt(nSentences)
                sentences[nextIndex] = generateSentence()
            }
        }

        return documents
    }

    fun generateDocument(): String =
            (0 until nSentences * 30).joinToString("\n") { generateSentence() }
}


fun computeStats(exampleDist: KernelDist, docs: List<KernelDist>) {
    val sortedOrigin = exampleDist.kernels.toSortedMap().entries
    val firstGenerated = docs.first().kernels
    val kernelCounts = HashMap<String, ArrayList<Double>>()

    docs.forEach { kernelDist ->
        val total = kernelDist.kernels.values.sumByDouble { kv -> kv.frequency }
        kernelDist.kernels.values.forEach { kv -> kv.frequency = kv.frequency / total }
        kernelDist.kernels.forEach { (k,v) ->
            kernelCounts.computeIfAbsent(k) { arrayListOf()} += v.frequency
        }
        sortedOrigin.forEach { (k,_) -> if (k !in kernelDist.kernels)
            kernelCounts.computeIfAbsent(k) { arrayListOf()} += 0.0
        }
    }

    kernelCounts.toSortedMap()
        .forEach { (k,v) ->
            val mean = v.average()
            val variance = v.sumByDouble { value -> pow(value - mean, 2.0) }
            val std = sqrt(variance / v.size)
            println("$k: m = $mean, v = $variance, std = $std")
        }
}




