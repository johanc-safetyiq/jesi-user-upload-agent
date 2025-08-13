(ns exploration.file-parsing.validate-rows
  "Exploration tests for data validation functionality"
  (:require [user-upload.parser.validate :as validate]
            [clojure.pprint :as pp]))

(defn test-individual-field-validation []
  (println "\n=== Individual Field Validation Tests ===\n")
  
  ;; Test 1: Email validation
  (println "1. Testing email validation:")
  (let [test-emails ["john@example.com" "invalid-email" "" "user@domain.co.uk" "test@sub.domain.com"]]
    (doseq [email test-emails]
      (let [result (validate/validate-email email)]
        (if (:valid? result)
          (println (str "  ✓ " email " -> " (:normalized-value result)))
          (println (str "  ✗ " email " -> " (:error result)))))))
  
  ;; Test 2: User role validation
  (println "\n2. Testing user role validation:")
  (let [test-roles ["TEAM MEMBER" "manager" "Administrator" "INVALID_ROLE" "" "company administrator"]]
    (doseq [role test-roles]
      (let [result (validate/validate-user-role role)]
        (if (:valid? result)
          (println (str "  ✓ " role " -> " (:normalized-value result)))
          (println (str "  ✗ " role " -> " (:error result)))))))
  
  ;; Test 3: Teams parsing
  (println "\n3. Testing teams parsing:")
  (let [test-teams ["Engineering|Development" "QA|Testing|Support" "Management" "" "Team1|Team2|Team1"]]
    (doseq [teams test-teams]
      (let [result (validate/parse-teams teams)]
        (if (:valid? result)
          (println (str "  ✓ '" teams "' -> " (:normalized-value result)))
          (println (str "  ✗ '" teams "' -> " (:error result)))))))
  
  ;; Test 4: Required fields
  (println "\n4. Testing required field validation:")
  (let [test-data [["First name" "John"] ["Last name" ""] ["First name" "   "] ["Last name" "Smith"]]]
    (doseq [[field-name value] test-data]
      (let [result (validate/validate-required-field field-name value)]
        (if (:valid? result)
          (println (str "  ✓ " field-name ": '" value "' -> '" (:normalized-value result) "'"))
          (println (str "  ✗ " field-name ": '" value "' -> " (:error result)))))))
  
  ;; Test 5: Mobile number validation
  (println "\n5. Testing mobile number validation:")
  (let [test-mobiles ["+1234567890" "" "   " "555-1234" "0"]]
    (doseq [mobile test-mobiles]
      (let [result (validate/validate-mobile-number mobile)]
        (println (str "  ✓ '" mobile "' -> '" (:normalized-value result) "'")))))
  
  (println "\n=== Individual Field Validation Tests Complete ===\n"))

(defn test-row-validation []
  (println "\n=== Row Validation Tests ===\n")
  
  ;; Test 1: Valid row
  (println "1. Testing valid row:")
  (let [valid-row {"email" "john@example.com"
                   "first name" "John"
                   "last name" "Doe"
                   "job title" "Software Engineer"
                   "mobile number" "+1234567890"
                   "teams" "Engineering|Development"
                   "user role" "TEAM MEMBER"}]
    (try
      (let [result (validate/validate-row valid-row 1)]
        (if (:valid? result)
          (do
            (println "  ✓ Row is valid")
            (println "  Normalized email:" (get-in result [:normalized-data :email]))
            (println "  Normalized teams:" (get-in result [:normalized-data :teams]))
            (println "  Normalized role:" (get-in result [:normalized-data :user-role])))
          (do
            (println "  ✗ Row validation failed")
            (pp/pprint (:errors result)))))
      (catch Exception e
        (println "  ✗ Exception during row validation:" (.getMessage e)))))
  
  ;; Test 2: Invalid row
  (println "\n2. Testing invalid row:")
  (let [invalid-row {"email" "invalid-email"
                     "first name" ""
                     "last name" "Smith"
                     "job title" "Manager"
                     "mobile number" ""
                     "teams" ""
                     "user role" "INVALID_ROLE"}]
    (try
      (let [result (validate/validate-row invalid-row 2)]
        (println (str "  Valid?: " (:valid? result)))
        (println "  Errors:")
        (doseq [error (:errors result)]
          (println (str "    " (:field error) ": " (:error error)))))
      (catch Exception e
        (println "  ✗ Exception during row validation:" (.getMessage e)))))
  
  ;; Test 3: Row with missing mobile (should default)
  (println "\n3. Testing row with empty mobile number:")
  (let [row-no-mobile {"email" "alice@example.com"
                       "first name" "Alice"
                       "last name" "Brown"
                       "job title" "QA Engineer"
                       "mobile number" ""
                       "teams" "QA"
                       "user role" "TEAM MEMBER"}]
    (try
      (let [result (validate/validate-row row-no-mobile 3)]
        (if (:valid? result)
          (println (str "  ✓ Mobile defaulted to: " (get-in result [:normalized-data :mobile-number])))
          (println "  ✗ Should have been valid with default mobile")))
      (catch Exception e
        (println "  ✗ Exception:" (.getMessage e)))))
  
  (println "\n=== Row Validation Tests Complete ===\n"))

(defn test-dataset-validation []
  (println "\n=== Dataset Validation Tests ===\n")
  
  ;; Test 1: Dataset with mixed valid/invalid rows
  (println "1. Testing mixed dataset:")
  (let [dataset [{"email" "john@example.com"
                  "first name" "John"
                  "last name" "Doe"
                  "job title" "Engineer"
                  "mobile number" "+123456"
                  "teams" "Engineering"
                  "user role" "TEAM MEMBER"}
                 {"email" "invalid-email"
                  "first name" ""
                  "last name" "Smith"
                  "job title" "Manager"
                  "mobile number" ""
                  "teams" "Management"
                  "user role" "INVALID"}
                 {"email" "alice@example.com"
                  "first name" "Alice"
                  "last name" "Brown"
                  "job title" "QA"
                  "mobile number" ""
                  "teams" "QA|Testing"
                  "user role" "MANAGER"}]]
    (try
      (let [result (validate/validate-dataset dataset)]
        (println "✓ Dataset validation completed")
        (println "  Summary:" (:summary result))
        (println "  Total rows:" (:total-rows result))
        (println "  Valid rows:" (:valid-rows result))
        (println "  Invalid rows:" (:invalid-rows result))
        (println "  Error breakdown:")
        (pp/pprint (:error-summary result)))
      (catch Exception e
        (println "✗ Failed to validate dataset:" (.getMessage e)))))
  
  ;; Test 2: Dataset with duplicate emails
  (println "\n2. Testing duplicate email detection:")
  (let [dataset-with-dupes [{"email" "john@example.com"
                             "first name" "John"
                             "last name" "Doe"
                             "job title" "Engineer"
                             "mobile number" "+123456"
                             "teams" "Engineering"
                             "user role" "TEAM MEMBER"}
                            {"email" "john@example.com"
                             "first name" "Johnny"
                             "last name" "Doe"
                             "job title" "Manager"
                             "mobile number" "+789012"
                             "teams" "Management"
                             "user role" "MANAGER"}]]
    (try
      (let [result (validate/validate-dataset dataset-with-dupes)]
        (println "✓ Duplicate email detection completed")
        (println "  Unique emails?:" (get-in result [:email-uniqueness :unique?]))
        (println "  Duplicate emails:")
        (pp/pprint (get-in result [:email-uniqueness :duplicates]))
        (println "  Valid rows after duplicate check:" (:valid-rows result)))
      (catch Exception e
        (println "✗ Failed duplicate email detection:" (.getMessage e)))))
  
  ;; Test 3: Empty dataset
  (println "\n3. Testing empty dataset:")
  (try
    (let [result (validate/validate-dataset [])]
      (println "✓ Empty dataset handled")
      (println "  Valid?:" (:valid? result))
      (println "  Summary:" (:summary result)))
    (catch Exception e
      (println "✗ Failed to handle empty dataset:" (.getMessage e))))
  
  (println "\n=== Dataset Validation Tests Complete ===\n"))

(defn test-validation-utilities []
  (println "\n=== Validation Utilities Tests ===\n")
  
  ;; Test extracting valid/invalid rows
  (println "1. Testing row extraction utilities:")
  (let [dataset [{"email" "valid@example.com"
                  "first name" "Valid"
                  "last name" "User"
                  "job title" "Engineer"
                  "mobile number" "+123456"
                  "teams" "Engineering"
                  "user role" "TEAM MEMBER"}
                 {"email" "invalid-email"
                  "first name" ""
                  "last name" "Invalid"
                  "job title" "Manager"
                  "mobile number" ""
                  "teams" "Management"
                  "user role" "INVALID"}]]
    (try
      (let [validation-result (validate/validate-dataset dataset)
            valid-rows (validate/get-valid-rows validation-result)
            invalid-rows (validate/get-invalid-rows validation-result)]
        (println "  ✓ Valid rows extracted:" (count valid-rows))
        (println "  ✓ Invalid rows extracted:" (count invalid-rows))
        (println "  First valid row email:" (get-in (first valid-rows) [:email]))
        (when (seq invalid-rows)
          (println "  First invalid row errors:" (count (get-in (first invalid-rows) [:errors])))))
      (catch Exception e
        (println "  ✗ Failed row extraction:" (.getMessage e)))))
  
  ;; Test validation report generation
  (println "\n2. Testing validation report:")
  (let [dataset [{"email" "john@example.com"
                  "first name" "John"
                  "last name" "Doe"
                  "job title" "Engineer"
                  "mobile number" "+123456"
                  "teams" "Engineering"
                  "user role" "TEAM MEMBER"}]]
    (try
      (let [validation-result (validate/validate-dataset dataset)
            report (validate/create-validation-report validation-result)
            summary (validate/get-validation-summary validation-result)]
        (println "  ✓ Validation report generated")
        (println "  Success rate:" (get-in report [:statistics :success-rate]))
        (println "  Summary:" summary))
      (catch Exception e
        (println "  ✗ Failed to generate report:" (.getMessage e)))))
  
  (println "\n=== Validation Utilities Tests Complete ===\n"))

(defn run-all-validation-tests []
  (println "\n=== Running All Validation Tests ===\n")
  (test-individual-field-validation)
  (test-row-validation)
  (test-dataset-validation)
  (test-validation-utilities)
  (println "\n=== All Validation Tests Complete ===\n"))

;; Run tests when file is loaded
(when (= *command-line-args* ["run"])
  (run-all-validation-tests))

;; For REPL usage
(comment
  (run-all-validation-tests)
  
  ;; Individual test functions
  (test-individual-field-validation)
  (test-row-validation)
  (test-dataset-validation)
  
  ;; Interactive testing
  (validate/validate-email "test@example.com")
  (validate/validate-user-role "team member")
  (validate/parse-teams "Engineering|Development")
  
  (validate/validate-row {"email" "john@example.com"
                          "first name" "John"
                          "last name" "Doe"
                          "job title" "Engineer"
                          "mobile number" "+123456"
                          "teams" "Engineering"
                          "user role" "TEAM MEMBER"} 1))