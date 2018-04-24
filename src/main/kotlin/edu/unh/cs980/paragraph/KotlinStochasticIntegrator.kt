package edu.unh.cs980.paragraph

import edu.unh.cs980.defaultWhenNotFinite
import edu.unh.cs980.normalize
import edu.unh.cs980.smooth
import org.apache.commons.math3.distribution.NormalDistribution
import smile.math.special.Erf.erf
import java.lang.Math.*
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow


class KotlinStochasticIntegrator(val perturbations: Pair<List<String>, List<List<Double>>>,
                                 topics: List<Pair<String, Map<String, Double>>>,
                                 val corpus: (String) -> Double?,
                                 val smooth: Boolean = false) {

    val restrictions = topics.map(this::getRestrictedTopic)
    val topicNames = topics.map { (name, _) -> name }

    private fun getRestrictedTopic(topic: Pair<String, Map<String, Double>>): List<Double> {
        val topicHash = topic.second

        val focusedHash = perturbations.first.map { word ->
            word to (topicHash[word] ?: corpus(word)?.run { (1/this) / topicHash.size.toDouble() } ?: 1/perturbations.first.size.toDouble())
        }
            .toMap()
//            .normalize() : Wait, why don't I normalize this anymore?...

        val restrictedDist = perturbations.first
            .map { word -> focusedHash[word]  ?: (0.0000001).defaultWhenNotFinite(0.0000001) }
            .toList()

        return restrictedDist
    }

    private fun kldToTopic(topic: List<Double>) =
            perturbations.second
                .map { perturbs ->
                    perturbs.zip(topic).sumByDouble { (k1, k2) -> pow(k1 - k2, 2.0) }.run { sqrt(this) } }
//                        perturbs.zip(topic).sumByDouble { (k1, k2) -> abs(k1 - k2) } }
                .normalize()
                .let {if (smooth) it.smooth() else it }

    fun integrate() =
            topicNames.zip(restrictions.map(this::kldToTopic))
}

fun main(args: Array<String>) {
    val norm = NormalDistribution(0.0, 0.5)
    println(norm.sample(10000).sum())
}
