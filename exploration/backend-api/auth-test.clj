(ns exploration.backend-api.auth-test
  "Test backend API authentication and login flow."
  (:require
    [user-upload.api.client :as api]
    [user-upload.config :as config]
    [user-upload.log :as log]
    [clojure.pprint :refer [pprint]]))

(defn test-config-credentials
  "Test that authentication credentials are available in config."
  []
  (println "=== Config Credentials Test ===")
  (let [config (config/load-config)
        email (:auth-email config)
        password (:auth-password config)
        base-url (:base-api-url config)]
    (println (format "Email: %s" (if (not-empty email) email "***missing***")))
    (println (format "Password: %s" (if (not-empty password) "***set***" "***missing***")))
    (println (format "Base API URL: %s" (if (not-empty base-url) base-url "***missing***")))
    
    (let [has-credentials (and (not-empty email) (not-empty password) (not-empty base-url))]
      (if has-credentials
        (println "✓ All authentication config present")
        (println "✗ Missing authentication configuration"))
      has-credentials)))

(defn test-login-with-config
  "Test login using credentials from configuration."
  []
  (println "\n=== Config Login Test ===")
  (try
    (let [result (api/login-with-config)]
      (if (:success result)
        (do
          (println "✓ Login successful!")
          (println (format "Message: %s" (:message result)))
          true)
        (do
          (println "✗ Login failed:")
          (println (format "Error: %s" (:error result)))
          (println (format "Message: %s" (:message result)))
          false)))
    (catch Exception e
      (println "✗ Login exception:")
      (println (.getMessage e))
      false)))

(defn test-manual-login
  "Test login with specific credentials."
  [email password]
  (println (format "\n=== Manual Login Test (%s) ===" email))
  (try
    (let [result (api/login email password)]
      (if (:success result)
        (do
          (println "✓ Login successful!")
          (println (format "Message: %s" (:message result)))
          true)
        (do
          (println "✗ Login failed:")
          (println (format "Error: %s" (:error result)))
          (println (format "Message: %s" (:message result)))
          false)))
    (catch Exception e
      (println "✗ Login exception:")
      (println (.getMessage e))
      false)))

(defn test-authentication-state
  "Test authentication state management."
  []
  (println "\n=== Authentication State Test ===")
  (try
    ;; Test initial state
    (let [initial-auth (api/authenticated?)]
      (println (format "Initial auth state: %s" initial-auth))
      
      ;; Test logout
      (let [logout-result (api/logout)]
        (println (format "Logout result: %s" (:message logout-result)))
        (let [after-logout (api/authenticated?)]
          (println (format "Auth state after logout: %s" after-logout))
          
          ;; Try to login again to restore state
          (println "Attempting to restore authentication...")
          (let [login-result (api/login-with-config)]
            (if (:success login-result)
              (do
                (println "✓ Authentication restored")
                (let [final-auth (api/authenticated?)]
                  (println (format "Final auth state: %s" final-auth))
                  true))
              (do
                (println "✗ Failed to restore authentication")
                false)))))
    (catch Exception e
      (println "✗ Authentication state test failed:")
      (println (.getMessage e))
      false))))

(defn test-mock-mode
  "Test the mock mode functionality."
  []
  (println "\n=== Mock Mode Test ===")
  (try
    ;; Enable mock mode
    (let [mock-result (api/enable-mock-mode!)]
      (println (format "Mock mode enabled: %s" (:message mock-result)))
      
      ;; Test mock login
      (let [mock-login-result (api/login "test@example.com" "test-password")]
        (if (:success mock-login-result)
          (do
            (println "✓ Mock login successful")
            (println (format "Message: %s" (:message mock-login-result)))
            true)
          (do
            (println "✗ Mock login failed")
            false))))
    (catch Exception e
      (println "✗ Mock mode test failed:")
      (println (.getMessage e))
      false)))

(defn test-authentication-workflow
  "Test the complete authentication workflow."
  []
  (println "\n=== Authentication Workflow Test ===")
  (try
    ;; Step 1: Check initial state
    (println "Step 1: Checking initial authentication state...")
    (let [initial-state (api/authenticated?)]
      (println (format "  Initial state: %s" initial-state))
      
      ;; Step 2: Logout to ensure clean state
      (println "Step 2: Ensuring clean state...")
      (api/logout)
      (let [clean-state (api/authenticated?)]
        (println (format "  Clean state: %s" clean-state))
        
        ;; Step 3: Attempt login
        (println "Step 3: Attempting login...")
        (let [login-result (api/login-with-config)]
          (if (:success login-result)
            (do
              (println "✓ Login successful in workflow")
              (let [final-state (api/authenticated?)]
                (println (format "  Final authenticated state: %s" final-state))
                final-state))
            (do
              (println "✗ Login failed in workflow")
              (println (format "  Error: %s" (:error login-result)))
              false)))))
    (catch Exception e
      (println "✗ Authentication workflow failed:")
      (println (.getMessage e))
      false)))

(defn run-all-tests
  "Run all authentication tests."
  []
  (println "Starting backend API authentication tests...")
  
  (let [results [(test-config-credentials)
                 (test-login-with-config)
                 (test-authentication-state)
                 (test-authentication-workflow)]]
    (println "\n=== Test Results ===")
    (let [passed (count (filter true? results))
          total (count results)]
      (println (format "Passed: %d/%d tests" passed total))
      (if (= passed total)
        (println "✓ All authentication tests passed!")
        (println "✗ Some authentication tests failed."))
      (= passed total))))

(defn run-mock-tests
  "Run tests using mock mode (for when backend is unavailable)."
  []
  (println "Starting mock mode tests...")
  (println "⚠️  Note: These tests use mock responses, not real backend data.")
  
  (let [results [(test-config-credentials)
                 (test-mock-mode)]]
    (println "\n=== Mock Test Results ===")
    (let [passed (count (filter true? results))
          total (count results)]
      (println (format "Passed: %d/%d mock tests" passed total))
      (if (= passed total)
        (println "✓ All mock tests passed!")
        (println "✗ Some mock tests failed."))
      (= passed total))))

;; For REPL usage
(comment
  ;; Run all tests (will use real backend)
  (run-all-tests)
  
  ;; Run mock tests only
  (run-mock-tests)
  
  ;; Test individual components
  (test-config-credentials)
  (test-login-with-config)
  (test-authentication-state)
  (test-authentication-workflow)
  
  ;; Test with specific credentials
  (test-manual-login "admin@example.com" "test-password")
  
  ;; Enable mock mode for testing
  (api/enable-mock-mode!)
  (test-mock-mode)
  
  ;; Check current authentication state
  (api/authenticated?)
  
  ;; Manual login/logout
  (api/login-with-config)
  (api/logout)
  )