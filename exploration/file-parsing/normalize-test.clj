(ns exploration.file-parsing.normalize-test
  "Exploration tests for header normalization and mapping"
  (:require [user-upload.parser.normalize :as normalize]
            [clojure.pprint :as pp]))

(defn test-header-normalization []
  (println "\n=== Header Normalization Tests ===\n")
  
  ;; Test 1: Basic header normalization
  (println "1. Testing basic header normalization:")
  (let [headers ["EMAIL", "First Name ", "LAST_NAME", "Job-Title", " Mobile Number", "Teams ", "User Role"]]
    (try
      (let [normalized (normalize/normalize-headers headers)]
        (println "✓ Headers normalized successfully")
        (println "  Original:" headers)
        (println "  Normalized:" normalized))
      (catch Exception e
        (println "✗ Failed to normalize headers:" (.getMessage e)))))
  
  ;; Test 2: Header mapping to standards
  (println "\n2. Testing header mapping to standards:")
  (let [headers ["email", "first name", "surname", "mobile", "team", "role"]]
    (try
      (let [mapping-result (normalize/map-headers-to-standard headers)]
        (println "✓ Headers mapped successfully")
        (println "  Original headers:" headers)
        (println "  Mapping:" (:mapping mapping-result))
        (println "  Reverse mapping:" (:reverse-mapping mapping-result))
        (println "  Unmapped headers:" (:unmapped-headers mapping-result)))
      (catch Exception e
        (println "✗ Failed to map headers:" (.getMessage e)))))
  
  ;; Test 3: Required header validation
  (println "\n3. Testing required header validation:")
  (let [mapped-headers {"email" "email", "first name" "first name", "last name" "last name"}]
    (try
      (let [validation (normalize/validate-required-headers mapped-headers)]
        (println "✓ Required headers validated")
        (println "  Missing headers:" (:missing-headers validation))
        (println "  Extra headers:" (:extra-headers validation))
        (println "  Valid?:" (:valid? validation)))
      (catch Exception e
        (println "✗ Failed to validate required headers:" (.getMessage e)))))
  
  ;; Test 4: Edge cases with messy headers
  (println "\n4. Testing edge cases with messy headers:")
  (let [messy-headers ["E-MAIL!!!", "First_Name", "lastname", "Job Title???", "Mobile#", "Team Names", "User-Role"]]
    (try
      (let [result (normalize/map-headers-to-standard messy-headers)]
        (println "✓ Messy headers handled")
        (println "  Original:" messy-headers)
        (println "  Mapped to:" (:reverse-mapping result)))
      (catch Exception e
        (println "✗ Failed to handle messy headers:" (.getMessage e)))))
  
  ;; Test 5: Duplicate headers
  (println "\n5. Testing duplicate header handling:")
  (let [duplicate-headers ["email", "Email", "EMAIL", "first name", "first name"]]
    (try
      (let [normalized (normalize/normalize-headers duplicate-headers)]
        (println "✓ Duplicates handled")
        (println "  Original:" duplicate-headers)
        (println "  After deduplication:" normalized))
      (catch Exception e
        (println "✗ Failed to handle duplicates:" (.getMessage e)))))
  
  (println "\n=== Header Normalization Tests Complete ===\n"))

(defn test-dataset-normalization []
  (println "\n=== Dataset Normalization Tests ===\n")
  
  ;; Test 1: Full dataset normalization
  (println "1. Testing full dataset normalization:")
  (let [dataset [{"EMAIL" "john@example.com" "First Name" "John" "surname" "Doe" 
                  "Job-Title" "Engineer" "mobile" "+123456" "teams" "Engineering" "role" "TEAM MEMBER"}
                 {"EMAIL" "jane@example.com" "First Name" "Jane" "surname" "Smith" 
                  "Job-Title" "Manager" "mobile" "+789012" "teams" "Management" "role" "MANAGER"}]]
    (try
      (let [result (normalize/normalize-dataset dataset)]
        (println "✓ Dataset normalized successfully")
        (println "  Original headers:" (:original-headers result))
        (println "  Mapped to standard:" (keys (:reverse-mapping result)))
        (println "  Validation valid?:" (get-in result [:validation :valid?]))
        (println "  Missing required:" (get-in result [:validation :missing-headers]))
        (println "  First normalized row:")
        (pp/pprint (first (:data result))))
      (catch Exception e
        (println "✗ Failed to normalize dataset:" (.getMessage e)))))
  
  ;; Test 2: Empty dataset
  (println "\n2. Testing empty dataset:")
  (try
    (let [result (normalize/normalize-dataset [])]
      (println "✓ Empty dataset handled")
      (println "  Data count:" (count (:data result)))
      (println "  Valid?:" (get-in result [:validation :valid?])))
    (catch Exception e
      (println "✗ Failed to handle empty dataset:" (.getMessage e))))
  
  ;; Test 3: Missing required headers
  (println "\n3. Testing missing required headers:")
  (let [incomplete-dataset [{"email" "john@example.com" "first name" "John"}]]
    (try
      (let [result (normalize/normalize-dataset incomplete-dataset)]
        (println "✓ Missing headers detected")
        (println "  Valid?:" (get-in result [:validation :valid?]))
        (println "  Missing:" (get-in result [:validation :missing-headers])))
      (catch Exception e
        (println "✗ Failed to detect missing headers:" (.getMessage e)))))
  
  (println "\n=== Dataset Normalization Tests Complete ===\n"))

(defn test-header-suggestions []
  (println "\n=== Header Suggestion Tests ===\n")
  
  ;; Test header suggestions for unmapped headers
  (println "1. Testing header suggestions:")
  (let [unmapped-headers ["e-mail" "fname" "family-name" "position" "phone" "department" "access-level"]]
    (try
      (let [suggestions (normalize/suggest-header-mapping unmapped-headers)]
        (println "✓ Suggestions generated")
        (doseq [suggestion suggestions]
          (println (str "  " (:original suggestion) " -> " (:suggestions suggestion)))))
      (catch Exception e
        (println "✗ Failed to generate suggestions:" (.getMessage e)))))
  
  (println "\n=== Header Suggestion Tests Complete ===\n"))

(defn test-normalization-reporting []
  (println "\n=== Normalization Reporting Tests ===\n")
  
  ;; Test comprehensive normalization report
  (println "1. Testing normalization report:")
  (let [dataset [{"E-MAIL" "john@example.com" "FirstName" "John" "LastName" "Doe" 
                  "Title" "Engineer" "Phone" "+123456" "Team" "Engineering" "Role" "TEAM MEMBER"}]]
    (try
      (let [result (normalize/normalize-dataset dataset)
            report (normalize/create-header-report result)]
        (println "✓ Report generated successfully")
        (println "  Summary:" (:summary report))
        (println "  Valid?:" (:is-valid report))
        (println "  Mapping count:" (:mapped-count report))
        (when (seq (:suggestions report))
          (println "  Suggestions available for unmapped headers")))
      (catch Exception e
        (println "✗ Failed to generate report:" (.getMessage e)))))
  
  (println "\n=== Normalization Reporting Tests Complete ===\n"))

(defn run-all-normalization-tests []
  (println "\n=== Running All Normalization Tests ===\n")
  (test-header-normalization)
  (test-dataset-normalization)
  (test-header-suggestions)
  (test-normalization-reporting)
  (println "\n=== All Normalization Tests Complete ===\n"))

;; Run tests when file is loaded
(when (= *command-line-args* ["run"])
  (run-all-normalization-tests))

;; For REPL usage
(comment
  (run-all-normalization-tests)
  
  ;; Individual test functions
  (test-header-normalization)
  (test-dataset-normalization)
  
  ;; Interactive testing
  (normalize/normalize-headers ["EMAIL", "First Name ", "LAST_NAME"])
  (normalize/map-headers-to-standard ["email", "first name", "surname"])
  (normalize/normalize-dataset [{"email" "john@example.com" "firstname" "John" "surname" "Doe"}]))