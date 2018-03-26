package edu.unh.cs980.ranklib

import edu.unh.cs980.applyIf
import edu.unh.cs980.runIf
import java.io.File


class RankLauncher(val rankLibLoc: String) {


    fun getRankLibSpecs() {

    }

    fun createModelDirectory() {
        File("models/").applyIf({!exists()}) {
            mkdir()
        }
    }

    fun retrieveWeights(fileLoc: String) =
        File(fileLoc)
            .readLines()
            .last()
            .split(" ")
            .map { result ->
                result.split(":")
                    .let { (id, weight) -> id.toInt() to weight.toFloat() }
            }


    fun writeFeatures(featList: List<Int>) =
        File("features.txt")
            .writeText(featList.joinToString("\n"))


    fun printLogResults() {
        File("log_rank.log")
            .readLines()
            .filter { it.startsWith("Fold ") || it.startsWith("MAP on") }
            .onEach(::println)
    }

    fun extractLogResults() =
        File("log_rank.log")
            .readLines()
            .filter { line -> line.startsWith("MAP on") }
            .map { line -> line.split(":")[1].toDouble() }
            .chunked(2)
            .mapIndexed { index, chunk -> index + 1 to chunk.average() }


    fun getBestModel() =
            extractLogResults()
                .maxBy { it.second }
                .let { result ->
                    val weights = retrieveWeights("models/f${result!!.first}.default")
                    println(weights)
                }

    fun runRankLib() {
        createModelDirectory()

        val commands = arrayListOf(
                "java", "-jar", "$rankLibLoc",
                "-train", "ranklib_features.txt",
                "-feature", "features.txt",
                "-ranker", "4",
                "-metric2t", "map",
                "-kcv", "5",
//                "-i", "50",
//                "-r", "10",
                "-tvs", "0.3",
                "-kcvmd", "models/"
                )

        val log = File("log_rank.log")


        val processBuilder = ProcessBuilder(commands)
//        processBuilder.redirectOutput(File("/dev/null"))
        processBuilder.redirectOutput(log)
            .redirectErrorStream(false)
        val process = processBuilder.start()
        process.waitFor()
    }

}

fun main(args: Array<String>) {
    val runner = RankLauncher("RankLib-2.1-patched.jar")
    runner.runRankLib()
    runner.printLogResults()
    runner.getBestModel()
}