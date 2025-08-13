(ns user-upload.ai.intent
  "AI-powered intent detection for Jira tickets.
   
   This module determines whether a Jira ticket represents a user upload request
   by analyzing the ticket summary, description, and attachments using Claude AI."
  (:require [user-upload.ai.claude :as claude]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private user-upload-keywords
  "Keywords that commonly indicate user upload requests."
  ["upload" "add users" "new users" "user import" "bulk users" "user list"
   "team members" "employees" "staff" "personnel" "roster" "directory"
   "onboard" "onboarding" "user data" "user file" "spreadsheet" "csv" "excel"])

(def ^:private user-upload-file-extensions
  "File extensions commonly used for user data uploads."
  [".csv" ".xlsx" ".xls" ".tsv"])

(defn contains-upload-keywords?
  "Check if text contains keywords indicating user upload intent."
  [text]
  (when text
    (let [lower-text (str/lower-case text)]
      (some #(str/includes? lower-text %) user-upload-keywords))))

(defn contains-upload-file-types?
  "Check if attachments contain file types commonly used for user uploads."
  [attachments]
  (when (seq attachments)
    (some (fn [filename]
            (let [lower-filename (str/lower-case filename)]
              (some #(str/ends-with? lower-filename %) user-upload-file-extensions)))
          attachments)))

(defn heuristic-intent-check
  "Perform heuristic-based intent detection as fallback when AI is unavailable.
   
   Args:
     ticket - Map with :key, :summary, :description
     attachments - Vector of attachment filenames
   
   Returns:
     Map with :method :heuristic and :is-user-upload boolean"
  [ticket attachments]
  (let [{:keys [summary description]} ticket
        has-keywords? (or (contains-upload-keywords? summary)
                          (contains-upload-keywords? description))
        has-upload-files? (contains-upload-file-types? attachments)
        
        ;; Consider it a user upload if it has relevant keywords OR upload file types
        is-user-upload (or has-keywords? has-upload-files?)]
    
    (log/debug (format "Heuristic intent check for %s: keywords=%s files=%s -> %s"
                       (:key ticket) has-keywords? has-upload-files? is-user-upload))
    
    {:method :heuristic
     :is-user-upload is-user-upload
     :confidence (cond
                   (and has-keywords? has-upload-files?) :high
                   (or has-keywords? has-upload-files?) :medium
                   :else :low)
     :reasons {:has-keywords has-keywords?
               :has-upload-files has-upload-files?}}))

(defn ai-intent-detection
  "Use Claude AI to detect user upload intent.
   
   Args:
     ticket - Map with :key, :summary, :description
     attachments - Vector of attachment filenames
   
   Returns:
     Map with :method :ai and either:
       :success true, :is-user-upload boolean
       :success false, :error string"
  [ticket attachments]
  (log/debug (format "AI intent detection for ticket %s with %d attachments"
                     (:key ticket) (count attachments)))
  
  (let [result (claude/invoke-intent-detection ticket attachments)]
    (if (:success result)
      {:method :ai
       :success true
       :is-user-upload (:is-user-upload result)}
      {:method :ai
       :success false
       :error (:error result)})))

(defn detect-user-upload-intent
  "Detect whether a Jira ticket represents a user upload request.
   
   Uses AI detection with heuristic fallback for reliability.
   
   Args:
     ticket - Map with :key, :summary, :description
     attachments - Vector of attachment filenames
     options - Map with optional keys:
       :ai-enabled - Use AI detection (default true)
       :fallback-to-heuristic - Fall back to heuristics if AI fails (default true)
   
   Returns:
     Map with keys:
       :is-user-upload - Boolean result
       :method - Detection method used (:ai, :heuristic, or :combined)
       :confidence - Confidence level (:high, :medium, :low) for heuristic method
       :ai-result - Original AI result (if AI was attempted)
       :heuristic-result - Heuristic result (if used)
       :error - Error message (if both methods failed)"
  [ticket attachments & {:keys [ai-enabled fallback-to-heuristic]
                         :or {ai-enabled true fallback-to-heuristic true}}]
  
  (log/info (format "Detecting intent for ticket %s: '%s'"
                    (:key ticket) (:summary ticket)))
  
  (if ai-enabled
    ;; Try AI detection first
    (let [ai-result (ai-intent-detection ticket attachments)]
      (if (:success ai-result)
        ;; AI succeeded
        (do
          (log/info (format "AI detected intent for %s: %s"
                            (:key ticket) (:is-user-upload ai-result)))
          {:is-user-upload (:is-user-upload ai-result)
           :method :ai
           :ai-result ai-result})
        
        ;; AI failed, try fallback
        (if fallback-to-heuristic
          (let [heuristic-result (heuristic-intent-check ticket attachments)]
            (log/warn (format "AI failed for %s, using heuristic: %s"
                              (:key ticket) (:is-user-upload heuristic-result)))
            {:is-user-upload (:is-user-upload heuristic-result)
             :method :combined
             :confidence (:confidence heuristic-result)
             :ai-result ai-result
             :heuristic-result heuristic-result})
          
          ;; No fallback, return AI error
          {:is-user-upload false
           :method :ai
           :ai-result ai-result
           :error (:error ai-result)})))
    
    ;; AI disabled, use heuristic only
    (let [heuristic-result (heuristic-intent-check ticket attachments)]
      (log/info (format "Heuristic-only intent for %s: %s"
                        (:key ticket) (:is-user-upload heuristic-result)))
      {:is-user-upload (:is-user-upload heuristic-result)
       :method :heuristic
       :confidence (:confidence heuristic-result)
       :heuristic-result heuristic-result})))

(defn batch-intent-detection
  "Detect intent for multiple tickets efficiently.
   
   Args:
     ticket-attachment-pairs - Vector of [ticket attachments] pairs
     options - Same options as detect-user-upload-intent
   
   Returns:
     Vector of results in same order as input"
  [ticket-attachment-pairs & options]
  (mapv (fn [[ticket attachments]]
          (apply detect-user-upload-intent ticket attachments options))
        ticket-attachment-pairs))

(comment
  ;; Test intent detection
  
  ;; Positive cases
  (detect-user-upload-intent
    {:key "HR-123" 
     :summary "Upload new team members"
     :description "Please process the attached spreadsheet with new employee data"}
    ["team-members.xlsx"])
  
  (detect-user-upload-intent
    {:key "IT-456"
     :summary "Add users to system"
     :description "Bulk user import needed"}
    ["users.csv"])
  
  ;; Negative cases
  (detect-user-upload-intent
    {:key "BUG-789"
     :summary "Login page not working"
     :description "Users cannot log in to the system"}
    [])
  
  (detect-user-upload-intent
    {:key "FEAT-101"
     :summary "Improve dashboard performance"
     :description "Dashboard loads too slowly"}
    ["performance-report.pdf"])
  
  ;; Edge cases
  (detect-user-upload-intent
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
  (detect-user-upload-intent
    {:key "HR-999" :summary "Add new staff members" :description ""}
    ["employees.xlsx"]
    :ai-enabled false))