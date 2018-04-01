package edu.unh.cs980;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import edu.unh.cs980.WordEmbedding.TfIdfSimilarity;
import edu.unh.cs980.ranklib.*;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;

import co.nstant.in.cbor.CborException;
import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import edu.unh.cs980.WordEmbedding.EntitySimilarity;
import edu.unh.cs980.WordEmbedding.Lucene_Query_Creator;
import edu.unh.cs980.WordEmbedding.ParagraphSimilarity;
import edu.unh.cs980.WordEmbedding.ParagraphWithWordnet;
import edu.unh.cs980.context.HyperlinkIndexer;
import edu.unh.cs980.language.KotlinAbstractAnalyzer;
import edu.unh.cs980.language.KotlinAbstractExtractor;
import edu.unh.cs980.language.KotlinGram;
import edu.unh.cs980.language.KotlinGramAnalyzer;
import edu.unh.cs980.utils.ProjectUtils;
import edu.unh.cs980.utils.QueryBuilder;
import edu.unh.cs980.variations.Doc_RM_QE_variation;
import edu.unh.cs980.variations.FreqBigram_variation;
import edu.unh.cs980.variations.QueryExpansion_variation;
import edu.unh.cs980.variations.Query_RM_QE_variation;
import kotlin.jvm.functions.Function3;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class Main {

	private static final Logger logger = Logger.getLogger(Main.class);

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

	/****
	 * @Function:getAllPageFromPath
	 * @param indexLocation
	 * @param queryLocation
	 * @param rankingOutputLocation
	 * @return pagelist
	 */

	private static ArrayList<Data.Page> getAllPageFromPath(String indexLocation, String queryLocation,
			String rankingOutputLocation) {
		ArrayList<Data.Page> pageList = new ArrayList<Data.Page>();

		try {

			FileInputStream fis = new FileInputStream(new File(queryLocation));
			for (Data.Page page : DeserializeData.iterableAnnotations(fis)) {

				pageList.add(page);

			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			logger.error(e.getMessage());
		}
		return pageList;
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

		// Argument parser for Paragraph Similarity (Added By Bindu)

		Subparser paragraphSimilarityParser = subparsers.addParser("paragraph_similarity")
				.setDefault("func", new Exec(Main::runParagraphSimilarity)).help("Queries Lucene database.");

		paragraphSimilarityParser.addArgument("index").help("Location of Lucene index directory.");
		paragraphSimilarityParser.addArgument("query_file").help("Location of the query file (.cbor)");
		paragraphSimilarityParser.addArgument("--out") // -- means it's not
														// positional
				.setDefault("query_results.run") // If no --out is supplied,
													// defaults to
													// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: query_results.run)");

		// Argument parser for TFIDF Similarity (Added By Bindu)

		Subparser tfidfSimilarityParser = subparsers.addParser("tfidf_similarity")
				.setDefault("func", new Exec(Main::runTfidfSimilarity)).help("Queries Lucene database.");

		tfidfSimilarityParser.addArgument("index").help("Location of Lucene index directory.");
		tfidfSimilarityParser.addArgument("query_file").help("Location of the query file (.cbor)");
		tfidfSimilarityParser.addArgument("--out") // -- means it's not
													// positional
				.setDefault("query_results.run") // If no --out is supplied,
													// defaults to
													// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: query_results.run)");
		
		
		// Argument parser for Entity Similarity
				Subparser entitySimilarityParser = subparsers.addParser("entitySimilarity")
						.setDefault("func", new Exec(Main::runEntitySimilarity))
						.help("Use EntitySimilarity");
				entitySimilarityParser.addArgument("query_choice").choices("page", "section")
						.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
				entitySimilarityParser.addArgument("multi_thread").choices("true", "false").setDefault("false")
						.help("\ttrue: Run Multi Thread function. (Not Stable)\n" + "\tfalse: Use normal function\n");

				entitySimilarityParser.addArgument("index").help("Location of Lucene index directory.");
				entitySimilarityParser.addArgument("abstract").help("Location of Lucene entity abstract index directory.");
				entitySimilarityParser.addArgument("query_file").help("Location of the query file (.cbor)");
				entitySimilarityParser.addArgument("--out") .setDefault("Entity-Similarity.run") 
						.help("The name of the trec_eval compatible run file to write. (default: EntitySimilarity.run)");
		
				
				
		
				Subparser paragraphwithwordnet;
					paragraphwithwordnet = subparsers.addParser("paragraph_wordnet")
							.setDefault("func", new Exec(Main::runParagraphWordnet)).help("Paragraph Wordnet Similarity");
				paragraphwithwordnet.addArgument("index").help("Location of Lucene index directory.");
				paragraphwithwordnet.addArgument("query_file").help("Location of the query file (.cbor)");
				paragraphwithwordnet.addArgument("outputLocation") // -- means it's not positional
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

		// Argument parser for Query RM Query Expansion
		Subparser query_RM_QE_Parser = subparsers.addParser("query_rm_qe")
				.setDefault("func", new Exec(Main::runQuery_RM_QE))
				.help("Use Query Entity Relevance Model + Query Expansion");
		query_RM_QE_Parser.addArgument("query_type").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		query_RM_QE_Parser.addArgument("multi_thread").choices("true", "false").setDefault("false")
				.help("\ttrue: Run Multi Thread function. (Not Stable)\n" + "\tfalse: Use normal function\n");

		query_RM_QE_Parser.addArgument("index").help("Location of Lucene index directory.");
		query_RM_QE_Parser.addArgument("abstract").help("Location of Lucene entity abstract index directory.");
		query_RM_QE_Parser.addArgument("query_file").help("Location of the query file (.cbor)");
		query_RM_QE_Parser.addArgument("--out") // -- means it's not
												// positional
				.setDefault("query_rm_qe_results.run") // If no --out is
														// supplied,
				// defaults to
				// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: query_rm_qe_results.run)");

		// Argument parser for Doc RM Query Expansion
		Subparser doc_RM_QE_Parser = subparsers.addParser("document_rm_qe")
				.setDefault("func", new Exec(Main::runDoc_RM_QE))
				.help("Use Document Entity Relevance Model +Query Expansion");
		doc_RM_QE_Parser.addArgument("query_type").choices("page", "section")
				.help("\tpage: Page of paragraph corpus\n" + "\tsection: Section of paragraph corpus\n");
		doc_RM_QE_Parser.addArgument("index").help("Location of Lucene index directory.");
		doc_RM_QE_Parser.addArgument("query_file").help("Location of the query file (.cbor)");
		doc_RM_QE_Parser.addArgument("--out") // -- means it's not
												// positional
				.setDefault("doc_rm_qe_results.run") // If no --out is supplied,
				// defaults to
				// query_results.txt
				.help("The name of the trec_eval compatible run file to write. (default: doc_rm_qe_results.run)");


		// Ranklib Query
		Subparser ranklibQueryParser = subparsers.addParser("ranklib_query")
				.setDefault("func", new Exec(Main::runRanklibQuery))
				.help("Runs queries using weighted combinations of features trained by RankLib.");

		ranklibQueryParser.addArgument("method").help("The type of method to use when querying (see readme).")
				.choices(QueryEnum.Companion.getCommands());

		ranklibQueryParser.addArgument("index").help("Location of Lucene index directory.");
		ranklibQueryParser.addArgument("query").help("Location of query file (.cbor)");
		ranklibQueryParser.addArgument("--out").setDefault("query_results.run")
				.help("Specifies the output name of the run file.");
		ranklibQueryParser.addArgument("--hyperlink_database").setDefault("/trec_data/team_1/entity_mentions.db")
				.help("Location to MapDB indexed by Hyperlink Indexer (default: /trec_data/team_1/entity_mentions.db)");
		ranklibQueryParser.addArgument("--abstract_index").setDefault("/trec_data/team_1/abstract")
				.help("Location of Lucene index for entity abstracts (default: /trec_data/team_1/abstract/)");
		ranklibQueryParser.addArgument("--gram_index").setDefault("/trec_data/team_1/gram")
				.help("Location of Lucene index for -grams used in SDM (default: /trec_data/team_1/gram/");

		// Ranklib Trainer
		Subparser ranklibTrainerParser = subparsers.addParser("ranklib_trainer")
				.setDefault("func", new Exec(Main::runRanklibTrainer))
				.help("Scores using methods and writes features to a RankLib compatible file for use with training.");

		ranklibTrainerParser.addArgument("method").help("The type of method to use when training (see readme).")
                .choices(TrainEnum.Companion.getCommands());
		ranklibTrainerParser.addArgument("index").help("Location of the Lucene index directory");
		ranklibTrainerParser.addArgument("query").help("Location of query file (.cbor)");
		ranklibTrainerParser.addArgument("qrel").help("Locations of matching qrel file.");
		ranklibTrainerParser.addArgument("--out").setDefault("ranklib_features.txt")
				.help("Output name for the RankLib compatible feature file.");
		ranklibTrainerParser.addArgument("--hyperlink_database").setDefault("/trec_data/team_1/entity_mentions.db")
				.help("Location to MapDB indexed by Hyperlink Indexer (default: /trec_data/team1/entit_mentions.db)");
		ranklibTrainerParser.addArgument("--abstract_index").setDefault("/trec_data/team_1/abstract")
				.help("Location of Lucene index for entity abstracts (default: /trec_data/team_1/abstract/)");
		ranklibTrainerParser.addArgument("--gram_index").setDefault("/trec_data/team_1/gram")
				.help("Location of Lucene index for -grams used in SDM (default: /trec_data/team_1/gram/");

		// Gram
		Subparser gramParser = subparsers.addParser("gram_indexer").setDefault("func", new Exec(Main::runGram))
				.help("Indexes -gram models for paragraphCorpus. See Readme for further details.");

		gramParser.addArgument("corpus").help("Location of paragraph corpus to index.");

		gramParser.addArgument("--database").setDefault("gram")
				.help("Name of the indexed Lucene database to creature (default is gram)");

		// Abstract Indexer
		Subparser abstractParser = subparsers.addParser("abstract_indexer")
				.setDefault("func", new Exec(Main::runAbstract))
				.help("Creates a Lucene index of entities, where abstract are derived from first three paragraphs."
						+ "See Readme for further details.");
		abstractParser.addArgument("corpus").help("Location of paragraph corpus to index.");

		// FeatureSelection
		Subparser featureParser = subparsers.addParser("feature_selection")
				.setDefault("func", new Exec(Main::runFeatureSelection))
				.help("Performs best subset selection given a RankLib feature file. See ReadMe on Github.");

		featureParser.addArgument("ranklib_jar").help("Location of RankLib jar file.");

		featureParser.addArgument("method").choices("alpha_selection", "subset_selection")
				.help("Method for feature selection / training");

		featureParser.addArgument("--features").setDefault("ranklib_features.txt")
				.help("Location of ranklib features file (default: ranklib_features.txt");


		// Hyperlink Indexer
		Subparser hyperlinkIndexerParser = subparsers.addParser("hyperlink_indexer")
				.setDefault("func", new Exec(Main::runHyperlinkIndexer))
				.help("Builds an entity likelihood model given entity mentions in page corpus.");
		hyperlinkIndexerParser.addArgument("corpus").help("Location of all alllButBenchmark corpus.");

		return parser;
	}

	private static void runIndexer(Namespace params) {
		String indexLocation = params.getString("out");
		String corpusFile = params.getString("corpus");
		String spotlight_location = params.getString("spotlight_folder");

		try {
			IndexData.indexAllData(indexLocation, corpusFile, spotlight_location);
		} catch (CborException e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
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

	private static void runHyperlinkIndexer(Namespace params) {
		String corpus = params.getString("corpus");
		HyperlinkIndexer hyperlinkIndexer = new HyperlinkIndexer("entity_mentions.db", false);
		hyperlinkIndexer.indexHyperlinks(corpus);
	}

	private static void runAbstract(Namespace params) {
		String corpusFile = params.getString("corpus");
		KotlinAbstractExtractor extractor = new KotlinAbstractExtractor("abstract");
		extractor.getAbstracts(corpusFile);
	}

	private static void runFeatureSelection(Namespace params) {
		String ranklibLoc = params.getString("ranklib_jar");
		String method = params.getString("method");
		String featureLoc = params.getString("features");
		KotlinFeatureSelector featureSelector = new KotlinFeatureSelector(ranklibLoc, featureLoc);
		featureSelector.runMethod(method);
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
			logger.error(e.getMessage());
		}
	}

	// Runs Bindu Paragraph Similarity Variation
	public static void runParagraphSimilarity(Namespace params) {
		try {
			String indexLocation = params.getString("index");// Paragraph Corpus
																// indexing
			String queryLocation = params.getString("Outlinecborfile"); // outlines-cbor
																		// file
			String rankingOutputLocation = params.getString("OutputLocationFile");

			ArrayList<Data.Page> pagelist = getAllPageFromPath(indexLocation, queryLocation, rankingOutputLocation);

			ParagraphSimilarity ps = new ParagraphSimilarity(pagelist, 100, indexLocation);
			ps.writeParagraphScore(rankingOutputLocation + "\\ParagraphSimilarity.run");

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}
	
	//Run for Entity Similarity
	
		private static void runEntitySimilarity(Namespace params) {
			try {
				String indexLocation = params.getString("index");
				String abstract_indexLocation = params.getString("abstract");
				String queryFile = params.getString("query_file");
				String queryChoice = params.getString("query_choice");
				String multi = params.getString("multi_thread");
				Boolean runMultiThread = Boolean.valueOf(multi.toLowerCase());
				String out = params.getString("out");
				QueryBuilder queryBuilder = new QueryBuilder(queryFile);
				if (queryChoice.equalsIgnoreCase("page")) {
					ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				
					if (runMultiThread) {
						ArrayList<String> page_run = EntitySimilarity.getResultsWithMultiThread(pages_queries, indexLocation,
								abstract_indexLocation);
						ProjectUtils.writeToFile(out, page_run);
					} else {
						ArrayList<String> page_run = EntitySimilarity.getEntityResults(pages_queries, indexLocation, abstract_indexLocation);
						ProjectUtils.writeToFile(out, page_run);
					}

				} else if (queryChoice.equalsIgnoreCase("section")) {
					ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				
					if (runMultiThread) {
						ArrayList<String> section_run = EntitySimilarity.getResultsWithMultiThread(section_queries,
								indexLocation, abstract_indexLocation);
						ProjectUtils.writeToFile(out, section_run);
					} else {
						ArrayList<String> section_run = EntitySimilarity.getEntityResults(section_queries, indexLocation,
								abstract_indexLocation);
						ProjectUtils.writeToFile(out, section_run);
					}

				} else {
					System.out.println("Error: QueryChoice was not recognized");
				}

			} catch (Throwable e) {
				logger.error(e.getMessage());
			}
		}



	// Runs Bindu Tfidf(lnc.ltc) SImilarity Variation

	public static void runTfidfSimilarity(Namespace params) {
		try {
			String indexLocation = params.getString("index"); // Paragraph
																// Corpus
																// indexing
			String queryLocation = params.getString("Outlinecborfile");
			String rankingOutputLocation = params.getString("OutputLocationFile"); // where
																					// Tf-IDF
																					// output
																					// should
																					// be
			ArrayList<Data.Page> pagelist = getAllPageFromPath(indexLocation, queryLocation, rankingOutputLocation);

			TfIdfSimilarity tfidf = new TfIdfSimilarity(pagelist, 100, indexLocation);
			tfidf.writeTFIDFScoresTo(rankingOutputLocation + "\\Similarity_TFIDF_lnc.ltc.run");
		} catch (Exception e) {
			logger.error(e.getMessage());

		}
	}
	
	
	 //This function is being used for running query
    public static void runParagraphWordnet(Namespace params ) {
    	try{
    	String index = params.getString("index");
		String queryLocation= params.getString("query_file");
		String rankingOutputLocation = params.getString("outputLocation");
		   	
    	ParagraphWithWordnet qbuilder = getQueryBuilder( index, queryLocation, rankingOutputLocation);
		qbuilder.writeRankings(queryLocation, rankingOutputLocation);
    	}
    	catch(IOException e)
    	{
    		e.printStackTrace();
    	}
	}
	
	
	

		  //This function is being used for creating 
  	public static ParagraphWithWordnet getQueryBuilder( String indexLocation, String queryLocation,
  			String rankingOutputLocation) throws IOException {
  	
     return new ParagraphWithWordnet( new StandardAnalyzer(), new BM25Similarity(), indexLocation);
  	}

	// Runs Kevin's Query Expansion Variation
	private static void runQueryExpansion(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				ArrayList<String> page_run = QueryExpansion_variation.getSearchResult(pages_queries, index);
				ProjectUtils.writeToFile(out, page_run);

			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				ArrayList<String> section_run = QueryExpansion_variation.getSearchResult(section_queries, index);
				ProjectUtils.writeToFile(out, section_run);

			} else {
				System.out.println("Error: QueryType not recognized. Got: " + queryType);
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}

	}

	// Runs Kevin's Frequent Bigram Variation
	private static void runFreqBigram(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				ArrayList<String> page_run = FreqBigram_variation.getSearchResult(pages_queries, index);
				ProjectUtils.writeToFile(out, page_run);

			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				ArrayList<String> section_run = FreqBigram_variation.getSearchResult(section_queries, index);
				ProjectUtils.writeToFile(out, section_run);

			} else {
				logger.error("Error: QueryType not recognized. Got: " + queryType);
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}
	}

	// Runs Kevin's Query Entity relevance model + query expansion method
	private static void runQuery_RM_QE(Namespace params) {
		try {
			String index = params.getString("index");
			String abstract_index = params.getString("abstract");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String multi = params.getString("multi_thread");
			Boolean runMultiThread = Boolean.valueOf(multi.toLowerCase());
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				if (runMultiThread) {
					ArrayList<String> page_run = Query_RM_QE_variation.getResultsWithMultiThread(pages_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, page_run);
				} else {
					ArrayList<String> page_run = Query_RM_QE_variation.getResults(pages_queries, index, abstract_index);
					ProjectUtils.writeToFile(out, page_run);
				}

			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				if (runMultiThread) {
					ArrayList<String> section_run = Query_RM_QE_variation.getResultsWithMultiThread(section_queries,
							index, abstract_index);
					ProjectUtils.writeToFile(out, section_run);
				} else {
					ArrayList<String> section_run = Query_RM_QE_variation.getResults(section_queries, index,
							abstract_index);
					ProjectUtils.writeToFile(out, section_run);
				}

			} else {
				logger.error("Error: QueryType not recognized. Got: " + queryType);
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}
	}

	// Runs Kevin's Document Entity Relevance Model + Query Expansion method
	private static void runDoc_RM_QE(Namespace params) {
		try {
			String index = params.getString("index");
			String queryFile = params.getString("query_file");
			String queryType = params.getString("query_type");
			String out = params.getString("out");
			QueryBuilder queryBuilder = new QueryBuilder(queryFile);
			if (queryType.equalsIgnoreCase("page")) {
				ArrayList<String> pages_queries = queryBuilder.getAllpageQueries();
				logger.info("Page queries: " + pages_queries.size());
				ArrayList<String> page_run = Doc_RM_QE_variation.getResults(pages_queries, index);
				ProjectUtils.writeToFile(out, page_run);

			} else if (queryType.equalsIgnoreCase("section")) {
				ArrayList<String> section_queries = queryBuilder.getAllSectionQueries();
				logger.info("Section queries: " + section_queries.size());
				ArrayList<String> section_run = Doc_RM_QE_variation.getResults(section_queries, index);
				ProjectUtils.writeToFile(out, section_run);

			} else {
				logger.error("Error: QueryType not recognized. Got: " + queryType);
			}

		} catch (Throwable e) {
			logger.error(e.getMessage());
		}
	}


	// Runs Jordan's Ranklib Trainer
	private static void runRanklibTrainer(Namespace namespace) {
		String indexLocation = namespace.getString("index");
		String qrelLocation = namespace.getString("qrel");
		String queryLocation = namespace.getString("query");
		String hyperLoc = namespace.getString("hyperlink_database");
		String gramLoc = namespace.getString("gram_index");
		String abstractLoc = namespace.getString("abstract_index");
		String out = namespace.getString("out");
		String method = namespace.getString("method");
		KotlinRankLibTrainer kotTrainer = new KotlinRankLibTrainer(indexLocation, queryLocation, qrelLocation, hyperLoc,
				abstractLoc, gramLoc);
		kotTrainer.train(method, out);
	}

	// Runs Jordan's Ranklib Query
	private static void runRanklibQuery(Namespace namespace) {
		String indexLocation = namespace.getString("index");
		String queryLocation = namespace.getString("query");
		String hyperLoc = namespace.getString("hyperlink_database");
		String gramLoc = namespace.getString("gram_index");
		String abstractLoc = namespace.getString("abstract_index");
		String out = namespace.getString("out");
		String method = namespace.getString("method");

		KotlinRankLibTrainer kotTrainer = new KotlinRankLibTrainer(indexLocation, queryLocation, "", hyperLoc,
				abstractLoc, gramLoc);
		kotTrainer.runRanklibQuery(method, out);
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
