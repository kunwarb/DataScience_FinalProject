package edu.unh.cs980.misc

import edu.unh.cs980.features.featSDMWithEntityQueryExpansion
import edu.unh.cs980.sharedRand
import smile.math.matrix.Matrix
import smile.math.*
import smile.math.Math.*
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import smile.math.matrix.*
import kotlin.math.sign
import kotlin.system.exitProcess


class MartingaleSolver(val origin: List<Double>, val topics: List<List<Double>>) {
    val weights = (0 until topics.size).map { 1.0 }.toDoubleArray()
    val normOrigin = origin.sum().let { total -> origin.map { it / total } }
    var step = 0

    fun doKld() =
        weights.zip(topics).sumByDouble { (weight, topic) -> weight * topic[step] }
            .let { observation ->
                val normObservation = observation / weights.sum()
                (normOrigin[step] - normObservation)  * log2(normOrigin[step] / normObservation)
            }

    fun getDerivative(baseline: Double, index: Int, curWeight: Double): Pair<Double, Double> {
        if (curWeight <= 0.001) return 0.0 to 0.0

        weights[index] = curWeight - 0.001
        val lowerDiff = (doKld() - baseline)

        weights[index] = curWeight + 0.001
        val upperDiff = (doKld() - baseline)

        weights[index] = curWeight
        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff) else 1.0 to abs(upperDiff)
//        return if (lowerDiff < upperDiff) 1.0 to abs(lowerDiff) else -1.0 to abs(upperDiff)
    }


    fun startDescent(replays: Int): Pair<List<Double>, Double> {
        var trainingWeights = (0 until topics.size).map { 1.0 }.toList()

        (0 until replays).forEach {
            (0 until origin.size).forEach { curStep ->
                trainingWeights.forEachIndexed { index, d -> weights[index] = d }
                step = curStep
                val baseline = doKld()
                val gradient = (0 until topics.size).map { index -> getDerivative(baseline, index, trainingWeights[index]) }
                val total = gradient.sumByDouble { (_, delta) -> delta }

                if (total != 0.0) {
                    trainingWeights = trainingWeights.mapIndexed { index, weight ->
                        val delta = gradient[index]
                        if (weight <= 0.001 || delta.second == 0.0) 0.0 else
                            max(0.0, weight + delta.first * (delta.second / total) * 0.01)
                    }
                }
            }

        }
        val weightSum = trainingWeights.sum()
        val finalWeights = trainingWeights.map { value -> value / weightSum }
        return finalWeights to doKld()
    }


}


class GradientDescenter(val origin: List<Double>, val topics: List<List<Double>>) {
    val weightMatrices =
        (0 until topics.size).map { Matrix.newInstance(1, origin.size, 1.0) }

    val topicMatrices = topics.map { Matrix.newInstance(it.toDoubleArray())}
    val originArray = origin.toDoubleArray()
    val originMatrix = Matrix.newInstance(origin.toDoubleArray())

    fun changeWeight(index: Int, weight: Double) =
        weightMatrices[index].mul(0.0).add(weight)

    fun doKld() =
        weightMatrices.zip(topicMatrices)
            .map { (w,t) -> w.transpose().mul(t) }
            .reduce { acc, denseMatrix -> acc.add(denseMatrix)  }
            .run { div(sum()) }
            .transpose().array()
            .run { KullbackLeiblerDivergence(originArray, this.first())}


    fun kld(weights: List<Double>): Double =
            origin.zip(mix(weights))
                .sumByDouble { (originDist, mixtureDist) ->
                    (originDist) * log2(originDist / mixtureDist) }


    private val mempty = (0 until topics.first().size).map { 0.0 }


    fun getDerivative(baseline: Double, index: Int, weights: List<Double>): Pair<Double, Double> {
        if (weights[index] <= 0.0) { return 0.0 to 0.0 }
        val curWeight = weights[index]

        val lower = weights.toMutableList().apply { set(index, curWeight - 0.001) }
        val upper = weights.toMutableList().apply { set(index, curWeight + 0.001) }
        val lowerDiff = (kld(lower) - baseline)
        val upperDiff = (kld(upper) - baseline)

//        return if (lowerDiff < upperDiff) -1.0 to pow(lowerDiff, 2.0) else 1.0 to pow(upperDiff, 2.0)
        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff) else 1.0 to abs(upperDiff)
    }

    fun getDerivative2(baseline: Double, index: Int, curWeight: Double): Pair<Double, Double> {
        if (curWeight <= 0.0) return 0.0 to 0.0

        changeWeight(index, curWeight - 0.001)
        val lowerDiff = (doKld() - baseline)

        changeWeight(index, curWeight + 0.001)
        val upperDiff = (doKld() - baseline)
        changeWeight(index, curWeight)

        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff) else 1.0 to abs(upperDiff)
    }


    fun startDescent(nTimes: Int): Pair<List<Double>, Double> {
        var weights = (0 until topics.size).map { 0.1 }.toList()

        (0 until nTimes).forEach {
//            weights.mapIndexed { index, weight -> changeWeight(index, weight) }
            val baseline = kld(weights)
//            val baseline = doKld()
//            val gradient = (0 until topics.size).map { index -> getDerivative(baseline, index, weights)}
            val gradient = (0 until topics.size).map { index -> getDerivative2(baseline, index, weights[index])}
            val total = gradient.sumByDouble { (_, delta) -> delta }

            if (total > 0.00000000000001) {
                weights = weights.mapIndexed { index, weight ->
                    val delta = gradient[index]
                    if (weight <= 0.0) 0.0 else
                        max(0.0, weight + delta.first * (delta.second / total) * 0.01)
                }
            }

        }
        val weightSum = weights.sum()
        val finalWeights = weights.map { value -> value / weightSum }
        return finalWeights to doKld()
    }


    private fun mix(weights: List<Double>): List<Double> {
        val weightsTotal = weights.sum()
        return topics.foldIndexed(mempty) { index, acc, list ->
            acc.zip(list).map { (v1, v2) -> v1 + v2 * weights[index] } }
            .map { mixValue -> mixValue / weightsTotal }
    }

}

