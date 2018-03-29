/*****
 * @author:Bindu Kumari
 * @Date:03/27/2018
 */
package edu.unh.cs980.WordEmbedding;

import edu.unh.cs.treccar_v2.Data;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.util.*;

public class TFIDFSimilarity {

	private IndexSearcher searcher;
	private QueryParser parser;
	private int numDocs; // Number of documents to return
	private ArrayList<Data.Page> pageList; // List of pages to query
	private HashMap<Query, ArrayList<ResultQuery>> queryResults; // Map of
																	// queries
																	// to map of
																	// Documents
																	// to scores
																	// for that
																	// query
	TFIDFSimilarity(ArrayList<Data.Page> pl, int n, String index) throws ParseException, IOException {
		String INDEX_DIRECTORY = index;
		numDocs = n;
		pageList = pl;

		// Parse the parabody field using StandardAnalyzer
		parser = new QueryParser("parabody", new StandardAnalyzer());

		// Create an index searcher
		searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(INDEX_DIRECTORY).toPath()))));

		// Setting our own similarity class which computes
		SimilarityBase tfidf = new SimilarityBase() {
			protected float score(BasicStats stats, float freq, float docLen) {
				return (float) (1 + Math.log10(freq));
			}

			@Override
			public String toString() {
				return null;
			}
		};
		searcher.setSimilarity(tfidf);
	}

	/*
	 * public List<Double> doScoring(String query, TopDocs tops ...) {
	 * BooleanQuery boolQuery = makeQuery(query); ArrayList<Double> scores = new
	 * ArrayList<>(); for (ScoreDoc sc : tops.scoreDocs) { Document doc =
	 * searcher.doc(sc.doc); String text = doc.get(CONTENT); List<String>
	 * entities = doc.getValues("spotlight"); Double score = doScore(.doc..)
	 * scores.add(score) } return score; }
	 */

	public synchronized List<Double> doScoring(String qid, TopDocs tpd, TermQuery query, HashMap<TermQuery, Float> queryweights2, HashMap<Document, Float> scores1, HashMap<Document, ResultQuery> docMap2)
			throws IOException, ParseException {
		// TODO Auto-generated method stub
       
		   List<Double> myList = new ArrayList<Double>();
		for (Data.Page page : pageList) {
			
			PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());
			
			String qid1 = page.getPageId();

		for (int i = 0; i < tpd.scoreDocs.length; i++) { // For every
			// returned
			// document...
			Document doc = searcher.doc(tpd.scoreDocs[i].doc); // Get

			double score = tpd.scoreDocs[i].score * queryweights2.get(query);

			ResultQuery dResults = docMap2.get(doc);
			if (dResults == null) {
				dResults = new ResultQuery(doc);
			}
			float prevScore = dResults.getScore();
			dResults.score((float) (prevScore + score));
			dResults.queryId(qid1);
			dResults.paragraphId(doc.getField("paragraphid").stringValue());
			dResults.teamName("Team1 ");
			dResults.methodName("TFDFSimilarity");

			docMap2.put(doc, dResults);

		}
		
		
		// Get cosine Length
					float cosineLength = 0.0f;
					for (Map.Entry<Document, Float> entry : scores1.entrySet()) {
					       Float score = entry.getValue();

						cosineLength = (float) (cosineLength + Math.pow(score, 2));
					}
					cosineLength = (float) (Math.sqrt(cosineLength));
                  
					// Normalization of scores
					for (Map.Entry<Document, Float> entry : scores1.entrySet()) {
                        
						Float score = entry.getValue();

						float finalscore=score / scores1.size();
						if(finalscore!=0.0)
						{
						myList.add((double) (score / scores1.size()));
					    System.out.println(score/scores1.size());
						}
					   
					}

		}
		return myList;	
				

	}

	/**
	 * @function:writeTFIDFScoresTo
	 * @param The
	 *            name of the run file output to
	 * @Description: Main function of the Similarity which calculates similarity
	 *               score of paragraph
	 * @throws IOException,ParseException
	 * 
	 */
	public void writeTFIDFScoresTo(String runfile) throws IOException, ParseException {

		queryResults = new HashMap<>(); // Maps query to map of Documents with
		// TF-IDF score

		for (Data.Page page : pageList) {
			HashMap<Document, Float> scores = new HashMap<>(); // Mapping of
																// each Document
																// to its score
			HashMap<Document, ResultQuery> docMap = new HashMap<>();
			PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ResultComparator());
			ArrayList<ResultQuery> docResults = new ArrayList<>();

			List<Double> ls = new ArrayList<Double>();

			HashMap<TermQuery, Float> queryweights = new HashMap<>();
			ArrayList<TermQuery> terms = new ArrayList<>(); // List of every
															// term in the query
			Query q = parser.parse(page.getPageName()); // The full query
														// containing all terms
			String qid = page.getPageId();

			for (String term : page.getPageName().split(" "))

			{ // For every word in page name...
				// Take word as query term for parabody
				TermQuery tq = new TermQuery(new Term("text", term));

				terms.add(tq);

				// Add one to our term weighting every time it appears in the
				// query
				queryweights.put(tq, queryweights.getOrDefault(tq, 0.0f) + 1.0f);
			}
			for (TermQuery query : terms) { // For every Term

				// Get our Index Reader for helpful statistics
				IndexReader reader = searcher.getIndexReader();

				// If document frequency is zero, set DF to 1; else, set DF to
				// document frequency
				float DF = (reader.docFreq(query.getTerm()) == 0) ? 1 : reader.docFreq(query.getTerm());

				// Calculate TF-IDF for the query vector
				float qTF = (float) (1 + Math.log10(queryweights.get(query))); // Logarithmic
																				// term
																				// frequency
				float qIDF = (float) (Math.log10(reader.numDocs() / DF)); // Logarithmic
																			// inverse
																			// document
																			// frequency
				float qWeight = qTF * qIDF;

				// Store query weight for later calculations
				queryweights.put(query, qWeight);

				// Get the top 100 documents that match our query
			     TopDocs tpd = searcher.search(query, numDocs);

				//ls = doScoring(qid, tpd, query, queryweights,scores,docQueue);

				for (int i = 0; i < tpd.scoreDocs.length; i++) { // For every
																	// returned
																	// document...
					Document doc = searcher.doc(tpd.scoreDocs[i].doc); // Get
																		// the
																		// document
					double score = tpd.scoreDocs[i].score * queryweights.get(query); // Calculate
																						// TF-IDF
					ResultQuery dResults = docMap.get(doc);
					if (dResults == null) {
						dResults = new ResultQuery(doc);
					}
					float prevScore = dResults.getScore();
					dResults.score((float) (prevScore + score));
					dResults.queryId(qid);
					dResults.paragraphId(doc.getField("paragraphid").stringValue());
					dResults.teamName("Team1 ");
					dResults.methodName("TFDFSimilarity");

					docMap.put(doc, dResults);

					// Store score for later use
					scores.put(doc, (float) (prevScore + score));
                      
					ls = doScoring(qid, tpd, query, queryweights,scores,docMap);
					
				}

			}

			// Get cosine Length
			float cosineLength = 0.0f;
			for (Map.Entry<Document, Float> entry : scores.entrySet()) {
			       Float score = entry.getValue();

				cosineLength = (float) (cosineLength + Math.pow(score, 2));
			}
			cosineLength = (float) (Math.sqrt(cosineLength));

			// Normalization of scores
			for (Map.Entry<Document, Float> entry : scores.entrySet()) {

				Document doc = entry.getKey();
				Float score = entry.getValue();

				// Normalize the score
				scores.put(doc, score / scores.size());
			
				ResultQuery dResults = docMap.get(doc);
				dResults.score(dResults.getScore() / cosineLength);

				docQueue.add(dResults);
			}

			int rankCount = 0;
			ResultQuery current;
			while ((current = docQueue.poll()) != null) {
				current.rank(rankCount);
                docResults.add(current);
				rankCount++;
			}

			// Map our Documents and scores to the corresponding query

			queryResults.put(q, docResults);
		}

		System.out.println("TF_IDFSimilarity writing results to: \t\t" + runfile);

		FileWriter runfileWriter = new FileWriter(new File(runfile));
		for (Map.Entry<Query, ArrayList<ResultQuery>> results : queryResults.entrySet()) {
			ArrayList<ResultQuery> list = results.getValue();

			for (int i = 0; i < list.size(); i++) {

				ResultQuery dr = list.get(i);

				runfileWriter.write(dr.getRunfileString());
			}
		}
		runfileWriter.close();

	}

}

/**
 * @class:ResultComparator Description: To compare the result query strings
 * @return return result comparisionbased on methods
 */

class ResultComparator implements Comparator<ResultQuery> {
	public int compare(ResultQuery d2, ResultQuery d1) {
		if (d1.getScore() < d2.getScore())
			return -1;
		if (d1.getScore() == d2.getScore())
			return 0;
		return 1;
	}
}
