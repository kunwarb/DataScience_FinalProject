package edu.unh.cs980.language

import edu.unh.cs980.*
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.GradientDescenter
import edu.unh.cs980.misc.PartitionDescenter
import edu.unh.cs980.paragraph.KotlinStochasticIntegrator
import info.debatty.java.stringsimilarity.*
import org.apache.commons.math3.distribution.NormalDistribution
import java.io.*

enum class ReductionMethod {
    REDUCTION_MAX_MAX, REDUCTION_AVERAGE, REDUCTION_MAX_AVERAGE
}

data class DescentData(val simFun: (String) -> Map<String, Double>,
                       val partitionFun: ((String) -> List<String>)? = null)

//class Sheaf(val name: String, val text: String, val cover: Sheaf? = null, useLetterGram: Boolean = false) {
class Sheaf(val name: String, val partitions: List<String>, val kld: Double = 1.0, val cover: Sheaf? = null) : Serializable {
    val measure = HashMap<String, Pair<Sheaf, Double>>()

    fun descend(descentData: List<DescentData>)  {
        val (simFun, partitionFun) = descentData.firstOrNull() ?: return
        val leftovers = descentData.subList(1, descentData.size)

        if (partitions.isEmpty()) {
            println("There's a hole in $name")
            println(cover!!.partitions)
            return
        }

        val (partitionSims, partitionTexts) = partitions.mapIndexed { index, text ->
            val pname = "${name}_$index"
            (pname to simFun(text)) to (pname to text) }.unzip()

        val coveringSim = partitionSims.flatMap { partition -> partition.second.entries }
            .groupingBy { it.key }
            .fold(0.0) { acc, entry -> acc!! + entry.value }
            .normalize()


        val (featureNames, weights) = (0 until 1).map {
            doPerturb(coveringSim, partitionSims = partitionSims)}
            .reduce { acc, list -> acc.zip(list).map { (f1, f2) -> f1.first to f2.second + f1.second } }
            .map { it.first to it.second / 1 }.unzip()

        val results = featureNames.zip(weights).toMap()
        val partitionTextMap = partitionTexts.toMap()


        val mixture =  TopicMixtureResult(results.toSortedMap(), kld)
        mixture.reportResults()

        partitionSims.mapNotNull { (name, _) -> results[name]?.to(name) }
            .filter { it.first > 0.0 }
            .map { (freq, sheafName) ->
                val partitionText = partitionTextMap[sheafName]!!
                val newPartitions = partitionFun?.invoke(partitionText) ?: emptyList()
                if (newPartitions.isEmpty()) { println("$sheafName") }
                measure[sheafName] = Sheaf(sheafName, newPartitions, kld, this) to freq
            }

        measure.forEach { (_, sheafMeasure) ->
            val sheaf = sheafMeasure.first
            sheaf.descend(leftovers)
        }

    }

    fun doPerturb(coveringSim: Map<String, Double>, partitionSims: List<Pair<String, Map<String, Double>>>): List<Pair<String, Double>> {
        val perturbations = perturb(300, coveringSim)
        val integrator = KotlinStochasticIntegrator(perturbations, partitionSims + (name to coveringSim), {null}, false)
        val integrals = integrator.integrate()

        val identityFreq = integrals.find { it.first == name }!!.second
        val (featureNames, featureFreqs) = integrals.filter { it.first != name }.unzip()

//        val stepper = PartitionDescenter(identityFreq, featureFreqs)
        val stepper = GradientDescenter(identityFreq, featureFreqs)
        val (weights, kld) = stepper.startDescent(1000)
        return featureNames.zip(weights)
    }

    fun transferMeasure(simFun: (String) -> Double): Pair<String, Double> {
        val similarityMeasure =
                partitions.map(simFun).average().defaultWhenNotFinite(0.0)
        return cover!!.ascend(name, similarityMeasure)
    }

    fun ascend(datumName: String, simMeasure: Double): Pair<String, Double> {
        val adjustedMeasure = measure[datumName]!!.second * simMeasure
        return cover?.ascend(name, adjustedMeasure) ?: name to adjustedMeasure
    }

    fun measurePartitions(simFun: (String) -> Double): Double  =
            partitions.map { partition -> simFun(partition) }.average()!!.defaultWhenNotFinite(0.00)

    fun transferDown(depthToGo: Int, simFun: (String) -> Double): Double {
        if (depthToGo == 0) return measurePartitions(simFun)

        return measure.values
            .sumByDouble { (sheaf, freq) ->
                sheaf.transferDown(depthToGo - 1, simFun) * freq
            }
    }


    fun retrieveLayer(depth: Int): List<Sheaf> =
        if (depth == 0) listOf(this)
        else measure.values.flatMap { (sheaf, _) -> sheaf.retrieveLayer(depth - 1) }

    companion object {
        fun perturb(nSamples: Int = 50, sims: Map<String, Double>): Pair<List<String>, List<List<Double>>> {
            val norm = NormalDistribution(sharedRand, 1.0, 0.5)
            val (kernelNames, kernelFreqs) = sims.toList().unzip()

            val perturbations = (0 until nSamples).map {
                norm.sample(kernelFreqs.size)
                    .toList()
                    .zip(kernelFreqs)
                    .map { (gaussian, kernelFreq) -> gaussian + kernelFreq  }.normalize() }

            return kernelNames to perturbations
        }

    }
}


class KotlinMetaKernelAnalyzer(val paragraphIndex: String) {
    val sheaves = arrayListOf<Sheaf>()
    private val sim = NormalizedLevenshtein()
//    private val sim = Jaccard(4)

    fun unigramFreq(text: String): Map<String, Double> =
        AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            .groupingBy(::identity)
            .eachCount()
            .normalize()

    fun bigramFrequency(text: String): Map<String, Double> =
            AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
                .windowed(2, partialWindows = false)
                .flatMap { (first, second) -> listOf("${first}_$second", "${second}_$first") }
                .groupingBy(::identity)
                .eachCount()
                .normalize()

    fun splitSentence(text: String): List<String> = text.split(".")
        .filter { it.length > 3 }

    fun splitWord(text: String): List<String> =
//            AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            "[ ]".toRegex().split(text)
                .filter { it.length > 3 }
                .toSet()
                .toList()


    fun letterFreq(windowSize: Int, text: String, partial: Boolean) =
        AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            .flatMap { token -> token.windowed(windowSize, partialWindows = partial) }
            .groupingBy(::identity)
            .eachCount()
            .normalize()

    fun bindFreq(windowSize: Int, partial: Boolean = false) = { text: String -> letterFreq(windowSize, text, partial)}

    fun singleLetterFreq(text: String) =
            text.map(Char::toString)
                .groupingBy(::identity)
                .eachCount()
                .normalize()

    fun evaluateMeasure(startingLayer: Int, measureLayer: Int, measure: (String) -> Double) =
            extractSheaves(startingLayer)
                .flatMap { (topName, sheafLayer) ->
                    sheafLayer.map { sheaf ->
                        sheaf.name to sheaf.transferDown(measureLayer - startingLayer, measure) } }



    private fun trainParagraph(topic: String, directory: File) {
        val paragraphs = directory.listFiles().map { file -> file.readText().toLowerCase().replace(",", " ") }
            .map { text -> splitSentence(text).map {  text ->
                AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH).joinToString(" ") }
                .joinToString(". ")
            }

        val sheaf = Sheaf(topic, paragraphs)
        val descentData = listOf(
//                DescentData(this::unigramFreq, this::splitSentence),
                DescentData(bindFreq(2), this::splitSentence),
                DescentData(bindFreq(1), this::splitWord),
                DescentData(bindFreq(1), ::listOf)
//                DescentData(this::unigramFreq, ::listOf)
//                DescentData(bindFreq(4), this::splitSentence),
//                DescentData(bindFreq(4), this::splitWord),
//                DescentData(bindFreq(4), ::listOf)
//                        DescentData(bindFreq(3), this::splitSentence),
//        DescentData(bindFreq(2), this::splitWord),
//        DescentData(bindFreq(1), ::listOf)
        )
        sheaf.descend(descentData)
        File("descent_data/").let { file -> if (!file.exists()) file.mkdir() }
        val f = FileOutputStream("descent_data/$topic")
        val of = ObjectOutputStream(f)
        of.writeObject(sheaf)
        of.close()
        f.close()


    }

    fun extractSheaves(level: Int) =
        sheaves.map { sheaf -> sheaf.name to sheaf.retrieveLayer(level) }

    fun trainParagraphs(filterList: List<String> = emptyList()) {
        File(paragraphIndex)
            .listFiles()
            .filter { file -> file.isDirectory && (filterList.isEmpty() || file.name in filterList)  }
            .forEach { file -> trainParagraph(file.name, file) }
    }

    fun extractTopicText(topicDir: File): List<String> =
        topicDir.listFiles().map { file -> file.readText().toLowerCase().replace(",", " ") }
            .map { text -> splitSentence(text).map {  text ->
            AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH).joinToString(" ") }
            .joinToString(". ") }


//    fun combinedTraining(filterList: List<String> = emptyList()) {
//        val allParas = File(paragraphIndex)
//            .listFiles()
//            .filter { file -> filterList.isEmpty() || file.name in filterList }
//            .flatMap { file -> extractTopicText(file) }
//
//        val sheaf = Sheaf("Combined", allParas)
//        val descentData = listOf(
//                DescentData(this::unigramFreq, this::splitSentence),
//                DescentData(bindFreq(3), this::splitWord),
////                DescentData(bindFreq(2), this::splitWord),
//                DescentData(bindFreq(2), ::listOf)
////                DescentData(this::singleLetterFreq, ::listOf)
////                DescentData(bindFreq(1), ::listOf)
//        )
//        sheaf.descend(descentData)
//        File("descent_data/").let { file -> if (!file.exists()) file.mkdir() }
//        val f = FileOutputStream("descent_data/Combined")
//        val of = ObjectOutputStream(f)
//        of.writeObject(sheaf)
//        of.close()
//        f.close()
//    }

    fun loadSheaves(sheafIndex: String, filterWords: List<String> = emptyList()) =
            File(sheafIndex)
                .listFiles()
                .filter { file -> filterWords.isEmpty() || file.name in filterWords }
                .map { file ->
                    val reader = ObjectInputStream(FileInputStream(file))
                    reader.readObject() as Sheaf}
                .onEach { sheaf -> sheaves += sheaf }


    fun inferMetric(text: String, startingLayer: Int, measureLayer: Int,
                    doNormalize: Boolean = true,
                    reductionMethod: ReductionMethod = ReductionMethod.REDUCTION_MAX_AVERAGE): TopicMixtureResult {

        val mySentence = bindSims(text, reductionMethod = reductionMethod)
        val res = evaluateMeasure(startingLayer, measureLayer, mySentence)
            .toMap()
            .run { if (doNormalize) normalize() else this }

        return TopicMixtureResult(res.toSortedMap(), 0.0)
    }

    fun averageSim(w1: String, w2: String): Double =
                (1.0 - sim.distance(w1, w2)).run { if (this < 0.8) 0.0 else this }


    fun productMaxMax(w1: List<String>, w2: List<String>): Double =
            w1.map { word1 -> w2.map { word2 -> averageSim(word1, word2) }.max()!! }.max()!!

    fun productMaxAverage(w1: List<String>, w2: List<String>): Double =
            w1.map { word1 -> w2.map { word2 -> averageSim(word1, word2) }.max()!! }.average()

    fun productAverage(w1: List<String>, w2: List<String>): Double =
            w1.flatMap { word1 -> w2.map { word2 -> averageSim(word1, word2) } }.average()

    fun bindSims(text: String, reductionMethod: ReductionMethod): (String) -> Double {
        val w1 = filterWords(text)
        return { otherWords ->
            val target = filterWords(otherWords)
            when (reductionMethod) {
                ReductionMethod.REDUCTION_MAX_MAX     -> productMaxMax(w1, target)
                ReductionMethod.REDUCTION_AVERAGE     -> productAverage(w1, target)
                ReductionMethod.REDUCTION_MAX_AVERAGE -> productMaxAverage(w1, target)
            }
        }
    }

}


fun filterWords(text: String) =
    AnalyzerFunctions.createTokenList(text.toLowerCase(),
            analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)



fun testStuff2(metaAnalyzer: KotlinMetaKernelAnalyzer) {
    val sheaves = metaAnalyzer.loadSheaves("descent_data/", filterWords = listOf("Medicine", "Cooking"))
    val text = """
        Philosophy (from Greek φιλοσοφία, philosophia, literally "love of wisdom"[1][2][3][4]) is the study of general and fundamental problems concerning matters such as existence, knowledge, values, reason, mind, and language.[5][6] The term was probably coined by Pythagoras (c. 570–495 BCE). Philosophical methods include questioning, critical discussion, rational argument, and systematic presentation.[7][8] Classic philosophical questions include: Is it possible to know anything and to prove it?[9][10][11] What is most real? Philosophers also pose more practical and concrete questions such as: Is there a best way to live? Is it better to be just or unjust (if one can get away with it)?[12] Do humans have free will?[13]
        A hospital is a health care institution providing patient treatment with specialized medical and nursing staff and medical equipment.[1] The best-known type of hospital is the general hospital, which typically has an emergency department to treat urgent health problems ranging from fire and accident victims to a heart attack. A district hospital typically is the major health care facility in its region, with large numbers of beds for intensive care and additional beds for patients who need long-term care. Specialised hospitals include trauma centres, rehabilitation hospitals, children's hospitals, seniors' (geriatric) hospitals, and hospitals for dealing with specific medical needs such as psychiatric treatment (see psychiatric hospital) and certain disease categories. Specialised hospitals can help reduce health care costs compared to general hospitals.[2]
        A hospital is a health care institution providing patient treatment with specialized medical and nursing staff and medical equipment.[1] The best-known type of hospital is the general hospital, which typically has an emergency department to treat urgent health problems ranging from fire and accident victims to a heart attack. A district hospital typically is the major health care facility in its region, with large numbers of beds for intensive care and additional beds for patients who need long-term care. Specialised hospitals include trauma centres, rehabilitation hospitals, children's hospitals, seniors' (geriatric) hospitals, and hospitals for dealing with specific medical needs such as psychiatric treatment (see psychiatric hospital) and certain disease categories. Specialised hospitals can help reduce health care costs compared to general hospitals.[2]
        A hospital is a health care institution providing patient treatment with specialized medical and nursing staff and medical equipment.[1] The best-known type of hospital is the general hospital, which typically has an emergency department to treat urgent health problems ranging from fire and accident victims to a heart attack. A district hospital typically is the major health care facility in its region, with large numbers of beds for intensive care and additional beds for patients who need long-term care. Specialised hospitals include trauma centres, rehabilitation hospitals, children's hospitals, seniors' (geriatric) hospitals, and hospitals for dealing with specific medical needs such as psychiatric treatment (see psychiatric hospital) and certain disease categories. Specialised hospitals can help reduce health care costs compared to general hospitals.[2]
            """

    val bb = """
        A kitchen is a room or part of a room used for cooking and food preparation in a dwelling or in a commercial establishment. A modern residential kitchen is typically equipped with a stove, a sink with hot and cold running water, a refrigerator, and it also has counters and kitchen cabinets arranged according to a modular design. Many households have a microwave oven, a dishwasher and other electric appliances. The main function of a kitchen is serving as a location for storing, cooking and preparing food (and doing related tasks such as dishwashing), but it may also be used for dining, entertaining and laundry.
    """
    val red = ReductionMethod.REDUCTION_AVERAGE
    val result = metaAnalyzer.inferMetric(text, 0, 3, doNormalize = true, reductionMethod = red)
    val result2 = metaAnalyzer.inferMetric(bb, 0, 3, doNormalize = true, reductionMethod = red)
    result.reportResults()
    result2.reportResults()
    println(result.manhattenDistance(result2))

}

fun showSheaves(metaAnalyzer: KotlinMetaKernelAnalyzer) {
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
    val res = sheaves
        .filter { sheaf -> sheaf.name == "Cuisine" }
        .map { sheaf ->
        sheaf.retrieveLayer(3)
            .map { s ->
                val parentScore = s.cover?.run { measure[s.name]!!.second }.toString()
                s.partitions.joinToString(" ") + ":\n\t$parentScore\n"  }
            .joinToString("\n")
        }

    println(res)

}


fun exploreSheaves(metaAnalyzer: KotlinMetaKernelAnalyzer) {
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
}

fun main(args: Array<String>) {
    val metaAnalyzer = KotlinMetaKernelAnalyzer("paragraphs/")
//    metaAnalyzer.trainParagraphs(listOf("Medicine", "Cooking"))
//    metaAnalyzer.trainParagraphs(listOf("Cooking"))
//    metaAnalyzer.combinedTraining(listOf("Medicine", "Cooking", "Warfare"))
    testStuff2(metaAnalyzer)
//    showSheaves(metaAnalyzer)
//    println(metaAnalyzer.extractSheaves(1))
}