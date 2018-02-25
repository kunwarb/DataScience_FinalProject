package edu.unh.cs980.variations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import edu.unh.cs980.utils.ProjectUtils;

public class FreqBigram_variation {

	public static ArrayList<String> getSearchResult(ArrayList<String> queriesStr, int max_result, String index_dir)
			throws IOException, ParseException {
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		QueryParser parser = new QueryParser("bigram", new StandardAnalyzer());

		for (String queryStr : queriesStr) {
			// Get bigram list for query
			ArrayList<String> bigram_list = analyzeByBigram(queryStr);

			// Create a HashMap to compute score for each document.
			// <documentId,score>
			HashMap<String, Float> score_map = new HashMap<String, Float>();

			if (bigram_list.isEmpty()) {
				System.out.println("Can't generate Bigram list for Query: " + queryStr);
				break;
			}

			// Query against bigram field with BM25
			for (String term : bigram_list) {
				Query q = parser.parse(QueryParser.escape(term));
				TopDocs tops = searcher.search(q, max_result);
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				for (int i = 0; i < scoreDoc.length; i++) {
					ScoreDoc score = scoreDoc[i];
					Document doc = searcher.doc(score.doc);
					String paraId = doc.getField("paraid").stringValue();
					float rankScore = score.score;
					int rank = i + 1;

					if (score_map.keySet().contains(paraId)) {
						score_map.put(paraId, score_map.get(paraId) + rankScore);

					} else {
						score_map.put(paraId, rankScore);
					}

				}
			}

			// Query against content field with BM25 and combine results with
			// bigram query.
			Query q = parser.parse(QueryParser.escape(queryStr));
			TopDocs tops = searcher.search(q, max_result);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField("paraid").stringValue();
				float rankScore = score.score;

				if (score_map.keySet().contains(paraId)) {
					score_map.put(paraId, score_map.get(paraId) + rankScore);

				} else {
					score_map.put(paraId, rankScore);
				}

			}

			int rank = 1;
			for (Map.Entry<String, Float> entry : ProjectUtils.getTopValuesInMap(score_map, max_result).entrySet()) {
				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + entry.getKey() + " " + rank + " "
						+ entry.getValue() + " FreqBigram";
				rank++;
				runFileStr.add(runStr);
			}

		}

		return runFileStr;
	}

	private static ArrayList<String> analyzeByBigram(String inputStr) throws IOException {
		ArrayList<String> strList = new ArrayList<String>();
		Analyzer analyzer = new BigramAnalyzer();
		TokenStream tokenizer = analyzer.tokenStream("content", inputStr);
		CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);
		tokenizer.reset();
		while (tokenizer.incrementToken()) {
			String token = charTermAttribute.toString();
			if (token.contains(" ")) {
				strList.add(token);
			}
			// System.out.println(token);
		}
		tokenizer.end();
		tokenizer.close();
		return strList;
	}

}
