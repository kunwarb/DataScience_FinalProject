package edu.unh.cs980.paragraph

import edu.unh.cs980.accumMap
import edu.unh.cs980.language.KernelDist
import edu.unh.cs980.language.KotlinKernelAnalyzer
import edu.unh.cs980.language.WordKernel
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.sharedRand
import java.io.File
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
//        val pick = ThreadLocalRandom.current().nextDouble(0.0, total)
        val pick = sharedRand.nextDouble() * total
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

class DomainGenerator(val generator: DocumentGenerator, val origin: KernelDist, val topics: List<KernelDist>) {
    val originScores = arrayListOf<Double>()
    val topicScores = (0 until topics.size).map { arrayListOf<Double>() }

    fun generateDomain(nSteps: Int) {
        (0 until nSteps).forEach {
            val nextDocument = generator.generateDocument()
            val kernel = KernelDist(0.0, 1.0).apply { analyzePartitionedDocument(nextDocument) }
            originScores += origin.kld(kernel)
            topicScores.forEachIndexed { index, arrayList ->
                arrayList += topics[index].kld(kernel)
            }
        }
    }
}


fun main(args: Array<String>) {
//    val example =
//            """
//                William Henry Gates III (born October 28, 1955) is an American business magnate, investor, author, philanthropist, humanitarian, and principal founder of the Microsoft Corporation.[2][3] During his career at Microsoft, Gates held the positions of chairman, CEO and chief software architect, while also being the largest individual shareholder until May 2014.
//
//In 1975, Gates and Paul Allen launched Microsoft, which became the world's largest PC software company.[4][a] Gates led the company as chief executive officer until stepping down in January 2000, but he remained as chairman and created the position of chief software architect for himself.[7]
//
//In June 2006, Gates announced that he would be transitioning from full-time work at Microsoft to part-time work and full-time work at the Bill & Melinda Gates Foundation, which was established in 2000.[8] He gradually transferred his duties to Ray Ozzie and Craig Mundie.[9] He stepped down as chairman of Microsoft in February 2014 and assumed a new post as technology adviser to support the newly appointed CEO Satya Nadella.[10]
//
//Gates is one of the best-known entrepreneurs of the personal computer revolution. He has been criticized for his business tactics, which have been considered anti-competitive. This opinion has been upheld by numerous court rulings.[11] Later in his career, Gates pursued a number of philanthropic endeavors. He donated large amounts of money to various charitable organizations and scientific research programs through the Bill & Melinda Gates Foundation.[12]
//
//Since 1987, Gates has been included in the Forbes list of the world's wealthiest people, an index of the wealthiest documented individuals, excluding and ranking against those with wealth that is not able to be completely ascertained.[13][14]
//
//From 1995 to 2017, he held the Forbes title of the richest person in the world all but four of those years, and held it consistently from March 2014 – July 2017, with an estimated net worth of US$89.9 billion as of October 2017.[1] However, on July 27, 2017, and since October 27, 2017, he has been surpassed by Amazon founder and CEO Jeff Bezos, who had an estimated net worth of US$90.6 billion at the time.[15]
//
//In 2009, Gates and Warren Buffett founded The Giving Pledge, whereby they and other billionaires pledge to give at least half of their wealth to philanthropy.[16] The foundation works to save lives and improve global health, and is working with Rotary International to eliminate polio.[17] As of February 17, 2018, Gates had a net worth of $91.7 billion, making him the second richest person in the world, behind Bezos.
//                """


//    Cartography studies the representation of the Earth's surface with abstract symbols (map making). Although other subdisciplines of geography rely on maps for presenting their analyses, the actual making of maps is abstract enough to be regarded separately. Cartography has grown from a collection of drafting techniques into an actual science.
//
//    Cartographers must learn cognitive psychology and ergonomics to understand which symbols convey information about the Earth most effectively, and behavioural psychology to induce the readers of their maps to act on the information. They must learn geodesy and fairly advanced mathematics to understand how the shape of the Earth affects the distortion of map symbols projected onto a flat surface for viewing. It can be said, without much controversy, that cartography is the seed from which the larger field of geography grew. Most geographers will cite a childhood fascination with maps as an early sign they would end up in the field.

//    here are two main defensive strategies: zone defense and man-to-man defense. In a zone defense, each player is assigned to guard a specific area of the court. Zone defenses often allow the defense to double team the ball, a manoeuver known as a trap. In a man-to-man defense, each defensive player guards a specific opponent.
//    Offensive plays are more varied, normally involving planned passes and movement by players without the ball. A quick movement by an offensive player without the ball to gain an advantageous position is known as a cut. A legal attempt by an offensive player to stop an opponent from guarding a teammate, by standing in the defender's way such that the teammate cuts next to him, is a screen or pick. The two plays are combined in the pick and roll, in which a player sets a pick and then "rolls" away from the pick towards the basket. Screens and cuts are very important in offensive plays; these allow the quick passes and teamwork, which can lead to a successful basket. Teams almost always have several offensive plays planned to ensure their movement is not predictable. On court, the point guard is usually responsible for indicating which play will occur.

    //Because of historical social and familial roles, baking has traditionally been performed at home by women for domestic consumption and by men in bakeries and restaurants for local consumption. When production was industrialized, baking was automated by machines in large factories. The art of baking remains a fundamental skill and is important for nutrition, as baked goods, especially breads, are a common and important food, both from an economic and cultural point of view. A person who prepares baked goods as a profession is called a baker.
//    Baking is a method of cooking food that uses prolonged dry heat, normally in an oven, but also in hot ashes, or on hot stones. The most common baked item is bread but many other types of foods are baked.[1] Heat is gradually transferred "from the surface of cakes, cookies, and breads to their centre. As heat travels through, it transforms batters and doughs into baked goods with a firm dry crust and a softer centre".[2] Baking can be combined with grilling to produce a hybrid barbecue variant by using both methods simultaneously, or one after the other. Baking is related to barbecuing because the concept of the masonry oven is similar to that of a smoke pit.

    val example = File("paragraphs/People/doc_8.txt").readText() +
            File("paragraphs/Computers/doc_1.txt").readText() +
            File("paragraphs/People/doc_3.txt").readText()
//    val example =
//            File("paragraphs/People/doc_3.txt").readText()

    val analyzer = KotlinKernelAnalyzer(0.0, 1.0, partitioned = true)
    val tifd = analyzer.mydf
    val exampleDist = KernelDist(0.0, 1.0)
        .apply { analyzePartitionedDocument(example) }
        .apply { (0 until 3).forEach { normalizeByCond() } }
//        .apply { normalizeKernels(tifd, false) }


    analyzer.analyzeTopicDirectories("paragraphs/")
    analyzer.normalizeTopics(true)
//    analyzer.topics.forEach { topic -> (0 until 2).forEach { topic.value.normalizeByCond() } }
//    val docGen = DocumentGenerator(listOf(exampleDist), 200, 25)
//    val sentGen = RandomSentenceGenerator(example)
    val sentGen = KernelDistSentenceGenerator(exampleDist)
    val docGen = DocumentGenerator(sentGen, 300, 50)
//    val docGen = DocumentGenerator(listOf(exampleDist), 600, 10)
//    val docGen = DocumentGenerator(analyzer.topics.values.toList(), 5, 300)
//    val docGen = DocumentGenerator(analyzer.topics.values.toList(), 1, 500)

    val docs = docGen.generateDocuments(300, replaceAll = true)
        .map { doc -> KernelDist(0.0, 1.0)
            .apply { analyzePartitionedDocument(doc) }
        }
    analyzer.classifyByDomainSimplex(example, docs, smooth = false)
//    val domainGenerator = DomainGenerator(docGen, exampleDist, analyzer.topics.values.toList())
//    domainGenerator.generateDomain(50)
//
//    analyzer.classifyByDomainSimplex2(domainGenerator.originScores, domainGenerator.topicScores)
//    Computers : 0.3998460792694989
//    Society : 0.28573038924712224
//    Politics : 0.17146662007082414
//    People : 0.08580599400416698
//    Organizations : 0.05715091740838773

}



