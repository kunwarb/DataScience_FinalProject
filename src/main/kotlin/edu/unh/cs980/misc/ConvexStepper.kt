package edu.unh.cs980.misc

import kotlin.math.log2
import kotlin.math.max


class ConvexStepper(val origin: List<Double>, val topics: List<List<Double>>) {
    val weights = (0 until topics.size).map { 100.0 }.toMutableList()

    fun kld(): Double =
            origin.zip(mix())
                .sumByDouble { (originDist, mixtureDist) ->
                    (originDist) * log2(originDist / mixtureDist) }


    private val mempty = (0 until topics.first().size).map { 0.0 }

    fun doStep(start: Double, stop: Double, index: Int, interval: Int) {
        val stepDistance = (stop - start)  / interval.toDouble()

        val results = (0 until interval)
            .map { step ->
                val cur = start + step * stepDistance
                weights[index] = cur
                cur to kld()  }

        val best = results.minBy { it.second }!!.first
        weights[index] = best
    }

    fun descendCoordinate(index: Int, nTimes: Int, interval: Int) {
        var distance = 100.0
        (0 until nTimes).forEach {
            val start = max(0.0, weights[index] - distance)
            val stop = weights[index] + distance
            doStep(start, stop, index, interval)
            distance /= interval.toDouble()
        }
    }

    fun searchSimplex(nTimes: Int = 5, interval: Int = 20): List<Double> {
        (0 until topics.size).forEach { index -> descendCoordinate(index, nTimes, interval) }
        val weightTotal = weights.sum()
        return weights.map { weight -> weight / weightTotal }
    }

    private fun mix(): List<Double> {
        val weightsTotal = weights.sum()
        return topics.foldIndexed(mempty) { index, acc, list ->
            acc.zip(list).map { (v1, v2) -> v1 + v2 * weights[index] } }
            .map { mixValue -> mixValue / weightsTotal }
    }

}