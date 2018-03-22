@file:JvmName("KotRankLibTrainer")
package edu.unh.cs980.ranklib

import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import edu.unh.cs980.*
import edu.unh.cs980.context.HyperlinkIndexer
import edu.unh.cs980.features.featAverageAbstractScore
import edu.unh.cs980.features.featLikehoodOfQueryGivenEntityMention
import edu.unh.cs980.features.featLikelihoodAbstract
import edu.unh.cs980.language.KotlinAbstractAnalyzer
import java.lang.Double.sum
import java.util.*
import info.debatty.java.stringsimilarity.*
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.index.Fields
import org.apache.lucene.index.TermContext
import org.apache.lucene.search.similarities.*
import java.lang.Double.max
import java.lang.Float.max
import java.util.concurrent.atomic.AtomicInteger

/**
 * Function: KotlinRankLibTrainer
 * Description: This is used to encapsulate my different query methods, and the training methods I used to
 *              learn their weights.
 */
class KotlinRankLibTrainer(indexPath: String, queryPath: String, qrelPath: String, graphPath: String) {

    val db = if (graphPath == "") null else KotlinDatabase(graphPath)
    val formatter = KotlinRanklibFormatter(queryPath, qrelPath, indexPath)
    val graphAnalyzer = if (graphPath == "") null else KotlinGraphAnalyzer(formatter.indexSearcher, db!!)
    val abstractAnalyzer = KotlinAbstractAnalyzer("abstract")


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
     * Description: Takes a Lucene similarity function and uses it to rescore documents.
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


    /**
     * Function: sectionSplit
     * Description: Splits query up and only uses a particular section for scoring.
     */
    fun sectionSplit(query: String, tops: TopDocs, indexSearcher: IndexSearcher, secIndex: Int): List<Double> {
        val termQueries = retrieveSequence(query)
            .map { token -> TermQuery(Term(CONTENT, token))}
            .map { termQuery -> BooleanQuery.Builder().add(termQuery, BooleanClause.Occur.SHOULD).build()}
            .toList()

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

        // The score of each paragraph depends on the total value of all scored paragraphs with respect to their
        // distributions over entities.
        return mixtures
            .map { pm -> pm.mixture.entries.sumByDouble { (k, v) -> sinks[k]!! * v } }
            .toList()
    }


    fun expandSearch(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List <Double> {
        val sinks = HashMap<String, Double>()

//        tops.scoreDocs.forEach { scoreDoc ->
//            val boolQuery = retrieveSequence(query)
//                .map { token -> TermQuery(Term(CONTENT, token))}
//                .fold(BooleanQuery.Builder()) { builder, termQuery ->
//                    builder.add(termQuery, BooleanClause.Occur.SHOULD) }
//                .build()
//
//            val neighbors = indexSearcher.search(boolQuery, 100)
//            val scoreSum = neighbors.scoreDocs.sumByDouble { neighDoc -> neighDoc.score.toDouble() }
//
//            neighbors.scoreDocs.forEach { neighDoc ->
//                val id = indexSearcher.doc(neighDoc.doc).get(PID)
//                val ratio = neighDoc.score.toDouble() / scoreSum
//                val adjustedScore = ratio * scoreDoc.score
//
//                sinks.merge(id, adjustedScore, ::sum)
//            }
//        }

        return tops.scoreDocs
//            .map { scoreDoc ->
//                    val id = indexSearcher.doc(scoreDoc.doc).get(PID)
//                    sinks[id] ?: 0.0 }
            .map { scoreDoc ->
                val doc = indexSearcher.doc(scoreDoc.doc)
                val content = doc.get(CONTENT)
                val tv = indexSearcher.indexReader.getTermVector(scoreDoc.doc, CONTENT)
                println(tv)

//                val boolQuery = retrieveSequence(content)
//                    .map { token -> TermQuery(Term(CONTENT, token))}
//                    .fold(BooleanQuery.Builder()) { builder, termQuery ->
//                        builder.add(termQuery, BooleanClause.Occur.SHOULD) }
//                    .build()
//
//                val neighborSum = tops.scoreDocs.sumByDouble { neighbor ->
//                    indexSearcher.explain(boolQuery, neighbor.doc).value.toDouble()
//                }
//                scoreDoc.score.toDouble() / neighborSum
                1.0

//                val neighbors = indexSearcher.search(boolQuery, 10)
//                val neighborSum = neighbors.scoreDocs.sumByDouble { neighborDoc -> neighborDoc.score.toDouble() }
//                scoreDoc.score.toDouble() / neighborSum
            }
    }



    /**
     * Function: querySimilarity
     * Description: Score with weighted combination of BM25 and string similarity functions (trained using RankLib).
     */
    fun querySimilarity() {
        formatter.addBM25(weight = 0.884669653, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, JaroWinkler())}, weight = -0.001055, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, weight = 0.11427, normType = NormType.ZSCORE)
    }


    /**
     * Function: querySimilarity
     * Description: Score with weighted combination of BM25 and average_query (trained using RankLib).
     */
    private fun queryAverage() {
        formatter.addBM25(weight = 0.5, normType = NormType.ZSCORE)
        formatter.addFeature(this::addAverageQueryScore, weight = 0.5, normType = NormType.ZSCORE)
    }


    /**
     * Function: querySplit
     * Description: Score with weighted combination of BM25 and separate section scores (trained using RankLib).
     */
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


    /**
     * Function: queryMixtures
     * Description: Score with weighted combination of BM25 and mixtures method (trained using RankLib).
     */
    private fun queryMixtures() {
        if (graphAnalyzer == null) {
            println("You must supply a --graph_database location for this method!")
            return
        }
        formatter.addBM25(weight = 0.9703138257, normType = NormType.ZSCORE)
        formatter.addFeature(this::addScoreMixtureSims, weight = 0.029686174, normType = NormType.ZSCORE)
    }


    /**
     * Function: queryDirichlet
     * Description: Score with weighted combination of BM25 and LM_Dirichlet method (trained using RankLib)
     */
    private fun queryDirichlet() {
        formatter.addBM25(weight = 0.80067, normType = NormType.ZSCORE)
        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMDirichletSimilarity())}, weight = 0.19932975,
                normType = NormType.ZSCORE)
    }


    /**
     * Function: queryMercer
     * Description: Score with weighted combination of BM25 and LM_Dirichlet method (trained using RankLib)
     */
    private fun queryMercer() {
        formatter.addBM25(weight = 0.82, normType = NormType.ZSCORE)

        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMJelinekMercerSimilarity(LMSimilarity.DefaultCollectionModel(),
                    0.5f))}, weight = 0.1798988, normType = NormType.ZSCORE)
    }


    /**
     * Function: queryMercer
     * Description: Score with weighted combination of BM25, Jaccard string similarity, LM_Dirichlet, and second/third
     *              section headers (trained using RankLib).
     */
    private fun queryCombined() {
        val weights = listOf(0.3106317698753524,-0.025891305471130843,
                0.34751201103557083, -0.2358113441529167, -0.08015356975284649)

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

    private fun queryAbstract() {
        formatter.addBM25(normType = NormType.ZSCORE, weight = 0.8236)
        val hLinker = HyperlinkIndexer("entity_mentions.db")
        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE,
                weight = 0.176)
    }

    // Runs associated query method
    fun runRanklibQuery(method: String, out: String) {
        when (method) {
            "abstract_score" -> queryAbstract()
            else -> println("Unknown method!")
        }

        // After scoring according to method, rerank the queries and write them to a run file
        formatter.rerankQueries()
        formatter.queryRetriever.writeQueriesToFile(formatter.queries, out)
    }


    /**
     * Function: trainSimilarity
     * Description: training for string_similarity method.
     * @see querySimilarity
     */
    private fun trainSimilarity() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, JaroWinkler())}, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, _ ->
            addStringDistanceFunction(query, tops, Jaccard() )}, normType = NormType.ZSCORE)
    }


    /**
     * Function: trainSplit
     * Description: training for section_split method.
     * @see sectionSplit
     */
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


    /**
     * Function: trainMixtures
     * Description: training for mixtures method.
     * @see queryMixtures
     */
    private fun trainMixtures() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(this::addScoreMixtureSims, normType = NormType.ZSCORE)
    }


    /**
     * Function: trainAverageQuery
     * Description: training for average_query method.
     * @see queryAverage
     */
    private fun trainAverageQuery() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(this::addAverageQueryScore, normType = NormType.ZSCORE)
    }


    /**
     * Function: trainDirichSim
     * Description: training for lm_dirichlet method.
     * @see queryDirichlet
     */
    private fun trainDirichSim() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMDirichletSimilarity())}, normType = NormType.ZSCORE)
    }


    /**
     * Function: trainJelinekMercerSimilarity
     * Description: training for lm_mercer method.
     * @see queryMercer
     */
    private fun trainJelinekMercerSimilarity() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature({query, tops, indexSearcher ->
            useLucSim(query, tops, indexSearcher, LMJelinekMercerSimilarity(LMSimilarity.DefaultCollectionModel(),
                    0.5f))}, normType = NormType.ZSCORE)
    }


    /**
     * Function: trainCombined
     * Description: training for combined method.
     * @see queryCombined
     */
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



    private fun trainExpandSearch() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(this::expandSearch, normType = NormType.ZSCORE)
    }

    private fun trainAbstractScore() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val hLinker = HyperlinkIndexer("entity_mentions.db")
        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featLikelihoodAbstract(query, tops, indexSearcher, abstractAnalyzer) },
//                normType = NormType.ZSCORE)

//        formatter.addFeature({ query, tops, indexSearcher ->
//            featAverageAbstractScore(query, tops, indexSearcher, abstractAnalyzer.indexSearcher) },
//            normType = NormType.ZSCORE)
    }

    /**
     * Function: train
     * Description: Add features associated with training method and then writes scored features to a RankLib compatible
     *              file for later use in training weights.
     */
    fun train(method: String, out: String) {
        when (method) {
            "abstract_score" -> trainAbstractScore()
            else -> println("Unknown method!")
        }
        formatter.writeToRankLibFile(out)
    }
}
