(ns exploration.workflow.approval-test
  "Test approval workflow with fingerprinting"
  (:require [user-upload.workflow.approval :as approval]
            [clojure.pprint :as pprint]
            [cheshire.core :as json]))

(defn test-fingerprinting
  "Test attachment fingerprinting"
  []
  (println "=== Testing Attachment Fingerprinting ===\n")
  
  ;; Create test attachments
  (let [attachment1 {:filename "users.csv"
                     :content (.getBytes "email,name\njohn@example.com,John Doe" "UTF-8")}
        attachment2 {:filename "teams.xlsx"
                     :content (.getBytes "different content here" "UTF-8")}
        
        fingerprint1 (approval/calculate-attachment-fingerprint attachment1)
        fingerprint2 (approval/calculate-attachment-fingerprint attachment2)]
    
    (println "Attachment 1 fingerprint:")
    (pprint/pprint fingerprint1)
    (println)
    
    (println "Attachment 2 fingerprint:")
    (pprint/pprint fingerprint2)
    (println)
    
    ;; Test that same content produces same fingerprint
    (let [attachment1-copy {:filename "users-copy.csv"
                           :content (.getBytes "email,name\njohn@example.com,John Doe" "UTF-8")}
          fingerprint1-copy (approval/calculate-attachment-fingerprint attachment1-copy)]
      
      (println "Same content, different filename:")
      (pprint/pprint fingerprint1-copy)
      (println)
      
      (if (= (:fingerprint fingerprint1) (:fingerprint fingerprint1-copy))
        (println "✅ Same content produces same fingerprint")
        (println "❌ Same content produced different fingerprints")))
    
    [fingerprint1 fingerprint2]))

(defn test-approval-request-generation
  "Test approval request data generation and formatting"
  []
  (println "\n=== Testing Approval Request Generation ===\n")
  
  (let [;; Mock validated user data
        valid-data [{:email "john.doe@example.com"
                     :first-name "John"
                     :last-name "Doe"
                     :user-role "TEAM MEMBER"
                     :teams ["Engineering" "DevOps"]}
                    {:email "jane.smith@example.com"
                     :first-name "Jane"
                     :last-name "Smith"
                     :user-role "MANAGER"
                     :teams ["Engineering"]}]
        
        ;; Mock attachments with fingerprints
        attachments [{:filename "users.csv"
                     :fingerprint "abc123def456"
                     :size 1024}
                    {:filename "additional-info.xlsx"
                     :fingerprint "xyz789uvw012"
                     :size 2048}]
        
        ;; Generate approval request data
        request-data (approval/generate-approval-request-data 
                     "TEST-123" "acme" valid-data attachments)]
    
    (println "Generated approval request data:")
    (pprint/pprint request-data)
    (println)
    
    ;; Test comment formatting
    (let [comment-body (approval/format-approval-request-comment request-data)]
      (println "Formatted comment body:")
      (println comment-body)
      (println)
      
      ;; Test data extraction from comment
      (let [extracted-data (approval/extract-approval-request-data comment-body)]
        (println "Extracted data from comment:")
        (pprint/pprint extracted-data)
        (println)
        
        (if (= request-data extracted-data)
          (println "✅ Round-trip data preservation successful")
          (println "❌ Round-trip data preservation failed"))))))

(defn test-fingerprint-validation
  "Test fingerprint validation logic"
  []
  (println "\n=== Testing Fingerprint Validation ===\n")
  
  (let [;; Original attachments from approval request
        original-request-data {:attachments [{:filename "users.csv"
                                             :fingerprint "abc123"
                                             :size 1024}
                                            {:filename "teams.xlsx"
                                             :fingerprint "def456"
                                             :size 2048}]}
        
        ;; Test case 1: No changes
        current-attachments1 [{:filename "users.csv"
                              :fingerprint "abc123"
                              :size 1024}
                             {:filename "teams.xlsx"
                              :fingerprint "def456"
                              :size 2048}]
        
        validation1 (approval/validate-attachment-fingerprints 
                    current-attachments1 original-request-data)]
    
    (println "Test 1 - No changes:")
    (pprint/pprint validation1)
    (println)
    
    ;; Test case 2: File modified
    (let [current-attachments2 [{:filename "users.csv"
                                :fingerprint "xyz789" ; Changed!
                                :size 1024}
                               {:filename "teams.xlsx"
                                :fingerprint "def456"
                                :size 2048}]
          
          validation2 (approval/validate-attachment-fingerprints 
                      current-attachments2 original-request-data)]
      
      (println "Test 2 - File modified:")
      (pprint/pprint validation2)
      (println))
    
    ;; Test case 3: File added
    (let [current-attachments3 [{:filename "users.csv"
                                :fingerprint "abc123"
                                :size 1024}
                               {:filename "teams.xlsx"
                                :fingerprint "def456"
                                :size 2048}
                               {:filename "new-file.pdf"
                                :fingerprint "new123"
                                :size 512}]
          
          validation3 (approval/validate-attachment-fingerprints 
                      current-attachments3 original-request-data)]
      
      (println "Test 3 - File added:")
      (pprint/pprint validation3)
      (println))
    
    ;; Test case 4: File removed
    (let [current-attachments4 [{:filename "users.csv"
                                :fingerprint "abc123"
                                :size 1024}]
          
          validation4 (approval/validate-attachment-fingerprints 
                      current-attachments4 original-request-data)]
      
      (println "Test 4 - File removed:")
      (pprint/pprint validation4)
      (println))))

(defn test-business-rules
  "Test business rule logic"
  []
  (println "\n=== Testing Business Rules ===\n")
  
  ;; Test approval requirement logic
  (let [tenant "acme"
        small-upload [{:email "john@example.com"}]
        large-upload (repeat 50 {:email "user@example.com"})]
    
    (println "Small upload approval required:" 
             (approval/workflow-approval-required? tenant small-upload))
    (println "Large upload approval required:" 
             (approval/workflow-approval-required? tenant large-upload))
    (println)))

(defn run-test
  "Run all approval workflow tests"
  []
  (test-fingerprinting)
  (test-approval-request-generation)
  (test-fingerprint-validation)
  (test-business-rules)
  
  (println "=== Approval Workflow Tests Complete ==="))

(comment
  ;; Run all tests
  (run-test)
  
  ;; Run individual tests
  (test-fingerprinting)
  (test-approval-request-generation)
  (test-fingerprint-validation))