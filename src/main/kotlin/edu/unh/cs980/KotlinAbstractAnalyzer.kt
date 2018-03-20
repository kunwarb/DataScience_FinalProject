@file:JvmName("KotlinAbstractAnalyzer")
package edu.unh.cs980

import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Occurs
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.TermQuery
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
            .apply { add(TermQuery(Term("name", cleanName)), BooleanClause.Occur.SHOULD) }
            .build()

        val topDocs = indexSearcher.search(query, 1)
        if (topDocs.scoreDocs.isEmpty()) {
            return null
        }

        val entityDoc = indexSearcher.doc(topDocs.scoreDocs[0].doc)
        val content = entityDoc.get("text")
        println(content)
        return createTokenSequence(content).toList()
    }


    fun getEntityStats(entity: String): Pair<Map<String, Double>, Map<String, Double>>? {
        val terms = getEntityTokens(entity) ?: return null

        val allTermsInDocument = terms.size.toDouble()
        val allTermsInCorpus = indexSearcher.indexReader
            .getSumTotalTermFreq("text")
            .toDouble()

        val termFreqs = terms
            .groupingBy(::identity)
            .eachCount()
            .mapValues { (term, freq) -> freq / allTermsInDocument }

        val docFreqs = terms
            .toSet()
            .map { term -> Pair(term, retrieveTermStats(term) / allTermsInCorpus)}
            .toMap()

        return Pair(docFreqs, termFreqs)
    }

    fun runTest() {
        val testEntities = listOf("Stack_exchange", "food", "theobromine", "united_states")

        testEntities
            .onEach(::println)
            .mapNotNull(this::getEntityStats)
            .forEach { (docFreqs, termFreqs) ->
                println(docFreqs)
                println(termFreqs)
            }
    }

}


