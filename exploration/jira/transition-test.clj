(ns exploration.jira.transition-test
  "Test Jira issue transition operations."
  (:require
    [user-upload.jira.client :as jira]
    [clojure.pprint :refer [pprint]]))

(defn test-get-transitions
  "Test fetching available transitions for an issue."
  [issue-key]
  (println (format "=== Get Transitions Test for %s ===" issue-key))
  (try
    (let [transitions-response (jira/get-issue-transitions issue-key)
          transitions (:transitions transitions-response)]
      (println (format "✓ Retrieved %d available transitions:" (count transitions)))
      (doseq [transition transitions]
        (println (format "  [%s] %s -> %s" 
                         (:id transition)
                         (:name transition)
                         (get-in transition [:to :name]))))
      (when (empty? transitions)
        (println "  No transitions available (issue may be in final state)"))
      transitions)
    (catch Exception e
      (println "✗ Failed to get transitions:")
      (println (.getMessage e))
      [])))

(defn test-transition-issue
  "Test transitioning an issue (WARNING: This will actually change the issue status)."
  [issue-key transition-id]
  (println (format "\n=== Transition Test for %s (ID: %s) ===" issue-key transition-id))
  (println "⚠️  WARNING: This will actually change the issue status!")
  (print "Continue? (y/N): ")
  (flush)
  (let [response (read-line)]
    (if (= (str/lower-case (str/trim response)) "y")
      (try
        (let [comment "Transitioned by user-upload-agent exploration script"
              result (jira/transition-issue issue-key transition-id comment)]
          (println "✓ Issue transitioned successfully")
          (println "  Response:")
          (pprint result)
          true)
        (catch Exception e
          (println "✗ Failed to transition issue:")
          (println (.getMessage e))
          false))
      (do
        (println "Transition test skipped by user")
        :skipped))))

(defn test-safe-transition-simulation
  "Test transition logic without actually performing the transition."
  [issue-key]
  (println (format "\n=== Safe Transition Simulation for %s ===" issue-key))
  (try
    ;; Get current issue status
    (let [issue (jira/get-issue issue-key {:fields "key,summary,status"})
          current-status (get-in issue [:fields :status :name])
          transitions (test-get-transitions issue-key)]
      
      (println (format "Current status: %s" current-status))
      
      (if (empty? transitions)
        (println "✓ No transitions available - issue is likely in final state")
        (do
          (println "Available transitions:")
          (doseq [transition transitions]
            (println (format "  Would transition to: %s (ID: %s)" 
                             (get-in transition [:to :name])
                             (:id transition))))
          
          ;; Demonstrate transition preparation
          (let [first-transition (first transitions)]
            (println (format "\nSimulation: Would use transition ID %s to move to '%s'"
                             (:id first-transition)
                             (get-in first-transition [:to :name]))))))
      true)
    (catch Exception e
      (println "✗ Simulation failed:")
      (println (.getMessage e))
      false)))

(defn test-transition-workflow
  "Test complete transition workflow: check status -> get transitions -> simulate."
  [issue-key]
  (println (format "\n=== Transition Workflow Test for %s ===" issue-key))
  (try
    ;; Step 1: Get current issue details
    (println "Step 1: Getting current issue status...")
    (let [issue (jira/get-issue issue-key {:fields "key,summary,status,assignee"})]
      (println "✓ Issue details:")
      (println (format "  Key: %s" (:key issue)))
      (println (format "  Summary: %s" (get-in issue [:fields :summary])))
      (println (format "  Status: %s" (get-in issue [:fields :status :name])))
      (println (format "  Assignee: %s" (or (get-in issue [:fields :assignee :displayName]) "Unassigned")))
      
      ;; Step 2: Get available transitions
      (println "\nStep 2: Getting available transitions...")
      (let [transitions (test-get-transitions issue-key)]
        (if (empty? transitions)
          (println "✓ Workflow complete - no transitions available")
          (println (format "✓ Found %d possible transitions" (count transitions))))
        
        ;; Step 3: Analyze transition options
        (when (> (count transitions) 0)
          (println "\nStep 3: Analyzing transition options...")
          (doseq [transition transitions]
            (let [to-status (get-in transition [:to :name])]
              (println (format "  Option: %s -> %s" 
                               (get-in issue [:fields :status :name])
                               to-status)))))
        
        true))
    (catch Exception e
      (println "✗ Workflow test failed:")
      (println (.getMessage e))
      false)))

(defn find-test-issue-for-transitions
  "Find a suitable test issue for transition operations."
  []
  (println "=== Finding Test Issue for Transitions ===")
  (try
    (let [results (jira/search-issues {:jql "project = JESI AND status IN (\"Open\", \"In Progress\", \"To Do\") ORDER BY updated DESC"
                                       :max-results 5
                                       :fields "key,summary,status"})]
      (if-let [issues (:issues results)]
        (let [test-issue (first issues)]
          (println (format "✓ Using test issue: %s" (:key test-issue)))
          (println (format "  Summary: %s" (get-in test-issue [:fields :summary])))
          (println (format "  Status: %s" (get-in test-issue [:fields :status :name])))
          (:key test-issue))
        (do
          (println "✗ No suitable issues found in transitionable states")
          nil)))
    (catch Exception e
      (println "✗ Failed to find test issue:")
      (println (.getMessage e))
      nil)))

(defn run-safe-tests
  "Run safe transition tests (no actual state changes)."
  ([]
   (if-let [test-issue-key (find-test-issue-for-transitions)]
     (run-safe-tests test-issue-key)
     (println "Cannot run tests without a valid issue key")))
  ([issue-key]
   (println (format "Starting safe transition tests with issue: %s" issue-key))
   
   (let [results [(test-safe-transition-simulation issue-key)
                  (test-transition-workflow issue-key)]]
     (println "\n=== Test Results ===")
     (if (every? true? results)
       (println "✓ All safe transition tests passed!")
       (println "✗ Some transition tests failed."))
     (every? true? results))))

(defn run-all-tests
  "Run all transition tests including potentially destructive ones."
  ([]
   (if-let [test-issue-key (find-test-issue-for-transitions)]
     (run-all-tests test-issue-key)
     (println "Cannot run tests without a valid issue key")))
  ([issue-key]
   (println (format "Starting all transition tests with issue: %s" issue-key))
   (println "⚠️  WARNING: Some tests may modify the issue state!")
   
   ;; Run safe tests first
   (let [safe-results (run-safe-tests issue-key)]
     (println "\n=== Potentially Destructive Tests ===")
     (let [transitions (test-get-transitions issue-key)]
       (if (empty? transitions)
         (println "✓ No destructive tests needed - no transitions available")
         (do
           (println "Available transitions for destructive testing:")
           (doseq [[idx transition] (map-indexed vector transitions)]
             (println (format "%d. %s (ID: %s)" idx (:name transition) (:id transition))))
           (println "\nTo test actual transitions, call test-transition-issue manually"))))
     
     safe-results)))

;; For REPL usage
(comment
  ;; Run safe tests only
  (run-safe-tests)
  (run-safe-tests "JESI-5928")
  
  ;; Run all tests (includes warnings for destructive operations)
  (run-all-tests)
  
  ;; Manual transition (destructive - use carefully!)
  (test-transition-issue "JESI-5928" "11") ; Replace with actual transition ID
  
  ;; Individual safe tests
  (test-safe-transition-simulation "JESI-5928")
  (test-transition-workflow "JESI-5928")
  
  ;; Find test issue
  (find-test-issue-for-transitions)
  )