(ns exploration.config.env-test
  "Test script to validate environment configuration loading."
  (:require [user-upload.config :as config]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]))

(defn test-env-loading
  "Test basic environment variable loading with environ."
  []
  (println "=== Testing Environment Variable Loading ===")
  
  (println "1. Testing environ library...")
  (if (map? env)
    (println "✓ environ.core/env map is accessible")
    (println "✗ environ.core/env is not a map"))
  
  (println "2. Checking for HOME environment variable...")
  (if-let [home (env :home)]
    (println "✓ HOME environment variable found:" home)
    (println "✗ HOME environment variable not found"))
  
  (println))

(defn test-config-without-env
  "Test config loading when required environment variables are missing."
  []
  (println "=== Testing Configuration Without .env File ===")
  
  (try
    (config/reset-config!)
    (config/get-config)
    (println "✗ Config validation should have failed without .env file")
    (catch Exception e
      (if (re-find #"Missing required configuration keys" (.getMessage e))
        (println "✓ Config validation properly fails with missing environment variables")
        (do
          (println "✗ Unexpected error message:" (.getMessage e))
          (throw e)))))
  
  (println))

(defn test-config-with-minimal-env
  "Test config loading with minimal required environment variables."
  []
  (println "=== Testing Configuration With Minimal Environment ===")
  
  ;; Set minimal environment variables for testing
  ;; Environ reads from both system properties and env vars
  (System/setProperty "JIRA_EMAIL" "test@example.com")
  (System/setProperty "JIRA_API_TOKEN" "test-token")
  (System/setProperty "JIRA_DOMAIN" "test.atlassian.net")
  (System/setProperty "BASE_API_URL" "https://test-api.example.com")
  (System/setProperty "BASE_CLJ_API_URL" "https://test-clj-api.example.com")
  (System/setProperty "ANTHROPIC_API_KEY" "test-anthropic-key")
  (System/setProperty "AUTH_EMAIL" "test-auth@example.com")
  (System/setProperty "AUTH_PASSWORD" "test-password")
  
  (try
    (config/reset-config!)
    (let [cfg (config/get-config)]
      (if (and (get-in cfg [:jira :email])
               (get-in cfg [:api :base-url])
               (get-in cfg [:ai :anthropic-api-key]))
        (println "✓ Configuration loaded successfully with test environment variables")
        (println "✗ Configuration missing expected values")))
    (catch Exception e
      (println "✗ Configuration loading failed with test environment variables:" (.getMessage e))
      (throw e))
    (finally
      ;; Clean up test environment variables
      (System/clearProperty "JIRA_EMAIL")
      (System/clearProperty "JIRA_API_TOKEN")
      (System/clearProperty "JIRA_DOMAIN")
      (System/clearProperty "BASE_API_URL")
      (System/clearProperty "BASE_CLJ_API_URL")
      (System/clearProperty "ANTHROPIC_API_KEY")
      (System/clearProperty "AUTH_EMAIL")
      (System/clearProperty "AUTH_PASSWORD")))
  
  (println))

(defn test-config-caching
  "Test that configuration caching works correctly."
  []
  (println "=== Testing Configuration Caching ===")
  
  ;; Set up test environment
  (System/setProperty "JIRA_EMAIL" "cache-test@example.com")
  (System/setProperty "JIRA_API_TOKEN" "cache-test-token")
  (System/setProperty "JIRA_DOMAIN" "cache-test.atlassian.net")
  (System/setProperty "BASE_API_URL" "https://cache-test-api.example.com")
  (System/setProperty "BASE_CLJ_API_URL" "https://cache-test-clj-api.example.com")
  (System/setProperty "ANTHROPIC_API_KEY" "cache-test-anthropic-key")
  (System/setProperty "AUTH_EMAIL" "cache-test-auth@example.com")
  (System/setProperty "AUTH_PASSWORD" "cache-test-password")
  
  (try
    (config/reset-config!)
    (let [cfg1 (config/config)
          cfg2 (config/config)]
      (if (identical? cfg1 cfg2)
        (println "✓ Configuration caching works (same object returned)")
        (println "✗ Configuration caching failed (different objects returned)")))
    (finally
      ;; Clean up
      (config/reset-config!)
      (System/clearProperty "JIRA_EMAIL")
      (System/clearProperty "JIRA_API_TOKEN")
      (System/clearProperty "JIRA_DOMAIN")
      (System/clearProperty "BASE_API_URL")
      (System/clearProperty "BASE_CLJ_API_URL")
      (System/clearProperty "ANTHROPIC_API_KEY")
      (System/clearProperty "AUTH_EMAIL")
      (System/clearProperty "AUTH_PASSWORD")))
  
  (println))

(defn run-all-tests
  "Run all configuration tests."
  []
  (println "Starting configuration tests...\n")
  
  (test-env-loading)
  (test-config-without-env)
  (test-config-with-minimal-env)
  (test-config-caching)
  
  (println "=== Configuration Tests Complete ==="))

;; Run tests when this file is evaluated
(when *command-line-args*
  (run-all-tests))