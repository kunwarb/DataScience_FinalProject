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

    fun normalize(tfidf: Map<String, Double>) {
        val total = distribution.values.sum()
//        distribution.forEach { (k,v) -> distribution[k] = v / total }
        distribution.forEach { (k,v) -> distribution[k] = (v / total) * tfidf[k]!! }
    }

    fun kld(other: WordKernel): Double {
        val restriction = distribution.keys.toSet()
            .intersect(other.distribution.keys.toSet())



        return restriction.sumByDouble { word ->
            val d1 = distribution[word]!!
            val d2 = other.distribution[word]!!
//            d1 * other.frequency * log10((d1 * other.frequency) / (d2 * frequency))
            d1 * log10(d1 / d2)
        } / max(1.0, restriction.size.toDouble())
    }

}

class KernelDist() {
    val kernels = HashMap<String, WordKernel>()

    val distMappings = getDistMappings(0.0, 3.0)

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


        return restriction.sumByDouble { word ->
            val k1 = kernels[word]!!
            val k2 = other.kernels[word]!!
//            k1.frequency * k1.kld(k2)
            k1.kld(k2) * (k2.frequency / k1.frequency)
        } / max(1.0, restriction.size.toDouble())
    }

    fun normalizeKernels(tfidf: Map<String, Double>) {
        val total = kernels.values.sumByDouble { kernel -> kernel.frequency }
//        kernels.values.forEach(WordKernel::normalize)
        kernels.values.forEach{ wordKernel -> wordKernel.normalize(tfidf)}
        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / total }
//        kernels.values.forEach { kernel -> kernel.frequency = (kernel.frequency / total) / tfidf[kernel.word]!! }
    }
}

class KotlinKernelAnalyzer() {
    val topics = HashMap<String, KernelDist>()

    fun analyzeTopicDirectories(mainDirectory: String) {
        File(mainDirectory)
            .listFiles()
            .filter(File::isDirectory)
            .forEach { file ->  analyzeTopic(file.name, file)}
    }

    private fun analyzeTopic(topic: String, directory: File) {
        val kernelDist = KernelDist()
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

        topics.values.forEach { topic -> topic.normalizeKernels(tfidf) }
    }
}


fun main(args: Array<String>) {
    var prev = 0.5
    val norm = NormalDistribution(0.0, 3.0)
//    (0 until 8).forEach { param ->
//        val myDist = norm.cumulativeProbability(param.toDouble())
//        val result = myDist - prev
//        prev = myDist
//        println("$param: $result")
//        println("$param: ${norm.density(param.toDouble())}")
//    }
    val analyzer = KotlinKernelAnalyzer()
    analyzer.analyzeTopicDirectories("paragraphs/")
    analyzer.normalizeTopics()
    analyzer.topics.forEach { t1 ->
        analyzer.topics
            .map { t2 -> Triple(t1, t2, (t1.value.kld(t2.value) + t2.value.kld(t1.value)) ) }
            .sortedByDescending { it.third }
            .forEach { (t1, t2, kld) ->
                println("${t1.key} <-> ${t2.key}: $kld")

            }
    }
//    analyzer.topics.entries.windowed(2, 1)
//        .forEach { (t1, t2) ->
//            println("${t1.key} <-> ${t2.key}")
//            println(t1.value.kld(t2.value) + t2.value.kld(t1.value))
////            println("${t2.key} -> ${t1.key}")
////            println(t2.value.kld(t1.value))
////            println("${t1.key} -> ${t1.key}")
////            println(t1.value.kld(t1.value))
////            println()
//        }
//    analyzer.topics.forEach { (topicName, kernelDist) ->
//        println("======= $topicName ======= \n\n")
//        val best = kernelDist.kernels.maxBy { (word, kernel) -> kernel.distribution.values.sum() }
//        best?.let { (word, dist) ->
//            println("\n" + word)
//            dist.distribution.entries.sortedByDescending { it.value }.forEach(::println)
//        }
//    }

}
