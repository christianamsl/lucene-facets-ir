# Lucene Faceted Search & Sentiment Analysis

This project was developed during our Erasmus studies in Granada, Spain, as part of the course **Recuperación de Información** at the University of Granada.

---

## Project Description

This Java-based application implements an **Information Retrieval System** using **Apache Lucene**, supporting faceted indexing and search, as well as basic sentiment analysis.

The application indexes JSON review documents (e.g., from Amazon), applies a hierarchical taxonomy for drill-down search, and supports three types of queries: simple, numeric, and boolean. It also features sentiment classification using **k-NN** and **Naive Bayes**.

---

## Learning Objectives

- Index documents with taxonomy and facets using Apache Lucene
- Implement different query types (simple, numeric, boolean)
- Drill down on search facets and explore hierarchical relationships
- Perform basic sentiment analysis with machine learning
- Evaluate model accuracy using confusion matrix

---

## System Architecture

The system consists of two main Java classes:

- `Indexer.java`: Indexes JSON review data with taxonomy support
- `Facetery.java`: Handles user interaction, searching, faceting, and sentiment analysis

Sentiment analysis includes:
- `k-NN` classification based on cosine similarity
- `Naive Bayes` with Laplace smoothing

---

## Technologies Used

- Java
- Apache Lucene
- Custom ML implementations (k-NN, Naive Bayes)
- CLI-based interaction

---

## Course Info

- **Course:** Recuperación de Información  
- **Instructor:** Juan F. Huete  
- **University:** Universidad de Granada  
- **Academic Year:** 2024/2025

---

## Authors

- Carolin Irrgang
- Christiana Mousele 
 

---

## License

This project was developed as part of the Erasmus program and is for educational use only.
