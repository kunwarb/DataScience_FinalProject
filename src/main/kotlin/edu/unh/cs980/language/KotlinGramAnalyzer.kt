@file:JvmName("KotGramAnalyzer")
package edu.unh.cs980.language

import com.google.common.collect.ImmutableMap
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.identity
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import java.io.StringReader
import java.lang.Math.log
import kotlin.coroutines.experimental.buildSequence

enum class GramStatType  { TYPE_UNIGRAM, TYPE_BIGRAM, TYPE_BIGRAM_WINDOW }


data class LikelihoodStat(val likelihoodMap: Map<String, Double>, val type: GramStatType) {
    fun likelihood() =
            likelihoodMap.values
                .sum()
//                .fold(1.0, {acc, prob -> acc * prob})
}
data class LikelihoodContainer(val unigramLikelihood: Double,
                               val bigramLikelihood: Double,
                               val bigramWindowLikelihood: Double)
//data class LikelihoodContainer(val unigramLikelihood: LikelihoodStat,
//                               val bigramLikelihood: LikelihoodStat,
//                               val bigramWindowLikelihood: LikelihoodStat)

data class LanguageStat(val docTermCounts: Map<String, Int>,
                         val docTermFreqs: Map<String, Double>,
                        val type: GramStatType)


data class LanguageStatContainer(
        val unigramStat: LanguageStat,
        val bigramStat: LanguageStat,
        val bigramWindowStat: LanguageStat) {

    private fun getLikelihood(queryStat: CorpusStat, alpha: Double): Double {
        val stat = when(queryStat.type) {
            GramStatType.TYPE_UNIGRAM -> unigramStat
            GramStatType.TYPE_BIGRAM -> bigramStat
            GramStatType.TYPE_BIGRAM_WINDOW -> bigramWindowStat
        }

//        val likelihood: Map<String, Double> = queryStat.corpusFrequency
//            .mapValues { (term, freq) -> alpha * (stat.docTermFreqs[term] ?: 0.0) + (1 - alpha) * freq }
        val docLength = stat.docTermCounts.values.sum().toDouble()
        val docSmooth = docLength / (docLength + alpha)
        val corpusSmooth = alpha / (alpha + docLength)

//        val likelihood =
//            queryStat.corpusFrequency
//                .mapValues { (term, freq) ->
//                    docSmooth * (stat.docTermFreqs[term] ?: 0.0) + corpusSmooth * freq }
        val likelihood =
                queryStat.corpusFrequency
                    .map { (term, freq) ->
//                        val pred = docSmooth * (stat.docTermFreqs[term] ?: 0.0) + corpusSmooth * freq
                        val smoothCounts = (stat.docTermCounts[term] ?: 0) + freq * alpha
                        println(freq)
                        log(smoothCounts / (docLength + alpha))
                    }
                    .sum()

        return likelihood
    }

    fun getLikelihoodGivenQuery(query: CorpusStatContainer, alpha: Double = 1.0): LikelihoodContainer =
            LikelihoodContainer(
                    unigramLikelihood = getLikelihood(query.unigramStat, alpha),
                    bigramLikelihood = getLikelihood(query.bigramStat, alpha),
                    bigramWindowLikelihood = getLikelihood(query.bigramWindowStat, alpha)
            )
}

//data class LanguageStats(val docTermCounts: Map<String, Int>,
//                         val docTermFreqs: Map<String, Double>)
//                         val corpusTermFreqs: Map<String, Double> = mapOf()) {

//    fun smooth(alpha: Double, stats: LanguageStats): Map<String, Double> {
//        return docTermFreqs.map { (k,v) -> k to v * alpha + stats.corpusTermFreqs[k]!! * (alpha - 1.0) }
//            .toMap()
//    }

//    fun smooth(alpha: Double): Map<String, Double> {
//        return docTermFreqs.map { (k,v) -> k to v * alpha + corpusTermFreqs[k]!! * (alpha - 1.0) }
//            .toMap()
//    }

data class CorpusStat(val corpusFrequency: Map<String, Double>, val corpusDoc: LanguageStat) {
    val type: GramStatType
            get() = corpusDoc.type
}
data class CorpusStatContainer(
        val unigramStat: CorpusStat,
        val bigramStat: CorpusStat,
        val bigramWindowStat: CorpusStat
)

class KotlinGramAnalyzer(val indexSearcher: IndexSearcher) {
    constructor(indexLoc: String) : this(getIndexSearcher(indexLoc))
    val analyzer = StandardAnalyzer()

    private fun getCorpusGram(gram: String, gramType: String): Long =
            indexSearcher.indexReader.totalTermFreq(Term(gramType, gram))


    private fun createTokenSequence(query: String): Sequence<String> {
        val tokenStream = analyzer.tokenStream("text", StringReader(query)).apply { reset() }

        return buildSequence<String> {
            while (tokenStream.incrementToken()) {
                yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
            }
            tokenStream.end()
            tokenStream.close()
        }
    }


    fun getLanguageStatContainer(text: String): LanguageStatContainer =
        LanguageStatContainer(
                unigramStat = getStats(text, GramStatType.TYPE_UNIGRAM),
                bigramStat = getStats(text, GramStatType.TYPE_BIGRAM),
                bigramWindowStat = getStats(text, GramStatType.TYPE_BIGRAM_WINDOW)
        )


    fun getCorpusStatContainer(text: String): CorpusStatContainer =
            CorpusStatContainer(
                    unigramStat = getCorpusStat(text, GramStatType.TYPE_UNIGRAM),
                    bigramStat = getCorpusStat(text, GramStatType.TYPE_BIGRAM),
                    bigramWindowStat = getCorpusStat(text, GramStatType.TYPE_BIGRAM_WINDOW)
            )

    private fun getCorpusStat(text: String, type: GramStatType): CorpusStat {
        val languageStats = getStats(text, type)

        val field = when(type) {
            GramStatType.TYPE_UNIGRAM -> "unigram"
            GramStatType.TYPE_BIGRAM -> "bigrams"
            GramStatType.TYPE_BIGRAM_WINDOW -> "bigram_windows"
        }

        val totalCorpusFreq = indexSearcher.indexReader
            .getSumTotalTermFreq(field)
            .toDouble()

        val corpusFreqs = languageStats.docTermCounts.keys
            .map { key -> Pair(key, getCorpusGram(key, field) / totalCorpusFreq) }
            .toMap()

        return CorpusStat(corpusFrequency = corpusFreqs, corpusDoc = languageStats)
    }


    private fun createLanguateStats(counts: Map<String, Int>, type: GramStatType): LanguageStat {
        val totalCount = counts.values
            .sum()
            .toDouble()

        val freqs = counts
            .mapValues { (gram, count) -> count / totalCount }
            .toMap()

        return LanguageStat(counts, freqs, type)
    }

    private fun getBigramCounts(text: String): Map<String, Int> {
        val terms = createTokenSequence(text).toList()
        val docBigramCounts = terms.windowed(2, 1)
            .map { window -> window.joinToString(separator = "") }
            .groupingBy(::identity)
            .eachCount()

        return docBigramCounts


//        val totalDocBigrams = docBigramCounts.values
//            .sum()
//            .toDouble()
//
//
//        val docBigramFreqs = docBigramCounts
//            .mapValues { (bigram, count) -> count / totalDocBigrams }
//            .toMap()

//        val corpusBigramFreqs = getCorpusScores(docBigramCounts.keys.toList(), GramStatType.TYPE_BIGRAM)

//        val totalCorpusBigrams = indexSearcher.indexReader
//            .getSumTotalTermFreq("bigrams")
//            .toDouble()
//
//        val corpusBigramFreqs = docBigramCounts
//            .map { (gram, count) -> Pair(gram, getCorpusGram(gram, "bigrams") / totalCorpusBigrams) }
//            .toMap()

//        return LanguageStats(docBigramCounts, docBigramFreqs)
//        return LanguageStats(docBigramCounts, docBigramFreqs, corpusBigramFreqs)
    }

    private fun getWindowedBigramCounts(text: String): Map<String, Int> {
        val terms = createTokenSequence(text).toList()
        val docBigramWindowCounts = terms
            .windowed(8, 1, true)
            .flatMap { window ->
                            val firstTerm = window[0]
                            window
                                .slice(1  until window.size)
                                .flatMap { secondTerm -> listOf(firstTerm + secondTerm, secondTerm + firstTerm) } }
//                                .map { secondTerm -> firstTerm + secondTerm } }
            .groupingBy(::identity)
            .eachCount()
            .toMap()
        return docBigramWindowCounts


//        val docTotalBigrams = docBigramWindowCounts.values
//            .sum()
//            .toDouble()
//
//        val docBigramWindowFreqs = docBigramWindowCounts
//            .mapValues { (bigram, count) -> count / docTotalBigrams }
//            .toMap()


//        val corpusBigramWindowFreqs = getCorpusScores(docBigramWindowFreqs.keys.toList(), GramStatType.TYPE_BIGRAM_WINDOW)
//        val totalCorpusBigrams = indexSearcher.indexReader
//            .getSumTotalTermFreq("bigram_windows")
//            .toDouble()
//
//
//        val corpusBigramWindowFreqs = docBigramWindowCounts
//            .mapValues { (bigram, count) -> getCorpusGram(bigram, "bigram_windows") / totalCorpusBigrams }
//            .toMap()


//        return LanguageStats(docBigramWindowCounts, docBigramWindowFreqs)
//        return LanguageStats(docBigramWindowCounts, docBigramWindowFreqs, corpusBigramWindowFreqs)

    }

    private fun getUnigramCounts(text: String): Map<String, Int> {
        val terms = createTokenSequence(text).toList()

        val docTermCounts = terms
            .groupingBy(::identity)
            .eachCount()
            .toMap()

        return docTermCounts
//
//        val docTermFreqs = docTermCounts
//            .map { (term,count) -> Pair(term, count / totalTerms)}
//            .toMap()

//        val corpusTermFreqs = getCorpusScores(docTermCounts.keys.toList(), GramStatType.TYPE_BIGRAM_WINDOW)

//        val totalCorpusTerms = indexSearcher.indexReader
//            .getSumTotalTermFreq("unigram")
//            .toDouble()
//
//        val corpusTermFreqs = docTermCounts
//            .map { (gram, count) -> Pair(gram, getCorpusGram(gram, "unigram") / totalCorpusTerms) }
//            .toMap()

//        return LanguageStats(docTermCounts, docTermFreqs)
//        return LanguageStats(docTermCounts, docTermFreqs, corpusTermFreqs)
    }

    private fun getStats(text: String, statType: GramStatType): LanguageStat {
        val counts = when (statType) {
            GramStatType.TYPE_UNIGRAM -> getUnigramCounts(text)
            GramStatType.TYPE_BIGRAM -> getBigramCounts(text)
            GramStatType.TYPE_BIGRAM_WINDOW -> getWindowedBigramCounts(text)
        }

        return createLanguateStats(counts, statType)
    }

    fun runTest() {
    }



}

