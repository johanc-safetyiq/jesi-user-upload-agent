(ns user-upload.auth.onepassword
  "1Password CLI integration for fetching tenant-specific authentication credentials.
   
   This module provides functions to securely retrieve service account passwords
   from 1Password using the op CLI tool with service account token authentication.
   Credentials are cached in memory during runtime to avoid repeated CLI calls."
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [user-upload.config :as config]))

(def ^:private default-timeout-ms 10000) ; 10 seconds
(def ^:private credentials-cache (atom {})) ; Cache for runtime credential storage

(defn- get-op-token
  "Get the 1Password service account token from config or environment."
  []
  (or 
    ;; Try to get from our loaded config/env vars
    (get-in (config/config) [:op-service-account-token])
    ;; Fallback to system environment
    (System/getenv "OP_SERVICE_ACCOUNT_TOKEN")))

(defn check-op-availability
  "Check if 1Password CLI (op) is available with service account token.
   
   Returns:
     Map with keys:
       :available - Boolean indicating if op CLI is available
       :authenticated - Boolean indicating if service account token is valid
       :version - CLI version (if available)
       :error - Error message (if not available)"
  []
  (try
    ;; Check if OP_SERVICE_ACCOUNT_TOKEN is available
    (let [token (get-op-token)]
      (if (str/blank? token)
        {:available false 
         :authenticated false
         :error "OP_SERVICE_ACCOUNT_TOKEN not set in environment or config"}
        
        ;; Try to list vaults to verify token works
        ;; Pass the token as additional environment variable to the subprocess
        (let [extra-env {"OP_SERVICE_ACCOUNT_TOKEN" token}
              result (process/shell {:out :string :err :string :timeout 5000 :extra-env extra-env}
                                    "op" "vault" "list" "--format=json")]
          (if (zero? (:exit result))
            {:available true 
             :authenticated true
             :version "Service account authenticated"}
            {:available false 
             :authenticated false
             :error (str "Service account token invalid or expired: " (:err result))}))))
    (catch Exception e
      {:available false :error (.getMessage e)})))

(defn fetch-credentials-for-tenant
  "Fetch credentials for a specific tenant from 1Password.
   
   Searches the 'Customer Support (Site Registrations)' vault for items that contain
   the expected email pattern (customersolutions+<tenant>@jesi.io) in their username field.
   If multiple matches are found, uses the most recently updated one.
   
   Args:
     tenant - The tenant name (e.g., 'elecnor')
     email-template - Email template with %s placeholder (default: 'customersolutions+%s@jesi.io')
   
   Returns:
     Map with keys:
       :success - Boolean indicating if fetch succeeded
       :email - The username/email (if successful)
       :password - The password (if successful)
       :multiple-items-found - Boolean indicating if multiple matches were found
       :item-used - Title of the 1Password item that was used
       :error - Error message (if failed)"
  ([tenant]
   (fetch-credentials-for-tenant tenant "customersolutions+%s@jesi.io"))
  ([tenant email-template]
   (try
     (let [cached-value (get @credentials-cache tenant)]
       
       ;; Return cached value if available
       (if cached-value
         (do
           (log/debug "Using cached credentials for tenant:" tenant)
           {:success true 
            :email (:email cached-value)
            :password (:password cached-value)
            :cached true})
         
         ;; Fetch from 1Password using service account
         (do
           (log/info "Fetching credentials from 1Password for tenant:" tenant)
           (let [token (get-op-token)
                 extra-env {"OP_SERVICE_ACCOUNT_TOKEN" token}
                 vault-name "Customer Support (Site Registrations)"
                 expected-email (format email-template tenant)
                 
                 ;; List all items in the vault
                 list-result (process/shell {:out :string :err :string :timeout default-timeout-ms :extra-env extra-env}
                                           "op" "item" "list"
                                           "--vault" vault-name
                                           "--format" "json")
                 {:keys [exit out err]} list-result]
             
             (if (zero? exit)
               ;; Successfully got the list of items
               (let [items (json/parse-string out true)
                     ;; Now get details for each item to check the username field
                     items-with-details (atom [])]
                 
                 ;; Check each item's username field
                 (doseq [item items]
                   (try
                     (let [detail-result (process/shell {:out :string :err :string :timeout 5000 :extra-env extra-env}
                                                       "op" "item" "get" (:id item)
                                                       "--vault" vault-name
                                                       "--fields" "username"
                                                       "--format" "json")]
                       (when (zero? (:exit detail-result))
                         (let [username (json/parse-string (:out detail-result) true)]
                           (when (and username 
                                    (string? username)
                                    (= (str/lower-case username) (str/lower-case expected-email)))
                             (swap! items-with-details conj item)))))
                     (catch Exception e
                       ;; Skip items that can't be retrieved
                       nil)))
                 
                 (let [matching-items @items-with-details]
                   (if (empty? matching-items)
                     ;; No matching items found
                     {:success false
                      :error (format "No 1Password entry found for email '%s'" expected-email)}
                     
                     ;; Found matching items - get the most recent one if there are multiple
                     (let [selected-item (if (> (count matching-items) 1)
                                          (do
                                            (log/warn "Multiple 1Password items found for tenant" 
                                                     {:tenant tenant 
                                                      :email expected-email
                                                      :count (count matching-items)
                                                      :items (map :title matching-items)})
                                            ;; Sort by updated time and take the most recent
                                            ;; Note: 1Password items have updated_at field
                                            (first (sort-by :updated_at #(compare %2 %1) matching-items)))
                                          (first matching-items))
                           
                           ;; Get the full credentials for the selected item
                           item-result (process/shell {:out :string :err :string :timeout default-timeout-ms :extra-env extra-env}
                                                     "op" "item" "get" (:id selected-item)
                                                     "--vault" vault-name
                                                     "--fields" "label=username,label=password"
                                                     "--reveal")
                           {:keys [exit out err]} item-result]
                       
                       (if (zero? exit)
                         (let [output (str/trim out)
                               parts (str/split output #"," 2)
                               raw-email (when (>= (count parts) 1) (first parts))
                               email (when raw-email 
                                      (-> raw-email
                                          str/trim
                                          (str/replace #"^[\"']+" "")
                                          (str/replace #"[\"']+$" "")
                                          str/trim))
                               password (when (>= (count parts) 2) (str/trim (second parts)))]
                           (if (and email password (not (str/blank? email)) (not (str/blank? password)))
                             (let [credentials {:email email :password password}]
                               (swap! credentials-cache assoc tenant credentials)
                               (log/info "Successfully fetched credentials for tenant" 
                                        {:tenant tenant 
                                         :item-title (:title selected-item)
                                         :multiple-items (> (count matching-items) 1)})
                               {:success true 
                                :email email 
                                :password password 
                                :cached false
                                :multiple-items-found (> (count matching-items) 1)
                                :item-used (:title selected-item)})
                             {:success false
                              :error "Invalid credential format from 1Password"}))
                         {:success false
                          :error (format "Failed to get item details: %s" err)})))))
               
               ;; Failed to list items
               {:success false
                :error (format "Failed to list 1Password items: %s" err)})))))
     
     (catch Exception e
       (log/error e "Error fetching credentials from 1Password for tenant:" tenant)
       {:success false :error (.getMessage e)}))))

(defn get-tenant-credentials
  "Get complete authentication credentials for a tenant.
   
   This is the main entry point for getting tenant credentials.
   It handles the full flow including email template formatting.
   
   Args:
     tenant - Tenant name (e.g., 'elecnor')
     email-template - Email template with %s placeholder (default: 'customersolutions+%s@jesi.io')
   
   Returns:
     Map with keys:
       :success - Boolean indicating if retrieval succeeded
       :email - Service account email (if successful)
       :password - Service account password (if successful)
       :error - Error message (if failed)
       :cached - Boolean indicating if result was from cache
       :multiple-items-found - Boolean if multiple 1Password items matched
       :item-used - Title of the 1Password item used"
  ([tenant]
   (get-tenant-credentials tenant "customersolutions+%s@jesi.io"))
  ([tenant email-template]
   (fetch-credentials-for-tenant tenant email-template)))

(defn clear-credential-cache
  "Clear the credential cache. Useful for testing or security purposes."
  []
  (reset! credentials-cache {})
  (log/info "Credential cache cleared"))

(defn get-cache-stats
  "Get statistics about the credential cache."
  []
  {:cached-tenants (count @credentials-cache)
   :cache-keys (keys @credentials-cache)})

(comment
  ;; Check if 1Password CLI is available with service account
  (check-op-availability)
  
  ;; Get credentials for a tenant
  (fetch-credentials-for-tenant "elecnor")
  
  ;; Get full credentials with email template
  (get-tenant-credentials "elecnor")
  
  ;; Cache management
  (get-cache-stats)
  (clear-credential-cache)
  )