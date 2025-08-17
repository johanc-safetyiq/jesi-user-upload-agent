(ns user_upload.ai.intent
  "AI-powered intent detection for Jira tickets.
   
   This module determines whether a Jira ticket represents a user upload request
   by analyzing the ticket summary, description, and attachments using Claude AI.
   No fallback heuristics - fails fast if AI is unavailable."
  (:require [user_upload.ai.claude :as claude]
            [user_upload.log :as log]
            [clojure.string :as str]))

;; Removed heuristic functions - no longer needed as we rely solely on AI detection
;; AI-only approach ensures consistent and accurate intent detection

(defn ai-intent-detection
  "Use Claude AI to detect user upload intent.
   
   Args:
     ticket - Map with :key, :summary, :description
     attachments - Vector of attachment filenames
   
   Returns:
     Map with :method :ai and either:
       :success true, :is-user_upload boolean
       :success false, :error string"
  [ticket attachments]
  (log/debug "AI intent detection starting" 
             {:ticket (:key ticket)
              :attachment-count (count attachments)})
  
  (let [result (claude/invoke-intent-detection ticket attachments)]
    (if (:success result)
      {:method :ai
       :success true
       :is-user_upload (:is-user_upload result)}
      {:method :ai
       :success false
       :error (:error result)})))

(defn detect-user_upload-intent
  "Detect whether a Jira ticket represents a user upload request.
   
   Uses AI detection exclusively - fails fast if AI is unavailable.
   
   Args:
     ticket - Map with :key, :summary, :description
     attachments - Vector of attachment filenames
   
   Returns:
     Map with keys:
       :is-user_upload - Boolean result (false if AI fails)
       :method - Always :ai
       :ai-result - Original AI result
       :success - Boolean indicating if detection succeeded
       :error - Error message (if AI failed)"
  [ticket attachments]
  
  (log/info "Detecting ticket intent" 
            {:ticket (:key ticket)
             :summary (:summary ticket)})
  
  ;; Always use AI detection
  (let [ai-result (ai-intent-detection ticket attachments)]
    (if (:success ai-result)
      ;; AI succeeded
      (do
        (log/info "AI intent detected" 
                  {:ticket (:key ticket)
                   :is-user_upload (:is-user_upload ai-result)})
        {:is-user_upload (:is-user_upload ai-result)
         :method :ai
         :ai-result ai-result
         :success true})
      
      ;; AI failed - fail fast
      (do
        (log/error "AI intent detection failed" 
                   {:ticket (:key ticket)
                    :error (:error ai-result)})
        {:is-user_upload false
         :method :ai
         :ai-result ai-result
         :success false
         :error (:error ai-result)}))))

(defn batch-intent-detection
  "Detect intent for multiple tickets efficiently.
   
   Args:
     ticket-attachment-pairs - Vector of [ticket attachments] pairs
   
   Returns:
     Vector of results in same order as input"
  [ticket-attachment-pairs]
  (mapv (fn [[ticket attachments]]
          (detect-user_upload-intent ticket attachments))
        ticket-attachment-pairs))

(comment
  ;; Test intent detection
  
  ;; Positive cases
  (detect-user_upload-intent
    {:key "HR-123" 
     :summary "Upload new team members"
     :description "Please process the attached spreadsheet with new employee data"}
    ["team-members.xlsx"])
  
  (detect-user_upload-intent
    {:key "IT-456"
     :summary "Add users to system"
     :description "Bulk user import needed"}
    ["users.csv"])
  
  ;; Negative cases
  (detect-user_upload-intent
    {:key "BUG-789"
     :summary "Login page not working"
     :description "Users cannot log in to the system"}
    [])
  
  (detect-user_upload-intent
    {:key "FEAT-101"
     :summary "Improve dashboard performance"
     :description "Dashboard loads too slowly"}
    ["performance-report.pdf"])
  
  ;; Edge cases
  (detect-user_upload-intent
    {:key "UNCLEAR-202"
     :summary "Help needed"
     :description ""}
    [])
  
  ;; Batch testing
  (batch-intent-detection
    [[{:key "HR-123" :summary "Upload users" :description ""} ["users.xlsx"]]
     [{:key "BUG-456" :summary "Fix login" :description ""} []]
     [{:key "IT-789" :summary "New employees" :description "Onboard team"} ["staff.csv"]]])
  
  ;; Test with AI disabled
  (detect-user_upload-intent
    {:key "HR-999" :summary "Add new staff members" :description ""}
    ["employees.xlsx"]
    :ai-enabled false))