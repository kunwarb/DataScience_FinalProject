package edu.unh.cs980.paragraph

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.jsoup.Jsoup
import khttp.post
import java.io.File
import kotlin.system.exitProcess


object KotlinSparql {
    private val topics = listOf(
            "Biology",
            "Computers",
            "Cooking",
            "Cuisine",
            "Engineering",
            "Environments",
            "Events",
            "Fashion",
            "Games",
            "Mathematics",
            "Medicine",
            "Organizations",
            "People",
            "Politics",
            "Science",
            "Society",
            "Statistics",
            "Technology",
            "Tools",
            "Travel",
            "Warfare"
    )

    private val pageTopics = listOf(
            "Biology",
            "Medicine",
            "Science"
    )


    fun abstractTemplate(entity: String): String {
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

    fun linkTemplate(entity: String): String {
        val sparTemp = """
            PREFIX vrank:<http://purl.org/voc/vrank#>
            SELECT distinct ?link
            FROM <http://people.aifb.kit.edu/ath/#DBpedia_PageRank>
            FROM <http://dbpedia.org>
            WHERE {
                 ?related skos:broader dbc:$entity .
                 {?entity  dct:subject/dbc:subClassOf* ?related } UNION {?entity dct:subject dbc:$entity } .
                 ?entity dbo:abstract ?abstract .
                 ?entity prov:wasDerivedFrom ?link .
                 filter langMatches(lang(?abstract),"en") .
                 filter (strlen(str(?abstract)) > 500) .
                 ?entity vrank:hasRank/vrank:rankValue ?v .
                  }
            ORDER BY DESC(?v) LIMIT 10
            """
        return sparTemp
    }




    fun doSearch(entity: String): List<String> {
        val doc = Jsoup.connect("http://dbpedia.org/sparql")
            .data("query", abstractTemplate(entity))
            .post()

        val elements = doc.getElementsByTag("pre")
        return elements.map { element -> element.text() }
    }

    fun doSearchPage(entity: String): List<String> {
        val doc = Jsoup.connect("http://dbpedia.org/sparql")
            .data("query", linkTemplate(entity))
            .post()

        val elements = doc.getElementsByTag("td")
        return elements.map { element -> element.text() }
    }

    fun writeResults(category: String, results: List<String>, folderName: String) {
        val outDir = "$folderName/$category/"
        File(outDir).apply { if (!exists()) mkdirs() }

        results.forEachIndexed { index, doc ->
            File("${outDir}doc_$index.txt").writeText(doc.take(doc.length - 3).replace("\"", ""))
        }
    }


    fun getWikiText(url: String): String {
        val doc = Jsoup.connect(url).get()
        val paragraphs = doc.select(".mw-content-ltr p")
        return paragraphs.map { p -> p.text() }.joinToString(" ")
    }

    fun extractCategoryAbstracts() {
        topics.forEach { topic ->
            val results = doSearch(topic)
            writeResults(topic, results, "paragraphs")
        }
    }

    fun extractWikiPages() {
        pageTopics.forEach { topic ->
            val results = doSearchPage(topic).map { link -> getWikiText(link) }
            writeResults(topic, results, "pages")
        }
    }

}




fun main(args: Array<String>) {
}