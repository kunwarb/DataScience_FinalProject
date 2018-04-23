package edu.unh.cs980.experiment

import edu.unh.cs980.Main
import edu.unh.cs980.context.HyperlinkIndexer
import edu.unh.cs980.features.*
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.identity
import edu.unh.cs980.language.*
import edu.unh.cs980.misc.MethodContainer
import edu.unh.cs980.misc.buildResourceDispatcher
import edu.unh.cs980.paragraph.KotlinEmbedding
import edu.unh.cs980.ranklib.KotlinRanklibFormatter
import edu.unh.cs980.ranklib.NormType
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs

class MasterExperiment(val resources: HashMap<String, Any>) {
    val indexPath: String by resources
    val qrelPath: String by resources
    val queryPath: String by resources
    val gram: KotlinGramAnalyzer by resources
    val descent_data: String by resources
    val paragraphs: String by resources

    val out: String by resources

    val formatter = KotlinRanklibFormatter(queryPath, qrelPath, indexPath)
    val indexer = getIndexSearcher(indexPath)
    val metaAnalyzer = KotlinMetaKernelAnalyzer(paragraphs)
    val embedder = KotlinEmbedding(indexPath)


    fun wee() {
        val weights = listOf(0.08047025663846726, 0.030239885393043505, 0.15642380129849698, 0.45881012321282,
                0.1370279667285861, 0.1370279667285861
        )

        val bindSDM = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featSDM(query, tops, indexSearcher, gram, 4.0)
        }

        formatter.addFeature({ query, tops, indexSearcher ->
            featSplitSim(query, tops, indexSearcher, bindSDM, weights)
        }, normType = NormType.ZSCORE)
    }



    // Sorry for the mess... Partially binds my sheaf distance feature so that it's compatible with the
    // signature expected by my formatter.
    fun bindSheafDist(startLayer: Int, measureLayer: Int, reductionMethod: ReductionMethod,
                      normalize: Boolean, mixtureDistanceMeasure: MixtureDistanceMeasure,
                      queryEmbeddingMethod: SheafQueryEmbeddingMethod,
                      filterList: List<String> = emptyList(),
                      ascentType: AscentType = AscentType.ASCENT_SUM) =
        { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featSheafDist(query, tops, indexSearcher, metaAnalyzer, startLayer, measureLayer, reductionMethod,
                    normalize, mixtureDistanceMeasure, queryEmbeddingMethod, filterList, ascentType)
        }


    /**
     * Func: trainClusters
     * Desc: In this method, I divide 15 topics into "clusters" of 3. Distances between the query and a paragraph are
     *       obtained by projecting them onto points on a 3D simplex and taking Manhattan distance.
     */
    fun trainClusters(level: Int = 0, weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        // Of the 21 features, I am partitioning 15 into five "clusters" and treating each as a feature
        val clusters = listOf(
                listOf("Cooking", "Games", "Society"),
                listOf("Warfare", "Biology", "Politics"),
                listOf("Technology", "Travel", "Environments"),
                listOf("Mathematics", "Fashion", "Engineering"),
                listOf("Events", "Organizations", "People") )


        clusters.forEachIndexed { index, cluster ->
            val boundSheaf = bindSheafDist(
                    startLayer = level, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY, filterList = cluster)

            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)
        }
    }

    /**
     * Func: trainSubClusters
     * Desc: As above, except I treat the dimensions of the simplex are equal to the number of paragraphs that were used
     *       to construct each topic. The number is not exact because some of the paragraphs are "not important" the
     *       topic and were eliminated when decomposing the topics into their basis paragraphs.
     * @see Sheaf
     * @see Sheaf.descend  # to see how I decompose topics into paragraphs, paragraphs into sentences, etc.
     */
    fun trainSubClusters(weights: List<Double>? = null) {
        trainClusters(level = 1, weights = weights) // Level determines which sheaf we should have a distribution over
        // Level 0 = topic sheaves
        // Level 1 = paragraph sheaves
        // Level 2 = sentence sheaves
        // Level 3 = word sheaves
    }


    /**
     * Func: trainMetrics
     * Desc: I experiment with different distance measures between simplexes: Euclinean, Manhattan, Cosine, Minkowski.
     *       I am only considering a subset of topics (Cooking, Games, Society)
     */
    fun trainMetrics(weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        val myFilter = listOf("Cooking", "Games", "Society")

        val distances = listOf(MixtureDistanceMeasure.KLD,
                MixtureDistanceMeasure.EUCLIDEAN,
                MixtureDistanceMeasure.MANHATTAN,
                MixtureDistanceMeasure.COSINE,
                MixtureDistanceMeasure.MINKOWSKI)

        distances.forEachIndexed { index, curDist ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = curDist,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY, filterList = myFilter)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)

        }
    }

    /**
     * Func: trainQueryEmbeddingMethods
     * Desc: In this experiment, I consider different approaches to embedding the query.
     *       QUERY: just use the query as-is
     *       MEAN:  merge the top 100 documents and project onto simplex. Order documents by their distance to
     *              this simplex (think of it like the "center" of a cluster).
     *       QUERY_EXPANSION: split query into tokens, query Lucene with each token, and merge first top document from
     *                        each search result into one giant query
     */
    fun trainQueryEmbeddingMethods(weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        val myFilter = listOf("Cooking", "Games", "Society")

        val queryEmbeddingMethods = listOf( SheafQueryEmbeddingMethod.QUERY,
                SheafQueryEmbeddingMethod.MEAN, SheafQueryEmbeddingMethod.QUERY_EXPANSION)

        queryEmbeddingMethods.forEachIndexed { index, embeddingMethod ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                    queryEmbeddingMethod = embeddingMethod, filterList = myFilter)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)
        }
    }

    /**
     * Func: trainReductionMethods
     * Desc: In this experiment, I consider different approaches to integration by parts.
     *       AVERAGE: Average the similarities from words in query/paragraphs to those in words in bottom-most sheaf.
     *       MAX_MAX:  Sum up the maximum similarities of each word in query/paragraphs to words in  bottom-most sheaf.
     *       SMOOTHED_THRESHOLD: When the size of the query/paragraph is small, its distance to words matters more.
     *                           The similarity score from words in the query/paragraph is boosted when the size of
     *                           the query/paragraph is small.
     */
    fun trainReductionMethods(weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        val myFilter = listOf("Cooking", "Games", "Society")
        val reductions = listOf(ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD, ReductionMethod.REDUCTION_MAX_MAX,
                ReductionMethod.REDUCTION_AVERAGE)


        reductions.forEachIndexed { index, reduction ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = reduction,
                    normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY, filterList = myFilter)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)
        }
    }

    /**
     * Func: trainAscentMethods
     * Desc: The value of a topic with respect to a query/paragraph is normally expressed by the sum of distances
     *       to the topic's words (weighted by how important the words are to the topic).
     *       Other methods are explored below:
     *       SUM: The normal method.
     *       MAX: At each level, take only the maximum similarity score of the sheaf's partitions after adjusting
     *            for the importance of each partition.
     *       THRESHOLD_SUM: Similarity scores are only passed up the sheaf if the sum meets a certain threshold.
     *                      (Think of activation threshold in neural networks)
     *       THRESHOLD_MAX: As above, except the threshold is based on the maximal score returned from the
     *                      lower sheaves.
     */
    fun trainAscentMethods(weights: List<Double>? = null) {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE, weight = weights?.get(0) ?: 1.0)

        val myFilter = listOf("Cooking", "Games", "Society")
        val ascentTypes = listOf(AscentType.ASCENT_SUM, AscentType.ASCENT_MAX,
                AscentType.ASCENT_THRESHOLD_SUM, AscentType.ASCENT_THRESHOLD_MAX)


        ascentTypes.forEachIndexed { index, ascentType ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY, filterList = myFilter,
                    ascentType = ascentType)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE, weight = weights?.get(index + 1) ?: 1.0)
        }
    }

    fun doClust() {
        metaAnalyzer.loadSheaves(descent_data)
        formatter.addBM25(normType = NormType.ZSCORE)

        // Medicine not so good, so is society
        val myFilter = listOf("Medicine", "Cooking", "Games", "Society")
        val myFilter2 = listOf("Warfare", "Biology")
        val myFilter3 = listOf("Technology", "Travel")
        val myFilter4 = listOf("Mathematics", "Fashion", "Engineering")
        val myFilter5 = listOf("Events", "Organizations", "People")
//        val myFilter = emptyList<String>()
//        val bindEmbed = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
//            featUseEmbeddedQuery(query, tops, indexSearcher, embedder) }

        val distances = listOf(MixtureDistanceMeasure.DELTA_DENSITY, MixtureDistanceMeasure.EUCLIDEAN)

        distances.forEach { curDist ->
            val boundSheaf = bindSheafDist(
                    startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                    normalize = true, mixtureDistanceMeasure = curDist,
                    queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY_EXPANSION, filterList = myFilter)
            formatter.addFeature(boundSheaf, normType = NormType.ZSCORE)

        }






//        val boundSheafDistFunction = bindSheafDist(
//                startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
//                normalize = false, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
//                queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY_EXPANSION, filterList = myFilter)

        val boundSheafDistFunction2 = bindSheafDist(
                startLayer = 1, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                normalize = false, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY_EXPANSION, filterList = myFilter)

//        val boundSheafDistFunction3 = bindSheafDist(
//                startLayer = 2, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
//                normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
//                queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY_EXPANSION, filterList = myFilter)
//
//
//        val boundSheafDistFunction3 = bindSheafDist(
//                startLayer = 1, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_MAX_MAX,
//                normalize = false, mixtureDistanceMeasure = MixtureDistanceMeasure.EUCLIDEAN,
//                queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY_EXPANSION)

//        formatter.addFeature(bindEmbed, normType = NormType.ZSCORE)
//        formatter.addFeature(boundSheafDistFunction, normType = NormType.ZSCORE)
//        formatter.addFeature(boundSheafDistFunction2, normType = NormType.ZSCORE)
//        formatter.addFeature(boundSheafDistFunction3, normType = NormType.ZSCORE)
    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("sheaf_embedding")
                .help("Collection of features involving the embedding of queries/paragraphs in structures" +
                        " representing topic models.")
//            dispatcher.generateArguments("", mainParser)

//            val exec = Main.buildExec { namespace: Namespace -> MasterExperiment(namespace).run() }
//            mainParser.setDefault("func", exec)

            val subparsers = mainParser.addSubparsers()
            val trainParser = subparsers.addParser("train")
                .help("Training methods for RankLib")
            register("train", trainParser)

            val queryParser = subparsers.addParser("query")
                .help("Query methods for RankLib")
            register("query", queryParser)

        }

        fun register(methodType: String, parser: Subparser)  {
            val exec = Main.buildExec { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = MasterExperiment(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<MasterExperiment>
                method.getMethod(methodType, methodName)?.invoke(instance)

                if (methodType == "query") {
                    instance.formatter
                        .apply { rerankQueries() }
                        .writeQueriesToFile(instance.out)
                } else {
                    instance.formatter.writeToRankLibFile("ranklib_results.txt")
                }
            }

            parser
                .help("Collection of features involving the embedding of queries/paragraphs in structures" +
                        " representing topic models.")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {

                    methods<MasterExperiment> {
                        method("train", "AscentMethods") { trainAscentMethods() }
                        method("train", "Clusters") { trainClusters() }
                        method("train", "SubClusters") { trainSubClusters() }
                        method("train", "QueryEmbeddingMethods") { trainQueryEmbeddingMethods() }
                        method("train", "ReductionMethods") { trainReductionMethods() }
                        method("train", "Metrics") { trainMetrics() }

                        method("query", "AscentMethods") {
                            val weights = listOf(1.0, 1.0, 1.0, 1.0, 1.0)
                            trainAscentMethods(weights)
                        }
                        method("query", "Clusters") {
                            val weights = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
                            trainClusters(level = 0, weights = weights)
                        }
                        method("query", "SubClusters") {
                            val weights = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
                            trainSubClusters(weights)
                        }
                        method("query", "QueryEmbeddingMethods") {
                            val weights = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
                            trainQueryEmbeddingMethods(weights)
                        }
                        method("query", "ReductionMethods") {
                            val weights = listOf(1.0, 1.0, 1.0, 1.0)
                            trainReductionMethods(weights)
                        }
                        method("query", "Metrics") {
                            val weights = listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
                            trainMetrics(weights)
                        }

                    }

                    resource("indexPath") {
                        help = "Location of the Lucene index database (Default: /trec_data/team_1/myindex"
                        default = "/trec_data/team_1/myindex"
                        loader = ::identity
                    }

                    resource("qrelPath") {
                        help = "Location to qrel file"
                        default = ""
                        loader = ::identity
                    }

                    resource("queryPath") {
                        help = "Location to query (.cbor) file."
                        loader = ::identity
                    }

                    resource("out") {
                        help = "Name of query file to create"
                        default = "query_results.run"
                        loader = ::identity
                    }


                    resource("gram") {
                        help = "Location to gram Lucene index (Default: /trec_data/team_1/gram)"
                        default = "/trec_data/team_1/gram/"
                        loader = { path -> KotlinGramAnalyzer(getIndexSearcher(path)) }
                    }

                    resource("paragraphs") {
                        help = "Location to paragraphs training directory (Default: /trec_data/team_1/paragraphs)"
                        default = "/trec_data/team_1/paragraphs/"
                        loader = ::identity
                    }

                    resource("descent_data") {
                        help = "Location to descent data directory (Default: /trec_data/team_1/descent_data)"
                        default = "/trec_data/team_1/descent_data/"
                        loader = ::identity
                    }

                }
    }

}