(ns user-upload.ai.claude
  "Claude Code CLI integration for AI-powered ticket analysis.
   
   This module provides functions to invoke the Claude Code CLI in non-interactive
   mode and parse structured JSON responses for intent detection and column mapping."
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [user-upload.log :as log]
            [clojure.string :as str]))

(def ^:private default-timeout-ms 30000) ; 30 seconds

(defn- build-claude-command
  "Build the Claude Code CLI command with required flags for non-interactive mode."
  [prompt system-prompt & {:keys [allowed-tools cwd]
                           :or {allowed-tools "Read"
                                cwd "/Users/johan/Work/brisbane/jesi-system/user-upload-agent"}}]
  (let [full-prompt (str prompt "\n\n" system-prompt)]
    ["claude" 
     "--print"
     "--allowedTools" allowed-tools
     "--output-format" "json"
     full-prompt]))

(defn- parse-json-response
  "Parse Claude's JSON response, handling errors gracefully."
  [response-str]
  (try
    (when (and response-str (not (str/blank? response-str)))
      ;; Don't keywordize keys - keep them as strings
      (json/parse-string response-str false))
    (catch Exception e
      (log/warn "Failed to parse Claude JSON response" 
                {:response response-str :error (.getMessage e)})
      nil)))

(defn invoke-claude-with-timeout
  "Invoke Claude Code CLI with timeout handling.
   
   Args:
     prompt - The task prompt as a string
     system-prompt - Additional system instructions 
     options - Map with optional keys:
       :timeout-ms - Timeout in milliseconds (default 30000)
       :allowed-tools - Comma-separated tool list (default 'Read')
       :cwd - Working directory for CLI
   
   Returns:
     Map with keys:
       :success - Boolean indicating if call succeeded
       :result - Parsed JSON response (if successful)
       :error - Error message (if failed)
       :timeout - Boolean indicating if timeout occurred"
  [prompt system-prompt & {:keys [timeout-ms allowed-tools cwd]
                           :or {timeout-ms default-timeout-ms}
                           :as options}]
  (let [command (build-claude-command prompt system-prompt
                                      :allowed-tools allowed-tools
                                      :cwd cwd)]
    (try
      (log/debug "Invoking Claude CLI" {:command (str/join " " command)})
      (let [result (apply process/shell 
                         {:timeout timeout-ms
                          :out :string
                          :err :string}
                         command)
            {:keys [exit out err]} result]
        (cond
          ;; Success case
          (zero? exit)
          (let [parsed (parse-json-response out)]
            (if parsed
              ;; Extract the actual result from the wrapper (using string key, not keyword)
              (if-let [result-text (get parsed "result")]
                ;; Try to parse the result as JSON if it's a JSON string
                (if (and (string? result-text) 
                         (or (str/starts-with? (str/trim result-text) "{")
                             (str/starts-with? (str/trim result-text) "```json")))
                  (let [cleaned (-> result-text
                                   (str/replace #"```json\n?" "")
                                   (str/replace #"\n?```" "")
                                   str/trim)
                        inner-json (parse-json-response cleaned)]
                    (if inner-json
                      {:success true :result inner-json}
                      {:success true :result result-text}))
                  {:success true :result result-text})
                {:success true :result parsed})
              {:success false :error "Failed to parse JSON response" :raw-output out}))
          
          ;; Process error
          :else
          {:success false 
           :error (format "Claude CLI failed with exit code %d. stderr: %s" exit err)
           :exit-code exit
           :stderr err}))
      
      (catch java.util.concurrent.TimeoutException e
        (log/warn "Claude CLI timed out" {:timeout-ms timeout-ms})
        {:success false :timeout true :error "Claude CLI timed out"})
      
      (catch Exception e
        (log/error "Unexpected error invoking Claude CLI" e)
        {:success false :error (.getMessage e)}))))

(defn check-claude-availability
  "Check if Claude Code CLI is available and working.
   
   Returns:
     Map with keys:
       :available - Boolean indicating if Claude is available
       :version - Claude CLI version (if available)
       :error - Error message (if not available)"
  []
  (try
    (let [result (process/shell {:out :string :err :string :timeout 5000}
                                "claude" "--version")]
      (if (zero? (:exit result))
        {:available true :version (str/trim (:out result))}
        {:available false :error (:err result)}))
    (catch Exception e
      {:available false :error (.getMessage e)})))

(defn invoke-intent-detection
  "Check if a Jira ticket is a user upload request.
   
   Args:
     ticket - Map with :key, :summary, :description
     attachments - Vector of attachment filenames
   
   Returns:
     Map with :success boolean and either:
       :is-user-upload - Boolean result
       :error - Error message"
  [ticket attachments]
  (let [prompt (str "Analyze this Jira ticket to determine if it's a user upload request:\n"
                   "Ticket: " (:key ticket) "\n"
                   "Summary: " (:summary ticket) "\n"
                   "Description: " (or (:description ticket) "No description") "\n"
                   "Attachments: " (if (seq attachments) (str/join ", " attachments) "None") "\n\n"
                   "Is this a request to bulk upload/import users into a system? "
                   "Reply with ONLY this JSON: {\"is_user_upload\": true} or {\"is_user_upload\": false}")
        system-prompt "IMPORTANT: Reply with ONLY valid JSON, no explanatory text. The response must be exactly: {\"is_user_upload\": true} or {\"is_user_upload\": false}"]
    
    (let [response (invoke-claude-with-timeout prompt system-prompt
                                               :allowed-tools "Read")]
      (if (:success response)
        (let [result (:result response)]
          (log/debug "Claude response result" {:result result})
          ;; Handle both direct JSON response and text containing JSON
          (cond
            ;; Direct JSON response with is_user_upload field
            (get result "is_user_upload")
            {:success true :is-user-upload (get result "is_user_upload")}
            
            ;; Text response that might contain JSON - try to extract it
            (string? result)
            (if-let [json-match (re-find #"\{[^}]*\"is_user_upload\"[^}]*\}" result)]
              (if-let [parsed (parse-json-response json-match)]
                (if-let [is-upload (get parsed "is_user_upload")]
                  {:success true :is-user-upload is-upload}
                  {:success false :error (str "Missing is_user_upload field after parsing. Got: " parsed)})
                {:success false :error (str "Failed to parse JSON from text response: " json-match)})
              {:success false :error (str "No valid JSON found in text response: " result)})
            
            ;; Unexpected response format
            :else
            {:success false :error (str "Unexpected response format. Got: " result)}))
        {:success false :error (:error response)}))))

(defn invoke-column-mapping
  "Map file column headers to expected schema fields.
   
   Args:
     expected-fields - Vector of expected field names
     file-headers - Vector of actual file column headers
   
   Returns:
     Map with :success boolean and either:
       :mapping - Map of file-header -> expected-field
       :unmapped - Vector of expected fields not found
       :error - Error message"
  [expected-fields file-headers]
  (let [prompt (json/generate-string
                {:task "map-columns"
                 :expected expected-fields
                 :headers file-headers})
        system-prompt "Return ONLY JSON with fields: {\"mapping\": {<file-col>: <expected>}, \"unmapped\": [<expected-fields-missing>]}. No prose."]
    
    (let [response (invoke-claude-with-timeout prompt system-prompt
                                               :allowed-tools "Read")]
      (if (:success response)
        (let [result (:result response)]
          (if (and (contains? result "mapping") (contains? result "unmapped"))
            {:success true 
             :mapping (get result "mapping")
             :unmapped (get result "unmapped")}
            {:success false :error "Missing mapping or unmapped fields in response"}))
        {:success false :error (:error response)}))))

(comment
  ;; Test Claude availability
  (check-claude-availability)
  
  ;; Test intent detection
  (invoke-intent-detection
    {:key "ABC-123" 
     :summary "Upload new users"
     :description "Please add these new team members to our system"}
    ["users.xlsx"])
  
  ;; Test column mapping
  (invoke-column-mapping
    ["email" "first name" "last name" "job title" "mobile number" "teams" "user role"]
    ["Email Address" "FirstName" "Surname" "Role" "Teams"]))