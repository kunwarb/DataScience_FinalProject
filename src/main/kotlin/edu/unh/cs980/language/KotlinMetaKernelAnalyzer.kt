package edu.unh.cs980.language

import edu.unh.cs980.defaultWhenNotFinite
import edu.unh.cs980.identity
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.PartitionDescenter
import edu.unh.cs980.normalize
import edu.unh.cs980.paragraph.KotlinStochasticIntegrator
import edu.unh.cs980.sharedRand
import info.debatty.java.stringsimilarity.Cosine
import info.debatty.java.stringsimilarity.Jaccard
import info.debatty.java.stringsimilarity.NormalizedLevenshtein
import org.apache.commons.math3.distribution.NormalDistribution
import java.io.*
import kotlin.math.log2


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


        val perturbations = perturb(1000, coveringSim)
        val integrator = KotlinStochasticIntegrator(perturbations, partitionSims + (name to coveringSim), {null}, false)
        val integrals = integrator.integrate()

        val identityFreq = integrals.find { it.first == name }!!.second
        val (featureNames, featureFreqs) = integrals.filter { it.first != name }.unzip()

        val stepper = PartitionDescenter(identityFreq, featureFreqs)
        val (weights, kld) = stepper.startDescent(500)

        val results = featureNames.zip(weights).toMap()
        val partitionTextMap = partitionTexts.toMap()
        val mixture =  TopicMixtureResult(results.toSortedMap(), kld)
        mixture.reportResults()
        if (kld.isNaN()) {
            println(name)
            println(partitions)
        }

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
        partitions.map { partition -> simFun(partition) }.average().defaultWhenNotFinite(0.0)

    fun transferDown1(simFun: (String) -> Double): Double {
        if (measure.isEmpty()) return measurePartitions(simFun)

        val (mFreq, curFreq) = measure.values
            .map { (sheaf, freq) -> sheaf.transferDown1(simFun) to freq }.unzip()

        return mFreq.normalize().zip(curFreq).sumByDouble { (v1, v2) -> v1 * log2(v1 / (if (v2 == 0.0) 0.0001 else v2)) }
    }

    fun transferDown2(simFun: (String) -> Double): Double {
        return measurePartitions(simFun) + measure.values.sumByDouble { (partition, freq) ->
            partition.transferDown2(simFun) * freq }
    }

    fun retrieveLayer(depth: Int): List<Sheaf> =
        if (depth == 0) listOf(this)
        else measure.values.flatMap { (sheaf, _) -> sheaf.retrieveLayer(depth - 1) }

    companion object {
        fun perturb(nSamples: Int = 50, sims: Map<String, Double>): Pair<List<String>, List<List<Double>>> {
            val norm = NormalDistribution(sharedRand, 1.0, 0.0001)
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

    fun unigramFreq(text: String): Map<String, Double> =
        AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            .groupingBy(::identity)
            .eachCount()
            .normalize()

    fun splitSentence(text: String): List<String> = text.split(".")
        .filter { it.length > 3 }

    fun splitWord(text: String): List<String> =
            "[ ]".toRegex().split(text)
                .filter { it.length > 3 }


    fun letterFreq(windowSize: Int, text: String) =
        AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            .flatMap { token -> token.windowed(windowSize, partialWindows = false) }
            .groupingBy(::identity)
            .eachCount()
            .normalize()

    fun bindFreq(windowSize: Int) = { text: String -> letterFreq(windowSize, text)}

    fun singleLetterFreq(text: String) =
            text.map(Char::toString)
                .groupingBy(::identity)
                .eachCount()
                .normalize()



    private fun trainParagraph(topic: String, directory: File) {
//        val paragraphs =
//                directory.listFiles() .mapIndexed { index, file -> "${topic}_$index" to file.readText() }
        val paragraphs = directory.listFiles().map { file -> file.readText().toLowerCase().replace(",", " ") }
            .map { text -> splitSentence(text).map {  text ->
                AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH).joinToString(" ") }
                .joinToString(". ")
            }

        val sheaf = Sheaf(topic, paragraphs)
        val descentData = listOf(
//                DescentData(this::unigramFreq, this::splitSentence),
                DescentData(bindFreq(3), this::splitSentence),
                DescentData(bindFreq(2), this::splitWord),
//                DescentData(this::singleLetterFreq, ::listOf)
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


    fun trainParagraphs(filterList: List<String> = emptyList()) {
        File(paragraphIndex)
            .listFiles()
            .filter { file -> file.isDirectory && (filterList.isEmpty() || file.name in filterList)  }
            .forEach { file -> trainParagraph(file.name, file) }
    }

    fun loadSheaves(sheafIndex: String) =
            File(sheafIndex)
                .listFiles()
                .map { file ->
                    val reader = ObjectInputStream(FileInputStream(file))
                    reader.readObject() as Sheaf
                }



}

fun averageSim(w1: String, w2: String): Double =
    1.0 - Jaccard(2).distance(w1, w2)
//    NormalizedLevenshtein().similarity(w1, w2)

fun filterWords(text: String) =
    AnalyzerFunctions.createTokenList(text.toLowerCase(),
            analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)

fun tokenizedSim(w1: String, w2: String): Double {
    val f1 = filterWords(w1)
    val f2 = filterWords(w2)
    return f1.flatMap { word1 -> f2.map { word2 -> averageSim(word1, word2).defaultWhenNotFinite(0.0) } }
        .average()!!.defaultWhenNotFinite(0.0)
}


fun bindSims(text: String): List<(String) -> Double> {
    val splitreg = "[ ]".toRegex()
    val words = splitreg.split(text.toLowerCase())
    return words.map { word -> {otherWord: String -> averageSim(word, otherWord)} }
//    return words.map { word -> {otherWord: String -> 1.0} }
}

//fun bindSims2(text: String): List<(String) -> Double> {
//    return {w2: String -> tokenizedSim(text, w2) }
//}

fun createWordAverage(text: String): (String) -> Double {
    return { w: String -> tokenizedSim(text, w) }

}

fun testStuff(metaAnalyzer: KotlinMetaKernelAnalyzer) {
//    val sentence = "Tanks tanks tanks"
//    val mySentence = listOf(createWordAverage(sentence))
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
//    val mySentence = bindSims("Here is some  and some medicine and some people")
    val text = """
        Medicine is the science and practice of the diagnosis, treatment, and prevention of disease. Medicine encompasses a variety of health care practices evolved to maintain and restore health by the prevention and treatment of illness. Contemporary medicine applies biomedical sciences, biomedical research, genetics, and medical technology to diagnose, treat, and prevent injury and disease, typically through pharmaceuticals or surgery, but also through therapies as diverse as psychotherapy, external splints and traction, medical devices, biologics, and ionizing radiation, amongst others.[1]

Medicine has existed for thousands of years, during most of which it was an art (an area of skill and knowledge) frequently having connections to the religious and philosophical beliefs of local culture. For example, a medicine man would apply herbs and say prayers for healing, or an ancient philosopher and physician would apply bloodletting according to the theories of humorism. In recent centuries, since the advent of modern science, most medicine has become a combination of art and science (both basic and applied, under the umbrella of medical science). While stitching technique for sutures is an art learned through practice, the knowledge of what happens at the cellular and molecular level in the tissues being stitched arises through science.

Prescientific forms of medicine are now known as traditional medicine and folk medicine. They remain commonly used with or instead of scientific medicine and are thus called alternative medicine. For example, evidence on the effectiveness of acupuncture is "variable and inconsistent" for any condition,[2] but is generally safe when done by an appropriately trained practitioner.[3] In contrast, treatments outside the bounds of safety and efficacy are termed quackery.
            """
    val mySentence = bindSims(text)
//    val mySentence = bindSims("Philosophy is an old time historical traditional thing")

//    val mySentence = listOf({ w: String -> tokenizedSim(sentence, w)})

    sheaves.flatMap { sheaf -> sheaf.retrieveLayer(3) }
        .map { sheaf ->
//            sheaf.transferMeasure { word -> mySentence.map { myword -> myword(word) }.max()!!.defaultWhenNotFinite(0.0) } }
            sheaf.transferMeasure { word -> mySentence.map {
                myword -> myword(word) }.max()!!.defaultWhenNotFinite(0.0).let { res -> if (res > 0.8) res else 0.0 } } }
        .groupBy { it.first }
//        .mapValues { (key, values) -> values.sumByDouble { it.second } / values.size }
//        .mapValues { (key, values) -> values.sumByDouble { it.second }  }
        .mapValues { (key, values) -> values.sumByDouble { it.second }  }
//        .fold(0.0) { cur, acc -> cur + acc.second}
        .filter { it.value.isFinite() }
//        .filter { it.key == "Medicine" }
//        .normalize()
        .entries.sortedByDescending { it.value }
        .forEach(::println)

//        .map { it.partitions.joinToString("|||") }
//        .joinToString("\n------\n")
//        .apply(::println)

}

fun showSheaves(metaAnalyzer: KotlinMetaKernelAnalyzer) {
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
    val res = sheaves
        .filter { sheaf -> sheaf.name == "Cooking" }
        .map { sheaf ->
        sheaf.retrieveLayer(3)
            .map { s -> s.partitions.joinToString(" ")  }.joinToString("\n") }

    println(res)

}


fun exploreSheaves(metaAnalyzer: KotlinMetaKernelAnalyzer) {
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
}

fun main(args: Array<String>) {
    val metaAnalyzer = KotlinMetaKernelAnalyzer("paragraphs/")
//    metaAnalyzer.trainParagraphs(listOf("Medicine", "Cooking"))
    testStuff(metaAnalyzer)
//    showSheaves(metaAnalyzer)
}