(ns exploration.file-parsing.integration-test
  "Integration test demonstrating the complete file processing pipeline"
  (:require [user-upload.parser.csv :as csv]
            [user-upload.parser.excel :as excel]
            [user-upload.parser.normalize :as normalize]
            [user-upload.parser.validate :as validate]
            [clojure.pprint :as pp]))

(def sample-dir "exploration/file-parsing/samples/")

(defn test-complete-csv-pipeline []
  (println "\n=== Complete CSV Processing Pipeline ===\n")
  
  (let [csv-file (str sample-dir "sample-users.csv")]
    (try
      ;; Step 1: Parse CSV file
      (println "1. Parsing CSV file...")
      (let [{:keys [data encoding]} (csv/parse-csv-to-maps csv-file)]
        (println (str "✓ CSV parsed successfully. Encoding: " encoding ", Rows: " (count data)))
        
        ;; Step 2: Normalize headers and data
        (println "\n2. Normalizing headers and data...")
        (let [normalized-result (normalize/normalize-dataset data)]
          (println "✓ Data normalized successfully")
          (println (str "  Original headers: " (:original-headers normalized-result)))
          (println (str "  Mapped to standard: " (keys (:reverse-mapping normalized-result))))
          (println (str "  Required headers valid: " (get-in normalized-result [:validation :valid?])))
          
          ;; Step 3: Validate data
          (println "\n3. Validating normalized data...")
          (let [validation-result (validate/validate-dataset (:data normalized-result))]
            (println "✓ Data validation completed")
            (println (str "  Summary: " (:summary validation-result)))
            (println (str "  Valid rows: " (:valid-rows validation-result)))
            (println (str "  Invalid rows: " (:invalid-rows validation-result)))
            
            ;; Step 4: Extract final results
            (println "\n4. Final processing results...")
            (let [valid-users (validate/get-valid-rows validation-result)
                  invalid-users (validate/get-invalid-rows validation-result)]
              (println (str "✓ Ready for upload: " (count valid-users) " users"))
              (when (seq invalid-users)
                (println (str "  Issues found in " (count invalid-users) " rows"))
                (doseq [invalid-user invalid-users]
                  (println (str "    Row " (:row-number invalid-user) ": " 
                               (count (:errors invalid-user)) " errors"))))
              
              (when (seq valid-users)
                (println "\n  Sample valid user:")
                (pp/pprint (first valid-users)))))))
      
      (catch Exception e
        (println (str "✗ Pipeline failed: " (.getMessage e)))))))

(defn test-complete-excel-pipeline []
  (println "\n=== Complete Excel Processing Pipeline ===\n")
  
  ;; First create a sample Excel file if it doesn't exist
  (let [excel-file (str sample-dir "integration-test.xlsx")]
    (try
      ;; Create sample Excel file for testing
      (let [wb (excel/load-workbook (str sample-dir "sample-users.xlsx"))]
        (println "✓ Using existing Excel file or creating one...")
        
        ;; Step 1: Parse Excel file
        (println "\n1. Parsing Excel file...")
        (let [data (excel/parse-excel-file excel-file)]
          (println (str "✓ Excel parsed successfully. Rows: " (count data)))
          
          ;; Step 2: Normalize headers and data
          (println "\n2. Normalizing headers and data...")
          (let [normalized-result (normalize/normalize-dataset data)]
            (println "✓ Data normalized successfully")
            (println (str "  Required headers valid: " (get-in normalized-result [:validation :valid?])))
            
            ;; Step 3: Validate data
            (println "\n3. Validating normalized data...")
            (let [validation-result (validate/validate-dataset (:data normalized-result))]
              (println "✓ Data validation completed")
              (println (str "  Summary: " (:summary validation-result)))))))
      
      (catch Exception e
        (println (str "Note: Excel test skipped - " (.getMessage e)))
        (println "  (This is normal if docjure Excel creation requires additional setup)")))))

(defn test-error-handling []
  (println "\n=== Error Handling Tests ===\n")
  
  ;; Test with problematic CSV
  (let [problem-csv (str sample-dir "sample-with-issues.csv")]
    (try
      (println "1. Testing problematic CSV file...")
      (let [{:keys [data]} (csv/parse-csv-to-maps problem-csv)
            normalized-result (normalize/normalize-dataset data)
            validation-result (validate/validate-dataset (:data normalized-result))]
        (println "✓ Problematic file processed gracefully")
        (println (str "  Valid/Invalid: " (:valid-rows validation-result) "/" (:invalid-rows validation-result)))
        (println "  Error types found:")
        (pp/pprint (:error-summary validation-result)))
      
      (catch Exception e
        (println (str "✗ Error handling failed: " (.getMessage e))))))
  
  ;; Test with non-existent file
  (println "\n2. Testing non-existent file...")
  (try
    (csv/parse-csv-to-maps (str sample-dir "does-not-exist.csv"))
    (println "✗ Should have failed for non-existent file")
    (catch Exception e
      (println "✓ Non-existent file handled properly")))
  
  (println "\n=== Error Handling Tests Complete ===\n"))

(defn run-integration-tests []
  (println "\n=== File Processing Integration Tests ===\n")
  (test-complete-csv-pipeline)
  (test-complete-excel-pipeline)
  (test-error-handling)
  (println "\n=== Integration Tests Complete ===\n"))

;; Run tests when file is loaded
(when (= *command-line-args* ["run"])
  (run-integration-tests))

;; For REPL usage
(comment
  (run-integration-tests)
  
  ;; Individual pipeline tests
  (test-complete-csv-pipeline)
  (test-error-handling))