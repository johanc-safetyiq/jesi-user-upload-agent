(ns exploration.workflow.tenant-extraction-test
  "Test tenant extraction from Jira issues"
  (:require [user-upload.auth.tenant :as tenant]
            [clojure.pprint :as pprint]))

(defn test-tenant-extraction
  "Test tenant extraction with various issue formats"
  []
  (println "=== Testing Tenant Extraction ===\n")
  
  ;; Test case 1: Standard customersolutions email in description
  (let [issue1 {:key "TEST-123"
                :fields {:description "Please upload users for customersolutions+acme@jesi.io"
                        :summary "User upload request"}}
        result1 (tenant/extract-and-validate-tenant issue1)]
    (println "Test 1 - Standard customersolutions email:")
    (pprint/pprint result1)
    (println))
  
  ;; Test case 2: Email in summary/title
  (let [issue2 {:key "TEST-124"
                :fields {:description "Please process this request"
                        :summary "Upload users for customersolutions+big-corp@jesi.io"}}
        result2 (tenant/extract-and-validate-tenant issue2)]
    (println "Test 2 - Email in summary:")
    (pprint/pprint result2)
    (println))
  
  ;; Test case 3: Service account format
  (let [issue3 {:key "TEST-125"
                :fields {:description "Use acme.service@jesi.io credentials"
                        :summary "User import request"}}
        result3 (tenant/extract-and-validate-tenant issue3)]
    (println "Test 3 - Service account format:")
    (pprint/pprint result3)
    (println))
  
  ;; Test case 4: No tenant found
  (let [issue4 {:key "TEST-126"
                :fields {:description "Regular issue with no tenant info"
                        :summary "Some other request"}}
        result4 (tenant/extract-and-validate-tenant issue4)]
    (println "Test 4 - No tenant found:")
    (pprint/pprint result4)
    (println))
  
  ;; Test case 5: Multiple emails (should pick customersolutions)
  (let [issue5 {:key "TEST-127"
                :fields {:description "Contact admin@example.com or use customersolutions+test@jesi.io"
                        :summary "Mixed email formats"}}
        result5 (tenant/extract-and-validate-tenant issue5)]
    (println "Test 5 - Multiple emails:")
    (pprint/pprint result5)
    (println))
  
  ;; Test validation edge cases
  (println "=== Testing Tenant Validation ===\n")
  
  (doseq [[input expected] [["acme" :valid]
                           ["big-corp" :valid]
                           ["test_123" :valid]
                           ["A" :invalid] ; too short
                           ["" :invalid] ; empty
                           ["has spaces" :invalid] ; invalid chars
                           ["very-long-tenant-name-that-exceeds-fifty-characters-limit" :invalid]]]
    (let [result (tenant/validate-tenant-name input)]
      (println (format "Validate '%s': %s (expected %s)" 
                      input 
                      (if (:valid result) :valid :invalid)
                      expected)))))

(defn run-test
  "Run the tenant extraction tests"
  []
  (test-tenant-extraction))

(comment
  ;; Run the tests
  (run-test))