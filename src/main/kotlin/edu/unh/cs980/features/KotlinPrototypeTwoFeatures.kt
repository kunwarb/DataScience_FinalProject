package edu.unh.cs980.features

import edu.unh.cs980.CONTENT
import edu.unh.cs980.language.KotlinAbstractAnalyzer
import edu.unh.cs980.language.LanguageStats
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import java.io.StringReader
import java.lang.Double.sum
import java.util.concurrent.ConcurrentHashMap
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
        }.toList()

        val totalScore = entityDocs.sumByDouble { docId ->
            val score = abstractSearcher.explain(booleanQuery, docId).value
            if (score.isFinite()) score.toDouble() else 0.0
        }


        totalScore / entities.size.toDouble()
    }.toList()
}

private val memoizedAbstractDocs = ConcurrentHashMap<String, Int?>()
private val memoizedAbstractStats = ConcurrentHashMap<String, LanguageStats?>()

private fun retrieveEntityDocId(entity: String, abstractSearcher: IndexSearcher): Int? =
    memoizedAbstractDocs.computeIfAbsent(entity, {key ->
        val nameQuery = buildEntityNameQuery(entity)
        val searchResult = abstractSearcher.search(nameQuery, 1)
        if (searchResult.scoreDocs.isEmpty()) null else searchResult.scoreDocs[0].doc
    })

private fun retrieveEntityStats(entity: String, abstractAnalyzer: KotlinAbstractAnalyzer): LanguageStats? =
        memoizedAbstractStats.computeIfAbsent(entity, {key ->
            abstractAnalyzer.getEntityStats(entity)
        })

private fun retrieveQueryAbstractStats(query: String, abstractAnalyzer: KotlinAbstractAnalyzer) =
    createTokenSequence(query).toList()
        .run(abstractAnalyzer::getTermStats)


private fun getSmoothedDocFreq(queryFreq: List<Pair<String, Double>>,
                               languageStats: LanguageStats, lambda: Double): Map<String, Double> =
    queryFreq.map { (query, queryTermFreqInCollection) ->
        val collectionFreq = lambda * queryTermFreqInCollection
        val documentFreq = (1 - lambda) * languageStats.docTermFreqs.getOrDefault(query, 0.0)
        query to collectionFreq + documentFreq
    }.toMap()

fun featLikelihoodAbstract(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                             abstractAnalyzer: KotlinAbstractAnalyzer): List<Double> {

    val queryStats = retrieveQueryAbstractStats(query, abstractAnalyzer)
    val queryTerms = queryStats.map(Pair<String,Double>::first)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight").toSet().toList()

        val entityDocs = entities
            .mapNotNull { entity -> retrieveEntityStats(entity, abstractAnalyzer) }
            .toList()

        val finalStats = HashMap<String, Double>()
        entityDocs
            .map { entityLanguageDoc -> getSmoothedDocFreq(queryStats, entityLanguageDoc, 0.5) }
            .forEach { smoothedEntityLikelihood ->
                smoothedEntityLikelihood.forEach { term, freq ->
                    finalStats.merge(term, freq, ::sum)
                }
            }

        queryTerms.sumByDouble { term -> finalStats[term]!! }
    }.toList()
}

