package edu.unh.cs980.paragraph

import edu.unh.cs980.defaultWhenNotFinite
import edu.unh.cs980.normalize
import edu.unh.cs980.smooth
import java.lang.Math.pow
import java.lang.Math.sqrt
import kotlin.math.abs
import kotlin.math.log2


class KotlinStochasticIntegrator(val perturbations: Pair<List<String>, List<List<Double>>>,
                                 topics: List<Pair<String, Map<String, Double>>>,
                                 val corpus: (String) -> Double?,
                                 val smooth: Boolean = false) {

    val restrictions = topics.map(this::getRestrictedTopic)
    val topicNames = topics.map { (name, _) -> name }

    private fun getRestrictedTopic(topic: Pair<String, Map<String, Double>>): List<Double> {
        val topicHash = topic.second
//        val restrictedFreq = perturbations.first.sumByDouble { word -> topicHash[word] ?: 0.0 } / topicHash.values.sum()

        val focusedHash = perturbations.first.map { word ->
//            word to (topicHash[word] ?: 1 / perturbations.first.size.toDouble())
            word to (topicHash[word] ?: corpus(word)?.run { (1/this) / topicHash.size.toDouble() } ?: 1/perturbations.first.size.toDouble())
//            word to (topicHash[word] ?: (perturbations.first.size * topic.second.size.toDouble()) )
        }
            .toMap()
//            .normalize()

        val restrictedDist = perturbations.first
//            .map { word -> topicHash[word]  ?: topic.second.keys.size * perturbations.first.size.toDouble() }
//            .map { word -> topicHash[word]  ?: perturbations.first.size.toDouble() }
//            .map { word -> topicHash[word]  ?: 1.0 }
//            .map { word -> topicHash[word]  ?: 1/topic.second.keys.size.toDouble() }
//            .map { word -> topicHash[word]  ?: topic.second.keys.size.toDouble() }
            .map { word -> focusedHash[word]  ?: (0.0000001).defaultWhenNotFinite(0.0000001) }
//            .map { word -> topicHash[word]  ?: 1.0 }
//            .let { curMap ->
////                    curMap.map { value -> value /  perturbations.first.size.toDouble() }}
//                        curMap.map { value -> value /  perturbations.first.size.toDouble() }}
            .toList()

        return restrictedDist
    }

    private fun kldToTopic(topic: List<Double>) =
            perturbations.second
                .map { perturbs ->
//                    perturbs.zip(topic).sumByDouble { (k1, k2) -> abs(k1  * log2(k1 / k2)) / perturbs.size } }
//                     perturbs.zip(topic).sumByDouble { (k1, k2) -> k1  * log2(k1 / k2) / perturbs.size } }
                    perturbs.zip(topic).sumByDouble { (k1, k2) -> abs(k1  * log2(k1 / k2))  } }
                .normalize()
                .let {if (smooth) it.smooth() else it }

    fun integrate() =
            topicNames.zip(restrictions.map(this::kldToTopic))
}
