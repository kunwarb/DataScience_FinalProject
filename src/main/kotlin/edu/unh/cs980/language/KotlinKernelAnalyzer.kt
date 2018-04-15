package edu.unh.cs980.language

import edu.unh.cs980.defaultWhenNotFinite
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_STANDARD
import edu.unh.cs980.misc.ConvexStepper
import edu.unh.cs980.misc.GradientDescenter
import edu.unh.cs980.sharedRand
import edu.unh.cs980.smooth
import org.apache.commons.math3.distribution.NormalDistribution
import java.io.File
import java.lang.Double.max
import java.lang.Double.sum
import java.lang.Math.log10
import java.lang.Math.sqrt
import kotlin.math.log2



class WordKernel(val word: String) {
    val distribution = HashMap<String, Double>()
    var frequency = 0.0
    var condFrequency = 0.0
    var normFreq = 0.0

    fun normalize(tfidf: Map<String, Double>, useTFID: Boolean = true) {
        var total = distribution.values.sum()
//        distribution.forEach { (k,v) -> distribution[k] = v / total }
        if (useTFID) {
            distribution.forEach { (k,v) -> distribution[k] = (v / total) * tfidf[k]!! }
        }

        total = distribution.values.sum()
        distribution.forEach { (k,v) -> distribution[k] = v / total }

    }
    fun getRestrictedDstribution(restriction: Set<String>): Map<String, Double> {
        val total = restriction.sumByDouble { word -> distribution[word]!! }
        return distribution.filterKeys { key -> key in restriction }
            .mapValues { (_,v) ->v / total }
    }

    fun kld(other: WordKernel): Double {
        val restriction = distribution.keys.toSet()
            .intersect(other.distribution.keys.toSet())

//        val dist1 = getRestrictedDstribution(restriction)
//        val dist2 = other.getRestrictedDstribution(restriction)

        return restriction.sumByDouble { word ->
//            val d1 = dist1[word]!!
//            val d2 = dist2[word]!!
            val d1 = distribution[word]!!
            val d2 = other.distribution[word]!!
//            d1 * other.frequency * log10((d1 * other.frequency) / (d2 * frequency))
//            (d2 - d1) * log2(d1 / d2)
            (d1) * log2(d1 / d2)
        } / max(1.0, restriction.size.toDouble())
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
            text.split(".")
                .filter { it.length > 3 }
                .forEach { splitText -> analyzeDocument(splitText) }


    fun normalizedByRestriction(restriction: Set<String>) {
//        val total = restriction.sumByDouble { word -> 0.5 * kernels[word]!!.frequency + 0.5 * kernels[word]!!.condFrequency }
//        restriction.forEach { word -> kernels[word]!!.normFreq = (0.5 * kernels[word]!!.frequency + 0.5 * kernels[word]!!.condFrequency) / total }
        val total = restriction.sumByDouble { word -> kernels[word]!!.frequency }
        restriction.forEach { word -> kernels[word]!!.normFreq = (kernels[word]!!.frequency / total) }

    }

    fun perturb(): Map<String, Double> {
        val perturbations = kernels.mapNotNull { (k,v) ->
//            val norm = NormalDistribution(1000.0 * v.frequency, 1.0 * v.frequency)
//            val sample = norm.sample()
//            if (sharedRand.nextDouble() <= sqrt(v.frequency)) k to sample else null
//            k to v.frequency * (sharedRand.nextDouble() * (sqrt(v.frequency) ))
//            k to v.frequency * (sharedRand.nextDouble())
//            k to v.frequency * (sharedRand.nextDouble() * (v.frequency)) * (sharedRand.nextDouble() * sqrt(v.frequency))
//            k to v.frequency * (sharedRand.nextDouble() * (v.frequency)) + (sharedRand.nextDouble() * sqrt(v.frequency))

            // interaction
            k to v.frequency * (sharedRand.nextDouble() * (v.frequency)) + v.frequency * (sharedRand.nextDouble() * sqrt(v.frequency))

//            k to v.frequency * (sharedRand.nextDouble() * (v.frequency))
//            if (sample <= 0.0) null else k to sample
        }

        val total = perturbations.sumByDouble { it.second }
        return perturbations.map { it.first to it.second / total }.toMap()
//        return perturbations.toMap()
    }

    fun kld(other: Map<String, Double>): Double {
        val restriction = kernels.keys.toSet()
            .intersect(other.keys.toSet())

        val restrictionTotal = restriction.sumByDouble { word -> kernels[word]!!.frequency }
        val kernelTotal = kernels.values.sumByDouble { kernel -> kernel.frequency }
        val coverage = (kernelTotal / restrictionTotal).defaultWhenNotFinite(1.0)

        return coverage * restriction.sumByDouble { word ->
            val k1 = kernels[word]!!
            val k2 = other[word]!!
            (k2 - k1.frequency) * log2((k2 / k1.frequency))
        } / max(1.0, restriction.size.toDouble())
    }

    fun kld(other: KernelDist, corpus: HashMap<String, WordKernel>): Double {
//        val restriction = kernels.keys.toSet()
//            .intersect(other.kernels.keys.toSet())

//        val restrictionTotal = restriction.sumByDouble { word -> kernels[word]!!.frequency }
//        val kernelTotal = kernels.values.sumByDouble { kernel -> kernel.frequency }
//        val coverage = (kernelTotal / restrictionTotal).defaultWhenNotFinite(1.0)

//        val kernelTotal = kernels.values.sumByDouble { kernel -> kernel.frequency }
        val union = kernels.keys.union(other.kernels.keys)
//            .filter { key -> key in kernels || key in other.kernels  }

        return 1.0 * union.sumByDouble { word ->
            var k1 = kernels[word]?.frequency ?: 1 / kernels.size.toDouble()
            var k2 = other.kernels[word]?.frequency ?: 1 / other.kernels.size.toDouble()
            val c = corpus[word]?.frequency ?: 0.00

            k1 = 0.5 * k1 + 0.5 * c * (1 / kernels.size.toDouble())
            k2 = 0.5 * k2 + 0.5 * c * (1 / other.kernels.size.toDouble())
            (k2 - k1) * log2((k2 / k1))
        } / union.size
    }

//    fun kld(other: KernelDist, corpus: HashMap<String, WordKernel>): Double {
//        val restriction = kernels.keys.toSet()
//            .intersect(other.kernels.keys.toSet())
//
//        val restrictionTotal = restriction.sumByDouble { word -> kernels[word]!!.frequency }
//        val kernelTotal = kernels.values.sumByDouble { kernel -> kernel.frequency }
//        val coverage = (kernelTotal / restrictionTotal).defaultWhenNotFinite(1.0)
//
//        return coverage * restriction.sumByDouble { word ->
//            val k1 = kernels[word]!!
//            val k2 = other.kernels[word]!!
//            (k2.frequency - k1.frequency) * log2((k2.frequency / k1.frequency))
//        } / max(1.0, restriction.size.toDouble())
//    }

    fun normalizeKernels(tfidf: Map<String, Double>, useTFID: Boolean = true) {
        var total = kernels.values.sumByDouble { kernel -> kernel.frequency }
        kernels.values.forEach{ wordKernel -> wordKernel.normalize(tfidf, useTFID)}
        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / total }
    }

    fun normalizeByCond() {
        var total = kernels.values.sumByDouble { kernel -> kernel.frequency }
        kernels.values.forEach{ wordKernel -> wordKernel.normalize(emptyMap(), false)}
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
}

class KotlinKernelAnalyzer(val mean: Double, val std: Double, val corpus: HashMap<String, WordKernel>,
                           val partitioned: Boolean = false) {
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
            .forEach { file ->  if (partitioned) kernelDist.analyzePartitionedDocument(file.readText())
                                else kernelDist.analyzeDocument(file.readText())}
    }

    //    fun normalizeTopics() = topics.values.forEach(KernelDist::normalizeKernels)
    fun normalizeTopics(useTFID: Boolean = true) {
        val tfidf = topics.flatMap { topic -> topic.value.kernels.values }
            .groupingBy(WordKernel::word)
            .fold(0.0, { acc, kernel -> acc + kernel.frequency})
            .toMap()
        mydf = tfidf

        topics.values.forEach { topic -> topic.normalizeKernels(tfidf, useTFID) }
    }


    fun scoreTopicDomain(topic: KernelDist, domain: List<KernelDist>, smooth: Boolean = false): List<Double> {
        val scores = domain.map { k -> topic.kld(k, corpus) }
        val total = scores.sum()
        return scores.map { score -> score / total }
            .let { results -> if (smooth) results.smooth() else results  }
    }

    fun scoreTopicDomain2(topic: KernelDist, domain: List<Map<String, Double>>, smooth: Boolean = false): List<Double> {
        val scores = domain.map { k -> topic.kld(k) }
        val total = scores.sum()
        return scores.map { score -> score / total }
            .let { results -> if (smooth) results.smooth() else results  }
    }



    fun classifyByDomainSimplex(text: String, domain: List<KernelDist>, smooth: Boolean = false) {
        val textKernel = KernelDist(mean, std)
        if (partitioned) textKernel.analyzePartitionedDocument(text) else textKernel.analyzeDocument(text)
        textKernel.normalizeKernels(mydf,false )
        val base = scoreTopicDomain(textKernel, domain)
        val filterDomained = domain.zip(base).filter { it.second > 0.0 }.map { it.first }
        val filteredBase = scoreTopicDomain(textKernel, filterDomained, smooth)
        println(filteredBase)


        val topicDistributions = topics
            .map { topic -> scoreTopicDomain(topic.value, filterDomained, smooth) }


//        val stepper = ConvexStepper(filteredBase, topicDistributions)
//        stepper.searchSimplex()
//        val weights = stepper.searchSimplex(10)
//
//        val kld = stepper.kld()
        // 7497872310634673
        val stepper = GradientDescenter(filteredBase, topicDistributions)
        val (weights, kld) = stepper.startDescent(800)

        weights.zip(topics.keys)
            .sortedByDescending { it.first }
            .forEach { (weight, topic) ->
            println("$topic : $weight")
        }

        println("KLD: $kld\n\n")

    }


    fun classifyByDomainSimplex2(text: String, domain: List<Map<String, Double>>, smooth: Boolean = false) {
        val textKernel = KernelDist(mean, std)
        if (partitioned) textKernel.analyzePartitionedDocument(text) else textKernel.analyzeDocument(text)
        textKernel.normalizeKernels(mydf,false )

        val base = scoreTopicDomain2(textKernel, domain)
        val filterDomained = domain.zip(base).filter { it.second > 0.0 }.map { it.first }
        val filteredBase = scoreTopicDomain2(textKernel, filterDomained, smooth)
        println(filteredBase)


        val topicDistributions = topics
            .map { topic -> scoreTopicDomain2(topic.value, filterDomained, smooth) }

        val stepper = GradientDescenter(filteredBase, topicDistributions)
        val (weights, kld) = stepper.startDescent(800)

        weights.zip(topics.keys)
            .sortedByDescending { it.first }
            .forEach { (weight, topic) ->
                println("$topic : $weight")
            }

        println("KLD: $kld\n\n")

    }



}


fun main(args: Array<String>) {

}
