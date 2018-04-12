package edu.unh.cs980.paragraph

import edu.unh.cs980.accumMap
import edu.unh.cs980.language.KernelDist
import edu.unh.cs980.language.KotlinKernelAnalyzer
import edu.unh.cs980.language.WordKernel
import java.util.concurrent.ThreadLocalRandom

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
        val pick = ThreadLocalRandom.current().nextDouble(0.0, total)
        return cumulativeDensity.find { (_, value) -> value >= pick  }!!
    }
}


class KernelDistSentenceGenerator(val dist: KernelDist) : SentenceGenerator() {
    val densityMap =
            dist.kernels.entries
                .map { (_, kernel) -> kernel to kernel.frequency }
                .map { (kernel, frequency ) ->
                    val wordList = kernel.distribution.map(Map.Entry<String, Double>::toPair)
                    val cDensity = CumulativeDensity(wordList)
                    Pair(kernel, cDensity) to frequency }
                .run { CumulativeDensity(this) }

    val wordMap =
            densityMap.cumulativeDensity.map { entry -> entry.first.first.word to entry }
                .toMap()


    override fun generate(length: Int): String {
        val words = ArrayList<String>()
        var curPick = densityMap.weightedPick().first.first.word
        words += curPick

        (0 until length).forEach { _ ->
            curPick = wordMap[curPick]!!.first.second.weightedPick().first
            words += curPick
        }

        return words.joinToString(" ")
    }

}


class DocumentGenerator(dists: List<KernelDist>, val nSentences: Int, val sentenceLength: Int) {
    val gens = dists.map(::KernelDistSentenceGenerator)

    fun generateSentence(): String =
        gens[ThreadLocalRandom.current().nextInt(gens.size)].generate(sentenceLength)

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
                val nextIndex = ThreadLocalRandom.current().nextInt(nSentences)
                sentences[nextIndex] = generateSentence()
            }
        }

        return documents
    }



}


fun main(args: Array<String>) {
    val example =
            """

    Baking is a method of cooking food that uses prolonged dry heat, normally in an oven, but also in hot ashes, or on hot stones. The most common baked item is bread but many other types of foods are baked.[1] Heat is gradually transferred "from the surface of cakes, cookies, and breads to their centre. As heat travels through, it transforms batters and doughs into baked goods with a firm dry crust and a softer centre".[2] Baking can be combined with grilling to produce a hybrid barbecue variant by using both methods simultaneously, or one after the other. Baking is related to barbecuing because the concept of the masonry oven is similar to that of a smoke pit.
    Because of historical social and familial roles, baking has traditionally been performed at home by women for domestic consumption and by men in bakeries and restaurants for local consumption. When production was industrialized, baking was automated by machines in large factories. The art of baking remains a fundamental skill and is important for nutrition, as baked goods, especially breads, are a common and important food, both from an economic and cultural point of view. A person who prepares baked goods as a profession is called a baker.
                """

    val exampleDist = KernelDist(0.0, 10.0).apply { analyzeDocument(example) }

    val analyzer = KotlinKernelAnalyzer(0.0, 1.0)
    analyzer.analyzeTopicDirectories("paragraphs/")
    analyzer.normalizeTopics(false)
    val docGen = DocumentGenerator(listOf(exampleDist), 200, 20)
//    val docGen = DocumentGenerator(analyzer.topics.values.toList(), 5, 300)
//    val docGen = DocumentGenerator(analyzer.topics.values.toList(), 1, 500)
    val docs = docGen.generateDocuments(500, replaceAll = true)
        .map { doc -> KernelDist(0.0, 1.0).apply { analyzeDocument(doc) } }
//        .forEach { docKernel -> println("${firstTopic.key} : ${docKernel.kld(firstTopic.value)}") }

//    analyzer.compareDistances(docs)
    analyzer.classifyByDomain(example, docs, smooth = false)

//    val topicGens = analyzer.topics.entries.map { (topic, kernelDist) ->
//        topic to KernelDistSentenceGenerator(kernelDist)  }
//    (0 .. 10).forEach { _ ->
//        val gen = topicGens[ThreadLocalRandom.current().nextInt(topicGens.size)]
//        println("(${gen.first}): " + gen.second.generate(20))
//    }
}



