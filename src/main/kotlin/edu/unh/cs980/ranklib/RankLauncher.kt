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

    fun runRankLib() {
        createModelDirectory()

        val commands = arrayListOf(
                "java", "-jar", "$rankLibLoc",
                "-train", "ranklib_features.txt",
                "-ranker", "4",
                "-metric2t", "map",
                "-kcv", "5",
                "-tvs", "0.3",
                "-kcvmd", "models"
                )

        val processBuilder = ProcessBuilder(commands)
        processBuilder.redirectOutput(File("/dev/null"))
            .redirectErrorStream(true)
        val process = processBuilder.start()
        process.waitFor()
    }

}