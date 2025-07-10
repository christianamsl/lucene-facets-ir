package org.example;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.util.BytesRef;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Class responsible for indexing review documents with faceted search support.
 * It uses Lucene for text indexing and taxonomy writer for faceted configurations.
 */
public class Indexer {

        private String filePath;
        private String indexPath;
        private String taxoPath;
        private IndexWriter indexWriter;
        private DirectoryTaxonomyWriter taxoWriter;
        private static FacetsConfig fconfig;

    /**
     * Retrieves the static FacetsConfig instance.
     *
     * @return the FacetsConfig instance.
     */
        public static FacetsConfig getFconfig(){return fconfig;}

    /**
     * Executes the indexing process by configuring analyzers, similarity measures,
     * and indexing review documents from the specified filePath.
     *
     * @param filePath the directory containing review JSON files.
     * @param indexPath the directory where the index will be stored.
     * @param taxoPath the directory where the taxonomy index will be stored.
     */
        public static void executeIndexer(String filePath, String indexPath, String taxoPath) {
            Indexer baseline = new Indexer(filePath, indexPath, taxoPath);

            //initialize all analyzers/similarities that we need
            Analyzer stAna = new StandardAnalyzer();
            Similarity similarity = new ClassicSimilarity();
            Analyzer keywordAnalyzer = new KeywordAnalyzer();   // For non-tokenized fields
            Analyzer englishAnalyzer = new EnglishAnalyzer();   // For English-language text fields

            //in this map we map the field name and its analyzer
            Map<String, Analyzer> mappedAnalyzers = new HashMap<>();

            //here we put all fields (how they're named in json) with their analyzer
            mappedAnalyzers.put("asin", keywordAnalyzer);
            mappedAnalyzers.put("reviewerID", keywordAnalyzer);
            mappedAnalyzers.put("reviewText", englishAnalyzer);
            mappedAnalyzers.put("summary", stAna);

            PerFieldAnalyzerWrapper perFieldAnalyzer = new PerFieldAnalyzerWrapper(stAna, mappedAnalyzers);

            try {
                baseline.configurarIndice(perFieldAnalyzer, similarity);
                    baseline.indexarReviews();// Index review documents
            } catch (IOException e) {
                System.err.println("Error configuring or indexing documents: " + e.getMessage());
            } finally {
                baseline.close();
            }
        }

    /**
     * Constructor to initialize the Indexer with specified paths and configure facets.
     *
     * @param filePath the directory containing review JSON files.
     * @param indexPath the directory where the index will be stored.
     * @param taxoPath the directory where the taxonomy index will be stored.
     */
        public Indexer(String filePath, String indexPath, String taxoPath) {
            this.filePath = filePath;
            this.indexPath = indexPath;
            this.taxoPath = taxoPath;

            //faceted configurations
            fconfig = new FacetsConfig();
            fconfig.setHierarchical("date", true);
            fconfig.setMultiValued("overall", false);
            fconfig.setMultiValued("asin",false);
        }

    /**
     * Configures the index with the specified analyzer and similarity settings.
     *
     * @param analyzer the analyzer to use for indexing.
     * @param similarity the similarity measure to use for scoring documents.
     * @throws IOException if an error occurs while configuring the index.
     */
        public void configurarIndice (PerFieldAnalyzerWrapper analyzer, Similarity similarity) throws IOException {
            //indexwriterconfig
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setSimilarity(similarity);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

            Directory indexDir = FSDirectory.open(Paths.get(indexPath));
            Directory taxoDir = FSDirectory.open(Paths.get(taxoPath));

            indexWriter = new IndexWriter(indexDir, iwc);
            taxoWriter = new DirectoryTaxonomyWriter(taxoDir);

        }

    /**
     * Indexes review documents from JSON files in the specified filePath.
     */
    public void indexarReviews() {
        ObjectMapper mapper = new ObjectMapper();
        int totalReviewCount = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(filePath), "*.json")) {
            for (Path jsonFilePath : stream) {
                System.out.println("Indexing document "+jsonFilePath.toString());
                totalReviewCount += parseAndIndexReviews(jsonFilePath.toString(), mapper);
            }
        } catch (IOException e) {
            System.err.println("Error reading review files in directory: " + e.getMessage());
        }

        System.out.println("Total number of reviews indexed: " + totalReviewCount);
    }

    /**
     * Parses and indexes reviews from a single JSON file.
     *
     * @param reviewFilePath the path to the JSON file containing reviews.
     * @param mapper the ObjectMapper for reading JSON data.
     * @return the number of reviews indexed.
     * @throws IOException if an error occurs while reading or indexing reviews.
     */
    public int parseAndIndexReviews(String reviewFilePath, ObjectMapper mapper) throws IOException {
        int reviewCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(reviewFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                for(JsonNode reviewNode : mapper.readTree(line)){
                    indexReviewDocument(reviewNode, this.indexWriter);
                    reviewCount++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading JSON file: " + e.getMessage());
        }

        System.out.println("Indexed " + reviewCount + " reviews from " + reviewFilePath);
        return reviewCount;
    }

    /**
     * Indexes a single review document into the index.
     *
     * @param reviewNode the JSON node representing a review.
     * @param writer the IndexWriter for adding documents to the index.
     * @throws IOException if an error occurs while indexing the document.
     */
    private void indexReviewDocument(JsonNode reviewNode, IndexWriter writer) throws IOException {
        Document doc = new Document();

        String reviewerID = reviewNode.path("reviewerID").asText();
        String asin = reviewNode.path("asin").asText();
        String reviewerName = reviewNode.path("reviewerName").asText();
        String reviewText = reviewNode.path("reviewText").asText();
        Double overall = reviewNode.path("overall").asDouble();
        String summary = reviewNode.path("summary").asText();
        String cleanReviewTime = reviewNode.path("cleanReviewTime").asText();

        doc.add(new TextField("everything", reviewerID, Field.Store.NO));
        doc.add(new TextField("everything", asin, Field.Store.NO));
        doc.add(new TextField("everything", reviewerName, Field.Store.NO));
        doc.add(new TextField("everything", reviewText, Field.Store.NO));
        doc.add(new TextField("everything", overall.toString(), Field.Store.NO));
        doc.add(new TextField("everything", summary, Field.Store.NO));
        doc.add(new TextField("everything", cleanReviewTime, Field.Store.NO));

        if  (!reviewerID.isEmpty()) {
            doc.add(new StringField("reviewerID", reviewerID, Field.Store.YES));
        }

        // asin as a product identifier, stored and sortable
        if (!asin.isEmpty()) {
            doc.add(new StringField("asin", asin, Field.Store.YES));
            doc.add(new SortedDocValuesField("asin", new BytesRef(asin)));  // For sorting or faceted search
            doc.add(new FacetField("asin",asin));
        }


        if (!reviewerName.isEmpty()) {
            doc.add(new StringField("reviewerName", reviewerName , Field.Store.YES));
        }

        // reviewText - main review content, indexed with tokenization for search
        if (!reviewText.isEmpty()) {
            FieldType reviewTextType = new FieldType();
            reviewTextType.setStored(true);
            reviewTextType.setTokenized(true);
            reviewTextType.setStoreTermVectors(true);  // Enable term vectors for potential phrase search or highlighting
            reviewTextType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            doc.add(new Field("reviewText", reviewText, reviewTextType));
        }

        doc.add(new DoublePoint("overall", overall)); //for range queries
        doc.add(new StoredField("overall",overall));
        doc.add(new NumericDocValuesField("overall", overall.longValue())); //for range faceting
        doc.add(new FacetField("overall", String.valueOf(overall))); //for categorical facets

        if(!summary.isEmpty()){
            doc.add(new TextField("summary", summary, Field.Store.YES));
        }

        doc.add(new StringField("year",cleanReviewTime.substring(0,4), Field.Store.YES));
        doc.add(new StringField("month",cleanReviewTime.substring(5,7),Field.Store.YES));
        doc.add(new FacetField("date",cleanReviewTime.substring(0,4),cleanReviewTime.substring(5,7)));

        writer.addDocument(fconfig.build(taxoWriter,doc));
    }

    /**
     * Closes the index and taxonomy writers, committing any pending changes.
     */
    public void close() {
        try {
            indexWriter.commit();
            taxoWriter.commit();
            indexWriter.close();
            taxoWriter.close();
        } catch (IOException e) {
            System.out.println("Error closing the index: " + e.getMessage());
        }
    }
}
