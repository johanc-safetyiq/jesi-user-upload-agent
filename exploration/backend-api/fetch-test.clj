(ns exploration.backend-api.fetch-test
  "Test backend API fetch operations (GET requests)."
  (:require
    [user-upload.api.client :as api]
    [clojure.pprint :refer [pprint]]))

(defn test-get-profile
  "Test fetching user profile (v1 API)."
  []
  (println "=== Get Profile Test (v1) ===")
  (try
    (let [profile (api/get-profile)]
      (println "✓ Profile fetched successfully:")
      (pprint (select-keys profile [:id :company]))
      (when (:company profile)
        (println (format "Company: %s (ID: %s)" 
                         (get-in profile [:company :name])
                         (get-in profile [:company :id]))))
      true)
    (catch Exception e
      (println "✗ Failed to get profile:")
      (println (.getMessage e))
      false)))

(defn test-get-roles
  "Test fetching roles (v1 API)."
  []
  (println "\n=== Get Roles Test (v1) ===")
  (try
    (let [roles (api/get-roles)]
      (println (format "✓ Retrieved %d roles:" (count roles)))
      (doseq [role (take 5 roles)] ; Show first 5 roles
        (println (format "  [%s] %s" (:id role) (:name role))))
      (when (> (count roles) 5)
        (println (format "  ... and %d more roles" (- (count roles) 5))))
      true)
    (catch Exception e
      (println "✗ Failed to get roles:")
      (println (.getMessage e))
      false)))

(defn test-search-users
  "Test searching users (v2 API)."
  []
  (println "\n=== Search Users Test (v2) ===")
  (try
    (let [response (api/search-users)
          users (:users response)]
      (println (format "✓ Retrieved %d users:" (count users)))
      (doseq [user (take 5 users)] ; Show first 5 users
        (println (format "  [%s] %s %s <%s>" 
                         (:id user)
                         (:firstName user)
                         (:lastName user)
                         (:email user))))
      (when (> (count users) 5)
        (println (format "  ... and %d more users" (- (count users) 5))))
      true)
    (catch Exception e
      (println "✗ Failed to search users:")
      (println (.getMessage e))
      false)))

(defn test-search-teams
  "Test searching teams (v2 API)."
  []
  (println "\n=== Search Teams Test (v2) ===")
  (try
    (let [response (api/search-teams)
          teams (:teams response)]
      (println (format "✓ Retrieved %d teams:" (count teams)))
      (doseq [team teams] ; Show all teams
        (println (format "  [%s] %s" (:id team) (:name team))))
      true)
    (catch Exception e
      (println "✗ Failed to search teams:")
      (println (.getMessage e))
      false)))

(defn test-get-all-data
  "Test fetching all data in one operation."
  []
  (println "\n=== Get All Data Test ===")
  (try
    (let [all-data (api/get-all-data)]
      (println "✓ All data fetched successfully:")
      (println (format "  Profile: %s" (if (:profile all-data) "loaded" "missing")))
      (println (format "  Roles: %d items" (count (:roles all-data))))
      (println (format "  Users: %d items" (count (:users all-data))))
      (println (format "  Teams: %d items" (count (:teams all-data))))
      
      ;; Show some sample data
      (when-let [first-role (first (:roles all-data))]
        (println (format "  Sample role: %s" (:name first-role))))
      (when-let [first-user (first (:users all-data))]
        (println (format "  Sample user: %s %s" 
                         (:firstName first-user) 
                         (:lastName first-user))))
      (when-let [first-team (first (:teams all-data))]
        (println (format "  Sample team: %s" (:name first-team))))
      
      true)
    (catch Exception e
      (println "✗ Failed to get all data:")
      (println (.getMessage e))
      false)))

(defn test-helper-functions
  "Test helper functions for finding entities."
  []
  (println "\n=== Helper Functions Test ===")
  (try
    ;; Test find user by email
    (println "Testing find-user-by-email...")
    (let [test-email "admin@example.com" ; Use a common test email
          user (api/find-user-by-email test-email)]
      (if user
        (println (format "✓ Found user: %s %s" (:firstName user) (:lastName user)))
        (println (format "- No user found with email: %s" test-email))))
    
    ;; Test find team by name
    (println "Testing find-team-by-name...")
    (let [test-team-name "Engineering" ; Use a common team name
          team (api/find-team-by-name test-team-name)]
      (if team
        (println (format "✓ Found team: %s (ID: %s)" (:name team) (:id team)))
        (println (format "- No team found with name: %s" test-team-name))))
    
    ;; Test find role by name
    (println "Testing find-role-by-name...")
    (let [test-role-name "Administrator" ; Use a common role name
          role (api/find-role-by-name test-role-name)]
      (if role
        (println (format "✓ Found role: %s (ID: %s)" (:name role) (:id role)))
        (println (format "- No role found with name: %s" test-role-name))))
    
    true)
    (catch Exception e
      (println "✗ Helper functions test failed:")
      (println (.getMessage e))
      false)))

(defn test-fetch-workflow
  "Test the complete fetch workflow."
  []
  (println "\n=== Fetch Workflow Test ===")
  (try
    ;; Step 1: Ensure authentication
    (println "Step 1: Checking authentication...")
    (if (api/authenticated?)
      (println "✓ Already authenticated")
      (do
        (println "- Not authenticated, logging in...")
        (let [login-result (api/login-with-config)]
          (if (:success login-result)
            (println "✓ Login successful")
            (throw (ex-info "Login failed" login-result))))))
    
    ;; Step 2: Fetch basic data
    (println "\nStep 2: Fetching basic profile and roles...")
    (let [profile-success (test-get-profile)
          roles-success (test-get-roles)]
      (when (and profile-success roles-success)
        (println "✓ Basic data fetched successfully")))
    
    ;; Step 3: Fetch entity data
    (println "\nStep 3: Fetching users and teams...")
    (let [users-success (test-search-users)
          teams-success (test-search-teams)]
      (when (and users-success teams-success)
        (println "✓ Entity data fetched successfully")))
    
    ;; Step 4: Test combined fetch
    (println "\nStep 4: Testing combined fetch...")
    (test-get-all-data)
    
    true)
    (catch Exception e
      (println "✗ Fetch workflow failed:")
      (println (.getMessage e))
      false)))

(defn run-all-tests
  "Run all fetch operation tests."
  []
  (println "Starting backend API fetch tests...")
  
  ;; Ensure we're authenticated first
  (when-not (api/authenticated?)
    (println "⚠️  Not authenticated, attempting login...")
    (let [login-result (api/login-with-config)]
      (when-not (:success login-result)
        (println "⚠️  Login failed, some tests may fail")
        (println "Enabling mock mode for testing...")
        (api/enable-mock-mode!)
        (api/login "test@example.com" "test-password"))))
  
  (let [results [(test-get-profile)
                 (test-get-roles)
                 (test-search-users)
                 (test-search-teams)
                 (test-get-all-data)
                 (test-helper-functions)]]
    (println "\n=== Test Results ===")
    (let [passed (count (filter true? results))
          total (count results)]
      (println (format "Passed: %d/%d tests" passed total))
      (if (= passed total)
        (println "✓ All fetch tests passed!")
        (println "✗ Some fetch tests failed."))
      (= passed total))))

(defn run-mock-tests
  "Run fetch tests in mock mode."
  []
  (println "Starting mock fetch tests...")
  (println "⚠️  Using mock mode - responses are fake data for testing.")
  
  ;; Enable mock mode
  (api/enable-mock-mode!)
  (api/login "test@example.com" "test-password")
  
  (let [results [(test-get-profile)
                 (test-get-roles)
                 (test-search-users)
                 (test-search-teams)
                 (test-get-all-data)
                 (test-helper-functions)]]
    (println "\n=== Mock Test Results ===")
    (let [passed (count (filter true? results))
          total (count results)]
      (println (format "Passed: %d/%d mock tests" passed total))
      (if (= passed total)
        (println "✓ All mock fetch tests passed!")
        (println "✗ Some mock fetch tests failed."))
      (= passed total))))

;; For REPL usage
(comment
  ;; Run all tests (will attempt real backend first)
  (run-all-tests)
  
  ;; Run in mock mode
  (run-mock-tests)
  
  ;; Test individual operations
  (test-get-profile)
  (test-get-roles)
  (test-search-users)
  (test-search-teams)
  (test-get-all-data)
  (test-helper-functions)
  
  ;; Test workflow
  (test-fetch-workflow)
  
  ;; Enable mock mode manually
  (api/enable-mock-mode!)
  (api/login "test@example.com" "test")
  
  ;; Check authentication
  (api/authenticated?)
  )