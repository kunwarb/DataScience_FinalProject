package edu.unh.cs980.language

import edu.unh.cs980.*
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_STANDARD
import edu.unh.cs980.misc.GradientDescenter
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.random.RandomGenerator
import java.io.File
import java.lang.Double.max
import java.lang.Double.sum
import java.lang.Math.log10
import java.lang.Math.sqrt
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


    fun perturb(): Map<String, Double> {
        val perturbations = kernels.mapNotNull { (k,v) ->
//            val norm = NormalDistribution(sharedRand,1000.0 * v.frequency, 50.0 * v.frequency)
//            val norm = NormalDistribution(1000.0, 50.0)
            val norm = NormalDistribution(sharedRand, 1000.0, 50.0)
            val sample = norm.sample() * v.frequency
//            if (sharedRand.nextDouble() <= sqrt(v.frequency)) k to sample else null
//            k to v.frequency * (sharedRand.nextDouble())
            k to sample
//            k to v.frequency * (sharedRand.nextDouble() * (v.frequency)) * (sharedRand.nextDouble() * sqrt(v.frequency))
//            k to v.frequency * (sharedRand.nextDouble() * (v.frequency)) + (sharedRand.nextDouble() * sqrt(v.frequency))

            // interaction
//            k to v.frequency * (sharedRand.nextDouble() * (v.frequency)) + v.frequency * (sharedRand.nextDouble() * sqrt(v.frequency))

//            k to v.frequency * (sharedRand.nextDouble() * (v.frequency))
//            if (sample <= 0.0) null else k to sample
        }

//        val total = perturbations.sumByDouble { it.second }
//        return perturbations.map { it.first to it.second / total }.toMap()
        return perturbations.toMap()
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
//            var k1 = kernels[word]?.frequency ?: 1 / kernels.size.toDouble()
//            var k2 = other.kernels[word]?.frequency ?: 1 / other.kernels.size.toDouble()

//            val c = corpus(word) ?: 1.0
//            var k1 = kernels[word]?.frequency ?: (1 / union.size.toDouble()) * c
//            var k1 = kernels[word]?.frequency ?: 1.0 / union.size
//            var k2 = other.kernels[word]?.frequency ?: (1 / other.kernels.size.toDouble() )
//            var k1 = kernels[word]?.frequency ?: 1.0
            var k1 = normalizedMap[word]!!
            var k2 = other[word] ?: 0.0

            (k2) * log2((k2 / k1))
        } / union.size
    }


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
}

class KotlinKernelAnalyzer(val mean: Double, val std: Double, val corpus: (String) -> Double?,
                           val partitioned: Boolean = false) {
    val topics = HashMap<String, KernelDist>()

    fun analyzeTopicDirectories(mainDirectory: String) {
        File(mainDirectory)
            .listFiles()
            .filter(File::isDirectory)
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
