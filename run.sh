#!/bin/bash

# Desc:     This script will run all of the methods and store them in the method_runs directory.
#           It is assumed that you have access to the /trec_data folder on the server where all of
#           the required files are stored. If not, you will need to change the paths 
#           for the variables pointing to trec_data/ (see comments below)

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

# Jordan's Hyperlink method 
run_ranklib_query_method hyperlink

# Jordan's Average Abstract method 
run_ranklib_query_method average_abstract

# Jordan's Section Component Method
run_ranklib_query_method section_component

# Jordan's Combined Method
run_ranklib_query_method combined




