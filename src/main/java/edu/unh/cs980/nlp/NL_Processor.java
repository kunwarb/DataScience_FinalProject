package edu.unh.cs980.nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class NL_Processor {
	private static final Logger logger = Logger.getLogger(NL_Processor.class);
	private static StanfordCoreNLP pipeline;

	public static NL_Document.Paragraph convertToNL_Document(String para_text) {

		logger.info("Processing text with stanford NLP.");
		if (pipeline == null) {
			Properties props = new Properties();
			props.put("annotators", "tokenize, ssplit, pos");
			pipeline = new StanfordCoreNLP(props);
		}

		NL_Document.Paragraph para = new NL_Document.Paragraph();
		Annotation annotation = new Annotation(para_text);

		pipeline.annotate(annotation);

		// Convert paragraph text into a list of sentences
		List<CoreMap> coreMap = annotation.get(SentencesAnnotation.class);
		ArrayList<NL_Document.Sentence> sentences = new ArrayList<>();

		for (CoreMap entryLine : coreMap) {
			// Iterate through each sentence
			NL_Document.Sentence sentence = new NL_Document.Sentence();
			ArrayList<String> allNouns = new ArrayList<String>();
			ArrayList<String> allVerbs = new ArrayList<String>();

			String sentConent = entryLine.get(TextAnnotation.class);
			logger.debug("Sentence: " + sentConent);
			// Iterate through each word in a sentence
			for (CoreLabel token : entryLine.get(TokensAnnotation.class)) {

				String word = token.get(TextAnnotation.class);
				String pos = token.get(PartOfSpeechAnnotation.class);

				if (isNoun(pos)) {
					allNouns.add(word);
				} else if (isVerb(pos)) {
					allVerbs.add(word);
				} else {
					// Ignore
				}
			}
			sentence.setSentContent(sentConent);
			sentence.setAllNouns(allNouns);
			sentence.setAllVerbs(allVerbs);

			sentences.add(sentence);
		}

		para.setParaContent(para_text);
		para.setSentences(sentences);

		return para;
	}

	// Check if the word is noun
	private static Boolean isNoun(String pos) {
		// POS: NN, NNS, NNP, NNPS
		return pos.toUpperCase().contains("NN");
	}

	// Check if the word is verb
	private static Boolean isVerb(String pos) {
		// POS: VB, VBD, VBG, VBN, VBP, VBZ
		return pos.toUpperCase().contains("VB");
	}

}
