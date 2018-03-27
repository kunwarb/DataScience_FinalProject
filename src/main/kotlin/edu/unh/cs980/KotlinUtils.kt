@file:JvmName("KotUtils")
package edu.unh.cs980

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import java.nio.file.Paths
import java.util.*

// Conditional versions of run/let/apply/also
fun <T,R> T.runIf(condition: Boolean, block: T.() -> R): R? = if (condition)  run(block)  else null
fun <T,R> T.runIf(condition: T.() -> Boolean, block: T.() -> R): R? = if (condition())  run(block)  else null

fun <T,R> T.letIf(condition: Boolean, block: (T) -> R): R? = if (condition)  let(block)  else null
fun <T,R> T.letIf(condition: T.() -> Boolean, block: (T) -> R): R? = if (condition())  let(block)  else null

fun <T> T.applyIf(condition: Boolean, block: T.() -> Unit): T = if (condition)  apply(block)  else this
fun <T> T.applyIf(condition: T.() -> Boolean, block: T.() -> Unit): T = if (condition())  apply(block)  else this

fun <T> T.alsoIf(condition: Boolean, block: (T) -> Unit): T = if (condition) also(block) else this
fun <T> T.alsoIf(condition: T.() -> Boolean, block: (T) -> Unit): T = if (condition()) also(block) else this


// Parallel versions of map/forEach methods.
// See: https://stackoverflow.com/questions/45575516/kotlin-process-collection-in-parallel
fun <A, B>Iterable<A>.pmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async(CommonPool) { f(it) } }.map { it.await() }
}

fun <A, B>Iterable<A>.pmapRestricted(nThreads: Int = 10, f: suspend (A) -> B): List<B> = runBlocking {
    val pool = newFixedThreadPoolContext(nThreads, "parallel")
    map { async(pool) { f(it) } }.map { it.await() }
}


fun <A>Iterable<A>.forEachParallel(f: suspend (A) -> Unit): Unit = runBlocking {
    map { async(CommonPool) { f(it) } }.forEach { it.await() }
}

fun <A>Iterable<A>.forEachParallelRestricted(nThreads: Int = 10, f: suspend (A) -> Unit): Unit = runBlocking {
    val pool = newFixedThreadPoolContext(nThreads, "parallel")
    map { async(pool) { f(it) } }.forEach { it.await() }
}



//fun <A>Sequence<A>.forEachParallel(f: suspend (A) -> Unit): Unit = runBlocking {
//    forEach { async(CommonPool) { f(it) }.await() }
//}


// Map Extensions
fun <K,V>MutableMap<K,V>.removeAll(f: (key:K,value:V) -> Boolean) {
    this.entries
        .filter{(key,value) -> f(key,value)}
        .forEach { (key,_) ->
            remove(key)
        }
}

// Retrieves an index searcher (I use this everywhere so might as well put it here)
val openIndexSearchers = HashSet<String>()
fun getIndexSearcher(indexLocation: String): IndexSearcher {
    if (!openIndexSearchers.add(indexLocation)) {
        println("Warning: you have already opened an index at $indexLocation!!!")
    }

    val indexPath = Paths.get(indexLocation)
    val indexDir = FSDirectory.open(indexPath)
    val indexReader = DirectoryReader.open(indexDir)
    return IndexSearcher(indexReader)
}

fun getIndexWriter(indexLocation: String): IndexWriter {
    val indexPath = Paths.get(indexLocation)
    val indexDir = FSDirectory.open(indexPath)
    val conf = IndexWriterConfig(EnglishAnalyzer())
        .apply { openMode = IndexWriterConfig.OpenMode.CREATE }
    return IndexWriter(indexDir, conf)
}


// Constants referring to Lucene fields
public const val PID: String = "paragraphid"
public const val CONTENT = "text"

// Functional interface for features
typealias FeatureInterface = (String, TopDocs, IndexSearcher) -> List<Double>

// I don't know why the hell they don't have an identity function..
fun <A> identity(it: A): A = it

fun Double.defaultWhenNotFinite(default: Double = 0.0): Double = if (!isFinite()) default else this

