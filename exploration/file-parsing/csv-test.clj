(ns exploration.file-parsing.csv-test
  "Exploration tests for CSV parsing functionality"
  (:require [user-upload.parser.csv :as csv-parser]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]))

(def sample-dir "exploration/file-parsing/samples/")

(defn test-file-path [filename]
  (str sample-dir filename))

(defn run-csv-parsing-tests []
  (println "\n=== CSV Parsing Tests ===\n")
  
  ;; Test 1: Basic CSV parsing
  (println "1. Testing basic CSV parsing:")
  (let [file-path (test-file-path "sample-users.csv")]
    (try
      (let [result (csv-parser/parse-csv-to-maps file-path)]
        (println "✓ Successfully parsed CSV file")
        (println "  Encoding:" (:encoding result))
        (println "  Total rows:" (:total-rows result))
        (println "  Data rows:" (:data-rows result))
        (println "  Filtered rows:" (:filtered-rows result))
        (println "  First user:")
        (pp/pprint (first (:data result))))
      (catch Exception e
        (println "✗ Failed to parse CSV:" (.getMessage e)))))
  
  ;; Test 2: File info
  (println "\n2. Testing file info extraction:")
  (let [file-path (test-file-path "sample-users.csv")]
    (try
      (let [info (csv-parser/get-file-info file-path)]
        (println "✓ File info extracted successfully")
        (pp/pprint info))
      (catch Exception e
        (println "✗ Failed to get file info:" (.getMessage e)))))
  
  ;; Test 3: CSV structure validation
  (println "\n3. Testing CSV structure validation:")
  (let [file-path (test-file-path "sample-users.csv")]
    (try
      (let [validation (csv-parser/validate-csv-structure file-path)]
        (println "✓ CSV structure validation completed")
        (println "  Valid?:" (:valid? validation))
        (println "  Headers:" (:headers validation))
        (when (:errors validation)
          (println "  Errors:" (:errors validation))))
      (catch Exception e
        (println "✗ Failed to validate CSV structure:" (.getMessage e)))))
  
  ;; Test 4: Empty file handling
  (println "\n4. Testing empty file handling:")
  (let [file-path (test-file-path "empty-file.csv")]
    (try
      (let [result (csv-parser/parse-csv-to-maps file-path)]
        (println "✓ Empty file handled gracefully")
        (println "  Data rows:" (count (:data result))))
      (catch Exception e
        (println "✗ Failed to handle empty file:" (.getMessage e)))))
  
  ;; Test 5: Encoding detection
  (println "\n5. Testing encoding detection:")
  (let [file-path (test-file-path "sample-users.csv")]
    (try
      (let [encoding (csv-parser/detect-encoding file-path)]
        (println "✓ Encoding detected:" (.name encoding)))
      (catch Exception e
        (println "✗ Failed to detect encoding:" (.getMessage e)))))
  
  ;; Test 6: Non-existent file
  (println "\n6. Testing non-existent file handling:")
  (let [file-path (test-file-path "non-existent.csv")]
    (try
      (csv-parser/parse-csv-to-maps file-path)
      (println "✗ Should have failed for non-existent file")
      (catch Exception e
        (println "✓ Properly handled non-existent file:" (.getMessage e)))))
  
  (println "\n=== CSV Parsing Tests Complete ===\n"))

;; Run tests when file is loaded
(when (= *command-line-args* ["run"])
  (run-csv-parsing-tests))

;; For REPL usage
(comment
  (run-csv-parsing-tests)
  
  ;; Individual test functions for interactive development
  (csv-parser/parse-csv-to-maps (test-file-path "sample-users.csv"))
  (csv-parser/get-file-info (test-file-path "sample-users.csv"))
  (csv-parser/validate-csv-structure (test-file-path "sample-with-issues.csv")))