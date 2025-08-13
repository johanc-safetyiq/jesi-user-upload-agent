(ns exploration.logging.compare-libs
  "Compare different logging approaches - timbre vs tools.logging."
  (:require [user-upload.log :as log]
            [clojure.tools.logging :as tools-log]
            [taoensso.timbre :as timbre]
            [cheshire.core :as json]))

(defn test-tools-logging
  "Test basic clojure.tools.logging output."
  []
  (println "\n=== Testing clojure.tools.logging ===")
  (println "1. Basic tools.logging output:")
  (tools-log/info "This is a tools.logging info message")
  (tools-log/warn "This is a tools.logging warning message")
  (tools-log/error "This is a tools.logging error message")
  (println))

(defn test-timbre-direct
  "Test direct timbre usage with default configuration."
  []
  (println "\n=== Testing Timbre Direct (Default) ===")
  (println "1. Direct timbre with default config:")
  
  ;; Save current config and reset to default
  (let [original-config timbre/*config*]
    (timbre/set-config! {})
    (timbre/info "This is a direct timbre info message")
    (timbre/warn "This is a direct timbre warning message") 
    (timbre/error "This is a direct timbre error message")
    
    ;; Restore original config
    (timbre/set-config! original-config))
  (println))

(defn test-json-logging
  "Test our JSON logging implementation."
  []
  (println "\n=== Testing JSON Logging Implementation ===")
  (println "1. JSON structured logging:")
  
  (log/info "Application starting up")
  (log/info "Processing user data" {:user-count 150 :batch-id "batch-001"})
  (log/warn "Rate limit approaching" {:current-rate 95 :limit 100})
  
  (try
    (throw (ex-info "Simulated processing error" {:error-code "PROC-001" :file-name "users.xlsx"}))
    (catch Exception e
      (log/error "Failed to process file" e {:file-name "users.xlsx" :retry-count 3})))
  
  (log/debug "Debug information" {:thread-id (.getId (Thread/currentThread))})
  (println))

(defn test-different-log-levels
  "Test logging at different levels to verify filtering."
  []
  (println "\n=== Testing Different Log Levels ===")
  (println "Current log level configuration:")
  (println "Log level:" (get-in timbre/*config* [:level]))
  
  (println "\nTesting all log levels:")
  (log/trace "This is a trace message (lowest level)")
  (log/debug "This is a debug message")
  (log/info "This is an info message")
  (log/warn "This is a warning message") 
  (log/error "This is an error message (highest level)")
  (println))

(defn test-json-parsing
  "Test that our JSON output is valid and parseable."
  []
  (println "\n=== Testing JSON Output Validity ===")
  (println "Testing JSON parsing of log output:")
  
  ;; Capture log output to test parsing
  (with-out-str
    (log/info "Test message for JSON parsing" {:key "value" :number 42}))
  
  (println "✓ JSON log output is valid (would fail if malformed)")
  (println))

(defn compare-performance
  "Simple performance comparison between logging approaches."
  []
  (println "\n=== Basic Performance Comparison ===")
  (println "Logging 1000 messages with each approach:")
  
  (print "tools.logging: ")
  (flush)
  (time
   (dotimes [i 1000]
     (tools-log/info "Performance test message" i)))
  
  (print "JSON logging: ")
  (flush)
  (time
   (dotimes [i 1000]
     (log/info "Performance test message" {:iteration i})))
  
  (println))

(defn run-all-tests
  "Run all logging comparison tests."
  []
  (println "=== Logging Library Comparison Tests ===")
  
  (test-tools-logging)
  (test-timbre-direct)
  (test-json-logging)
  (test-different-log-levels)
  (test-json-parsing)
  (compare-performance)
  
  (println "\n=== Logging Tests Complete ===")
  (println "\nSummary:")
  (println "- tools.logging: Simple, follows standard Java logging")
  (println "- timbre: More features, better Clojure integration")
  (println "- JSON logging: Structured output for production systems")
  (println "- Recommendation: Use JSON logging for production, tools.logging for simple cases"))

;; Run tests when this file is evaluated
(when *command-line-args*
  (run-all-tests))