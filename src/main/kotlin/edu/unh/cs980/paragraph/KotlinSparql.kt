package edu.unh.cs980.paragraph

import edu.unh.cs.treccar_v2.Data
import edu.unh.cs.treccar_v2.read_data.DeserializeData
import org.jsoup.Jsoup
import khttp.post
import java.io.File
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory
import org.deeplearning4j.models.word2vec.VocabWord
import org.deeplearning4j.text.documentiterator.FileLabelAwareIterator
import org.deeplearning4j.text.documentiterator.LabelledDocument
import org.deeplearning4j.text.documentiterator.LabelAwareIterator

import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory
import org.nd4j.linalg.io.ClassPathResource
import org.slf4j.LoggerFactory
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
            File("${outDir}doc_$index.txt").writeText(doc.take(doc.length - 3))
        }
    }

    fun doTraining() {
//        val resource: ClassPathResource = ClassPathResource("paragraphs/")
        val direc = File("paragraphs/")

        val iterator = FileLabelAwareIterator
            .Builder()
            .addSourceFolder(direc)
            .build()

        val tokenFactory = DefaultTokenizerFactory()
            .apply { tokenPreProcessor = CommonPreprocessor() }

        val pVectors = ParagraphVectors.Builder()
            .learningRate(0.025)
            .minLearningRate(0.001)
            .batchSize(100)
            .epochs(5)
            .iterate(iterator)
            .trainWordVectors(true)
            .tokenizerFactory(tokenFactory)
            .build()


        pVectors.fit()
//        println(pVectors.inferVector("This is a test to see if you can infer."))
        val vWords = arrayListOf(
                VocabWord(0.5, "hi"),
                VocabWord(2.0, "Cooking")
        )
        val vWords2 = arrayListOf(
                VocabWord(0.5, "blah"),
                VocabWord(2.0, "how")
        )
        println(pVectors.similarity("Cooking", "wine"))
        println(pVectors.similarity("chef", "Cooking"))
//        println(pVectors.similarity("Chef", "wine"))
//        println(pVectors.predict(vWords))
//        println(pVectors.similarityToLabel(vWords, "Cooking"))
//        println(pVectors.similarityToLabel(vWords2, "Cooking"))
        println("Got here")
        exitProcess(0)
//        println(pVectors.getWordVectorsMean(arrayListOf("Cooking")))
//        doTraining2(pVectors!!)
    }

    private fun doTraining2(pVectors: ParagraphVectors) {
        val tests = File("paragraphs/Cooking")
        val iterator = FileLabelAwareIterator.Builder()
            .addSourceFolder(tests)
            .build()

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
    val topics = listOf("Cooking", "Mathematics", "Society", "Games", "Cuisine", "Science", "Statistics")
    topics.forEach { topic ->
        val results = KotlinSparql.doSearch(topic)
        KotlinSparql.writeResults(topic, results)
    }
//    KotlinSparql.doTraining()
//    tryPiece("/home/hcgs/Desktop/projects/data_science/DataScience_FinalProject/piece.cbor")

}