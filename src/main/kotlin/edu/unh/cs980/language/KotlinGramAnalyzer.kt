@file:JvmName("KotGramAnalyzer")
package edu.unh.cs980.language

import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.identity
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.Term
import java.io.StringReader
import kotlin.coroutines.experimental.buildSequence

enum class GramStatType  { TYPE_UNIGRAM, TYPE_BIGRAM, TYPE_BIGRAM_WINDOW }

data class GramStatistics(val docTermCounts: Map<String, Int>,
                          val docTermFreqs: Map<String, Double>,
                          val corpusTermFreqs: Map<String, Double>)

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

    fun getBigramStats(text: String): GramStatistics {
        val terms = createTokenSequence(text).toList()
        val docBigramCounts = terms.windowed(2, 1)
            .map { window -> window.joinToString(separator = "") }
            .groupingBy(::identity)
            .eachCount()


        val totalDocBigrams = docBigramCounts.values
            .sum()
            .toDouble()

        val totalCorpusBigrams = indexSearcher.indexReader
            .getSumTotalTermFreq("bigrams")
            .toDouble()

        val docBigramFreqs = docBigramCounts
            .mapValues { (bigram, count) -> count / totalDocBigrams }
            .toMap()

        val corpusBigramFreqs = docBigramCounts
            .map { (gram, count) -> Pair(gram, getCorpusGram(gram, "bigrams") / totalCorpusBigrams) }
            .toMap()

        return GramStatistics(docBigramCounts, docBigramFreqs, corpusBigramFreqs)
    }

    fun getWindowedBigramStats(text: String): GramStatistics {
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

        val totalCorpusBigrams = indexSearcher.indexReader
            .getSumTotalTermFreq("bigram_windows")
            .toDouble()

        val corpusBigramWindowFreqs = docBigramWindowCounts
            .mapValues { (bigram, count) -> getCorpusGram(bigram, "bigram_windows") / totalCorpusBigrams }
            .toMap()

        return GramStatistics(docBigramWindowCounts, docBigramWindowFreqs, corpusBigramWindowFreqs)

    }

    fun getUnigramStats(text: String): GramStatistics {
        val terms = createTokenSequence(text).toList()
        val totalTerms = terms.size.toDouble()
        val totalCorpusTerms = indexSearcher.indexReader
            .getSumTotalTermFreq("unigram")
            .toDouble()

        val docTermCounts = terms
            .groupingBy(::identity)
            .eachCount()
            .toMap()

        val docTermFreqs = docTermCounts
            .map { (term,count) -> Pair(term, count / totalTerms)}
            .toMap()

        val corpusTermFreqs = docTermCounts
            .map { (gram, count) -> Pair(gram, getCorpusGram(gram, "unigram") / totalCorpusTerms) }
            .toMap()

        return GramStatistics(docTermCounts, docTermFreqs, corpusTermFreqs)
    }

    fun getStats(text: String, statType: GramStatType): GramStatistics {
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

