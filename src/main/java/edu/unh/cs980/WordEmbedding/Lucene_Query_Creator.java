/**
 * Given a query file, will build queries to query indexed Lucene database with.
 * Implements variations of querying and ranking.
 *
 * command: Command used to run program. Used to distinguish between variations of normal methods when querying.
 * gloveReader: Used for word vector variation: compares query and documents via cosine sim
 */

package edu.unh.cs980.WordEmbedding;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.Data.Page;
import edu.unh.cs.treccar_v2.Data.Section;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;

class Lucene_Query_Creator {
	private static final String PAGE = "PAGE";
	private static final String SECTION = "SECTION";
	private static final String BM25QUERYJUSTTHEPAGENAME = "BM25QUERYJUSTTHEPAGENAME";
	private static final String BM25QUERYJUSTTHELOWESTHEADING = "BM25QUERYJUSTTHELOWESTHEADING";
	private static final String BM25QUERYOFINTERIORHEADING = "BM25QUERYOFINTERIORHEADING";

	final MyQueryBuilder queryBuilder = new MyQueryBuilder(new StandardAnalyzer());
	private IndexSearcher indexSearcher;
	private Analyzer analyzer;
	private String queryType;
	private GloveReader gloveReader;

	Lucene_Query_Creator(String qType, String queryType2, Analyzer ana, Similarity sim, String indexPath)
			throws IOException {
		analyzer = ana;
		queryType = queryType2;

		setUpIndexSearcher(indexPath);
		indexSearcher.setSimilarity(sim);
	}

	@SuppressWarnings("unused")
	private class TokenGenerator implements Supplier<String> {
		final TokenStream tokenStream;

		@SuppressWarnings("unused")
		TokenGenerator(TokenStream ts) throws IOException {
			tokenStream = ts;
			ts.reset();
		}

		public String get() {
			try {
				if (!tokenStream.incrementToken()) {
					tokenStream.end();
					tokenStream.close();
					return null;
				}
			} catch (IOException e) {
				return null;
			}
			return tokenStream.getAttribute(CharTermAttribute.class).toString();
		}
	}

	// Used by word vector variation: creates a reader from 50D GloVE word
	// vector file.
	public void setVectorLocation(String vectorLocation) throws IOException {
		gloveReader = new GloveReader(vectorLocation);
	}

	// Author: Prof Laura.
	static class MyQueryBuilder {
		private final StandardAnalyzer analyzer;
		private List<String> tokens;

		public MyQueryBuilder(StandardAnalyzer standardAnalyzer) {
			analyzer = standardAnalyzer;
			tokens = new ArrayList<>(128);
		}

		public BooleanQuery toQuery(String queryStr) throws IOException {

			TokenStream tokenStream = analyzer.tokenStream("text", new StringReader(queryStr));
			tokenStream.reset();
			tokens.clear();

			while (tokenStream.incrementToken()) {
				final String token = tokenStream.getAttribute(CharTermAttribute.class).toString();
				tokens.add(token);
			}
			tokenStream.end();
			tokenStream.close();
			BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
			for (String token : tokens) {
				booleanQuery.add(new TermQuery(new Term("text", token)), BooleanClause.Occur.SHOULD);
			}
			return booleanQuery.build();
		}

	}

	private static String buildSectionQueryStr(Data.Page page, List<Data.Section> sectionPath) {

		StringBuilder queryStr = new StringBuilder();
		queryStr.append(page.getPageName());
		for (Data.Section section : sectionPath) {
			queryStr.append(" ").append(section.getHeading());
		}
		return queryStr.toString();
	}

	void writeRankings(String queryLocation, String rankingsOutput) throws IOException {
		final BufferedWriter out = new BufferedWriter(new FileWriter(rankingsOutput));
		final FileInputStream inputStream = new FileInputStream(new File(queryLocation));

		if (queryType.equalsIgnoreCase(PAGE)) {
			writePageRankings(inputStream, out);
		} else if (queryType.equalsIgnoreCase(SECTION)) {
			writeSectionRankings(inputStream, out);
		} else if (queryType.equalsIgnoreCase(BM25QUERYJUSTTHEPAGENAME)) {
			writeBM25QueryJustThePageName(inputStream, out);
		} else if (queryType.equalsIgnoreCase(BM25QUERYJUSTTHELOWESTHEADING)) {
			writeBm25QueryJustTheLowestHeading(inputStream, out);
		} else if (queryType.equalsIgnoreCase(BM25QUERYOFINTERIORHEADING)) {
			writeBm25QueryOfInteriorHeading(inputStream, out);
		} else {
			System.out.println("Wrong input");

		}

		out.flush();
		out.close();
	}

	private void writeBm25QueryOfInteriorHeading(FileInputStream inputStream, BufferedWriter out) throws IOException {
		// TODO Auto-generated method stub
		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			System.out.println("\n Page:" + page.getPageId());
			for (List<Data.Section> sectionPath : page.flatSectionPaths()) {

				System.out.println();
				System.out.println("Section Path Id =" + Data.sectionPathId(page.getPageId(), sectionPath) + "   \t "
						+ "Section Path Headings =" + Data.sectionPathHeadings(sectionPath));
				final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);
				
				String queryStr = buildBm25QueryOfInteriorHeading(page, sectionPath);

				TopDocs tops = null;
				try {
					tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				System.out.println("Found " + scoreDoc.length + " results.");
				writeRankingsToFile(scoreDoc, queryId, out);
		
			}
		}
				
			}
	private String buildBm25QueryOfInteriorHeading(Data.Page page, List<Section> sectionPath) {
	   // TODO Auto-generated method stub
				
		StringBuilder queryStr = new StringBuilder();
        queryStr.append(page.getPageName());
        System.out.println("queryStr = " + queryStr);
        for (List<Data.Section> sectionPath1 : page.flatSectionPaths()) {
    	        for (Data.Section section: sectionPath1) {
                      queryStr.append(" ").append(section.getHeading());
		
    	        }
        }
		return queryStr.toString();
	
	}
		

	private void writeBm25QueryJustTheLowestHeading(FileInputStream inputStream, BufferedWriter out) throws IOException {
		// TODO Auto-generated method stub
		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			System.out.println("\n Page:" + page.getPageId());
			final String queryId=page.getPageId();
			/*for (List<Data.Section> sectionPath : page.flatSectionPaths()) {

				System.out.println();
				System.out.println("Section Path Id =" + Data.sectionPathId(page.getPageId(), sectionPath) + "   \t "
						+ "Section Path Headings =" + Data.sectionPathHeadings(sectionPath));
				final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);*/
				
				String queryStr = buildBm25QueryJustTheLowestHeading(page, Collections.<Data.Section>emptyList());

				TopDocs tops = null;
				try {
					tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				System.out.println("Found " + scoreDoc.length + " results.");
				writeRankingsToFile(scoreDoc, queryId, out);
		
			}
		}
	
	

	private String buildBm25QueryJustTheLowestHeading(Data.Page page, List<Section> sectionPath) {
		// TODO Auto-generated method stub
	
		String queryStr = " ";
		for (List<Data.Section> sectionPath1 : page.flatSectionPaths()) {
	            for (Data.Section section: sectionPath1) {
               queryStr = section.getHeading();
	}
		}
		return queryStr;
	}

	private void writeBM25QueryJustThePageName(FileInputStream inputStream, BufferedWriter out) throws IOException {
		// TODO Auto-generated method stub

		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			System.out.println("\n Page:" + page.getPageId());
			String queryId=page.getPageName();
			/*for (List<Data.Section> sectionPath : page.flatSectionPaths()) {

				System.out.println();
				System.out.println("Section Path Id =" + Data.sectionPathId(page.getPageId(), sectionPath) + "   \t "
						+ "Section Path Headings =" + Data.sectionPathHeadings(sectionPath));
				final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);*/

				String queryStr = buildBM25QueryJustThePageName(page,Collections.<Data.Section>emptyList() );

				TopDocs tops = null;
				try {
					tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				System.out.println("Found " + scoreDoc.length + " results.");
				writeRankingsToFile(scoreDoc, queryId, out);
			}

		}
	

	private String buildBM25QueryJustThePageName(Data.Page page, List<Data.Section> sectionPath) {
		// TODO Auto-generated method stub
		StringBuilder queryStr = new StringBuilder();
		queryStr.append(page.getPageName());
		for (Data.Section section : sectionPath) {
			queryStr.append(" ").append(section.getHeading());
		}

		return queryStr.toString();

	}

	void writeRankingsToFile(ScoreDoc[] scoreDoc, String queryId, BufferedWriter out) throws IOException {
		ArrayList<String>outputRunFileString=new ArrayList<String>();
		ArrayList<String>duplicateCheckRunFileString=new ArrayList<String>();
		for (int i = 0; i < scoreDoc.length; i++) {
			ScoreDoc score = scoreDoc[i];
			final Document doc = indexSearcher.doc(score.doc);
			final String paragraphid = doc.getField("paragraphid").stringValue();
			
		 
						
			final float searchScore = score.score;
			final int searchRank = i + 1;
            String writeIntoFile=queryId + " Q0 " + paragraphid + " " + searchRank + " " + searchScore + " Lucene-BM25" + "\n";
            if(!duplicateCheckRunFileString.contains(paragraphid))
            {
            	duplicateCheckRunFileString.add(paragraphid);
            	outputRunFileString.add(writeIntoFile);
            	out.write(writeIntoFile);
            }
            duplicateCheckRunFileString.clear();
            
			//out.write(writeIntoFile);

		}
		System.out.println("Completed");
	}

	void writePageRankings(FileInputStream inputStream, BufferedWriter out) throws IOException {

		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			final String queryId = page.getPageId();

			String queryStr = buildSectionQueryStr(page, Collections.<Data.Section> emptyList());

			TopDocs tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			System.out.println("Found " + scoreDoc.length + " results.");
			writeRankingsToFile(scoreDoc, queryId, out);
		}
	}

	void writeSectionRankings(FileInputStream inputStream, BufferedWriter out) throws IOException {

		for (Data.Page page : DeserializeData.iterableAnnotations(inputStream)) {
			System.out.println("\n Page:" + page.getPageId());
			for (List<Data.Section> sectionPath : page.flatSectionPaths()) {

				System.out.println();
				System.out.println("Section Path Id =" + Data.sectionPathId(page.getPageId(), sectionPath) + "   \t "
						+ "Section Path Headings =" + Data.sectionPathHeadings(sectionPath));
				final String queryId = Data.sectionPathId(page.getPageId(), sectionPath);
				String queryStr = buildSectionQueryStr(page, sectionPath);

				TopDocs tops = indexSearcher.search(queryBuilder.toQuery(queryStr), 100);
				ScoreDoc[] scoreDoc = tops.scoreDocs;
				writeRankingsToFile(scoreDoc, queryId, out);
			}
		}

	}

	private void setUpIndexSearcher(String iPath) throws IOException {
		Path indexPath = Paths.get(iPath);
		Directory indexDir = FSDirectory.open(indexPath);
		IndexReader indexReader = DirectoryReader.open(indexDir);
		indexSearcher = new IndexSearcher(indexReader);
	}
}
