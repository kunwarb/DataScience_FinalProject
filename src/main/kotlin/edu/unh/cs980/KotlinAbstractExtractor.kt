@file:JvmName("KotAbstractExtractor")
package edu.unh.cs980

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class KotlinAbstractExtractor {
    fun getAbstracts(filename: String) {
        val f = File(filename).inputStream().buffered(16 * 1024)
        val counter = AtomicInteger()

        DeserializeData.iterableAnnotations(f)
            .forEach { page ->

                // This is just to keep track of how many pages we've parsed
                counter.incrementAndGet().let {
                    if (it % 100000 == 0) {
                        println(it)
                    }
                }

                // Extract all of the anchors/entities and add them to database
                page.childSections[0]
                    .children
                    .filterIsInstance<Data.Paragraph>()
                    .forEach { println(it) }

//                .flatMap { psection ->
//                    psection.paragraph.bodies
//                        .filterIsInstance<Data.ParaLink>()
//                        .map { paraLink -> paraLink.anchorText.toLowerCase() to paraLink.page.toLowerCase() } }
//                .apply(this::addLinks)
            }
    }
}


