# User Upload Agent - Current Capabilities

## What's Currently Implemented (Phases 1-4)

### 1. Configuration & Logging
- ✅ Auto-loads `.env` file (no need to source or export)
- ✅ Structured JSON logging for production monitoring

### 2. Jira Integration
```clojure
;; Search for tickets
(require '[user-upload.jira.client :as jira])

;; Get current user info
(jira/get-user-info)

;; Search for issues
(jira/search-issues "project = JESI AND status = Open")

;; Get issue details
(jira/get-issue "JESI-123")

;; Add a comment
(jira/add-comment "JESI-123" "Processing user upload request...")

;; Check approval status
(require '[user-upload.jira.approval :as approval])
(approval/check-approval-status "JESI-123")
```

### 3. File Processing
```clojure
(require '[user-upload.parser.csv :as csv]
         '[user-upload.parser.excel :as excel]
         '[user-upload.parser.validate :as validate])

;; Parse CSV file
(csv/parse-csv-file "users.csv")
;; Returns: [[headers...] [row1...] [row2...]]

;; Parse Excel file with multiple sheets
(excel/parse-excel-file "users.xlsx")
;; Returns: {:sheets [{:name "Sheet1" :data [[...]]}]}

;; Validate user data
(validate/validate-row 
  {:email "john@example.com"
   :first-name "John"
   :last-name "Doe"
   :user-role "MANAGER"
   :teams "Engineering|QA"
   :mobile-number ""
   :job-title "Tech Lead"}
  #{}) ; existing emails
;; Returns: {:valid? true} or {:valid? false :errors [...]}
```

### 4. AI Integration (Claude Code CLI)
```clojure
(require '[user-upload.ai.intent :as intent]
         '[user-upload.ai.mapping :as mapping])

;; Detect if ticket is about user uploads (with fallback heuristics)
(intent/detect-user-upload-intent 
  {:summary "Upload new users for Q1"
   :description "Please add these employees"
   :attachments [{:filename "users.csv"}]}
  {:use-ai false}) ; Can use AI or heuristics

;; Map CSV columns to expected schema
(mapping/map-columns 
  ["Email Address" "FirstName" "Surname" "Role" "Team Names"])
;; Returns: {:mapping {"Email Address" "email", ...} 
;;           :unmapped ["mobile number"]}
```

### 5. Backend API (Mock Mode Available)
```clojure
(require '[user-upload.api.client :as api])

;; Enable mock mode for testing
(api/enable-mock-mode!)

;; Authenticate
(api/login "customersolutions+tenant@jesi.io" "password")
;; Returns: auth token

;; Fetch data
(api/get-roles)        ; Get available roles
(api/search-users)     ; Get existing users
(api/search-teams)     ; Get existing teams

;; Create entities (not yet connected to real backend)
(api/create-user {...})
(api/create-team {...})
```

## Example: What We Can Do Right Now

```clojure
;; 1. Check if a Jira ticket needs processing
(let [ticket (jira/get-issue "JESI-5928")
      is-upload (intent/detect-user-upload-intent 
                  {:summary (:summary ticket)
                   :description (:description ticket)
                   :attachments (:attachments ticket)}
                  {:use-ai false})]
  (when is-upload
    (println "This ticket contains a user upload request!")))

;; 2. Process an attached CSV file
(let [csv-data (csv/parse-csv-file "path/to/users.csv")
      headers (first csv-data)
      rows (rest csv-data)]
  ;; Map columns to our schema
  (let [mapping (mapping/map-columns headers)]
    (println "Column mapping:" (:mapping mapping))
    ;; Validate each row
    (doseq [row rows]
      (let [user-map (zipmap headers row)
            validation (validate/validate-row user-map #{})]
        (if (:valid? validation)
          (println "Valid user:" (get user-map "email"))
          (println "Invalid:" (:errors validation)))))))

;; 3. Post results back to Jira
(jira/add-comment "JESI-5928" 
  "[BOT] Processed file. Found 10 valid users, 2 errors.")
```

## What's NOT Yet Implemented (Phases 5-7)

- ❌ Tenant extraction from tickets
- ❌ 1Password integration for credentials  
- ❌ Main processing loop that ties everything together
- ❌ Approval workflow automation
- ❌ Actual user creation in backend (only mock mode)
- ❌ Result reporting with CSV attachments
- ❌ Docker containerization
- ❌ Azure deployment

## Running the Application

Currently, you can test individual components:

```bash
# Load .env automatically and start REPL
clj -M:dev

# In REPL, require what you need:
(require '[user-upload.jira.client :as jira])
(require '[user-upload.parser.csv :as csv])
(require '[user-upload.ai.intent :as intent])

# Test Jira connection
(jira/get-user-info)

# Parse a CSV file
(csv/parse-csv-file "exploration/file-parsing/samples/sample-users.csv")

# Check if text indicates user upload
(intent/heuristic-intent-check 
  {:summary "Bulk user upload"
   :description "Please add these users"})
```

## Next Steps

Phase 5 will implement the main workflow that:
1. Queries Jira for tickets
2. Extracts tenant from each ticket
3. Gets credentials from 1Password
4. Processes attachments
5. Handles approval flow
6. Creates users in backend
7. Reports results back to Jira

This will tie all the current capabilities together into a working system!