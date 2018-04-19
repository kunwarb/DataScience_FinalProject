package edu.unh.cs980.language

import edu.unh.cs980.identity
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.misc.PartitionDescenter
import edu.unh.cs980.normalize
import edu.unh.cs980.paragraph.KotlinStochasticIntegrator
import edu.unh.cs980.sharedRand
import org.apache.commons.math3.distribution.NormalDistribution
import java.io.*


data class DescentData(val simFun: (String) -> Map<String, Double>,
                       val partitionFun: ((String) -> List<String>)? = null)

//class Sheaf(val name: String, val text: String, val cover: Sheaf? = null, useLetterGram: Boolean = false) {
class Sheaf(val name: String, val partitions: List<String>, val kld: Double = 1.0, val cover: Sheaf? = null) : Serializable {
    val measure = HashMap<String, Pair<Sheaf, Double>>()

    fun descend(descentData: List<DescentData>)  {
        val (simFun, partitionFun) = descentData.firstOrNull() ?: return
        val leftovers = descentData.subList(1, descentData.size)

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
        val mixture =  TopicMixtureResult(results.toSortedMap(), kld).reportResults()

        partitionSims.mapNotNull { (name, _) -> results[name]?.to(name) }
            .filter { it.first > 0.0 }
            .map { (freq, sheafName) ->
                val partitionText = partitionTextMap[sheafName]!!
                val newPartitions = partitionFun?.invoke(partitionText) ?: emptyList()
                measure[sheafName] = Sheaf(sheafName, newPartitions, kld, cover) to freq
            }

        measure.forEach { (_, sheafMeasure) ->
            val sheaf = sheafMeasure.first
            sheaf.descend(leftovers)
        }

    }

    companion object {
        fun perturb(nSamples: Int = 50, sims: Map<String, Double>): Pair<List<String>, List<List<Double>>> {
            val norm = NormalDistribution(sharedRand, 1000.0, 50.0)
            val (kernelNames, kernelFreqs) = sims.toList().unzip()

            val perturbations = (0 until nSamples).map {
                norm.sample(kernelFreqs.size)
                    .toList()
                    .zip(kernelFreqs)
                    .map { (gaussian, kernelFreq) -> gaussian * kernelFreq  } }

            return kernelNames to perturbations
        }

    }
}

//    val kernel =
//            KernelDist(0.0, 1.0, false)
//                .apply {
//                    analyzePartitionedDocument(text, useLetterGram)
//                    normalizeKernels()
//                }

//    fun retrieveKernelFreqs() = name to kernel.getKernelFreqs()

class KotlinMetaKernelAnalyzer(val paragraphIndex: String) {

    fun unigramFreq(text: String): Map<String, Double> =
        AnalyzerFunctions.createTokenList(text, analyzerType = AnalyzerFunctions.AnalyzerType.ANALYZER_ENGLISH)
            .groupingBy(::identity)
            .eachCount()
            .normalize()

    fun splitSentence(text: String): List<String> = text.split(".")
        .filter { it.length > 3 }

    fun splitWord(text: String): List<String> =
            "[ \n\t]".toRegex().split(text)
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
        val paragraphs = directory.listFiles().map { file -> file.readText() }
        val sheaf = Sheaf(topic, paragraphs)
        val descentData = listOf(
                DescentData(this::unigramFreq, this::splitSentence),
                DescentData(bindFreq(2), this::splitWord),
                DescentData(this::singleLetterFreq)
        )
        sheaf.descend(descentData)
        File("descent_data/").let { file -> if (!file.exists()) file.mkdir() }
        val f = FileOutputStream("descent_data/$topic")
        val of = ObjectOutputStream(f)
        of.writeObject(sheaf)
        of.close()
        f.close()


//        val cover = paragraphs
//            .map { (_, text) -> text }
//            .joinToString("\n")
//            .let { combinedText -> Sheaf(topic, combinedText) }
//
//        val sheaves = paragraphs.map { (sheafName, text) -> Sheaf(sheafName, text, cover)}
//
//        val perturbations = cover.kernel.perturb(1000)
//        val kernelFreqs = sheaves.map(Sheaf::retrieveKernelFreqs) + cover.retrieveKernelFreqs()
//
//
//        val integrator = KotlinStochasticIntegrator(perturbations, kernelFreqs, {null}, false)
//        val integrals = integrator.integrate()
//
//        val identityFreq = integrals.find { it.first == topic }!!.second
//        val (featureNames, featureFreqs) = integrals.filter { it.first != topic }.unzip()
//
//        val stepper = PartitionDescenter(identityFreq, featureFreqs)
//        val (weights, kld) = stepper.startDescent(500)
//        val results = featureNames.zip(weights).toMap()
//
//        sheaves.mapNotNull { sheaf -> results[sheaf.name]?.to(sheaf) }
//            .forEach { (freq, sheaf) -> cover.measure[sheaf.name] = sheaf to freq }
//
////        val mixture =  TopicMixtureResult(results.toSortedMap(), kld)
//
////        println("For topic: $topic")
////        mixture.reportResults()

    }


    fun trainParagraphs() {
        File(paragraphIndex)
            .listFiles()
            .filter { file -> file.isDirectory }
            .take(1)
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

fun main(args: Array<String>) {
    val metaAnalyzer = KotlinMetaKernelAnalyzer("paragraphs/")
//    metaAnalyzer.trainParagraphs()
    val sheaves = metaAnalyzer.loadSheaves("descent_data/")
    println(sheaves.first().partitions.joinToString())
}