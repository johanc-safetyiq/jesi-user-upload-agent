(ns exploration.workflow.onepassword-test
  "Test 1Password CLI integration"
  (:require [user-upload.auth.onepassword :as op]
            [clojure.pprint :as pprint]))

(defn test-op-availability
  "Test 1Password CLI availability and authentication"
  []
  (println "=== Testing 1Password CLI Availability ===\n")
  
  (let [result (op/check-op-availability)]
    (pprint/pprint result)
    (println)
    
    (if (:available result)
      (if (:authenticated result)
        (println "✅ 1Password CLI is available and authenticated")
        (println "⚠️  1Password CLI available but not authenticated"))
      (println "❌ 1Password CLI not available"))
    (println)))

(defn test-credential-retrieval
  "Test credential retrieval for test tenants"
  []
  (println "=== Testing Credential Retrieval ===\n")
  
  ;; Test with a fake tenant first (should fail)
  (let [fake-result (op/get-tenant-password "fake-tenant-that-does-not-exist")]
    (println "Test with non-existent tenant:")
    (pprint/pprint fake-result)
    (println))
  
  ;; Test with cache behavior
  (println "Testing cache behavior:")
  (let [tenant "test-tenant"
        ;; First call (should try to fetch)
        result1 (op/get-tenant-password tenant :use-cache true)
        ;; Second call (should use cache if first succeeded)
        result2 (op/get-tenant-password tenant :use-cache true)]
    
    (println "First call:")
    (pprint/pprint (dissoc result1 :password)) ; Don't print actual password
    (println)
    
    (println "Second call:")
    (pprint/pprint (dissoc result2 :password)) ; Don't print actual password
    (println)
    
    (when (and (:success result1) (:success result2))
      (println (format "Cache working: first=%s, second=%s" 
                      (:cached result1) (:cached result2)))))
  
  ;; Test full credentials
  (println "\nTesting full credentials:")
  (let [cred-result (op/get-tenant-credentials "test-tenant" "customersolutions+%s@jesi.io")]
    (pprint/pprint (if (:success cred-result)
                    (assoc cred-result :password "***hidden***")
                    cred-result))
    (println))
  
  ;; Show cache stats
  (let [stats (op/get-cache-stats)]
    (println "Cache stats:")
    (pprint/pprint stats)))

(defn test-error-handling
  "Test error handling scenarios"
  []
  (println "\n=== Testing Error Handling ===\n")
  
  ;; Test with empty tenant name
  (try
    (op/get-tenant-password "")
    (catch Exception e
      (println "Empty tenant name error (expected):" (.getMessage e))))
  
  ;; Test with nil tenant name
  (try
    (op/get-tenant-password nil)
    (catch Exception e
      (println "Nil tenant name error (expected):" (.getMessage e))))
  
  ;; Test cache clearing
  (println "\nTesting cache management:")
  (op/clear-credential-cache)
  (let [stats-after-clear (op/get-cache-stats)]
    (println "Cache stats after clear:")
    (pprint/pprint stats-after-clear)))

(defn run-test
  "Run all 1Password tests"
  []
  (test-op-availability)
  (test-credential-retrieval)
  (test-error-handling))

(comment
  ;; Run the tests
  (run-test)
  
  ;; Check just availability
  (test-op-availability)
  
  ;; Clear cache
  (op/clear-credential-cache))