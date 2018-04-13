package edu.unh.cs980.language

import edu.unh.cs980.defaultWhenNotFinite
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH
import edu.unh.cs980.misc.ConvexStepper
import edu.unh.cs980.misc.GradientDescenter
import edu.unh.cs980.smooth
import org.apache.commons.math3.distribution.NormalDistribution
import java.io.File
import java.lang.Double.max
import java.lang.Double.sum
import java.lang.Math.log10
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
        return (0 .. 10).map { param -> norm.cumulativeProbability(param.toDouble()) }
            .windowed(2)
            .map { (v1, v2) -> v2 - v1 }
    }

    fun analyzeDocument(text: String) {
        val filterPattern = "[\\d+]".toRegex()
        val filteredText = filterPattern.replace(text, "")
        val terms = AnalyzerFunctions.createTokenList(filteredText, analyzerType = ANALYZER_ENGLISH)

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

    fun analyzePartitionedDocument(text: String) =
            text.split(".")
                .filter { it.length > 10 }
                .forEach { splitText -> analyzeDocument(splitText) }


    fun normalizedByRestriction(restriction: Set<String>) {
//        val total = restriction.sumByDouble { word -> 0.5 * kernels[word]!!.frequency + 0.5 * kernels[word]!!.condFrequency }
//        restriction.forEach { word -> kernels[word]!!.normFreq = (0.5 * kernels[word]!!.frequency + 0.5 * kernels[word]!!.condFrequency) / total }
        val total = restriction.sumByDouble { word -> kernels[word]!!.frequency }
        restriction.forEach { word -> kernels[word]!!.normFreq = (kernels[word]!!.frequency / total) }

    }


    fun kld(other: KernelDist): Double {
        val restriction = kernels.keys.toSet()
            .intersect(other.kernels.keys.toSet())
//        normalizedByRestriction(restriction)
//        other.normalizedByRestriction(restriction)
        val restrictionTotal = restriction.sumByDouble { word -> kernels[word]!!.frequency }
        val kernelTotal = kernels.values.sumByDouble { kernel -> kernel.frequency }
        val coverage = (kernelTotal / restrictionTotal).defaultWhenNotFinite(1.0)

        return coverage * restriction.sumByDouble { word ->
            val k1 = kernels[word]!!
            val k2 = other.kernels[word]!!
//            (k1.frequency - k2.frequency) * log2((k1.frequency / k2.frequency))
//            (k1.frequency - k2.frequency) * log2((k1.frequency / k2.frequency))
//            (k1.frequency) * log2((k1.frequency / k2.frequency))
//            (k2.frequency) * log2((k2.frequency / k1.frequency))
            (k2.frequency - k1.frequency) * log2((k2.frequency / k1.frequency))
//            (k1.frequency - k2.frequency) * k1.kld(k2)
//            k2.kld(k1)
//        }
    } / max(1.0, restriction.size.toDouble())
    }

    fun normalizeKernels(tfidf: Map<String, Double>, useTFID: Boolean = true) {
        var total = kernels.values.sumByDouble { kernel -> kernel.frequency }
        kernels.values.forEach{ wordKernel -> wordKernel.normalize(tfidf, useTFID)}
        kernels.values.forEach { kernel -> kernel.frequency = kernel.frequency / total }

//        val condTotal = kernels.values.flatMap { kernel -> kernel.distribution.map { (k,v) -> k to v * kernel.frequency } }
//            .groupingBy { (word, _) -> word }
//            .fold(0.0) { acc, (_, conditionalFreq) -> acc + conditionalFreq }
//
//        condTotal.forEach { (k,v) -> kernels[k]!!.condFrequency = v }
//        total = kernels.values.sumByDouble { kernel -> kernel.condFrequency }
//        kernels.values.forEach { kernel -> kernel.condFrequency = kernel.condFrequency / total }
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

class KotlinKernelAnalyzer(val mean: Double, val std: Double, val partitioned: Boolean = false) {
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

    fun classify(text: String) {
        val textKernel = KernelDist(mean, std)
        textKernel.analyzeDocument(text)
        textKernel.normalizeKernels(mydf,false )


//        val results = topics.map { topic -> topic to (topic.value.kld(textKernel))   }
        val results = topics.map { topic -> topic to (textKernel.kld(topic.value))   }
        results.sortedBy { it.second }
            .forEach { (topic, kldDist) -> println("${topic.key}: $kldDist" ) }
    }

    fun mixTopics(tops: List<KernelDist>, weights: List<Double>): KernelDist {
        val newKernelDist = KernelDist(mean, std)
        val words = tops.zip(weights)
            .flatMap { (topic, weight) ->
                topic.kernels.values.map { kernel -> kernel.word to kernel.frequency * weight } }
            .groupingBy { (word, _) -> word }
            .fold(0.0) { acc, (_, freq) -> acc + freq }
            .toMap()

        val total = words.values.sum()

        words.forEach { (word, freq) ->
            val wordKernel = WordKernel(word)
            wordKernel.frequency = freq / total
            newKernelDist.kernels[word] = wordKernel
        }
        return newKernelDist
    }

    fun getTopicDomain() =
        File("paragraphs/")
            .listFiles()
            .flatMap { dir ->
                dir.listFiles()
                    .map { file ->
                        KernelDist(0.0, 3.0)
                            .apply { analyzeDocument(file.readText()) }
                            .apply { normalizeKernels(mydf, false) }
                    }
            }

    fun scoreTopicDomain(topic: KernelDist, domain: List<KernelDist>, smooth: Boolean = false): List<Double> {
        val scores = domain.map { k -> topic.kld(k) }
        val total = scores.sum()
        return scores.map { score -> score / total }
//            .let { results ->
//                val lowest = results.min()!!
//                val newResults = results.map { value -> value - lowest }
//                val newTotal = results.sum()
//                newResults.map { value -> value / newTotal }
//            }
            .let { results -> if (smooth) results.smooth() else results  }
    }


    fun compareDistances(domain: List<KernelDist>) {

        val results = topics.map { topic -> topic.key to scoreTopicDomain(topic.value, domain) }
        results.forEach { firstTopic ->
            results.filter { (name, _) -> name != firstTopic.first }
                .map { secondTopic ->
                    val kl = firstTopic.second.zip(secondTopic.second)
                        .sumByDouble { (v1, v2) ->
//                            (v1-v2) * log2(v1 / v2)
                            (v1) * log2(v1 / v2)
//                            (v2) * log2(v2 / v1)
                        }
                    secondTopic.first to kl
                }
                .sortedBy { it.second }
                .take(3)
                .forEach { result -> println("${firstTopic.first} -> ${result.first}: ${result.second}") }
        }
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


    fun classifyByDomainSimplex2(originScores: List<Double>, topicScores: List<List<Double>>) {
        val stepper = ConvexStepper(originScores, topicScores)
        stepper.searchSimplex()
        stepper.searchSimplex()
        stepper.searchSimplex()
        val weights = stepper.searchSimplex()
        val kld = stepper.kld()

        weights.zip(topics.keys).forEach { (weight, topic) ->
            println("$topic : $weight")
        }

        println("KLD: $kld\n\n")

    }



    fun classifyByDomain(text: String, domain: List<KernelDist>, smooth: Boolean = false) {
        val textKernel = KernelDist(mean, std)
        if (partitioned) textKernel.analyzePartitionedDocument(text) else textKernel.analyzeDocument(text)
        textKernel.normalizeKernels(mydf,false )
        val base = scoreTopicDomain(textKernel, domain)
        val filterDomained = domain.zip(base).filter { it.second > 0.0 }.map { it.first }
        val filteredBase = scoreTopicDomain(textKernel, filterDomained, smooth)

        val results = topics
            .map { topic ->
            val score = scoreTopicDomain(topic.value, filterDomained, smooth)
            val kl = filteredBase.zip(score)
                .sumByDouble { (v1, v2) ->
//                    val v1new = v1 * 0.5 + uniform  * 0.5
//                    val v2new = v2 * 0.5 + uniform * 0.5
//                    val vnew = if (v1 <= 0.0) 1.0 else v1
//                    (vnew) * log2(vnew / v2)
                    (v1) * log2(v1 / v2)
                }
//            val kl2 = base.zip(score)
//                .sumByDouble { (v1, v2) ->
//                    val vnew = if (v1 <= 0.0) 99999.0 else v1
//                    ((vnew) * log2(vnew / v2))
//                }
            Triple(topic.key, kl, score)
        }

        results.sortedBy { it.second }
            .forEach { result -> println("${result.first} : " + "%.10f".format(result.second)) }

        val (d1, d2, d3) = results.sortedBy { it.second }.take(3)

        (0 .. 10).flatMap { f1 ->
            (0 .. 10).flatMap { f2 ->
                (0 .. 10).map { f3 ->
                    val total = (f1 + f2 + f3).toDouble()
                    val r1 = f1.toDouble() / total
                    val r2 = f2.toDouble() / total
                    val r3 = f3.toDouble() / total

                    val mixed = d1.third.zip(d2.third.zip(d3.third)).map { (v1, pair) ->
                        val (v2, v3) = pair
                        v1 * r1 + v2 * r2 + v3 * r3
                    }

                    val kl = filteredBase.zip(mixed)
                        .sumByDouble { (v1, v2) ->
                            (v1) * log2(v1 / v2)
                        }
                    Pair(Triple(r1, r2, r3), kl)

                }
            }
        }.minBy { it.second }?.let { (trip, kl) ->
            val (r1, r2, r3) = trip
            println("($r1)${d1.first}, ($r2)${d2.first}, ($r3)${d3.first}: " + "%.10f".format(kl))
        }

//        (0 .. 40).map { base ->
//            val r1 = base.toDouble() * 0.025
//            val r2 = 1.0 - r1
//
//            val mixed = d1.third.zip(d2.third).map { (v1, v2) -> v1 * r1 + v2 * r2 }
//            val kl = filteredBase.zip(mixed)
//                .sumByDouble { (v1, v2) ->
//                    (v1) * log2(v1 / v2)
//                }
//            Triple(r1, r2, kl)
//        }.minBy { it.third }?.let { (r1, r2, kl) ->
//            println("($r1)${d1.first}, ($r2)${d2.first}: " + "%.10f".format(kl))
//        }
    }
}


fun main(args: Array<String>) {
    val analyzer = KotlinKernelAnalyzer(0.0, 3.0)
//    analyzer.analyzeTopicDirectories("paragraphs/")
    analyzer.analyzeTopicDirectories("pages/")
    analyzer.normalizeTopics(true)
//    val example = File("paragraphs/Computers/doc_1.txt").readText()
//    analyzer.compareDistances()

    val example =
            """
                Baking is a method of cooking food that uses prolonged dry heat, normally in an oven, but also in hot ashes, or on hot stones. The most common baked item is bread but many other types of foods are baked.[1] Heat is gradually transferred "from the surface of cakes, cookies, and breads to their centre. As heat travels through, it transforms batters and doughs into baked goods with a firm dry crust and a softer centre".[2] Baking can be combined with grilling to produce a hybrid barbecue variant by using both methods simultaneously, or one after the other. Baking is related to barbecuing because the concept of the masonry oven is similar to that of a smoke pit.

Because of historical social and familial roles, baking has traditionally been performed at home by women for domestic consumption and by men in bakeries and restaurants for local consumption. When production was industrialized, baking was automated by machines in large factories. The art of baking remains a fundamental skill and is important for nutrition, as baked goods, especially breads, are a common and important food, both from an economic and cultural point of view. A person who prepares baked goods as a profession is called a baker.
                """
//    analyzer.classifyByDomain(example)
//    analyzer.classify(example)


}
