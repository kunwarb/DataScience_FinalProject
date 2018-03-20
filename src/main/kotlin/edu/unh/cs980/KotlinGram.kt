@file:JvmName("KotGram")
package edu.unh.cs980

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.query.QueryAutoStopWordAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.Term
import org.mapdb.BTreeMap
import org.mapdb.DBMaker
import org.mapdb.Serializer
import org.mapdb.serializer.SerializerArrayTuple
import java.io.File
import java.io.StringReader
import java.lang.Math.max
import java.lang.Math.min
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.buildSequence


class KotlinGram(filename: String) {
//    val db = DBMaker
//        .fileDB(dbPath)
//        .fileMmapEnable()
//        .closeOnJvmShutdown()
//        .concurrencyScale(60)
//        .make()


    // Key: (anchor text, linked entity)
    // value: Number of times we have seen anchor text refer to linked entity
//    val bigramMap = db.treeMap("bigram")
//        .keySerializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
//        .valueSerializer(Serializer.INTEGER)
//        .createOrOpen()
//
//    val bigramMap = db.treeMap("bigram")
//        .keySerializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
//        .valueSerializer(Serializer.INTEGER)
//        .createOrOpen()
//
//    val bigramWindowMap = db.treeMap("bigramWindowMap")
//        .keySerializer(SerializerArrayTuple(Serializer.STRING, Serializer.STRING))
//        .valueSerializer(Serializer.INTEGER)
//        .createOrOpen()
//
//    val unigramMap = db.hashMap("unigram")
//        .keySerializer(Serializer.STRING)
//        .valueSerializer(Serializer.INTEGER)
//        .createOrOpen()

    val analyzer = EnglishAnalyzer()
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

//    fun addUnigram(unigram: String) =
//        unigramMap.compute(unigram, { key, value -> value?.inc() ?: 1 })
//
//    fun addBigram(w1: String, w2: String) =
//        bigramMap.compute(arrayOf(w1, w2), { key, value -> value?.inc() ?: 1 })
//
//    fun addBigramWindow(w1: String, w2: String) =
//            bigramWindowMap.compute(arrayOf(w1, w2), { key, value -> value?.inc() ?: 1 })


//    private fun doIndex(parText: String) {
//        val tokens = getFilteredTokens(parText).toList()
//        (0 until tokens.size).forEach { i ->
//            addUnigram(tokens[i])
//            if (i < tokens.size - 1) {
//                addBigram(tokens[i], tokens[i + 1])
//            }
//
//            ( i + 1 until min(i + 5, tokens.size)).forEach { j ->
//                if (j - i == 1) {
//                } else {
//                    addBigramWindow(tokens[i], tokens[j])
//                }
//            }
//        }
//    }

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

            ( i + 1 until min(i + 5, tokens.size)).forEach { j ->
                bigramWindows.add(tokens[i] + tokens[j])
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
                if (ThreadLocalRandom.current().nextDouble() <= 0.1) {
                    doIndex(par.textOnly)
                }
            }

        indexWriter.close()
    }

}


