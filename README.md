## Results and Report
The newest results for prototype 3 can be found in the results_prototype3 directory. The results are as follows

**results_prototype3/report.pdf**: The group report for the current prototype.

**results_prototype3/jordan/**: Runfiles and trec eval stats for each of Jordan's methods

**results_prototype3/kevin/**: Trec eval stats and run files for Kevin's methods.

**results_prototype3/bindu/**: Trec eval stats and run files for Bindu's methods.
 
 **results_prototype3/public_test/** Compressed run files for runs on benchmark_kY1_public_query test data. Contains the following:
 

___
## Installation Instructions
Because of the balooning size of the precompiled jar, it is no longer being tracked on GitHub. You can find a precompiled version of prototype 3's program on the server at: **/trec_data/team_1/program.jar**

You may also compile the jar directly from the source code in this repository by entering the following command, or by running ./compile.sh while in the project directory:

```bash
mvn clean compile assembly:single
```

This will create a jar file in the target/ directory (which can be used instead of program.jar).
___
## Running Methods
The compiled jar file contains subcommands for querying, indexing, and other relevant functions.
**If you are just interested in running our methods**, you can call the run.sh script and pass as an argument the jar file obtained during the installation step. For example:

```bash
./run.sh /trec_data/team_1/program.jar
```

or using the assembled jar:
```bash
./run.sh target/DataSciene_FinalProject-0.2.0-SNAPSHOT-jar-with-dependencies.jar 
```


This will create a new directory called method_results. Each subdirectory inside of method_results contains the results of a particular method. This includes a run file and the output of trec_eval -c

Note that this run.sh script depends on files (such as our Lucene index) that are stored in /trec_eval on the server. If you are not running this on the server you will need to change the filepaths to point to the correct files (descriptions of the filepaths are in the comments of the run.sh script).
___
## Program Commands
The program is divided into the following subcommands:

 ___
 ### Sparql Downloader (sparql_downloader)

This command is used to query SPARQL and download abstracts (or links that are then uses to download Wikipedia pages). These are used to build topical models (from the first 50 abstracts). The topics are:  Biology, Computers, Cooking, Cuisine, Engineering, Environments, Events, Fashion, Games, Mathematics, Medicine, Organizations, People, Politics, Science, Society, Statistics, Technology, Tools, Travel, Warfare. 


```bash
program.jar sparql_downloader --method {abstracts, page}
```

Where:
average_abstract,combined,abstract_sdm,sdm_components,hyperlink,sdm,section_component

**method**: Is one of:
 - **abstracts**: Downloads topic abstracts and stores them them in an output directory named paragraphs/
 - **page**: Downloads Wikipedia pages and stores them in an output directory named pages/
 
  ___
 ### Topic Decomposer (topic_decomposer)

Decomposes each topic (a collection of paragraphs) into the most important paragraphs. Then decomposes the most important paragraphs into the most important sentences, and finally words. Decomposed topics are stored in the descent_data/ directory.


```bash
program.jar topic_decomposer --method run --paragraphs paragraphs/}
```

Where:
**paragraphs/**: Is the location to the paragraphs directory (retrieved by sparql_downloader). Defaults to /trec_data/team_1/paragraphs
  ___
 ### Embedding (embedding)
This command explores the embedding methods using topics describes previously. The command is divided into two subcommands: train and query. Train generates a file that can be run in RankLib (used for training weights). Query is used to run the actual queries and generate runfiles.

```bash
program.jar embedding query --indexPath INDEXPATH --queryPath QUERYPATH --out OUT --paragraphs PARAGRAPHS --descent_data DESCENT_DATA --method METHOD }
```

Where:
**INDEXPATH**: Location of the Lucene index (default: /trec_data/team_1/myindex)
**QUERYPATH**: Location of the query .cbor file
**OUT**: Location of the runfile to generate (default: query_results.run)
**PARAGRAPHS**: Location of the folder containing topic paragraphs (obtained by sparql_downloader) (default: /trec_data/team_1/paragraphs)
**DESCENT_DATA**: Location of the folder containing decomposed topics (obtained by topic_decomposer) (default: /trec_data/team_1/descent_data)

**METHOD**: is one of the follow (these methods are described at the top of the readme)
 - hier_ascent
 - hier_clusters
 - hier_subclusters
 - hier_query_variations
 - hier_reduction_variations
 - hier_metrics
 - perturbation_embedding
 
 

___
### Indexer (index)
Creates a Lucene index from a given paragraph corpus. Also annotates paragraphs with entities using Spotlight if relevant spotlight directory is given. **Note that an existing copy of this index is already on the server at: /trec_data/team_1/myindex**

```bash
program.jar index corpus [--spotlight_folder ""] [--out "index"]
```
Where:

**corpus**: Is the paragraphCorpus.cbor to create the Lucene index from.

**--out**: Is the name of the directory that the Lucene index will be created in. Default: "index"

**--spotlight_folder**: Is the directory where a runnable DBPedia Spotlight Jar and model are located. If the folder does not contain the required files, the contents are automatically downloaded and unpacked to the folder. If no folder is specified, then entity-linking with Spotlight is skipped during indexing. **A copy of the spotlight_folder is available at: /trec_data/team_1/spotlight_server**

 ___
### Document Entities Relevance Model + Query Expansion Variation:
Predict relevant entities  through entity linking paragraphs of the feedback run
Expand query with top 5 entities, run against paragraph index using BM25.

```bash
program.jar doc_rm_qe query_type index query_file [--out query_results.run]
```

Where:

**query_type** is one of:
 - **page**: Page query using BM25
 - **section**: Section path query using BM25
 
 **index**: Is the location of the Lucene index directory.
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index. (benchmarkY1-train/train.pages.cbor)
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: query_results.run
  ___
### Query Abstract Entiteis Relevance Model + Query Expansion Variation:
Predict relevant entities by annotated abstract of the entities from query
Build expanded query = query + words from entity's page (like RM3/Relevance Model); run this query against paragraph index  

```bash
program.jar query_rm_qe query_type mulit_thread index abstract query_file [--out query_results.run]
```

Where:

**query_type** is one of:
 - **page**: Page query
 - **section**: Section path query
 
 **multi_thread** is one of:
 - **true**: Use multi-thread function to generate expanded query. (**Notice**: Due to virtual memory issue, this method is throwing error on Windows/Linus OS. Need to extend memory limits. More info: http://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html)
 - **false**: Use normal function. (Recommended)
 
 **index**: Is the location of the Lucene index directory.
 
 **abstract**: Is the location of the Lucene abstract index directory. 
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index. (benchmarkY1-train/train.pages.cbor)
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: query_results.run
 ___
 ### Query Ranking by Entity linking using DBPedia Spotlight:
In this method I am trying to rank the Query based on annotated Entity of paragraph content and then paragraph’s score is being calculated as the average score of the annotated entities using spotlight where the score is derived from the query against the abstract index. Here we can use page query.

```bash
program.jar entitySimilarity query_type(page) mulit_thread index abstract query_file [--out entity_Similarity.run]
```
___
### 	Paragraph text with TF-IDF similarity: 
Paragraph text with TF-IDF similarity: In order to rank the Query based on Paragraph score, using TF_IDF lnc.ltc variant. adding log is to dampen the importance of term that has a high frequency. I add 1 to the log(tf) because when tf is equal to 1, the log 1 is zero. by adding one, I can distinguish between tf=0 and tf=1 also for normalization using Cosine normalization and Ranked the query based on paragraph score.

```bash
program.jar tfidf_similarity index cborfile outputlocationfile
```
___
### 	Paragraph text with Wordnet: To rank the paragraph text using Wordnet API.
Which gives similarity score. For Wordnet implementation I am using extjwnl Extended Java WordNet Library is a Java API for creating, reading and updating dictionaries in WordNet format. 

```bash
program.jar paragraph_wordnet index cborfile outputlocationfile
```
___
### Paragraph with entity: 
In this method I am trying to rank the paragraph based on entity present in paragraph only. Then counting the total number of entities present in the paragraph and scoring accordingly. The difference between first one and this is that It does not check the entity content.

```bash
program.jar paragraph_similarity index cborfile outputlocationfile
```
___


 
 ## Description of embedding methods
 Each of these methods score the Top 100 documents obtained by running BM25 on the concatenated section path against the index.
 For all individual methods, the score from BM25 is added as an additional feature (in addition to those created by the methods) unless otherwise noted, and the weights are trained using RankLib. **The features (including BM25) were normalized by Z-score.**
 


