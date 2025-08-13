(ns exploration.jira.jql-test
  "Test JQL queries against Jira."
  (:require
    [user-upload.jira.client :as jira]
    [clojure.pprint :refer [pprint]]))

(defn test-basic-jql
  "Test basic JQL queries."
  []
  (println "=== Basic JQL Test ===")
  (try
    ;; Test a simple query to find recent issues
    (let [results (jira/search-issues {:jql "project = JESI ORDER BY created DESC"
                                       :max-results 5
                                       :fields "key,summary,status,created"})]
      (println (format "✓ Found %d issues" (count (:issues results))))
      (println "Recent issues:")
      (doseq [issue (:issues results)]
        (println (format "  %s: %s [%s]" 
                         (:key issue)
                         (get-in issue [:fields :summary])
                         (get-in issue [:fields :status :name]))))
      true)
    (catch Exception e
      (println "✗ JQL query failed:")
      (println (.getMessage e))
      false)))

(defn test-user-upload-jql
  "Test JQL queries specific to user upload requests."
  []
  (println "\n=== User Upload JQL Test ===")
  (try
    ;; Look for issues with user-upload in the title or labels
    (let [results (jira/search-issues {:jql "project = JESI AND (summary ~ \"user\" OR summary ~ \"upload\") ORDER BY created DESC"
                                       :max-results 10
                                       :fields "key,summary,status,labels,created"})]
      (println (format "✓ Found %d user-upload related issues" (count (:issues results))))
      (when (> (count (:issues results)) 0)
        (println "User upload issues:")
        (doseq [issue (:issues results)]
          (println (format "  %s: %s [%s]" 
                           (:key issue)
                           (get-in issue [:fields :summary])
                           (get-in issue [:fields :status :name])))))
      true)
    (catch Exception e
      (println "✗ User upload JQL query failed:")
      (println (.getMessage e))
      false)))

(defn test-attachment-jql
  "Test JQL queries for issues with attachments."
  []
  (println "\n=== Attachment JQL Test ===")
  (try
    ;; Find issues with attachments
    (let [results (jira/search-issues {:jql "project = JESI AND attachments > 0 ORDER BY created DESC"
                                       :max-results 5
                                       :fields "key,summary,attachment"
                                       :expand "attachment"})]
      (println (format "✓ Found %d issues with attachments" (count (:issues results))))
      (doseq [issue (:issues results)]
        (let [attachments (get-in issue [:fields :attachment])]
          (println (format "  %s: %d attachment(s)" 
                           (:key issue)
                           (count attachments)))
          (doseq [att attachments]
            (println (format "    - %s (%s bytes)" (:filename att) (:size att))))))
      true)
    (catch Exception e
      (println "✗ Attachment JQL query failed:")
      (println (.getMessage e))
      false)))

(defn test-status-jql
  "Test JQL queries by status."
  []
  (println "\n=== Status JQL Test ===")
  (try
    ;; Find open issues
    (let [results (jira/search-issues {:jql "project = JESI AND status = \"Open\" ORDER BY created DESC"
                                       :max-results 5
                                       :fields "key,summary,status"})]
      (println (format "✓ Found %d open issues" (count (:issues results))))
      (when (> (count (:issues results)) 0)
        (println "Open issues:")
        (doseq [issue (:issues results)]
          (println (format "  %s: %s" 
                           (:key issue)
                           (get-in issue [:fields :summary])))))
      true)
    (catch Exception e
      (println "✗ Status JQL query failed:")
      (println (.getMessage e))
      false)))

(defn test-pagination
  "Test JQL pagination."
  []
  (println "\n=== Pagination Test ===")
  (try
    ;; First page
    (let [page1 (jira/search-issues {:jql "project = JESI ORDER BY created DESC"
                                     :max-results 2
                                     :start-at 0
                                     :fields "key,summary"})
          page2 (jira/search-issues {:jql "project = JESI ORDER BY created DESC"
                                     :max-results 2
                                     :start-at 2
                                     :fields "key,summary"})]
      (println (format "✓ Page 1: %d issues (start-at: 0)" (count (:issues page1))))
      (println (format "✓ Page 2: %d issues (start-at: 2)" (count (:issues page2))))
      (println (format "  Total available: %d" (:total page1)))
      true)
    (catch Exception e
      (println "✗ Pagination test failed:")
      (println (.getMessage e))
      false)))

(defn run-all-tests
  "Run all JQL tests."
  []
  (println "Starting JQL query tests...")
  
  (let [results [(test-basic-jql)
                 (test-user-upload-jql)
                 (test-attachment-jql)
                 (test-status-jql)
                 (test-pagination)]]
    (println "\n=== Test Results ===")
    (if (every? true? results)
      (println "✓ All JQL tests passed!")
      (println "✗ Some JQL tests failed."))
    (every? true? results)))

;; For REPL usage
(comment
  ;; Run individual tests
  (test-basic-jql)
  (test-user-upload-jql)
  (test-attachment-jql)
  (test-status-jql)
  (test-pagination)
  
  ;; Run all tests
  (run-all-tests)
  
  ;; Custom JQL query
  (jira/search-issues {:jql "project = JESI ORDER BY created DESC"
                       :max-results 3
                       :fields "key,summary,status"})
  )