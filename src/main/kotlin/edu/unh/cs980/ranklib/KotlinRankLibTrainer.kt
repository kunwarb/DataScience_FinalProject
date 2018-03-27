@file:JvmName("KotRankLibTrainer")
package edu.unh.cs980.ranklib

import edu.unh.cs980.CONTENT
import edu.unh.cs980.KotlinDatabase
import edu.unh.cs980.KotlinGraphAnalyzer
import edu.unh.cs980.context.HyperlinkIndexer
import edu.unh.cs980.features.*
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.language.GramStatType
import edu.unh.cs980.language.KotlinAbstractAnalyzer
import edu.unh.cs980.language.KotlinGramAnalyzer
import edu.unh.cs980.misc.AnalyzerFunctions
import info.debatty.java.stringsimilarity.Jaccard
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.search.similarities.LMDirichletSimilarity
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity
import org.apache.lucene.search.similarities.LMSimilarity
import org.apache.lucene.search.similarities.Similarity
import java.lang.Double.sum
import java.util.*

/**
 * Function: KotlinRankLibTrainer
 * Description: This is used to encapsulate my different query methods, and the training methods I used to
 *              learn their weights.
 */
class KotlinRankLibTrainer(indexPath: String, queryPath: String, qrelPath: String, graphPath: String) {

    val db = if (graphPath == "") null else KotlinDatabase(graphPath)
    val formatter = KotlinRanklibFormatter(queryPath, qrelPath, indexPath)
    val graphAnalyzer = if (graphPath == "") null else KotlinGraphAnalyzer(formatter.indexSearcher, db!!)



    /**
     * Function: queryCombined
     * Description: Score with weighted combination of BM25, Jaccard string similarity, LM_Dirichlet, and second/third
     *              section headers (trained using RankLib).
     */
    private fun queryCombined() {
//        val weights = listOf(0.3106317698753524,-0.025891305471130843,
//                0.34751201103557083, -0.2358113441529167, -0.08015356975284649)

        val weights = listOf(0.27711892, 0.04586862, 0.24996234, -0.21980639, -0.100580536, 0.008560385,0.09810279)

        formatter.addBM25(weight = weights[0], normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, Jaccard() )
        }, weight = weights[1], normType = NormType.ZSCORE)

        formatter.addFeature({query, tops, indexSearcher ->
            featUseLucSim(query, tops, indexSearcher, LMDirichletSimilarity())
        }, weight = weights[2],
                normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 1) }, weight = weights[3], normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 2) }, weight = weights[4], normType = NormType.ZSCORE)

        val gramIndexSearcher = getIndexSearcher("gram")
        val hLinker = HyperlinkIndexer("entity_mentions.db")
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 0.5)
        }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)
    }

    private fun queryAbstract() {
        formatter.addBM25(normType = NormType.ZSCORE, weight = 0.1295723092588)
        val gramSearcher = getIndexSearcher("gram")
//        formatter.addBM25(normType = NormType.ZSCORE, weight = 1.0)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE, weight = 0.9704276907411262)

//        val hLinker = HyperlinkIndexer("entity_mentions.db")
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE,
//                weight = 0.176)
    }

    private fun querySDMComponents() {
        formatter.addBM25(normType = NormType.ZSCORE, weight = 0.381845239)
        val gramSearcher = getIndexSearcher("gram")
        val hGram = KotlinGramAnalyzer(gramSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0, GramStatType.TYPE_UNIGRAM)
        }, normType = NormType.ZSCORE, weight = -0.385628901)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0, GramStatType.TYPE_BIGRAM)
        }, normType = NormType.ZSCORE, weight = 0.01499519)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0, GramStatType.TYPE_BIGRAM_WINDOW)
        }, normType = NormType.ZSCORE, weight = 0.217530662)
    }

    // Runs associated query method
    fun runRanklibQuery(method: String, out: String) {
        when (method) {
            "abstract_score" -> queryAbstract()
            "sdm_components" -> querySDMComponents()
            "combined" -> queryCombined()
            else -> println("Unknown method!")
        }

        // After scoring according to method, rerank the queries and write them to a run file
        formatter.rerankQueries()
        formatter.queryRetriever.writeQueriesToFile(formatter.queries, out)
    }




    /**
     * Function: trainCombined
     * Description: training for combined method.
     * @see queryCombined
     */
    private fun trainCombined() {
        formatter.addBM25(weight = 1.0, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, Jaccard() )
        }, normType = NormType.ZSCORE)
        formatter.addFeature({query, tops, indexSearcher ->
            featUseLucSim(query, tops, indexSearcher, LMDirichletSimilarity())
        }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 1) }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 2) }, normType = NormType.ZSCORE)

        val gramIndexSearcher = getIndexSearcher("gram")
        val hLinker = HyperlinkIndexer("entity_mentions.db")
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 0.5)
        }, normType = NormType.ZSCORE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)

        //[(1, 0.37643456), (2, 0.06951797), (3, 0.21233706), (4, -0.11022807), (5, -0.03575336), (6, -0.06678948), (7, 0.12893948)]
        //[(1, 0.27711892), (2, 0.04586862), (3, 0.24996234), (4, -0.21980639), (5, -0.100580536), (6, 0.008560385), (7, 0.09810279)]
    }


    private fun trainDirichletAlpha() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val gramIndexSearcher = getIndexSearcher("gram")
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        listOf(2, 8, 16, 32, 64, 128).forEach { alpha ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSDM(query, tops, indexSearcher, hGram, alpha.toDouble())
            }, normType = NormType.ZSCORE)
        }

    }

    private fun trainSDMComponents() {
        val gramIndexSearcher = getIndexSearcher("gram")
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        val grams = listOf(GramStatType.TYPE_UNIGRAM, GramStatType.TYPE_BIGRAM, GramStatType.TYPE_BIGRAM_WINDOW)
        grams.forEach { gram ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSDM(query, tops, indexSearcher, hGram, 4.0, gramType = gram)
            }, normType = NormType.NONE)
        }
    }

    private fun trainEntitySDMComponents() {
        val abstractSearcher = getIndexSearcher("abstract")
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractSearcher)
        val grams = listOf(GramStatType.TYPE_UNIGRAM, GramStatType.TYPE_BIGRAM, GramStatType.TYPE_BIGRAM_WINDOW)
        grams.forEach { gram ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featEntitySDM(query, tops, indexSearcher, abstractAnalyzer, gramType = gram)
            }, normType = NormType.NONE)
        }
    }

    private fun trainAbstractSDM() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val abstractIndexer = getIndexSearcher("abstract")
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featEntitySDM(query, tops, indexSearcher, abstractAnalyzer)
        }, normType = NormType.ZSCORE)

    }

    private fun trainAbstractScore() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val gramIndexSearcher = getIndexSearcher("gram")
//        val hLinker = HyperlinkIndexer("entity_mentions.db")
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featLikelihoodAbstract(query, tops, indexSearcher, abstractAnalyzer) },
//                normType = NormType.ZSCORE)

//        formatter.addFeature({ query, tops, indexSearcher ->
//            featAbstractSim(query, tops, indexSearcher, abstractAnalyzer.indexSearcher, BM25Similarity()) },
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
            "abstract_sdm" -> trainAbstractSDM()
            "train_alpha" -> trainDirichletAlpha()
            "train_sdm_components" -> trainSDMComponents()
            "train_entity_sdm_components" -> trainEntitySDMComponents()
            "combined" -> trainCombined()
            else -> println("Unknown method!")
        }
        formatter.writeToRankLibFile(out)
    }
}
