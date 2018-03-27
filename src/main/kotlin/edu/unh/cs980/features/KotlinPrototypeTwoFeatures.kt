package edu.unh.cs980.features

import edu.unh.cs980.CONTENT
import edu.unh.cs980.context.HyperlinkIndexer
import edu.unh.cs980.language.GramStatType
import edu.unh.cs980.language.KotlinAbstractAnalyzer
import edu.unh.cs980.language.KotlinGramAnalyzer
import edu.unh.cs980.misc.AnalyzerFunctions
import info.debatty.java.stringsimilarity.Jaccard
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.search.similarities.Similarity
import java.io.StringReader
import kotlin.coroutines.experimental.buildSequence

private val analyzer = StandardAnalyzer()

//private fun createTokenSequence(query: String): Sequence<String> {
//    val replaceNumbers = """(\d+|enwiki:)""".toRegex()
//    val cleanQuery = query.replace(replaceNumbers, "").replace("/", " ")
//    val tokenStream = analyzer.tokenStream("text", StringReader(cleanQuery)).apply { reset() }
//
//    return buildSequence<String> {
//        while (tokenStream.incrementToken()) {
//            yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
//        }
//        tokenStream.end()
//        tokenStream.close()
//    }
//}



//private fun buildQuery(query: String): BooleanQuery =
//    createTokenSequence(query)
//        .map { token -> TermQuery(Term(CONTENT, token))}
//        .fold(BooleanQuery.Builder()) { builder, termQuery ->
//            builder.add(termQuery, BooleanClause.Occur.SHOULD) }
//        .build()




// Get likelihood of query given entity mention
// Then get likelihood of entity mention given document
fun featLikehoodOfQueryGivenEntityMention(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                          hIndexer: HyperlinkIndexer): List<Double> {

//    val queryTokens = createTokenSequence(query).toList()
    val queryTokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
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


fun featAbstractSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                          abstractSearcher: IndexSearcher, sim: Similarity): List<Double> {

//    val booleanQuery = buildQuery(query)
    val booleanQuery = AnalyzerFunctions.createQuery(query, useFiltering = true)
//    val tokens = createTokenSequence(query).toList().joinToString()
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
            gramAnalyzer: KotlinGramAnalyzer, alpha: Double,
            gramType: GramStatType? = null): List<Double> {
//    val tokens = createTokenSequence(query).toList()
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")

    val queryCorpus = gramAnalyzer.getCorpusStatContainer(cleanQuery)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
//        val text = cleanQuery + " " + doc.get(CONTENT) + " " + cleanQuery
        val text = doc.get(CONTENT)
        val docStat = gramAnalyzer.getLanguageStatContainer(text)

        val queryLikelihood = docStat.getLikelihoodGivenQuery(queryCorpus, alpha)
        val v1 = queryLikelihood.unigramLikelihood
        val v2 = queryLikelihood.bigramLikelihood
        val v3 = queryLikelihood.bigramWindowLikelihood
        val weights = listOf(0.9113992744, 0.08220043144599, 0.0064001941)

        when (gramType) {
            GramStatType.TYPE_UNIGRAM -> v1
            GramStatType.TYPE_BIGRAM -> v2
            GramStatType.TYPE_BIGRAM_WINDOW -> v3
            else -> v1 + v2 + v3
//            else -> v1 * weights[0] + v2 * weights[1] + v3 * weights[2]
        }
//        val v1 = queryLikelihood.unigramLikelihood.likelihood()
//        val v2 = queryLikelihood.bigramLikelihood.likelihood()
//        val v3 = queryLikelihood.bigramWindowLikelihood.likelihood()
//        v1 + v2 + v3
    }
}


fun featEntitySDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                  abstractAnalyzer: KotlinAbstractAnalyzer): List<Double> {
//    val tokens = createTokenSequence(query).toList()
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")

    val queryCorpus = abstractAnalyzer.gramAnalyzer.getCorpusStatContainer(cleanQuery)
    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight")

        entities.mapNotNull { entity -> abstractAnalyzer.retrieveEntityDoc(entity) }
            .map { entityDoc -> entityDoc.get("text")  }
//            .map { entityDoc -> cleanQuery + entityDoc.get("text") + cleanQuery }
            .map(abstractAnalyzer.gramAnalyzer::getLanguageStatContainer)
            .map { stat -> stat.getLikelihoodGivenQuery(queryCorpus, 0.5)}
            .map { queryLikelihood ->
                val v1 = queryLikelihood.unigramLikelihood
                val v2 = queryLikelihood.bigramLikelihood
                val v3 = queryLikelihood.bigramWindowLikelihood
//                val v1 = queryLikelihood.unigramLikelihood.likelihood()
//                val v2 = queryLikelihood.bigramLikelihood.likelihood()
//                val v3 = queryLikelihood.bigramWindowLikelihood.likelihood()
                v1 + v2 + v3 }
            .average()
    }
}
