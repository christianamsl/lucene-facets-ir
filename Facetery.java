package org.example;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.range.*;
import org.apache.lucene.facet.range.DoubleRange;
import org.apache.lucene.facet.range.LongRange;
import org.apache.lucene.facet.taxonomy.*;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/**
 * The Facetery class provides functionalities to index documents with facets,
 * search using facets and drill down capabilities, and perform sentiment analysis.
 * It utilizes Apache Lucene and related libraries for indexing, searching, and facet handling.
 */
public class Facetery {

    private String indexPath;
    private String taxoPath;
    private FacetsConfig config;

    /**
     * Main method to interact with the Facetery functionality via console inputs.
     *
     * @param args Command line arguments (not used).
     * @throws IOException If an I/O error occurs.
     * @throws ParseException If a query parsing error occurs.
     */
    public static void main(String[] args) throws IOException, ParseException {
        Scanner scanner = new Scanner(System.in);

        // Get path for index directory
        System.out.print("Enter the path to the index directory: ");
        String indexPath = scanner.nextLine();

        //Get path for directory
        System.out.print("Enter path to taxonomy directory: ");
        String taxoPath = scanner.nextLine();

        // Check if the required inputs are provided
        if (taxoPath.isEmpty() || indexPath.isEmpty()) {
            System.out.println("All parameters (file path, index path, data type) are required.");
            return;
        }

        Facetery baseline = new Facetery(indexPath, taxoPath);

        while (true) {
            System.out.println("\nChoose an action:");
            System.out.println("1. Index Documents with Facets (including Range Facets)");
            System.out.println("2. Search with Facets and Drill Down");
            System.out.println("3. Exit");
            System.out.print("Enter your choice: ");
            int choice = Integer.parseInt(scanner.nextLine());

            switch (choice) {
                case 1:
                    // Get path for file directory
                    System.out.print("Enter the path to the file directory: ");
                    String filePath = scanner.nextLine();
                    baseline.indexDocumentsWithFacets(filePath);
                    break;
                case 2:
                    baseline.searchWithFacets(indexPath, taxoPath);
                    break;
                case 3: System.exit(0);
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    /**
     * Constructs a Facetery instance with specified index and taxonomy paths.
     *
     * @param indexPath The path to the index directory.
     * @param taxoPath The path to the taxonomy directory.
     */
    public Facetery(String indexPath, String taxoPath) {
        this.indexPath = indexPath;
        this.taxoPath = taxoPath;
        Indexer ind = new Indexer("src/main/resources/reviews",
                indexPath,taxoPath);
        this.config = ind.getFconfig();
    }

    /**
     * Indexes documents with facets from the specified file path.
     *
     * @param filePath The path to the directory containing documents to index.
     */
    public void indexDocumentsWithFacets(String filePath) {
        Indexer.executeIndexer(filePath, this.indexPath, this.taxoPath);
    }

    /**
     * Searches the index with facets and allows drill down for specific categories.
     *
     * @param indexPath The path to the index directory.
     * @param taxoPath The path to the taxonomy directory.
     * @throws IOException If an I/O error occurs.
     * @throws ParseException If a query parsing error occurs.
     */
    public void searchWithFacets(String indexPath, String taxoPath) throws IOException, ParseException {
        Directory indexDir = FSDirectory.open(Paths.get(indexPath));
        Directory taxoDir = FSDirectory.open(Paths.get(taxoPath));
        DirectoryReader indexReader = DirectoryReader.open(indexDir);
        TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir);

        IndexSearcher searcher = new IndexSearcher(indexReader);
        Scanner scanner = new Scanner(System.in);
        int choice;

        //queries: normal; numeric; sorted
        while (true) {
            System.out.println("Choose a query to search, press 0 to exit. ");
            System.out.println("1. word query");
            System.out.println("2. numeric query");
            System.out.println("3. boolean query");
            System.out.print("Enter your choice: ");
            choice = Integer.parseInt(scanner.nextLine());

            if (choice == 0) {
                System.out.println("Exiting queries...");
                break; // Exit the loop
            }
            switch (choice) {
                case 1:
                    // word query
                    executeQuery(searcher,taxoReader,scanner);
                    break;
                case 2:
                    //numeric query
                    executeNumericQuery(searcher,taxoReader,scanner);
                    break;
                case 3:
                    //sorted query
                    executeBooleanQuery(searcher,taxoReader,scanner);
                    break;
            }
        }


            indexReader.close();
            taxoReader.close();
        }

    /**
     * Executes a query on the specified field and displays facet results.
     *
     * @param searcher The IndexSearcher instance.
     * @param taxoReader The TaxonomyReader instance.
     * @param scanner Scanner for user input.
     * @throws ParseException If a query parsing error occurs.
     * @throws IOException If an I/O error occurs.
     */
        public void executeQuery(IndexSearcher searcher, TaxonomyReader taxoReader,Scanner scanner) throws ParseException, IOException {//Query query = new MatchAllDocsQuery();

        //example: "world" in summary, "world" in reviewText
            System.out.println("Choose field to be queried (reviewText, summary): ");
            String field = scanner.nextLine();

            System.out.println("Enter your query string: ");
            String queryString = scanner.nextLine();

            QueryParser parser = new QueryParser(field, new EnglishAnalyzer());
            Query query = parser.parse(queryString);

            FacetsCollector facetsCollector = new FacetsCollector();
            FacetsCollector.search(searcher, query, 10, facetsCollector);


            //for each assigned category (asin, date, overall) give label and how many are in that group
            Facets facetas = new FastTaxonomyFacetCounts(taxoReader, Indexer.getFconfig(), facetsCollector); //count of each facet
            List<FacetResult> TodasDims = facetas.getAllDims(100);
            System.out.println("Total number of categories " + TodasDims.size());
            for (FacetResult fr : TodasDims) {
                System.out.println("category: " + fr.dim);
                for (LabelAndValue lv : fr.labelValues) {
                    System.out.println(lv.label + "(" + lv.value + ")");
                }
            }
            // Drill Down
            while (true) {
                System.out.println("To drill down, choose a category; to exit, press 0: ");
                System.out.println("1.asin\n2.date\n3.overall");
                int cat = Integer.parseInt(scanner.nextLine());

                if (cat==0) {
                    System.out.println("Exiting drilling...");
                    break; // Exit the loop
                }

                DrillDownQuery drillDownQuery = new DrillDownQuery(new FacetsConfig(), query);

                if (cat==1) {
                    System.out.println("Enter asin: ");
                    String asin = scanner.nextLine();
                    drillDownQuery.add("asin", asin);
                } else if (cat==2) {
                    System.out.println("Enter year: ");
                    String year = scanner.nextLine();
                    drillDownQuery.add("date", year);

                    while (true) { // Loop for navigating through months and beyond
                        // Perform search and collect facets
                        FacetsCollector facetsCollector2 = new FacetsCollector();
                        TopDocs drillDownDocs = FacetsCollector.search(searcher, drillDownQuery, 10, facetsCollector2);

                        // Retrieve facets for child categories (e.g., months)
                        Facets categoryFacets = new FastTaxonomyFacetCounts(taxoReader, Indexer.getFconfig(), facetsCollector2);
                        FacetResult categoryResult = categoryFacets.getTopChildren(10, "date", year);

                        if (categoryResult != null && categoryResult.labelValues.length > 0) {
                            System.out.println("Child categories under year " + year + ":");
                            for (LabelAndValue lv : categoryResult.labelValues) {
                                System.out.println("Month: " + lv.label + "(" + lv.value+")");
                            }

                            System.out.println("Enter a month to drill down further or press Enter to display results: ");
                            String month = scanner.nextLine();

                            if (month.isEmpty()) {
                                break; // Exit the inner loop to display results
                            }

                            // Add the selected month to the drill-down query
                            drillDownQuery.add("date", year + "/" + month);

                                break; // Exit to display results

                        } else {
                            System.out.println("No child facets found under the selected category.");
                            break;
                        }
                    }
                } else if (cat==3) {
                    System.out.println("Enter specific rating: ");
                    Double rating = scanner.nextDouble();
                    scanner.nextLine(); // Consume newline
                    drillDownQuery.add("overall", String.valueOf(rating));
                }

                // Perform search and display results
                TopDocs drillDownDocs = searcher.search(drillDownQuery, 10);
                System.out.println("Drill Down Results: " + drillDownDocs.totalHits);
                for (ScoreDoc hit : drillDownDocs.scoreDocs) {
                    Document doc = searcher.doc(hit.doc);
                    System.out.println(field + ": " + doc.get(field));
                }
            }


        }

    /**
     * Executes a numeric range query and displays facet results.
     *
     * @param searcher The IndexSearcher instance.
     * @param taxoReader The TaxonomyReader instance.
     * @param scanner Scanner for user input.
     * @throws IOException If an I/O error occurs.
     */
        public void executeNumericQuery(IndexSearcher searcher, TaxonomyReader taxoReader,Scanner scanner) throws IOException {
            System.out.print("Search for good (3-5) and bad (1-2) overall rating on a certain product: ");
            String asinNr = scanner.nextLine();

            Query query = new TermQuery(new Term("asin", asinNr));
            FacetsCollector facetsCollector = new FacetsCollector();
            FacetsCollector.search(searcher, query, 10, facetsCollector);

            Facets rangeFacets = new LongRangeFacetCounts(
                    "overall", facetsCollector,
                    new LongRange("good", (long) 3.0, true, (long)5.0, true),
                    new LongRange("bad", (long)0.0, true, (long)2.0, true)
            );

        System.out.println("overall Ranges:");
        for (LabelAndValue lv : rangeFacets.getTopChildren(10, "overall").labelValues) {
            System.out.println(lv.label + ": " + lv.value);
        }
            //for each assigned category ( date, overall) give label and how many are in that group
            Facets facetas = new FastTaxonomyFacetCounts(taxoReader, Indexer.getFconfig(), facetsCollector); //count of each facet
            List<FacetResult> TodasDims = facetas.getAllDims(100);
            System.out.println("Total number of categories " + (TodasDims.size()-1));
            for (FacetResult fr : TodasDims) {
                if(fr.dim.equals("asin")){continue;}
                System.out.println("category: " + fr.dim);
                for (LabelAndValue lv : fr.labelValues) {
                    System.out.println(lv.label + "(" + lv.value + ")");
                }
            }

        }

    /**
     * Executes a boolean query combining two field queries and displays facet results.
     *
     * @param searcher The IndexSearcher instance.
     * @param taxoReader The TaxonomyReader instance.
     * @param scanner Scanner for user input.
     * @throws IOException If an I/O error occurs.
     */
    public void executeBooleanQuery(IndexSearcher searcher, TaxonomyReader taxoReader, Scanner scanner) throws IOException {
        // Input for the first query
        System.out.println("Enter field for first query: ");
        String f1 = scanner.nextLine();
        System.out.println("Enter search term (enter numeric double value for field 'overall'): ");
        String t1 = scanner.nextLine();

        // Input for the second query
        System.out.println("Enter field for second query: ");
        String f2 = scanner.nextLine();
        System.out.println("Enter search term (enter numeric double value for field 'overall'): ");
        String t2 = scanner.nextLine();

        Query query1 = createFieldQuery(f1, t1);
        Query query2 = createFieldQuery(f2, t2);

        // Choose boolean query mode
        System.out.println("Enter your choice: 1. Both should appear\n 2. Query 1 can be optional\n 3. Query 2 can be optional");
        int mode = Integer.parseInt(scanner.nextLine());

        BooleanQuery.Builder bQbuilder = new BooleanQuery.Builder();

        switch (mode) {
            case 1:
                bQbuilder.add(new BooleanClause(query1, BooleanClause.Occur.MUST));
                bQbuilder.add(new BooleanClause(query2, BooleanClause.Occur.MUST));
                break;
            case 2:
                bQbuilder.add(new BooleanClause(query1, BooleanClause.Occur.SHOULD));
                bQbuilder.add(new BooleanClause(query2, BooleanClause.Occur.MUST));
                break;
            case 3:
                bQbuilder.add(new BooleanClause(query1, BooleanClause.Occur.MUST));
                bQbuilder.add(new BooleanClause(query2, BooleanClause.Occur.SHOULD));
                break;
            default:
                System.out.println("Invalid choice. Exiting.");
                return;
        }

        BooleanQuery booleanQuery = bQbuilder.build();
        FacetsCollector facetsCollector = new FacetsCollector();
        FacetsCollector.search(searcher, booleanQuery, 10, facetsCollector);

        // Display facets
        Facets facetas = new FastTaxonomyFacetCounts(taxoReader, Indexer.getFconfig(), facetsCollector);
        List<FacetResult> TodasDims = facetas.getAllDims(100);
        System.out.println("Total number of categories: " + TodasDims.size());
        for (FacetResult fr : TodasDims) {
            System.out.println("Category: " + fr.dim);
            for (LabelAndValue lv : fr.labelValues) {
                System.out.println(lv.label + " (" + lv.value + ")");
            }
        }

        // Drill Down Example
        while (true) {
            System.out.println("To drill down, choose a category; to exit, press 0: ");
            System.out.println("1.asin\n2.date\n3.overall");
            int cat = Integer.parseInt(scanner.nextLine());

            if (cat==0) {
                System.out.println("Exiting drilling...");
                break; // Exit the loop
            }

            DrillDownQuery drillDownQuery = new DrillDownQuery(new FacetsConfig(), booleanQuery);

            if (cat==1) {
                System.out.println("Enter asin: ");
                String asin = scanner.nextLine();
                drillDownQuery.add("asin", asin);
            } else if (cat==2) {
                System.out.println("Enter year: ");
                String year = scanner.nextLine();
                drillDownQuery.add("date", year);

                while (true) { // Loop for navigating through months and beyond
                    // Perform search and collect facets
                    FacetsCollector facetsCollector2 = new FacetsCollector();
                    TopDocs drillDownDocs = FacetsCollector.search(searcher, drillDownQuery, 10, facetsCollector2);

                    // Retrieve facets for child categories (e.g., months)
                    Facets categoryFacets = new FastTaxonomyFacetCounts(taxoReader, Indexer.getFconfig(), facetsCollector2);
                    FacetResult categoryResult = categoryFacets.getTopChildren(10, "date", year);

                    if (categoryResult != null && categoryResult.labelValues.length > 0) {
                        System.out.println("Child categories under year " + year + ":");
                        for (LabelAndValue lv : categoryResult.labelValues) {
                            System.out.println("Month: " + lv.label + "(" + lv.value+")");
                        }

                        System.out.println("Enter a month to drill down further or press Enter to display results: ");
                        String month = scanner.nextLine();

                        if (month.isEmpty()) {
                            break; // Exit the inner loop to display results
                        }

                        // Add the selected month to the drill-down query
                        drillDownQuery.add("date", year + "/" + month);

                        break; // Exit to display results

                    } else {
                        System.out.println("No child facets found under the selected category.");
                        break;
                    }
                }
            } else if (cat==3) {
                System.out.println("Enter specific rating: ");
                Double rating = scanner.nextDouble();
                scanner.nextLine(); // Consume newline
                drillDownQuery.add("overall", String.valueOf(rating));
            }

            // Perform search and display results
            TopDocs drillDownDocs = searcher.search(drillDownQuery, 10);
            System.out.println("Drill Down Results: " + drillDownDocs.totalHits);
            for (ScoreDoc hit : drillDownDocs.scoreDocs) {
                Document doc = searcher.doc(hit.doc);
                System.out.println(f1 + ": " + doc.get(f1));
                System.out.println(f2 + ": " + doc.get(f2));
            }
        }
    }
    /**
     * Creates a query for the specified field and value.
     *
     * @param field The field to query.
     * @param value The value to search for in the field.
     * @return A Query instance for the specified field and value.
     */
    private Query createFieldQuery(String field, String value) {
        try {
            if (field.equals("overall")) {
                return DoublePoint.newExactQuery(field, Double.parseDouble(value));
            } else {
                return new TermQuery(new Term(field, value)); // Treat as text field by default
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid numeric value for field: " + field);
            throw e;
        }
    }

    }