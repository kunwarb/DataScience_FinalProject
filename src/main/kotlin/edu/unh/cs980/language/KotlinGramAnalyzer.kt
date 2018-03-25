@file:JvmName("KotGramAnalyzer")
package edu.unh.cs980.language

import com.google.common.collect.ImmutableMap
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.identity
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import java.io.StringReader
import kotlin.coroutines.experimental.buildSequence

enum class GramStatType  { TYPE_UNIGRAM, TYPE_BIGRAM, TYPE_BIGRAM_WINDOW }


data class LanguageStats(val docTermCounts: Map<String, Int>,
                         val docTermFreqs: Map<String, Double>,
                         val corpusTermFreqs: Map<String, Double> = mapOf()) {

//    fun smooth(alpha: Double, stats: LanguageStats): Map<String, Double> {
//        return docTermFreqs.map { (k,v) -> k to v * alpha + stats.corpusTermFreqs[k]!! * (alpha - 1.0) }
//            .toMap()
//    }

    fun smooth(alpha: Double): Map<String, Double> {
        return docTermFreqs.map { (k,v) -> k to v * alpha + corpusTermFreqs[k]!! * (alpha - 1.0) }
            .toMap()
    }
}

class KotlinGramAnalyzer(gramLoc: String) {
    val indexSearcher = getIndexSearcher(gramLoc)
    val analyzer = EnglishAnalyzer()

    fun getCorpusGram(gram: String, gramType: String): Long =
            indexSearcher.indexReader.totalTermFreq(Term(gramType, gram))


    fun createTokenSequence(query: String): Sequence<String> {
        val tokenStream = analyzer.tokenStream("text", StringReader(query)).apply { reset() }

        return buildSequence<String> {
            while (tokenStream.incrementToken()) {
                yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
            }
            tokenStream.end()
            tokenStream.close()
        }
    }

    fun getCorpusScores(keys: List<String>, type: GramStatType): Map<String, Double> {
        val field = when(type) {
            GramStatType.TYPE_UNIGRAM -> "unigram"
            GramStatType.TYPE_BIGRAM -> "bigrams"
            GramStatType.TYPE_BIGRAM_WINDOW -> "bigram_windows"
        }

        val totalCorpusFreq = indexSearcher.indexReader
            .getSumTotalTermFreq(field)
            .toDouble()

        val corpusFreqs = keys
            .map { key -> Pair(key, getCorpusGram(key, field) / totalCorpusFreq) }
            .toMap()

        return corpusFreqs
    }

    fun getBigramStats(text: String): LanguageStats {
        val terms = createTokenSequence(text).toList()
        val docBigramCounts = terms.windowed(2, 1)
            .map { window -> window.joinToString(separator = "") }
            .groupingBy(::identity)
            .eachCount()


        val totalDocBigrams = docBigramCounts.values
            .sum()
            .toDouble()


        val docBigramFreqs = docBigramCounts
            .mapValues { (bigram, count) -> count / totalDocBigrams }
            .toMap()

        val corpusBigramFreqs = getCorpusScores(docBigramCounts.keys.toList(), GramStatType.TYPE_BIGRAM)

//        val totalCorpusBigrams = indexSearcher.indexReader
//            .getSumTotalTermFreq("bigrams")
//            .toDouble()
//
//        val corpusBigramFreqs = docBigramCounts
//            .map { (gram, count) -> Pair(gram, getCorpusGram(gram, "bigrams") / totalCorpusBigrams) }
//            .toMap()

        return LanguageStats(docBigramCounts, docBigramFreqs, corpusBigramFreqs)
    }

    fun getWindowedBigramStats(text: String, add: Boolean = false): LanguageStats {
        val terms = createTokenSequence(text).toList()
        val docBigramWindowCounts = terms
            .windowed(8, 1, true)
            .flatMap { window ->
                            val firstTerm = window[0]
                            window
                                .slice(1  until window.size)
                                .map { secondTerm -> firstTerm + secondTerm } }
            .groupingBy(::identity)
            .eachCount()
            .toMap()


        val docTotalBigrams = docBigramWindowCounts.values
            .sum()
            .toDouble()

        val docBigramWindowFreqs = docBigramWindowCounts
            .mapValues { (bigram, count) -> count / docTotalBigrams }
            .toMap()


        val corpusBigramWindowFreqs = getCorpusScores(docBigramWindowFreqs.keys.toList(), GramStatType.TYPE_BIGRAM_WINDOW)
//        val totalCorpusBigrams = indexSearcher.indexReader
//            .getSumTotalTermFreq("bigram_windows")
//            .toDouble()
//
//
//        val corpusBigramWindowFreqs = docBigramWindowCounts
//            .mapValues { (bigram, count) -> getCorpusGram(bigram, "bigram_windows") / totalCorpusBigrams }
//            .toMap()


        return LanguageStats(docBigramWindowCounts, docBigramWindowFreqs, corpusBigramWindowFreqs)

    }

    fun getUnigramStats(text: String): LanguageStats {
        val terms = createTokenSequence(text).toList()
        val totalTerms = terms.size.toDouble()

        val docTermCounts = terms
            .groupingBy(::identity)
            .eachCount()
            .toMap()

        val docTermFreqs = docTermCounts
            .map { (term,count) -> Pair(term, count / totalTerms)}
            .toMap()

        val corpusTermFreqs = getCorpusScores(docTermCounts.keys.toList(), GramStatType.TYPE_BIGRAM_WINDOW)

//        val totalCorpusTerms = indexSearcher.indexReader
//            .getSumTotalTermFreq("unigram")
//            .toDouble()
//
//        val corpusTermFreqs = docTermCounts
//            .map { (gram, count) -> Pair(gram, getCorpusGram(gram, "unigram") / totalCorpusTerms) }
//            .toMap()

        return LanguageStats(docTermCounts, docTermFreqs, corpusTermFreqs)
    }

    fun getStats(text: String, statType: GramStatType): LanguageStats {
        return when (statType) {
            GramStatType.TYPE_UNIGRAM -> getUnigramStats(text)
            GramStatType.TYPE_BIGRAM -> getBigramStats(text)
            GramStatType.TYPE_BIGRAM_WINDOW -> getWindowedBigramStats(text)
        }
    }

    fun runTest() {
        val testString = "This will be a test string to see if I can get gram statistics"
        val unigrams = getStats(testString, GramStatType.TYPE_UNIGRAM)
        val bigrams = getStats(testString, GramStatType.TYPE_BIGRAM)
        val bigram_windows = getStats(testString, GramStatType.TYPE_BIGRAM_WINDOW)

        println("Unigrams\n${unigrams.docTermFreqs}\n${unigrams.corpusTermFreqs}\n\n")
        println("Bigams\n${bigrams.docTermFreqs}\n${bigrams.corpusTermFreqs}\n\n")
        println("Bigam Windows\n${bigram_windows.docTermFreqs}\n${bigram_windows.corpusTermFreqs}\n\n")
    }



}

