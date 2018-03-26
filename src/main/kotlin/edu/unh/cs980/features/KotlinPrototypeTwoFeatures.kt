package edu.unh.cs980.features

import edu.unh.cs980.CONTENT
import edu.unh.cs980.context.HyperlinkIndexer
import edu.unh.cs980.language.GramStatType
import edu.unh.cs980.language.KotlinAbstractAnalyzer
import edu.unh.cs980.language.KotlinGramAnalyzer
import edu.unh.cs980.language.LanguageStatContainer
import info.debatty.java.stringsimilarity.Jaccard
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.search.similarities.LMDirichletSimilarity
import org.apache.lucene.search.similarities.Similarity
import java.io.StringReader
import java.lang.Double.max
import java.lang.Double.sum
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.buildSequence

private val englishAnalyzer = StandardAnalyzer()

private fun createTokenSequence(query: String): Sequence<String> {
    val replaceNumbers = """(\d+|enwiki:)""".toRegex()
    val cleanQuery = query.replace(replaceNumbers, "").replace("/", " ")
    val tokenStream = englishAnalyzer.tokenStream("text", StringReader(cleanQuery)).apply { reset() }

    return buildSequence<String> {
        while (tokenStream.incrementToken()) {
            yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
        }
        tokenStream.end()
        tokenStream.close()
    }
}



private fun buildQuery(query: String): BooleanQuery =
    createTokenSequence(query)
        .map { token -> TermQuery(Term(CONTENT, token))}
        .fold(BooleanQuery.Builder()) { builder, termQuery ->
            builder.add(termQuery, BooleanClause.Occur.SHOULD) }
        .build()

//fun featAverageAbstractScore(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
//                              abstractSearcher: IndexSearcher): List<Double> {
//
//    val booleanQuery = buildQuery(query)
//
//    return tops.scoreDocs.map { scoreDoc ->
//        val doc = indexSearcher.doc(scoreDoc.doc)
//        val entities = doc.getValues("spotlight").toSet().toList()
//
//        val entityDocs = entities.mapNotNull { entity ->
//            val nameQuery = buildEntityNameQuery(entity)
//            val searchResult = abstractSearcher.search(nameQuery, 1)
//            if (searchResult.scoreDocs.isEmpty()) null else searchResult.scoreDocs[0].doc
//        }.toList()
//
//        val totalScore = entityDocs.sumByDouble { docId ->
//            val score = abstractSearcher.explain(booleanQuery, docId).value
//            if (score.isFinite()) score.toDouble() else 0.0
//        }
//
//
//        totalScore / entities.size.toDouble()
//    }.toList()
//}

//private val memoizedAbstractStats = ConcurrentHashMap<String, LanguageStats?>()
//
//
//private fun retrieveEntityDocId(entity: String, abstractSearcher: IndexSearcher): Int? =
//    memoizedAbstractDocs.computeIfAbsent(entity, {key ->
//        val nameQuery = buildEntityNameQuery(entity)
//        val searchResult = abstractSearcher.search(nameQuery, 1)
//        if (searchResult.scoreDocs.isEmpty()) null else searchResult.scoreDocs[0].doc
//    })
//
//private fun retrieveEntityStats(entity: String, abstractAnalyzer: KotlinAbstractAnalyzer): LanguageStats? =
//        memoizedAbstractStats.computeIfAbsent(entity, {key ->
//            abstractAnalyzer.getEntityStats(entity)
//        })
//
//private fun retrieveQueryAbstractStats(query: String, abstractAnalyzer: KotlinAbstractAnalyzer) =
//    createTokenSequence(query).toList()
//        .run(abstractAnalyzer::getTermStats)
//
//
//private fun getSmoothedDocFreq(queryFreq: List<Pair<String, Double>>,
//                               languageStats: LanguageStats, lambda: Double): Map<String, Double> =
//    queryFreq.map { (query, queryTermFreqInCollection) ->
//        val collectionFreq = lambda * queryTermFreqInCollection
//        val documentFreq = (1 - lambda) * languageStats.docTermFreqs.getOrDefault(query, 0.0)
//        query to collectionFreq + documentFreq
//    }.toMap()

//fun featLikelihoodAbstract(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
//                             abstractAnalyzer: KotlinAbstractAnalyzer): List<Double> {
//
//    val queryStats = retrieveQueryAbstractStats(query, abstractAnalyzer)
//    val queryTerms = queryStats.map(Pair<String,Double>::first)
//
//    return tops.scoreDocs.map { scoreDoc ->
//        val doc = indexSearcher.doc(scoreDoc.doc)
//        val entities = doc.getValues("spotlight").toSet().toList()
//
//        val entityDocs = entities
//            .mapNotNull { entity -> retrieveEntityStats(entity, abstractAnalyzer) }
//            .toList()
//
////        val finalStats = HashMap<String, Double>()
////        entityDocs
////            .map { entityLanguageDoc -> getSmoothedDocFreq(queryStats, entityLanguageDoc, 0.5) }
////            .forEach { smoothedEntityLikelihood ->
////                smoothedEntityLikelihood.forEach { term, freq ->
////                    finalStats.merge(term, freq, ::sum)
////                }
////            }
//        0.0
////        queryTerms.sumByDouble { term -> finalStats[term]!! }
//    }.toList()
//}


// Get likelihood of query given entity mention
// Then get likelihood of entity mention given document
fun featLikehoodOfQueryGivenEntityMention(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                          hIndexer: HyperlinkIndexer): List<Double> {

    val queryTokens = createTokenSequence(query).toList()
    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight").toList()
        queryTokens
            .map    { queryToken ->
                         entities
                            .map { entity -> hIndexer.getMentionLikelihood(queryToken, entity) }
                            .sum()
                    }.sum()
//            .fold(1.0, {acc, likelihood -> acc * max(likelihood, 0.001)})
    }.toList()
}

// Get likelihood of entity (given indexed abstract) given query
// Then get likelihood of entity given document
// Then go backwards

fun featAbstractSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                          abstractSearcher: IndexSearcher, sim: Similarity): List<Double> {

    val booleanQuery = buildQuery(query)
    val tokens = createTokenSequence(query).toList().joinToString()
    val jac = Jaccard()

    abstractSearcher.setSimilarity(sim)
    val relevantEntities = abstractSearcher.search(booleanQuery, 200)
//    println("$query: ${relevantEntities.maxScore}")
//    val totalScore = relevantEntities.scoreDocs.sumByDouble { it.score.toDouble() }

    val entityScores = relevantEntities.scoreDocs.map { scoreDoc ->
        val doc = abstractSearcher.doc(scoreDoc.doc)
        val entity = doc.get("name")
        entity.toLowerCase().replace(" ", "_") to scoreDoc.score.toDouble()
    }.toList()


    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight").toList()
        entities
            .map { entity -> entityScores.maxBy { (e, v) -> jac.distance(entity, e) }?.let {it.second * jac.distance(entity, it.first)} ?: 0.0 }
//            .mapNotNull { entity -> entityScores[entity] }
            .sum()
    }.toList()
}



fun featSDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
            gramAnalyzer: KotlinGramAnalyzer): List<Double> {
    val tokens = createTokenSequence(query).toList()
    val cleanQuery = tokens.toList().joinToString(" ")

    val queryCorpus = gramAnalyzer.getCorpusStatContainer(query)

//    val queryUnigram = gramAnalyzer.getStats(cleanQuery, GramStatType.TYPE_UNIGRAM)
//    val queryBigram = gramAnalyzer.getStats(cleanQuery, GramStatType.TYPE_BIGRAM)
//    val queryWindow = gramAnalyzer.getStats(cleanQuery, GramStatType.TYPE_BIGRAM_WINDOW)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val text = cleanQuery + " " + doc.get(CONTENT) + " " + cleanQuery
        val docStat = gramAnalyzer.getLanguageStatContainer(text)
        val queryLikelihood = docStat.getLikelihoodGivenQuery(queryCorpus, 0.5)
        val v1 = queryLikelihood.unigramLikelihood.likelihood()
        val v2 = queryLikelihood.bigramLikelihood.likelihood()
        val v3 = queryLikelihood.bigramWindowLikelihood.likelihood()

//        val docUnigram = gramAnalyzer
//            .getStats(text, GramStatType.TYPE_UNIGRAM)
//            .smooth(0.5)
//        val docBigram = gramAnalyzer
//            .getStats(text, GramStatType.TYPE_BIGRAM)
//            .smooth(0.5)
//        val docBigramWindow = gramAnalyzer
//            .getStats(text, GramStatType.TYPE_BIGRAM_WINDOW)
//            .smooth(0.5)

//        val v1 = queryUnigram.docTermCounts.keys.map { key -> docUnigram[key]!! }.sum()
//        val v2 = queryBigram.docTermCounts.keys.map { key -> docBigram[key]!! }.sum()
//        val v3 = queryWindow.docTermCounts.keys.map { key -> docBigramWindow[key]!! }.sum()

        v1 + v2 + v3
    }
}


fun featEntitySDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                  abstractAnalyzer: KotlinAbstractAnalyzer): List<Double> {
    val tokens = createTokenSequence(query).toList()
    val cleanQuery = tokens.toList().joinToString(" ")

    val queryCorpus = abstractAnalyzer.gramAnalyzer.getCorpusStatContainer(query)
    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight")

        entities.mapNotNull { entity -> abstractAnalyzer.retrieveEntityDoc(entity) }
            .map { entityDoc -> cleanQuery + entityDoc.get("text") + cleanQuery }
            .map(abstractAnalyzer.gramAnalyzer::getLanguageStatContainer)
            .map { stat -> stat.getLikelihoodGivenQuery(queryCorpus, 0.5)}
            .map { queryLikelihood ->
                val v1 = queryLikelihood.unigramLikelihood.likelihood()
                val v2 = queryLikelihood.bigramLikelihood.likelihood()
                val v3 = queryLikelihood.bigramWindowLikelihood.likelihood()
                v1 + v2 + v3 }
            .average()
    }
}

