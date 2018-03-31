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
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import info.debatty.java.stringsimilarity.SorensenDice
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.search.similarities.*
import edu.unh.cs980.WordEmbedding.TFIDFSimilarity
import java.lang.Double.sum
import java.util.*
import kotlin.math.abs


/**
 * Function: KotlinRankLibTrainer
 * Description: This is used to encapsulate my different query methods, and the training methods I used to
 *              learn their weights.
 */
class KotlinRankLibTrainer(val indexPath: String, val queryPath: String, val qrelPath: String,
                           val hyperlinkPath: String, val abstractPath: String, val gramPath: String ) {

//    val db = if (graphPath == "") null else KotlinDatabase(graphPath)
    val formatter = KotlinRanklibFormatter(queryPath, qrelPath, indexPath)
//    val graphAnalyzer = if (graphPath == "") null else KotlinGraphAnalyzer(formatter.indexSearcher, db!!)



    /**
     * Function: queryCombined
     * Description: Score with weighted combination of BM25, Jaccard string similarity, LM_Dirichlet, and second/third
     *              section headers (trained using RankLib).
     */
    private fun queryCombined() {
//        val weights = listOf(0.3106317698753524,-0.025891305471130843,
//                0.34751201103557083, -0.2358113441529167, -0.08015356975284649)

        val weights = listOf(0.27569142, 0.17437123, 0.20848581, 0.34145153, 0.0, 0.0, 0.0)
//        val weights = listOf(0.61881053, 0.0, 0.0, 0.38118944)
//        val weights = listOf(0.5327952, 0.0, 0.18299106, 0.28421375)

        val gramIndexSearcher = getIndexSearcher(gramPath)
        val hLinker = HyperlinkIndexer(hyperlinkPath)
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE, weight = weights[0])

        formatter.addFeature({ query, tops, indexSearcher ->
            featStringSimilarityComponent(query, tops, indexSearcher)
        }, normType = NormType.ZSCORE, weight = weights[1])

        formatter.addFeature({query, tops, indexSearcher ->
            featUseLucSim(query, tops, indexSearcher, LMDirichletSimilarity())
        }, normType = NormType.ZSCORE, weight = weights[2])

        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE, weight = weights[3])

//        formatter.addFeature({ query, tops, indexSearcher ->
//            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)
//
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 2.0)
//        }, normType = NormType.ZSCORE)
//
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
//        }, normType = NormType.ZSCORE)
    }
    private fun queryAverageAbstractScore() {
        val weights = listOf(0.8974292632642049, -0.10257073673579509)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
        }, normType = NormType.ZSCORE, weight = weights[1])
    }

    private fun queryHyperlinkLikelihood() {
        val weights = listOf(0.8821131679, -0.11788632077)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val hLinker = HyperlinkIndexer(hyperlinkPath)
        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)},
                normType = NormType.ZSCORE, weight = weights[1])
    }

    private fun queryAbstractSim() {
        formatter.addBM25(weight = 0.86553535, normType = NormType.ZSCORE)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 1.0)
        }, normType = NormType.ZSCORE, weight = 0.1344646)
    }



    private fun querySDMComponents() {
        formatter.addBM25(normType = NormType.ZSCORE, weight = 0.381845239)
        val gramSearcher = getIndexSearcher(gramPath)
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

    private fun queryAbstractSDM() {
        val weights = listOf(0.86553535, 0.134464695)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 2.0)
        }, normType = NormType.ZSCORE, weight = weights[1])
    }

    private fun querySDM() {
        val weights = listOf(0.14059688887081667, 0.8594031111291832)
//        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE, weight = weights[0])
        val gramSearcher = getIndexSearcher(gramPath)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val hGram = KotlinGramAnalyzer(gramSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE, weight = weights[1])
    }

    private fun querySDMExpansion() {
        val weights = listOf(0.9691977861102452, -0.030802213889754796)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        formatter.addFeature({ query, tops, indexSearcher ->
            featSDMWithEntityQueryExpansion(query, tops, indexSearcher,
                    hGram, abstractAnalyzer.indexSearcher, 4.0)},
                normType = NormType.ZSCORE, weight = weights[1])
    }

    private fun querySectionComponent() {
        val weights = listOf(0.0, 1.0)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights[0])
        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE, weight = weights[1])
    }

    private fun querySDMSection() {
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
//        val weights = listOf(0.18040763371108623, 0.053702972763138165, 0.3145376765137826, 0.45135171701199295)
        val weights = listOf(0.08047025663846726, 0.030239885393043505, 0.15642380129849698, 0.45881012321282,
        0.1370279667285861, 0.1370279667285861
        )

        val bindSDM = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }

        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, bindSDM, weights)
        }, normType = NormType.ZSCORE)
    }

    // Runs associated query method
    fun runRanklibQuery(method: String, out: String) {
        when (method) {
            "average_abstract" -> queryAverageAbstractScore()
            "hyperlink" -> queryHyperlinkLikelihood()
            "sdm_components" -> querySDMComponents()
            "abstract_sim" -> queryAbstractSim()
            "section_component" -> querySectionComponent()
            "abstract_sdm" -> queryAbstractSDM()
            "sdm" -> querySDM()
            "sdm_section" -> querySDMSection()
            "sdm_expansion" -> querySDMExpansion()
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
        val gramIndexSearcher = getIndexSearcher(gramPath)
        val hLinker = HyperlinkIndexer(hyperlinkPath)
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featStringSimilarityComponent(query, tops, indexSearcher)
        }, normType = NormType.ZSCORE)

        formatter.addFeature({query, tops, indexSearcher ->
            featUseLucSim(query, tops, indexSearcher, LMDirichletSimilarity())
        }, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 2.0)
        }, normType = NormType.ZSCORE)

        formatter.addFeature({ query, tops, indexSearcher ->
            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
        }, normType = NormType.ZSCORE)

    }


    private fun trainDirichletAlpha() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val gramIndexSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        listOf(2, 8, 16, 32, 64, 128).forEach { alpha ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSDM(query, tops, indexSearcher, hGram, alpha.toDouble())
            }, normType = NormType.ZSCORE)
        }

    }

    private fun trainAbstractSDMAlpha() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val abstractSearcher = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractSearcher)
        listOf(2, 8, 16, 32, 64, 128, 256, 512, 1024).forEach { alpha ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, alpha.toDouble())
            }, normType = NormType.ZSCORE)
        }

    }

    private fun trainSDM() {
//        val weights = listOf(0.49827237108, 0.23021207089, 0.1280351944, 0.143480363604666)
//        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE, weight = weights[0])
        formatter.addBM25(normType = NormType.ZSCORE)
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDM(query, tops, indexSearcher, hGram, 4.0)
        }, normType = NormType.ZSCORE)
    }

    private fun trainSDMComponents() {
        val gramIndexSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
        val grams = listOf(GramStatType.TYPE_UNIGRAM, GramStatType.TYPE_BIGRAM, GramStatType.TYPE_BIGRAM_WINDOW)
        grams.forEach { gram ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSDM(query, tops, indexSearcher, hGram, 4.0, gramType = gram)
            }, normType = NormType.NONE)
        }
    }


    private fun trainAbstractSDM() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, 2.0)
        }, normType = NormType.ZSCORE)
    }

    private fun trainAbstractSDMComponents() {
        val grams = listOf(GramStatType.TYPE_UNIGRAM, GramStatType.TYPE_BIGRAM, GramStatType.TYPE_BIGRAM_WINDOW)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        grams.forEach { gram ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featAbstractSDM(query, tops, indexSearcher, abstractAnalyzer, gramType = gram, alpha = 2.0)
            }, normType = NormType.NONE)
        }
    }

    private fun trainSectionPath() {
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 0) }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 1) }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 2) }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSectionSplit(query, tops, indexSearcher, 3) }, normType = NormType.NONE)
    }

    private fun trainSectionComponent() {
        formatter.addBM25(normType = NormType.ZSCORE)
        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE)
    }

    private fun trainSimilarityComponents() {
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, Jaccard() )
        }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, JaroWinkler() )
        }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, NormalizedLevenshtein() )
        }, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAddStringDistanceFunction(query, tops, indexSearcher, SorensenDice() )
        }, normType = NormType.NONE)
    }

    private fun trainSimilaritySection() {
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, ::featStringSimilarityComponent,
                    secWeights = listOf(1.0, 0.0, 0.0, 0.0))}, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, ::featStringSimilarityComponent,
                    secWeights = listOf(0.0, 1.0, 0.0, 0.0))}, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, ::featStringSimilarityComponent,
                    secWeights = listOf(0.0, 0.0, 1.0, 0.0))}, normType = NormType.NONE)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, ::featStringSimilarityComponent,
                    secWeights = listOf(0.0, 0.0, 0.0, 1.0))}, normType = NormType.NONE)

//        val weights = listOf(0.13506566, -0.49940691, 0.21757824, 0.14794917259)
    }

    private fun trainAverageAbstractScore() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featAverageAbstractScoreByQueryRelevance(query, tops, indexSearcher, abstractAnalyzer)
        }, normType = NormType.ZSCORE)

//        formatter.addFeature(::featSectionComponent, normType = NormType.ZSCORE)
//        val gramIndexSearcher = getIndexSearcher(gramPath)
//        val hLinker = HyperlinkIndexer("entity_mentions.db")
//        val hGram = KotlinGramAnalyzer(gramIndexSearcher)
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featSDM(query, tops, indexSearcher, hGram, 4.0)
//        }, normType = NormType.ZSCORE)

//        formatter.addFeature(::featStringSimilarityComponent, normType = NormType.ZSCORE)
    }

    private fun trainHyperlinkLikelihood() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val hLinker = HyperlinkIndexer(hyperlinkPath)
        formatter.addFeature({ query, tops, indexSearcher ->
            featLikehoodOfQueryGivenEntityMention(query, tops, indexSearcher, hLinker)}, normType = NormType.ZSCORE)
    }

    private fun trainSDMExpansion() {
        formatter.addBM25(normType = NormType.ZSCORE)
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)
        formatter.addFeature({ query, tops, indexSearcher ->
            featSDMWithEntityQueryExpansion(query, tops, indexSearcher, hGram, abstractAnalyzer.indexSearcher, 4.0)
        }, normType = NormType.ZSCORE)
    }

    private fun trainSDMEntityQueryExpansionComponents() {
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)
        val abstractIndexer = getIndexSearcher(abstractPath)
        val abstractAnalyzer = KotlinAbstractAnalyzer(abstractIndexer)

        val grams = listOf(GramStatType.TYPE_UNIGRAM, GramStatType.TYPE_BIGRAM, GramStatType.TYPE_BIGRAM_WINDOW)
        grams.forEach { gram ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSDMWithEntityQueryExpansion(query, tops, indexSearcher,
                        hGram, abstractAnalyzer.indexSearcher, 4.0, gramType = gram)
            }, normType = NormType.NONE)
        }
    }

    private fun trainSectionTFIDF() {

        val tifdSearcher = getIndexSearcher(indexPath)
        // Huh... Bindu's class shares the same name as the TFIDFSimilarity from Lucene... that's not good.
        val tifd = TFIDFSimilarity(100, tifdSearcher)
        val bindTIFD = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featTFIFDAverage(query, tops, indexSearcher, tifd)
        }

        val makeWeights = { pos: Int ->
            arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0).apply { this[pos] = 1.0 }
        }

        (0 until 6).forEach { sectionWeight ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSplitSim(query, tops, indexSearcher, bindTIFD, secWeights = makeWeights(sectionWeight))},
                    normType = NormType.NONE)
        }



    }

    private fun trainSectionSDM() {
        val gramSearcher = getIndexSearcher(gramPath)
        val hGram = KotlinGramAnalyzer(gramSearcher)

        val bindSDM = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
                featSDM(query, tops, indexSearcher, hGram, 4.0) }

        val makeWeights = { pos: Int ->
            arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0).apply { this[pos] = 1.0 }
        }

        (0 until 6).forEach { sectionWeight ->
            formatter.addFeature({ query, tops, indexSearcher ->
                featSplitSim(query, tops, indexSearcher, bindSDM, secWeights = makeWeights(sectionWeight))},
                    normType = NormType.NONE)
        }

//        formatter.addFeature({ query, tops, indexSearcher ->
//            featSplitSim(query, tops, indexSearcher, bindSDM,
//                    secWeights = listOf(1.0, 0.0, 0.0, 0.0))},
//                    normType = NormType.NONE)
//
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featSplitSim(query, tops, indexSearcher, bindSDM,
//                    secWeights = listOf(0.0, 1.0, 0.0, 0.0))},
//                    normType = NormType.NONE)
//
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featSplitSim(query, tops, indexSearcher, bindSDM,
//                    secWeights = listOf(0.0, 0.0, 1.0, 0.0))},
//                    normType = NormType.NONE)
//
//        formatter.addFeature({ query, tops, indexSearcher ->
//            featSplitSim(query, tops, indexSearcher, bindSDM,
//                    secWeights = listOf(0.0, 0.0, 0.0, 1.0))},
//                    normType = NormType.NONE)

//        val weights = listOf(0.13506566, -0.49940691, 0.21757824, 0.14794917259)
    }



    /**
     * Function: train
     * Description: Add features associated with training method and then writes scored features to a RankLib compatible
     *              file for later use in training weights.
     */
    fun train(method: String, out: String) {
        when (method) {
            "hyperlink" -> trainHyperlinkLikelihood()
            "abstract_sdm" -> trainAbstractSDM()
            "abstract_sdm_components" -> trainAbstractSDMComponents()
            "abstract_alpha" -> trainAbstractSDMAlpha()
            "average_abstract" -> trainAverageAbstractScore()
            "sdm" -> trainSDM()
            "sdm_alpha" -> trainDirichletAlpha()
            "sdm_components" -> trainSDMComponents()
            "section_path" -> trainSectionPath()
            "tfidf_section" -> trainSectionTFIDF()
            "section_component" -> trainSectionComponent()
            "string_similarities" -> trainSimilarityComponents()
            "similarity_section" -> trainSimilaritySection()
            "sdm_expansion_components" -> trainSDMEntityQueryExpansionComponents()
            "sdm_expansion" -> trainSDMExpansion()
            "sdm_section" -> trainSectionSDM()
            "combined" -> trainCombined()
            else -> println("Unknown method!")
        }
        formatter.writeToRankLibFile(out)
    }
}
