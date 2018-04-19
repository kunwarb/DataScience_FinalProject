package edu.unh.cs980.language

import edu.unh.cs980.*
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.misc.GradientDescenter
import edu.unh.cs980.misc.PartitionDescenter
import org.apache.commons.math3.distribution.NormalDistribution
import smile.math.matrix.Matrix
import smile.math.matrix.PageRank
import java.io.File
import java.lang.Double.sum
import java.lang.Math.*
import java.util.*
import kotlin.math.log2


data class TopicMixtureResult(val results: SortedMap<String, Double>, val kld: Double) {

    fun reportResults() {
        results.entries
            .filter { (_, weight) -> weight > 0.0 }
            .sortedByDescending { (_, weight) -> weight }
            .forEach { (topicName, weight) ->
                println("$topicName : $weight")}

        println("KLD: $kld\n\n")
    }

    fun euclideanDistance(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> (pow(x - y, 2.0)) }
                .apply { sqrt(this)  }

    fun minkowskiDistance(other: TopicMixtureResult): Double =
        results.values.zip(other.results.values)
            .sumByDouble { (x, y) -> (pow(abs(x - y), results.values.size.toDouble())) }
            .apply { pow(abs(this), 1 / results.values.size.toDouble())  }

    fun deltaSim(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> (x * y) }

    fun weirdSim(other: TopicMixtureResult): Double {
        val delta = deltaSim(other)
        val manhatten = euclideanDistance(other)
        return delta * (1 - manhatten)
    }

    fun weirdSim2(other: TopicMixtureResult): Double {
        val delta = deltaSim(other)
        val manhatten = euclideanDistance(other)
        return delta / max(0.00001, manhatten)
    }

    fun kld(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) ->
                    val (xM, yM) = max(0.0001, x) to max(0.0001, y)
                    xM * log2(xM / yM)
                }

    fun deltaDensity4(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> y * (abs(y - x)) }

    fun deltaDensity2(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> (x * y) / max(0.001, (abs(x - y))) }

    fun deltaDensity(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> x * (x - y) + y * (y - x) }

    fun manhattenDistance(other: TopicMixtureResult): Double =
            results.values.zip(other.results.values)
                .sumByDouble { (x, y) -> abs(x - y) }

    fun cosineSim(other: TopicMixtureResult): Double {
        val totalProduct = results.values.zip(other.results.values)
            .sumByDouble { (x, y) -> (x * y) }

        val xProduct = results.values.sumByDouble { value -> value * value }.apply { sqrt(this) }
        val yProduct = other.results.values.sumByDouble { value -> value * value }.apply { sqrt(this) }
        return totalProduct / (xProduct * yProduct)

    }

}

class WordKernel(val word: String) {
    val distribution = HashMap<String, Double>()
    var frequency = 0.0
    var condFrequency = 0.0
    var normFreq = 0.0

    fun normalize() {
        val total = distribution.values.sum()
        distribution.forEach { (k,v) -> distribution[k] = v / total }
    }


}

class KernelDist(val mean: Double, val std: Double, val doCondition: Boolean = true,
                 val analyzer: AnalyzerFunctions.AnalyzerType = ANALYZER_ENGLISH) {
    val kernels = HashMap<String, WordKernel>()

    val distMappings = getDistMappings(mean, std)

    private fun updateKernels(w1: String, w2: String, dist: Int) {
        updateKernel(w1, w2, dist)
        updateKernel(w2, w1, dist)
    }

    private fun updateKernel(kernelWord: String, destWord: String, dist: Int) {
        kernels.computeIfAbsent(kernelWord, { WordKernel(kernelWord) })
            .distribution
            .merge(destWord, distMappings[dist], ::sum)
    }

    private fun getDistMappings(mean: Double, sd: Double): List<Double> {
        val norm = NormalDistribution(mean, sd)
        return (0 .. 10).map { param -> norm.cumulativeProbability(param.toDouble()) }
            .windowed(2)
            .map { (v1, v2) -> v2 - v1 }
    }

    fun analyzeDocument(text: String, letterGram: Boolean = false) {
        val filterPattern = "[\\d+]".toRegex()
        val filteredText = filterPattern.replace(text, "")
        val terms = AnalyzerFunctions.createTokenList(filteredText, analyzerType = analyzer)

        if (letterGram) {
            terms.forEach { term ->
                term.windowed(2, partialWindows = false)
                    .forEach { lGram -> kernels.computeIfAbsent(lGram, { WordKernel(term) }).frequency += 1.0 }
            }
            return
        }

        terms.forEach { term -> kernels.computeIfAbsent(term, { WordKernel(term) }).frequency += 1.0 }
        if (doCondition) {
            terms
                .windowed(8, 1, true)
                .forEach { window ->
                    val firstTerm = window[0]

                    window
                        .slice(1 until window.size)
                        .forEachIndexed { index, secondTerm ->
                            updateKernels(firstTerm, secondTerm, index)
                        }
                }
        }
    }

    fun analyzePartitionedDocument(text: String, letterGram: Boolean = false) =
            text
                .toLowerCase()
                .split(".")
                .filter { it.length > 3 }
                .forEach { splitText -> analyzeDocument(splitText, letterGram) }


    fun perturb(nSamples: Int = 50): Pair<List<String>, List<List<Double>>> {
//        val norm = NormalDistribution(sharedRand, 1000.0, 50.0)
        val norm = NormalDistribution(sharedRand, 1000.0, 50.0)
//        val norm = NormalDistribution(sharedRand, 1000.0, 100.0)
        val (kernelNames, kernelFreqs) = kernels.toList().unzip()

        val perturbations = (0 until nSamples).map {
            norm.sample(kernelFreqs.size)
                .toList()
                .zip(kernelFreqs)
                .map { (gaussian, kernelFreq) -> gaussian * kernelFreq.frequency  } }

        return kernelNames to perturbations
    }



    fun getKernelFreqs() = kernels.map { (word, kernel) -> word to kernel.frequency }.toMap()


    fun normalizeKernels() {
        val total = kernels.values.sumByDouble { kernel -> kernel.frequency }
        kernels.values.forEach{ wordKernel -> wordKernel.normalize()}
        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / total }
    }


    fun normalizeByCond2() {
        kernels.values.forEach{ wordKernel -> wordKernel.normalize()}
        kernels.values.forEach { kernel -> kernel.frequency = 1.0 }

        (0 until 20).forEach {
            kernels.values.flatMap { kernel -> kernel.distribution.map { (k,v) -> k to v * kernel.frequency } }
                .groupingBy { (word, _) -> word }
                .fold(0.0) { acc, (_, conditionalFreq) -> acc + conditionalFreq }
                .forEach { (k,v) -> kernels[k]!!.frequency = v}
        }

        val total = kernels.values.sumByDouble { kernel -> kernel.frequency }
        kernels.values.forEach { kernel -> kernel.frequency /= total }
    }

    fun equilibriumCovariance() {
        kernels.values.forEach { worKernel -> worKernel.normalize() }
        val wordIndices = kernels.keys.mapIndexed { index, key -> key to index  }.toMap()
        val reverseIndices = wordIndices.map { (k,v) -> v to k }.toMap()
        val covarianceMatrix = Matrix.newInstance(wordIndices.size, wordIndices.size, 0.0)
        val base = wordIndices.map { (word, index) -> kernels[word]!!.frequency }.toDoubleArray()

        kernels.values.map { kernel ->
            val i = wordIndices[kernel.word]!!
            kernel.distribution.forEach { (word,covariance) ->
                val j = wordIndices[word]!!
                covarianceMatrix[i,j] = covariance
            }
        }

        val ranked = PageRank
            .pagerank(covarianceMatrix, base, 0.99, 0.00000001, 10000)
                .map { if (it < 0.0) 1 / wordIndices.size.toDouble() else it }

        val total = ranked.sum()
        ranked.forEachIndexed { index, d -> kernels[reverseIndices[index]]!!.frequency = d / total  }
    }
}

class KotlinKernelAnalyzer(val mean: Double, val std: Double, val corpus: (String) -> Double?,
                           val partitioned: Boolean = false) {
    val topics = HashMap<String, KernelDist>()

    fun analyzeTopicDirectories(mainDirectory: String, filterList: List<String> = listOf(), smooth: Boolean = false) {
        File(mainDirectory)
            .listFiles()
            .filter(File::isDirectory)
            .filter { file -> filterList.isEmpty() || file.name in filterList  }
            .forEach { file ->  analyzeTopic(file.name, file, smooth)}
    }

    private fun analyzeTopic(topic: String, directory: File, smooth: Boolean) {
        val kernelDist = KernelDist(mean, std, doCondition = smooth)
        topics[topic] = kernelDist
        directory.listFiles()
            .forEach { file ->  if (partitioned) kernelDist.analyzePartitionedDocument(file.readText())
                                else kernelDist.analyzeDocument(file.readText())}
    }

    fun createTopicFromParagraph(topic: String, paragraph: String, smooth: Boolean) {
        val kernelDist = KernelDist(mean, std, doCondition = smooth)
        topics[topic] = kernelDist
        kernelDist.analyzePartitionedDocument(paragraph)
    }

    fun normalizeTopics() {
        topics.values.forEach { topic -> topic.normalizeKernels() }
    }

    fun retrieveTopicFrequencies() =
            topics.map { (topic, kernel) ->
                topic to kernel.getKernelFreqs()
            }



    fun classifyByDomainSimplex2(integrals: List<Pair<String, List<Double>>>, nIterations: Int = 500, smooth: Boolean = false): TopicMixtureResult {
        val identityFreq = integrals.find { it.first == "identity" }!!.second
        val (featureNames, featureFreqs) = integrals.filter { it.first != "identity" }.unzip()

//        val stepper = GradientDescenter(identityFreq, featureFreqs)
        val stepper = PartitionDescenter(identityFreq, featureFreqs)
//        val stepper = MartingaleSolver(identityFreq, featureFreqs)
        val (weights, kld) = stepper.startDescent(nIterations)
        val results = featureNames.zip(weights).toMap()

        return TopicMixtureResult(results.toSortedMap(), kld)
    }




}


fun main(args: Array<String>) {

}
