package edu.unh.cs980.variations;

import static edu.unh.cs980.KotUtils.CONTENT;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;

// 1. Predict relevant entities by annotated abstract of the entities from query
// 2. Build expanded query = query + words from entity's page (like RM3/Relevance Model); run this query against paragraph index

public class Query_RM_QE_variation {

	private static final Logger logger = Logger.getLogger(Query_RM_QE_variation.class);
	private static int top_k_entities = 5; // Include top k entities for QE
	private static int top_k_doc = 10; // Initial top k documents for QE
	private static int max_result = 100; // Max number for Lucene docs
	private static QueryParser parser = new QueryParser(CONTENT, new StandardAnalyzer());

	public static ArrayList<String> getResults(ArrayList<String> queriesStr, String index_dir)
			throws IOException, ParseException {

		return null;
	}

	private static ArrayList<String> getExpandedTerms(int top_k, String queryStr) throws IOException {
		return null;
	}
}
