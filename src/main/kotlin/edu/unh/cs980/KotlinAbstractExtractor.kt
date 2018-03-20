@file:JvmName("KotAbstractExtractor")
package edu.unh.cs980

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class KotlinAbstractExtractor(filename: String) {
    val indexWriter = getIndexWriter(filename)


    fun getAbstracts(filename: String) {
        val f = File(filename).inputStream().buffered(16 * 1024)
        val counter = AtomicInteger()

        DeserializeData.iterableAnnotations(f)
            .forEachParallel { page ->

                // This is just to keep track of how many pages we've parsed
                counter.incrementAndGet().let {
                    if (it % 100000 == 0) {
                        println(it)
                        indexWriter.commit()
                    }
                }

                val name = page.pageName.toLowerCase().replace(" ", "_")
                val content = page.flatSectionPathsParagraphs()
                    .take(4)
                    .map { psection -> psection.paragraph.textOnly }
                    .joinToString(" ")

                val doc = Document()
                doc.add(TextField("name", name, Field.Store.YES))
                doc.add(TextField("text", content, Field.Store.YES))
                indexWriter.addDocument(doc)
            }

        indexWriter.close()
    }
}

