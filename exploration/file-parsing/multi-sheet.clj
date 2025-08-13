(ns exploration.file-parsing.multi-sheet
  "Exploration of multi-sheet Excel file handling"
  (:require [user-upload.parser.excel :as excel-parser]
            [dk.ative.docjure.spreadsheet :as xl]
            [clojure.pprint :as pp]))

(def sample-dir "exploration/file-parsing/samples/")

(defn test-file-path [filename]
  (str sample-dir filename))

(defn create-multi-sheet-excel
  "Create a complex multi-sheet Excel file for testing"
  [file-path]
  (try
    (let [wb (xl/create-workbook 
               "Employee_Data"
               [["email" "first name" "last name" "job title" "mobile number" "teams" "user role"]
                ["john.doe@company.com" "John" "Doe" "Software Engineer" "+1234567890" "Engineering|Development" "TEAM MEMBER"]
                ["jane.smith@company.com" "Jane" "Smith" "Team Lead" "" "Engineering" "MANAGER"]
                ["bob.wilson@company.com" "Bob" "Wilson" "Project Manager" "+1234567891" "Management|Engineering" "ADMINISTRATOR"]]
               
               "Team_Structure"
               [["team_name" "parent_team" "manager_email" "description"]
                ["Engineering" "" "jane.smith@company.com" "Software Development"]
                ["Development" "Engineering" "john.doe@company.com" "Core Development"]
                ["QA" "Engineering" "alice.brown@company.com" "Quality Assurance"]
                ["Management" "" "bob.wilson@company.com" "Leadership Team"]]
               
               "Roles_Permissions"
               [["role_name" "permissions" "can_create_users" "can_manage_teams"]
                ["TEAM MEMBER" "read" false false]
                ["MANAGER" "read,write" false true]
                ["ADMINISTRATOR" "read,write,delete" true true]
                ["COMPANY ADMINISTRATOR" "all" true true]]
               
               "Empty_Sheet"
               []
               
               "Single_Header"
               [["lonely_header"]])]
      (xl/save-workbook! file-path wb)
      (println "✓ Created multi-sheet Excel file:" file-path))
    (catch Exception e
      (println "✗ Failed to create multi-sheet Excel file:" (.getMessage e)))))

(defn analyze-multi-sheet-file [file-path]
  (println "\n=== Multi-Sheet Analysis ===\n")
  
  ;; Get basic file info
  (println "1. File Information:")
  (try
    (let [info (excel-parser/get-file-info file-path)]
      (pp/pprint info))
    (catch Exception e
      (println "✗ Failed to get file info:" (.getMessage e))))
  
  ;; Parse all sheets
  (println "\n2. All Sheets Data:")
  (try
    (let [all-sheets (excel-parser/parse-all-sheets file-path)]
      (doseq [[sheet-name data] all-sheets]
        (println (str "\nSheet: " sheet-name))
        (println (str "  Rows: " (count data)))
        (when (seq data)
          (println "  Headers:" (keys (first data)))
          (println "  First row:")
          (pp/pprint (first data)))))
    (catch Exception e
      (println "✗ Failed to parse all sheets:" (.getMessage e))))
  
  ;; Test individual sheet parsing
  (println "\n3. Individual Sheet Parsing:")
  (let [sheet-names ["Employee_Data" "Team_Structure" "Roles_Permissions" "Empty_Sheet" "Single_Header"]]
    (doseq [sheet-name sheet-names]
      (try
        (let [data (excel-parser/parse-excel-file file-path sheet-name)]
          (println (str "✓ " sheet-name ": " (count data) " rows")))
        (catch Exception e
          (println (str "✗ " sheet-name ": " (.getMessage e)))))))
  
  ;; Test sheet by index
  (println "\n4. Sheet Access by Index:")
  (dotimes [i 5]
    (try
      (let [data (excel-parser/parse-excel-file file-path i)]
        (println (str "✓ Sheet " i ": " (count data) " rows")))
      (catch Exception e
        (println (str "✗ Sheet " i ": " (.getMessage e))))))
  
  (println "\n=== Multi-Sheet Analysis Complete ===\n"))

(defn test-edge-cases []
  (println "\n=== Edge Cases Testing ===\n")
  
  (let [file-path (test-file-path "multi-sheet-test.xlsx")]
    (create-multi-sheet-excel file-path)
    
    ;; Test 1: Empty sheet handling
    (println "1. Testing empty sheet:")
    (try
      (let [data (excel-parser/parse-excel-file file-path "Empty_Sheet")]
        (println "✓ Empty sheet handled, rows:" (count data)))
      (catch Exception e
        (println "✗ Failed on empty sheet:" (.getMessage e))))
    
    ;; Test 2: Sheet with only headers
    (println "\n2. Testing sheet with only headers:")
    (try
      (let [data (excel-parser/parse-excel-file file-path "Single_Header")]
        (println "✓ Header-only sheet handled, rows:" (count data)))
      (catch Exception e
        (println "✗ Failed on header-only sheet:" (.getMessage e))))
    
    ;; Test 3: Invalid sheet index
    (println "\n3. Testing invalid sheet index:")
    (try
      (excel-parser/parse-excel-file file-path 999)
      (println "✗ Should have failed for invalid index")
      (catch Exception e
        (println "✓ Properly handled invalid index:" (.getMessage e))))
    
    ;; Test 4: Mixed data types in cells
    (println "\n4. Testing mixed data types:")
    (try
      (let [data (excel-parser/parse-excel-file file-path "Roles_Permissions")]
        (println "✓ Mixed data types handled")
        (println "  Sample boolean values:")
        (pp/pprint (select-keys (first data) ["can_create_users" "can_manage_teams"])))
      (catch Exception e
        (println "✗ Failed on mixed data types:" (.getMessage e)))))
  
  (println "\n=== Edge Cases Testing Complete ===\n"))

(defn run-multi-sheet-tests []
  (println "\n=== Multi-Sheet Excel Testing ===\n")
  (let [file-path (test-file-path "multi-sheet-test.xlsx")]
    (create-multi-sheet-excel file-path)
    (analyze-multi-sheet-file file-path)
    (test-edge-cases)))

;; Run tests when file is loaded
(when (= *command-line-args* ["run"])
  (run-multi-sheet-tests))

;; For REPL usage
(comment
  (run-multi-sheet-tests)
  
  ;; Individual testing
  (let [file-path (test-file-path "multi-sheet-test.xlsx")]
    (create-multi-sheet-excel file-path)
    (excel-parser/get-file-info file-path))
  
  (excel-parser/parse-all-sheets (test-file-path "multi-sheet-test.xlsx")))