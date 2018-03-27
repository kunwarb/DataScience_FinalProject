package edu.unh.cs980.features

import edu.unh.cs980.KotlinGraphAnalyzer
import edu.unh.cs980.misc.AnalyzerFunctions
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.similarities.Similarity
import java.lang.Double.sum

/**
 * Function: addStringDistanceFunction
 * Description: In this method, I try to the distance (or similarity) between the terms (after splitting)
 *              and the entities in each document.
 * @params dist: StingDistance interface (from debatty stringsimilarity library)
 */
fun featAddStringDistanceFunction(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                  dist: StringDistance): List<Double> {
//        val tokens = retrieveSequence(query)
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)

    // Map this over the score docs, taking the average similarity between query tokens and entities
    return tops.scoreDocs
        .map { scoreDoc ->
//            val doc = formatter.indexSearcher.doc(scoreDoc.doc)
            val doc = indexSearcher.doc(scoreDoc.doc)
            val entities = doc.getValues("spotlight").map { it.replace("_", " ") }
            if (entities.isEmpty()) 0.0 else
            // This is the actual part where I average the results using the distance function
                tokens.flatMap { q -> entities.map { e -> dist.distance(q, e)  } }.average()
        }
}

/**
 * Function: addAverageQueryScore
 * Description: In this method, I tokenize the query and treat each token as an individual query.
 *              I then get the BM25 score of each query to each document and average the results.
 */
fun featAddAverageQueryScore(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
//        val termQueries = retrieveSequence(query)
    val termQueries = AnalyzerFunctions.createQueryList(query, useFiltering = true)
//        .map { token -> TermQuery(Term(CONTENT, token))}
//        .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
//        .toList()

    return tops.scoreDocs.map { scoreDoc ->
        termQueries.map { booleanQuery ->
            indexSearcher.explain(booleanQuery, scoreDoc.doc).value.toDouble() }
            .average()
    }
}


/**
 * Function: addEntityQueries
 * Description: This method calculates the score using only entities
 */
fun featAddEntityQueries(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
//        val entityQuery = retrieveSequence(query)
    val entityQuery = AnalyzerFunctions.createQuery(query, useFiltering = true, field = "spotlight")
//    val entityQuery = AnalyzerFunctions.createTokenList(query, useFiltering = true)
//        .flatMap { token ->
//            listOf(TermQuery(Term("spotlight", token)), TermQuery(Term(CONTENT, token)))
//        }
//        .fold(BooleanQuery.Builder()) { builder, termQuery ->
//            builder.add(termQuery, BooleanClause.Occur.SHOULD) }
//        .build()


    return tops.scoreDocs.map { scoreDoc ->
        indexSearcher.explain(entityQuery, scoreDoc.doc).value.toDouble() }
}


/**
 * Function: useLucSim
 * Description: Takes a Lucene similarity function and uses it to rescore documents.
 */
fun featUseLucSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher, sim: Similarity): List<Double> {
//        val entityQuery = retrieveSequence(query)
    val booleanQuery = AnalyzerFunctions.createQuery(query, useFiltering = true)
//        val entityQuery = AnalyzerFunctions.createTokenList(query, useFiltering = true)
//            .map { token -> TermQuery(Term(CONTENT, token)) }
//            .fold(BooleanQuery.Builder()) { builder, termQuery ->
//                builder.add(termQuery, BooleanClause.Occur.SHOULD) }
//            .build()

    val curSim = indexSearcher.getSimilarity(true)
    indexSearcher.setSimilarity(sim)

    return tops.scoreDocs.map { scoreDoc ->
        indexSearcher.explain(booleanQuery, scoreDoc.doc).value.toDouble() }
        .apply { indexSearcher.setSimilarity(curSim) }  // Gotta remember to set the similarity back
}


/**
 * Function: sectionSplit
 * Description: Splits query up and only uses a particular section for scoring.
 */
fun featSectionSplit(query: String, tops: TopDocs, indexSearcher: IndexSearcher, secIndex: Int): List<Double> {
//        val termQueries = retrieveSequence(query)
//            .map { token -> TermQuery(Term(CONTENT, token))}
//            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
//            .toList()
    val termQueries = AnalyzerFunctions.createQueryList(query, useFiltering = true)

    if (termQueries.size < secIndex + 1) {
        return (0 until tops.scoreDocs.size).map { 0.0 }
    }

    val boolQuery = termQueries[secIndex]!!

    return tops.scoreDocs
        .map { scoreDoc ->
            indexSearcher.explain(boolQuery, scoreDoc.doc).value.toDouble()
        }
}


/**
 * Function: addScoreMixtureSims
 * Description: Uses Random Walk model over bipartite graph of entities and paragraphs to rescore paragraphs.
 */
fun featAddScoreMixtureSims(query: String, tops:TopDocs, indexSearcher: IndexSearcher,
                        graphAnalyzer: KotlinGraphAnalyzer): List<Double> {
    val sinks = HashMap<String, Double>()
    val mixtures = graphAnalyzer!!.getMixtures(tops)

    mixtures.forEach { pm ->
        pm.mixture.forEach { entity, probability ->
            sinks.merge(entity, probability * pm.score, ::sum)
        }
    }

    val total = sinks.values.sum()
    sinks.replaceAll { k, v -> v / total }

    // The score of each paragraph depends on the total value of all scored paragraphs with respect to their
    // distributions over entities.
    return mixtures
        .map { pm -> pm.mixture.entries.sumByDouble { (k, v) -> sinks[k]!! * v } }
        .toList()
}
