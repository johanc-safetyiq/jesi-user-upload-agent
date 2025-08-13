(ns user-upload.jira.approval
  "Approval detection logic for user upload requests in Jira comments."
  (:require
    [user-upload.jira.client :as jira-client]
    [user-upload.config :as config]
    [user-upload.log :as log]
    [clojure.string :as str]))

;; Constants and patterns

(def ^:private approval-request-prefix "[BOT:user-upload:approval-request:v2]")

(defn- normalize-comment-body
  "Normalize comment body for comparison - lowercase, collapse whitespace."
  [body]
  (when body
    (-> body
        str/lower-case
        (str/replace #"\s+" " ")
        str/trim)))

;; Bot identification

(defn- is-bot-comment?
  "Check if a comment was made by the bot account."
  [comment]
  (let [config (config/load-config)
        bot-account-id (:jira-bot-account-id config)
        bot-account-name (:jira-bot-account-name config)
        author-id (get-in comment [:author :accountId])
        author-name (get-in comment [:author :displayName])]
    (cond
      (and bot-account-id (not (str/blank? bot-account-id)))
      (= author-id bot-account-id)
      
      (and bot-account-name (not (str/blank? bot-account-name)))
      (= author-name bot-account-name)
      
      :else
      false)))

(defn- is-human-comment?
  "Check if a comment was made by a human (not the bot)."
  [comment]
  (not (is-bot-comment? comment)))

;; Approval request detection

(defn extract-text-from-adf
  "Extract plain text from ADF (Atlassian Document Format) structure.
   Returns the concatenated text content from all text nodes."
  [adf-content]
  (cond
    ;; If it's already a string, return it
    (string? adf-content) adf-content
    
    ;; If it's a map with content field (ADF structure)
    (and (map? adf-content) (:content adf-content))
    (str/join " " (map extract-text-from-adf (:content adf-content)))
    
    ;; If it's a text node
    (and (map? adf-content) (= (:type adf-content) "text"))
    (:text adf-content)
    
    ;; If it's a sequence, process each item
    (sequential? adf-content)
    (str/join " " (map extract-text-from-adf adf-content))
    
    ;; Otherwise return empty string
    :else ""))

(defn- is-approval-request-comment?
  "Check if a comment is an approval request from the bot.
   We check if the comment starts with the approval request prefix,
   regardless of author, since the bot account might not be configured."
  [comment]
  (let [body-text (extract-text-from-adf (:body comment))]
    (str/starts-with? body-text approval-request-prefix)))

(defn find-latest-approval-request
  "Find the latest approval request comment by the bot in the given comments.
   Returns the comment or nil if none found."
  [comments]
  (log/debug "Searching for latest approval request" {:total-comments (count comments)})
  (->> comments
       (filter is-approval-request-comment?)
       (sort-by :created)
       last))

;; Approval detection

(defn- is-approval-comment?
  "Check if a comment contains exactly 'approved' (case-insensitive, whitespace normalized)."
  [comment]
  (let [body-text (extract-text-from-adf (:body comment))]
    (= "approved" (normalize-comment-body body-text))))

(defn find-approval-after-request
  "Find human approval comments after the given request comment.
   Returns the first approval comment found or nil."
  [comments request-comment]
  (when request-comment
    (let [request-created (:created request-comment)
          request-author-id (get-in request-comment [:author :accountId])]
      (log/debug "Searching for approval after request" 
                 {:request-id (:id request-comment) 
                  :request-created request-created
                  :total-comments (count comments)})
      (->> comments
           (filter #(> (compare (:created %) request-created) 0)) ; Created after request
           ;; Exclude comments from the same author as the request (assumed to be bot)
           (filter #(not= (get-in % [:author :accountId]) request-author-id))
           (filter is-approval-comment?) ; Contains "approved"
           (sort-by :created) ; Oldest first
           first))))

;; Main approval logic

(defn check-approval-status
  "Check the approval status for an issue.
   
   Returns a map with:
   - :status - one of :no-request, :pending, :approved
   - :request-comment - the latest approval request comment (if any)
   - :approval-comment - the approval comment (if approved)
   - :message - human-readable status message"
  [issue-key]
  (log/info "Checking approval status" {:issue-key issue-key})
  (try
    (let [comments-response (jira-client/get-issue-comments issue-key)
          all-comments (:comments comments-response)
          latest-request (find-latest-approval-request all-comments)]
      
      (cond
        ;; No approval request found
        (nil? latest-request)
        (do
          (log/info "No approval request found" {:issue-key issue-key})
          {:status :no-request
           :message "No approval request found from bot"})
        
        ;; Look for approval after the latest request
        :else
        (let [approval-comment (find-approval-after-request all-comments latest-request)]
          (if approval-comment
            (do
              (log/info "Approval found" {:issue-key issue-key 
                                          :request-id (:id latest-request)
                                          :approval-id (:id approval-comment)})
              {:status :approved
               :request-comment (assoc latest-request :adf-body (:body latest-request))
               :approval-comment approval-comment
               :message (format "Approved by %s after request %s" 
                               (get-in approval-comment [:author :displayName])
                               (:id latest-request))})
            (do
              (log/info "Pending approval" {:issue-key issue-key 
                                           :request-id (:id latest-request)})
              {:status :pending
               :request-comment (assoc latest-request :adf-body (:body latest-request))
               :message (format "Pending approval for request %s" (:id latest-request))})))))
    (catch Exception e
      (log/error "Failed to check approval status" {:issue-key issue-key :error (.getMessage e)})
      {:status :error
       :error (.getMessage e)
       :message (format "Error checking approval status: %s" (.getMessage e))})))

(defn request-approval
  "Post an approval request comment to an issue."
  [issue-key request-details]
  (let [comment-body (str approval-request-prefix "\n"
                          "This user upload request requires approval.\n\n"
                          "**Request Details:**\n"
                          (format "- Total users: %s\n" (:user-count request-details))
                          (format "- Total teams: %s\n" (:team-count request-details))
                          (when-let [file (:filename request-details)]
                            (format "- File: %s\n" file))
                          "\n**To approve:** Reply with exactly `approved` (case-insensitive)\n"
                          "\nThis request was generated automatically by the user-upload agent.")]
    (log/info "Requesting approval" {:issue-key issue-key :details request-details})
    (try
      (let [response (jira-client/add-comment issue-key comment-body)]
        (log/info "Approval request posted" {:issue-key issue-key :comment-id (:id response)})
        {:success true
         :comment-id (:id response)
         :message "Approval request posted successfully"})
      (catch Exception e
        (log/error "Failed to post approval request" {:issue-key issue-key :error (.getMessage e)})
        {:success false
         :error (.getMessage e)
         :message (format "Failed to post approval request: %s" (.getMessage e))}))))

;; Utility functions

(defn is-approved?
  "Quick check if an issue is approved. Returns boolean."
  [issue-key]
  (= :approved (:status (check-approval-status issue-key))))

(defn wait-for-approval?
  "Quick check if an issue is waiting for approval. Returns boolean."
  [issue-key]
  (= :pending (:status (check-approval-status issue-key))))

(defn needs-approval-request?
  "Quick check if an issue needs an approval request. Returns boolean."
  [issue-key]
  (= :no-request (:status (check-approval-status issue-key))))