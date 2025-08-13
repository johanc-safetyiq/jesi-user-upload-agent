(ns exploration.ai.timeout-test
  "Exploration tests for timeout handling in Claude CLI integration.
   
   These tests validate that timeout mechanisms work correctly for
   various scenarios including long-running prompts and unresponsive processes."
  (:require [user-upload.ai.claude :as claude]
            [babashka.process :as process]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]))

(comment
  "Manual exploration of timeout handling.
   
   Test various timeout scenarios:
   
   1. Normal responses within timeout
   2. Responses that exceed timeout
   3. Process hanging scenarios
   4. Different timeout values
   5. Timeout error handling and recovery"

  ;; Test normal responses within timeout
  (defn test-normal-response-timeout []
    (println "\n=== Normal Response Timeout Tests ===\n")
    
    ;; Fast response with generous timeout
    (let [result (claude/invoke-claude-with-timeout
                  "What is 1+1?"
                  "Return ONLY JSON: {\"answer\": <number>} with no prose."
                  :timeout-ms 30000)] ; 30 second timeout
      (println "Fast response result:")
      (println "Success:" (:success result))
      (println "Timeout:" (:timeout result))
      (when (:success result)
        (println "Response time was within 30s limit")))
    
    ;; Fast response with tight timeout
    (let [result (claude/invoke-claude-with-timeout
                  "What is 2+2?"
                  "Return ONLY JSON: {\"answer\": <number>} with no prose."
                  :timeout-ms 5000)] ; 5 second timeout
      (println "\nTight timeout result:")
      (println "Success:" (:success result))
      (println "Timeout:" (:timeout result))
      (if (:timeout result)
        (println "⚠️ Response exceeded 5s limit")
        (println "✅ Response completed within 5s"))))

  ;; Test timeout scenarios
  (defn test-timeout-scenarios []
    (println "\n=== Timeout Scenario Tests ===\n")
    
    ;; Very short timeout (should timeout)
    (let [result (claude/invoke-claude-with-timeout
                  "Please provide a detailed analysis of machine learning algorithms"
                  "Take your time to provide a comprehensive response"
                  :timeout-ms 1000)] ; 1 second timeout
      (println "Very short timeout (1s) result:")
      (println "Success:" (:success result))
      (println "Timeout:" (:timeout result))
      (if (:timeout result)
        (println "✅ Correctly timed out after 1s")
        (println "⚠️ Unexpectedly completed within 1s")))
    
    ;; Medium timeout
    (let [result (claude/invoke-claude-with-timeout
                  "Count from 1 to 5"
                  "Return ONLY JSON: {\"count\": [1,2,3,4,5]} with no prose."
                  :timeout-ms 10000)] ; 10 second timeout
      (println "\nMedium timeout (10s) result:")
      (println "Success:" (:success result))
      (println "Timeout:" (:timeout result))))

  ;; Test different timeout values
  (defn test-timeout-values []
    (println "\n=== Timeout Value Tests ===\n")
    
    (let [timeout-values [2000 5000 10000 15000] ; 2s, 5s, 10s, 15s
          prompt "What is the capital of France?"
          system-prompt "Return ONLY JSON: {\"capital\": \"<city>\"} with no prose."]
      
      (doseq [timeout-ms timeout-values]
        (println (format "\nTesting %ds timeout:" (/ timeout-ms 1000)))
        (let [start-time (System/currentTimeMillis)
              result (claude/invoke-claude-with-timeout prompt system-prompt
                                                        :timeout-ms timeout-ms)
              actual-time (- (System/currentTimeMillis) start-time)]
          (println "Success:" (:success result))
          (println "Timeout:" (:timeout result))
          (println "Actual time:" actual-time "ms")
          
          (when (:timeout result)
            (println (format "✅ Correctly timed out around %dms" timeout-ms)))))))

  ;; Test timeout error handling
  (defn test-timeout-error-handling []
    (println "\n=== Timeout Error Handling Tests ===\n")
    
    ;; Test timeout with fallback
    (defn call-with-fallback [prompt timeout-ms]
      (let [result (claude/invoke-claude-with-timeout prompt 
                                                      "Return JSON response"
                                                      :timeout-ms timeout-ms)]
        (if (:success result)
          (:result result)
          (do
            (println "AI call failed, using fallback")
            {:fallback true :error (:error result)}))))
    
    (let [result (call-with-fallback "Complex analysis task" 1000)]
      (println "Fallback test result:" result))
    
    ;; Test timeout recovery
    (println "\nTesting timeout recovery...")
    (let [first-attempt (claude/invoke-claude-with-timeout
                         "Long task" "Be comprehensive" :timeout-ms 1000)
          second-attempt (claude/invoke-claude-with-timeout
                          "Quick task" "Be brief" :timeout-ms 5000)]
      (println "First attempt (should timeout):" (:timeout first-attempt))
      (println "Second attempt (should succeed):" (:success second-attempt))))

  ;; Test process timeout vs Claude processing time
  (defn test-process-vs-claude-timeout []
    (println "\n=== Process vs Claude Timeout Tests ===\n")
    
    ;; Test with a prompt that should be quick for Claude but we set a very short timeout
    (let [result (claude/invoke-claude-with-timeout
                  "Hi"
                  "Say hi back in JSON: {\"greeting\": \"hello\"}"
                  :timeout-ms 500)] ; Very short timeout
      (println "Quick prompt, short timeout:")
      (println "Success:" (:success result))
      (println "Timeout:" (:timeout result))
      
      (if (:timeout result)
        (println "Process timeout occurred (network/startup delay)")
        (println "Claude responded quickly")))
    
    ;; Test with a prompt that should take time for Claude to process
    (let [result (claude/invoke-claude-with-timeout
                  "Write a detailed essay about the history of computing"
                  "Provide comprehensive coverage"
                  :timeout-ms 3000)] ; 3 second timeout
      (println "\nComplex prompt, medium timeout:")
      (println "Success:" (:success result))
      (println "Timeout:" (:timeout result))))

  ;; Test timeout handling with invalid commands
  (defn test-timeout-with-errors []
    (println "\n=== Timeout with Error Tests ===\n")
    
    ;; Command that will fail quickly
    (let [result (claude/invoke-claude-with-timeout
                  "test"
                  "test"
                  :timeout-ms 30000
                  :allowed-tools "InvalidTool")] ; Invalid tool should cause quick failure
      (println "Invalid command result:")
      (println "Success:" (:success result))
      (println "Timeout:" (:timeout result))
      (println "Error:" (:error result)))
    
    ;; Test with non-existent CLI command (should fail fast)
    (try
      (let [result (process/shell {:timeout 5000 :out :string :err :string}
                                  ["non-existent-claude-command" "--version"])]
        (println "Non-existent command succeeded unexpectedly"))
      (catch Exception e
        (println "✅ Non-existent command failed as expected:" (.getMessage e)))))

  ;; Comprehensive timeout test suite
  (defn run-timeout-test-suite []
    (println "⏱️  Running Timeout Handling Test Suite")
    
    ;; Check if Claude is available first
    (let [availability (claude/check-claude-availability)]
      (if (:available availability)
        (do
          (println "✅ Claude CLI available, running timeout tests...\n")
          (test-normal-response-timeout)
          (test-timeout-scenarios)
          (test-timeout-values)
          (test-timeout-error-handling)
          (test-process-vs-claude-timeout)
          (test-timeout-with-errors)
          (println "\n✅ All timeout tests completed"))
        (do
          (println "❌ Claude CLI not available, skipping timeout tests")
          (println "Error:" (:error availability)))))
    
    ;; Test timeout utilities without Claude
    (println "\n--- Testing timeout utilities ---")
    
    ;; Test timeout exception handling
    (try
      (process/shell {:timeout 100} ["sleep" "1"]) ; Sleep for 1s with 100ms timeout
      (catch java.util.concurrent.TimeoutException e
        (println "✅ Timeout exception caught correctly"))
      (catch Exception e
        (println "Unexpected exception:" (.getMessage e)))))

  ;; Performance monitoring for timeout tuning
  (defn monitor-response-times []
    (println "\n=== Response Time Monitoring ===\n")
    
    (let [test-prompts [
                        "What is 1+1?"
                        "List 3 colors"
                        "Explain quantum computing in one sentence"
                        "What is the weather like?"]
          system-prompt "Return JSON response"]
      
      (doseq [prompt test-prompts]
        (println (format "\nTesting prompt: \"%s\"" prompt))
        (let [start-time (System/currentTimeMillis)
              result (claude/invoke-claude-with-timeout prompt system-prompt
                                                        :timeout-ms 15000)
              end-time (System/currentTimeMillis)
              duration (- end-time start-time)]
          (println (format "Duration: %dms" duration))
          (println "Success:" (:success result))
          (when (:timeout result)
            (println "⚠️ Timed out")))))))