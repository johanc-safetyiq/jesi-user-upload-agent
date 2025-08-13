(ns exploration.workflow.full-integration-test
  "Full integration test using JESI-5928 as test ticket"
  (:require [user-upload.workflow.processor :as processor]
            [user-upload.workflow.orchestrator :as orchestrator]
            [user-upload.auth.tenant :as tenant]
            [user-upload.auth.onepassword :as op]
            [user-upload.jira.client :as jira]
            [user-upload.api.client :as api]
            [user-upload.config :as config]
            [clojure.pprint :as pprint]))

(def test-ticket-key "JESI-5928")

(defn test-prerequisites
  "Test all prerequisites for full workflow"
  []
  (println "=== Testing Prerequisites ===\n")
  
  ;; Test configuration
  (println "1. Configuration:")
  (try
    (let [cfg (config/config)]
      (println "✅ Configuration loaded successfully")
      (println "   Jira domain:" (get-in cfg [:jira :domain]))
      (println "   API base URL:" (get-in cfg [:api :base-url])))
    (catch Exception e
      (println "❌ Configuration error:" (.getMessage e))))
  (println)
  
  ;; Test 1Password CLI
  (println "2. 1Password CLI:")
  (let [op-check (op/check-op-availability)]
    (if (:available op-check)
      (if (:authenticated op-check)
        (println "✅ 1Password CLI available and authenticated")
        (println "⚠️  1Password CLI available but not authenticated"))
      (println "❌ 1Password CLI not available:" (:error op-check))))
  (println)
  
  ;; Test Jira connection
  (println "3. Jira connection:")
  (try
    (let [jira-test (jira/test-connection)]
      (if (:success jira-test)
        (println "✅ Jira connection successful. User:" (get-in jira-test [:user :displayName]))
        (println "❌ Jira connection failed:" (:error jira-test))))
    (catch Exception e
      (println "❌ Jira connection error:" (.getMessage e))))
  (println))

(defn test-ticket-fetching
  "Test fetching the specific test ticket"
  []
  (println "=== Testing Ticket Fetching ===\n")
  
  (try
    (let [ticket (jira/get-issue-with-attachments test-ticket-key)]
      (println "✅ Successfully fetched ticket:" test-ticket-key)
      (println "   Summary:" (get-in ticket [:fields :summary]))
      (println "   Status:" (get-in ticket [:fields :status :name]))
      (println "   Attachments:" (count (get-in ticket [:fields :attachment] [])))
      
      ;; List attachment details
      (doseq [attachment (get-in ticket [:fields :attachment] [])]
        (println (format "     - %s (%d bytes)" 
                        (:filename attachment) 
                        (:size attachment))))
      (println)
      ticket)
    (catch Exception e
      (println "❌ Error fetching ticket:" (.getMessage e))
      nil)))

(defn test-tenant-extraction
  "Test tenant extraction from the test ticket"
  [ticket]
  (when ticket
    (println "=== Testing Tenant Extraction ===\n")
    
    (let [result (tenant/extract-and-validate-tenant ticket)]
      (if (:error result)
        (println "❌ Tenant extraction failed:" (:error result))
        (println "✅ Tenant extracted:" (:tenant result)))
      (println)
      result)))

(defn test-attachment-processing
  "Test downloading and processing attachments"
  [ticket]
  (when ticket
    (println "=== Testing Attachment Processing ===\n")
    
    (let [attachments (get-in ticket [:fields :attachment] [])]
      (if (empty? attachments)
        (println "⚠️  No attachments found in ticket")
        (doseq [attachment attachments]
          (println "Processing attachment:" (:filename attachment))
          (try
            (let [download-result (processor/download-attachment attachment)]
              (if (:success download-result)
                (do
                  (println "✅ Download successful, size:" (count (:content download-result)))
                  ;; Test parsing
                  (let [attachment-with-content (assoc attachment :content (:content download-result))
                        parse-result (orchestrator/download-and-parse-attachment attachment-with-content)]
                    (if (:success parse-result)
                      (println "✅ Parsing successful, rows:" (count (:data parse-result)))
                      (println "❌ Parsing failed:" (:error parse-result)))))
                (println "❌ Download failed:" (:error download-result))))
            (catch Exception e
              (println "❌ Error processing attachment:" (.getMessage e))))
          (println))))))

(defn test-mock-backend-integration
  "Test backend integration in mock mode"
  []
  (println "=== Testing Backend Integration (Mock Mode) ===\n")
  
  (try
    ;; Enable mock mode
    (api/enable-mock-mode!)
    (println "✅ Mock mode enabled")
    
    ;; Test login
    (let [login-result (api/login "test@example.com" "test-password")]
      (if (:success login-result)
        (println "✅ Mock login successful")
        (println "❌ Mock login failed:" (:error login-result))))
    
    ;; Test data fetching
    (let [data (api/get-all-data)]
      (println "✅ Mock data fetched:")
      (println "   Users:" (count (:users data)))
      (println "   Teams:" (count (:teams data)))
      (println "   Roles:" (count (:roles data))))
    
    (println)
    
    (catch Exception e
      (println "❌ Backend integration error:" (.getMessage e))))

(defn test-jql-building
  "Test JQL query building"
  []
  (println "=== Testing JQL Building ===\n")
  
  (let [cfg (config/config)
        jql (processor/build-jql-query cfg)]
    (println "Generated JQL:")
    (println jql)
    (println)
    
    ;; Test the JQL by running it
    (try
      (let [search-result (jira/search-issues {:jql jql :max-results 5})]
        (println "✅ JQL executed successfully")
        (println "   Found tickets:" (count (:issues search-result)))
        (doseq [issue (:issues search-result)]
          (println (format "     - %s: %s" 
                          (:key issue) 
                          (get-in issue [:fields :summary]))))
        (println))
      (catch Exception e
        (println "❌ JQL execution failed:" (.getMessage e))))))

(defn test-single-ticket-processing
  "Test processing a single specific ticket"
  []
  (println "=== Testing Single Ticket Processing ===\n")
  
  (try
    (let [ticket (jira/get-issue-with-attachments test-ticket-key)
          cfg (config/config)]
      
      (if ticket
        (let [result (processor/process-single-ticket ticket cfg)]
          (println "Processing result:")
          (pprint/pprint (dissoc result :results)) ; Exclude detailed results for readability
          (println)
          
          (if (:success result)
            (println "✅ Ticket processing successful")
            (println "❌ Ticket processing failed:" (:error result))))
        (println "❌ Could not fetch test ticket")))
    
    (catch Exception e
      (println "❌ Error in ticket processing:" (.getMessage e)))))

(defn run-integration-test
  "Run complete integration test"
  []
  (println "🚀 FULL INTEGRATION TEST")
  (println "========================\n")
  
  ;; Step 1: Prerequisites
  (test-prerequisites)
  
  ;; Step 2: Ticket operations
  (let [ticket (test-ticket-fetching)]
    
    ;; Step 3: Tenant extraction
    (test-tenant-extraction ticket)
    
    ;; Step 4: Attachment processing
    (test-attachment-processing ticket))
  
  ;; Step 5: Backend integration
  (test-mock-backend-integration)
  
  ;; Step 6: JQL building
  (test-jql-building)
  
  ;; Step 7: Single ticket processing
  (test-single-ticket-processing)
  
  (println "🏁 INTEGRATION TEST COMPLETE")
  (println "============================"))

(defn run-quick-test
  "Run a quick subset of tests"
  []
  (println "⚡ QUICK INTEGRATION TEST")
  (println "========================\n")
  
  (test-prerequisites)
  (test-jql-building)
  
  (println "⚡ QUICK TEST COMPLETE"))

(comment
  ;; Run full integration test
  (run-integration-test)
  
  ;; Run quick test
  (run-quick-test)
  
  ;; Test individual components
  (test-prerequisites)
  (test-ticket-fetching)
  (test-jql-building)
  )