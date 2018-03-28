package edu.unh.cs980.WordEmbedding;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Map.Entry;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import info.debatty.java.stringsimilarity.*;


import edu.unh.cs.treccar_v2.Data;

/**
 * @class:ParagraphResultComparator 
 * Description: To compare the result query strings
 * @return based on methods
 */


class ParagraphResultComparator implements Comparator<ResultQuery> {
	public int compare(ResultQuery d2, ResultQuery d1) {
		if (d1.getScore() < d2.getScore())
			return -1;
		if (d1.getScore() == d2.getScore())
			return 0;
		return 1;
	}
}


public class ParagraphSimilarity{

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

	ParagraphSimilarity(ArrayList<Data.Page> pl, int n, String index) throws ParseException, IOException {
		String INDEX_DIRECTORY = index;
		numDocs = n; 
		pageList = pl; 

		parser = new QueryParser("parabody", new StandardAnalyzer());
		searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(INDEX_DIRECTORY).toPath()))));

		// Setting our own similarity function
		SimilarityBase ps = new SimilarityBase() {
			protected float score(BasicStats stats, float freq, float docLen) {
				return (freq);
			}

			@Override
			public String toString() {
				return null;
			}
		};
		searcher.setSimilarity(ps);
	}

	/**
	 *
	 * @param The name of the run file output to
	 * @throws IOException,ParseException
	 * 
	 */
	public void writeParagraphScore(String runfile) throws IOException, ParseException {
		queryResults = new HashMap<>(); // Maps query to map of Documents with
										// TF-IDF score
        
		//For every paragraph
		for (Data.Page page : pageList) { 
			HashMap<Document, Float> scores = new HashMap<>(); 
			HashMap<Document, ResultQuery> docMap = new HashMap<>();
			PriorityQueue<ResultQuery> docQueue = new PriorityQueue<>(new ParagraphResultComparator());
			ArrayList<ResultQuery> docResults = new ArrayList<>();
			// Mapping of  each term  to  its query  tf
			HashMap<TermQuery, Float> queryweights = new HashMap<>(); 
			
			// List of every term in the query
			ArrayList<TermQuery> terms = new ArrayList<>(); 
			
			// The full query containing all terms
			Query q = parser.parse(page.getPageName()); 
			String qid = page.getPageId();
			String qname=page.getPageName();

			for (String term : page.getPageName().split(" "))

			{
				TermQuery tq = new TermQuery(new Term("text", term));

				terms.add(tq);

				
				queryweights.put(tq, queryweights.getOrDefault(tq, 0.0f) + 1.0f);
			}
			for (TermQuery query : terms) { 
              IndexReader reader = searcher.getIndexReader();

				float DF = (reader.docFreq(query.getTerm()) == 0) ? 1 : reader.docFreq(query.getTerm());

				float qTF = (float) (1 + Math.log10(queryweights.get(query))); // Logarithmic
																				// term
																				// frequency
				float qIDF = (float) (Math.log10(reader.numDocs() / DF)); // Logarithmic
																			// inverse
																			// document
																			// frequency
				float qWeight = qTF * qIDF; 

				queryweights.put(query, qWeight);
                  
				// Get the top 100 documents that match our query
				TopDocs tpd = searcher.search(query, numDocs);
				for (int i = 0; i < tpd.scoreDocs.length; i++) { // For every
																	// returned
																	// document...
					Document doc = searcher.doc(tpd.scoreDocs[i].doc); // Get
																		// the
																		// document
					double score = tpd.scoreDocs[i].score * queryweights.get(query); 
					
					
					String docBody = doc.get("text");

					ResultQuery dResults = docMap.get(doc);
					if (dResults == null) {
						dResults = new ResultQuery(doc);
					}
					float prevScore = dResults.getScore();
					
			NGram ngram = new NGram(8);
			     double score1=ngram.distance(qname,docBody);
					
					dResults.score((float) (prevScore + score));
					dResults.queryId(qid);
					dResults.paragraphId(doc.getField("paragraphid").stringValue());
					dResults.teamName("Team1 ");
					dResults.methodName("ParagraphSimilarity");
					docMap.put(doc, dResults);

					
					scores.put(doc, (float) (prevScore + score+score1));
				}
			}

			// Get cosine Length
			float cosineLength = 0.0f;
			for (Map.Entry<Document, Float> entry : scores.entrySet()) {
				Document doc = entry.getKey();
				Float score = entry.getValue();

				cosineLength = (float) (cosineLength + Math.pow(score, 2));
			}
			cosineLength = (float) (Math.sqrt(cosineLength));

			// Normalization of scores
			for (Map.Entry<Document, Float> entry : scores.entrySet()) { // For every document and its corresponding score
																			
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

			queryResults.put(q, docResults);
		}

		System.out.println("Paragraph Similarity writing results to: \t\t" + runfile);
		
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


