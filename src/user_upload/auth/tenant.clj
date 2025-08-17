(ns user_upload.auth.tenant
  "Tenant extraction logic for identifying the tenant from Jira tickets.
   
   Expected format in ticket content:
   - customersolutions+<tenant>@jesi.io
   - Service account email patterns
   - Ticket titles or descriptions containing tenant identifiers"
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def ^:private email-pattern
  "Regex pattern to extract tenant from customersolutions+<tenant>@jesi.io format"
  #"customersolutions\+([a-zA-Z0-9_-]+)@jesi\.io")

(def ^:private service-account-pattern
  "Regex pattern for service account emails that might contain tenant info"
  #"([a-zA-Z0-9_-]+)\.service@jesi\.io")

(defn extract-tenant-from-email
  "Extract tenant name from customersolutions+<tenant>@jesi.io format.
   
   Examples:
   - customersolutions+acme@jesi.io -> 'acme'
   - customersolutions+big-corp@jesi.io -> 'big-corp'
   - invalid@example.com -> nil"
  [email-str]
  (when email-str
    (when-let [match (re-find email-pattern email-str)]
      (second match))))

(defn extract-tenant-from-service-account
  "Extract tenant name from service account email format.
   
   Examples:
   - acme.service@jesi.io -> 'acme'
   - big-corp.service@jesi.io -> 'big-corp'"
  [email-str]
  (when email-str
    (when-let [match (re-find service-account-pattern email-str)]
      (second match))))

(defn find-emails-in-text
  "Find all email addresses in a given text string."
  [text]
  (when text
    (re-seq #"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}" text)))

(defn extract-tenant-from-jira-issue
  "Extract tenant name from a Jira issue map.
   
   Searches in the following order:
   1. Issue description for customersolutions+<tenant>@jesi.io
   2. Issue summary/title for customersolutions+<tenant>@jesi.io  
   3. Issue description for service account emails
   4. Comments for customersolutions+<tenant>@jesi.io
   
   Returns:
   - {:tenant \"tenant-name\"} if found
   - {:error \"No tenant found\"} if not found
   - {:error \"Error message\"} if issue data is invalid"
  [issue]
  (try
    (when-not (map? issue)
      (throw (ex-info "Invalid issue data: expected map" {:issue issue})))

    (let [description (get-in issue [:fields :description])
          summary (get-in issue [:fields :summary])
          comments (get-in issue [:fields :comment :comments] [])
          
          ;; Extract all text content to search
          all-text (str/join " " 
                            (filter some? 
                                   [description summary
                                    (str/join " " (map #(get-in % [:body]) comments))]))
          
          ;; Find all emails in the content
          emails (find-emails-in-text all-text)]

      (log/debug "Searching for tenant in issue" {:issue-key (:key issue)
                                                  :emails-found (count emails)})

      ;; Try to extract tenant from emails in priority order
      (or 
        ;; First priority: customersolutions+ format
        (when-let [tenant (some extract-tenant-from-email emails)]
          (log/info "Found tenant from customersolutions email" {:tenant tenant})
          {:tenant tenant})
        
        ;; Second priority: service account format
        (when-let [tenant (some extract-tenant-from-service-account emails)]
          (log/info "Found tenant from service account email" {:tenant tenant})
          {:tenant tenant})
        
        ;; No tenant found
        (do
          (log/warn "No tenant identifier found in issue" 
                   {:issue-key (:key issue)
                    :emails-found emails})
          {:error "No tenant found in issue content"})))

    (catch Exception e
      (log/error e "Error extracting tenant from issue" {:issue issue})
      {:error (str "Error extracting tenant: " (.getMessage e))})))

(defn validate-tenant-name
  "Validate that a tenant name is valid for use with authentication systems.
   
   Valid tenant names:
   - Contains only alphanumeric characters, hyphens, and underscores
   - At least 2 characters long
   - No more than 50 characters long
   
   Returns:
   - {:valid true :tenant \"cleaned-name\"} if valid
   - {:valid false :error \"Error message\"} if invalid"
  [tenant-name]
  (cond
    (not (string? tenant-name))
    {:valid false :error "Tenant name must be a string"}
    
    (< (count tenant-name) 2)
    {:valid false :error "Tenant name must be at least 2 characters"}
    
    (> (count tenant-name) 50)
    {:valid false :error "Tenant name must be no more than 50 characters"}
    
    (not (re-matches #"^[a-zA-Z0-9_-]+$" tenant-name))
    {:valid false :error "Tenant name contains invalid characters (only alphanumeric, hyphens, and underscores allowed)"}
    
    :else
    {:valid true :tenant (str/lower-case tenant-name)}))

(defn extract-and-validate-tenant
  "Extract tenant name from Jira issue and validate it.
   
   Returns:
   - {:tenant \"validated-tenant-name\"} if successful
   - {:error \"Error message\"} if extraction or validation fails"
  [issue]
  (let [extraction-result (extract-tenant-from-jira-issue issue)]
    (if (:error extraction-result)
      extraction-result
      (let [validation-result (validate-tenant-name (:tenant extraction-result))]
        (if (:valid validation-result)
          {:tenant (:tenant validation-result)}
          {:error (:error validation-result)})))))