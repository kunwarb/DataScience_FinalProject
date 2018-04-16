package edu.unh.cs980.language

import edu.unh.cs980.*
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.misc.GradientDescenter
import org.apache.commons.math3.distribution.NormalDistribution
import smile.math.matrix.Matrix
import smile.math.matrix.PageRank
import java.io.File
import java.lang.Double.sum
import kotlin.math.log2


data class TopicMixtureResult(val results: Map<String, Double>, val kld: Double) {

    fun reportResults() {
        results.entries
            .filter { (_, weight) -> weight > 0.0 }
            .sortedByDescending { (_, weight) -> weight }
            .forEach { (topicName, weight) ->
                println("$topicName : $weight")}

        println("KLD: $kld\n\n")
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

    fun analyzeDocument(text: String) {
        val filterPattern = "[\\d+]".toRegex()
        val filteredText = filterPattern.replace(text, "")
        val terms = AnalyzerFunctions.createTokenList(filteredText, analyzerType = analyzer)

        terms.forEach { term -> kernels.computeIfAbsent(term, { WordKernel(term) }).frequency += 1.0 }
        if (doCondition) {
            terms
                .windowed(8, 1, true)
                .forEach { window ->
                    val firstTerm = window[0]
//                    kernels.computeIfAbsent(firstTerm, { WordKernel(firstTerm) }).frequency += 1.0

                    window
                        .slice(1 until window.size)
                        .forEachIndexed { index, secondTerm ->
                            updateKernels(firstTerm, secondTerm, index)
                        }
                }
        }
    }

    fun analyzePartitionedDocument(text: String) =
            text
                .toLowerCase()
                .split(".")
                .filter { it.length > 3 }
                .forEach { splitText -> analyzeDocument(splitText) }


    fun perturb(nSamples: Int = 50): Pair<List<String>, List<List<Double>>> {
        val norm = NormalDistribution(sharedRand, 1000.0, 50.0)
        val (kernelNames, kernelFreqs) = kernels.toList().unzip()

        val perturbations = (0 until nSamples).map {
            norm.sample(kernelFreqs.size)
                .zip(kernelFreqs)
                .map { (gaussian, kernelFreq) -> gaussian * kernelFreq.frequency  } }

        return kernelNames to perturbations
    }

    fun perturb2(nSamples: Int = 50): List<Map<String, Double>> {
        val norm = NormalDistribution(sharedRand, 1000.0, 50.0)
//        val (kernelNames, kernelFreqs) = kernels.toList().unzip()
        val results = (0 until nSamples).map {
            kernels.map { (k, v) -> k to norm.sample() * v.frequency }.toMap()
        }

        return results
    }


    fun kld(other: Map<String, Double>, corpus: (String) -> Double?): Double {
//        val union = kernels.keys.union(other.kernels.keys)
        val union = other.keys
//        val sumFreq = other.kernels.values.sumByDouble { kernel -> kernel.frequency }

        val normalizedMap = HashMap<String, Double>()
        val defValue = 1.0
        union.forEach { key ->
            normalizedMap[key] =  kernels[key]?.run{frequency} ?: defValue
        }

        val total = normalizedMap.values.sum() +
                kernels.filter { kernel -> kernel.key !in normalizedMap }.values.sumByDouble { kernel -> kernel.frequency }
        normalizedMap.keys.forEach { key -> normalizedMap[key] = normalizedMap[key]!! / total }

        return 1.0 * union.sumByDouble { word ->
            var k1 = normalizedMap[word]!!
            var k2 = other[word] ?: 0.0

            (k2) * log2((k2 / k1))
        } / union.size
    }

    fun getKernelFreqs() = kernels.map { (word, kernel) -> word to kernel.frequency }.toMap()


    fun normalizeKernels() {
        val total = kernels.values.sumByDouble { kernel -> kernel.frequency }
        kernels.values.forEach{ wordKernel -> wordKernel.normalize()}
        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / total }
    }

    fun normalizeByCond() {
        var total = kernels.values.sumByDouble { kernel -> kernel.frequency }
        kernels.values.forEach{ wordKernel -> wordKernel.normalize()}
        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / total }

        val condTotal = kernels.values.flatMap { kernel -> kernel.distribution.map { (k,v) -> k to v * kernel.frequency } }
            .groupingBy { (word, _) -> word }
            .fold(0.0) { acc, (_, conditionalFreq) -> acc + conditionalFreq }

        condTotal.forEach { (k,v) -> kernels[k]!!.condFrequency = v }
        total = kernels.values.sumByDouble { kernel -> kernel.condFrequency }
        kernels.values.forEach { kernel -> kernel.condFrequency = kernel.condFrequency / total }

        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency * 0.5 + kernel.condFrequency * 0.5 }
        total = kernels.values.sumByDouble { kernel -> kernel.frequency }
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

//        println(covarianceMatrix)
//        val base = (0 until wordIndices.size).map { 1.0 }.toDoubleArray()
        val ranked = PageRank
//                .pagerank(covarianceMatrix, base, 0.9, 0.000001, 10000)
            .pagerank(covarianceMatrix, base, 0.8, 0.00000001, 10000)
                .map { if (it < 0.0) 1 / wordIndices.size.toDouble() else it }

        val total = ranked.sum()
        ranked.forEachIndexed { index, d -> kernels[reverseIndices[index]]!!.frequency = d / total  }
    }
}

class KotlinKernelAnalyzer(val mean: Double, val std: Double, val corpus: (String) -> Double?,
                           val partitioned: Boolean = false) {
    val topics = HashMap<String, KernelDist>()

    fun analyzeTopicDirectories(mainDirectory: String, filterList: List<String> = listOf()) {
        File(mainDirectory)
            .listFiles()
            .filter(File::isDirectory)
            .filter { file -> filterList.isEmpty() || file.name in filterList  }
            .forEach { file ->  analyzeTopic(file.name, file)}
    }

    private fun analyzeTopic(topic: String, directory: File) {
        val kernelDist = KernelDist(mean, std, doCondition = false)
        topics[topic] = kernelDist
        directory.listFiles()
            .forEach { file ->  if (partitioned) kernelDist.analyzePartitionedDocument(file.readText())
                                else kernelDist.analyzeDocument(file.readText())}
    }

    fun normalizeTopics() {
        topics.values.forEach { topic -> topic.normalizeKernels() }
    }

    fun retrieveTopicFrequencies() =
            topics.map { (topic, kernel) ->
                topic to kernel.getKernelFreqs()
            }


    fun scoreTopicDomain(topic: KernelDist, domain: List<Map<String, Double>>, smooth: Boolean = false): List<Double> {
        val scores = domain.map { k -> topic.kld(k, corpus) }
        val total = scores.sum()
        return scores.map { score -> score / total }
            .let { results -> if (smooth) results.smooth() else results  }
    }


    fun classifyByDomainSimplex(text: String, domain: List<Map<String, Double>>, smooth: Boolean = false): TopicMixtureResult {
        val textKernel = KernelDist(mean, std)
        if (partitioned) textKernel.analyzePartitionedDocument(text) else textKernel.analyzeDocument(text)
        textKernel.normalizeKernels()

        val base = scoreTopicDomain(textKernel, domain)
        val filterDomained = domain.zip(base).filter { it.second > 0.0 }.map { it.first }
        val filteredBase = scoreTopicDomain(textKernel, filterDomained, smooth)

        val topicDistributions = topics
            .map { topic -> scoreTopicDomain(topic.value, filterDomained, smooth) }

        val stepper = GradientDescenter(filteredBase, topicDistributions)
        val (weights, kld) = stepper.startDescent(800)

        val results = topics.keys.zip(weights).toMap()
        return TopicMixtureResult(results, kld)
    }

    fun classifyByDomainSimplex2(integrals: List<Pair<String, List<Double>>>, nIterations: Int = 500, smooth: Boolean = false): TopicMixtureResult {
        val identityFreq = integrals.find { it.first == "identity" }!!.second
        val (featureNames, featureFreqs) = integrals.filter { it.first != "identity" }.unzip()

        val stepper = GradientDescenter(identityFreq, featureFreqs)
        val (weights, kld) = stepper.startDescent(nIterations)

        val results = featureNames.zip(weights).toMap()
        return TopicMixtureResult(results, kld)
    }

    fun getUnigramFrequencies(text: String): Map<String, Double> {
        val filterPattern = "[\\d+]".toRegex()
        val filteredText = filterPattern.replace(text, "").toLowerCase()
        val terms = AnalyzerFunctions.createTokenList(filteredText, analyzerType = ANALYZER_ENGLISH)

        return terms
            .groupingBy(::identity)
            .eachCount()
            .normalize()
    }




}


fun main(args: Array<String>) {

}
