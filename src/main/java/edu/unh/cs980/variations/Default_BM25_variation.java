package edu.unh.cs980.variations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
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

public class Default_BM25_variation {
	public static ArrayList<String> getSearchResult(ArrayList<String> queriesStr, int max_result, String index_dir)
			throws IOException, ParseException {
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		QueryParser parser = new QueryParser("content", new StandardAnalyzer());
		int duplicate = 0;
		for (String queryStr : queriesStr) {
			Query q = parser.parse(QueryParser.escape(queryStr));

			TopDocs tops = searcher.search(q, max_result);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField("paraid").stringValue();
				float rankScore = score.score;
				int rank = i + 1;

				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
						+ rankScore + " BM25";
				if (runFileStr.contains(runStr)) {
					duplicate++;
					// System.out.println("Found duplicate: " + runStr);
				} else {
					runFileStr.add(runStr);
				}
			}
		}

		return runFileStr;
	}
}
