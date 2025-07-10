import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class SentimentAnalysis {

    private static final String INDEX_PATH = "C:\\Users\\xrist\\Documents\\pr6-facets\\index\\new"; // Enter your index path here.

    public static void main(String[] args) {
        try {
            performSentimentAnalysisAndClassification();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void performSentimentAnalysisAndClassification() throws IOException {
        Directory directory = FSDirectory.open(Paths.get(INDEX_PATH));
        IndexReader reader = DirectoryReader.open(directory);

        List<Integer> trainingDocIds = new ArrayList<>();
        List<Integer> testingDocIds = new ArrayList<>();
        prepareDatasets(reader, trainingDocIds, testingDocIds);

        List<Document> trainingDocs = extractDocuments(reader, trainingDocIds);
        List<Document> testingDocs = extractDocuments(reader, testingDocIds);

        if (trainingDocs.isEmpty() || testingDocs.isEmpty()) {
            System.out.println("Error: Insufficient training or testing data.");
            return;
        }

        int k = 4;
        ConfusionMatrix knnConfusionMatrix = evaluateKNNClassifier(trainingDocs, testingDocs, k);
        printEvaluationResults("k-NN", knnConfusionMatrix);

        ConfusionMatrix naiveBayesConfusionMatrix = evaluateNaiveBayesClassifier(trainingDocs, testingDocs);
        printEvaluationResults("Naive Bayes", naiveBayesConfusionMatrix);

        Scanner scanner = new Scanner(System.in);
        System.out.println("Please enter a review text for sentiment classification:");
        String userReview = scanner.nextLine();

        String predictedSentimentKNN = predictSentimentUsingKNN(userReview, trainingDocs, k);
        String predictedSentimentNB = predictSentimentUsingNaiveBayes(userReview, trainingDocs);

        System.out.println("Predicted Sentiment using k-NN: " + predictedSentimentKNN);
        System.out.println("Predicted Sentiment using Naive Bayes: " + predictedSentimentNB);

        reader.close();
    }

    private static void prepareDatasets(IndexReader reader, List<Integer> trainingDocIds, List<Integer> testingDocIds) throws IOException {
        int totalDocs = reader.maxDoc();
        int trainingSize = (int)(totalDocs * 0.8);
        Random random = new Random();
        List<Integer> allDocIds = new ArrayList<>();

        for (int i = 0; i < totalDocs; i++) {
            allDocIds.add(i);
        }

        Collections.shuffle(allDocIds, random);

        for (int i = 0; i < totalDocs; i++) {
            if (i < trainingSize) {
                trainingDocIds.add(allDocIds.get(i));
            } else {
                testingDocIds.add(allDocIds.get(i));
            }
        }
    }

    private static List<Document> extractDocuments(IndexReader reader, List<Integer> docIds) throws IOException {
        List<Document> docs = new ArrayList<>();
        for (Integer docId : docIds) {
            Document doc = reader.document(docId);
            docs.add(doc);
        }
        return docs;
    }

    private static ConfusionMatrix evaluateKNNClassifier(List<Document> trainingDocs, List<Document> testingDocs, int k) {
        int truePositive = 0, falsePositive = 0, trueNegative = 0, falseNegative = 0;

        for (Document doc : testingDocs) {
            String reviewText = doc.get("reviewText");
            String originalSentiment = doc.get("sentiment");

            if (reviewText != null) {
                String predictedSentiment = predictSentimentUsingKNN(reviewText, trainingDocs, k);

                if (originalSentiment.equals(predictedSentiment)) {
                    if (predictedSentiment.equals("positive")) {
                        truePositive++;
                    } else {
                        trueNegative++;
                    }
                } else {
                    if (predictedSentiment.equals("positive")) {
                        falsePositive++;
                    } else {
                        falseNegative++;
                    }
                }
            }
            if (reviewText == null || reviewText.trim().isEmpty()) {
                continue; // Skip this document
            }
        }

        return new ConfusionMatrix(truePositive, falsePositive, trueNegative, falseNegative);
    }

    private static ConfusionMatrix evaluateNaiveBayesClassifier(List<Document> trainingDocs, List<Document> testingDocs) {
        int truePositive = 0, falsePositive = 0, trueNegative = 0, falseNegative = 0;

        for (Document doc : testingDocs) {
            String reviewText = doc.get("reviewText");
            String originalSentiment = doc.get("sentiment");

            if (reviewText != null) {
                String predictedSentiment = predictSentimentUsingNaiveBayes(reviewText, trainingDocs);

                if (originalSentiment.equals(predictedSentiment)) {
                    if (predictedSentiment.equals("positive")) {
                        truePositive++;
                    } else {
                        trueNegative++;
                    }
                } else {
                    if (predictedSentiment.equals("positive")) {
                        falsePositive++;
                    } else {
                        falseNegative++;
                    }
                }
            }
        }

        return new ConfusionMatrix(truePositive, falsePositive, trueNegative, falseNegative);
    }

    private static void printEvaluationResults(String classifierName, ConfusionMatrix confusionMatrix) {
        System.out.println("\n" + classifierName + " Sentiment Classification Evaluation:");
        System.out.println("Accuracy: " + confusionMatrix.getAccuracy());
        System.out.println("Precision: " + confusionMatrix.getPrecision());
        System.out.println("Recall: " + confusionMatrix.getRecall());
        System.out.println("F1 Score: " + confusionMatrix.getF1Score());

        System.out.println("\nConfusion Matrix Details:");
        System.out.println(confusionMatrix);
    }

    public static class ConfusionMatrix {
        private int truePositive, falsePositive, trueNegative, falseNegative;

        public ConfusionMatrix(int truePositive, int falsePositive, int trueNegative, int falseNegative) {
            this.truePositive = truePositive;
            this.falsePositive = falsePositive;
            this.trueNegative = trueNegative;
            this.falseNegative = falseNegative;
        }

        public double getAccuracy() {
            return (double) (truePositive + trueNegative) / (truePositive + falsePositive + trueNegative + falseNegative);
        }

        public double getPrecision() {
            return truePositive + falsePositive == 0 ? 0 : (double) truePositive / (truePositive + falsePositive);
        }

        public double getRecall() {
            return truePositive + falseNegative == 0 ? 0 : (double) truePositive / (truePositive + falseNegative);
        }

        public double getF1Score() {
            double precision = getPrecision();
            double recall = getRecall();
            return precision + recall == 0 ? 0 : 2 * (precision * recall) / (precision + recall);
        }

        @Override
        public String toString() {
            return "Confusion Matrix: \n" +
                    "True Positive: " + truePositive + "\n" +
                    "False Positive: " + falsePositive + "\n" +
                    "True Negative: " + trueNegative + "\n" +
                    "False Negative: " + falseNegative;
        }
    }

    private static String predictSentimentUsingKNN(String reviewText, List<Document> trainingDocs, int k) {
        Map<String, Integer> sentimentVotes = new HashMap<>();

        for (Document trainingDoc : trainingDocs) {
            String trainingReviewText = trainingDoc.get("reviewText");
            String trainingSentiment = trainingDoc.get("sentiment");

            double distance = calculateTextDistance(reviewText, trainingReviewText);

            sentimentVotes.put(trainingSentiment, sentimentVotes.getOrDefault(trainingSentiment, 0) + 1);
        }


        return sentimentVotes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get()
                .getKey();
    }
    private static double calculateTextSimilarity(String text1, String text2) {
        Map<String, Integer> tf1 = computeTermFrequencies(text1);
        Map<String, Integer> tf2 = computeTermFrequencies(text2);

        double dotProduct = 0.0, magnitude1 = 0.0, magnitude2 = 0.0;

        for (String term : tf1.keySet()) {
            dotProduct += tf1.get(term) * tf2.getOrDefault(term, 0);
            magnitude1 += Math.pow(tf1.get(term), 2);
        }

        for (int freq : tf2.values()) {
            magnitude2 += Math.pow(freq, 2);
        }

        if (magnitude1 == 0 || magnitude2 == 0) {
            return 0.0;
        }
        //we calculate the cosine similarity
        return dotProduct / (Math.sqrt(magnitude1) * Math.sqrt(magnitude2));
    }

    private static double calculateTextDistance(String text1, String text2) {
        double similarity = calculateTextSimilarity(text1, text2);
        return 1.0 - similarity;
    }


    private static Map<String, Integer> computeTermFrequencies(String text) {
        Map<String, Integer> termFreq = new HashMap<>();
        for (String word : text.toLowerCase().split("\\s+")) {
            termFreq.put(word, termFreq.getOrDefault(word, 0) + 1);
        }
        return termFreq;
    }


    private static String predictSentimentUsingNaiveBayes(String reviewText, List<Document> trainingDocs) {
        Map<String, Integer> positiveWordCount = new HashMap<>();
        Map<String, Integer> negativeWordCount = new HashMap<>();
        int positiveDocCount = 0, negativeDocCount = 0;
        int totalPositiveWords = 0, totalNegativeWords = 0;

        for (Document doc : trainingDocs) {
            String sentiment = doc.get("sentiment");
            String[] words = doc.get("reviewText").toLowerCase().split("\\s+");

            if ("positive".equals(sentiment)) {
                positiveDocCount++;
                for (String word : words) {
                    positiveWordCount.put(word, positiveWordCount.getOrDefault(word, 0) + 1);
                    totalPositiveWords++;
                }
            } else if ("negative".equals(sentiment)) {
                negativeDocCount++;
                for (String word : words) {
                    negativeWordCount.put(word, negativeWordCount.getOrDefault(word, 0) + 1);
                    totalNegativeWords++;
                }
            }
        }

        double probPositive = (double) positiveDocCount / (positiveDocCount + negativeDocCount);
        double probNegative = (double) negativeDocCount / (positiveDocCount + negativeDocCount);


        // we seperate the words in the input
        String[] reviewWords = reviewText.split("\\s+");

        // calculating the probability for sentiment using Naive Bayes
        double positiveProbability = Math.log(probPositive);
        double negativeProbability = Math.log(probNegative);

        // calculating the likelihood
        for (String word : reviewWords) {
            word = word.toLowerCase();

            double wordProbPositive = (double) (positiveWordCount.getOrDefault(word, 0) + 1) / (totalPositiveWords + positiveWordCount.size());
            positiveProbability += Math.log(wordProbPositive);

            double wordProbNegative = (double) (negativeWordCount.getOrDefault(word, 0) + 1) / (totalNegativeWords + negativeWordCount.size());
            negativeProbability += Math.log(wordProbNegative);
        }

        if (positiveProbability > negativeProbability) {
            return "positive";
        } else {
            return "negative";
        }
    }
}