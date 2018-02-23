/**
 * @author Bindu Kumari
 * Date:2/21/2018
 *
 */



package edu.unh.cs980.WordEmbedding;
import java.io.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jooq.lambda.Seq;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.api.ndarray.INDArray;  // for n dimensional array in java
import org.nd4j.linalg.ops.transforms.Transforms;
import java.util.Arrays;

/**
 * Parses and stores 50D GloVe word vectors. It is being used in wordEmbedding variation.
 */
public class GloveReader {
	
    final Map<String, INDArray> indexing;

         private static ImmutablePair<String, INDArray> getEntry(String line) {
            String[] elements = line.split(" ");
            // converting String Array to double Array.
            double[] result = Arrays.stream(elements) .skip(1).mapToDouble(Double::parseDouble) .toArray();

            INDArray array = Nd4j.create(result);
            return new ImmutablePair<String, INDArray>(elements[0], array);
        }

        GloveReader(String path) throws IOException {
            indexing = Seq.seq(Files.lines(Paths.get(path)))
                    .map(GloveReader::getEntry)
                    .toMap(ImmutablePair::getLeft, ImmutablePair::getRight);
        }


    // Takes tokens, finds corresponding word vectors, and takes the average of these vectors
    public INDArray getWordVector(List<String> words) {
        Double count = 0.0;
        INDArray array = Nd4j.zeros(50);

        for (String word : words) {
            INDArray result = indexing.get(word);
            if (result != null) {
                count += 1.0;
                array.addi(result);
            }
        }
        array.divi(count);
        return array;
    }

    // Returns cosine similarity between two vectors
    public Double getCosineSim(INDArray a1, INDArray a2) {
        return Transforms.cosineSim(a1, a2);
    }

    public static void main(String[] args) throws IOException {
        GloveReader gv = new GloveReader("glove.6B.50d.txt");
        ArrayList<String> ls1 = new ArrayList<>();
        ls1.add("Soil");
        ls1.add("Erosion");
        ls1.add("study");
        ls1.add("in");
        ls1.add("Durham");

        ArrayList<String> ls2 = new ArrayList<>();
        ls2.add("germs");
        ls2.add("deterioration");
      ;

        INDArray id1 = gv.getWordVector(ls1);
        INDArray id2 = gv.getWordVector(ls2);
        Double result = Transforms.cosineSim(id1, id2);
      //  INDArray word=Transforms.unitVec(id2);
        System.out.println(result);
    }
}