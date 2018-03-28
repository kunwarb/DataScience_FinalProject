@file:JvmName("KotFeatureSelector")
package edu.unh.cs980.ranklib

import edu.unh.cs980.applyIf
import java.io.File


class KotlinFeatureSelector(val rankLibLoc: String, val featuresLoc: String) {

    private fun runFeatureTraining() {

    }

    private fun createModelDirectory() =
            File("models/").applyIf({!exists()}) { mkdir() }

    private fun retrieveWeights(fileLoc: String) =
        File(fileLoc)
            .readLines()
            .last()
            .split(" ")
            .map { result ->
                result.split(":")
                    .let { (id, weight) -> id.toInt() to weight.toFloat() }
            }


    private fun writeFeatures(featList: List<Int>) =
        File("features.txt")
            .writeText(featList.joinToString("\n"))


    private fun countFeatures(): Int =
            File(featuresLoc)
                .bufferedReader()
                .readLine()
                .split(" ")
                .size - 2


    private fun printLogResults() {
        File("log_rank.log")
            .readLines()
            .filter { it.startsWith("Fold ") || it.startsWith("MAP on") }
            .onEach(::println)
    }

    private fun extractLogResults() =
        File("log_rank.log")
            .readLines()
            .filter { line -> line.startsWith("MAP on") }
            .map { line -> line.split(":")[1].toDouble() }
            .chunked(2)
            .mapIndexed { index, chunk -> index + 1 to chunk.average() }


    private fun getBestModel() =
            extractLogResults()
                .maxBy { it.second }
                .let { result ->
                    val weights = retrieveWeights("models/f${result!!.first}.default")
                    println(weights)
                }

    private fun getAveragePerformance() =
            extractLogResults()
                .run { sumByDouble { it.second } / size }


    private fun alphaSelection() {
        val nFeatures = countFeatures()
        val features = (2 .. nFeatures)

        features.forEach { feature ->
            writeFeatures(listOf(1, feature))
            runRankLib(true)
            val result = feature to getAveragePerformance()
            println("Result for $feature : $result")
        }
    }

    private fun runRankLib(useFeatures: Boolean = false) {
        createModelDirectory()

        val commands = arrayListOf(
                "java", "-jar", rankLibLoc,
                "-train", featuresLoc,
//                "-feature", "features.txt",
                "-ranker", "4",
                "-metric2t", "map",
                "-kcv", "5",
//                "-i", "50",
//                "-r", "10",
                "-tvs", "0.3",
                "-kcvmd", "models/"
                )

        if (useFeatures) {
            commands.addAll(listOf("-feature", "features.txt"))
        }

        val log = File("log_rank.log")


        val processBuilder = ProcessBuilder(commands)
//        processBuilder.redirectOutput(File("/dev/null"))
        processBuilder.redirectOutput(log)
            .redirectErrorStream(false)
        val process = processBuilder.start()
        process.waitFor()
    }

    fun runMethod(method: String) {
        when (method) {
            "alpha_selection" -> alphaSelection()
            else -> println("Unknown method!")
        }
    }

}

fun main(args: Array<String>) {
    val runner = KotlinFeatureSelector("RankLib-2.1-patched.jar", "ranklib_features.txt")
    runner.runMethod("alpha_selection")
//    runner.runRankLib()
//    runner.printLogResults()
//    runner.getBestModel()
}