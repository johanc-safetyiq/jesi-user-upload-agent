(ns exploration.jira.approval-detect
  "Test approval detection logic for Jira comments."
  (:require
    [user-upload.jira.approval :as approval]
    [user-upload.jira.client :as jira]
    [clojure.pprint :refer [pprint]]))

(defn test-approval-status-check
  "Test checking approval status for an issue."
  [issue-key]
  (println (format "=== Approval Status Check for %s ===" issue-key))
  (try
    (let [status (approval/check-approval-status issue-key)]
      (println "✓ Approval status check completed:")
      (println (format "  Status: %s" (:status status)))
      (println (format "  Message: %s" (:message status)))
      
      (when (:request-comment status)
        (println (format "  Request Comment ID: %s" (get-in status [:request-comment :id])))
        (println (format "  Request Created: %s" (get-in status [:request-comment :created]))))
      
      (when (:approval-comment status)
        (println (format "  Approval Comment ID: %s" (get-in status [:approval-comment :id])))
        (println (format "  Approved by: %s" (get-in status [:approval-comment :author :displayName]))))
      
      status)
    (catch Exception e
      (println "✗ Failed to check approval status:")
      (println (.getMessage e))
      {:status :error :error (.getMessage e)})))

(defn test-request-approval
  "Test posting an approval request comment."
  [issue-key]
  (println (format "\n=== Request Approval Test for %s ===" issue-key))
  (let [request-details {:user-count 25
                         :team-count 3
                         :filename "test-users.csv"}]
    (try
      (let [result (approval/request-approval issue-key request-details)]
        (if (:success result)
          (do
            (println "✓ Approval request posted successfully:")
            (println (format "  Comment ID: %s" (:comment-id result)))
            (println (format "  Message: %s" (:message result)))
            true)
          (do
            (println "✗ Failed to post approval request:")
            (println (format "  Error: %s" (:error result)))
            false)))
      (catch Exception e
        (println "✗ Exception posting approval request:")
        (println (.getMessage e))
        false))))

(defn test-approval-detection-workflow
  "Test the complete approval detection workflow."
  [issue-key]
  (println (format "\n=== Approval Detection Workflow for %s ===" issue-key))
  
  ;; Step 1: Check initial status
  (println "Step 1: Checking initial approval status...")
  (let [initial-status (test-approval-status-check issue-key)]
    
    ;; Step 2: If no request exists, post one
    (when (= (:status initial-status) :no-request)
      (println "\nStep 2: No approval request found, posting one...")
      (test-request-approval issue-key)
      (Thread/sleep 2000)) ; Wait for comment to be indexed
    
    ;; Step 3: Check status again
    (println "\nStep 3: Checking approval status after potential request...")
    (let [final-status (test-approval-status-check issue-key)]
      (println "\n=== Workflow Summary ===")
      (case (:status final-status)
        :no-request (println "✗ Still no approval request found")
        :pending (println "✓ Approval request is pending human response")
        :approved (println "✓ Request has been approved!")
        :error (println (format "✗ Error: %s" (:error final-status))))
      final-status)))

(defn test-utility-functions
  "Test the utility functions for approval checking."
  [issue-key]
  (println (format "\n=== Utility Functions Test for %s ===" issue-key))
  (try
    (let [is-approved (approval/is-approved? issue-key)
          needs-request (approval/needs-approval-request? issue-key)
          waiting (approval/wait-for-approval? issue-key)]
      (println "✓ Utility functions executed:")
      (println (format "  Is approved: %s" is-approved))
      (println (format "  Needs approval request: %s" needs-request))
      (println (format "  Waiting for approval: %s" waiting))
      
      ;; Validate logic consistency
      (let [states [is-approved needs-request waiting]
            active-states (count (filter identity states))]
        (if (<= active-states 1)
          (println "✓ State consistency check passed")
          (println "✗ State consistency check failed - multiple states active")))
      true)
    (catch Exception e
      (println "✗ Utility functions test failed:")
      (println (.getMessage e))
      false)))

(defn test-comment-parsing
  "Test comment parsing and detection logic with sample data."
  []
  (println "\n=== Comment Parsing Test ===")
  
  ;; Test bot comment detection
  (println "Testing bot comment detection...")
  (let [sample-comments [{:author {:accountId "johan.codinha@safetyiq.com" 
                                   :displayName "Johan Codinha"}
                          :body "[BOT:user-upload:approval-request:v1]\nTest request"
                          :created "2024-01-01T10:00:00.000Z"
                          :id "12345"}
                         {:author {:accountId "other.user@safetyiq.com"
                                   :displayName "Other User"}
                          :body "approved"
                          :created "2024-01-01T11:00:00.000Z"
                          :id "12346"}]]
    
    ;; Test with config override
    (try
      (let [request-comment (approval/find-latest-approval-request sample-comments)
            approval-comment (when request-comment
                               (approval/find-approval-after-request sample-comments request-comment))]
        (if request-comment
          (do
            (println "✓ Found approval request comment")
            (println (format "  ID: %s" (:id request-comment)))
            (if approval-comment
              (do
                (println "✓ Found approval comment")
                (println (format "  ID: %s" (:id approval-comment))))
              (println "- No approval comment found")))
          (println "✗ No approval request found in sample data"))
        true)
      (catch Exception e
        (println "✗ Comment parsing test failed:")
        (println (.getMessage e))
        false))))

(defn find-test-issue-with-comments
  "Find a suitable test issue that has comments for approval testing."
  []
  (println "=== Finding Test Issue with Comments ===")
  (try
    (let [results (jira/search-issues {:jql "project = JESI AND comment ~ \"[BOT:user-upload\" ORDER BY updated DESC"
                                       :max-results 3
                                       :fields "key,summary,comment"})]
      (if-let [issues (seq (:issues results))]
        (let [issue-with-bot-comments (first issues)]
          (println (format "✓ Found issue with bot comments: %s" (:key issue-with-bot-comments)))
          (:key issue-with-bot-comments))
        ;; Fallback to any issue with comments
        (let [results2 (jira/search-issues {:jql "project = JESI AND comment IS NOT EMPTY ORDER BY updated DESC"
                                            :max-results 3
                                            :fields "key,summary"})]
          (if-let [issues2 (seq (:issues results2))]
            (let [any-issue-with-comments (first issues2)]
              (println (format "✓ Using issue with comments (no bot comments found): %s" (:key any-issue-with-comments)))
              (:key any-issue-with-comments))
            (do
              (println "✗ No issues with comments found")
              nil)))))
    (catch Exception e
      (println "✗ Failed to find test issue:")
      (println (.getMessage e))
      nil)))

(defn run-all-tests
  "Run all approval detection tests."
  ([]
   (if-let [test-issue-key (find-test-issue-with-comments)]
     (run-all-tests test-issue-key)
     (do
       (println "No test issue found, running parsing tests only...")
       (test-comment-parsing))))
  ([issue-key]
   (println (format "Starting approval detection tests with issue: %s" issue-key))
   
   (let [results [(test-approval-status-check issue-key)
                  (test-utility-functions issue-key)
                  (test-comment-parsing)
                  ;; Note: Not running test-request-approval automatically to avoid spam
                  ]]
     (println "\n=== Test Results ===")
     (let [success-count (count (filter #(not (or (false? %) (= :error (:status %)))) results))]
       (println (format "%d/%d tests completed successfully" success-count (count results)))
       (when (< success-count (count results))
         (println "Note: Some tests may require manual approval request posting"))
       (> success-count 0)))))

(defn run-workflow-test
  "Run the complete approval workflow test (may post comments)."
  ([]
   (if-let [test-issue-key (find-test-issue-with-comments)]
     (run-workflow-test test-issue-key)
     (println "Cannot run workflow test without a valid issue key")))
  ([issue-key]
   (println "⚠️  WARNING: This test may post approval request comments to the issue!")
   (print "Continue with workflow test? (y/N): ")
   (flush)
   (let [response (read-line)]
     (if (= (clojure.string/lower-case (clojure.string/trim response)) "y")
       (test-approval-detection-workflow issue-key)
       (println "Workflow test skipped by user")))))

;; For REPL usage
(comment
  ;; Run safe tests (no comment posting)
  (run-all-tests)
  
  ;; Run with specific issue
  (run-all-tests "JESI-5928")
  
  ;; Run workflow test (may post comments - use carefully!)
  (run-workflow-test "JESI-5928")
  
  ;; Individual tests
  (test-approval-status-check "JESI-5928")
  (test-utility-functions "JESI-5928")
  (test-comment-parsing)
  
  ;; Manual approval request (use carefully!)
  (test-request-approval "JESI-5928")
  
  ;; Find test issue
  (find-test-issue-with-comments)
  )