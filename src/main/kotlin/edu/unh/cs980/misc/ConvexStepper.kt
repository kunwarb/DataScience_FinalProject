package edu.unh.cs980.misc

import edu.unh.cs980.features.featSDMWithEntityQueryExpansion
import edu.unh.cs980.sharedRand
import smile.math.Math.c
import smile.math.matrix.Matrix
import smile.math.*
import smile.math.Math.KullbackLeiblerDivergence
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import smile.math.matrix.*
import kotlin.math.sign
import kotlin.system.exitProcess



class GradientDescenter(val origin: List<Double>, val topics: List<List<Double>>) {
    val weightMatrices =
        (0 until topics.size).map { Matrix.newInstance(1, origin.size, 1.0) }

    val topicMatrices = topics.map { Matrix.newInstance(it.toDoubleArray())}
    val originArray = origin.toDoubleArray()

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
            val baseline = kld(weights)
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
        return finalWeights to kld(weights)
    }


    private fun mix(weights: List<Double>): List<Double> {
        val weightsTotal = weights.sum()
        return topics.foldIndexed(mempty) { index, acc, list ->
            acc.zip(list).map { (v1, v2) -> v1 + v2 * weights[index] } }
            .map { mixValue -> mixValue / weightsTotal }
    }

}

fun applyWeight(weight: Double, mat: DenseMatrix, size: Int): DenseMatrix {
    val weightMatrix = Matrix.newInstance(1, size, weight)
    return weightMatrix.abmm(mat)!!
}

fun main(args: Array<String>) {
    val lists = (0 until 3).map { (0 until 5).map { it.toDouble() }.toDoubleArray() }.toTypedArray()
    val myMatrix = smile.math.matrix.Matrix.newInstance(lists)
    val weights = c(10.0, 100.0, 1000.0)
    val weightMatrix = Matrix.newInstance(weights)


    val w1 = applyWeight(1.5, myMatrix, 3)
    val w2 = applyWeight(1.5, myMatrix, 3)
    println(w1)
    println(w2)
    println(Matrix.ones(3, 3).mul(0.3))
    println("TOGETH")
    println(w1.add(w2))
}
