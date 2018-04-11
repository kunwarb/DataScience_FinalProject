package edu.unh.cs980.misc

import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser

@DslMarker annotation class DispatchDSL

data class ResourceContainer(val arg: String, val help: String, val loader: (String) -> Any)



class KotlinResourceDispatcher(val resourceContainers: List<ResourceContainer>) {

    fun generateArguments(parser: Subparser) {
        resourceContainers.forEach { container ->
            parser.addArgument("--${container.arg}")
                .help(container.help)
        }
    }

    fun loadResources(namespace: Namespace): HashMap<String, Any> {
        val resources = HashMap<String, Any>()

        resourceContainers.forEach { container ->
            val resourceArgument = namespace.get<String>(container.arg)
            resources[container.arg] = container.loader(resourceArgument)
        }

        return resources
    }

    operator fun plus(other: KotlinResourceDispatcher): KotlinResourceDispatcher {
        return KotlinResourceDispatcher(resourceContainers + other.resourceContainers)
    }
}



@DispatchDSL
class ResourceContainerBuilder(val arg: String) {
    var help: String = ""
    var loader: (String) -> Any = {}

    fun build(): ResourceContainer {
        return ResourceContainer(arg = arg, help = help, loader = loader)
    }
}


@DispatchDSL
class ResourceDispatchBuilder() {
    private val resourceContainers = ArrayList<ResourceContainer>()

    fun build(): KotlinResourceDispatcher {
        return KotlinResourceDispatcher(resourceContainers = resourceContainers)
    }

    fun resource(arg: String, setup: ResourceContainerBuilder.() -> Unit) {
        val resourceBuilder = ResourceContainerBuilder(arg)
        resourceBuilder.setup()
        resourceContainers += resourceBuilder.build()
    }
}


@DispatchDSL
fun buildResourceDispatcher(setup: ResourceDispatchBuilder.() -> Unit): KotlinResourceDispatcher {
    val dispatchBuilder = ResourceDispatchBuilder()
    dispatchBuilder.setup()
    return dispatchBuilder.build()
}

fun main(args: Array<String>) {
    val b = buildResourceDispatcher {
        resource("index") {
            help = "Location of Lucene index."
            loader = { "hi" }
        }

        resource("number") {
            help = "Number to add things to."
            loader = { 20 }
        }
    }

    b.resourceContainers.forEach { container ->
        println("${container.arg}: ${container.help} : ${container.loader("hi")}")
    }
}

//data class MethodChoice(val arg: String, val help: String, val methods: ArrayList<MethodCaller> = arrayListOf()) {
//    fun addChoice(caller: MethodCaller) = apply { methods += caller }
//}
//
//data class MethodCaller(val choice: String, val method: () -> Unit)

//class Dispatcher() {
//}
//
//
//class MethodCallerBuilder(val methodName: String) {
//
//}
//
//class MethodChoiceBuilder(val arg: String) {
//    var help: String = ""
//    private val methods = ArrayList<MethodCaller>()
//
//    fun method() {
//
//    }
//
//}
//
//class ResourceContainerBuilder(val arg: String) {
//    var help: String = ""
//    var loader: () -> Any = {}
//
//    fun build(): ResourceContainer {
//        return ResourceContainer(arg = arg, help = help, loader = loader)
//    }
//}
//
//class DispatchBuilder() {
//    private val resourceContainers = ArrayList<ResourceContainer>()
//    private val methodChoices = ArrayList<MethodChoice>()
//
//    fun build(): Dispatcher {
//        return Dispatcher()
//    }
//
//    fun resource(argName: String, setup: ResourceContainerBuilder.() -> Unit) {
//        val resourceContainerBuilder = ResourceContainerBuilder(argName)
//        resourceContainerBuilder.setup()
//        resourceContainers += resourceContainerBuilder.build()
//    }
//
//    fun methodChoice(argName: String) {
//
//    }
//}
//
//fun buildDispatcher(setup: DispatchBuilder.() -> Unit): Dispatcher {
//    val dispatchBuilder = DispatchBuilder()
//    dispatchBuilder.setup()
//    return dispatchBuilder.build()
//}
//
//fun main(args: Array<String>) {
//    val dispatcher = KotlinResourceDispatcher()
//
////    fun methodChoice(init: MethodChoice.() -> Unit): MethodChoice {
////        val method = MethodChoice()
////    }
//
//}
