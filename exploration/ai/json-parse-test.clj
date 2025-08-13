(ns exploration.ai.json-parse-test
  "Exploration tests for JSON response parsing from Claude CLI.
   
   These tests validate that JSON parsing handles various response formats,
   malformed JSON, and edge cases correctly."
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]))

(comment
  "Manual exploration of JSON parsing scenarios.
   
   Test various JSON response formats that Claude might return:
   
   1. Valid JSON responses (intent detection, column mapping)
   2. Malformed JSON (syntax errors, incomplete responses)
   3. Empty or null responses
   4. Extra content around JSON (prose before/after)
   5. Unicode and special character handling"

  ;; Test valid JSON responses
  (defn test-valid-json-parsing []
    (println "\n=== Valid JSON Parsing Tests ===\n")
    
    ;; Intent detection response
    (let [intent-json "{\"is_user_upload\": true}"
          parsed (json/parse-string intent-json true)]
      (println "Intent JSON:" intent-json)
      (println "Parsed:" parsed)
      (println "is_user_upload:" (:is_user_upload parsed)))
    
    ;; Column mapping response
    (let [mapping-json "{\"mapping\": {\"Email Address\": \"email\", \"FirstName\": \"first name\"}, \"unmapped\": [\"mobile number\"]}"
          parsed (json/parse-string mapping-json true)]
      (println "\nMapping JSON:" mapping-json)
      (println "Parsed:" parsed)
      (println "Mapping:" (:mapping parsed))
      (println "Unmapped:" (:unmapped parsed)))
    
    ;; Error summary response
    (let [summary-json "{\"summary\": \"2 users failed validation\", \"bullet_points\": [\"Invalid email format\", \"Missing required field\"]}"
          parsed (json/parse-string summary-json true)]
      (println "\nSummary JSON:" summary-json)
      (println "Parsed:" parsed)
      (println "Summary:" (:summary parsed))
      (println "Bullet points:" (:bullet_points parsed))))

  ;; Test malformed JSON handling
  (defn test-malformed-json-handling []
    (println "\n=== Malformed JSON Handling Tests ===\n")
    
    ;; Missing closing brace
    (let [bad-json "{\"is_user_upload\": true"]
      (try
        (let [parsed (json/parse-string bad-json true)]
          (println "Unexpected success parsing:" bad-json))
        (catch Exception e
          (println "✅ Correctly caught malformed JSON:" (.getMessage e)))))
    
    ;; Invalid value
    (let [bad-json "{\"is_user_upload\": undefined}"]
      (try
        (let [parsed (json/parse-string bad-json true)]
          (println "Unexpected success parsing:" bad-json))
        (catch Exception e
          (println "✅ Correctly caught invalid value:" (.getMessage e)))))
    
    ;; Extra commas
    (let [bad-json "{\"mapping\": {\"email\": \"email\",}, \"unmapped\": []}"]
      (try
        (let [parsed (json/parse-string bad-json true)]
          (println "Unexpected success parsing:" bad-json))
        (catch Exception e
          (println "✅ Correctly caught extra comma:" (.getMessage e))))))

  ;; Test edge cases
  (defn test-edge-cases []
    (println "\n=== Edge Case Tests ===\n")
    
    ;; Empty string
    (let [empty-json ""]
      (try
        (let [parsed (json/parse-string empty-json true)]
          (println "Empty string parsed as:" parsed))
        (catch Exception e
          (println "✅ Empty string error:" (.getMessage e)))))
    
    ;; Null/whitespace
    (let [whitespace-json "   \n\t  "]
      (try
        (let [parsed (json/parse-string whitespace-json true)]
          (println "Whitespace parsed as:" parsed))
        (catch Exception e
          (println "✅ Whitespace error:" (.getMessage e)))))
    
    ;; Just "null"
    (let [null-json "null"]
      (try
        (let [parsed (json/parse-string null-json true)]
          (println "Null JSON parsed as:" parsed " (type:" (type parsed) ")"))
        (catch Exception e
          (println "Null JSON error:" (.getMessage e)))))
    
    ;; Unicode characters
    (let [unicode-json "{\"message\": \"User: João (email: joão@test.com) ✅\"}"]
      (try
        (let [parsed (json/parse-string unicode-json true)]
          (println "Unicode JSON parsed as:" parsed))
        (catch Exception e
          (println "Unicode JSON error:" (.getMessage e))))))

  ;; Test responses with extra content (prose around JSON)
  (defn test-responses-with-prose []
    (println "\n=== JSON with Extra Content Tests ===\n")
    
    ;; Response with prose before JSON
    (let [response-with-prose "Here's the analysis:\n\n{\"is_user_upload\": true}\n\nHope this helps!"]
      (println "Response with prose:" response-with-prose)
      ;; Try to extract JSON from response
      (let [json-match (re-find #"\{.*\}" response-with-prose)]
        (if json-match
          (try
            (let [parsed (json/parse-string json-match true)]
              (println "✅ Extracted and parsed JSON:" parsed))
            (catch Exception e
              (println "Failed to parse extracted JSON:" (.getMessage e))))
          (println "❌ No JSON found in response"))))
    
    ;; Multiple JSON objects
    (let [multiple-json "{\"step1\": true} {\"step2\": false}"]
      (println "\nMultiple JSON objects:" multiple-json)
      ;; Find all JSON objects
      (let [json-matches (re-seq #"\{[^}]*\}" multiple-json)]
        (println "Found JSON objects:" (count json-matches))
        (doseq [json-obj json-matches]
          (try
            (let [parsed (json/parse-string json-obj true)]
              (println "Parsed:" parsed))
            (catch Exception e
              (println "Failed to parse:" json-obj " - " (.getMessage e))))))))

  ;; Safe JSON parsing utility
  (defn safe-parse-json
    "Safely parse JSON string, returning nil on failure."
    [json-str]
    (when (and json-str (not (str/blank? json-str)))
      (try
        (json/parse-string json-str true)
        (catch Exception e
          (println "JSON parse error:" (.getMessage e))
          nil))))

  ;; Test the safe parsing utility
  (defn test-safe-parsing []
    (println "\n=== Safe JSON Parsing Tests ===\n")
    
    (let [test-cases [
                      ;; Valid JSON
                      "{\"valid\": true}"
                      ;; Invalid JSON
                      "{invalid: json}"
                      ;; Empty string
                      ""
                      ;; Whitespace
                      "   "
                      ;; Null
                      nil
                      ;; Number
                      "42"
                      ;; String
                      "\"just a string\""
                      ;; Array
                      "[1, 2, 3]"]]
      
      (doseq [test-case test-cases]
        (let [result (safe-parse-json test-case)]
          (println (format "Input: %s -> Result: %s" 
                           (pr-str test-case) 
                           (pr-str result)))))))

  ;; Run all JSON parsing tests
  (defn run-all-json-tests []
    (println "🧪 Running JSON Parsing Exploration Tests")
    (test-valid-json-parsing)
    (test-malformed-json-handling)
    (test-edge-cases)
    (test-responses-with-prose)
    (test-safe-parsing)
    (println "\n✅ JSON parsing tests completed")))