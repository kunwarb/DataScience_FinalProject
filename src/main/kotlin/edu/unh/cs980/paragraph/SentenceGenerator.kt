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
        gens[ThreadLocalRandom.current().nextInt(gens.size)].generate(sentenceLength) + ". "

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
                Basketball's early adherents were dispatched to YMCAs throughout the United States, and it quickly spread through the United States and Canada. By 1895, it was well established at several women's high schools. While the YMCA was responsible for initially developing and spreading the game, within a decade it discouraged the new sport, as rough play and rowdy crowds began to detract from the YMCA's primary mission. However, other amateur sports clubs, colleges, and professional clubs quickly filled the void. In the years before World War I, the Amateur Athletic Union and the Intercollegiate Athletic Association of the United States (forerunner of the NCAA) vied for control over the rules for the game. The first pro league, the National Basketball League, was formed in 1898 to protect players from exploitation and to promote a less rough game. This league only lasted five years.

Dr. James Naismith was instrumental in establishing college basketball. His colleague C.O. Beamis fielded the first college basketball team just a year after the Springfield YMCA game at the suburban Pittsburgh Geneva College.[14] Naismith himself later coached at the University of Kansas for six years, before handing the reins to renowned coach Forrest "Phog" Allen. Naismith's disciple Amos Alonzo Stagg brought basketball to the University of Chicago, while Adolph Rupp, a student of Naismith's at Kansas, enjoyed great success as coach at the University of Kentucky. On February 9, 1895, the first intercollegiate 5-on-5 game was played at Hamline University between Hamline and the School of Agriculture, which was affiliated with the University of Minnesota.[15][16][17] The School of Agriculture won in a 9–3 game.

In 1901, colleges, including the University of Chicago, Columbia University, Cornell University, Dartmouth College, the University of Minnesota, the U.S. Naval Academy, the University of Colorado and Yale University began sponsoring men's games. In 1905, frequent injuries on the football field prompted President Theodore Roosevelt to suggest that colleges form a governing body, resulting in the creation of the Intercollegiate Athletic Association of the United States (IAAUS). In 1910, that body would change its name to the National Collegiate Athletic Association (NCAA). The first Canadian interuniversity basketball game was played at the YMCA in Kingston, Ontario on February 6, 1904, when McGill University -- Naismith's alma mater -- visited Queen's University. McGill won 9–7 in overtime; the score was 7–7 at the end of regulation play, and a ten-minute overtime period settled the outcome. A good turnout of spectators watched the game.[18]

The first men's national championship tournament, the National Association of Intercollegiate Basketball tournament, which still exists as the National Association of Intercollegiate Athletics (NAIA) tournament, was organized in 1937. The first national championship for NCAA teams, the National Invitation Tournament (NIT) in New York, was organized in 1938; the NCAA national tournament would begin one year later. College basketball was rocked by gambling scandals from 1948 to 1951, when dozens of players from top teams were implicated in match fixing and point shaving. Partially spurred by an association with cheating, the NIT lost support to the NCAA tournament.
                """

    //Because of historical social and familial roles, baking has traditionally been performed at home by women for domestic consumption and by men in bakeries and restaurants for local consumption. When production was industrialized, baking was automated by machines in large factories. The art of baking remains a fundamental skill and is important for nutrition, as baked goods, especially breads, are a common and important food, both from an economic and cultural point of view. A person who prepares baked goods as a profession is called a baker.
//    Baking is a method of cooking food that uses prolonged dry heat, normally in an oven, but also in hot ashes, or on hot stones. The most common baked item is bread but many other types of foods are baked.[1] Heat is gradually transferred "from the surface of cakes, cookies, and breads to their centre. As heat travels through, it transforms batters and doughs into baked goods with a firm dry crust and a softer centre".[2] Baking can be combined with grilling to produce a hybrid barbecue variant by using both methods simultaneously, or one after the other. Baking is related to barbecuing because the concept of the masonry oven is similar to that of a smoke pit.
    val exampleDist = KernelDist(0.0, 1.0).apply { analyzePartitionedDocument(example) }

    val analyzer = KotlinKernelAnalyzer(0.0, 1.0, partitioned = true)
    analyzer.analyzeTopicDirectories("paragraphs/")
    analyzer.normalizeTopics(true)
//    val docGen = DocumentGenerator(listOf(exampleDist), 200, 25)
    val docGen = DocumentGenerator(listOf(exampleDist), 20, 50)
//    val docGen = DocumentGenerator(listOf(exampleDist), 600, 10)
//    val docGen = DocumentGenerator(analyzer.topics.values.toList(), 5, 300)
//    val docGen = DocumentGenerator(analyzer.topics.values.toList(), 1, 500)
    val docs = docGen.generateDocuments(2000, replaceAll = false)
        .map { doc -> KernelDist(0.0, 1.0).apply { analyzePartitionedDocument(doc) } }
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



