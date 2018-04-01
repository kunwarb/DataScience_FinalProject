#!/bin/bash

# Desc:     This script will run all of the methods and store them in the method_runs directory.
#           It is assumed that you have access to the /trec_data folder on the server where all of
#           the required files are stored. If not, you will need to change the paths 
#           for the variables pointing to trec_data/ (see comments below)

# Note:     For Kevin's and Bindu's methods, when given a choice between page or section variants,
#           I only demonstrate their page variants below. This is because the section variants are taking
#           too long to run (and may not work) on the server.

#           If you want to evaluate the section variants for query_rm_qe, entitySimilarity, and doc_rm_qe,
#           Then change the page type to section (see my comments for those methods below)
#           This also means that they should be evaluated with hierarchical, not article, qrels.

# Argument: Location of Team 1's JAR file (either in target/ after compiling or in /bin)

JAR=$1

# Pathway to unprocessedAllButBenchmark cbor (contains Wikipedia pages)
ALL_BUT_BENCH=/trec_data/unprocessedAllButBenchmark/unprocessedAllButBenchmark.cbor

# Pathway to Trec Car's paragraphCorpus cbor file
PAR_CORPUS=/trec_data/paragraphCorpus/dedup.articles-paragraphs.cbor

# Pathway to paragraphCorpus index (made using jar command: index)
INDEX=/trec_data/team_1/myindex

# Pathway to abstract index (made using jar command: abstract_indexer)
ABSTRACT=/trec_data/team_1/abstract

# Pathway to hyperlink database (made using jar command: hyperlink_indexer)
HYPERLINK=/trec_data/team_1/entity_mentions.db

# Pathway to -gram Lucene index (made using jar command: gram_indexer)
GRAM=/trec_data/team_1/gram

# Pathway to trec_eval program
TREC_EVAL=/trec_data/trec_eval

# Pathway to benchmarkY1's hierarchical qrels file
TRAIN_HIER_QRELS=/trec_data/benchmarkY1-train/train.pages.cbor-hierarchical.qrels

# Pathway to articles qrels file
TRAIN_ARTICLE_QRELS=/trec_data/benchmarkY1-train/train.pages.cbor-article.qrels

# Pathway to benchmarkY1's train.pages.cbor-outlines file
TRAIN_OUTLINE_CBOR=/trec_data/benchmarkY1-train/train.pages.cbor-outlines.cbor

# This function is used to run methods run through the ranklib_query command in the jar.
run_ranklib_query_method () {
    # Create subdirectory to hold method results and eval stats
    METHOD=$1
    METHOD_DIR=method_results/$1
    METHOD_RESULT=$METHOD_DIR/query_results.run
    EVAL_RESULT=$METHOD_DIR/trec_eval_stats.txt
    mkdir -p $METHOD_DIR

    # Run method and write to corresponding method_results dir
    echo "=== Running Method: $1 ==="
    java -jar $JAR ranklib_query\
        --hyperlink_database $HYPERLINK\
        --abstract_index $ABSTRACT\
        --gram_index $GRAM\
        --out $METHOD_RESULT\
        $METHOD $INDEX $TRAIN_OUTLINE_CBOR
    echo -e Runfile Stored in $METHOD_RESULT \\n

    # Write trec eval stats of method to corresponding method_results dir
    echo "=== Evaluating Method: $1 ==="
    $TREC_EVAL -c $TRAIN_HIER_QRELS $METHOD_RESULT > $EVAL_RESULT
    echo -e Trec eval results stored in $EVAL_RESULT \\n\\n
}

# Create directory to store results if it doesn't exist
mkdir -p method_results

# Jordan's SDM method 
run_ranklib_query_method sdm

# Jordan's Abstract SDM method 
run_ranklib_query_method abstract_sdm

# Jordan's SDM Section Method
run_ranklib_query_method sdm_section

# Jordan's Hyperlink method 
run_ranklib_query_method hyperlink

# Jordan's Average Abstract method 
run_ranklib_query_method average_abstract

# Jordan's String Similarity Section Method
run_ranklib_query_method string_similarity_section

# Jordan's Combined Method
run_ranklib_query_method combined

# Jordan's TFIDF Section Method
run_ranklib_query_method tfidf_section

# Jordan's SDM Expanson Method
run_ranklib_query_method sdm_expansion

# Jordan's NAT SDM Method
run_ranklib_query_method nat_sdm

# Jordan's Super Awesome Teamwork Method
run_ranklib_query_method super_awesome_teamwork


# Adding Kevin's doc_rm_qe (switch page to section for section variant)
echo "=== Running doc_rm_qe (this will take a while) ==="
mkdir -p method_results/doc_rm_qe
java -jar $JAR doc_rm_qe\
    page\
    $INDEX\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/doc_rm_qe/query_results.run

echo "=== Evaluating Method: doc_rm_qe (only on article qrels) ==="
ERESULT=method_results/doc_rm_qe/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/doc_rm_qe/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n


# Adding Kevin's query_rm_qe (switch page to section for section variant)
echo "=== Running query_rm_qe  ==="
mkdir -p method_results/query_rm_qe
java -jar $JAR query_rm_qe\
    page\
    false\
    $INDEX\
    $ABSTRACT\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/query_rm_qe/query_results.run

echo "=== Evaluating Method: query_rm_qe (only on article qrels) ==="
ERESULT=method_results/query_rm_qe/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/query_rm_qe/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n

# Adding Bindu's paragraph_wordnet
echo "=== Running paragraph_wordnet (this will take a while) ==="
mkdir -p method_results/paragraph_wordnet
java -jar $JAR paragraph_wordnet\
    $INDEX\
    $TRAIN_OUTLINE_CBOR\
    method_results/paragraph_wordnet/query_results.run


echo "=== Evaluating Method: paragraph_wordnet (only on article qrels) ==="
ERESULT=method_results/paragraph_wordnet/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/paragraph_wordnet/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n


# Adding Bindu's entitySimilarity method (switch page to section for section variant)
echo "=== Running entitySimilarity ==="
mkdir -p method_results/entitySimilarity
java -jar $JAR entitySimilarity\
    page\
    false\
    $INDEX\
    $ABSTRACT\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/entitySimilarity/query_results.run

echo "=== Evaluating Method: entitySimilarity (only on article qrels) ==="
ERESULT=method_results/entitySimilarity/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/entitySimilarity/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n


# Adding Bindu's tfidf_similarity
echo "=== Running tfidf_similarity  ==="
mkdir -p method_results/tfidf_similarity
java -jar $JAR tfidf_similarity\
    $INDEX\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/tfidf_similarity/query_results.run


echo "=== Evaluating Method: tfidf_similarity (only on article qrels) ==="
ERESULT=method_results/tfidf_similarity/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/tfidf_similarity/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n

# Adding Bindu's tfidf_similarity
echo "=== Running paragraph_similarity  ==="
mkdir -p method_results/paragraph_similarity
java -jar $JAR paragraph_similarity\
    $INDEX\
    $TRAIN_OUTLINE_CBOR\
    --out method_results/paragraph_similarity/query_results.run


echo "=== Evaluating Method: paragraph_similarity (only on article qrels) ==="
ERESULT=method_results/paragraph_similarity/trec_eval_stats.txt
$TREC_EVAL -c $TRAIN_ARTICLE_QRELS method_results/paragraph_similarity/query_results.run > $ERESULT
echo -e Trec eval results stored in $ERESULT \\n\\n


