(ns user-upload.workflow.orchestrator
  "Main workflow orchestrator that coordinates the complete user upload process.
   
   This module handles:
   - Tenant authentication using extracted credentials
   - File processing and validation
   - Approval workflow management
   - User and team creation
   - Result aggregation and reporting"
  (:require [user-upload.log :as log]
            [user-upload.auth.tenant :as tenant]
            [user-upload.auth.onepassword :as op]
            [user-upload.api.client :as api]
            [user-upload.parser.excel :as excel]
            [user-upload.parser.csv :as csv]
            [user-upload.parser.normalize :as normalize]
            [user-upload.parser.validate :as validate]
            [user-upload.parser.document-analyzer :as analyzer]
            [user-upload.ai.intent :as intent]
            [user-upload.ai.mapping :as mapping]
            [user-upload.ai.sheet-detector :as sheet-detector]
            [user-upload.workflow.approval :as approval]
            [user-upload.jira.client :as jira]
            [clojure.string :as str]))

(defn extract-tenant-and-authenticate
  "Extract tenant from issue and authenticate using 1Password credentials.
   
   Args:
     issue - Jira issue map
     email-template - Template for service account email (e.g., 'customersolutions+%s@jesi.io')
   
   Returns:
     Map with keys:
       :success - Boolean indicating if authentication succeeded
       :tenant - Tenant name (if successful)
       :credentials - Auth credentials (if successful)
       :error - Error message (if failed)"
  [issue email-template]
  (try
    (log/info "Extracting tenant and authenticating" {:issue-key (:key issue)})
    
    ;; Extract tenant from issue
    (let [tenant-result (tenant/extract-and-validate-tenant issue)]
      (if (:error tenant-result)
        {:success false :error (:error tenant-result)}
        
        (let [tenant-name (:tenant tenant-result)]
          (log/info "Extracted tenant" {:tenant tenant-name})
          
          ;; Get credentials from 1Password
          (let [cred-result (op/get-tenant-credentials tenant-name email-template)]
            (if (:success cred-result)
              (do
                (log/info "Retrieved credentials for tenant" {:tenant tenant-name 
                                                             :cached (:cached cred-result)
                                                             :multiple-items (:multiple-items-found cred-result)
                                                             :item-used (:item-used cred-result)})
                
                ;; Attempt to authenticate with backend API
                (let [login-result (api/login (:email cred-result) (:password cred-result))]
                  (if (:success login-result)
                    {:success true
                     :tenant tenant-name
                     :credentials {:email (:email cred-result)
                                  :password "***hidden***"}
                     :multiple-items-found (:multiple-items-found cred-result)
                     :item-used (:item-used cred-result)}
                    {:success false 
                     :tenant tenant-name  ; Include tenant in failure response
                     :error (str "Backend authentication failed: " (:error login-result))})))
              
              {:success false 
               :tenant tenant-name  ; Include tenant in failure response
               :error (str "Failed to retrieve credentials: " (:error cred-result))})))))
    
    (catch Exception e
      (log/error e "Error during tenant extraction and authentication")
      {:success false :error (.getMessage e)})))

(defn download-and-parse-attachment
  "Download and parse an attachment from Jira.
   
   For Excel files with multiple sheets or complex structure, uses AI to detect
   which sheet contains user data and where the headers/data start.
   
   Args:
     attachment - Jira attachment map with :content and :filename
   
   Returns:
     Map with keys:
       :success - Boolean indicating if parsing succeeded
       :data - Parsed data rows (if successful)
       :headers - Original headers from file
       :error - Error message (if failed)"
  [attachment]
  (try
    (let [filename (:filename attachment)
          content (:content attachment)]
      
      (log/info "Parsing attachment" {:filename filename :size (count content)})
      
      (cond
        ;; Excel files - may need sheet detection
        (re-matches #".*\.(xlsx|xls)$" (str/lower-case filename))
        (let [;; First try simple parsing of first sheet
              simple-result (excel/parse-excel-bytes content)]
          
          (if (and (:success simple-result)
                   (:headers simple-result)
                   (> (count (:headers simple-result)) 5)) ; Likely has all columns
            ;; Simple mode: first sheet looks good
            {:success true
             :data (:data simple-result)
             :headers (:headers simple-result)}
            
            ;; Complex mode: need AI to find the right sheet
            (do
              (log/info "First sheet doesn't have enough columns, using AI sheet detection")
              (let [analysis (analyzer/analyze-excel-bytes content filename)
                    _ (log/info "Excel analysis complete" {:sheets (count (:sheets analysis))
                                                          :error (:error analysis)})
                    detection-result (sheet-detector/detect-user-data-sheet analysis)]
                
                (log/info "Sheet detection result" 
                         {:success (:success detection-result)
                          :error (:error detection-result)
                          :sheet-name (:sheet-name detection-result)})
                
                (if (:success detection-result)
                  ;; Parse the detected sheet with correct parameters
                  (let [temp-file (java.io.File/createTempFile "excel-" ".xlsx")]
                    (try
                      (clojure.java.io/copy content temp-file)
                      (let [workbook (excel/load-workbook (.getAbsolutePath temp-file))
                            worksheet (excel/select-sheet workbook (:sheet-name detection-result))
                            raw-data (excel/sheet-data worksheet)
                            ;; Extract headers from detected row
                            headers (vec (map excel/normalize-cell-value 
                                            (nth raw-data (:header-row detection-result))))
                            ;; Extract data starting from detected row
                            data-rows (drop (:data-start-row detection-result) raw-data)
                            ;; Create maps with proper headers
                            parsed-data (mapv (fn [row]
                                              (zipmap headers
                                                     (mapv excel/normalize-cell-value row)))
                                            data-rows)]
                        
                        (log/info "Successfully parsed detected sheet" 
                                 {:sheet (:sheet-name detection-result)
                                  :headers headers
                                  :data-count (count parsed-data)})
                        
                        {:success true
                         :data parsed-data
                         :headers headers
                         :detected-sheet (:sheet-name detection-result)
                         :header-row (:header-row detection-result)
                         :data-start-row (:data-start-row detection-result)})
                      
                      (finally
                        (.delete temp-file))))
                  
                  ;; Sheet detection failed
                  (do
                    (log/error "Sheet detection failed" {:error (:error detection-result)
                                                        :raw-response (:raw-response detection-result)})
                    {:success false
                     :error (str "Could not detect user data sheet: " (:error detection-result))}))))))
        
        ;; CSV files - simple parsing
        (re-matches #".*\.csv$" (str/lower-case filename))
        (let [result (csv/parse-csv-bytes content)]
          (if (:success result)
            {:success true
             :data (:data result)
             :headers (:headers result)}
            {:success false :error (:error result)}))
        
        ;; Unsupported format
        :else
        {:success false 
         :error (str "Unsupported file format: " filename ". Only .xlsx, .xls, and .csv files are supported.")}))
    
    (catch Exception e
      (log/error e "Error parsing attachment" {:filename (:filename attachment)})
      {:success false :error (.getMessage e)})))

(defn normalize-and-validate-data
  "Normalize headers and validate data using AI-assisted column mapping.
   
   Args:
     headers - Original column headers from file
     data - Raw data rows
     use-ai? - Whether to use AI for column mapping (default true)
   
   Returns:
     Map with keys:
       :success - Boolean indicating if validation succeeded
       :validation-result - Full validation result from validate/validate-dataset
       :valid-data - Normalized valid rows ready for upload
       :invalid-data - Invalid rows with error details
       :mapping-used - Column mapping that was applied
       :error - Error message (if failed)"
  [headers data & {:keys [use-ai?] :or {use-ai? true}}]
  (try
    (log/info "Normalizing headers and validating data" 
              {:header-count (count headers) 
               :row-count (count data)
               :use-ai use-ai?})
    
    ;; First normalize the headers to expected format
    (let [expected-fields ["email" "first name" "last name" "job title" "mobile number" "teams" "user role"]
          
          ;; Check if headers exactly match expected fields (simple mode)
          headers-match? (= (set (map str/lower-case headers))
                           (set expected-fields))
          
          final-mapping (if headers-match?
                         ;; Simple mode: exact match, no AI needed
                         (do
                           (log/info "Headers match expected fields exactly, using direct mapping")
                           ;; Create identity-to-lower mapping for each header
                           (into {} (map (fn [h] [(str h) (str/lower-case (str h))]) headers)))
                         
                         ;; Complex mode: use AI for mapping
                         (if use-ai?
                           (let [mapping-result (mapping/map-columns expected-fields headers)]
                             (if (:success mapping-result)
                               (do
                                 (log/info "AI column mapping successful" 
                                          {:mapped (count (:mapping mapping-result))
                                           :unmapped (count (:unmapped mapping-result))})
                                 (:mapping mapping-result))
                               (do
                                 (log/error "AI column mapping failed" {:error (:error mapping-result)})
                                 {})))
                           (do
                             (log/warn "AI disabled and headers don't match - cannot process")
                             {})))
          
          ;; Apply the mapping to normalize data
          normalized-data (normalize/apply-header-mapping data final-mapping)]
      
      (log/info "Applied column mapping" {:mapping final-mapping})
      
      ;; Log sample of normalized data for debugging
      (when (seq normalized-data)
        (log/debug "Sample normalized data (first row):" (first normalized-data)))
      
      ;; Validate the normalized data
      (let [validation-result (validate/validate-dataset normalized-data)]
        (if (:valid? validation-result)
          {:success true
           :validation-result validation-result
           :valid-data (validate/get-valid-rows validation-result)
           :invalid-data []
           :mapping-used final-mapping}
          
          (let [valid-data (validate/get-valid-rows validation-result)
                invalid-data (validate/get-invalid-rows validation-result)]
            (log/warn "Data validation found issues" 
                     {:valid-rows (count valid-data)
                      :invalid-rows (count invalid-data)})
            
            ;; Return partial success if we have some valid data
            (if (> (count valid-data) 0)
              {:success true
               :validation-result validation-result
               :valid-data valid-data
               :invalid-data invalid-data
               :mapping-used final-mapping
               :warnings (validate/get-validation-summary validation-result)}
              
              {:success false
               :error "No valid rows found after validation"
               :validation-result validation-result
               :mapping-used final-mapping})))))
    
    (catch Exception e
      (log/error e "Error during data normalization and validation")
      {:success false :error (.getMessage e)})))

(defn fetch-backend-data
  "Fetch all required backend data for user/team operations.
   
   Returns:
     Map with keys:
       :success - Boolean indicating if fetch succeeded
       :users - Existing users list
       :teams - Existing teams list  
       :roles - Available roles list
       :error - Error message (if failed)"
  []
  (try
    (log/info "Fetching backend data for upload operations")
    (let [data (api/get-all-data)]
      {:success true
       :users (:users data)
       :teams (:teams data)
       :roles (:roles data)})
    
    (catch Exception e
      (log/error e "Error fetching backend data")
      {:success false :error (.getMessage e)})))

(defn create-missing-teams
  "Create any teams that don't exist in the backend.
   
   Args:
     valid-data - Validated user data rows
     existing-teams - List of existing teams from backend
   
   Returns:
     Map with keys:
       :success - Boolean indicating if all team operations succeeded
       :created-teams - List of newly created teams
       :existing-teams - List of teams that already existed
       :failed-teams - List of teams that failed to create
       :team-map - Map of team-name -> team-id for all teams"
  [valid-data existing-teams]
  (try
    (log/info "Creating missing teams")
    
    ;; Extract all unique team names from the data
    (let [all-team-names (set (mapcat :teams valid-data))
          existing-team-names (set (map :name existing-teams))
          missing-team-names (clojure.set/difference all-team-names existing-team-names)
          
          ;; Create map of existing teams
          existing-team-map (into {} (map #(vector (:name %) (:id %)) existing-teams))
          
          created-teams (atom [])
          failed-teams (atom [])]
      
      (log/info "Team analysis" 
               {:total-teams (count all-team-names)
                :existing-teams (count existing-team-names)
                :missing-teams (count missing-team-names)})
      
      ;; Create missing teams
      (doseq [team-name missing-team-names]
        (try
          (log/info "Creating team" {:name team-name})
          
          ;; Create team with minimal required structure
          (let [team-data {:name team-name
                          :members [] ; Will be populated when users are created
                          :escalationLevels [{:minutes 60
                                            :escalationContacts []}]}
                result (api/create-team team-data)]
            
            (if (:id result)
              (do
                (log/info "Successfully created team" {:name team-name :id (:id result)})
                (swap! created-teams conj result))
              (do
                (log/error "Team creation returned no ID" {:name team-name :result result})
                (swap! failed-teams conj {:name team-name :error "No ID returned"}))))
          
          (catch Exception e
            (log/error e "Failed to create team" {:name team-name})
            (swap! failed-teams conj {:name team-name :error (.getMessage e)}))))
      
      ;; Build final team map
      (let [created-team-map (into {} (map #(vector (:name %) (:id %)) @created-teams))
            final-team-map (merge existing-team-map created-team-map)]
        
        {:success (empty? @failed-teams)
         :created-teams @created-teams
         :existing-teams existing-teams
         :failed-teams @failed-teams
         :team-map final-team-map}))
    
    (catch Exception e
      (log/error e "Error during team creation")
      {:success false :error (.getMessage e)})))

(defn upload-users
  "Upload validated users to the backend.
   
   Args:
     valid-data - Validated user data rows
     team-map - Map of team-name -> team-id
     available-roles - List of available roles from backend
   
   Returns:
     Map with keys:
       :success - Boolean indicating if any users were uploaded
       :created-users - List of successfully created users
       :existing-users - List of users that already existed
       :failed-users - List of users that failed to create
       :total-processed - Total number of users processed"
  [valid-data team-map available-roles]
  (try
    (log/info "Uploading users" {:count (count valid-data)})
    
    ;; Create role name -> role id mapping
    (let [role-map (into {} (map #(vector (:name %) (:id %)) available-roles))
          
          created-users (atom [])
          existing-users (atom [])
          failed-users (atom [])]
      
      ;; Process each user
      (doseq [user-data valid-data]
        (try
          (let [email (:email user-data)
                existing-user (api/find-user-by-email email)]
            
            (if existing-user
              (do
                (log/info "User already exists" {:email email})
                (swap! existing-users conj {:email email :id (:id existing-user)}))
              
              ;; Create new user
              (let [;; Map team names to team IDs
                    team-ids (keep #(get team-map %) (:teams user-data))
                    
                    ;; Get role ID
                    role-id (get role-map (:user-role user-data))
                    
                    ;; Build user creation payload
                    user-payload {:firstName (:first-name user-data)
                                 :lastName (:last-name user-data)
                                 :email email
                                 :title (:job-title user-data)
                                 :mobileNumbers [{:number (:mobile-number user-data)
                                                :isActive true}]
                                 :teamIds team-ids
                                 :defaultTeam (first team-ids)
                                 :roleId role-id}]
                
                (if (and role-id (seq team-ids))
                  (let [result (api/create-user user-payload)]
                    (if (:id result)
                      (do
                        (log/info "Successfully created user" {:email email :id (:id result)})
                        (swap! created-users conj result))
                      (do
                        (log/error "User creation returned no ID" {:email email :result result})
                        (swap! failed-users conj {:email email :error "No ID returned" :data user-data}))))
                  
                  (do
                    (log/error "Missing required data for user creation" 
                              {:email email :role-id role-id :team-ids team-ids})
                    (swap! failed-users conj {:email email 
                                             :error "Missing role ID or team IDs" 
                                             :data user-data}))))))
          
          (catch Exception e
            (log/error e "Failed to process user" {:email (:email user-data)})
            (swap! failed-users conj {:email (:email user-data) 
                                     :error (.getMessage e) 
                                     :data user-data}))))
      
      (let [total-processed (+ (count @created-users) (count @existing-users) (count @failed-users))]
        (log/info "User upload complete" 
                 {:total-processed total-processed
                  :created (count @created-users)
                  :existing (count @existing-users)
                  :failed (count @failed-users)})
        
        {:success (> (+ (count @created-users) (count @existing-users)) 0)
         :created-users @created-users
         :existing-users @existing-users
         :failed-users @failed-users
         :total-processed total-processed}))
    
    (catch Exception e
      (log/error e "Error during user upload")
      {:success false :error (.getMessage e)})))

(defn- proceed-with-upload
  "Helper function to proceed with team creation and user upload.
   
   Args:
     validation-result - Result from normalize-and-validate-data
     parse-result - Result from download-and-parse-attachment  
     tenant - Tenant name
     filename - Attachment filename
   
   Returns:
     Processing result map"
  [validation-result parse-result tenant filename]
  ;; Fetch backend data
  (let [backend-result (fetch-backend-data)]
    (if-not (:success backend-result)
      {:success false
       :filename filename
       :error (:error backend-result)
       :summary (str "Failed to fetch backend data: " (:error backend-result))}
      
      ;; Create missing teams
      (let [team-result (create-missing-teams 
                        (:valid-data validation-result)
                        (:teams backend-result))]
        (if-not (:success team-result)
          {:success false
           :filename filename
           :error (:error team-result)
           :summary (str "Failed to create teams: " (:error team-result))}
          
          ;; Upload users
          (let [upload-result (upload-users
                              (:valid-data validation-result)
                              (:team-map team-result)
                              (:roles backend-result))]
            
            {:success (:success upload-result)
             :filename filename
             :approval-status :not-needed
             :results {:parsing parse-result
                      :validation validation-result
                      :backend backend-result
                      :teams team-result
                      :upload upload-result}
             :summary (if (:success upload-result)
                       (str "Successfully processed " filename ": "
                            (count (:created-users upload-result)) " users created, "
                            (count (:existing-users upload-result)) " users existed, "
                            (count (:failed-users upload-result)) " users failed")
                       (str "Upload failed for " filename ": " (:error upload-result)))}))))))

(defn process-attachment
  "Complete workflow to process a single attachment.
   
   Args:
     attachment - Jira attachment map with :content
     tenant - Tenant name for logging context
     ticket-key - Jira ticket key
     ticket - Full Jira ticket object
     credentials-found - Boolean indicating if credentials were found in 1Password
   
   Returns:
     Map with keys:
       :success - Boolean indicating overall success
       :filename - Attachment filename
       :results - Detailed results of each processing step
       :summary - Human-readable summary
       :approval-status - :requested, :approved, :not-needed, :invalid
       :error - Error message (if failed)"
  [attachment tenant ticket-key ticket & [credentials-found]]
  (try
    (let [filename (:filename attachment)
          ticket-status (get-in ticket [:fields :status :name])]
      (log/info "Processing attachment" {:filename filename :tenant tenant :ticket-key ticket-key :status ticket-status})
      
      ;; Step 1: Parse the file
      (let [parse-result (download-and-parse-attachment attachment)]
        (if-not (:success parse-result)
          {:success false
           :filename filename
           :error (:error parse-result)
           :summary (str "Failed to parse " filename ": " (:error parse-result))}
          
          ;; Step 2: Normalize and validate data
          (let [validation-result (normalize-and-validate-data 
                                  (:headers parse-result) 
                                  (:data parse-result))]
            (if-not (:success validation-result)
              {:success false
               :filename filename
               :error (:error validation-result)
               :summary (str "Failed to validate " filename ": " (:error validation-result))}
              
              ;; Step 3: Check if approval is needed
              (let [approval-required? (approval/workflow-approval-required? validation-result parse-result)
                    ;; Calculate attachment fingerprint
                    attachment-with-fingerprint (approval/calculate-attachment-fingerprint attachment)]
                
                (log/info "Approval check" {:required approval-required? :ticket-status ticket-status})
                
                (cond
                  ;; Case 1: Ticket is in Review status - check for existing approval
                  (= ticket-status "Review")
                  (let [approval-check (approval/check-workflow-approval 
                                       ticket-key 
                                       [attachment-with-fingerprint])]
                    (if (:approval-valid approval-check)
                      ;; Approval is valid - proceed with upload
                      (proceed-with-upload validation-result parse-result tenant filename)
                      ;; Approval is invalid or pending
                      {:success false
                       :filename filename
                       :approval-status (:status approval-check)
                       :error (:message approval-check)
                       :summary (str "Approval status for " filename ": " (:message approval-check))}))
                  
                  ;; Case 2: Approval required and ticket is Open - request approval
                  (and approval-required? (= ticket-status "Open"))
                  (let [;; Prepare extra info for approval request
                        extra-info {:column-mapping (:mapping-used validation-result)
                                   :tenant-email (str "customersolutions+" tenant "@jesi.io")
                                   :credentials-found (if (nil? credentials-found) true credentials-found)
                                   :sheet-detected (:detected-sheet parse-result)}
                        approval-request (approval/request-workflow-approval
                                        ticket-key
                                        tenant
                                        (:valid-data validation-result)
                                        [attachment-with-fingerprint]
                                        extra-info)
                        ;; Transition ticket to Review status
                        transition-result (try
                                          (jira/transition-issue ticket-key "Review")
                                          (catch Exception e
                                            (log/warn "Failed to transition ticket" {:ticket-key ticket-key :error (.getMessage e)})
                                            nil))]
                    (if (:success approval-request)
                      {:success true  ; Not a failure - approval was successfully requested
                       :filename filename
                       :approval-status :requested
                       :summary (str "Approval requested for " filename ". "
                                   "Please review and reply 'approved' to proceed.")}
                      {:success false
                       :filename filename
                       :approval-status :error
                       :error (:error approval-request)
                       :summary (str "Failed to request approval for " filename ": " 
                                   (:message approval-request))}))
                  
                  ;; Case 3: No approval needed - proceed directly
                  (not approval-required?)
                  (proceed-with-upload validation-result parse-result tenant filename)
                  
                  ;; Case 4: Edge case - approval required but unexpected status
                  :else
                  {:success false
                   :filename filename
                   :approval-status :error
                   :error "Unexpected ticket status for approval workflow"
                   :summary (str "Cannot process " filename ": ticket status is " ticket-status 
                               " but approval is required")})))))))
    
    (catch Exception e
      (log/error e "Error processing attachment" {:filename (:filename attachment)})
      {:success false
       :filename (:filename attachment)
       :error (.getMessage e)
       :summary (str "Unexpected error processing " (:filename attachment) ": " (.getMessage e))})))

(comment
  ;; Example usage
  
  ;; Test tenant extraction and authentication
  (extract-tenant-and-authenticate 
    {:key "TEST-123"
     :fields {:description "Please upload users for customersolutions+acme@jesi.io"}}
    "customersolutions+%s@jesi.io")
  
  ;; Test complete attachment processing
  (process-attachment
    {:filename "users.csv"
     :content (slurp "path/to/users.csv")}
    "acme"
    "TEST-123"
    {:fields {:status {:name "Open"}}}))