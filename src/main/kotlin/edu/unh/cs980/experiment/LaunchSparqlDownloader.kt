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
import edu.unh.cs980.paragraph.KotlinSparql
import edu.unh.cs980.ranklib.KotlinRanklibFormatter
import edu.unh.cs980.ranklib.NormType
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser
import net.sourceforge.argparse4j.inf.Subparsers
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs

class LaunchSparqlDownloader(val resources: HashMap<String, Any>) {



    fun downloadAbstracts() {
        KotlinSparql.extractCategoryAbstracts()
    }

    fun downloadPages() {
        KotlinSparql.extractWikiPages()
    }





    companion object {
        fun addExperiments(mainSubparser: Subparsers) {
            val mainParser = mainSubparser.addParser("sparql_downloader_")
                .help("Downloads abstracts and pages for topics.")
            register("", mainParser)


        }

        fun register(methodType: String, parser: Subparser)  {
            val exec = Main.buildExec { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")
                val instance = LaunchSparqlDownloader(resources)
                val method = dispatcher.methodContainer!! as MethodContainer<LaunchSparqlDownloader>
                method.getMethod("", methodName)?.invoke(instance)

            }

            parser.help("Launch method")
            parser.setDefault("func", exec)
            dispatcher.generateArguments(methodType, parser)
        }


        val dispatcher =
                buildResourceDispatcher {
                    methods<LaunchSparqlDownloader> {
                        method("", "abstracts") { downloadAbstracts() }
                        method("", "pages") { downloadPages() }
                    }
                }
    }

}