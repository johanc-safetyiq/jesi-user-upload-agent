(ns user-upload.jira.client
  "Jira REST API client for basic operations."
  (:require
    [clj-http.client :as http]
    [cheshire.core :as json]
    [clojure.string :as str]
    [user-upload.config :as config]
    [user-upload.log :as log])
  (:import
    [java.util Base64]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]))

;; Authentication helpers

(defn- encode-basic-auth
  "Encode email and API token for Basic Auth header."
  [email api-token]
  (let [credentials (str email ":" api-token)
        encoder (Base64/getEncoder)
        encoded (.encodeToString encoder (.getBytes credentials "UTF-8"))]
    (str "Basic " encoded)))

(defn- auth-headers
  "Generate authentication headers for Jira API requests."
  []
  (let [cfg (config/config)
        email (get-in cfg [:jira :email])
        api-token (get-in cfg [:jira :api-token])]
    (when (or (str/blank? email) (str/blank? api-token))
      (throw (ex-info "Missing Jira credentials" {:email email :api-token (when api-token "***")})))
    {"Authorization" (encode-basic-auth email api-token)
     "Accept" "application/json"
     "Content-Type" "application/json"}))

(defn- base-url
  "Generate base URL for Jira API requests."
  []
  (let [cfg (config/config)
        domain (get-in cfg [:jira :domain])]
    (when (str/blank? domain)
      (throw (ex-info "Missing Jira domain" {:domain domain})))
    (str "https://" domain "/rest/api/3")))

;; Core API functions

(defn- make-request
  "Make an authenticated request to Jira API with error handling."
  [method endpoint & [options]]
  (let [url (str (base-url) endpoint)
        request-options (merge {:headers (auth-headers)
                                :throw-exceptions false
                                :as :json
                                :coerce :always}
                               options)]
    (log/info "Jira API request" {:method method :endpoint endpoint})
    (try
      (let [response (case method
                       :get (http/get url request-options)
                       :post (http/post url request-options)
                       :put (http/put url request-options)
                       :delete (http/delete url request-options))]
        (if (< (:status response) 400)
          (do
            (log/info "Jira API success" {:status (:status response) :endpoint endpoint})
            response)
          (do
            (log/error "Jira API error" {:status (:status response) :endpoint endpoint :body (:body response)})
            (throw (ex-info "Jira API request failed"
                            {:status (:status response)
                             :endpoint endpoint
                             :body (:body response)})))))
      (catch Exception e
        (log/error "Jira API exception" {:endpoint endpoint :error (.getMessage e)})
        (throw e)))))

;; JQL and search operations

(defn search-issues
  "Search for issues using JQL.
   
   Options:
   - :jql - JQL query string (required)
   - :fields - comma-separated string of fields to return (optional)
   - :expand - comma-separated string of fields to expand (optional)
   - :max-results - maximum number of results (default 50)
   - :start-at - starting index for pagination (default 0)"
  [options]
  (let [{:keys [jql fields expand max-results start-at]
         :or {max-results 50 start-at 0}} options]
    (when (str/blank? jql)
      (throw (ex-info "JQL query is required" {:options options})))
    (let [query-params (cond-> {:jql jql
                                :maxResults max-results
                                :startAt start-at}
                         fields (assoc :fields fields)
                         expand (assoc :expand expand))
          response (make-request :get "/search" {:query-params query-params})]
      (:body response))))

(defn get-issue
  "Get a single issue by key with optional field filtering.
   
   Options:
   - :fields - comma-separated string of fields to return (optional)
   - :expand - comma-separated string of fields to expand (optional)"
  [issue-key & [options]]
  (when (str/blank? issue-key)
    (throw (ex-info "Issue key is required" {:issue-key issue-key})))
  (let [{:keys [fields expand]} options
        query-params (cond-> {}
                       fields (assoc :fields fields)
                       expand (assoc :expand expand))
        endpoint (str "/issue/" issue-key)
        response (make-request :get endpoint {:query-params query-params})]
    (:body response)))

(defn get-issue-with-attachments
  "Get an issue with full attachment metadata."
  [issue-key]
  (get-issue issue-key {:fields "summary,description,status,attachment,comment"
                        :expand "attachment"}))

;; Issue operations

(defn get-issue-transitions
  "Get available transitions for an issue."
  [issue-key]
  (when (str/blank? issue-key)
    (throw (ex-info "Issue key is required" {:issue-key issue-key})))
  (let [endpoint (str "/issue/" issue-key "/transitions")
        response (make-request :get endpoint)]
    (:body response)))

(defn transition-issue
  "Transition an issue to a new status.
   
   Parameters:
   - issue-key: the Jira issue key (e.g., 'JESI-1234')
   - transition-id: the ID of the transition to perform (can be numeric ID or status name)
   - comment: optional comment to add during transition"
  [issue-key transition-id & [comment]]
  (when (str/blank? issue-key)
    (throw (ex-info "Issue key is required" {:issue-key issue-key})))
  (when (nil? transition-id)
    (throw (ex-info "Transition ID is required" {:transition-id transition-id})))
  
  ;; If transition-id is a string that's not numeric, try to find the transition by name
  (let [actual-transition-id (if (and (string? transition-id) 
                                      (not (re-matches #"^\d+$" transition-id)))
                               ;; Look up transition by name
                               (let [transitions (:transitions (get-issue-transitions issue-key))
                                     matching-transition (first (filter #(= (str/lower-case (:name (:to %)))
                                                                           (str/lower-case transition-id))
                                                                      transitions))]
                                 (if matching-transition
                                   (:id matching-transition)
                                   (throw (ex-info (str "No transition found for status: " transition-id)
                                                   {:issue-key issue-key
                                                    :requested-status transition-id
                                                    :available (map #(get-in % [:to :name]) transitions)}))))
                               ;; Use the ID as-is
                               transition-id)
        payload (cond-> {:transition {:id (str actual-transition-id)}}
                  comment (assoc :update {:comment [{:add {:body comment}}]}))
        endpoint (str "/issue/" issue-key "/transitions")]
    ;; Use json-params for automatic JSON conversion
    (make-request :post endpoint {:json-params payload})))

;; Comment operations

(defn text-to-adf
  "Convert plain text to Atlassian Document Format.
   Handles multi-line text by splitting into paragraphs.
   Supports basic formatting:
   - Lines starting with '# ' become headings
   - Text between ** becomes bold
   - Lines between ``` become code blocks"
  [text]
  (let [lines (str/split-lines text)
        process-line (fn [line]
                      (cond
                        ;; Heading
                        (str/starts-with? line "# ")
                        {:type "heading"
                         :attrs {:level 3}
                         :content [{:type "text"
                                   :text (subs line 2)}]}
                        
                        ;; Empty line
                        (str/blank? line)
                        {:type "paragraph"
                         :content []}
                        
                        ;; Regular paragraph with bold support
                        :else
                        (let [parts (str/split line #"\*\*")
                              content (map-indexed 
                                      (fn [idx part]
                                        (if (and (odd? idx) (not (str/blank? part)))
                                          {:type "text"
                                           :text part
                                           :marks [{:type "strong"}]}
                                          {:type "text"
                                           :text part}))
                                      parts)]
                          {:type "paragraph"
                           :content (vec (remove #(str/blank? (:text %)) content))})))
        
        ;; Process code blocks
        process-content (fn [lines]
                          (loop [remaining lines
                                 result []
                                 in-code? false
                                 code-lines []]
                            (if (empty? remaining)
                              (if in-code?
                                ;; Close unclosed code block
                                (conj result {:type "codeBlock"
                                            :content [{:type "text"
                                                     :text (str/join "\n" code-lines)}]})
                                result)
                              (let [line (first remaining)]
                                (cond
                                  ;; Start or end code block
                                  (str/starts-with? line "```")
                                  (if in-code?
                                    ;; End code block
                                    (recur (rest remaining)
                                           (conj result {:type "codeBlock"
                                                       :content [{:type "text"
                                                                :text (str/join "\n" code-lines)}]})
                                           false
                                           [])
                                    ;; Start code block
                                    (recur (rest remaining)
                                           result
                                           true
                                           []))
                                  
                                  ;; Inside code block
                                  in-code?
                                  (recur (rest remaining)
                                         result
                                         true
                                         (conj code-lines line))
                                  
                                  ;; Regular line
                                  :else
                                  (recur (rest remaining)
                                         (conj result (process-line line))
                                         false
                                         []))))))]
    
    {:version 1
     :type "doc"
     :content (process-content lines)}))

(defn get-issue-comments
  "Get all comments for an issue."
  [issue-key]
  (when (str/blank? issue-key)
    (throw (ex-info "Issue key is required" {:issue-key issue-key})))
  (let [endpoint (str "/issue/" issue-key "/comment")
        response (make-request :get endpoint)]
    (:body response)))

(defn add-comment
  "Add a comment to an issue.
   
   Parameters:
   - issue-key: the Jira issue key (e.g., 'JESI-1234')
   - comment-body: the comment text (plain text, will be converted to ADF)"
  [issue-key comment-body]
  (when (str/blank? issue-key)
    (throw (ex-info "Issue key is required" {:issue-key issue-key})))
  (when (str/blank? comment-body)
    (throw (ex-info "Comment body is required" {:comment-body comment-body})))
  (let [;; Convert plain text to Atlassian Document Format
        adf-body (text-to-adf comment-body)
        payload {:body adf-body}
        endpoint (str "/issue/" issue-key "/comment")]
    ;; Use json-params for automatic JSON conversion
    (make-request :post endpoint {:json-params payload})))

;; Attachment operations

(defn add-attachment
  "Add an attachment to a Jira issue.
   
   Parameters:
   - issue-key: the Jira issue key (e.g., 'JESI-1234')
   - filename: name of the file
   - content: byte array of file content
   
   Returns:
     Attachment metadata from Jira"
  [issue-key filename content]
  (when (str/blank? issue-key)
    (throw (ex-info "Issue key is required" {:issue-key issue-key})))
  (when (str/blank? filename)
    (throw (ex-info "Filename is required" {:filename filename})))
  (when (nil? content)
    (throw (ex-info "Content is required" {:filename filename})))
  
  (let [url (str (base-url) "/issue/" issue-key "/attachments")
        headers (-> (auth-headers)                         ; use the whole map
                    (dissoc "Content-Type" "Accept")       ; let clj-http set multipart headers
                    (assoc "X-Atlassian-Token" "no-check"))
        ;; Create a temporary directory and file with the exact filename
        temp-dir (java.nio.file.Files/createTempDirectory "jira-upload" (into-array java.nio.file.attribute.FileAttribute []))
        temp-file (java.io.File. (.toFile temp-dir) filename)]
    (try
      (with-open [fos (java.io.FileOutputStream. temp-file)]
        (.write fos content))
      (let [response (http/post url {:headers headers
                                     :multipart [{:name "file"
                                                  :content temp-file
                                                  :filename filename}]
                                     :as :json
                                     :throw-exceptions false})]
        (if (< (:status response) 400)
          (do
            (log/info "Attachment uploaded successfully" 
                     {:issue-key issue-key :filename filename :status (:status response)})
            (:body response))
          (do
            (log/error "Failed to upload attachment" 
                      {:issue-key issue-key :filename filename 
                       :status (:status response) :body (:body response)})
            (throw (ex-info "Failed to upload attachment"
                            {:status (:status response)
                             :body (:body response)
                             :filename filename})))))
      (finally
        (.delete temp-file)
        (java.nio.file.Files/deleteIfExists temp-dir)))))

;; Utility functions

(defn test-connection
  "Test the Jira API connection and authentication."
  []
  (try
    (let [response (make-request :get "/myself")]
      (log/info "Jira connection test successful" {:user (get-in response [:body :displayName])})
      {:success true
       :user (:body response)})
    (catch Exception e
      (log/error "Jira connection test failed" {:error (.getMessage e)})
      {:success false
       :error (.getMessage e)})))

(defn get-user-info
  "Get information about the authenticated user."
  []
  (let [response (make-request :get "/myself")]
    (:body response)))