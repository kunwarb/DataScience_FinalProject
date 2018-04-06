package edu.unh.cs980.paragraph

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.jsoup.Jsoup
import khttp.post
import java.io.File
import kotlin.system.exitProcess

val topics = listOf(
        "Science",
        "Medicine",
        "Mathematics",
        "Geography"

)

object KotlinSparql {

    fun getDbcTemplate(entity: String): String {
        val sparTemp = """
        SELECT ?abstract
        WHERE {
            ?entity dct:subject/dbc:subClassOf* dbc:${entity} .
            ?entity dbo:abstract ?abstract .
            filter langMatches(lang(?abstract),"en") .
            filter (strlen(str(?abstract)) > 300)
        }
        LIMIT 100
        """
        return sparTemp
    }

    fun getDboTemplate(entity: String): String {
        val sparTemp = """
        SELECT ?abstract
        WHERE {
            ?entity rdf:type/rdfs:subClassOf* dbo:${entity} .
            ?entity dbo:abstract ?abstract .
            filter langMatches(lang(?abstract),"en") .
            filter (strlen(str(?abstract)) > 300)
        }
        LIMIT 100
        """
        return sparTemp
    }

    fun getBroadTemplate(entity: String): String {
        val sparTemp = """
        SELECT distinct ?abstract
        WHERE {
            ?related skos:broader dbc:$entity .
            ?entity  dct:subject/dbc:subClassOf* ?related .
            ?entity dbo:abstract ?abstract .
            filter langMatches(lang(?abstract),"en") .
            filter (strlen(str(?abstract)) > 300) .
            BIND ( MD5 ( ?entity ) AS ?rnd)
        }
        ORDER BY ?rnd
        LIMIT 100
        """
        return sparTemp
    }

    fun broadestTemplate(entity: String): String {
        val sparTemp = """
            PREFIX vrank:<http://purl.org/voc/vrank#>
            SELECT distinct ?abstract
            FROM <http://people.aifb.kit.edu/ath/#DBpedia_PageRank>
            FROM <http://dbpedia.org>
            WHERE {
                 ?related skos:broader dbc:$entity .
                 {?entity  dct:subject/dbc:subClassOf* ?related } UNION {?entity dct:subject dbc:$entity } .
                 ?entity dbo:abstract ?abstract .
                 filter langMatches(lang(?abstract),"en") .
                 filter (strlen(str(?abstract)) > 500) .
                 ?entity vrank:hasRank/vrank:rankValue ?v .
                  }
            ORDER BY DESC(?v) LIMIT 50
            """
        return sparTemp
    }




    fun doSearch(entity: String): List<String> {
        val doc = Jsoup.connect("http://dbpedia.org/sparql")
            .data("query", broadestTemplate(entity))
            .post()

        val elements = doc.getElementsByTag("pre")
        return elements.map { element -> element.text() }
    }

    fun writeResults(category: String, results: List<String>) {
        val outDir = "paragraphs/$category/"
        File(outDir).apply { if (!exists()) mkdirs() }

        results.forEachIndexed { index, doc ->
            File("${outDir}doc_$index.txt").writeText(doc.take(doc.length - 3).replace("\"", ""))
        }
    }


}


//fun tryPiece(filename: String) {
//    val f = File(filename).inputStream().buffered(16 * 1024)
//
//    DeserializeData.iterableAnnotations(f)
//        .take(10)
//        .forEach {  page ->
//            println("Title: ${page.pageName}")
//            println("IDS: ${page.pageMetadata.categoryIds.joinToString("\n\t")}")
//            println("Count: ${page.pageMetadata.categoryIds.size}")
//
//        }
//
//}


fun main(args: Array<String>) {
//    val topics = listOf("Cooking", "Mathematics", "Society", "Games", "Cuisine", "Science", "Statistics", "Engineering", "Statistics" )
    val topics = listOf("Computers", "Cooking", "Cuisine", "Engineering", "Games", "Mathematics", "Society", "Statistics", "Technology", "Science")
//    val topics = listOf("Technology", "Engineering")
    topics.forEach { topic ->
        val results = KotlinSparql.doSearch(topic)
        KotlinSparql.writeResults(topic, results)
    }
//    KotlinSparql.doTraining()
//    tryPiece("/home/hcgs/Desktop/projects/data_science/DataScience_FinalProject/piece.cbor")

}