package edu.unh.cs980;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import edu.unh.cs980.language.KotlinAbstractAnalyzer;
import edu.unh.cs980.language.KotlinAbstractExtractor;
import edu.unh.cs980.language.KotlinGram;
import edu.unh.cs980.language.KotlinGramAnalyzer;
import edu.unh.cs980.ranklib.KotlinRankLibTrainer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;

import co.nstant.in.cbor.CborException;
import edu.unh.cs980.WordEmbedding.Lucene_Query_Creator;
import edu.unh.cs980.ranklib.KotlinRanklibFormatter;
import edu.unh.cs980.ranklib.NormType;
import edu.unh.cs980.utils.ProjectUtils;
import edu.unh.cs980.utils.QueryBuilder;
import edu.unh.cs980.variations.FreqBigram_variation;
import edu.unh.cs980.variations.QueryExpansion_variation;
import kotlin.jvm.functions.Function3;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class Main {

	// Used as a wrapper around a static method: will call method and pass
	// argument parser's parameters to it
	private static class Exec {
		private Consumer<Namespace> func;

		Exec(Consumer<Namespace> funcArg) {
			func = funcArg;
		}

		void run(Namespace params) {
			func.accept(params);
		}
	}

	public static ArgumentParser createArgParser() {
		ArgumentParser parser = ArgumentParsers.newFor("program").build();
		Subparsers subparsers = parser.addSubparsers(); // Subparsers is used to
														// create subcommands

		// Add subcommand for running index program
		Subparser indexParser = subparsers.addParser("index") // index is the
																// name of the
																// subcommand
				.setDefault("func", new Exec(Main::runIndexer)).help("Indexes paragraph corpus using Lucene.");
		indexParser.addArgument("corpus").required(true).help("Location to paragraph corpus file (.cbor)");
		indexParser.addArgument("--spotlight_folder").setDefault("")
				.help("Directory containing spotlight jar file and model."
						+ "If the directory doesn't exist, the required files are downloaded automatically."
						+ "If no folder is specified, entity annotation is skipped.");
		indexParser.addArgument("--out").setDefault("index")
				.help("Directory name to create for Lucene index (default: index)");

		// You can add more subcommands below by calling subparsers.addparser
		// and following the examples above
		Subparser queryHeadingParser = subparsers.addParser("query_heading")
				.setDefault("func", new Exec(Main::runQueryHeadingWeights)).help("Queries Lucene database.");
		queryHeadingParser.addArgument("query_type")
				.choices("page", "section", "just_the_page", "just_the_lowest_heading", "interior_heading",
						"word_embedding")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n"
						+ "\tjust_the_page: Page name query.\n" + "\tlowest_heading: Lowest heading of query\n"
						+ "\tinterior_heading: Interior heading of query.\n"
						+ "\tword_embedding: Word embedding on the query headers.");
		queryHeadingParser.addArgument("index").help("Location of Lucene index directory.");
		queryHeadingParser.addArgument("query_file").help("Location of the query file (.cbor)");
		queryHeadingParser.addArgument("--out") // -- means it's not positional
				.setDefault("query_results.run") // If no --out is supplied,
													// defaults to
													// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: query_results.run)");

		// Argument parser for Query Expansion
		Subparser queryExpansionParser = subparsers.addParser("query_expansion")
				.setDefault("func", new Exec(Main::runQueryExpansion)).help("Use Query Expansion");
		queryExpansionParser.addArgument("query_type").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		queryExpansionParser.addArgument("index").help("Location of Lucene index directory.");
		queryExpansionParser.addArgument("query_file").help("Location of the query file (.cbor)");
		queryExpansionParser.addArgument("--out") // -- means it's not
													// positional
				.setDefault("query_results.run") // If no --out is supplied,
				// defaults to
				// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: query_results.run)");

		Subparser bigramParser = subparsers.addParser("frequent_bigram")
				.setDefault("func", new Exec(Main::runFreqBigram)).help("Queries Lucene database.");
		bigramParser.addArgument("query_type").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		bigramParser.addArgument("index").help("Location of Lucene index directory.");
		bigramParser.addArgument("query_file").help("Location of the query file (.cbor)");
		bigramParser.addArgument("--out") // -- means it's not
											// positional
				.setDefault("query_results.run") // If no --out is supplied,
				// defaults to
				// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: query_results.run)");


		// Graph Builder
		Subparser graphBuilderParser = subparsers.addParser("graph_builder")
				.setDefault("func", new Exec(Main::runGraphBuilder))
				.help("Creates bipartite graph between entities and paragraphs, stored in a MapDB file:" +
						"graph_database.db");

		graphBuilderParser.addArgument("index")
				.help("Location of the Lucene index directory");

		// Ranklib Query
		Subparser ranklibQueryParser = subparsers.addParser("ranklib_query")
				.setDefault("func", new Exec(Main::runRanklibQuery))
				.help("Runs queries using weighted combinations of features trained by RankLib.");

		ranklibQueryParser.addArgument("method")
				.help("The type of method to use when querying (see readme).")
				.choices("entity_similarity", "average_query", "split_sections", "mixtures", "combined",
						"lm_mercer", "lm_dirichlet");

		ranklibQueryParser.addArgument("index").help("Location of Lucene index directory.");
		ranklibQueryParser.addArgument("query").help("Location of query file (.cbor)");
		ranklibQueryParser.addArgument("--out")
				.setDefault("query_results.run")
				.help("Specifies the output name of the run file.");
		ranklibQueryParser.addArgument("--graph_database")
				.setDefault("")
				.help("(only used for mixtures method): Location of graph_database.db file.");

		// Ranklib Trainer
		Subparser ranklibTrainerParser = subparsers.addParser("ranklib_trainer")
				.setDefault("func", new Exec(Main::runRanklibTrainer))
				.help("Scores using methods and writes features to a RankLib compatible file for use with training.");

		ranklibTrainerParser.addArgument("method")
				.help("The type of method to use when training (see readme).")
				.choices("abstract_score");
		ranklibTrainerParser.addArgument("index").help("Location of the Lucene index directory");
		ranklibTrainerParser.addArgument("query").help("Location of query file (.cbor)");
		ranklibTrainerParser.addArgument("qrel").help("Locations of matching qrel file.");
		ranklibTrainerParser.addArgument("--out")
				.setDefault("ranklib_features.txt")
				.help("Output name for the RankLib compatible feature file.");
		ranklibTrainerParser.addArgument("--graph_database")
				.setDefault("")
				.help("(only used for mixtures method): Location of graph_database.db file.");

		// Gram
		Subparser gramParser = subparsers.addParser("gram")
				.setDefault("func", new Exec(Main::runGram))
				.help("");

		gramParser.addArgument("corpus")
				.help("Location of paragraph corpus to index.");

		gramParser.addArgument("--database")
				.setDefault("gram")
				.help("");

        // Abstract
        Subparser abstractParser = subparsers.addParser("abstract")
                .setDefault("func", new Exec(Main::runAbstract))
                .help("");
        abstractParser.addArgument("corpus")
                .help("Location of paragraph corpus to index.");

        // Abstract Analyzer
		Subparser abstractAnalyzerParser = subparsers.addParser("abstract_analyzer")
				.setDefault("func", new Exec(Main::runAbstractAnalyzer))
				.help("");
		abstractAnalyzerParser.addArgument("index")
				.help("Location of abstract index.");

		// Gram Analyzer
		Subparser gramAnalyzerParser = subparsers.addParser("gram_analyzer")
				.setDefault("func", new Exec(Main::runGramAnalyzer))
				.help("");
		gramAnalyzerParser.addArgument("index")
				.help("Location of abstract index.");


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

	private static void runGram(Namespace params) {
		String indexLocation = params.getString("database");
		String corpusFile = params.getString("corpus");
		KotlinGram kotlinGram = new KotlinGram(indexLocation);
		kotlinGram.indexGrams(corpusFile);
	}

    private static void runGramAnalyzer(Namespace params) {
        String indexLocation = params.getString("index");
		KotlinGramAnalyzer gramAnalyzer = new KotlinGramAnalyzer(indexLocation);
		gramAnalyzer.runTest();
    }

	private static void runAbstract(Namespace params) {
		String corpusFile = params.getString("corpus");
		KotlinAbstractExtractor extractor = new KotlinAbstractExtractor("abstract");
		extractor.getAbstracts(corpusFile);
	}

	private static void runAbstractAnalyzer(Namespace params) {
		String index = params.getString("index");
		KotlinAbstractAnalyzer analyzer = new KotlinAbstractAnalyzer(index);
		analyzer.runTest();
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
			Lucene_Query_Creator qCreator = new Lucene_Query_Creator("", queryType, standardAnalyzer, sim, index);

			qCreator.writeRankings(queryFile, out);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Runs Kevin's Query Expansion Variation
	private static void runQueryExpansion(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType == "pages") {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				System.out.println("Page queries: " + pages_queries.size());
				ArrayList<String> page_run = QueryExpansion_variation.getSearchResult(pages_queries, index);
				ProjectUtils.writeToFile(out, page_run);

			} else if (queryType == "section") {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				System.out.println("Section queries: " + section_queries.size());
				ArrayList<String> section_run = QueryExpansion_variation.getSearchResult(section_queries, index);
				ProjectUtils.writeToFile(out, section_run);

			} else {
				System.out.println("Error: QueryType not recognized.");
			}

		} catch (Throwable e) {
			e.printStackTrace();
		}

	}

	// Run Kevin's Frequent Bigram Variation
	private static void runFreqBigram(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType == "pages") {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				System.out.println("Page queries: " + pages_queries.size());
				ArrayList<String> page_run = FreqBigram_variation.getSearchResult(pages_queries, index);
				ProjectUtils.writeToFile(out, page_run);

			} else if (queryType == "section") {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				System.out.println("Section queries: " + section_queries.size());
				ArrayList<String> section_run = FreqBigram_variation.getSearchResult(section_queries, index);
				ProjectUtils.writeToFile(out, section_run);

			} else {
				System.out.println("Error: QueryType not recognized.");
			}

		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	// Runs Jordan's Graph Builder
	private static void runGraphBuilder(Namespace namespace) {
		String indexLocation = namespace.getString("index");
		KotlinGraphBuilder graphBuilder = new KotlinGraphBuilder(indexLocation);
		graphBuilder.run();
	}


	// Runs Jordan's Ranklib Trainer
	private static void runRanklibTrainer(Namespace namespace) {
		String indexLocation = namespace.getString("index");
		String qrelLocation = namespace.getString("qrel");
		String queryLocation = namespace.getString("query");
		String graphLocation = namespace.getString("graph_database");
		String out = namespace.getString("out");
		String method = namespace.getString("method");
		KotlinRankLibTrainer kotTrainer =
				new KotlinRankLibTrainer(indexLocation, queryLocation, qrelLocation, graphLocation);
		kotTrainer.train(method, out);
	}


	// Runs Jordan's Ranklib Query
	private static void runRanklibQuery(Namespace namespace) {
		String indexLocation = namespace.getString("index");
		String queryLocation = namespace.getString("query");
		String graphLocation = namespace.getString("graph_database");
		String method = namespace.getString("method");
		String out = namespace.getString("out");
		KotlinRankLibTrainer kotTrainer =
				new KotlinRankLibTrainer(indexLocation, queryLocation, "", graphLocation);
		kotTrainer.runRanklibQuery(method, out);
	}

	private static void runDemo(Namespace params) {
		String indexLocation = params.getString("index");
		String queryLocation = params.getString("query");
		String method = params.getString("method");

		KotlinRanklibFormatter formatter = new KotlinRanklibFormatter(queryLocation, "", indexLocation);

		// Your function must be cast using the signature below
		Function3<? super String, ? super TopDocs, ? super IndexSearcher, ? extends List<Double>> exampleFunction = Main::testFunction;

		// This adds BM25's scores as a feature
		formatter.addBM25(1.0, NormType.NONE);

		// And this adds your custom function/method that has the Function3
		// signature listed above (just cast it)
		formatter.addFeature(exampleFunction, 1.0, NormType.NONE);

		// This will rerank queries by doing the following: sum the added
		// features together (multiplied by weights)
		// And then using these new scores, sort the TopDocs from highest to
		// lowest
		formatter.rerankQueries();

		// This will write the reranked queries to a new trec_car compatible run
		// file
		formatter.writeQueriesToFile("test.run");
	}

	// This is an example of a method that is compatible with
	// KotlinRanklibFormatter's addFeature
	// queryString: Name of the sectionpath query
	// tops: Top 100 documents returned after querying using BM25
	// indexSearcher: IndexSearcher that has access to the Lucene database (use
	// this if you need to)
	public static List<Double> testFunction(String queryString, TopDocs tops, IndexSearcher indexSearcher) {
		ArrayList<Double> scores = new ArrayList<>();
		for (ScoreDoc sc : tops.scoreDocs) {
			scores.add((double) sc.score);
		}
		return scores;
	}

	// Main class for project
	public static void main(String[] args) {
		System.setProperty("file.encoding", "UTF-8");
		ArgumentParser parser = createArgParser();

		try {
			// This parses the arguments (based on createArgParser) and returns
			// the results
			Namespace params = parser.parseArgs(args);

			// We store the function that handles using these parameters in the
			// "func" field
			// In this example, we retrieve the parameter and cast it as Exec,
			// which is used to run the method reference
			// That was passed to it when the Exec was created.
			((Exec) params.get("func")).run(params);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
	}

}
