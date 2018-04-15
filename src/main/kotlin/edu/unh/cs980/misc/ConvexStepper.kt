package edu.unh.cs980.misc

import edu.unh.cs980.sharedRand
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sign
import kotlin.system.exitProcess



class GradientDescenter(val origin: List<Double>, val topics: List<List<Double>>) {

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

        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff) else 1.0 to abs(upperDiff)
    }



    fun startDescent(nTimes: Int): Pair<List<Double>, Double> {
        var weights = (0 until topics.size).map { 0.1 }.toList()

        (0 until nTimes).forEach {
            val baseline = kld(weights)
            val gradient = (0 until topics.size).map { index -> getDerivative(baseline, index, weights)}
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
        return finalWeights to kld(weights)
    }


    private fun mix(weights: List<Double>): List<Double> {
        val weightsTotal = weights.sum()
        return topics.foldIndexed(mempty) { index, acc, list ->
            acc.zip(list).map { (v1, v2) -> v1 + v2 * weights[index] } }
            .map { mixValue -> mixValue / weightsTotal }
    }

}
