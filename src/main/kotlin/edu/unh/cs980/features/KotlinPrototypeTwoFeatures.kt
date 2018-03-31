package edu.unh.cs980.features

import edu.unh.cs980.CONTENT
import edu.unh.cs980.PID
import edu.unh.cs980.context.HyperlinkIndexer
import edu.unh.cs980.defaultWhenNotFinite
import edu.unh.cs980.identity
import edu.unh.cs980.language.GramStatType
import edu.unh.cs980.language.KotlinAbstractAnalyzer
import edu.unh.cs980.language.KotlinGramAnalyzer
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.WordEmbedding.TFIDFSimilarity
import edu.unh.cs980.variations.Query_RM_QE_variation
import info.debatty.java.stringsimilarity.Jaccard
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import info.debatty.java.stringsimilarity.SorensenDice
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import kotlin.math.ln


/**
 * Func: featSplitSim
 * Desc: Given a feature that scores according to query and TopDocs, reweights score based
 *       on section.
 */
fun featSplitSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                 func: (String, TopDocs, IndexSearcher) -> List<Double>,
                 secWeights: List<Double> = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)): List<Double> {

    val sections = query.split("/")
        .map { section -> AnalyzerFunctions
            .createTokenList(section, useFiltering = true)
            .joinToString(" ")}
        .toList()

    val results = secWeights.zip(sections)
        .filter { (weight, section) -> weight != 0.0 }
        .map { (weight, section) ->
                    func(section, tops, indexSearcher).map { result -> result * weight}}

    val finalList = try{results.reduce { acc, list ->  acc.zip(list).map { (l1, l2) -> l1 + l2 }} }
                    catch(e: UnsupportedOperationException) { (0 until tops.scoreDocs.size).map { 0.0 }}
    return finalList
}

/**
 * Func: featSectionComponent
 * Desc: Reweights BM25 score of sections in query and returns a score that is a sum of these reweighted scores.
 */
fun featSectionComponent(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
    val termQueries = query.split("/")
        .map { section -> AnalyzerFunctions
            .createTokenList(section, useFiltering = true)
            .joinToString(" ")}
        .map { section -> AnalyzerFunctions.createQuery(section)}
        .toList()

    val weights = listOf(0.200983, 0.099785, 0.223777, 0.4754529531)
    val validQueries = weights.zip(termQueries)

    return tops.scoreDocs
        .map { scoreDoc ->
            validQueries.map { (weight, boolQuery) ->
                indexSearcher.explain(boolQuery, scoreDoc.doc).value.toDouble() * weight }
            .sum()
        }
}

/**
 * Func: featStringSimilarityComponent
 * Desc: Combines Jaccard Similarity, JaroWinkler Similarity, NormalizedLevenshstein, and SorensenDice coefficient
 *       by taking a weighted combined of these scores. The scores evaluate the similarity of the query's terms to
 *       that of the spotlight entities in each of the documents.
 */
fun featStringSimilarityComponent(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
    val weights = listOf(0.540756, 0.0, 0.0605, -0.3986067)
    val sims = listOf<StringDistance>(Jaccard(), JaroWinkler(), NormalizedLevenshtein(), SorensenDice())
    val simTrials = weights.zip(sims)

    val simResults = simTrials.map { (weight, sim) ->
        featAddStringDistanceFunction(query, tops, indexSearcher, sim)
            .map { score -> score * weight }
    }

    return simResults.reduce { acc, scores -> acc.zip(scores).map { (l1, l2) -> l1 + l2 } }
}


/**
 * Func: featLikelihood  of Query Given Entity Mention
 * Desc: The query is tokenized, and the likelihood of seeing an entity is based on how often the query token
 *       is the anchor text for a given entity. The final score is the sum of these likelihoods.
 */
fun featLikehoodOfQueryGivenEntityMention(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                          hIndexer: HyperlinkIndexer): List<Double> {
    val queryTokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight").toList()
        queryTokens
            .map    { queryToken ->
                         entities
                            .map { entity ->
                                val like = hIndexer.getMentionLikelihood(queryToken.toLowerCase(), entity.toLowerCase())
                                ln(like).defaultWhenNotFinite(0.0)
                            }
                            .sum()
                    }.average().defaultWhenNotFinite(0.0)
    }.toList()
}


/**
 * Func: featSDM
 * Desc: My best attempt at an SDM model (using Dirichlet smoothing). The individual components have
 *       already been weighted (see training examples) and the final score is a weighted combination
 *       of unigram, bigram, and windowed bigram.
 */
fun featSDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
            gramAnalyzer: KotlinGramAnalyzer, alpha: Double,
            gramType: GramStatType? = null): List<Double> {
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")
    val queryCorpus = gramAnalyzer.getCorpusStatContainer(cleanQuery)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val text = doc.get(CONTENT)

        // Generate a language model for the given document's text
        val docStat = gramAnalyzer.getLanguageStatContainer(text)
        val queryLikelihood = docStat.getLikelihoodGivenQuery(queryCorpus, alpha)
        val v1 = queryLikelihood.unigramLikelihood
        val v2 = queryLikelihood.bigramLikelihood
        val v3 = queryLikelihood.bigramWindowLikelihood

        // If gram type is given, only return the score of a particular -gram method.
        // Otherwise, used the weights that were learned and combine all three types into a score.
        val weights = listOf(0.9285990421606605, 0.070308081629, -0.0010928762)
        when (gramType) {
            GramStatType.TYPE_UNIGRAM -> v1
            GramStatType.TYPE_BIGRAM -> v2
            GramStatType.TYPE_BIGRAM_WINDOW -> v3
            else -> v1 * weights[0] + v2 * weights[1] + v3 * weights[2]
        }
    }
}

/**
 * Func: featSDMWithEntityQueryExpansion
 * Desc: The original query string is tokenized, and the token's terms are expanded according to Kevin's
 *       entity expansion model (top n entities are retrieved with BM25, abstracts are annotated with Spotlight,
 *       annotations are extracted and returned as a list of expanded terms)
 *
 *       Expanded terms are appended to each query token and the final list is joined together as a single query.
 *       This query is then used with the SDM method.
 */
fun featSDMWithEntityQueryExpansion(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
            gramAnalyzer: KotlinGramAnalyzer, abstractSearcher: IndexSearcher, alpha: Double,
            gramType: GramStatType? = null): List<Double> {
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val expandedQueryResults = tokens
        .map { token ->
            token to Query_RM_QE_variation.getExpandedEntitiesFromPageQuery(token, 1, abstractSearcher) }
        .map { (token, expandedResults) -> token + " " + expandedResults.joinToString(" ")  }
        .map { expandedQuery -> featSDM(expandedQuery, tops, indexSearcher, gramAnalyzer, alpha, gramType) }

    return expandedQueryResults
        .reduce { acc, list -> acc.zip(list).map { (l1, l2) -> l1 + l2 } }
        .map { finalResult -> finalResult / expandedQueryResults.size }

}


/**
 * Func: featTIFDAverage
 * Desc: Uses Bindu's TIFD to get average TIFD score for tokens in query.
 */
fun featTFIFDAverage(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                    tifd: TFIDFSimilarity): List<Double> {
    val termQueries = AnalyzerFunctions.createTokenList(query, useFiltering = true)
        .map { token -> TermQuery(Term(CONTENT, token)) }

    // Call Bindu's TIFD to get one list of doubles per term in query string
    val results: List<List<Double>> = tifd.getQueryScore(termQueries, tops)

    return results
        .reduce { acc, list ->
                    acc.zip(list).map { (l1, l2) -> l1 + l2 } }
        .map { reducedScore -> reducedScore / results.size }
}



/**
 * Func: featAbstractSDM
 * Desc: An SDM that uses the abstracts of Wikipedia pages as the language model.
 *       The goal of this method is to find the likelihood of entities given a document
 *       (simply the frequency they are mentioned in the document) and then get the likelihood of the
 *       query given the entity's abstract text.
 */
fun featAbstractSDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                   abstractAnalyzer: KotlinAbstractAnalyzer,
                   alpha: Double, gramType: GramStatType? = null): List<Double> {
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")

    // Only look at entities that might be relevant to our query. This is done by searching the abstract
    // index using BM25 and looking at only the top 20 entities. Each of these entities then has its
    // language model generated and the likelihood of the model given the query.
    val relevantEntities = abstractAnalyzer.getRelevantEntities(cleanQuery, alpha)
    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight")

        // Because the abstract page names don't always line up with Spotlight entities, try to match
        // them using string similarity.
        val rels = entities.mapNotNull { entity ->
            abstractAnalyzer.getMostSimilarRelevantEntity(entity.toLowerCase(), relevantEntities)
        }

        // Score is basically the average of all relevant entity models that a document contains.
        // Surprisingly, after training, the bigrams were favored the most (compared to the normal SDM above)
        val weights = listOf(0.0486185, 0.9318018089, 0.01957)
        val results = rels.map { (_, relEntity) ->
            val v1 = relEntity.queryLikelihood.unigramLikelihood
            val v2 = relEntity.queryLikelihood.bigramLikelihood
            val v3 = relEntity.queryLikelihood.bigramWindowLikelihood
            when (gramType) {
                GramStatType.TYPE_UNIGRAM       -> v1
                GramStatType.TYPE_BIGRAM        -> v2
                GramStatType.TYPE_BIGRAM_WINDOW -> v3
                else                            -> weights[0] * v1 + weights[1] * v2 + weights[2] * v3
            }
        }

        results.average().defaultWhenNotFinite(0.0)
    }
}


/**
 * Func: featAverageAbstractScoreByQueryRelevance
 * Desc: Using BM25 to score relevant entities, the scores of documents are expressed as the average
 *       BM25 score of the relevant entities that it contains.
 */
fun featAverageAbstractScoreByQueryRelevance(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                   abstractAnalyzer: KotlinAbstractAnalyzer): List<Double> {
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")
    val relevantEntities = abstractAnalyzer.getRelevantEntities(cleanQuery)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight")
        val rels = entities.mapNotNull { entity ->
            abstractAnalyzer.getMostSimilarRelevantEntity(entity.toLowerCase(), relevantEntities)
        }

        rels.map { (sim, relEntity) -> sim * relEntity.score }
            .average()
            .defaultWhenNotFinite(0.0)
    }
}

