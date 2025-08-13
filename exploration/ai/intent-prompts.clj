(ns exploration.ai.intent-prompts
  "Exploration tests for AI intent detection prompts and accuracy.
   
   These tests validate that intent detection prompts work correctly
   with various ticket scenarios and edge cases."
  (:require [user-upload.ai.intent :as intent]
            [user-upload.ai.claude :as claude]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]))

(comment
  "Manual exploration of intent detection prompts.
   
   Test various ticket scenarios:
   
   1. Clear positive cases (obvious user upload requests)
   2. Clear negative cases (definitely not user uploads)  
   3. Edge cases and ambiguous tickets
   4. Different attachment types and combinations
   5. Prompt effectiveness and accuracy"

  ;; Test data - positive cases
  (def positive-test-cases
    [{:ticket {:key "HR-001" 
               :summary "Upload new employees"
               :description "Please add these new team members to our system"}
      :attachments ["new-employees.xlsx"]
      :expected true
      :note "Clear user upload with Excel file"}
     
     {:ticket {:key "IT-002"
               :summary "Bulk user import needed"
               :description "Need to import 50 new users from the attached CSV file"}
      :attachments ["users.csv"]
      :expected true
      :note "Bulk import with CSV"}
     
     {:ticket {:key "ADM-003"
               :summary "Add new team members"
               :description "Onboarding new staff, please process the attached list"}
      :attachments ["staff-list.xlsx"]
      :expected true
      :note "Onboarding scenario"}
     
     {:ticket {:key "HR-004"
               :summary "User directory update"
               :description "Update our user directory with new personnel data"}
      :attachments ["personnel.csv"]
      :expected true
      :note "Directory update"}
     
     {:ticket {:key "ORG-005"
               :summary "New department setup"
               :description "Setting up new department with initial user roster"}
      :attachments ["dept-users.xlsx" "org-chart.pdf"]
      :expected true
      :note "Department setup with user data"}])

  ;; Test data - negative cases
  (def negative-test-cases
    [{:ticket {:key "BUG-101"
               :summary "Login page not working"
               :description "Users cannot access the login page, getting 500 error"}
      :attachments []
      :expected false
      :note "Bug report, no uploads"}
     
     {:ticket {:key "FEAT-102"
               :summary "Improve dashboard performance"
               :description "Dashboard is loading slowly, need optimization"}
      :attachments ["performance-report.pdf"]
      :expected false
      :note "Performance improvement"}
     
     {:ticket {:key "CONF-103"
               :summary "Configure new server"
               :description "Need to set up new application server"}
      :attachments ["server-config.yml"]
      :expected false
      :note "Configuration task"}
     
     {:ticket {:key "DOC-104"
               :summary "Update user manual"
               :description "User documentation needs to be updated with latest features"}
      :attachments ["user-manual.docx"]
      :expected false
      :note "Documentation update"}
     
     {:ticket {:key "SEC-105"
               :summary "Security audit report"
               :description "Review security findings and implement fixes"}
      :attachments ["audit-report.pdf" "vulnerability-scan.csv"]
      :expected false
      :note "Security audit with CSV (not user data)"}])

  ;; Test data - edge cases
  (def edge-test-cases
    [{:ticket {:key "EDGE-201"
               :summary "User access issues"
               :description "Some users can't log in, check their accounts"}
      :attachments []
      :expected false
      :note "User-related but not upload"}
     
     {:ticket {:key "EDGE-202"
               :summary "Export user data"
               :description "Need to export current user list for audit"}
      :attachments []
      :expected false
      :note "Export vs import"}
     
     {:ticket {:key "EDGE-203"
               :summary "Help needed"
               :description ""}
      :attachments ["data.xlsx"]
      :expected false  ; Ambiguous, could go either way
      :note "Ambiguous with Excel file"}
     
     {:ticket {:key "EDGE-204"
               :summary "Process this file"
               :description "Please process the attached file"}
      :attachments ["users.txt"]
      :expected true   ; Likely user data despite .txt
      :note "Text file with 'users' name"}
     
     {:ticket {:key "EDGE-205"
               :summary "Update system with new data"
               :description "System needs updating"}
      :attachments ["employees.json"]
      :expected true
      :note "JSON user data"}])

  ;; Run tests with AI detection
  (defn test-ai-intent-detection []
    (println "\n=== AI Intent Detection Tests ===\n")
    
    (let [all-cases (concat positive-test-cases negative-test-cases edge-test-cases)
          ai-availability (claude/check-claude-availability)]
      
      (if (:available ai-availability)
        (do
          (println "✅ Claude available, testing AI detection...\n")
          
          (doseq [{:keys [ticket attachments expected note]} all-cases]
            (println (format "Testing %s: %s" (:key ticket) (:summary ticket)))
            (println (format "  Attachments: %s" (str/join ", " attachments)))
            (println (format "  Expected: %s (%s)" expected note))
            
            (let [result (intent/ai-intent-detection ticket attachments)
                  actual (if (:success result) (:is-user-upload result) nil)
                  correct? (= actual expected)]
              
              (println (format "  AI Result: %s (Success: %s)" actual (:success result)))
              (println (format "  %s %s" (if correct? "✅" "❌") (if correct? "Correct" "Incorrect")))
              
              (when-not (:success result)
                (println (format "  Error: %s" (:error result))))
              
              (println))))
        
        (println "❌ Claude not available, skipping AI tests"))))

  ;; Run tests with heuristic detection
  (defn test-heuristic-intent-detection []
    (println "\n=== Heuristic Intent Detection Tests ===\n")
    
    (let [all-cases (concat positive-test-cases negative-test-cases edge-test-cases)]
      
      (doseq [{:keys [ticket attachments expected note]} all-cases]
        (println (format "Testing %s: %s" (:key ticket) (:summary ticket)))
        (println (format "  Attachments: %s" (str/join ", " attachments)))
        (println (format "  Expected: %s (%s)" expected note))
        
        (let [result (intent/heuristic-intent-check ticket attachments)
              actual (:is-user-upload result)
              correct? (= actual expected)]
          
          (println (format "  Heuristic Result: %s (Confidence: %s)" 
                           actual (:confidence result)))
          (println (format "  Reasons: %s" (:reasons result)))
          (println (format "  %s %s" (if correct? "✅" "❌") (if correct? "Correct" "Incorrect")))
          
          (println)))))

  ;; Compare AI vs Heuristic performance
  (defn compare-detection-methods []
    (println "\n=== AI vs Heuristic Comparison ===\n")
    
    (let [all-cases (concat positive-test-cases negative-test-cases)
          ai-availability (claude/check-claude-availability)]
      
      (if (:available ai-availability)
        (let [results (map (fn [{:keys [ticket attachments expected]}]
                             (let [ai-result (intent/ai-intent-detection ticket attachments)
                                   heuristic-result (intent/heuristic-intent-check ticket attachments)
                                   ai-correct (= expected (and (:success ai-result) (:is-user-upload ai-result)))
                                   heuristic-correct (= expected (:is-user-upload heuristic-result))]
                               {:ticket-key (:key ticket)
                                :expected expected
                                :ai-result ai-result
                                :heuristic-result heuristic-result
                                :ai-correct ai-correct
                                :heuristic-correct heuristic-correct}))
                           all-cases)]
          
          (let [total (count results)
                ai-successes (count (filter :ai-correct results))
                heuristic-successes (count (filter :heuristic-correct results))
                both-correct (count (filter #(and (:ai-correct %) (:heuristic-correct %)) results))
                ai-only-correct (count (filter #(and (:ai-correct %) (not (:heuristic-correct %))) results))
                heuristic-only-correct (count (filter #(and (:heuristic-correct %) (not (:ai-correct %))) results))]
            
            (println (format "Total test cases: %d" total))
            (println (format "AI accuracy: %d/%d (%.1f%%)" 
                             ai-successes total (* 100.0 (/ ai-successes total))))
            (println (format "Heuristic accuracy: %d/%d (%.1f%%)" 
                             heuristic-successes total (* 100.0 (/ heuristic-successes total))))
            (println (format "Both correct: %d" both-correct))
            (println (format "AI only correct: %d" ai-only-correct))
            (println (format "Heuristic only correct: %d" heuristic-only-correct))
            
            (println "\nDetailed comparison:")
            (doseq [{:keys [ticket-key expected ai-result heuristic-result ai-correct heuristic-correct]} results]
              (when-not (and ai-correct heuristic-correct)
                (println (format "%s - Expected: %s, AI: %s, Heuristic: %s" 
                                 ticket-key expected 
                                 (if (:success ai-result) (:is-user-upload ai-result) "FAILED")
                                 (:is-user-upload heuristic-result)))))))
        
        (println "❌ Claude not available, cannot compare AI vs heuristic"))))

  ;; Test prompt variations
  (defn test-prompt-variations []
    (println "\n=== Prompt Variation Tests ===\n")
    
    ;; Test with minimal information
    (let [minimal-ticket {:key "MIN-001" :summary "Upload" :description ""}
          result (intent/detect-user-upload-intent minimal-ticket ["file.xlsx"])]
      (println "Minimal info test:")
      (println "Result:" (:is-user-upload result))
      (println "Method:" (:method result)))
    
    ;; Test with verbose information
    (let [verbose-ticket {:key "VERB-001" 
                          :summary "Comprehensive user data management and import process"
                          :description "We need to perform a comprehensive import of new employee data into our system. The attached spreadsheet contains detailed information about 45 new team members including their contact information, job roles, department assignments, and access levels. Please process this data and create user accounts for all individuals listed in the file."}
          result (intent/detect-user-upload-intent verbose-ticket ["detailed-employee-data.xlsx"])]
      (println "\nVerbose info test:")
      (println "Result:" (:is-user-upload result))
      (println "Method:" (:method result))))

  ;; Run comprehensive test suite
  (defn run-intent-detection-tests []
    (println "🎯 Running Intent Detection Test Suite")
    (test-heuristic-intent-detection)
    (test-ai-intent-detection)
    (compare-detection-methods)
    (test-prompt-variations)
    (println "\n✅ Intent detection tests completed")))