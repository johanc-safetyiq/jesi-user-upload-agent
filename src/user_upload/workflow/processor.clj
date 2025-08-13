(ns user-upload.workflow.processor
  "Main ticket processing loop that handles the complete workflow.
   
   This module:
   - Fetches tickets from Jira using configured JQL
   - Processes each ticket independently with error isolation
   - Downloads and processes attachments
   - Manages tenant-specific authentication
   - Orchestrates the approval workflow"
  (:require [clojure.tools.logging :as log]
            [clj-http.client :as http]
            [user-upload.config :as config]
            [user-upload.jira.client :as jira]
            [user-upload.ai.intent :as intent]
            [user-upload.workflow.orchestrator :as orchestrator]
            [user-upload.auth.tenant :as tenant]
            [user-upload.auth.onepassword :as op]
            [user-upload.jira.approval :as approval]
            [clojure.string :as str]))

(defn build-jql-query
  "Build JQL query from configuration keywords.
   
   Args:
     config - Configuration map with :jira section
   
   Returns:
     JQL query string for finding relevant tickets"
  [config]
  (let [jira-config (:jira config)
        ;; Check if custom JQL is provided
        custom-jql (:custom-jql jira-config)]
    
    ;; If custom JQL is provided, use it directly
    (if (not (str/blank? custom-jql))
      (do
        (log/info "Using custom JQL from config" {:jql custom-jql})
        custom-jql)
      
      ;; Otherwise build JQL from components
      (let [project (or (:project jira-config) "JESI")
        ;; Include both Open and Review status to handle approval flow
        statuses (or (:statuses jira-config) ["Open" "Review"])
        
        ;; Build JQL components
        project-clause (str "project = " project)
        status-clause (if (= (count statuses) 1)
                       (str "status = \"" (first statuses) "\"")
                       (str "status IN (" (str/join ", " (map #(str "\"" % "\"") statuses)) ")"))
        ;; Simpler text search without nested quotes
        search-clause "text ~ \"user upload\""
        
        ;; Combine clauses
        clauses [project-clause status-clause search-clause]
        jql (str/join " AND " clauses)]
    
    (log/info "Built JQL query" {:jql jql})
    jql))))

(defn download-attachment
  "Download attachment content from Jira.
   
   Args:
     attachment - Jira attachment object with :content URL
   
   Returns:
     Map with keys:
       :success - Boolean indicating if download succeeded
       :content - Byte array of file content (if successful)
       :filename - Original filename
       :error - Error message (if failed)"
  [attachment]
  (try
    (let [content-url (:content attachment)
          filename (:filename attachment)]
      
      (log/info "Downloading attachment" {:filename filename :url content-url})
      
      (if (str/blank? content-url)
        {:success false :filename filename :error "No content URL provided"}
        
        (let [;; Use same auth headers as Jira client
              cfg (config/config)
              email (get-in cfg [:jira :email])
              api-token (get-in cfg [:jira :api-token])
              auth-header (str "Basic " 
                              (.encodeToString 
                                (java.util.Base64/getEncoder)
                                (.getBytes (str email ":" api-token) "UTF-8")))
              
              response (http/get content-url
                               {:headers {"Authorization" auth-header}
                                :as :byte-array
                                :throw-exceptions false})]
          
          (if (< (:status response) 400)
            {:success true
             :content (:body response)
             :filename filename}
            {:success false
             :filename filename
             :error (str "HTTP " (:status response) " downloading attachment")}))))
    
    (catch Exception e
      (log/error e "Error downloading attachment" {:filename (:filename attachment)})
      {:success false
       :filename (:filename attachment)
       :error (.getMessage e)})))

(defn filter-eligible-attachments
  "Filter attachments to only those eligible for processing.
   
   Args:
     attachments - List of Jira attachment objects
   
   Returns:
     List of attachments that are CSV or Excel files"
  [attachments]
  (filter (fn [attachment]
            (let [filename (str/lower-case (:filename attachment))]
              (or (str/ends-with? filename ".csv")
                  (str/ends-with? filename ".xlsx")
                  (str/ends-with? filename ".xls"))))
          attachments))

(defn process-single-ticket
  "Process a single Jira ticket through the complete workflow.
   
   Args:
     ticket - Jira ticket/issue object
     config - Configuration map
   
   Returns:
     Map with keys:
       :success - Boolean indicating overall success
       :ticket-key - Jira ticket key
       :tenant - Extracted tenant name
       :results - Detailed processing results
       :summary - Human-readable summary
       :error - Error message (if failed)"
  [ticket config]
  (let [ticket-key (:key ticket)
        ticket-status (get-in ticket [:fields :status :name])]
    (try
      (log/info "Processing ticket" {:ticket-key ticket-key :status ticket-status})
      
      ;; Check if ticket is in Review status - if so, only check for approval
      (if (= ticket-status "Review")
        (do
          (log/info "Ticket is in Review status, checking for approval only" {:ticket-key ticket-key})
          (let [approval-check (approval/check-approval-status ticket-key)]
            (case (:status approval-check)
              :approved
              (do
                (log/info "Ticket approved, transitioning to Closed" {:ticket-key ticket-key})
                ;; Extract tenant for completing the workflow
                (let [auth-result (orchestrator/extract-tenant-and-authenticate 
                                  ticket 
                                  "customersolutions+%s@jesi.io")]
                  (if-not (:success auth-result)
                    {:success false
                     :ticket-key ticket-key
                     :error (:error auth-result)
                     :summary (str "Failed to authenticate for " ticket-key ": " (:error auth-result))}
                    
                    ;; Complete the workflow by transitioning to Done/Closed
                    (do
                      (try
                        (jira/transition-issue ticket-key "Done" "User upload approved and processed")
                        (catch Exception e
                          (log/warn "Could not transition to Done, trying Closed" {:error (.getMessage e)})
                          (jira/transition-issue ticket-key "Closed" "User upload approved and processed")))
                      {:success true
                       :ticket-key ticket-key
                       :tenant (:tenant auth-result)
                       :approval-status :approved
                       :summary (str "Approved ticket " ticket-key " transitioned to Done")}))))
              
              :pending
              (do
                (log/info "Ticket still pending approval, skipping" {:ticket-key ticket-key})
                {:success true
                 :ticket-key ticket-key
                 :approval-status :pending
                 :skipped true
                 :summary (str "Skipped " ticket-key " - waiting for approval")})
              
              ;; :no-request or :error
              (do
                (log/warn "Unexpected state for Review ticket" {:ticket-key ticket-key :approval-status (:status approval-check)})
                {:success false
                 :ticket-key ticket-key
                 :error (str "Unexpected approval state: " (:message approval-check))
                 :summary (str "Ticket " ticket-key " in Review but " (:message approval-check))}))))
        
        ;; Not in Review status - process normally
        (let [attachments (get-in ticket [:fields :attachment] [])
            attachment-filenames (mapv :filename attachments)
            ;; Extract fields for intent detection - the function expects these at top level
            ticket-for-intent {:key (:key ticket)
                               :summary (get-in ticket [:fields :summary])
                               :description (get-in ticket [:fields :description])}
            intent-result (intent/detect-user-upload-intent ticket-for-intent attachment-filenames)]
        
        (if-not (:is-user-upload intent-result)
          {:success false
           :ticket-key ticket-key
           :error "Not identified as user upload request"
           :summary (str "Skipped " ticket-key ": not a user upload request")}
          
          ;; Step 2: Extract tenant and authenticate
          (let [auth-result (orchestrator/extract-tenant-and-authenticate 
                            ticket 
                            "customersolutions+%s@jesi.io")]
            (if-not (:success auth-result)
              {:success false
               :ticket-key ticket-key
               :error (:error auth-result)
               :summary (str "Failed to authenticate for " ticket-key ": " (:error auth-result))}
              
              ;; Step 3: Filter and process eligible attachments
              (let [tenant (:tenant auth-result)
                    eligible-attachments (filter-eligible-attachments attachments)]
                
                (if (empty? eligible-attachments)
                  {:success false
                   :ticket-key ticket-key
                   :tenant tenant
                   :error "No eligible attachments found"
                   :summary (str "No CSV/Excel attachments found in " ticket-key)}
                  
                  ;; Process each attachment
                  (let [attachment-results (atom [])
                        overall-success (atom true)]
                    
                    (doseq [attachment eligible-attachments]
                      (try
                        ;; Download attachment content
                        (let [download-result (download-attachment attachment)]
                          (if (:success download-result)
                            ;; Process the attachment with downloaded content
                            (let [attachment-with-content (assoc attachment :content (:content download-result))
                                  ticket-status (get-in ticket [:fields :status :name])
                                  process-result (orchestrator/process-attachment 
                                                attachment-with-content 
                                                tenant
                                                ticket-key
                                                ticket
                                                true)]  ; credentials-found is true since we authenticated
                              (swap! attachment-results conj process-result)
                              ;; Don't mark as failure if approval was requested
                              (when (and (not (:success process-result))
                                        (not= (:approval-status process-result) :requested))
                                (reset! overall-success false)))
                            
                            ;; Download failed
                            (do
                              (swap! attachment-results conj 
                                    {:success false
                                     :filename (:filename attachment)
                                     :error (:error download-result)
                                     :summary (str "Failed to download " (:filename attachment))})
                              (reset! overall-success false))))
                        
                        (catch Exception e
                          (log/error e "Error processing attachment" {:filename (:filename attachment)})
                          (swap! attachment-results conj 
                                {:success false
                                 :filename (:filename attachment)
                                 :error (.getMessage e)
                                 :summary (str "Error processing " (:filename attachment))})
                          (reset! overall-success false))))
                    
                    ;; Return results
                    (let [results @attachment-results
                          successful-count (count (filter :success results))
                          total-count (count results)]
                      
                      {:success @overall-success
                       :ticket-key ticket-key
                       :tenant tenant
                       :results results
                       :summary (str "Processed " ticket-key " (" tenant "): "
                                    successful-count "/" total-count " attachments succeeded")})))))))))
      
      (catch Exception e
        (log/error e "Error processing ticket" {:ticket-key ticket-key})
        {:success false
         :ticket-key ticket-key
         :error (.getMessage e)
         :summary (str "Unexpected error processing " ticket-key ": " (.getMessage e))}))))

(defn fetch-tickets
  "Fetch tickets from Jira using configured JQL.
   
   Args:
     config - Configuration map
   
   Returns:
     Map with keys:
       :success - Boolean indicating if fetch succeeded
       :tickets - List of ticket objects (if successful)
       :count - Number of tickets found
       :error - Error message (if failed)"
  [config]
  (try
    (log/info "Fetching tickets from Jira")
    
    (let [jql (build-jql-query config)
          search-result (jira/search-issues 
                        {:jql jql
                         :fields "summary,description,status,attachment,comment"
                         :expand "attachment"
                         :max-results 100})
          tickets (get search-result :issues [])]
      
      (log/info "Fetched tickets" {:count (count tickets)})
      
      {:success true
       :tickets tickets
       :count (count tickets)})
    
    (catch Exception e
      (log/error e "Error fetching tickets from Jira")
      {:success false
       :error (.getMessage e)})))

(defn process-tickets
  "Main processing loop that fetches and processes all eligible tickets.
   
   Args:
     config - Configuration map (optional, uses default config if not provided)
     options - Processing options map with keys:
       :single-ticket - Boolean, if true process only first ticket (default: false)
       :ticket - String, specific ticket key to process (e.g., 'JESI-5928')
   
   Returns:
     Map with keys:
       :success - Boolean indicating overall success
       :total-tickets - Total number of tickets processed
       :successful-tickets - Number of tickets processed successfully
       :failed-tickets - Number of tickets that failed
       :results - List of detailed results for each ticket
       :summary - Human-readable summary"
  [& [config options]]
  (let [cfg (or config (config/config))
        single-ticket (:single-ticket options false)
        specific-ticket (:ticket options)]
    (try
      (log/info "Starting ticket processing loop" 
               (cond
                 specific-ticket {:mode "specific-ticket" :ticket specific-ticket}
                 single-ticket {:mode "single-ticket"}
                 :else {:mode "all-tickets"}))
      
      ;; Check prerequisites
      (let [op-check (op/check-op-availability)]
        (when-not (:available op-check)
          (throw (ex-info "1Password CLI not available" op-check))))
      
      ;; Fetch tickets
      (let [fetch-result (if specific-ticket
                          ;; Fetch specific ticket
                          (try
                            (let [ticket (jira/get-issue-with-attachments specific-ticket)]
                              {:success true
                               :tickets [ticket]
                               :count 1})
                            (catch Exception e
                              {:success false
                               :error (str "Failed to fetch ticket " specific-ticket ": " (.getMessage e))}))
                          ;; Fetch all matching tickets
                          (fetch-tickets cfg))]
        (if-not (:success fetch-result)
          {:success false
           :error (:error fetch-result)
           :summary (str "Failed to fetch tickets: " (:error fetch-result))}
          
          (let [tickets (:tickets fetch-result)
                results (atom [])
                successful-count (atom 0)
                failed-count (atom 0)
                skipped-count (atom 0)]
            
            (if (empty? tickets)
              {:success true
               :total-tickets 0
               :successful-tickets 0
               :failed-tickets 0
               :skipped-tickets 0
               :results []
               :summary "No tickets found to process"}
              
              (do
                ;; Determine which tickets to process
                (let [tickets-to-process (cond
                                          specific-ticket tickets  ; Already filtered to specific ticket
                                          single-ticket (do
                                                         (log/info "Single-ticket mode: processing only first ticket")
                                                         [(first tickets)])
                                          :else tickets)]
                  
                  ;; Process selected tickets
                  (doseq [ticket tickets-to-process]
                    (let [ticket-result (process-single-ticket ticket cfg)]
                      (swap! results conj ticket-result)
                      (cond
                        (:skipped ticket-result) (swap! skipped-count inc)
                        (:success ticket-result) (swap! successful-count inc)
                        :else (swap! failed-count inc))
                      
                      (log/info "Ticket processing complete" 
                               {:ticket-key (:ticket-key ticket-result)
                                :success (:success ticket-result)
                                :skipped (:skipped ticket-result)
                                :summary (:summary ticket-result)}))))
                
                ;; Return summary
                (let [total (+ @successful-count @failed-count @skipped-count)
                      overall-success (or (> @successful-count 0) (> @skipped-count 0))]
                  
                  (log/info (if single-ticket "Single ticket processed" "All tickets processed")
                           {:total total
                            :successful @successful-count
                            :failed @failed-count
                            :skipped @skipped-count})
                  
                  {:success overall-success
                   :total-tickets total
                   :successful-tickets @successful-count
                   :failed-tickets @failed-count
                   :skipped-tickets @skipped-count
                   :results @results
                   :summary (str "Processed " total 
                                (if single-ticket " ticket" " tickets") ": "
                                @successful-count " successful, " 
                                @failed-count " failed, "
                                @skipped-count " skipped")}))))))
      
      (catch Exception e
        (log/error e "Error in ticket processing loop")
        {:success false
         :error (.getMessage e)
         :summary (str "Processing loop failed: " (.getMessage e))}))))

(defn run-once
  "Run the ticket processor once and return results.
   
   This is the main entry point for single-run execution.
   
   Args:
     options - Processing options map (optional) with keys:
       :single-ticket - Boolean, if true process only first ticket"
  [& [options]]
  (log/info "Running ticket processor (single execution)" 
           (when (:single-ticket options) {:mode "single-ticket"}))
  (process-tickets (config/config) options))

(comment
  ;; Example usage
  
  ;; Run the processor once
  (run-once)
  
  ;; Test JQL building
  (build-jql-query (config/config))
  
  ;; Test fetching tickets
  (fetch-tickets (config/config)))