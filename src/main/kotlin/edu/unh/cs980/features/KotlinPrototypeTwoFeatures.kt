package edu.unh.cs980.features

import edu.unh.cs980.CONTENT
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import java.io.StringReader
import kotlin.coroutines.experimental.buildSequence

private val englishAnalyzer = EnglishAnalyzer()

private fun createTokenSequence(query: String): Sequence<String> {
    val tokenStream = englishAnalyzer.tokenStream("text", StringReader(query)).apply { reset() }

    return buildSequence<String> {
        while (tokenStream.incrementToken()) {
            yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
        }
        tokenStream.end()
        tokenStream.close()
    }
}


private fun buildEntityNameQuery(entity: String): BooleanQuery =
    BooleanQuery.Builder()
        .apply { add(FuzzyQuery(Term("name", entity)), BooleanClause.Occur.SHOULD) }
        .build()

private fun buildQuery(query: String): BooleanQuery =
    createTokenSequence(query)
        .map { token -> TermQuery(Term(CONTENT, token))}
        .fold(BooleanQuery.Builder()) { builder, termQuery ->
            builder.add(termQuery, BooleanClause.Occur.SHOULD) }
        .build()

fun featAverageAbstractScore(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                              abstractSearcher: IndexSearcher): List<Double> {

    val booleanQuery = buildQuery(query)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight").toSet().toList()

        val entityDocs = entities.mapNotNull { entity ->
            val nameQuery = buildEntityNameQuery(entity)
            val searchResult = abstractSearcher.search(nameQuery, 1)
            if (searchResult.scoreDocs.isEmpty()) null else searchResult.scoreDocs[0].doc
        }

        val totalScore = entityDocs.sumByDouble { docId ->
            val score = abstractSearcher.explain(booleanQuery, docId).value
            if (score.isFinite()) score.toDouble() else 0.0
        }

        totalScore / entities.size.toDouble()
    }.toList()
}

