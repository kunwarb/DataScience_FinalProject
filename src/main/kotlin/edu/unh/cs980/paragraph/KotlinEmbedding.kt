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

        kernelDist.normalizeByCond2()

//        val samples = kernelDist.perturb(300)
        val samples = kernelDist.perturb2(300)

//        val topicStats = kernelAnalyzer.retrieveTopicFrequencies() + identityFreqs
//        val stochasticIntegrator = KotlinStochasticIntegrator(samples, topicStats)
//        val integrals = stochasticIntegrator.integrate()

//        return kernelAnalyzer.classifyByDomainSimplex2(integrals, smooth = false)
        return kernelAnalyzer.classifyByDomainSimplex(text, samples, smooth = false)
    }
}


fun main(args: Array<String>) {
    val gramLoc = "/home/hcgs/data_science/gram"
    val indexLoc = "/home/hcgs/data_science/index"

    val embedder = KotlinEmbedding(indexLoc, gramLoc)
    embedder.loadTopics("paragraphs/")
//    val testText = """
//        Contemporary medicine is in general conducted within health care systems. Legal, credentialing and financing frameworks are established by individual governments, augmented on occasion by international organizations, such as churches. The characteristics of any given health care system have significant impact on the way medical care is provided.
//
//From ancient times, Christian emphasis on practical charity gave rise to the development of systematic nursing and hospitals and the Catholic Church today remains the largest non-government provider of medical services in the world.[15] Advanced industrial countries (with the exception of the United States)[16][17] and many developing countries provide medical services through a system of universal health care that aims to guarantee care for all through a single-payer health care system, or compulsory private or co-operative health insurance. This is intended to ensure that the entire population has access to medical care on the basis of need rather than ability to pay. Delivery may be via private medical practices or by state-owned hospitals and clinics, or by charities, most commonly by a combination of all three.
//
//Most tribal societies provide no guarantee of healthcare for the population as a whole. In such societies, healthcare is available to those that can afford to pay for it or have self-insured it (either directly or as part of an employment contract) or who may be covered by care financed by the government or tribe directly.
//
//collection of glass bottles of different sizes
//Modern drug ampoules
//        """
    val testText =
            File("paragraphs/Biology/doc_1.txt").readText() +
            File("paragraphs/Computers/doc_1.txt").readText()

    val result = embedder.embed(testText, nSamples = 300)
    result.reportResults()
}