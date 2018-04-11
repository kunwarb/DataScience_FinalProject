@file: JvmName("KotStupidTest")
package edu.unh.cs980.experiment

import edu.unh.cs980.Main
import edu.unh.cs980.identity
import edu.unh.cs980.misc.MethodContainer
import edu.unh.cs980.misc.buildResourceDispatcher
import edu.unh.cs980.ranklib.KotlinRanklibFormatter
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser

class StupidTest(resources: HashMap<String, Any>) {
    val index: List<String> by resources
    val out: String by resources

    fun run() {
        println("Loaded: $index and $out")
    }

    fun run2() {
        println("Loaded2: $index and $out")
    }

    companion object: ExperimentInterface {

        override fun register(methodType: String, parser: Subparser)  {
            val exec = { namespace: Namespace ->
                val resources = dispatcher.loadResources(namespace)
                val methodName = namespace.get<String>("method")

                val instance = StupidTest(resources)
                val methodCaller = dispatcher.methodContainer!! as MethodContainer<StupidTest>
                methodCaller.getMethod(methodType, methodName)?.invoke(instance)
            }


            parser.help("Does stuff")
            parser.setDefault("methodFunc", exec)
            dispatcher.generateArguments(methodType, parser)
        }

//        override fun register(methodType: String, parser: Subparser)  {
//            val exec = Main.buildExec { namespace: Namespace ->
//                val resources = dispatcher.loadResources(namespace)
//                val methodName = namespace.get<String>("method")
//                val instance = StupidTest(resources)
//                val method = dispatcher.methodContainer!! as MethodContainer<StupidTest>
//                method.getMethod(methodType, methodName)?.invoke(instance)
//            }
//
//            parser.help("Does stuff")
//            parser.setDefault("func2", exec)
//            dispatcher.generateArguments(methodType, parser)
//        }

        val dispatcher =
                buildResourceDispatcher {
                    methods<StupidTest> {
                        trainMethod("run") { run() }
                        trainMethod("run2") { run2() }
                    }
                    resource("index") {
                        help = "Location of Lucene index."
                        loader = { arg -> listOf(arg) }
                    }

//                    resource("out") {
//                        help = "Output location"
//                        loader = ::identity
//                    }
                }
    }
}

