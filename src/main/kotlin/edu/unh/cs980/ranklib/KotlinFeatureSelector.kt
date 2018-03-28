@file:JvmName("KotFeatureSelector")
package edu.unh.cs980.ranklib

import edu.unh.cs980.applyIf
import edu.unh.cs980.pmap
import java.io.File


class KotlinFeatureSelector(val rankLibLoc: String, val featuresLoc: String) {

    private fun runFeatureTraining() {

    }

    private fun createModelDirectory() =
            File("models/").applyIf({!exists()}) { mkdir() }

    private fun createLogDirectory() =
            File("ranklib_logs/").applyIf({!exists()}) { mkdir() }

    private fun createFeatureSubsetsDirectory() =
            File("ranklib_subsets/").applyIf({!exists()}) { mkdir() }

    private fun retrieveWeights(fileLoc: String) =
        File(fileLoc)
            .readLines()
            .last()
            .split(" ")
            .map { result ->
                result.split(":")
                    .let { (id, weight) -> id.toInt() to weight.toFloat() }
            }


    private fun writeFeatures(featList: List<Int>, logLoc: String) =
        File("ranklib_subsets/${logLoc}.txt")
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

    private fun extractLogResults(logLoc: String) =
        File("ranklib_logs/${logLoc}.log")
            .readLines()
            .filter { line -> line.startsWith("MAP on") }
            .map { line -> line.split(":")[1].toDouble() }
            .chunked(2)
            .mapIndexed { index, chunk -> index + 1 to chunk.average() }


    private fun getBestModel(logLoc: String) =
            extractLogResults(logLoc)
                .maxBy { it.second }
                .let { result ->
                    val weights = retrieveWeights("models/f${result!!.first}.default")
                    println(weights)
                }

    private fun getAveragePerformance(logLoc: String) =
            extractLogResults(logLoc)
                .run { sumByDouble { it.second } / size }


    private fun alphaSelection() {
        val nFeatures = countFeatures()
        val features = (2 .. nFeatures)

        features.forEach { feature ->
            val featLog = "feature_$feature"
            writeFeatures(listOf(1, feature), featLog)
            runRankLib(featLog, useFeatures = true, useKcv = false)
            val result = feature to getAveragePerformance(featLog)
            println("Result for $feature : $result")
        }
    }

    private fun subsetSelection() {
        val nFeatures = countFeatures()
        var features = (2 .. nFeatures).toList()
        val curBestFeatures = arrayListOf(1)

        var baseline = tryAddingFeature(arrayListOf(), 1, "feat")
        println("Baseline: $baseline")

        for (i in 0 until nFeatures - 1) {
            var bestBaseline = 0.0
            var bestFeature = 0

            val results = features.pmap { feature ->
                val featLog = "feature_$feature"
                feature to tryAddingFeature(curBestFeatures, feature, featLog)
            }

            results.forEach { (feature, result) ->
                val improvement = result - baseline
                if (improvement > 0 && result > bestBaseline ) {
                    bestBaseline = result
                    bestFeature = feature
                }
            }


            if (bestFeature != 0 || bestBaseline - baseline < 0.01) {
                println("Adding feature $bestFeature to $curBestFeatures")
                println("Previous / New Baseline: $baseline / $bestBaseline")
                baseline = bestBaseline
                curBestFeatures += bestFeature
                features = features.filter { it != bestFeature }.toList()
            } else {
                println("Can do no further improvements")
                break
            }
        }

        println("Training final model")
        writeFeatures(curBestFeatures, "final")
        runRankLib("final", useFeatures = true)
        println("Best Model:")
        getBestModel("final")
    }

    private fun tryAddingFeature(curBestFeature: ArrayList<Int>, featureToAdd: Int, logLoc: String): Double {
        val flist = curBestFeature.toList() + listOf(featureToAdd)
        writeFeatures(flist, logLoc)
        runRankLib(logLoc, useFeatures = true, useKcv = false)
        return getAveragePerformance(logLoc)
    }

    private fun runRankLib(logLoc: String, useFeatures: Boolean = false, useKcv: Boolean = true) {
        createModelDirectory()
        createLogDirectory()
        createFeatureSubsetsDirectory()

        val commands = arrayListOf(
                "java", "-jar", rankLibLoc,
                "-train", featuresLoc,
                "-ranker", "4",
                "-metric2t", "map",
//                "-i", "50",
//                "-r", "10",
                "-tvs", "0.3"
                )

        if (useFeatures) {
            commands.addAll(listOf("-feature", "ranklib_subsets/${logLoc}.txt"))
        }

        if (useKcv) {
            commands.addAll(listOf("-kcv", "5"))
            commands.addAll(listOf("-kcvmd", "models/"))
        } else {
            commands.addAll(listOf("-save", "model.txt"))
        }

        val log = File("ranklib_logs/${logLoc}.loc")


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
            "subset_selection" -> subsetSelection()
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