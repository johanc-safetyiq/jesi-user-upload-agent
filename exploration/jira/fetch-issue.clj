(ns exploration.jira.fetch-issue
  "Test issue fetching with attachment metadata."
  (:require
    [user-upload.jira.client :as jira]
    [clojure.pprint :refer [pprint]]))

(defn test-fetch-issue-basic
  "Test fetching a basic issue by key."
  [issue-key]
  (println (format "=== Basic Fetch Test for %s ===" issue-key))
  (try
    (let [issue (jira/get-issue issue-key {:fields "key,summary,status,description,created,updated"})]
      (println "✓ Issue fetched successfully:")
      (println (format "  Key: %s" (:key issue)))
      (println (format "  Summary: %s" (get-in issue [:fields :summary])))
      (println (format "  Status: %s" (get-in issue [:fields :status :name])))
      (println (format "  Created: %s" (get-in issue [:fields :created])))
      (println (format "  Description length: %d characters" 
                       (count (or (get-in issue [:fields :description]) ""))))
      true)
    (catch Exception e
      (println "✗ Failed to fetch issue:")
      (println (.getMessage e))
      false)))

(defn test-fetch-with-attachments
  "Test fetching an issue with attachment metadata."
  [issue-key]
  (println (format "\n=== Attachment Fetch Test for %s ===" issue-key))
  (try
    (let [issue (jira/get-issue-with-attachments issue-key)]
      (println "✓ Issue with attachments fetched successfully:")
      (println (format "  Key: %s" (:key issue)))
      (println (format "  Summary: %s" (get-in issue [:fields :summary])))
      
      ;; Show attachment details
      (let [attachments (get-in issue [:fields :attachment])]
        (if (empty? attachments)
          (println "  No attachments found")
          (do
            (println (format "  %d attachment(s):" (count attachments)))
            (doseq [att attachments]
              (println (format "    - %s" (:filename att)))
              (println (format "      Size: %s bytes" (:size att)))
              (println (format "      MIME: %s" (:mimeType att)))
              (println (format "      Created: %s" (:created att)))
              (println (format "      Author: %s" (get-in att [:author :displayName])))))))
      
      ;; Show comment count
      (let [comments (get-in issue [:fields :comment :comments])]
        (println (format "  %d comment(s)" (count comments))))
      
      true)
    (catch Exception e
      (println "✗ Failed to fetch issue with attachments:")
      (println (.getMessage e))
      false)))

(defn test-fetch-transitions
  "Test fetching available transitions for an issue."
  [issue-key]
  (println (format "\n=== Transitions Test for %s ===" issue-key))
  (try
    (let [transitions (jira/get-issue-transitions issue-key)]
      (println "✓ Transitions fetched successfully:")
      (doseq [transition (:transitions transitions)]
        (println (format "  %s -> %s (ID: %s)" 
                         (:id transition)
                         (:name transition)
                         (:id transition))))
      true)
    (catch Exception e
      (println "✗ Failed to fetch transitions:")
      (println (.getMessage e))
      false)))

(defn test-fetch-comments
  "Test fetching comments for an issue."
  [issue-key]
  (println (format "\n=== Comments Test for %s ===" issue-key))
  (try
    (let [comments-response (jira/get-issue-comments issue-key)
          comments (:comments comments-response)]
      (println (format "✓ %d comment(s) fetched successfully:" (count comments)))
      (doseq [comment (take 3 comments)] ; Show first 3 comments
        (println (format "  Comment ID: %s" (:id comment)))
        (println (format "  Author: %s" (get-in comment [:author :displayName])))
        (println (format "  Created: %s" (:created comment)))
        (println (format "  Body preview: %s..." 
                         (subs (:body comment) 0 (min 50 (count (:body comment)))))))
      (when (> (count comments) 3)
        (println (format "  ... and %d more comments" (- (count comments) 3))))
      true)
    (catch Exception e
      (println "✗ Failed to fetch comments:")
      (println (.getMessage e))
      false)))

(defn find-test-issue
  "Find a suitable test issue from recent issues."
  []
  (println "=== Finding Test Issue ===")
  (try
    (let [results (jira/search-issues {:jql "project = JESI ORDER BY created DESC"
                                       :max-results 10
                                       :fields "key,summary,attachment"})]
      (if-let [issues (:issues results)]
        (let [issue-with-attachments (first (filter #(> (count (get-in % [:fields :attachment])) 0) issues))
              any-issue (first issues)]
          (cond
            issue-with-attachments
            (do
              (println (format "✓ Found test issue with attachments: %s" (:key issue-with-attachments)))
              (:key issue-with-attachments))
            
            any-issue
            (do
              (println (format "✓ Found test issue (no attachments): %s" (:key any-issue)))
              (:key any-issue))
            
            :else
            (do
              (println "✗ No issues found in project")
              nil)))
        (do
          (println "✗ No issues found")
          nil)))
    (catch Exception e
      (println "✗ Failed to find test issue:")
      (println (.getMessage e))
      nil)))

(defn run-all-tests
  "Run all issue fetching tests."
  ([]
   (if-let [test-issue-key (find-test-issue)]
     (run-all-tests test-issue-key)
     (println "Cannot run tests without a valid issue key")))
  ([issue-key]
   (println (format "Starting issue fetch tests with issue: %s" issue-key))
   
   (let [results [(test-fetch-issue-basic issue-key)
                  (test-fetch-with-attachments issue-key)
                  (test-fetch-transitions issue-key)
                  (test-fetch-comments issue-key)]]
     (println "\n=== Test Results ===")
     (if (every? true? results)
       (println "✓ All issue fetch tests passed!")
       (println "✗ Some issue fetch tests failed."))
     (every? true? results))))

;; For REPL usage
(comment
  ;; Find a test issue automatically
  (run-all-tests)
  
  ;; Use a specific issue key
  (run-all-tests "JESI-5928")
  
  ;; Run individual tests
  (test-fetch-issue-basic "JESI-5928")
  (test-fetch-with-attachments "JESI-5928")
  (test-fetch-transitions "JESI-5928")
  (test-fetch-comments "JESI-5928")
  
  ;; Find test issue
  (find-test-issue)
  )