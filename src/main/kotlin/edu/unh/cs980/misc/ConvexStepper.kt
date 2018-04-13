package edu.unh.cs980.misc

import edu.unh.cs980.sharedRand
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.sign
import kotlin.system.exitProcess


class ConvexStepper(val origin: List<Double>, val topics: List<List<Double>>) {
    val weights = (0 until topics.size).map { 500.0 }.toMutableList()

    fun kld(): Double =
            origin.zip(mix())
                .sumByDouble { (originDist, mixtureDist) ->
                    (originDist) * log2(originDist / mixtureDist) }


    private val mempty = (0 until topics.first().size).map { 0.0 }


    fun doStep(start: Double, stop: Double, index: Int, interval: Int) {
        val stepDistance = (stop - start)  / interval.toDouble()
        val baseline = kld()

        val results = (0 until interval)
            .map { step ->
                val cur = start + step * stepDistance
                weights[index] = cur
                cur to kld()  }

        val best = results.minBy { it.second }!!
        if (baseline > best.second) weights[index] = best.first
//        weights[index] = best
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


//    fun doStep(start: Double, stop: Double, index: Int, interval: Int) {
//        val stepDistance = (stop - start)  / interval.toDouble()
//
//        val results = (0 until interval)
//            .map { step ->
//                val cur = start + step * stepDistance
//                weights[index] = cur
//                cur to kld()  }
//
//        val best = results.minBy { it.second }!!.first
//        weights[index] = best
//    }
//
//    fun descendCoordinate(index: Int, nTimes: Int, interval: Int) {
//        var distance = 100.0
//        (0 until nTimes).forEach {
//            val start = max(0.0, weights[index] - distance)
//            val stop = weights[index] + distance
//            doStep(start, stop, index, interval)
//            distance /= interval.toDouble()
//        }
//    }

//    fun searchSimplex(nTimes: Int = 5, interval: Int = 20): List<Double> {
////        descendCoordinate(0, nTimes, interval)
//        (0 until topics.size).forEach { index -> descendCoordinate(index, nTimes, interval) }
//        val weightTotal = weights.sum()
//        return weights.map { weight -> weight / weightTotal }
//    }

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

class GradientDescenter(val origin: List<Double>, val topics: List<List<Double>>) {

    fun kld(weights: List<Double>): Double =
            origin.zip(mix(weights))
                .sumByDouble { (originDist, mixtureDist) ->
                    (originDist) * log2(originDist / mixtureDist) }


    private val mempty = (0 until topics.first().size).map { 0.0 }

//    private fun changeIndexed(index: Int, weights: List<Double>, newValue: Double): List<Double> =
//            weights.


//    fun doStep(baseline: Double, index: Int, weights: List<Double>, error: Double): Pair<Double, Double> {
//        val curWeight = weights[index]
//        if (curWeight <= 0.0)
//            return 0.0 to 0.0
//        val stepUp = weights.toMutableList().apply { set(index, curWeight + error) }
//        val stepDown = weights.toMutableList().apply { set(index, curWeight - error) }
//
//        val stepUpDiff = kld(stepUp) - baseline
//        val stepDownDiff = kld(stepDown) - baseline
////        return  if (stepUpDiff < stepDownDiff) stepUp[index] to stepUpDiff * 10  + error
////                else stepDown[index] to stepDownDiff * 10 + error
//        return  if (stepUpDiff < stepDownDiff) stepUp[index] to stepUpDiff * 10   + error
//        else stepDown[index] to stepDownDiff * 10  + error
////        return  if (stepUpDiff < stepDownDiff) stepUp[index] to stepUpDiff   + error
////        else stepDown[index] to stepDownDiff  + error
//    }

    fun doStep(baseline: Double, index: Int, weights: List<Double>, error: Double, rate: Double): Pair<Double, Double> {
        val curWeight = weights[index]
        if (curWeight <= 0.0)
            return 0.0 to 0.0
        val change = weights.toMutableList().apply { set(index, curWeight + error) }
        val diff = kld(change) - baseline
        return change[index] to (error - diff) * rate
    }

    fun getDerivative(baseline: Double, index: Int, weights: List<Double>): Pair<Double, Double> {
        if (weights[index] <= 0.0) {
            return 0.0 to 0.0
        }
        val curWeight = weights[index]
//        val lowerStep = curWeight * 0.9
//        val upperStep = curWeight * 1.1
        val lower = weights.toMutableList().apply { set(index, curWeight - 0.001) }
        val upper = weights.toMutableList().apply { set(index, curWeight + 0.001) }
        val lowerDiff = (kld(lower) - baseline)
        val upperDiff = (kld(upper) - baseline)

//        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff / (lowerStep - curWeight)) else 1.0 to abs(upperDiff / (upperStep - curWeight))
        return if (lowerDiff < upperDiff) -1.0 to abs(lowerDiff) else 1.0 to abs(upperDiff)
    }


    fun hypothesis(baseline: Double, weights: List<Double>, errors: List<Double>, rate: Double ): Double {
        val newWeights = weights.zip(errors).map { (v1,v2) -> v1 + v2 * rate}
        val delta = kld(newWeights) - baseline
        return delta
    }

    fun startDescent(nTimes: Int): Pair<List<Double>, Double> {
//        val kltest = kld(weights)
        var weights = (0 until topics.size).map { 0.1 }.toList()
//        var errors = (0 until topics.size).map { sharedRand.nextDouble() * 0.1 + 0.1 }.toList()
        var errors = (0 until topics.size).map { 0.01 }.toList()
        var learningRate = 0.99

        (0 until nTimes).forEach {
            val baseline = kld(weights)
            println(baseline)
            val gradient = (0 until topics.size).map { index -> getDerivative(baseline, index, weights)}
            val total = gradient.sumByDouble { (_, delta) -> delta }
            if (total > 0.000000000001) {
                weights = weights.mapIndexed { index, weight ->
                    val delta = gradient[index]
                    if (weight <= 0.0) 0.0 else
                        max(0.0, weight + delta.first * (delta.second / total) * 0.01)
                }
            }
            // 0.00633
            // 0.0053a


//            val residuals = (0 until topics.size).map { index -> doStep(baseline, index, weights, errors[index], learningRate) }
//            val (newWeights, newErrors) = residuals.unzip()
//            weights = newWeights
//            errors = newErrors
            learningRate *= 0.99
            // .00827
        }
        val weightSum = weights.sum()
        val finalWeights = weights.map { value -> value / weightSum }
        return finalWeights to kld(weights)
    }


//    fun doStep(start: Double, stop: Double, index: Int, interval: Int) {
//        val stepDistance = (stop - start)  / interval.toDouble()
//
//        val results = (0 until interval)
//            .map { step ->
//                val cur = start + step * stepDistance
//                weights[index] = cur
//                cur to kld()  }
//
//        val best = results.minBy { it.second }!!.first
//        weights[index] = best
//    }
//
//    fun descendCoordinate(index: Int, nTimes: Int, interval: Int) {
//        var distance = 100.0
//        (0 until nTimes).forEach {
//            val start = max(0.0, weights[index] - distance)
//            val stop = weights[index] + distance
//            doStep(start, stop, index, interval)
//            distance /= interval.toDouble()
//        }
//    }

//    fun searchSimplex(nTimes: Int = 5, interval: Int = 20): List<Double> {
////        descendCoordinate(0, nTimes, interval)
//        (0 until topics.size).forEach { index -> descendCoordinate(index, nTimes, interval) }
//        val weightTotal = weights.sum()
//        return weights.map { weight -> weight / weightTotal }
//    }


    private fun mix(weights: List<Double>): List<Double> {
        val weightsTotal = weights.sum()
        return topics.foldIndexed(mempty) { index, acc, list ->
            acc.zip(list).map { (v1, v2) -> v1 + v2 * weights[index] } }
            .map { mixValue -> mixValue / weightsTotal }
    }

}
