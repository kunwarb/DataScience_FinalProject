package edu.unh.cs980.paragraph

import edu.unh.cs980.language.TopicMixtureResult
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
            File("paragraphs/Tools/doc_0.txt").readText() +
                    File("paragraphs/Travel/doc_3.txt").readText() +
                    File("paragraphs/Environments/doc_3.txt").readText() +
                    File("paragraphs/Environments/doc_2.txt").readText()

//    val testText =
//            File("paragraphs/Biology/doc_0.txt").readText() +
//                    File("paragraphs/People/doc_0.txt").readText()
    return embedder.embed(testText, nSamples = 10000, nIterations = 4000, smooth = false)
}


fun testText(embedder: KotlinEmbedding): TopicMixtureResult {
    val testText = """
        A party is a gathering of people who have been invited by a host for the purposes of socializing, conversation, recreation, or as part of a festival or other commemoration of a special occasion. A party will typically feature food and beverages, and often music and dancing or other forms of entertainment. In many Western countries, parties for teens and adults are associated with drinking alcohol such as beer, wine or distilled spirits.
        """

    return embedder.embed(testText, nSamples = 30000, nIterations = 6000, smooth = false)
}

fun main(args: Array<String>) {
    val indexLoc = "/home/hcgs/data_science/index"

    val embedder = KotlinEmbedding(indexLoc)
//    embedder.loadTopics("paragraphs/",
//            filterList = listOf())

    val myquery = "Arachnophobia signs and symptoms"
    val queryResults = embedder.query(myquery, 100).mapIndexed { index, s -> index.toString() to s  }.toMap()

    embedder.loadQueries(myquery, nQueries = 100)
    val expanded = embedder.expandQueryText(myquery, 10)

    val results = embedder.embed(expanded, nSamples = 3000, nIterations = 100, smooth = false)
    results.results.entries.sortedByDescending { it.value }
        .forEach { (k,v) -> println("${queryResults[k]}:\n\t$v") }

//    println(expanded)


//    testBasisParagraphs(embedder).reportResults()
//    testText(embedder).reportResults()



}
