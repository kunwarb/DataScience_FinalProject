package edu.unh.cs980.misc

import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser

@DslMarker annotation class DispatchDSL

data class ResourceContainer(val arg: String, val help: String, val default: String = "", val loader: (String) -> Any)

data class MethodCaller<T> (val choice: String, val method: T.() -> Unit)

data class MethodContainer<T>(val arg: String, val help: String,
                              val methodMap: Map<String, List<MethodCaller<T>>>) {
//                              val queryMethods: List<MethodCaller<T>>,
//                              val trainMethods: List<MethodCaller<T>>) {

//    fun getMethod(methodType: String, methodName: String): (T.() -> Unit)? {
//        val methods = if (methodType == "query") queryMethods else trainMethods
//        return methods
//            .find { methodCaller -> methodCaller.choice == methodName}
//            ?.method
//    }

    fun getMethod(methodType: String, methodName: String): (T.() -> Unit)? {
        return methodMap[methodType]!!
            .find { methodCaller -> methodCaller.choice == methodName}
            ?.method

//        val methods = if (methodType == "query") queryMethods else trainMethods
//        return methods
//            .find { methodCaller -> methodCaller.choice == methodName}
//            ?.method
    }

    fun getMethodChoices(methodType: String): List<String> {
        return methodMap[methodType]!!.map { method -> method.choice }

//        val methods = if (methodType == "query") queryMethods else trainMethods
//        return methods.map { method -> method.choice }
    }
}




class KotlinResourceDispatcher(val resourceContainers: List<ResourceContainer>,
                               val methodContainer: MethodContainer<*>? = null) {

    fun generateArguments(methodType: String, parser: Subparser) {
        methodContainer?.let { container ->
            parser.addArgument("--${container.arg}")
                .choices(container.getMethodChoices(methodType))
                .help(container.help)
        }
        resourceContainers.forEach { container ->
            parser.addArgument("--${container.arg}")
                .setDefault(container.default)
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
class MethodContainerBuilder<T>(val arg: String) {
    var help: String = ""
    private val methods = HashMap<String, ArrayList<MethodCaller<T>>>()
//    private val trainMethods = ArrayList<MethodCaller<T>>()
//    private val queryMethods = ArrayList<MethodCaller<T>>()

//    fun trainMethod(choice: String, method: T.() -> Unit) {
//        trainMethods += MethodCaller(choice, method)
//    }
//
//    fun queryMethod(choice: String, method: T.() -> Unit) {
//        queryMethods += MethodCaller(choice, method)
//    }

    fun method(category: String, choice: String, method: T.() -> Unit) {
        methods.computeIfAbsent(category, { arrayListOf() }).add(MethodCaller(choice, method))
//        queryMethods += MethodCaller(choice, method)
    }

    fun build(): MethodContainer<T> {
//        return MethodContainer(arg, help, queryMethods = queryMethods, trainMethods = trainMethods)
        return MethodContainer(arg, help, methodMap = methods)
    }

}


@DispatchDSL
class ResourceContainerBuilder(val arg: String) {
    var help: String = ""
    var default: String = ""
    var loader: (String) -> Any = {}

    fun build(): ResourceContainer {
        return ResourceContainer(arg = arg, help = help, default = default, loader = loader)
    }
}


@DispatchDSL
class ResourceDispatchBuilder() {
    private val resourceContainers = ArrayList<ResourceContainer>()
    private var methodContainer: MethodContainer<*>?  = null

    fun build(): KotlinResourceDispatcher {
        return KotlinResourceDispatcher(resourceContainers = resourceContainers, methodContainer = methodContainer)
    }

    fun<T> methods(arg: String = "method", setup: MethodContainerBuilder<T>.() -> Unit) {
        val methodContainerBuilder = MethodContainerBuilder<T>(arg)
        methodContainerBuilder.setup()
        methodContainer = methodContainerBuilder.build()
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

