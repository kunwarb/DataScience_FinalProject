package edu.unh.cs980.paragraph

import edu.unh.cs980.CONTENT
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.language.KernelDist
import edu.unh.cs980.language.KotlinGramAnalyzer
import edu.unh.cs980.language.KotlinKernelAnalyzer
import edu.unh.cs980.language.TopicMixtureResult
import edu.unh.cs980.misc.AnalyzerFunctions
import org.apache.lucene.index.Term
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess


class KotlinEmbedding(indexLoc: String, gramLoc: String) {
    private val gramAnalyzer = KotlinGramAnalyzer(gramLoc)
    private val searcher = getIndexSearcher(indexLoc)
    private val memoizedFreqs = ConcurrentHashMap<String, Double>()
    private val kernelAnalyzer =
            KotlinKernelAnalyzer(0.0, 1.0, corpus = this::getUnigramFrequency, partitioned = true)

    private val totalCorpusFreq = gramAnalyzer.indexSearcher.indexReader
        .getSumTotalTermFreq("unigram")
        .toDouble()

    fun query(query: String): List<String> {
        val boolQuery = AnalyzerFunctions.createQuery(query)
        val results = searcher.search(boolQuery, 10)
        return results.scoreDocs.map { scoreDoc ->
            val doc = searcher.doc(scoreDoc.doc)
            doc.get(CONTENT)
        }
    }

    fun getUnigramFrequency(unigram: String): Double? {
        val gramFreq = memoizedFreqs.computeIfAbsent(unigram) {
            gramAnalyzer
                .indexSearcher
                .indexReader
                .totalTermFreq(Term("unigram", unigram)) / totalCorpusFreq
        }

        return if (!gramFreq.isFinite() || gramFreq == 0.0) null else gramFreq
    }

    fun loadTopics(directory: String) {
        kernelAnalyzer.analyzeTopicDirectories(directory)
        kernelAnalyzer.normalizeTopics()
    }

    fun embed(text: String, nSamples: Int = 300): TopicMixtureResult {
        val kernelDist = KernelDist(0.0, 100.0)
            .apply { analyzePartitionedDocument(text) }
            .apply { normalizeKernels() }


        val identityFreqs = "identity" to kernelDist.getKernelFreqs()

        kernelDist.equilibriumCovariance()
//        kernelDist.normalizeByCond2()

        val samples = kernelDist.perturb(2000)
//        val samples = kernelDist.perturb2(300)

        val topicStats = kernelAnalyzer.retrieveTopicFrequencies() + identityFreqs
        val stochasticIntegrator = KotlinStochasticIntegrator(samples, topicStats, corpus = this::getUnigramFrequency)
        val integrals = stochasticIntegrator.integrate()

        return kernelAnalyzer.classifyByDomainSimplex2(integrals, smooth = false)
//        return kernelAnalyzer.classifyByDomainSimplex(text, samples, smooth = false)
    }
}


fun main(args: Array<String>) {
    val gramLoc = "/home/hcgs/data_science/gram"
    val indexLoc = "/home/hcgs/data_science/index"

    val embedder = KotlinEmbedding(indexLoc, gramLoc)
    embedder.loadTopics("paragraphs/")
//    val testText = """
//        A party is a gathering of people who have been invited by a host for the purposes of socializing, conversation, recreation, or as part of a festival or other commemoration of a special occasion. A party will typically feature food and beverages, and often music and dancing or other forms of entertainment. In many Western countries, parties for teens and adults are associated with drinking alcohol such as beer, wine or distilled spirits.
//        """
    val testText =
            File("paragraphs/Biology/doc_1.txt").readText() +
            File("paragraphs/Computers/doc_1.txt").readText()

    val result = embedder.embed(testText, nSamples = 300)
    result.reportResults()
}