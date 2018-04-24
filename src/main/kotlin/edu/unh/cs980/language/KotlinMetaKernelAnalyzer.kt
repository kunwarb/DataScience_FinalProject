package edu.unh.cs980.language

import edu.unh.cs980.*
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.GradientDescenter
import edu.unh.cs980.paragraph.KotlinStochasticIntegrator
import info.debatty.java.stringsimilarity.*
import org.apache.commons.math3.distribution.NormalDistribution
import java.io.*
import java.lang.Math.max
import kotlin.math.log2

// Method to use for integrating text from query/paragraphs against sheaves
enum class ReductionMethod {
    REDUCTION_MAX_MAX, REDUCTION_AVERAGE, REDUCTION_SMOOTHED_THRESHOLD
}


enum class AscentType {
    ASCENT_SUM, ASCENT_MAX, ASCENT_THRESHOLD_SUM, ASCENT_THRESHOLD_MAX
}

// Desribes method to partition sheaf and a defines a measure over partitions
data class DescentData(val simFun: (String) -> Map<String, Double>,
                       val partitionFun: ((String) -> List<String>)? = null)


/**
 * Class: Sheaf
 * Desc: Describes a space covering another space. In the context of these experiment, a sheaf is a topic, paragraph,
 *       sentence, or word. We "descend" by describing how a sheaf can be approximated by its partitions. For example,
 *       the best linear combination of paragraphs that describes the unigram frequencies in a topic, and then the
 *       best linear combination of sentences in these paragraphs that describe k-mer frequencies, etc.
 *
 */
class Sheaf(val name: String, val partitions: List<String>, val kld: Double = 1.0, val cover: Sheaf? = null) : Serializable {
    val measure = HashMap<String, Pair<Sheaf, Double>>()



    fun descend(descentData: List<DescentData>)  {
        val (simFun, partitionFun) = descentData.firstOrNull() ?: return
        val leftovers = descentData.subList(1, descentData.size)

        if (partitions.isEmpty()) {
            println("There's a hole in $name, skipping...")
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


        val (featureNames, weights) = (0 until 3).map {
            doPerturb(coveringSim, partitionSims = partitionSims)}
            .reduce { acc, list -> acc.zip(list).map { (f1, f2) -> f1.first to f2.second + f1.second } }
            .map { it.first to it.second / 3 }.unzip()

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
        val perturbations = perturb(100, coveringSim)
        val integrator = KotlinStochasticIntegrator(perturbations, partitionSims + (name to coveringSim), {null}, false)
        val integrals = integrator.integrate()

        val identityFreq = integrals.find { it.first == name }!!.second
        val (featureNames, featureFreqs) = integrals.filter { it.first != name }.unzip()

        val stepper = GradientDescenter(identityFreq, featureFreqs)
        val (weights, kld) = stepper.startDescent(800)
        return featureNames.zip(weights)
    }


    fun measurePartitions(simFun: (String) -> Double): Double  =
            partitions.map { partition -> simFun(partition) }.average()!!.defaultWhenNotFinite(0.00) *
                    (1 / log2(cover!!.partitions.size.toDouble())).defaultWhenNotFinite(0.0)

    fun transferDown(depthToGo: Int, simFun: (String) -> Double,
                     ascentType: AscentType = AscentType.ASCENT_SUM): Double {
        if (depthToGo == 0) return measurePartitions(simFun)


        val results = measure.values.map { (sheaf, freq) ->
            if (freq < 0.1) 0.0
            else sheaf.transferDown(depthToGo - 1, simFun) * freq.defaultWhenNotFinite(1.0)
        }

        val total = results.sum()
        val maximum = results.max() ?: 0.0
        return when (ascentType) {
            AscentType.ASCENT_SUM -> total
            AscentType.ASCENT_MAX -> maximum
            AscentType.ASCENT_THRESHOLD_MAX -> if (maximum < 1/(max(1.0, partitions.size.toDouble())))
                                               return 0.00 else return maximum
            AscentType.ASCENT_THRESHOLD_SUM -> if (total < 1/(max(1.0, partitions.size.toDouble())))
                                               return 0.00 else return total
        }

//        if (total < 1/(max(1.0, partitions.size.toDouble()))) return 0.00 else return total
    }


    fun retrieveLayer(depth: Int): List<Sheaf> =
        if (depth == 0) listOf(this)
        else measure.values.flatMap { (sheaf, _) -> sheaf.retrieveLayer(depth - 1) }

    companion object {
        fun perturb(nSamples: Int = 50, sims: Map<String, Double>): Pair<List<String>, List<List<Double>>> {
            val norm = NormalDistribution(sharedRand, 1.0, 0.001)
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
            AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
                .windowed(2, partialWindows = false)
                .flatMap { (first, second) -> listOf("${first}_$second", "${second}_$first") }
                .groupingBy(::identity)
                .eachCount()
                .normalize()

    fun splitSentence(text: String): List<String> = text.split(".")
        .filter { it.length > 2 }

    fun splitWord(text: String): List<String> =
//            AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            "[ ]".toRegex().split(text)
                .filter { it.length > 2 }
                .toSet()
                .toList()


    fun letterFreq(windowSize: Int, text: String, partial: Boolean) =
        AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)
            .flatMap { token -> token.windowed(windowSize, partialWindows = partial) }
            .groupingBy(::identity)
            .eachCount()
            .normalize()


    fun bindFreq(windowSize: Int, partial: Boolean = false) = { text: String -> letterFreq(windowSize, text, partial)}


    fun evaluateMeasure(startingLayer: Int, measureLayer: Int, measure: (String) -> Double, filterList: List<String>,
                        ascentType: AscentType) =
            extractSheaves(startingLayer)
                .filter { (topName, _) -> filterList.isEmpty() || topName in filterList }
                .flatMap { (topName, sheafLayer) ->
                    sheafLayer.map { sheaf ->
                        sheaf.name to sheaf.transferDown(measureLayer - startingLayer, measure, ascentType) } }



    private fun trainParagraph(topic: String, directory: File) {
        val paragraphs = directory.listFiles().map { file -> file.readText().toLowerCase().replace(",", " ") }
            .map { text -> splitSentence(text).map {  text ->
                AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED).joinToString(" ") }
                .joinToString(". ")
            }

        val sheaf = Sheaf(topic, paragraphs)
        val descentData = listOf(
                DescentData(bindFreq(2), this::splitSentence),
                DescentData(bindFreq(2), this::splitWord),
                DescentData(bindFreq(2), ::listOf)
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

    fun combinedTraining(filterList: List<String>) {
        val pars = File(paragraphIndex)
            .listFiles()
            .filter { file -> filterList.isEmpty() || file.name in filterList }
            .flatMap { file -> extractTopicText(file) }

        val sheaf = Sheaf("Combined", pars)
        val descentData = listOf(
                DescentData(bindFreq(2), this::splitSentence),
                DescentData(bindFreq(2), this::splitWord),
                DescentData(bindFreq(2), ::listOf)
        )
        sheaf.descend(descentData)
        File("descent_data/").let { file -> if (!file.exists()) file.mkdir() }
        val f = FileOutputStream("descent_data/Combined")
        val of = ObjectOutputStream(f)
        of.writeObject(sheaf)
        of.close()
        f.close()
    }

    fun extractTopicText(topicDir: File): List<String> =
        topicDir.listFiles().map { file -> file.readText().toLowerCase().replace(",", " ") }
            .map { text -> splitSentence(text).map {  text ->
            AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED).joinToString(" ") }
            .joinToString(". ") }



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
                    reductionMethod: ReductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    ascentType: AscentType = AscentType.ASCENT_SUM,
                    filterList: List<String> = emptyList()): TopicMixtureResult {

        val mySentence = bindSims(text, reductionMethod = reductionMethod)
        val res = evaluateMeasure(startingLayer, measureLayer, mySentence, filterList, ascentType = ascentType)
            .toMap()
            .run { if (doNormalize) normalize() else this }

        return TopicMixtureResult(res.toSortedMap(), 0.0)
    }

    fun averageSim(w1: String, w2: String): Double =
                (1.0 - sim.distance(w1, w2)).run { if (this < 0.8) 0.0 else this }


    fun productMaxMax(w1: List<String>, w2: List<String>): Double =
            w1.map { word1 -> w2.map { word2 -> averageSim(word1, word2) }.max()!! }.max()!!


    fun productSmoothThreshold(w1: List<String>, w2: String): Double {
        val results = w1.map { word1 -> averageSim(word1, w2) }
        val misses = results.count { it == 0.0 }
        val hits = results.sum()
        val sizeSmooth = 1.0 + 200 / (1.0 + misses.toDouble())

        return hits * sizeSmooth

    }

    fun productAverage(w1: List<String>, w2: List<String>): Double =
            w1.flatMap { word1 -> w2.map { word2 -> averageSim(word1, word2) } }.average()



    fun bindSims(text: String, reductionMethod: ReductionMethod): (String) -> Double {
        val w1 = filterWords(text)
        return { w2 ->
            when (reductionMethod) {
                ReductionMethod.REDUCTION_MAX_MAX            -> productMaxMax(w1, listOf(w2))
                ReductionMethod.REDUCTION_AVERAGE            -> productAverage(w1, listOf(w2))
                ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD -> productSmoothThreshold(w1, w2)
            }
        }
    }

}


fun filterWords(text: String) =
    AnalyzerFunctions.createTokenList(text.toLowerCase(),
            analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH_STOPPED)



fun testStuff2(metaAnalyzer: KotlinMetaKernelAnalyzer) {
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
//    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
    val text = """
        Instead of table service, there are food-serving counters/stalls, either in a line or allowing arbitrary walking paths. Customers take the food that they desire as they walk along, placing it on a tray. In addition, there are often stations where customers order food and wait while it is prepared, particularly for items such as hamburgers or tacos which must be served hot and can be immediately prepared. Alternatively, the patron is given a number and the item is brought to their table. For some food items and drinks, such as sodas, water, or the like, customers collect an empty container, pay at the check-out, and fill the container after the check-out. Free unlimited second servings are often allowed under this system. For legal purposes (and the consumption patterns of customers), this system is rarely, if at all, used for alcoholic beverages in the US.
            """

    val bb = """
        food
    """
    val red = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD
    val ascentType = AscentType.ASCENT_THRESHOLD_SUM
    val (time, result) = withTime {
        metaAnalyzer.inferMetric(text, 0, 3, doNormalize = true, reductionMethod = red,
                ascentType = ascentType) }
    val result2 = metaAnalyzer.inferMetric(bb, 0, 3, doNormalize = true, reductionMethod = red,
            ascentType = ascentType)

    result.reportResults()
    result2.reportResults()
    println(result.manhattenDistance(result2))
    println("TIME: $time")
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



fun main(args: Array<String>) {
    val metaAnalyzer = KotlinMetaKernelAnalyzer("paragraphs/")
//    metaAnalyzer.trainParagraphs()
    testStuff2(metaAnalyzer)
}