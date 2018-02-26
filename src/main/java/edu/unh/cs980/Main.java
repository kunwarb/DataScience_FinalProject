package edu.unh.cs980;
import co.nstant.in.cbor.CborException;
import edu.unh.cs980.ranklib.KotlinRanklibFormatter;
import edu.unh.cs980.ranklib.NormType;
import kotlin.Pair;
import kotlin.jvm.functions.Function2;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

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

        // Example of adding a second subcommand (query)
        Subparser queryParser = subparsers.addParser("query")
                .setDefault("func", new Exec(Main::runQuery))                   // Pass method reference to Exec to
                .help("Queries Lucene database.");                                 // run method when it is called.

        // This is an example of adding a position argument (query_type) that has multiple choices
        queryParser.addArgument("query_type")
                .choices("bm25", "wordvec", "entity_graph", "query_expansion") // Each string is a choice for this param
                .help("The type of query method to use." +
                        "\nbm25: Standard query using BM25 algorithm." +
                        "\nwordvec: Reranks using to similarity to query." +
                        "\nentity_graph: reranks using entity graph model." +
                        "\nquery_expansion: uses query expansion method.");

        // Another positional argument
        queryParser.addArgument("index")
                .required(true)
                .help("Location of the Lucene index directory.");  // This gets printed when -h or --help is called
        queryParser.addArgument("query_file")
                .required(true)
                .help("(required) Location of the query (.cbor) file.");

        // This is an example of an optional argument
        queryParser.addArgument("--out") // -- means it's not positional
                .setDefault("query_results.txt") // If no --out is supplied, defaults to query_results.txt
                .help("The name of the query results file to write. (default: query_results.txt)");

        // You can add more subcommands below by calling subparsers.addparser and following the examples above
        Subparser demoParser = subparsers.addParser("demo")
                .setDefault("func", new Exec(Main::runDemo))                   // Pass method reference to Exec to
                .help("Queries Lucene database.");                                 // run method when it is called.
        demoParser.addArgument("index").help("Location of Lucene index directory.");
        demoParser.addArgument("query").help("Location of query file (.cbor)");

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

    // Example of a method that takes the parser's Namespace and runs something with it
    private static void runQuery(Namespace params) {
        String index = params.getString("index");
        String queryFile = params.getString("query_file");
        String out = params.getString("out");

    }

    private static void runDemo(Namespace params) {
        String indexLocation = params.getString("index");
        String queryLocation = params.getString("query");
        String method = params.getString("method");

        KotlinRanklibFormatter formatter = new KotlinRanklibFormatter(queryLocation, "", indexLocation);

        // Your function must be cast using the signature below
        Function2<? super String, ? super TopDocs, ? extends List<Double>> ff = Main::testFunction;

        // This adds BM25's scores as a feature
        formatter.addBM25(1.0, NormType.NONE);

        // And this adds your custom function/method that has the Function2 signature listed above (just cast it)
        formatter.addFeature(ff, 1.0, NormType.NONE);

        // This will rerank queries by doing the following: sum the added features together (multiplied by weights)
        // And then using these new scores, sort the TopDocs from highest to loest
        formatter.rerankQueries();

        // This will write the reranked queries to a new trec_car compatible run file
        formatter.writeQueriesToFile("test.run");
    }

    // This is an example of a method that is compatible with KotlinRanklibFormatter's addFeature
    public static List<Double> testFunction(String queryString, TopDocs tops) {
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
