@file:JvmName("KotRankLibTrainer")
package edu.unh.cs980.ranklib

import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import edu.unh.cs980.*
import java.lang.Double.sum
import java.util.*
import info.debatty.java.stringsimilarity.*
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.search.similarities.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Function: KotlinRankLibTrainer
 * Description: This is used to encapsulate my different query methods, and the training methods I used to
 *              learn their weights.
 */
class KotlinRankLibTrainer(indexPath: String, queryPath: String, qrelPath: String, graphPath: String) {

    val db = KotlinDatabase(graphPath)
    val formatter = KotlinRanklibFormatter(queryPath, qrelPath, indexPath)
    val graphAnalyzer = if (graphPath == "") null else KotlinGraphAnalyzer(formatter.indexSearcher, db)


    /**
     * Function: retrieveSequence
     * Description: For qiven query string, filters out numbers (and enwiki) and retruns a list of tokens
     */
    fun retrieveSequence(query: String): List<String> {
        val replaceNumbers = """(\d+|enwiki:)""".toRegex()
        return query.replace(replaceNumbers, "")
            .run { formatter.queryRetriever.createTokenSequence(this) }
            .toList()
    }


    /**
     * Function: addStringDistanceFunction
     * Description: In this method, I try to the distance (or similarity) between the terms (after splitting)
     *              and the entities in each document.
     * @params dist: StingDistance interface (from debatty stringsimilarity library)
     */
    fun addStringDistanceFunction(query: String, tops: TopDocs, dist: StringDistance): List<Double> {
        val tokens = retrieveSequence(query)

        // Map this over the score docs, taking the average similarity between query tokens and entities
        return tops.scoreDocs
            .map { scoreDoc ->
                val doc = formatter.indexSearcher.doc(scoreDoc.doc)
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
    fun addAverageQueryScore(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
        val termQueries = retrieveSequence(query)
            .map { token -> TermQuery(Term(CONTENT, token))}
            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
            .toList()

        return tops.scoreDocs.map { scoreDoc ->
                termQueries.map { booleanQuery ->
                    indexSearcher.explain(booleanQuery, scoreDoc.doc).value.toDouble() }
                    .average()
            }
    }


    /**
     * Function: addEntityQueries
     * Description: This method is supposed to consider query only the
     */
    fun addEntityQueries(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
        val entityQuery = retrieveSequence(query)
            .flatMap { token ->
                listOf(TermQuery(Term("spotlight", token)), TermQuery(Term(CONTENT, token)))
            }
            .fold(BooleanQuery.Builder()) { builder, termQuery ->
                builder.add(termQuery, BooleanClause.Occur.SHOULD) }
            .build()


        return tops.scoreDocs.map { scoreDoc ->
                indexSearcher.explain(entityQuery, scoreDoc.doc).value.toDouble() }
    }


    /**
     * Function: useLucSim
     * Description: Generalized function for testing similarity function from query to paragraph text.
     */
    fun useLucSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher, sim: Similarity): List<Double> {
        val entityQuery = retrieveSequence(query)
            .map { token -> TermQuery(Term(CONTENT, token)) }
            .fold(BooleanQuery.Builder()) { builder, termQuery ->
                builder.add(termQuery, BooleanClause.Occur.SHOULD) }
            .build()

        val curSim = indexSearcher.getSimilarity(true)
        indexSearcher.setSimilarity(sim)

        return tops.scoreDocs.map { scoreDoc ->
                                    indexSearcher.explain(entityQuery, scoreDoc.doc).value.toDouble() }
            .apply { indexSearcher.setSimilarity(curSim) }  // Gotta remember to set the similarity back
    }



    fun sectionSplit(query: String, tops: TopDocs, indexSearcher: IndexSearcher, secIndex: Int): List<Double> {
        val termQueries = retrieveSequence(query)
            .map { token -> TermQuery(Term(CONTENT, token))}
            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
            .toList()

        if (termQueries.size < secIndex + 1) {
            return (0 until tops.scoreDocs.size).map { 0.0 }
        }

//        val termQuery = TermQuery(Term(CONTENT, termQueries[secIndex]!!))
        val boolQuery = termQueries[secIndex]!!

        return tops.scoreDocs
            .map { scoreDoc ->
                indexSearcher.explain(boolQuery, scoreDoc.doc).value.toDouble()
            }
    }


    val scount = AtomicInteger(0)
    fun addScoreMixtureSims(query: String, tops:TopDocs, indexSearcher: IndexSearcher): List<Double> {
        val sinks = HashMap<String, Double>()
        val mixtures = graphAnalyzer!!.getMixtures(tops)

        mixtures.forEach { pm ->
            pm.mixture.forEach { entity, probability ->
                sinks.merge(entity, probability * pm.score, ::sum)
            }
        }

        val total = sinks.values.sum()
        sinks.replaceAll { k, v -> v / total }

        println(scount.incrementAndGet())

        return mixtures
            .map { pm -> pm.mixture.entries.sumByDouble { (k, v) -> sinks[k]!! * v } }
//            .map {pm -> pm.score}
//            .map { pm -> pm.score }
            .toList()
    }

    fun queryStandard() {

    }

    fun querySimilarity() {
        formatter.addBM25(weight = 0.884669653, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, JaroWinkler())}, weight = -0.001055, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, weight = 0.11427, normType = NormType.ZSCORE)
    }

    private fun queryAverage() {
        formatter.addBM25(weight = 0.5, normType = NormType.ZSCORE)
        formatter.addFeature(this::addAverageQueryScore, weight = 0.5, normType = NormType.ZSCORE)
    }

    private fun querySplit() {
        formatter.addBM25(weight = 0.4824247, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 0) }, weight = 0.069, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 1) }, weight = -0.1845, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 2) }, weight = -0.25063, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 3) }, weight = 0.0134, normType = NormType.ZSCORE)
    }

    private fun queryMixtures() {
        if (graphAnalyzer == null) {
            println("You must supply a --graph_database location for this method!")
            return
        }
        formatter.addBM25(weight = 0.9703138257, normType = NormType.ZSCORE)
        formatter.addFeature(this::addScoreMixtureSims, weight = 0.029686174, normType = NormType.ZSCORE)
    }

    private fun queryDirichlet() {
        formatter.addBM25(weight = 0.80067, normType = NormType.ZSCORE)
        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMDirichletSimilarity())}, weight = 0.19932975,
                normType = NormType.ZSCORE)
    }

    private fun queryMercer() {
        formatter.addBM25(weight = 0.82, normType = NormType.ZSCORE)

        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMJelinekMercerSimilarity(LMSimilarity.DefaultCollectionModel(),
                    0.5f))}, weight = 0.1798988, normType = NormType.ZSCORE)
    }

    private fun queryCombined() {
        val weights = listOf(0.3106317698753524,-0.025891305471130843,
                0.34751201103557083, -0.2358113441529167, -0.08015356975284649)
//        val weights = listOf(0.40138524776868684, 0.2560172622244137, -0.23199890320801206, -0.11059858679888734)
//        val weights = listOf(0.6351872044086408, 0.2425613502855492, -0.0506568027797861424, 0.0715946425079486)

        formatter.addBM25(weight = weights[0], normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, weight = weights[1], normType = NormType.ZSCORE)

        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMDirichletSimilarity())}, weight = weights[2],
                normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 1) }, weight = weights[3], normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 2) }, weight = weights[4], normType = NormType.ZSCORE)
    }

    fun runRanklibQuery(method: String, out: String) {
        when (method) {
            "bm25" -> queryStandard()
            "entity_similarity" -> querySimilarity()
            "average_query" -> queryAverage()
            "split_sections" -> querySplit()
            "mixtures" -> queryMixtures()
            "lm_mercer" -> queryMercer()
            "lm_dirichlet" -> queryDirichlet()
            "combined" -> queryCombined()
            else -> println("Unknown method!")
        }
        formatter.rerankQueries()
        formatter.queryRetriever.writeQueriesToFile(formatter.queries, out)
    }

    private fun trainSimilarity() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, JaroWinkler())}, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, normType = NormType.ZSCORE)
    }

    private fun trainSplit() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 0) }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 1) }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 2) }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 3) }, normType = NormType.ZSCORE)
    }

    private fun trainMixtures() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(this::addScoreMixtureSims, normType = NormType.ZSCORE)
    }

    private fun trainAverageQuery() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(this::addAverageQueryScore, normType = NormType.ZSCORE)
    }

    private fun trainEntityQuery() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(this::addEntityQueries, normType = NormType.ZSCORE)
    }

    private fun trainDirichSim() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMDirichletSimilarity())}, normType = NormType.ZSCORE)
    }

    private fun trainJelinekMercerSimilarity() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMJelinekMercerSimilarity(LMSimilarity.DefaultCollectionModel(),
                    0.5f))}, normType = NormType.ZSCORE)
    }

    private fun trainCombined() {
        formatter.addBM25(weight = 1.0, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, normType = NormType.ZSCORE)
        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMDirichletSimilarity())}, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 1) }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            sectionSplit(query, tops, indexSearcher, 2) }, normType = NormType.ZSCORE)
    }

    fun train(method: String, out: String) {
        when (method) {
            "entity_similarity" -> trainSimilarity()
            "average_query" -> trainAverageQuery()
            "split_sections" -> trainSplit()
            "mixtures" -> trainMixtures()
            "combined" -> trainCombined()
            "entity_query" -> trainEntityQuery()
            "lm_dirichlet" -> trainDirichSim()
            "lm_mercer" -> trainJelinekMercerSimilarity()
            else -> println("Unknown method!")
        }
        formatter.writeToRankLibFile(out)
    }


}
