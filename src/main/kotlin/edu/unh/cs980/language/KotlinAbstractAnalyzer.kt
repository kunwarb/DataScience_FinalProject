@file:JvmName("KotlinAbstractAnalyzer")
package edu.unh.cs980.language

import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.identity
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.FuzzyQuery
import java.io.StringReader
import kotlin.coroutines.experimental.buildSequence

class KotlinAbstractAnalyzer(abstractLocation: String) {
    val indexSearcher = getIndexSearcher(abstractLocation)
    val analyzer = EnglishAnalyzer()

    fun createTokenSequence(query: String): Sequence<String> {
        val tokenStream = analyzer.tokenStream("text", StringReader(query)).apply { reset() }

        return buildSequence<String> {
            while (tokenStream.incrementToken()) {
                yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
            }
            tokenStream.end()
            tokenStream.close()
        }
    }

    fun retrieveTermStats(term: String): Long =
        indexSearcher.indexReader.totalTermFreq(Term("text", term))

    fun getEntityTokens(entity: String): List<String>? {
        val cleanName = entity.toLowerCase().replace(" ", "_")
        val query = BooleanQuery
            .Builder()
            .apply { add(FuzzyQuery(Term("name", cleanName)), BooleanClause.Occur.SHOULD) }
            .build()

        val topDocs = indexSearcher.search(query, 1)
        if (topDocs.scoreDocs.isEmpty()) {
            return null
        }

        val entityDoc = indexSearcher.doc(topDocs.scoreDocs[0].doc)
        val content = entityDoc.get("text")
        return createTokenSequence(content).toList()
    }

    fun getTermStats(terms: List<String>): List<Pair<String, Double>> {
        val totalTerms = indexSearcher.indexReader.getSumTotalTermFreq("text").toDouble()
        return terms.map { term ->
            val termFreq = indexSearcher.indexReader.totalTermFreq(Term("text", term)) / totalTerms
            term to termFreq
        }.toList()
    }


    fun getEntityStats(entity: String): LanguageStats? {
        val terms = getEntityTokens(entity) ?: return null

        val allTermsInDocument = terms.size.toDouble()
//        val allTermsInCorpus = indexSearcher.indexReader
//            .getSumTotalTermFreq("text")
//            .toDouble()

        val docTermCounts = terms
            .groupingBy(::identity)
            .eachCount()

        val docTermFreqs = docTermCounts
            .mapValues { (term, count) -> count / allTermsInDocument }

//        val corpusTermFreqs = terms
//            .toSet()
//            .map { term -> Pair(term, retrieveTermStats(term) / allTermsInCorpus)}
//            .toMap()

        return LanguageStats(docTermCounts, docTermFreqs)
    }

    fun runTest() {
        val testEntities = listOf("Stack_exchange", "food", "theobromine", "united_states")

        testEntities
            .onEach(::println)
            .mapNotNull(this::getEntityStats)
            .forEach { (docFreqs, termFreqs, _) ->
                println(docFreqs)
                println(termFreqs)
            }
    }

}


