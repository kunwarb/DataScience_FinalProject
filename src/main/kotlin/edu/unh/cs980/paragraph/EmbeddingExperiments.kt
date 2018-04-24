package edu.unh.cs980.paragraph

import edu.unh.cs980.CONTENT
import edu.unh.cs980.PID
import edu.unh.cs980.language.TopicMixtureResult
import edu.unh.cs980.misc.AnalyzerFunctions
import org.apache.lucene.search.TermQuery
import java.io.File

fun testQuery() {
//    embedder.reportQueryResults("Infinitesimal calculus", smoothDocs = false, smoothCombined = false)
//    embedder.reportQueryResults("Variational calculus and bayes theorem", smoothDocs = false, smoothCombined = false)

//    embedder.reportQueryResults("Health benefits of chocolate", smoothDocs = false, smoothCombined = false)

//    embedder.reportQueryResults("Microsoft", smoothDocs = false, smoothCombined = false)
//    embedder.reportQueryResults("Laplace operator", smoothDocs = false, smoothCombined = false)

}

fun testBasisParagraphs(embedder: KotlinEmbedding): TopicMixtureResult  {
    val testText =
            File("paragraphs/Computers/doc_1.txt").readText() +
                    File("paragraphs/Travel/doc_3.txt").readText() +
                    File("paragraphs/Games/doc_3.txt").readText()

//    val testText =
//            File("paragraphs/Biology/doc_0.txt").readText() +
//                    File("paragraphs/People/doc_0.txt").readText()
    return embedder.embed(testText, nSamples = 500, nIterations = 2000, smooth = false)
}


fun testText(embedder: KotlinEmbedding): TopicMixtureResult {
    val testText2 = """
        A party is a gathering of people who have been invited by a host for the purposes of socializing, conversation, recreation, or as part of a festival or other commemoration of a special occasion. A party will typically feature food and beverages, and often music and dancing or other forms of entertainment. In many Western countries, parties for teens and adults are associated with drinking alcohol such as beer, wine or distilled spirits.
        """
    val testText = """
        William Henry Gates III (born October 28, 1955) is an American business magnate, investor, author, philanthropist, humanitarian, and principal founder of Microsoft Corporation.[2][3] During his career at Microsoft, Gates held the positions of chairman, CEO and chief software architect, while also being the largest individual shareholder until May 2014.

In 1975, Gates and Paul Allen launched Microsoft, which became the world's largest PC software company.[4][a] Gates led the company as chief executive officer until stepping down in January 2000, but he remained as chairman and created the position of chief software architect for himself.[7] In June 2006, Gates announced that he would be transitioning from full-time work at Microsoft to part-time work and full-time work at the Bill & Melinda Gates Foundation, which was established in 2000.[8] He gradually transferred his duties to Ray Ozzie and Craig Mundie.[9] He stepped down as chairman of Microsoft in February 2014 and assumed a new post as technology adviser to support the newly appointed CEO Satya Nadella.[10]

Gates is one of the best-known entrepreneurs of the personal computer revolution. He has been criticized for his business tactics, which have been considered anti-competitive. This opinion has been upheld by numerous court rulings.[11]

Since 1987, Gates has been included in the Forbes list of the world's wealthiest people, an index of the wealthiest documented individuals, excluding and ranking against those with wealth that is not able to be completely ascertained.[12][13] From 1995 to 2017, he held the Forbes title of the richest person in the world all but four of those years, and held it consistently from March 2014 – July 2017, with an estimated net worth of US${'$'}89.9 billion as of October 2017.[1] However, on July 27, 2017, and since October 27, 2017, he has been surpassed by Amazon founder and CEO Jeff Bezos, who had an estimated net worth of US${'$'}90.6 billion at the time.[14]

Later in his career and since leaving Microsoft, Gates pursued a number of philanthropic endeavors. He donated large amounts of money to various charitable organizations and scientific research programs through the Bill & Melinda Gates Foundation.[15] In 2009, Gates and Warren Buffett founded The Giving Pledge, whereby they and other billionaires pledge to give at least half of their wealth to philanthropy.[16] The foundation works to save lives and improve global health, and is working with Rotary International to eliminate polio.[17] As of February 17, 2018, Gates had a net worth of ${'$'}91.7 billion, making him the second-richest person in the world, behind Bezos.
        """


    return embedder.embed(testText, nSamples = 1000, nIterations = 1000, smooth = false)
}

var good = 0
var bad = 0
var distTotal = 0.0
var lows = 0
var lows2 = 0
var terribles = 0

//fun doTests(embedder: KotlinEmbedding, queryStuff: Pair<String, List<String>>) {
//    val filteredQuery = AnalyzerFunctions.createTokenList(queryStuff.first, useFiltering = true)
//        .joinToString(" ")
//
//    val queryEmbedding = embedder.embed(filteredQuery, nSamples = 500, nIterations = 800, smooth = false)
////    queryEmbedding.reportResults()
//    queryStuff.second.forEach { candidate ->
//        val boolQuery = AnalyzerFunctions.createQuery(candidate, PID, false)
//        val docId = embedder.searcher.search(boolQuery, 1).scoreDocs.first().doc
//        val paagraphText = embedder.searcher.doc(docId).get(CONTENT)
//
//        val embedded = embedder.embed(paagraphText, 500, 800, false)
//        val dist = queryEmbedding.manhattenDistance(embedded)
//        distTotal += dist
//        if (dist < 0.5) lows++ else if (dist < 1.0) lows2++
//        if (dist > 1.5) terribles++
//
//        if (dist > 1.9) {
//            bad++
//            println("$good / $bad _ $terribles / $lows _ $lows2 / ${distTotal / (good + bad.toDouble())}")
//            println("BASE")
//            queryEmbedding.reportResults()
//            println("BAD")
//            embedded.reportResults()
//
//
//        } else (good++)
//
//    }

//    replaceNumbers.replace(queryStuff.first, " ")

//}

//fun unpack(embedder: KotlinEmbedding) {
//    File("train.pages.cbor-hierarchical.qrels")
//        .bufferedReader()
//        .readLines()
//        .map { it.split(" ").let { it[0] to it[2] } }
//        .groupBy { it.first }
//        .onEach { doTests(embedder, it.key to it.value.map { it.second }) }
//}


fun main(args: Array<String>) {
    val indexLoc = "/home/hcgs/data_science/index"

//    val validTopics = File("paragraphs/")
//        .listFiles()
//        .map { file -> file.name }
//        .filter { name -> name !in listOf("Medicine", "Tools", "Society", "Warfare") }

    val embedder = KotlinEmbedding(indexLoc)
    embedder.loadTopics("paragraphs/")


//    unpack(embedder)

    testBasisParagraphs(embedder).reportResults()
//    testText(embedder).reportResults()

    val myquery = "Arachnophobia signs and symptoms"
//    val queryResults = embedder.query(myquery, 100).mapIndexed { index, s -> index.toString() to s  }.toMap()
//
//    embedder.loadQueries(myquery, nQueries = 100)
//    val expanded = embedder.expandQueryText(myquery, 10)
//
//    val results = embedder.embed(expanded, nSamples = 3000, nIterations = 100, smooth = false)
//    results.results.entries.sortedByDescending { it.value }
//        .forEach { (k,v) -> println("${queryResults[k]}:\n\t$v") }

//    println(expanded)


//    testBasisParagraphs(embedder).reportResults()
//    testText(embedder).reportResults()



}
