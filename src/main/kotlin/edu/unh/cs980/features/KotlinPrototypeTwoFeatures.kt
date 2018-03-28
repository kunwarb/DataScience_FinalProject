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
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.*
import info.debatty.java.stringsimilarity.Jaccard
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import info.debatty.java.stringsimilarity.SorensenDice
import info.debatty.java.stringsimilarity.interfaces.StringDistance
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.search.similarities.Similarity
import java.io.StringReader
import java.lang.Double.max
import kotlin.coroutines.experimental.buildSequence
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.log10
import kotlin.math.max

//private val analyzer = StandardAnalyzer()

fun featSplitSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                 func: (String, TopDocs, IndexSearcher) -> List<Double>,
                 secWeights: List<Double> = listOf(1.0, 1.0, 1.0, 1.0)): List<Double> {

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

fun featSectionComponent(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
    val termQueries = query.split("/")
        .map { section -> AnalyzerFunctions
            .createTokenList(section, useFiltering = true)
            .joinToString(" ")}
        .map { section -> AnalyzerFunctions.createQuery(section)}
        .toList()

    val weights = listOf(0.200983, 0.099785, 0.223777, 0.4754529531)
    val validQueries = weights.zip(termQueries)

//    if (termQueries.size < secIndex + 1) {
//        return (0 until tops.scoreDocs.size).map { 0.0 }
//    }

//    val boolQuery = termQueries[secIndex]

    return tops.scoreDocs
        .map { scoreDoc ->
            validQueries.map { (weight, boolQuery) ->
                indexSearcher.explain(boolQuery, scoreDoc.doc).value.toDouble() * weight }
            .sum()
        }
}

fun featStringSimilarityComponent(query: String, tops: TopDocs, indexSearcher: IndexSearcher): List<Double> {
    val weights = listOf(-0.52902088057, 0.009563578, -0.10055384, 0.3608616989)
    val sims = listOf<StringDistance>(Jaccard(), JaroWinkler(), NormalizedLevenshtein(), SorensenDice())
    val simTrials = weights.zip(sims)

    val simResults = simTrials.map { (weight, sim) ->
        featAddStringDistanceFunction(query, tops, indexSearcher, sim)
            .map { score -> score * weight }
    }

    return simResults.reduce { acc, scores -> acc.zip(scores).map { (l1, l2) -> l1 + l2 } }
}


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
                            .map { entity ->
                                val like = hIndexer.getMentionLikelihood(queryToken.toLowerCase(), entity.toLowerCase())
                                ln(like).defaultWhenNotFinite(0.0)
                            }
                            .sum()
                    }.average().defaultWhenNotFinite(0.0)
    }.toList()
}


fun featAbstractSim(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                                          abstractSearcher: IndexSearcher, sim: Similarity): List<Double> {

    val booleanQuery = AnalyzerFunctions.createQuery(query, useFiltering = true)
    val jac = Jaccard()

    abstractSearcher.setSimilarity(sim)
    val relevantEntities = abstractSearcher.search(booleanQuery, 300)

//    val entityScores = relevantEntities.scoreDocs.mapIndexed { index, scoreDoc ->
//        val doc = abstractSearcher.doc(scoreDoc.doc)
//        val entity = doc.get("name")
//        entity.toLowerCase().replace(" ", "_") to scoreDoc.score.toDouble()
//    }.toList()

    val entityScores = relevantEntities.scoreDocs.map { scoreDoc ->
        val doc = abstractSearcher.doc(scoreDoc.doc)
        val entity = doc.get("name")
        entity.toLowerCase().replace(" ", "_") to scoreDoc.score.toDouble()
    }.toList()

    val entityRanks = entityScores
        .sortedByDescending { (entity, score) -> score }
        .mapIndexed{ index, (entity, score) -> entity to (entityScores.size - index) / entityScores.size.toDouble() }

    val retrieveMostSimilarEntity = { candidateEntity: String ->
        entityRanks
            .map { (targetEntity, score) -> Triple(targetEntity, score, jac.distance(candidateEntity, targetEntity)) }
            .maxBy { (targetEntity, score, similarity) -> similarity }
    }

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight").toList()
        entities
            .mapNotNull { candidateEntity ->
                val (_, bestScore, bestSimilarity) = retrieveMostSimilarEntity(candidateEntity) ?:
                        Triple("", 0.0, 0.0)
                if (bestSimilarity < 0.9) 0.0 else bestScore}
            .let { result -> if (result.isEmpty()) 0.0 else result.average()  }
    }.toList()
}



fun featSDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
            gramAnalyzer: KotlinGramAnalyzer, alpha: Double,
            gramType: GramStatType? = null): List<Double> {
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")
    val queryCorpus = gramAnalyzer.getCorpusStatContainer(cleanQuery)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
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
            else -> v1 * weights[0] + v2 * weights[1] + v3 * weights[2]
        }
    }
}


fun featEntitySDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                  abstractAnalyzer: KotlinAbstractAnalyzer,
                  gramType: GramStatType? = null): List<Double> {
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")
    val weights = listOf(0.6830338975799, -0.31628449221678, 0.00006816)

    val queryCorpus = abstractAnalyzer.gramAnalyzer.getCorpusStatContainer(cleanQuery)
    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight")
            .groupingBy(::identity)
            .eachCount()
            .entries
            .sortedByDescending { entry -> entry.value }
            .take(3)
            .map { entry -> entry.key }

//        entities.mapNotNull { entity -> abstractAnalyzer.retrieveEntityDoc(entity) }
        entities
            .mapNotNull(abstractAnalyzer::retrieveEntityStatContainer)
//            .map { entityDoc -> entityDoc.get("text")  }
//            .map(abstractAnalyzer.gramAnalyzer::getLanguageStatContainer)
            .map { stat -> stat.getLikelihoodGivenQuery(queryCorpus, 4.0)}
            .map { queryLikelihood ->
                val v1 = queryLikelihood.unigramLikelihood
                val v2 = queryLikelihood.bigramLikelihood
                val v3 = queryLikelihood.bigramWindowLikelihood
//                println("$v1 $v2 $v3")
                when (gramType) {
                    GramStatType.TYPE_UNIGRAM -> v1
                    GramStatType.TYPE_BIGRAM -> v2
                    GramStatType.TYPE_BIGRAM_WINDOW -> v3
                    else -> weights[0] * v1 + weights[1] * v2 + weights[2] * v3
                }}
            .let { result -> if  (result.isEmpty()) 0.0 else result.average() }
    }
}


fun featAbstractSDM(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                   abstractAnalyzer: KotlinAbstractAnalyzer,
                   gramType: GramStatType? = null): List<Double> {
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")
    val weights = listOf(0.0486185, 0.9318018089, 0.01957)

//    val queryCorpus = abstractAnalyzer.gramAnalyzer.getCorpusStatContainer(cleanQuery)
    val relevantEntities = abstractAnalyzer.getRelevantEntities(cleanQuery)

    return tops.scoreDocs.map { scoreDoc ->
        val doc = indexSearcher.doc(scoreDoc.doc)
        val entities = doc.getValues("spotlight")
        val rels = entities.mapNotNull { entity ->
            abstractAnalyzer.getMostSimilarRelevantEntity(entity.toLowerCase(), relevantEntities)
        }

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


fun featAverageAbstractScoreByQueryRelevance(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                   abstractAnalyzer: KotlinAbstractAnalyzer): List<Double> {
    val tokens = AnalyzerFunctions.createTokenList(query, useFiltering = true)
    val cleanQuery = tokens.toList().joinToString(" ")

//    val queryCorpus = abstractAnalyzer.gramAnalyzer.getCorpusStatContainer(cleanQuery)
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

