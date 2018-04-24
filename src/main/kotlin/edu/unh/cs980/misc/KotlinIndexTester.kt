package edu.unh.cs980.misc

import edu.unh.cs980.CONTENT
import edu.unh.cs980.getIndexSearcher


class KotlinIndexTester(indexLoc: String) {
    val searcher = getIndexSearcher(indexLoc)

    fun getResults(query: String): List<String> {
        val boolQuery = AnalyzerFunctions.createQuery(query)
        val results = searcher.search(boolQuery, 10)
        return results.scoreDocs.map { scoreDoc ->
            val doc = searcher.doc(scoreDoc.doc)
            doc.get(CONTENT)
        }
    }
}