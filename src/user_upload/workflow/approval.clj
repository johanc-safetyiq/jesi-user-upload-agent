(ns user_upload.workflow.approval
  "Workflow approval automation with attachment fingerprinting.
   
   This module extends the basic approval functionality with:
   - SHA-256 fingerprinting of attachments
   - Approval invalidation when files change
   - Integration with the processing workflow
   - Structured approval request generation"
  (:require [user_upload.log :as log]
            [user_upload.jira.approval :as jira-approval]
            [user_upload.jira.client :as jira]
            [user_upload.parser.team_disambiguator :as disambig]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [cheshire.core :as json])
  (:import [java.security MessageDigest]
           [java.util Base64]
           [java.io ByteArrayOutputStream]))

(def ^:private approval-request-prefix "[BOT:user_upload:approval-request:v2]")

(defn- bytes-to-sha256
  "Calculate SHA-256 hash of byte array and return as base64 string."
  [byte-array]
  (let [digest (MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest digest byte-array)
        encoder (Base64/getEncoder)]
    (.encodeToString encoder hash-bytes)))

(defn calculate-attachment-fingerprint
  "Calculate SHA-256 fingerprint for an attachment.
   
   Args:
     attachment - Attachment map with :filename and :content (byte array)
   
   Returns:
     Map with keys:
       :filename - Original filename
       :fingerprint - SHA-256 hash of content
       :size - Size in bytes"
  [attachment]
  (let [content (:content attachment)
        filename (:filename attachment)]
    {:filename filename
     :fingerprint (bytes-to-sha256 content)
     :size (count content)}))

(defn generate-approval-request-data
  "Generate structured data for an approval request.
   
   Args:
     ticket-key - Jira ticket key
     tenant - Tenant name
     valid-data - Validated user data rows
     attachments - List of attachments with fingerprints
     extra-info - Map with additional info like mapping, credentials status, etc.
   
   Returns:
     Map with structured approval request data"
  [ticket-key tenant valid-data attachments & [extra-info]]
  (let [team-names (set (mapcat :teams valid-data))
        user-count (count valid-data)
        team-count (count team-names)
        
        ;; Build team summary
        team-summary (vec team-names)]
    
    (merge
     {:ticket-key ticket-key
      :tenant tenant
      :user-count user-count
      :team-count team-count
      :attachments (mapv #(select-keys % [:filename :fingerprint :size]) attachments)
      :teams team-summary
      :timestamp (str (java.time.Instant/now))}
     extra-info)))

(defn format-approval-request-comment
  "Format approval request data into a Jira comment.
   
   Args:
     request-data - Structured approval request data
   
   Returns:
     Formatted comment body string"
  [request-data]
  (let [{:keys [ticket-key tenant user-count team-count attachments 
                teams timestamp
                column-mapping tenant-email credentials-found sheet-detected
                failed-attachments]} request-data
        
        ;; Format successful attachments section
        attachments-section (str/join "\n" 
                                     (map #(format "  - %s (SHA-256: %s, %d bytes)"
                                                  (:filename %)
                                                  (if (:fingerprint %)
                                                    (subs (:fingerprint %) 0 (min 12 (count (:fingerprint %))))
                                                    "unknown")
                                                  (or (:size %) 0))
                                          attachments))
        
        ;; Format failed attachments section if any
        failed-section (when (seq failed-attachments)
                        (str/join "\n"
                                 (map #(format "  - %s: %s"
                                              (:filename %)
                                              (or (:error %) "Processing failed"))
                                      failed-attachments)))
        
        ;; Format teams
        teams-section (str/join ", " teams)
        
        ;; Format column mapping if present
        mapping-section (when column-mapping
                         (str/join "\n"
                                  (map (fn [[file-col expected-col]]
                                        (format "  - '%s' → '%s'"
                                               file-col expected-col))
                                       column-mapping)))]
    
    (str approval-request-prefix "\n"
         "**USER UPLOAD APPROVAL REQUEST**\n"
         "**Tenant:** " (or tenant-email (str "customersolutions+" tenant "@jesi.io")) 
         " | **1Password:** " (if credentials-found 
                                (if (:multiple-items-found request-data)
                                  (str "✓ Found (multiple items - using: " (:item-used request-data) ")")
                                  "✓ Found")
                                "✗ Not found") "\n"
         "**Users:** " user-count " | **Teams (" team-count "):** " teams-section " | **Files:** " (count attachments) "\n"
         "**Attachments:** " (str/join ", " 
                                      (map #(format "%s (%s...)"
                                                   (:filename %)
                                                   (if (:fingerprint %)
                                                     (subs (:fingerprint %) 0 (min 8 (count (:fingerprint %))))
                                                     "unknown"))
                                           attachments)) "\n"
         
         (when failed-section
           (str "**Failed:** " (str/join ", " 
                                        (map #(format "%s (%s)"
                                                     (:filename %)
                                                     (or (:error %) "error"))
                                             failed-attachments)) "\n"))
         
         (when mapping-section
           (str (if sheet-detected
                  (str "**Sheet:** " sheet-detected "\n")
                  "")
                "**Column Mapping:**\n" 
                (str/join "\n" 
                         (map (fn [[file-col expected-col]]
                               (format "  %s → %s"
                                      file-col expected-col))
                              column-mapping)) "\n"))
         
         "**To approve:** Reply with 'approved' | **CSV attached:** users-for-approval.csv")))

(defn- extract-codeblocks-from-adf
  "Extract code block content from ADF structure."
  [adf]
  (letfn [(walk [n]
            (cond
              (and (map? n) (= "codeBlock" (:type n)))
              (map :text (get-in n [:content]))

              (map? n) (mapcat walk (:content n))
              (sequential? n) (mapcat walk n)
              :else []))]
    (->> (walk adf) (remove str/blank?) vec)))

(defn extract-approval-request-data
  "Extract structured data from an approval request comment.
   
   Args:
     comment-adf - Comment body in ADF format containing approval request
   
   Returns:
     Parsed approval request data map or nil if not found"
  [comment-adf]
  (try
    (when comment-adf
      (some (fn [code]
              (try (json/parse-string code true)
                   (catch Exception _ nil)))
            (extract-codeblocks-from-adf comment-adf)))
    (catch Exception e
      (log/warn "Failed to extract approval request data" {:error (.getMessage e)})
      nil)))

(defn validate-attachment-fingerprints
  "Validate that current attachments match those in the approval request.
   
   Args:
     current-attachments - Current attachments with fingerprints
     approval-request-data - Data from original approval request
   
   Returns:
     Map with keys:
       :valid - Boolean indicating if fingerprints match
       :changes - List of changes detected
       :message - Human-readable validation message"
  [current-attachments approval-request-data]
  (try
    (let [original-fingerprints (into {} 
                                     (map #(vector (:filename %) (:fingerprint %))
                                          (:attachments approval-request-data)))
          current-fingerprints (into {}
                                    (map #(vector (:filename %) (:fingerprint %))
                                         current-attachments))
          
          ;; Check for changes
          added-files (clojure.set/difference (set (keys current-fingerprints))
                                             (set (keys original-fingerprints)))
          removed-files (clojure.set/difference (set (keys original-fingerprints))
                                               (set (keys current-fingerprints)))
          
          modified-files (keep (fn [filename]
                                (when (and (contains? original-fingerprints filename)
                                          (contains? current-fingerprints filename)
                                          (not= (get original-fingerprints filename)
                                               (get current-fingerprints filename)))
                                  filename))
                              (keys current-fingerprints))
          
          all-changes (concat (map #(str "Added: " %) added-files)
                             (map #(str "Removed: " %) removed-files)
                             (map #(str "Modified: " %) modified-files))]
      
      (if (empty? all-changes)
        {:valid true
         :changes []
         :message "All attachment fingerprints match approval request"}
        {:valid false
         :changes all-changes
         :message (str "Attachment changes detected: " (str/join ", " all-changes))}))
    
    (catch Exception e
      (log/error e "Error validating attachment fingerprints")
      {:valid false
       :changes []
       :error (.getMessage e)
       :message (str "Error validating fingerprints: " (.getMessage e))})))

(defn check-workflow-approval
  "Check approval status with attachment fingerprint validation.
   
   Args:
     ticket-key - Jira ticket key
     current-attachments - Current attachments with fingerprints
   
   Returns:
     Map with keys:
       :status - :no-request, :pending, :approved, :invalid, :error
       :approval-valid - Boolean (for :approved status)
       :fingerprint-validation - Fingerprint validation result
       :message - Human-readable status message"
  [ticket-key current-attachments]
  (try
    (log/info "Checking workflow approval with fingerprint validation" {:ticket-key ticket-key})
    
    ;; Get basic approval status
    (let [basic-approval (jira-approval/check-approval-status ticket-key)]
      (case (:status basic-approval)
        :no-request
        (merge basic-approval {:approval-valid false})
        
        :pending
        (merge basic-approval {:approval-valid false})
        
        :error
        basic-approval
        
        :approved
        ;; For approved status, validate fingerprints
        (let [request-comment (:request-comment basic-approval)
              request-data (extract-approval-request-data (:adf-body request-comment))]
          
          (if-not request-data
            {:status :invalid
             :approval-valid false
             :message "Cannot validate approval - missing structured data"}
            
            (let [validation (validate-attachment-fingerprints current-attachments request-data)]
              (if (:valid validation)
                {:status :approved
                 :approval-valid true
                 :fingerprint-validation validation
                 :request-comment request-comment
                 :approval-comment (:approval-comment basic-approval)
                 :message "Approved and attachment fingerprints validated"}
                
                {:status :invalid
                 :approval-valid false
                 :fingerprint-validation validation
                 :request-comment request-comment
                 :message (str "Approval invalid - " (:message validation))}))))))
    
    (catch Exception e
      (log/error e "Error checking workflow approval" {:ticket-key ticket-key})
      {:status :error
       :approval-valid false
       :error (.getMessage e)
       :message (str "Error checking approval: " (.getMessage e))})))

(defn generate-csv-for-approval
  "Generate a clean CSV file from validated user data.
   
   Args:
     valid-data - Vector of validated user maps
   
   Returns:
     Map with:
       :success - Boolean
       :content - Byte array of CSV content
       :filename - Suggested filename
       :row-count - Number of data rows"
  [valid-data]
  (try
    (let [headers ["Email" "First Name" "Last Name" "Job Title" "Mobile Number" "Teams" "User Role"]
          rows (map (fn [user]
                     [(:email user)
                      (:first-name user)
                      (:last-name user)
                      (or (:job-title user) "")
                      (or (:mobile-number user) "0")
                      (if (coll? (:teams user))
                        (str/join "|" (:teams user))
                        (or (:teams user) ""))
                      (:user-role user)])
                   valid-data)
          baos (ByteArrayOutputStream.)]
      
      (with-open [writer (io/writer baos)]
        (csv/write-csv writer (cons headers rows)))
      
      {:success true
       :content (.toByteArray baos)
       :filename "users-for-approval.csv"  ; Fixed name for easy retrieval
       :row-count (count valid-data)})
    
    (catch Exception e
      (log/error e "Error generating CSV for approval")
      {:success false
       :error (.getMessage e)})))

(defn request-workflow-approval
  "Request approval with structured data and attachment fingerprints.
   Generates a clean CSV of the validated data and attaches it to the ticket.
   
   Args:
     ticket-key - Jira ticket key
     tenant - Tenant name
     valid-data - Validated user data
     attachments - Attachments with fingerprints (successful attachments)
     extra-info - Map with additional info like mapping, credentials status, failed-attachments, backend-teams, etc.
   
   Returns:
     Map with success status and details"
  [ticket-key tenant valid-data attachments & [extra-info]]
  (try
    (log/info "Requesting workflow approval" 
             {:ticket-key ticket-key 
              :tenant tenant
              :user-count (count valid-data)
              :attachment-count (count attachments)})
    
    ;; Step 1: Analyze teams and split on whitespace
    (let [team-analysis (disambig/analyze-dataset-teams valid-data)
          _ (when (> (:split-count team-analysis) 0)
              (log/info "Splitting team names with spaces" 
                       {:count (:split-count team-analysis)
                        :teams (map :team (:teams-with-spaces team-analysis))}))
          
          ;; Apply team splitting to the data for CSV generation
          split-data (if (> (:split-count team-analysis) 0)
                       (disambig/apply-team-splitting valid-data team-analysis)
                       valid-data)
          
          ;; Step 2: Generate CSV for approval with split data
          csv-result (generate-csv-for-approval split-data)]
      (if-not (:success csv-result)
        {:success false
         :error (:error csv-result)
         :message (str "Failed to generate CSV: " (:error csv-result))}
        
        ;; Step 3: Upload CSV to Jira
        (let [csv-attachment (try
                              (jira/add-attachment ticket-key 
                                                  (:filename csv-result)
                                                  (:content csv-result))
                              (catch Exception e
                                (log/warn "Failed to upload CSV attachment" 
                                         {:error (.getMessage e)})
                                nil))
              
              ;; Step 4: Generate approval request with CSV reference
              csv-info (when csv-attachment
                        {:filename (:filename csv-result)
                         :id (first (map :id csv-attachment))
                         :row-count (:row-count csv-result)})
              
              request-data (-> (generate-approval-request-data ticket-key tenant valid-data attachments extra-info)
                              (assoc :csv-attachment csv-info))
              
              ;; CSV info is now included in format-approval-request-comment
              initial-comment (format-approval-request-comment request-data)
              
              ;; Add team splitting message if needed
              comment-body (if (> (:split-count team-analysis) 0)
                            (disambig/enhance-approval-comment initial-comment team-analysis)
                            initial-comment)
              
              ;; Step 5: Post the comment
              result (jira/add-comment ticket-key comment-body)]
          
          (if result
            {:success true
             :comment-id (:id result)
             :csv-attachment csv-info
             :request-data request-data
             :message (str "Approval request posted with CSV attachment"
                          (when csv-info (str " (" (:filename csv-info) ")")))}
            {:success false
             :error "No response from Jira API"
             :message "Failed to post approval request"}))))
    
    (catch Exception e
      (log/error e "Error requesting workflow approval" {:ticket-key ticket-key})
      {:success false
       :error (.getMessage e)
       :message (str "Error posting approval request: " (.getMessage e))})))

(defn workflow-approval-required?
  "Check if workflow approval is required for the given data.
   
   Approval is required when:
   - AI column mapping was used (headers didn't exactly match)
   - Data transformations were applied
   - Multi-sheet Excel processing occurred
   
   Approval is NOT required when:
   - CSV file with exact column matches
   - No AI mapping or transformations needed
   
   Args:
     validation-result - Result from normalize-and-validate-data containing :mapping-used
     parse-result - Result from download-and-parse-attachment
   
   Returns:
     Boolean indicating if approval is required"
  [validation-result parse-result]
  (let [mapping-used (:mapping-used validation-result)
        detected-sheet (:detected-sheet parse-result)
        expected-headers #{"email" "first name" "last name" "job title" 
                          "mobile number" "teams" "user role"}
        
        ;; Check if original headers exactly matched expected (case-insensitive)
        original-headers (set (map str/lower-case (:headers parse-result)))
        exact-match? (= original-headers expected-headers)
        
        ;; Check if any AI was used
        ai-used? (or detected-sheet  ; AI sheet detection was used
                    (not exact-match?))  ; Headers didn't match exactly
        
        ;; Check if mapping indicates transformations
        mapping-transformations? (and mapping-used
                                     (not= (set (keys mapping-used))
                                          (set (vals mapping-used))))]
    
    (log/info "Checking if approval required" 
             {:exact-match exact-match?
              :ai-used ai-used?
              :detected-sheet detected-sheet
              :mapping-transformations mapping-transformations?})
    
    ;; Require approval if AI was used or transformations applied
    (or ai-used? mapping-transformations?)))

(comment
  ;; Example usage
  
  ;; Calculate fingerprint for an attachment
  (calculate-attachment-fingerprint 
    {:filename "users.csv"
     :content (.getBytes "email,name\njohn@example.com,John" "UTF-8")})
  
  ;; Check approval with fingerprint validation
  (check-workflow-approval "TEST-123" 
                          [{:filename "users.csv" 
                            :fingerprint "abc123..."
                            :size 1024}])
  
  ;; Request approval
  (request-workflow-approval "TEST-123" "acme" 
                            [{:email "john@example.com"
                              :first-name "John"
                              :last-name "Doe"
                              :user-role "TEAM MEMBER"
                              :teams ["Engineering"]}]
                            [{:filename "users.csv"
                              :fingerprint "abc123..."
                              :size 1024}]))