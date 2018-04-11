@file: JvmName("KotStupidTest")
package edu.unh.cs980.misc

import edu.unh.cs980.Main
import edu.unh.cs980.identity
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser

class StupidTest(resources: HashMap<String, Any>) {
    val index: List<String> by resources
    val out: String by resources

    fun run() {
        println("Loaded: $index and $out")
    }

    companion object {

        fun register(parser: Subparser)  {
            val exec = Main.buildExec { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                StupidTest(resources).run()
            }

            parser.setDefault("func", exec)
            dispatcher.generateArguments(parser)
        }

        val dispatcher =
                buildResourceDispatcher {
                    resource("index") {
                        help = "Location of Lucene index."
                        loader = { arg -> listOf(arg)}
                    }

                    resource("out") {
                        help = "Output location"
                        loader = ::identity
                    }
                }
    }
}

