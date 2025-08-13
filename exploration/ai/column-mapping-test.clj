(ns exploration.ai.column-mapping-test
  "Exploration tests for AI column mapping accuracy and edge cases.
   
   These tests validate that column mapping works correctly with various
   header formats, variations, and challenging scenarios."
  (:require [user-upload.ai.mapping :as mapping]
            [user-upload.ai.claude :as claude]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]))

(comment
  "Manual exploration of column mapping functionality.
   
   Test various header scenarios:
   
   1. Standard headers (exact matches)
   2. Common variations (case, spacing, synonyms)
   3. Tricky variations (abbreviations, extra words)
   4. Missing fields and partial matches
   5. AI vs heuristic comparison"

  ;; Test data - exact matches
  (def exact-match-cases
    [{:name "Perfect match"
      :expected ["email" "first name" "last name" "job title"]
      :headers ["email" "first name" "last name" "job title"]
      :expected-mapping {"email" "email" "first name" "first name" 
                         "last name" "last name" "job title" "job title"}
      :expected-unmapped []}
     
     {:name "Case variations"
      :expected ["email" "first name" "last name"]
      :headers ["EMAIL" "First Name" "LAST NAME"]
      :expected-mapping {"EMAIL" "email" "First Name" "first name" "LAST NAME" "last name"}
      :expected-unmapped []}])

  ;; Test data - common variations
  (def common-variation-cases
    [{:name "Email variations"
      :expected ["email"]
      :headers ["Email Address" "E-mail" "Email_Address" "User Email"]
      :expected-mapping {"Email Address" "email" "E-mail" "email" 
                         "Email_Address" "email" "User Email" "email"}
      :expected-unmapped []}
     
     {:name "Name variations"
      :expected ["first name" "last name"]
      :headers ["FirstName" "First_Name" "Surname" "Family Name"]
      :expected-mapping {"FirstName" "first name" "First_Name" "first name"
                         "Surname" "last name" "Family Name" "last name"}
      :expected-unmapped []}
     
     {:name "Job title variations"  
      :expected ["job title"]
      :headers ["Title" "Position" "Role" "Job_Title" "Designation"]
      :expected-mapping {"Title" "job title" "Position" "job title"
                         "Role" "job title" "Job_Title" "job title"
                         "Designation" "job title"}
      :expected-unmapped []}
     
     {:name "Phone variations"
      :expected ["mobile number"]
      :headers ["Phone" "Mobile" "Cell" "Mobile Phone" "Phone Number"]
      :expected-mapping {"Phone" "mobile number" "Mobile" "mobile number"
                         "Cell" "mobile number" "Mobile Phone" "mobile number"
                         "Phone Number" "mobile number"}
      :expected-unmapped []}])

  ;; Test data - tricky cases
  (def tricky-cases
    [{:name "Mixed quality headers"
      :expected ["email" "first name" "last name" "job title" "mobile number"]
      :headers ["Email_Addr" "FName" "Surname" "JobTitle" "CellPhone"]
      :note "Abbreviated and concatenated headers"}
     
     {:name "Extra words"
      :expected ["email" "teams" "user role"]
      :headers ["Primary Email Address" "Department Teams" "System Access Role"]
      :note "Headers with extra descriptive words"}
     
     {:name "Non-standard formats"
      :expected ["email" "first name" "last name"]  
      :headers ["email@domain" "given-name" "family_name_field"]
      :note "Unusual formatting"}
     
     {:name "Ambiguous headers"
      :expected ["user role" "teams"]
      :headers ["Role" "Group"]  ; Could map to multiple fields
      :note "Headers that could match multiple expected fields"}])

  ;; Test data - missing fields
  (def missing-field-cases
    [{:name "Partial data"
      :expected ["email" "first name" "last name" "job title" "mobile number"]
      :headers ["Email" "Full Name"]  ; Missing several fields
      :note "Only some expected fields present"}
     
     {:name "Extra columns"
      :expected ["email" "first name"]
      :headers ["Email" "FirstName" "Age" "Salary" "Comments"]
      :note "File has more columns than expected"}
     
     {:name "No matches"
      :expected ["email" "first name" "last name"]
      :headers ["Column1" "Column2" "Column3"]
      :note "Generic column names"}])

  ;; Run heuristic mapping tests
  (defn test-heuristic-mapping []
    (println "\n=== Heuristic Column Mapping Tests ===\n")
    
    (let [all-cases (concat exact-match-cases common-variation-cases 
                            tricky-cases missing-field-cases)]
      
      (doseq [{:keys [name expected headers expected-mapping expected-unmapped note]} all-cases]
        (println (format "Testing: %s" name))
        (when note (println (format "  Note: %s" note)))
        (println (format "  Expected fields: %s" expected))
        (println (format "  File headers: %s" headers))
        
        (let [result (mapping/heuristic-column-mapping expected headers)
              actual-mapping (:mapping result)
              actual-unmapped (:unmapped result)
              confidence (:confidence result)]
          
          (println (format "  Mapping: %s" actual-mapping))
          (println (format "  Unmapped: %s" actual-unmapped))
          (println (format "  Confidence: %s" confidence))
          
          ;; Check if result makes sense
          (let [mapped-count (count actual-mapping)
                expected-count (count expected)
                coverage (if (pos? expected-count) 
                           (* 100.0 (/ mapped-count expected-count)) 
                           0)]
            (println (format "  Coverage: %d/%d (%.1f%%)" 
                             mapped-count expected-count coverage)))
          
          (println)))))

  ;; Run AI mapping tests
  (defn test-ai-mapping []
    (println "\n=== AI Column Mapping Tests ===\n")
    
    (let [ai-availability (claude/check-claude-availability)]
      
      (if (:available ai-availability)
        (do
          (println "✅ Claude available, testing AI mapping...\n")
          
          (let [test-cases (take 5 (concat common-variation-cases tricky-cases))] ; Limited for API usage
            
            (doseq [{:keys [name expected headers note]} test-cases]
              (println (format "Testing: %s" name))
              (when note (println (format "  Note: %s" note)))
              (println (format "  Expected: %s" expected))
              (println (format "  Headers: %s" headers))
              
              (let [result (mapping/ai-column-mapping expected headers)]
                (if (:success result)
                  (do
                    (println (format "  AI Mapping: %s" (:mapping result)))
                    (println (format "  AI Unmapped: %s" (:unmapped result)))
                    
                    (let [mapped-count (count (:mapping result))
                          expected-count (count expected)
                          coverage (* 100.0 (/ mapped-count expected-count))]
                      (println (format "  Coverage: %d/%d (%.1f%%)" 
                                       mapped-count expected-count coverage))))
                  (println (format "  AI Error: %s" (:error result))))
                
                (println)))))
        
        (println "❌ Claude not available, skipping AI mapping tests"))))

  ;; Compare AI vs Heuristic mapping performance
  (defn compare-mapping-methods []
    (println "\n=== AI vs Heuristic Mapping Comparison ===\n")
    
    (let [ai-availability (claude/check-claude-availability)]
      
      (if (:available ai-availability)
        (let [test-cases (take 3 common-variation-cases) ; Limited for API usage
              results (map (fn [{:keys [name expected headers]}]
                             (let [ai-result (mapping/ai-column-mapping expected headers)
                                   heuristic-result (mapping/heuristic-column-mapping expected headers)
                                   ai-coverage (if (:success ai-result)
                                                 (/ (count (:mapping ai-result)) (count expected))
                                                 0)
                                   heuristic-coverage (/ (count (:mapping heuristic-result)) (count expected))]
                               {:name name
                                :ai-result ai-result
                                :heuristic-result heuristic-result
                                :ai-coverage ai-coverage
                                :heuristic-coverage heuristic-coverage}))
                           test-cases)]
          
          (println "Comparison Results:")
          (doseq [{:keys [name ai-result heuristic-result ai-coverage heuristic-coverage]} results]
            (println (format "\n%s:" name))
            (println (format "  AI Coverage: %.1f%% (Success: %s)" 
                             (* 100 ai-coverage) 
                             (if (:success ai-result) "Yes" "No")))
            (println (format "  Heuristic Coverage: %.1f%% (Confidence: %s)" 
                             (* 100 heuristic-coverage)
                             (:confidence heuristic-result)))
            
            (when (:success ai-result)
              (let [ai-mapping (:mapping ai-result)
                    heuristic-mapping (:mapping heuristic-result)
                    agreement (count (filter (fn [[header field]]
                                               (= field (get heuristic-mapping header)))
                                             ai-mapping))]
                (println (format "  Agreement: %d/%d mappings" 
                                 agreement (count ai-mapping))))))
          
          ;; Summary
          (let [avg-ai-coverage (/ (reduce + (map :ai-coverage results)) (count results))
                avg-heuristic-coverage (/ (reduce + (map :heuristic-coverage results)) (count results))]
            (println (format "\nOverall Average Coverage:"))
            (println (format "  AI: %.1f%%" (* 100 avg-ai-coverage)))
            (println (format "  Heuristic: %.1f%%" (* 100 avg-heuristic-coverage)))))
        
        (println "❌ Claude not available, cannot compare methods"))))

  ;; Test edge cases and error handling
  (defn test-mapping-edge-cases []
    (println "\n=== Mapping Edge Cases ===\n")
    
    ;; Empty inputs
    (println "Testing empty inputs:")
    (let [result (mapping/map-columns [] [])]
      (println (format "  Empty -> Mapping: %s, Unmapped: %s" 
                       (:mapping result) (:unmapped result))))
    
    ;; Nil inputs
    (println "Testing nil inputs:")
    (let [result (mapping/map-columns nil ["Header1"])]
      (println (format "  Nil expected -> Method: %s" (:method result))))
    
    ;; Very long headers
    (println "Testing very long headers:")
    (let [long-headers ["This_Is_A_Very_Long_Header_Name_That_Might_Contain_Email_Information"
                        "Another_Extremely_Long_Header_For_First_Name_Field"]
          result (mapping/map-columns ["email" "first name"] long-headers)]
      (println (format "  Long headers -> Mapped: %d" (count (:mapping result)))))
    
    ;; Special characters
    (println "Testing special characters:")
    (let [special-headers ["Email@Address" "First-Name" "Last_Name#Field"]
          result (mapping/map-columns ["email" "first name" "last name"] special-headers)]
      (println (format "  Special chars -> Mapped: %d" (count (:mapping result)))))
    
    ;; Duplicate headers
    (println "Testing duplicate headers:")
    (let [duplicate-headers ["Email" "Email" "Name"]
          result (mapping/map-columns ["email" "first name"] duplicate-headers)]
      (println (format "  Duplicates -> Mapping: %s" (:mapping result))))
    
    ;; Unicode characters
    (println "Testing unicode characters:")
    (let [unicode-headers ["Ëmäîl" "Nàmé" "Rôlé"]
          result (mapping/map-columns ["email" "first name" "user role"] unicode-headers)]
      (println (format "  Unicode -> Mapped: %d" (count (:mapping result))))))

  ;; Test validation functionality
  (defn test-mapping-validation []
    (println "\n=== Mapping Validation Tests ===\n")
    
    ;; Valid mapping
    (let [good-mapping {:mapping {"Email" "email" "Name" "first name"}
                        :unmapped ["last name"]}
          validation (mapping/validate-mapping good-mapping ["email"])]
      (println "Valid mapping:")
      (println (format "  Valid: %s" (:valid validation)))
      (println (format "  Issues: %s" (:issues validation))))
    
    ;; Missing required fields
    (let [bad-mapping {:mapping {"Name" "first name"}
                       :unmapped ["email" "last name"]}
          validation (mapping/validate-mapping bad-mapping ["email" "first name"])]
      (println "\nMissing required fields:")
      (println (format "  Valid: %s" (:valid validation)))
      (println (format "  Issues: %s" (map :message (:issues validation)))))
    
    ;; Duplicate mappings
    (let [dup-mapping {:mapping {"Email1" "email" "Email2" "email" "Name" "first name"}
                       :unmapped []}
          validation (mapping/validate-mapping dup-mapping [])]
      (println "\nDuplicate mappings:")
      (println (format "  Valid: %s" (:valid validation)))
      (println (format "  Issues: %s" (map :message (:issues validation))))))

  ;; Run comprehensive mapping test suite
  (defn run-column-mapping-tests []
    (println "📊 Running Column Mapping Test Suite")
    (test-heuristic-mapping)
    (test-ai-mapping)
    (compare-mapping-methods)
    (test-mapping-edge-cases) 
    (test-mapping-validation)
    (println "\n✅ Column mapping tests completed"))

  ;; Test specific challenging cases
  (defn test-challenging-mappings []
    (println "\n=== Challenging Mapping Cases ===\n")
    
    (let [challenging-cases
          [{:name "Abbreviated headers"
            :expected ["email" "first name" "last name" "job title"]
            :headers ["Mail" "FName" "LName" "Pos"]}
           
           {:name "Foreign language variations"
            :expected ["email" "first name" "last name"]  
            :headers ["Correo" "Prénom" "Nachname"]} ; Spanish, French, German
           
           {:name "Technical variations"
            :expected ["email" "user role" "teams"]
            :headers ["login_id" "access_level" "dept_code"]}
           
           {:name "Business variations"
            :expected ["first name" "last name" "job title" "mobile number"]
            :headers ["Given Name" "Family Name" "Job Function" "Contact Number"]}]]
      
      (doseq [{:keys [name expected headers]} challenging-cases]
        (println (format "Testing: %s" name))
        (println (format "  Expected: %s" expected))
        (println (format "  Headers: %s" headers))
        
        ;; Test both methods
        (let [heuristic-result (mapping/heuristic-column-mapping expected headers)
              heuristic-coverage (/ (count (:mapping heuristic-result)) (count expected))]
          
          (println (format "  Heuristic coverage: %.1f%% (Confidence: %s)" 
                           (* 100 heuristic-coverage) (:confidence heuristic-result)))
          (println (format "  Heuristic mapping: %s" (:mapping heuristic-result)))
          
          ;; Only test AI if available
          (when (:available (claude/check-claude-availability))
            (let [ai-result (mapping/ai-column-mapping expected headers)]
              (if (:success ai-result)
                (let [ai-coverage (/ (count (:mapping ai-result)) (count expected))]
                  (println (format "  AI coverage: %.1f%%" (* 100 ai-coverage)))
                  (println (format "  AI mapping: %s" (:mapping ai-result))))
                (println (format "  AI failed: %s" (:error ai-result))))))
          
          (println)))))