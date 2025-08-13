(ns exploration.backend-api.create-user
  "Test backend API user and team creation operations."
  (:require
    [user-upload.api.client :as api]
    [clojure.pprint :refer [pprint]]))

(defn test-create-user
  "Test creating a user with the backend API."
  [user-data]
  (println (format "=== Create User Test (%s) ===" (:email user-data)))
  (try
    ;; Validate required data
    (let [required-fields [:firstName :lastName :email :roleId :teamIds]
          missing-fields (filter #(empty? (get user-data %)) required-fields)]
      (when (seq missing-fields)
        (throw (ex-info "Missing required fields" {:missing missing-fields :user-data user-data}))))
    
    ;; Attempt to create user
    (let [created-user (api/create-user user-data)]
      (println "✓ User created successfully:")
      (println (format "  ID: %s" (:id created-user)))
      (println (format "  Name: %s %s" (:firstName created-user) (:lastName created-user)))
      (println (format "  Email: %s" (:email created-user)))
      (when (:title created-user)
        (println (format "  Title: %s" (:title created-user))))
      (println (format "  Role ID: %s" (:roleId created-user)))
      (println (format "  Team IDs: %s" (:teamIds created-user)))
      created-user)
    (catch Exception e
      (println "✗ Failed to create user:")
      (println (.getMessage e))
      nil)))

(defn test-create-team
  "Test creating a team with the backend API."
  [team-data]
  (println (format "\n=== Create Team Test (%s) ===" (:name team-data)))
  (try
    ;; Validate required data
    (let [required-fields [:name :members :escalationLevels]
          missing-fields (filter #(empty? (get team-data %)) required-fields)]
      (when (seq missing-fields)
        (throw (ex-info "Missing required fields" {:missing missing-fields :team-data team-data}))))
    
    ;; Attempt to create team
    (let [created-team (api/create-team team-data)]
      (println "✓ Team created successfully:")
      (println (format "  ID: %s" (:id created-team)))
      (println (format "  Name: %s" (:name created-team)))
      (println (format "  Members: %s" (:members created-team)))
      (println (format "  Escalation Levels: %d" (count (:escalationLevels created-team))))
      created-team)
    (catch Exception e
      (println "✗ Failed to create team:")
      (println (.getMessage e))
      nil)))

(defn test-validation
  "Test input validation for user and team creation."
  []
  (println "\n=== Validation Test ===")
  
  ;; Test user validation
  (println "Testing user validation...")
  (try
    (api/create-user {:firstName "John"}) ; Missing required fields
    (println "✗ User validation failed - should have rejected incomplete data")
    false)
    (catch Exception e
      (println "✓ User validation works - rejected incomplete data")
      true))
  
  ;; Test team validation
  (println "Testing team validation...")
  (try
    (api/create-team {:name "Test Team"}) ; Missing required fields
    (println "✗ Team validation failed - should have rejected incomplete data")
    false)
    (catch Exception e
      (println "✓ Team validation works - rejected incomplete data")
      true)))

(defn test-user-creation-workflow
  "Test complete user creation workflow with dependency resolution."
  []
  (println "\n=== User Creation Workflow Test ===")
  
  ;; Ensure authentication
  (when-not (api/authenticated?)
    (println "Authenticating...")
    (let [login-result (api/login-with-config)]
      (when-not (:success login-result)
        (println "Login failed, enabling mock mode...")
        (api/enable-mock-mode!)
        (api/login "test@example.com" "test-password"))))
  
  ;; Step 1: Get available roles and teams
  (println "Step 1: Fetching available roles and teams...")
  (try
    (let [all-data (api/get-all-data)
          roles (:roles all-data)
          teams (:teams all-data)
          users (:users all-data)]
      
      (println (format "  Available roles: %d" (count roles)))
      (println (format "  Available teams: %d" (count teams)))
      (println (format "  Existing users: %d" (count users)))
      
      ;; Step 2: Select role and team for test user
      (if (and (seq roles) (seq teams))
        (let [test-role (first roles)
              test-team (first teams)
              test-user-email (str "test-user-" (System/currentTimeMillis) "@example.com")
              test-user-data {:firstName "Test"
                              :lastName "User"
                              :email test-user-email
                              :title "Test Engineer"
                              :mobileNumbers [{:number "+1234567890" :isActive true}]
                              :teamIds [(:id test-team)]
                              :defaultTeam (:id test-team)
                              :roleId (:id test-role)}]
          
          (println "\nStep 2: Creating test user...")
          (println (format "  Role: %s (ID: %s)" (:name test-role) (:id test-role)))
          (println (format "  Team: %s (ID: %s)" (:name test-team) (:id test-team)))
          
          ;; Check if user already exists
          (let [existing-user (api/find-user-by-email test-user-email)]
            (if existing-user
              (do
                (println (format "✓ User already exists: %s" test-user-email))
                existing-user)
              (test-create-user test-user-data))))
        
        (do
          (println "✗ Cannot create user - no roles or teams available")
          nil)))
    (catch Exception e
      (println "✗ User creation workflow failed:")
      (println (.getMessage e))
      nil)))

(defn test-team-creation-workflow
  "Test complete team creation workflow."
  []
  (println "\n=== Team Creation Workflow Test ===")
  
  ;; Ensure authentication
  (when-not (api/authenticated?)
    (println "Authenticating...")
    (let [login-result (api/login-with-config)]
      (when-not (:success login-result)
        (println "Login failed, enabling mock mode...")
        (api/enable-mock-mode!)
        (api/login "test@example.com" "test-password"))))
  
  ;; Step 1: Get available users for team membership
  (println "Step 1: Fetching available users for team membership...")
  (try
    (let [all-data (api/get-all-data)
          users (:users all-data)
          teams (:teams all-data)]
      
      (println (format "  Available users: %d" (count users)))
      (println (format "  Existing teams: %d" (count teams)))
      
      ;; Step 2: Create test team
      (if (seq users)
        (let [test-user (first users)
              test-team-name (str "Test Team " (System/currentTimeMillis))
              test-team-data {:name test-team-name
                              :members [(:id test-user)]
                              :escalationLevels [{:minutes 180
                                                  :escalationContacts [(:id test-user)]}]}]
          
          (println "\nStep 2: Creating test team...")
          (println (format "  Team name: %s" test-team-name))
          (println (format "  Member: %s %s (ID: %s)" 
                           (:firstName test-user) 
                           (:lastName test-user)
                           (:id test-user)))
          
          ;; Check if team already exists
          (let [existing-team (api/find-team-by-name test-team-name)]
            (if existing-team
              (do
                (println (format "✓ Team already exists: %s" test-team-name))
                existing-team)
              (test-create-team test-team-data))))
        
        (do
          (println "✗ Cannot create team - no users available for membership")
          nil)))
    (catch Exception e
      (println "✗ Team creation workflow failed:")
      (println (.getMessage e))
      nil)))

(defn test-creation-integration
  "Test creating a user and then a team with that user."
  []
  (println "\n=== Creation Integration Test ===")
  
  ;; This test creates a user first, then creates a team with that user
  (let [created-user (test-user-creation-workflow)]
    (when created-user
      (println "\nCreating team with the new user...")
      (let [integration-team-name (str "Integration Team " (System/currentTimeMillis))
            integration-team-data {:name integration-team-name
                                   :members [(:id created-user)]
                                   :escalationLevels [{:minutes 240
                                                       :escalationContacts [(:id created-user)]}]}]
        (test-create-team integration-team-data)))))

(defn run-all-tests
  "Run all creation tests."
  []
  (println "Starting backend API creation tests...")
  (println "⚠️  WARNING: These tests may create actual users/teams in the backend!")
  (println "Use mock mode if you don't want to create real data.")
  
  (let [results [(test-validation)
                 (not (nil? (test-user-creation-workflow)))
                 (not (nil? (test-team-creation-workflow)))]]
    (println "\n=== Test Results ===")
    (let [passed (count (filter true? results))
          total (count results)]
      (println (format "Passed: %d/%d tests" passed total))
      (if (= passed total)
        (println "✓ All creation tests passed!")
        (println "✗ Some creation tests failed."))
      (= passed total))))

(defn run-mock-tests
  "Run creation tests in mock mode."
  []
  (println "Starting mock creation tests...")
  (println "⚠️  Using mock mode - no real data will be created.")
  
  ;; Enable mock mode
  (api/enable-mock-mode!)
  (api/login "test@example.com" "test-password")
  
  (let [results [(test-validation)
                 (not (nil? (test-user-creation-workflow)))
                 (not (nil? (test-team-creation-workflow)))]]
    (println "\n=== Mock Test Results ===")
    (let [passed (count (filter true? results))
          total (count results)]
      (println (format "Passed: %d/%d mock tests" passed total))
      (if (= passed total)
        (println "✓ All mock creation tests passed!")
        (println "✗ Some mock creation tests failed."))
      (= passed total))))

(defn run-safe-tests
  "Run only validation and workflow tests without actual creation."
  []
  (println "Starting safe creation tests (validation only)...")
  
  (let [results [(test-validation)]]
    (println "\n=== Safe Test Results ===")
    (let [passed (count (filter true? results))
          total (count results)]
      (println (format "Passed: %d/%d safe tests" passed total))
      (if (= passed total)
        (println "✓ All safe tests passed!")
        (println "✗ Some safe tests failed."))
      (= passed total))))

;; For REPL usage
(comment
  ;; Run safe tests only (no creation)
  (run-safe-tests)
  
  ;; Run mock tests (fake creation)
  (run-mock-tests)
  
  ;; Run all tests (WARNING: creates real data!)
  (run-all-tests)
  
  ;; Test individual workflows
  (test-user-creation-workflow)
  (test-team-creation-workflow)
  (test-creation-integration)
  
  ;; Test validation
  (test-validation)
  
  ;; Enable mock mode for safe testing
  (api/enable-mock-mode!)
  (api/login "test@example.com" "test")
  
  ;; Manual user creation (with real data)
  (test-create-user {:firstName "John"
                     :lastName "Doe"
                     :email "john.doe.test@example.com"
                     :title "Software Engineer"
                     :mobileNumbers [{:number "+1234567890" :isActive true}]
                     :teamIds ["team-id-1"]
                     :roleId "role-id-1"})
  
  ;; Manual team creation (with real data)
  (test-create-team {:name "Test Engineering Team"
                     :members ["user-id-1"]
                     :escalationLevels [{:minutes 180
                                         :escalationContacts ["user-id-1"]}]})
  )