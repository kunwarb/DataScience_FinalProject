@file:JvmName("KotGram")
package edu.unh.cs980.language

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import edu.unh.cs980.forEachParallel
import edu.unh.cs980.getIndexWriter
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import java.io.File
import java.io.StringReader
import java.lang.Math.min
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.buildSequence


class KotlinGram(filename: String) {
    val analyzer = StandardAnalyzer()
    val rand = Random().apply { setSeed(128383197) }
    val indexWriter = getIndexWriter(filename)

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

    fun indexGrams(filename: String) {
        val f = File(filename).inputStream().buffered(16 * 1024)
        val counter = AtomicInteger()

        DeserializeData.iterableParagraphs(f)
            .forEachParallel { par ->

                // This is just to keep track of how many pages we've parsed
                counter.incrementAndGet().let {
                    if (it % 100000 == 0) {
                        println(it)
                        indexWriter.commit()
                    }
                }

                // Extract all of the anchors/entities and add them to database
                if (ThreadLocalRandom.current().nextDouble() <= 0.5) {
                    doIndex(par.textOnly)
                }
            }

        indexWriter.close()
    }

}


