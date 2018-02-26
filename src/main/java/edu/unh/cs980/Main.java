package edu.unh.cs980;
import co.nstant.in.cbor.CborException;
import edu.unh.cs980.WordEmbedding.Lucene_Query_Creator;
import edu.unh.cs980.ranklib.KotlinRanklibFormatter;
import edu.unh.cs980.ranklib.NormType;
import kotlin.Pair;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Main {

    // Used as a wrapper around a static method: will call method and pass argument parser's parameters to it
    private static class Exec {
        private Consumer<Namespace> func;
        Exec(Consumer<Namespace> funcArg) { func = funcArg; }
        void run(Namespace params) { func.accept(params);}
    }

    public static ArgumentParser createArgParser() {
        ArgumentParser parser = ArgumentParsers.newFor("program").build();
        Subparsers subparsers = parser.addSubparsers(); // Subparsers is used to create subcommands

        // Add subcommand for running index program
        Subparser indexParser = subparsers.addParser("index")                  // index is the name of the subcommand
                .setDefault("func", new Exec(Main::runIndexer))
                .help("Indexes paragraph corpus using Lucene.");
        indexParser.addArgument("corpus")
                .required(true)
                .help("Location to paragraph corpus file (.cbor)");
        indexParser.addArgument("--spotlight_folder")
                .setDefault("")
                .help("Directory containing spotlight jar file and model." +
                        "If the directory doesn't exist, the required files are downloaded automatically." +
                        "If no folder is specified, entity annotation is skipped.");
        indexParser.addArgument("--out")
                .setDefault("index")
                .help("Directory name to create for Lucene index (default: index)");


        // You can add more subcommands below by calling subparsers.addparser and following the examples above
        Subparser queryHeadingParser  = subparsers.addParser("query_heading")
                .setDefault("func", new Exec(Main::runQueryHeadingWeights))
                .help("Queries Lucene database.");
        queryHeadingParser.addArgument("query_type")
                .choices("page", "section", "just_the_page", "just_the_lowest_heading",
                        "interior_heading", "word_embedding")
                .help("\tpage: Page of paragraph corpus\n" +
                        "\tsection: Section of paragraph corpus\n" +
                        "\tjust_the_page: Page name query.\n" +
                        "\tlowest_heading: Lowest heading of query\n" +
                        "\tinterior_heading: Interior heading of query.\n" +
                        "\tword_embedding: Word embedding on the query headers.");
        queryHeadingParser.addArgument("index").help("Location of Lucene index directory.");
        queryHeadingParser.addArgument("query_file").help("Location of the query file (.cbor)");
        queryHeadingParser.addArgument("--out") // -- means it's not positional
                .setDefault("query_results.run") // If no --out is supplied, defaults to query_results.txt
                .help("The name of the trec_eval compatible run file to write. (default: query_results.run)");

        //

        return parser;
    }

    private static void runIndexer(Namespace params) {
        String indexLocation = params.getString("out");
        String corpusFile = params.getString("corpus");
        String spotlight_location = params.getString("spotlight_folder");

        try {
            IndexData.indexAllData(indexLocation, corpusFile, spotlight_location);
        } catch (CborException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Runs Bindu's Query Heading Weights Variation
    private static void runQueryHeadingWeights(Namespace params) {
        String index = params.getString("index");
        String queryFile = params.getString("query_file");
        String queryType = params.getString("query_type");
        String out = params.getString("out");
        StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
        BM25Similarity sim = new BM25Similarity();

        try {
            Lucene_Query_Creator qCreator =
                    new Lucene_Query_Creator("", queryType, standardAnalyzer, sim, index);

            qCreator.writeRankings(queryFile, out);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void runDemo(Namespace params) {
        String indexLocation = params.getString("index");
        String queryLocation = params.getString("query");
        String method = params.getString("method");

        KotlinRanklibFormatter formatter = new KotlinRanklibFormatter(queryLocation, "", indexLocation);

        // Your function must be cast using the signature below
        Function3<? super String, ? super TopDocs, ? super IndexSearcher, ? extends List<Double>> exampleFunction
                = Main::testFunction;

        // This adds BM25's scores as a feature
        formatter.addBM25(1.0, NormType.NONE);

        // And this adds your custom function/method that has the Function3 signature listed above (just cast it)
        formatter.addFeature(exampleFunction, 1.0, NormType.NONE);

        // This will rerank queries by doing the following: sum the added features together (multiplied by weights)
        // And then using these new scores, sort the TopDocs from highest to lowest
        formatter.rerankQueries();

        // This will write the reranked queries to a new trec_car compatible run file
        formatter.writeQueriesToFile("test.run");
    }

    // This is an example of a method that is compatible with KotlinRanklibFormatter's addFeature
    // queryString: Name of the sectionpath query
    // tops: Top 100 documents returned after querying using BM25
    // indexSearcher: IndexSearcher that has access to the Lucene database (use this if you need to)
    public static List<Double> testFunction(String queryString, TopDocs tops, IndexSearcher indexSearcher) {
        ArrayList<Double> scores = new ArrayList<>();
        for (ScoreDoc sc : tops.scoreDocs) {
            scores.add((double)sc.score);
        }
        return scores;
    }

    // Main class for project
    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        ArgumentParser parser = createArgParser();

        // Example of running the query program
        // args = new String[4]; args[0] = "query"; args[1] = "bm25"; args[2] = "myindex"; args[3] = "queries.cbor";

        // Example of calling help for the indexer program
        // args = new String[2]; args[0] = "index"; args[1] = "-h";

        // Example of calling help for the query program
        // args = new String[2]; args[0] = "query"; args[1] = "-h";

        try {
            // This parses the arguments (based on createArgParser) and returns the results
            Namespace params = parser.parseArgs(args);

            // We store the function that handles using these parameters in the "func" field
            // In this example, we retrieve the parameter and cast it as Exec, which is used to run the method reference
            // That was passed to it when the Exec was created.
            ((Exec)params.get("func")).run(params);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }

}
