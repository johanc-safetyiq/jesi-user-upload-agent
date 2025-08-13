(ns exploration.ai.claude-cli-test
  "Exploration tests for Claude Code CLI invocation.
   
   These tests validate that the Claude CLI integration works correctly
   with process spawning, timeout handling, and error recovery."
  (:require [user-upload.ai.claude :as claude]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]))

(comment
  "Manual exploration of Claude CLI integration.
   
   Run these tests interactively to verify Claude integration works:
   
   1. Check if Claude CLI is available on system
   2. Test basic CLI invocation with simple prompts
   3. Verify JSON parsing works correctly
   4. Test timeout handling with long-running prompts"

  ;; Test 1: Check Claude availability
  (testing "Claude CLI availability"
    (let [result (claude/check-claude-availability)]
      (println "Claude availability check:" result)
      (if (:available result)
        (println "✅ Claude CLI is available, version:" (:version result))
        (println "❌ Claude CLI not available:" (:error result)))))

  ;; Test 2: Simple invocation test
  (testing "Basic Claude invocation"
    (let [simple-prompt "What is 2+2?"
          system-prompt "Return ONLY JSON: {\"answer\": <number>} with no prose."
          result (claude/invoke-claude-with-timeout simple-prompt system-prompt
                                                    :timeout-ms 10000)]
      (println "Simple invocation result:" result)
      (println "Success:" (:success result))
      (when (:success result)
        (println "Answer:" (get-in result [:result :answer])))))

  ;; Test 3: Intent detection with mock ticket
  (testing "Intent detection test"
    (let [mock-ticket {:key "TEST-123"
                       :summary "Upload new team members"
                       :description "Please process the attached spreadsheet with new users"}
          mock-attachments ["team-members.xlsx"]
          result (claude/invoke-intent-detection mock-ticket mock-attachments)]
      (println "Intent detection result:" result)
      (when (:success result)
        (println "Is user upload request:" (:is-user-upload result)))))

  ;; Test 4: Column mapping test
  (testing "Column mapping test"
    (let [expected-fields ["email" "first name" "last name" "job title"]
          file-headers ["Email Address" "FirstName" "LastName" "Position"]
          result (claude/invoke-column-mapping expected-fields file-headers)]
      (println "Column mapping result:" result)
      (when (:success result)
        (println "Mapping:" (:mapping result))
        (println "Unmapped fields:" (:unmapped result)))))

  ;; Test 5: Error handling - invalid command
  (testing "Error handling with invalid prompt"
    (let [result (claude/invoke-claude-with-timeout "" "" :timeout-ms 5000)]
      (println "Error handling test:" result)
      (println "Expected failure - Success:" (:success result))))

  ;; Test 6: Timeout handling
  (testing "Timeout handling"
    (let [long-prompt "Please think for 60 seconds before responding"
          system-prompt "Take your time to think"
          result (claude/invoke-claude-with-timeout long-prompt system-prompt
                                                    :timeout-ms 2000)] ; 2 second timeout
      (println "Timeout test result:" result)
      (println "Timeout occurred:" (:timeout result))))

  ;; Helper: Run all tests
  (defn run-all-claude-tests []
    (println "\n=== Claude CLI Integration Tests ===\n")
    
    ;; Check availability first
    (let [availability (claude/check-claude-availability)]
      (if (:available availability)
        (do
          (println "✅ Claude CLI available, running tests...\n")
          
          ;; Run each test
          (println "Test 1: Basic invocation")
          (let [result (claude/invoke-claude-with-timeout
                        "What is 2+2?" 
                        "Return ONLY JSON: {\"answer\": <number>} with no prose.")]
            (println "Result:" (:success result))
            (when (:success result)
              (println "Answer:" (get-in result [:result :answer]))))
          
          (println "\nTest 2: Intent detection")
          (let [result (claude/invoke-intent-detection
                        {:key "TEST-123" :summary "Upload users" :description "Add new team members"}
                        ["users.xlsx"])]
            (println "Result:" (:success result))
            (when (:success result)
              (println "Is upload request:" (:is-user-upload result))))
          
          (println "\nTest 3: Column mapping")
          (let [result (claude/invoke-column-mapping
                        ["email" "name" "role"]
                        ["Email" "Full Name" "Job Title"])]
            (println "Result:" (:success result))
            (when (:success result)
              (println "Mapping:" (:mapping result))
              (println "Unmapped:" (:unmapped result))))
          
          (println "\n✅ All tests completed"))
        
        (println "❌ Claude CLI not available:" (:error availability))))))