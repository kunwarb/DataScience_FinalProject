@file:JvmName("KotEntityLinker")
package edu.unh.cs980

import edu.unh.cs.treccar_v2.read_data.DeserializeData
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * Class: retrieveEntities
 * Description: Queries spotlight server with string and retrieve list of linked entities.
 * @return List of linked entities (strings). Empty if no entities were linked or if errors were encountered.
 */
class KotlinEntityLinker(serverLocation: String) {
    val url = "http://localhost:9310/jsr-spotlight/annotate"        // Hardcoded url to local server

    // Opens up a new index searcher using the directory given to us as an argument
//    val indexSearcher = kotlin.run {
//        val  indexPath = Paths.get (indexLoc)
//        val indexDir = FSDirectory.open(indexPath)
//        val indexReader = DirectoryReader.open(indexDir)
//        IndexSearcher(indexReader)
//    }

    // Start up server (can take a while if we need to download files
    val server = KotlinSpotlightRunner(serverLocation)


    /**
     * Function: retrieveEntities
     * Description: Queries spotlight server with string and retrieve list of linked entities.
     * @return List of linked entities (strings). Empty if no entities were linked or if errors were encountered.
     */
    fun retrieveEntities(content: String): List<String> {

        // Retrieve html file from the Spotlight server
        val jsoupDoc = Jsoup.connect(url)
                .data("text", content)
                .post()

        // Parse urls, returning only the last word of the url (after the last /)
        val links = jsoupDoc.select("a[href]")
        return links.map { element ->
            val title = element.attr("title")
            title.substring(title.lastIndexOf("/") + 1)
        }.toList()
    }


    /**
     * Function: queryServer
     * Description: Wrapper around retrieveEntities to handle timeouts (which seem to be possible for this server).
     *              Because of Socket Timeout Exceptions, will try three times before giving up.
     * @return List of entities (if any) linked by retrieveEntities function.
     */
    fun queryServer(content: String): List<String> {
        var entities = ArrayList<String>() as List<String>

        // Try three times to query server before giving up
        for (i in (0..3)) {
              try { entities = retrieveEntities(content); break
            } catch (e: SocketTimeoutException) { Thread.sleep(ThreadLocalRandom.current().nextLong(500))
            } catch (e: ConnectException) { Thread.sleep(ThreadLocalRandom.current().nextLong(500))}
        }
        return entities
    }


    /**
     * Function: keepPokingServer
     * Description: For some reason, the Spotlight errors-out for the first few queries
     *              I can see no way to fix this, so the only work around is to query the sever multiple times until it
     *              finally decides to start working again.
     */
    fun keepPokingServer() {
        for (it in 0..100) {
            Thread.sleep(250)
            try {
                retrieveEntities("Wake the hell up, server!")
                break
            }
            catch (e: ConnectException) { }
            catch (e: SocketTimeoutException) {}
        }
    }

    fun start_server() {
        println("Waiting for server to get ready")
        server.process.waitFor(15, TimeUnit.SECONDS)
        keepPokingServer()
    }


//    /**
//     * Function: run
//     * Description: Iterates over documents in Lucene index and links contents of each document using Spotlight server.
//     */
//    fun run() {
//        // Give a moment for server to warm up and keep poking it until it's ready to accept connections
//        println("Waiting for server to get ready")
////        server.process.waitFor(15, TimeUnit.SECONDS)
//        keepPokingServer()
//
//        // Set up progress bar and begin iterating over Lucene index documents
////        val totalDocs = indexSearcher.indexReader.maxDoc()
////        println("Indexing a total of $totalDocs documents")
////        val bar = ProgressBar("Documents Linked", totalDocs.toLong(),
////                ProgressBarStyle.ASCII)
////        bar.start()
////        val lock = ReentrantLock()
//
//
//        (0 until totalDocs).chunked(5000).forEach { chunk ->
//            chunk.forEachParallel { docId ->
//                val doc = indexSearcher.doc(docId)
//                val entities = queryServer(doc.get(CONTENT))
//
//                // Only attempt to annotate paragraph if there are no entities already
//                if (doc.getValues("spotlight").isEmpty()) {
//                    entities.forEach { entity ->
//                        doc.add(StringField("spotlight", entity, Field.Store.YES))
//                    }
//                }
//
//                // Update progress bar (have to make sure it's thread-safe)
//            }
//            lock.withLock { bar.stepBy(5000) }
//
//        }
//
////        (0 until totalDocs).chunked(5000).forEachParallel { docId ->
////            val doc = indexSearcher.doc(docId)
////            val entities = queryServer(doc.get(CONTENT))
////
////            // Only attempt to annotate paragraph if there are no entities already
////            if (doc.getValues("spotlight").isEmpty()) {
////                entities.forEach { entity ->
////                    doc.add(StringField("spotlight", entity, Field.Store.YES))
////                }
////            }
////
////            // Update progress bar (have to make sure it's thread-safe)
////            lock.withLock { bar.stepBy(1) }
////
////        }
//
//        bar.stop()
//        println("Finished annotating index!")
//    }
}
