@file: JvmName("LaunchSparqlDownloader")
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

class LaunchSparqlDownloader(val resources: HashMap<String, Any>) {



    fun run() {
    }


//
//
//
//    companion object {
//        fun addExperiments(mainSubparser: Subparsers) {
//            val mainParser = mainSubparser.addParser("sparql_")
//                .help("Hello")
////            dispatcher.generateArguments("", mainParser)
//
////            val exec = Main.buildExec { namespace: Namespace -> MasterExperiment(namespace).run() }
////            mainParser.setDefault("func", exec)
//
//            val subparsers = mainParser.addSubparsers()
//            val trainParser = subparsers.addParser("train")
//                .help("Training methods for RankLib")
//            register("train", trainParser)
//
//            val queryParser = subparsers.addParser("query")
//                .help("Query methods for RankLib")
//            register("query", queryParser)
//
//        }
//
//        fun register(methodType: String, parser: Subparser)  {
//            val exec = Main.buildExec { namespace: Namespace ->
//                val resources = dispatcher.loadResources(namespace)
//                val methodName = namespace.get<String>("method")
//                val instance = MasterExperiment(resources)
//                val method = dispatcher.methodContainer!! as MethodContainer<LaunchSparqlDownloader>
//                method.getMethod(methodType, methodName)?.invoke(instance)
//
//                if (methodType == "query") {
//                    instance.formatter
//                        .apply { rerankQueries() }
//                        .writeQueriesToFile(instance.out)
//                } else {
//                    instance.formatter.writeToRankLibFile("ranklib_results.txt")
//                }
//            }
//
//            parser.help("Does stuff")
//            parser.setDefault("func", exec)
//            dispatcher.generateArguments(methodType, parser)
//        }
//
//
//        val dispatcher =
//                buildResourceDispatcher {
//
//                    methods<LaunchSparqlDownloader> {
//                        trainMethod("doClust") { doClust() }
//                        queryMethod("wee2") { wee() }
//                    }
//
//                    resource("indexPath") {
//                        help = "Location of the Lucene index database (Default: /trec_data/team_1/myindex"
//                        default = "/trec_data/team_1/myindex"
//                        loader = ::identity
//                    }
//
//                    resource("qrelPath") {
//                        help = "Location to qrel file"
//                        default = ""
//                        loader = ::identity
//                    }
//
//                    resource("queryPath") {
//                        help = "Location to query (.cbor) file."
//                        loader = ::identity
//                    }
//
//                    resource("out") {
//                        help = "Name of query file to create"
//                        default = "query_results.run"
//                        loader = ::identity
//                    }
//
//
//                    resource("gram") {
//                        help = "Location to gram Lucene index (Default: /trec_data/team_1/gram)"
//                        default = "/trec_data/team_1/gram/"
//                        loader = { path -> KotlinGramAnalyzer(getIndexSearcher(path)) }
//                    }
//
//                    resource("paragraphs") {
//                        help = "Location to paragraphs training directory (Default: /trec_data/team_1/paragraphs)"
//                        default = "/trec_data/team_1/paragraphs/"
//                        loader = ::identity
//                    }
//
//                    resource("descent_data") {
//                        help = "Location to descent data directory (Default: /trec_data/team_1/descent_data)"
//                        default = "/trec_data/team_1/descent_data/"
//                        loader = ::identity
//                    }
//
//                }
//    }

}