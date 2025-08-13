(ns exploration.jira.auth-test
  "Test Jira authentication and connection."
  (:require
    [user-upload.jira.client :as jira]
    [user-upload.config :as config]
    [user-upload.log :as log]
    [clojure.pprint :refer [pprint]]))

(defn test-auth
  "Test Jira authentication with the configured credentials."
  []
  (println "=== Jira Authentication Test ===")
  (try
    ;; Test connection and get user info
    (let [connection-result (jira/test-connection)]
      (if (:success connection-result)
        (do
          (println "✓ Authentication successful!")
          (println "User info:")
          (pprint (:user connection-result))
          true)
        (do
          (println "✗ Authentication failed:")
          (println (:error connection-result))
          false)))
    (catch Exception e
      (println "✗ Exception during authentication test:")
      (println (.getMessage e))
      false)))

(defn test-user-info
  "Test getting detailed user information."
  []
  (println "\n=== User Info Test ===")
  (try
    (let [user-info (jira/get-user-info)]
      (println "✓ User info retrieved successfully:")
      (pprint (select-keys user-info [:accountId :displayName :emailAddress :accountType :active]))
      true)
    (catch Exception e
      (println "✗ Failed to get user info:")
      (println (.getMessage e))
      false)))

(defn run-all-tests
  "Run all authentication tests."
  []
  (println "Starting Jira authentication tests...")
  (println "Config:")
  (let [config (config/load-config)]
    (println (format "  Domain: %s" (:jira-domain config)))
    (println (format "  Email: %s" (:jira-email config)))
    (println (format "  API Token: %s" (if (:jira-api-token config) "***set***" "***missing***"))))
  
  (let [results [(test-auth)
                 (test-user-info)]]
    (println "\n=== Test Results ===")
    (if (every? true? results)
      (println "✓ All authentication tests passed!")
      (println "✗ Some authentication tests failed."))
    (every? true? results)))

;; For REPL usage
(comment
  ;; Run individual tests
  (test-auth)
  (test-user-info)
  
  ;; Run all tests
  (run-all-tests)
  
  ;; Check configuration
  (config/load-config)
  )