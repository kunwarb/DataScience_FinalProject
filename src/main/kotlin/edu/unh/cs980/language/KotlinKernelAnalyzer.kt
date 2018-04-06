package edu.unh.cs980.language

import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import org.apache.commons.math3.distribution.NormalDistribution
import java.io.File
import java.lang.Double.max
import java.lang.Double.sum
import java.lang.Math.log10


//class WordCount(var freq: Double, var cofreq: Double)

class WordKernel(val word: String) {
    val distribution = HashMap<String, Double>()
    var frequency = 0.0

    fun normalize(tfidf: Map<String, Double>, useTFID: Boolean = true) {
        var total = distribution.values.sum()
//        distribution.forEach { (k,v) -> distribution[k] = v / total }
        if (useTFID) {
            distribution.forEach { (k,v) -> distribution[k] = (v / total) * tfidf[k]!! }
        }

        total = distribution.values.sum()
        distribution.forEach { (k,v) -> distribution[k] = v / total }

    }

    fun kld(other: WordKernel): Double {
        val restriction = distribution.keys.toSet()
            .intersect(other.distribution.keys.toSet())

        return restriction.sumByDouble { word ->
            val d1 = distribution[word]!!
            val d2 = other.distribution[word]!!
//            d1 * other.frequency * log10((d1 * other.frequency) / (d2 * frequency))
            (d1 - d2) * log10(d1 / d2)
        } / max(1.0, restriction.size.toDouble())
    }

}

class KernelDist(val mean: Double, val std: Double) {
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
        return (0 .. 8).map { param -> norm.cumulativeProbability(param.toDouble()) }
            .windowed(2)
            .map { (v1, v2) -> v2 - v1 }
    }

    fun analyzeDocument(text: String) {
        val terms = AnalyzerFunctions.createTokenList(text, analyzerType = ANALYZER_ENGLISH)

        terms
            .windowed(8, 1, true)
            .forEach { window ->
                val firstTerm = window[0]
                kernels.computeIfAbsent(firstTerm, { WordKernel(firstTerm) }).frequency += 1.0

                window
                    .slice(1  until window.size)
                    .forEachIndexed{ index, secondTerm ->
                        updateKernels(firstTerm, secondTerm, index)
                    }
            }
    }

    fun kld(other: KernelDist): Double {
        val restriction = kernels.keys.toSet()
            .intersect(other.kernels.keys.toSet())
//        val ktotal = kernels.values.sumByDouble { it.distribution.values.sum() }
//
//        val dissimilar = kernels.keys.union(other.kernels.keys).size / max(1.0, restriction.size.toDouble())
//        val unionTotal = kernels.values.sumByDouble(WordKernel::frequency) +
//                other.kernels.values.sumByDouble(WordKernel::frequency)
//
//        val interTotal = max( 1.0, restriction.sumByDouble { key -> kernels[key]!!.frequency + other.kernels[key]!!.frequency })
//        val dissimilar = unionTotal / interTotal

        return 1.0 * restriction.sumByDouble { word ->
            val k1 = kernels[word]!!
            val k2 = other.kernels[word]!!
//            k1.frequency * k1.kld(k2)
//            k1.kld(k2) * (k2.frequency / k1.frequency)
            k1.kld(k2) * (k1.frequency - k2.frequency) * log10((k1.frequency / k2.frequency))
        } / max(1.0, restriction.size.toDouble())
    }

    fun normalizeKernels(tfidf: Map<String, Double>, useTFID: Boolean = true) {
        val total = kernels.values.sumByDouble { kernel -> kernel.frequency }
//        kernels.values.forEach(WordKernel::normalize)
        kernels.values.forEach{ wordKernel -> wordKernel.normalize(tfidf, useTFID)}
        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / total }
//        kernels.values.forEach { kernel -> kernel.frequency = (kernel.frequency / total) / tfidf[kernel.word]!! }
    }
}

class KotlinKernelAnalyzer(val mean: Double, val std: Double) {
    val topics = HashMap<String, KernelDist>()
    var mydf: Map<String, Double> = HashMap<String, Double>()

    fun analyzeTopicDirectories(mainDirectory: String) {
        File(mainDirectory)
            .listFiles()
            .filter(File::isDirectory)
            .forEach { file ->  analyzeTopic(file.name, file)}
    }

    private fun analyzeTopic(topic: String, directory: File) {
        val kernelDist = KernelDist(mean, std)
        topics[topic] = kernelDist
        directory.listFiles()
            .forEach { file ->  kernelDist.analyzeDocument(file.readText())}
    }

    //    fun normalizeTopics() = topics.values.forEach(KernelDist::normalizeKernels)
    fun normalizeTopics() {
        val tfidf = topics.flatMap { topic -> topic.value.kernels.values }
            .groupingBy(WordKernel::word)
            .fold(0.0, { acc, kernel -> acc + kernel.frequency})
            .toMap()
        mydf = tfidf

        topics.values.forEach { topic -> topic.normalizeKernels(tfidf) }
    }

    fun classify(text: String) {
        val textKernel = KernelDist(mean, std)
        textKernel.analyzeDocument(text)
//        textKernel.normalizeKernels(mydf, true)

        val results = topics.map { topic -> topic to topic.value.kld(textKernel) }
        val best = results.maxBy { (_, kldDist) -> kldDist }!!
        val total = results.sumByDouble { (_,kldDist) -> kldDist }
//        println("Best: ${best.first.key}: ${best.second} / ${best.second / total}")
        results.sortedBy { it.second }
            .forEach { (topic, kldDist) -> println("${topic.key}: $kldDist" ) }
    }
}


fun main(args: Array<String>) {
    val analyzer = KotlinKernelAnalyzer(0.0, 3.0)
    analyzer.analyzeTopicDirectories("paragraphs/")
    analyzer.normalizeTopics()
    val example = File("paragraphs/Computers/doc_49.txt").readText()
    analyzer.classify(example)

//    analyzer.topics.forEach { t1 ->
//        analyzer.topics
////            .map { t2 -> Triple(t1, t2, (t1.value.kld(t2.value) + t2.value.kld(t1.value)) ) }
//            .map { t2 -> Triple(t1, t2, t1.value.kld(t2.value)) }
//            .sortedByDescending { it.third }
//            .forEach { (t1, t2, kld) ->
//                println("${t1.key} <-> ${t2.key}: $kld")
//
//            }
//    }

}
