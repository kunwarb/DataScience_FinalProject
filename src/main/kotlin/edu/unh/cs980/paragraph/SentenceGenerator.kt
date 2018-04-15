package edu.unh.cs980.paragraph

import edu.unh.cs980.accumMap
import edu.unh.cs980.language.KernelDist
import edu.unh.cs980.language.KotlinKernelAnalyzer
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_STANDARD
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

fun main(args: Array<String>) {
//    val example =
//            """
//                The most obvious function of clothing is to improve the comfort of the wearer, by protecting the wearer from the elements. In hot climates, clothing provides protection from sunburn or wind damage, while in cold climates its thermal insulation properties are generally more important. The shelter usually reduces the functional need for clothing. For example, coats, hats, gloves, and other superficial layers are normally removed when entering a warm home, particularly if one is residing or sleeping there. Similarly, clothing has seasonal and regional aspects, so that thinner materials and fewer layers of clothing are generally worn in warmer seasons and regions than in colder ones.
//
//Clothing performs a range of social and cultural functions, such as individual, occupational and gender differentiation, and social status.[6] In many societies, norms about clothing reflect standards of modesty, religion, gender, and social status. Clothing may also function as a form of adornment and an expression of personal taste or style.
//
//Clothing can and has in history been made from a very wide variety of materials. Materials have ranged from leather and furs to woven materials, to elaborate and exotic natural and synthetic fabrics. Not all body coverings are regarded as clothing. Articles carried rather than worn (such as purses), worn on a single part of the body and easily removed (scarves), worn purely for adornment (jewelry), or those that serve a function other than protection (eyeglasses), are normally considered accessories rather than clothing, except for shoes.
//
//Clothing protects against many things that might injure the uncovered human body. Clothes protect people from the elements, including rain, snow, wind, and other weather, as well as from the sun. However, clothing that is too sheer, thin, small, tight, etc., offers less protection. Clothes also reduce risk during activities such as work or sport. Some clothing protects from specific environmental hazards, such as insects, noxious chemicals, weather, weapons, and contact with abrasive substances. Conversely, clothing may protect the environment from the clothing wearer, as with doctors wearing medical scrubs.
//
//Humans have shown the extreme invention in devising clothing solutions to environmental hazards. Examples include: space suits, air conditioned clothing, armor, diving suits, swimsuits, bee-keeper gear, motorcycle leathers, high-visibility clothing, and other pieces of protective clothing. Meanwhile, the distinction between clothing and protective equipment is not always clear-cut—since clothes designed to be fashionable often have protective value and clothes designed for function often consider fashion in their design. Wearing clothes also has social implications. They cover parts of the body that social norms require being covered, act as a form of adornment, and serve other social purposes. Someone who lacks the means to procure reasonable clothing due to poverty or affordability is sometimes said to be scruffy, ragged, or shabby.[7]
//                """

//    Baking is a method of cooking food that uses prolonged dry heat, normally in an oven, but also in hot ashes, or on hot stones. The most common baked item is bread but many other types of foods are baked.[1] Heat is gradually transferred "from the surface of cakes, cookies, and breads to their centre. As heat travels through, it transforms batters and doughs into baked goods with a firm dry crust and a softer centre".[2] Baking can be combined with grilling to produce a hybrid barbecue variant by using both methods simultaneously, or one after the other. Baking is related to barbecuing because the concept of the masonry oven is similar to that of a smoke pit.

//    Cartography studies the representation of the Earth's surface with abstract symbols (map making). Although other subdisciplines of geography rely on maps for presenting their analyses, the actual making of maps is abstract enough to be regarded separately. Cartography has grown from a collection of drafting techniques into an actual science.
//
//    Cartographers must learn cognitive psychology and ergonomics to understand which symbols convey information about the Earth most effectively, and behavioural psychology to induce the readers of their maps to act on the information. They must learn geodesy and fairly advanced mathematics to understand how the shape of the Earth affects the distortion of map symbols projected onto a flat surface for viewing. It can be said, without much controversy, that cartography is the seed from which the larger field of geography grew. Most geographers will cite a childhood fascination with maps as an early sign they would end up in the field.

    val example =  """
        Derivative academic fields of study may both interact with and develop independently of filmmaking, as in film theory and analysis. Fields of academic study have been created that are derivative or dependent on the existence of film, such as film criticism, film history, divisions of film propaganda in authoritarian governments, or psychological on subliminal effects (e.g., of a flashing soda can during a screening). These fields may further create derivative fields, such as a movie review section in a newspaper or a television guide. Sub-industries can spin off from film, such as popcorn makers, and film-related toys (e.g., Star Wars figures). Sub-industries of pre-existing industries may deal specifically with film, such as product placement and other advertising within films.
"""

//    here are two main defensive strategies: zone defense and man-to-man defense. In a zone defense, each player is assigned to guard a specific area of the court. Zone defenses often allow the defense to double team the ball, a manoeuver known as a trap. In a man-to-man defense, each defensive player guards a specific opponent.
//    Offensive plays are more varied, normally involving planned passes and movement by players without the ball. A quick movement by an offensive player without the ball to gain an advantageous position is known as a cut. A legal attempt by an offensive player to stop an opponent from guarding a teammate, by standing in the defender's way such that the teammate cuts next to him, is a screen or pick. The two plays are combined in the pick and roll, in which a player sets a pick and then "rolls" away from the pick towards the basket. Screens and cuts are very important in offensive plays; these allow the quick passes and teamwork, which can lead to a successful basket. Teams almost always have several offensive plays planned to ensure their movement is not predictable. On court, the point guard is usually responsible for indicating which play will occur.


//    val example = File("paragraphs/Computers/doc_0.txt").readText() +
//            File("paragraphs/Biology/doc_1.txt").readText() +
//    File("paragraphs/People/doc_2.txt").readText()

//    val example = File("paragraphs/Biology/doc_10.txt").readText()
//    val example = File("paragraphs/Biology/doc_10.txt").readText() +
//            File("paragraphs/Biology/doc_1.txt").readText()


//    val example =
//            File("paragraphs/Biology/doc_1.txt").readText()

    val corpusStats = KernelDist(0.0, 1.0)
    File("pages/")
        .listFiles()
        .filter(File::isDirectory)
        .forEach { file ->
            file.listFiles().forEach { corpusStats.analyzePartitionedDocument(it.readText()) } }

//    File("paragraphs/")
//        .listFiles()
//        .filter(File::isDirectory)
//        .forEach { file ->
//            file.listFiles().forEach { corpusStats.analyzePartitionedDocument(it.readText()) } }


    val analyzer = KotlinKernelAnalyzer(0.0, 1.0, corpus = corpusStats.kernels, partitioned = true)
    val tifd = analyzer.mydf
    corpusStats.normalizeKernels(tifd, false)
//    corpusStats.kernels.values.map { kernel -> kernel.frequency = 1 / kernel.frequency }
//    val ktotal = corpusStats.kernels.values.sumByDouble { kernel -> kernel.frequency }
//    corpusStats.kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / ktotal }



    val exampleDist = KernelDist(0.0, 20.0)
        .apply { analyzePartitionedDocument(example) }
        .apply { (0 until 40).forEach { normalizeByCond() } }
//        .apply { normalizeKernels(tifd, false) }


    analyzer.analyzeTopicDirectories("paragraphs/")
//    analyzer.topics.remove("Organizations")
    analyzer.normalizeTopics(true)

//    val docGen = DocumentGenerator(listOf(exampleDist), 200, 25)
//    val sentGen = RandomSentenceGenerator(example)

    val sentGen = KernelDistSentenceGenerator(exampleDist)
    val docGen = DocumentGenerator(sentGen, 700, 15)

//
    val docs = docGen.generateDocuments(200, replaceAll = true)
        .map { doc -> KernelDist(0.0, 1.0)
            .apply { analyzePartitionedDocument(doc) }
        }
    analyzer.classifyByDomainSimplex(example, docs, false)

//    computeStats(exampleDist, docs)


//    val domain = (0 until 800).map { exampleDist.perturb() }
//    analyzer.classifyByDomainSimplex2(example, domain, true)


}



