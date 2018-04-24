package edu.unh.cs980.variations;

import static edu.unh.cs980.KotUtils.CONTENT;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.QueryParser;

public class NLP_QRM_QE_variation {
	// NLP + Query RM QE
	// Relevance entities are from Document entities, score based on the
	// relations between entities in query.
	// Then expanded query with top 5 entities relevance scores.

	private static int top_k_entities = 5; // Include top k entities for QE
	private static int top_k_doc = 10; // Initial top k documents for QE
	private static int max_result = 100; // Max number for Lucene docs
	private static QueryParser parser = new QueryParser(CONTENT, new StandardAnalyzer());

	private static final Logger logger = Logger.getLogger(Doc_RM_QE_variation.class);

}
