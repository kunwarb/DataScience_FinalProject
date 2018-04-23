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

//    val abstract: KotlinAbstractAnalyzer by resources
//    val hyperlink: HyperlinkIndexer by resources
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


//    fun featSheafDist(query: String, tops: TopDocs, indexSearcher: IndexSearcher, analyzer: KotlinMetaKernelAnalyzer,
//                      startLayer: Int, measureLayer: Int, reductionMethod: ReductionMethod,
//                      normalize: Boolean, mixtureDistanceMeasure: MixtureDistanceMeasure,
//                      queryEmbeddingMethod: SheafQueryEmbeddingMethod): List<Double> {

    fun bindSheafDist(startLayer: Int, measureLayer: Int, reductionMethod: ReductionMethod,
                      normalize: Boolean, mixtureDistanceMeasure: MixtureDistanceMeasure,
                      queryEmbeddingMethod: SheafQueryEmbeddingMethod) =
        { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
            featSheafDist(query, tops, indexSearcher, metaAnalyzer, startLayer, measureLayer, reductionMethod,
                    normalize, mixtureDistanceMeasure, queryEmbeddingMethod)
        }



    fun doClust() {
        metaAnalyzer.loadSheaves(descent_data, filterWords = listOf("Medicine", "Cooking", "Games", "Society"))
//        metaAnalyzer.loadSheaves(descent_data)
//        embedder.loadTopics(paragraphs)

//        val bindEmbed = { query: String, tops: TopDocs, indexSearcher: IndexSearcher ->
//            featUseEmbeddedQuery(query, tops, indexSearcher, embedder) }



        val boundSheafDistFunction = bindSheafDist(
                startLayer = 0, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY)

        val boundSheafDistFunction2 = bindSheafDist(
                startLayer = 1, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_SMOOTHED_THRESHOLD,
                normalize = true, mixtureDistanceMeasure = MixtureDistanceMeasure.MANHATTAN,
                queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY)
//
//        val boundSheafDistFunction2 = bindSheafDist(
//                startLayer = 1, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_AVERAGE,
//                normalize = false, mixtureDistanceMeasure = MixtureDistanceMeasure.EUCLIDEAN,
//                queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY_EXPANSION)
//
//        val boundSheafDistFunction3 = bindSheafDist(
//                startLayer = 1, measureLayer = 3, reductionMethod = ReductionMethod.REDUCTION_MAX_MAX,
//                normalize = false, mixtureDistanceMeasure = MixtureDistanceMeasure.EUCLIDEAN,
//                queryEmbeddingMethod = SheafQueryEmbeddingMethod.QUERY_EXPANSION)

        formatter.addBM25(normType = NormType.ZSCORE)
//        formatter.addFeature(bindEmbed, normType = NormType.ZSCORE)
        formatter.addFeature(boundSheafDistFunction, normType = NormType.ZSCORE)
        formatter.addFeature(boundSheafDistFunction2, normType = NormType.ZSCORE)
//        formatter.addFeature(boundSheafDistFunction3, normType = NormType.ZSCORE)
    }


    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("hi")
                .help("Hello")
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

            parser.help("Does stuff")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {

                    methods<MasterExperiment> {
                        trainMethod("doClust") { doClust() }
                        queryMethod("wee2") { wee() }
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