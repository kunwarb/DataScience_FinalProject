## Installation Instructions
A precompiled jar file can be found in bin/program.jar

You may also compile the source code by entering the following command while in the project directory:

```bash
mvn clean compile assembly:single
```

This will create an executable jar file in the target/ directory.

## Program Commands
The program is divided into the following subcommands:

___
#### Indexer (index)
Creates a bipartite graph between entities and paragraphs based on entities linked by Spotlight.
The graph is stored in the MapDB database: graph_database.db

```bash
program.jar index corpus [--spotlight_folder ""] [--out "index"]
```
Where:

**corpus**: Is the paragraphCorpus.cbor to create the Lucene index from.

**--out**: Is the name of the directory that the Lucene index will be created in. Default: "index"

**--spotlight_folder**: Is the directory where a runnable DBPedia Spotlight Jar and model are located. If the folder does not contain the required files, the contents are automatically downloaded and unpacked to the folder. If no folder is specified, then entity-linking with Spotlight is skipped during indexing. 

___
#### Graph Builder (graph_builder)
Creates a bipartite graph between entities and paragraphs based on entities linked by Spotlight.
The graph is stored in the MapDB database: graph_database.db. **This command may be skipped if you are using the pre-existing graph_database.db on the server.**


```bash
program.jar graph_builder index
```

Where **index** is the directory of the Lucene index.
___
#### Query Heading (query_heading)
Contains methods for querying based on headings and word embedding.

```bash
program.jar query_heading query_type index query_file [--out query_results.run]
```

Where:

**query_type** is one of:
 - **page**: Page query using BM25
 - **section**: Section path query using BM25
 - **just_the_page**: Section path query using only page name
 - **lowest_heading**: Section path query using only the lowest heading of the query.
 - **interior_heading**: Section path query using only the interior heading of the query.
 - **word_embedding**: Word embedding of the query headers.
 
 **index**: Is the location of the Lucene index directory.
 
 **query_file**: Is the query (.cbor) file to be used in querying the Lucene index.
 
 **--out**: Is the name of the trec_car compatible run file to create. Default: query_results.run
 ___
 ##### Ranklib Query (ranklib_quer)

This command runs a query using linear combinations of features obtained by methods described in the methodology section further down.
The weights of the features have been trained using RankLib. When run, ranklib_query will output a trec_eval compatible run file (default is to "query_results.run")

```bash
program.jar ranklib_query method index query [--out "query_results.run"] [--graph_database ""] 
```

Where:

**method**: Is the type of method to use when querying. The choices are:
 - **entity_similarity**
 - **average_query**
 - **split_sections**
 - **mixtures**
 - **lm_mercer**
 - **lm_dirichlet**
 - **combined**
 
 
 **index**: Is the location of the Lucene index directory.
 
 **query**: Is the query file (.cbor) to query the Lucene index with.
 
 **--out**: Is the name of the runfile to create after querying. Default: query_results.run
 
 **--graph_database**: This option is only used for the mixtures method. It specifies the location of the graph_database.db database.
___ 
 ##### Ranklib Trainer (ranklib_trainer)
 
 The trainer creates a RankLib compatible file by annotating the BM25 query results with features obtained by using methods described further down. The trainer doesn't quite "train" the features yet: it is required that the outputted file be run with RankLib, and the resulting weights are used to determine the weights of the features when querying (these are the weights used by the RankLib Query command)
 
 ```bash
program.jar ranklib_trainer method index query qrel [--out "query_results.run"] [--graph_database ""] 
```
 
 Where:

**method**: Are the same methods described in Ranklib Query
 
 **index**: Is the location of the Lucene index directory.
 
 **query**: Is the query file (.cbor) to query the Lucene index with.
 
 **qrel**: Is the relevancy file (.qrel) used to determine whether or not documents are revant.
 
 **--out**: Is the name of the runfile to create after querying. Default: query_results.run
 
 **--graph_database**: This option is only used for the mixtures method. It specifies the location of the graph_database.db database.
 ___
 
 ## Description of RanklibTrainer / RanklibQuery Methods
 Each of these methods score the Top 100 documents obtained by running BM25 on the concatenated section path against the index.
 For all methods, the score from BM25 is added as an additional feature (in addition to those created by the methods) and the weights are trained using RankLib. **The features (including BM25) were normalized by Z-score.**

#### entity_similary
The query string is first tokenized, and then the score of each paragraph is expressed as the average similiarity score of each query token to each entity in the paragraph. Two similarity metrics are considered in this method: Jaccard and JaroWinkler. The metrics were obtained from the Java library: https://github.com/tdebatty/java-string-similarity

#### average_query
The query string is first tokenized and turned into individual boolean queries. The score of each paragraph is expressed as the average score of these boolean queries (using BM25) against the text of the paragraph.

#### split_sections
The concatenated section query is split into separate sections. These are then scored individually (using BM25) against the text of each paragraph, and each section (when present) is treated as a separate feature.

#### mixtures
Each paragraph is assigned a distribution over entities with respect to a random walk model over a bipartite graph of entities and paragraphs. These distributions are mixed together based on the BM25 score from the query to the paragraph. The proportion of each distribution in the mixture is equal to the proportion of the paragraph's BM25 score over the total score of all paragraphs. The final distribution is used to rescore the paragraphs.

#### lm_mercer
This uses Lucene's LMJelinekMercerSimilarity metric in place of the BM25 similarity metric, and it is used to score each query against the paragraph's text.

#### lm_dirichlet
This uses Lucene's LMDirichletSimilarity metric in place of the BM25 similarity metric, and it is used to score each query against the paragraph's text.

#### combined
This method combines the following previous methods as separate features: BM25, LMDirichletSimilarity (mu 2000), entity_similarity (only using Jaccard string similarity), and first and second heading scores (i.e. pagename/header1/header2)/
