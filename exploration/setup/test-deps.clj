(ns exploration.setup.test-deps
  "Test script to validate deps.edn setup and basic functionality."
  (:require [user-upload.core :as core]
            [clojure.tools.logging :as log]))

(defn test-basic-functionality
  "Test that basic project setup works correctly."
  []
  (println "=== Testing Basic Project Setup ===")
  
  (println "1. Testing namespace loading...")
  (try
    (require 'user-upload.core :reload)
    (println "✓ Successfully loaded user-upload.core")
    (catch Exception e
      (println "✗ Failed to load user-upload.core:" (.getMessage e))
      (throw e)))
  
  (println "2. Testing hello-world function...")
  (try
    (let [result (core/hello-world)]
      (if (= result "Hello, World!")
        (println "✓ hello-world function works correctly")
        (println "✗ hello-world function returned unexpected result:" result)))
    (catch Exception e
      (println "✗ hello-world function failed:" (.getMessage e))
      (throw e)))
  
  (println "3. Testing logging...")
  (try
    (log/info "Test log message from exploration script")
    (println "✓ Logging works")
    (catch Exception e
      (println "✗ Logging failed:" (.getMessage e))
      (throw e)))
  
  (println "\n=== All Tests Passed! ==="))

;; Run the test when this file is evaluated
(when *command-line-args*
  (test-basic-functionality))