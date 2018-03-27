@file:JvmName("KotGram")
package edu.unh.cs980.language

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import edu.unh.cs980.forEachParallel
import edu.unh.cs980.getIndexWriter
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import java.io.BufferedInputStream
import java.io.File
import java.io.StringReader
import java.lang.Math.min
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.buildIterator
import kotlin.coroutines.experimental.buildSequence


/**
 * Class: KotlinGram
 * Desc: This class is responsible for extracting unigrams, bigrams, and windowed bigrams from the corpus.
 */
class KotlinGram(filename: String) {
    val analyzer = EnglishAnalyzer()
    val indexWriter = getIndexWriter(filename)

    /**
     * Function: getFilteredtokens
     * Desc: Returns a sequence of (stemmed) tokens from the given text
     */
    fun getFilteredTokens(text: String): Sequence<String> {
        val tokenStream = analyzer.tokenStream("text", StringReader(text)).apply { reset() }
        return buildSequence<String>() {
            while (tokenStream.incrementToken()) {
                yield(tokenStream.getAttribute(CharTermAttribute::class.java).toString())
            }
            tokenStream.end()
            tokenStream.close()
        }
    }


    /**
     * Function: doIndex
     * Desc: Given the context of a paragraph, indexes unigrams, bigrams, and windowed bigrams.
     */
    private fun doIndex(parText: String) {
        val tokens = getFilteredTokens(parText).toList()
        val doc = Document()
        val unigrams = ArrayList<String>()
        val bigrams = ArrayList<String>()
        val bigramWindows = ArrayList<String>()
        (0 until tokens.size).forEach { i ->
            unigrams.add(tokens[i])
            if (i < tokens.size - 1) {
                bigrams.add(tokens[i] + tokens[i + 1])
            }

            ( i + 1 until min(i + 9, tokens.size)).forEach { j ->
                bigramWindows.add(tokens[i] + tokens[j])
                bigramWindows.add(tokens[j] + tokens[i])
            }
        }
        doc.add(TextField("unigram", unigrams.joinToString(separator = " "), Field.Store.YES))
        doc.add(TextField("bigrams", bigrams.joinToString(separator = " "), Field.Store.YES))
        doc.add(TextField("bigram_windows", bigrams.joinToString(separator = " "), Field.Store.YES))
        indexWriter.addDocument(doc)

    }

    private fun iterWrapper(f: BufferedInputStream): Iterable<String> {
        val iter = DeserializeData.iterParagraphs(f)
        var counter = 0
        val iterWrapper = buildIterator<String>() {
            while (iter.hasNext()) {
                val nextPar = iter.next()
                if (counter % 3 == 0) {
                    yield(nextPar.textOnly)
                }
            }
        }
        return Iterable { iterWrapper }
    }


    /**
     * Class: indexGrams
     * Desc: Given a paragraph corpus, creates an index of grams, bigrams, and windowed bigrams.
     *       Only 25% of the available corpus is used (to save space).
     */
    fun indexGrams(filename: String) {
        val f = File(filename).inputStream().buffered(16 * 1024)
        val counter = AtomicInteger()

//        DeserializeData.iterableParagraphs(f)
        iterWrapper(f)
            .forEachParallel { par ->

                // This is just to keep track of how many pages we've parsed
                counter.incrementAndGet().let {
                    if (it % 100000 == 0) {
                        println(it)
                        indexWriter.commit()
                    }
                }

                // Extract all of the anchors/entities and add them to database
                doIndex(par)
//                if (ThreadLocalRandom.current().nextDouble() <= 0.25) {
//                }
            }

        indexWriter.close()
    }
}
