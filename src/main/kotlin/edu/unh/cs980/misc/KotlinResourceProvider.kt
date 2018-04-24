package edu.unh.cs980.misc

import net.sourceforge.argparse4j.inf.Namespace
import kotlin.reflect.KProperty

class KotlinResourceProvider(val resources: Map<String, Any>) {

    companion object {
        val sharedResources = HashMap<String, Any>()
    }
}