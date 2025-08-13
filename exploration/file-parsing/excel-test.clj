(ns exploration.file-parsing.excel-test
  "Exploration tests for Excel parsing functionality"
  (:require [user-upload.parser.excel :as excel-parser]
            [dk.ative.docjure.spreadsheet :as xl]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]))

(def sample-dir "exploration/file-parsing/samples/")

(defn test-file-path [filename]
  (str sample-dir filename))

(defn create-sample-excel-file
  "Create a sample Excel file for testing"
  [file-path]
  (try
    (let [wb (xl/create-workbook "Users"
                                [["email" "first name" "last name" "job title" "mobile number" "teams" "user role"]
                                 ["john.doe@company.com" "John" "Doe" "Software Engineer" "+1234567890" "Engineering|Development" "TEAM MEMBER"]
                                 ["jane.smith@company.com" "Jane" "Smith" "Team Lead" "" "Engineering" "MANAGER"]
                                 ["bob.wilson@company.com" "Bob" "Wilson" "Project Manager" "+1234567891" "Management|Engineering" "ADMINISTRATOR"]
                                 ["alice.brown@company.com" "Alice" "Brown" "Quality Assurance" "0" "QA|Testing" "TEAM MEMBER"]]
                                "Teams"
                                [["team_name" "description" "manager"]
                                 ["Engineering" "Software Development Team" "jane.smith@company.com"]
                                 ["QA" "Quality Assurance Team" "alice.brown@company.com"]
                                 ["Management" "Management Team" "bob.wilson@company.com"]])]
      (xl/save-workbook! file-path wb)
      (println "✓ Created sample Excel file:" file-path))
    (catch Exception e
      (println "✗ Failed to create sample Excel file:" (.getMessage e)))))

(defn run-excel-parsing-tests []
  (println "\n=== Excel Parsing Tests ===\n")
  
  ;; Create sample Excel file
  (let [excel-file-path (test-file-path "sample-users.xlsx")]
    (create-sample-excel-file excel-file-path)
    
    ;; Test 1: Basic Excel parsing
    (println "\n1. Testing basic Excel parsing:")
    (try
      (let [result (excel-parser/parse-excel-file excel-file-path)]
        (println "✓ Successfully parsed Excel file")
        (println "  Total rows:" (count result))
        (println "  First user:")
        (pp/pprint (first result)))
      (catch Exception e
        (println "✗ Failed to parse Excel:" (.getMessage e))))
    
    ;; Test 2: File info
    (println "\n2. Testing Excel file info extraction:")
    (try
      (let [info (excel-parser/get-file-info excel-file-path)]
        (println "✓ Excel file info extracted successfully")
        (pp/pprint info))
      (catch Exception e
        (println "✗ Failed to get Excel file info:" (.getMessage e))))
    
    ;; Test 3: Multi-sheet parsing
    (println "\n3. Testing multi-sheet parsing:")
    (try
      (let [all-sheets (excel-parser/parse-all-sheets excel-file-path)]
        (println "✓ Successfully parsed all sheets")
        (println "  Sheet names:" (keys all-sheets))
        (doseq [[sheet-name data] all-sheets]
          (println (str "  " sheet-name " rows: " (count data)))))
      (catch Exception e
        (println "✗ Failed to parse all sheets:" (.getMessage e))))
    
    ;; Test 4: Specific sheet parsing
    (println "\n4. Testing specific sheet parsing:")
    (try
      (let [teams-data (excel-parser/parse-excel-file excel-file-path "Teams")]
        (println "✓ Successfully parsed Teams sheet")
        (println "  Teams data rows:" (count teams-data))
        (println "  First team:")
        (pp/pprint (first teams-data)))
      (catch Exception e
        (println "✗ Failed to parse specific sheet:" (.getMessage e))))
    
    ;; Test 5: Sheet names extraction
    (println "\n5. Testing sheet names extraction:")
    (try
      (let [workbook (excel-parser/load-workbook excel-file-path)
            sheet-names (excel-parser/sheet-names workbook)]
        (println "✓ Successfully extracted sheet names")
        (println "  Sheet names:" sheet-names))
      (catch Exception e
        (println "✗ Failed to extract sheet names:" (.getMessage e))))
    
    ;; Test 6: Non-existent file
    (println "\n6. Testing non-existent file handling:")
    (try
      (excel-parser/parse-excel-file (test-file-path "non-existent.xlsx"))
      (println "✗ Should have failed for non-existent file")
      (catch Exception e
        (println "✓ Properly handled non-existent file:" (.getMessage e))))
    
    ;; Test 7: Invalid sheet name
    (println "\n7. Testing invalid sheet name handling:")
    (try
      (excel-parser/parse-excel-file excel-file-path "NonExistentSheet")
      (println "✗ Should have failed for non-existent sheet")
      (catch Exception e
        (println "✓ Properly handled non-existent sheet:" (.getMessage e)))))
  
  (println "\n=== Excel Parsing Tests Complete ===\n"))

;; Run tests when file is loaded
(when (= *command-line-args* ["run"])
  (run-excel-parsing-tests))

;; For REPL usage
(comment
  (run-excel-parsing-tests)
  
  ;; Individual test functions for interactive development
  (let [file-path (test-file-path "sample-users.xlsx")]
    (create-sample-excel-file file-path)
    (excel-parser/parse-excel-file file-path))
  
  (excel-parser/get-file-info (test-file-path "sample-users.xlsx"))
  (excel-parser/parse-all-sheets (test-file-path "sample-users.xlsx")))