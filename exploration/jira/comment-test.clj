(ns exploration.jira.comment-test
  "Test Jira comment operations."
  (:require
    [user-upload.jira.client :as jira]
    [clojure.pprint :refer [pprint]]))

(defn test-get-comments
  "Test fetching comments for an issue."
  [issue-key]
  (println (format "=== Get Comments Test for %s ===" issue-key))
  (try
    (let [comments-response (jira/get-issue-comments issue-key)
          comments (:comments comments-response)]
      (println (format "✓ Retrieved %d comments" (count comments)))
      (when (> (count comments) 0)
        (println "Recent comments:")
        (doseq [comment (take 3 (reverse comments))] ; Show 3 most recent
          (println (format "  [%s] %s" 
                           (:id comment)
                           (get-in comment [:author :displayName])))
          (println (format "    Created: %s" (:created comment)))
          (println (format "    Body: %s..." 
                           (subs (:body comment) 0 (min 60 (count (:body comment))))))))
      true)
    (catch Exception e
      (println "✗ Failed to get comments:")
      (println (.getMessage e))
      false)))

(defn test-add-comment
  "Test adding a comment to an issue."
  [issue-key]
  (println (format "\n=== Add Comment Test for %s ===" issue-key))
  (let [test-comment (str "Test comment from user-upload-agent exploration script - " (java.util.Date.))]
    (try
      (let [response (jira/add-comment issue-key test-comment)]
        (println "✓ Comment added successfully:")
        (println (format "  Comment ID: %s" (:id response)))
        (println (format "  Self URL: %s" (:self response)))
        (println (format "  Body: %s" test-comment))
        true)
      (catch Exception e
        (println "✗ Failed to add comment:")
        (println (.getMessage e))
        false))))

(defn test-add-structured-comment
  "Test adding a structured comment (like approval request)."
  [issue-key]
  (println (format "\n=== Structured Comment Test for %s ===" issue-key))
  (let [structured-comment (str "[BOT:user-upload:test:v1]\n"
                                "This is a test structured comment.\n\n"
                                "**Test Details:**\n"
                                "- Timestamp: " (java.util.Date.) "\n"
                                "- Type: exploration test\n\n"
                                "This comment can be safely ignored or deleted.")]
    (try
      (let [response (jira/add-comment issue-key structured-comment)]
        (println "✓ Structured comment added successfully:")
        (println (format "  Comment ID: %s" (:id response)))
        (println "  Comment preview:")
        (println (format "    %s..." (subs structured-comment 0 (min 80 (count structured-comment)))))
        true)
      (catch Exception e
        (println "✗ Failed to add structured comment:")
        (println (.getMessage e))
        false))))

(defn test-comment-workflow
  "Test the complete comment workflow: add, then fetch to verify."
  [issue-key]
  (println (format "\n=== Comment Workflow Test for %s ===" issue-key))
  (let [test-message (str "Workflow test - " (System/currentTimeMillis))]
    (try
      ;; Step 1: Add comment
      (println "Step 1: Adding test comment...")
      (let [add-response (jira/add-comment issue-key test-message)
            comment-id (:id add-response)]
        (println (format "✓ Comment added with ID: %s" comment-id))
        
        ;; Step 2: Fetch comments to verify
        (println "Step 2: Fetching comments to verify...")
        (Thread/sleep 1000) ; Brief delay to ensure comment is indexed
        (let [comments-response (jira/get-issue-comments issue-key)
              comments (:comments comments-response)
              our-comment (first (filter #(= (:id %) comment-id) comments))]
          (if our-comment
            (do
              (println "✓ Comment verified in issue comments")
              (println (format "  Body matches: %s" (= (:body our-comment) test-message)))
              true)
            (do
              (println "✗ Comment not found in issue comments")
              false))))
      (catch Exception e
        (println "✗ Workflow test failed:")
        (println (.getMessage e))
        false))))

(defn find-test-issue-for-comments
  "Find a suitable test issue for comment operations."
  []
  (println "=== Finding Test Issue for Comments ===")
  (try
    (let [results (jira/search-issues {:jql "project = JESI AND status != \"Closed\" ORDER BY updated DESC"
                                       :max-results 5
                                       :fields "key,summary,status"})]
      (if-let [issues (:issues results)]
        (let [test-issue (first issues)]
          (println (format "✓ Using test issue: %s" (:key test-issue)))
          (println (format "  Summary: %s" (get-in test-issue [:fields :summary])))
          (println (format "  Status: %s" (get-in test-issue [:fields :status :name])))
          (:key test-issue))
        (do
          (println "✗ No suitable issues found")
          nil)))
    (catch Exception e
      (println "✗ Failed to find test issue:")
      (println (.getMessage e))
      nil)))

(defn run-all-tests
  "Run all comment tests."
  ([]
   (if-let [test-issue-key (find-test-issue-for-comments)]
     (run-all-tests test-issue-key)
     (println "Cannot run tests without a valid issue key")))
  ([issue-key]
   (println (format "Starting comment tests with issue: %s" issue-key))
   
   (let [results [(test-get-comments issue-key)
                  (test-add-comment issue-key)
                  (test-add-structured-comment issue-key)
                  (test-comment-workflow issue-key)]]
     (println "\n=== Test Results ===")
     (if (every? true? results)
       (println "✓ All comment tests passed!")
       (println "✗ Some comment tests failed."))
     (every? true? results))))

;; For REPL usage
(comment
  ;; Run all tests automatically
  (run-all-tests)
  
  ;; Use specific issue
  (run-all-tests "JESI-5928")
  
  ;; Run individual tests
  (test-get-comments "JESI-5928")
  (test-add-comment "JESI-5928")
  (test-add-structured-comment "JESI-5928")
  (test-comment-workflow "JESI-5928")
  
  ;; Find test issue
  (find-test-issue-for-comments)
  )