(ns user_upload.auth.onepassword
  "1Password CLI integration with bulk pre-loading of all credentials.
   
   This module fetches ALL credentials from the vault once at startup,
   building a cache that maps email addresses to credentials.
   This avoids timeouts when searching for specific credentials later."
  (:require [babashka.process :as process]
            [cheshire.core :as json]
            [user_upload.log :as log]
            [clojure.string :as str]
            [user_upload.config :as config]))

(def ^:private default-timeout-ms 10000)
(def ^:private credentials-cache (atom {}))
(def ^:private cache-loaded? (atom false))

(defn- get-op-token
  "Get the 1Password service account token from config or environment."
  []
  (or 
    (get-in (config/config) [:op-service-account-token])
    (System/getenv "OP_SERVICE_ACCOUNT_TOKEN")))

(defn check-op-availability
  "Check if 1Password CLI (op) is available with service account token."
  []
  (try
    (let [token (get-op-token)]
      (if (str/blank? token)
        {:available false 
         :authenticated false
         :error "OP_SERVICE_ACCOUNT_TOKEN not set in environment or config"}
        
        (let [env (merge (into {} (System/getenv)) {"OP_SERVICE_ACCOUNT_TOKEN" token})
              result (process/shell {:out :string :err :string :timeout 5000 :env env}
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

(defn- fetch-item-details
  "Fetch details for a single 1Password item.
   Returns {:username <email> :password <pwd> :title <title> :updated_at <date>} or nil"
  [item-id env]
  (try
    (let [result (process/shell 
                  {:out :string :err :string :timeout 3000 :env env}
                  "op" "item" "get" item-id
                  "--vault" "Customer Support (Site Registrations)"
                  "--format" "json"
                  "--reveal")]
      (when (zero? (:exit result))
        (let [data (json/parse-string (:out result) true)
              username (some #(when (or (= "username" (:label %))
                                        (= "username" (:id %)))
                               (:value %))
                            (:fields data))
              password (some #(when (or (= "password" (:label %))
                                        (= "password" (:id %)))
                               (:value %))
                            (:fields data))]
          (when (and username password)
            {:username (str/trim username)
             :password password
             :title (:title data)
             :updated_at (:updated_at data)
             :item_id item-id}))))
    (catch Exception e
      (log/debug "Failed to fetch item" {:item-id item-id :error (.getMessage e)})
      nil)))

(defn- process-items-batch
  "Process a batch of items in parallel.
   Returns a vector of credential maps."
  [items env batch-size]
  (let [futures (doall
                 (map (fn [item]
                        (future
                          (fetch-item-details (:id item) env)))
                      items))]
    ;; Wait for all futures and filter out nils
    (filter some? (map deref futures))))

(defn preload-all-credentials!
  "Pre-load ALL credentials from the 1Password vault.
   This fetches all items once and builds a cache mapping emails to credentials.
   If multiple items have the same email, keeps the most recently updated one.
   
   Returns:
     {:success true/false
      :total-items <count>
      :credentials-loaded <count>
      :duplicates <count>
      :error <message if failed>}"
  []
  (try
    (log/info "Pre-loading 1Password credentials" {:vault "Customer Support (Site Registrations)"})
    (let [token (get-op-token)
          env (merge (into {} (System/getenv)) {"OP_SERVICE_ACCOUNT_TOKEN" token})
          vault-name "Customer Support (Site Registrations)"
          
          ;; List all items in the vault
          list-result (process/shell 
                       {:out :string :err :string :timeout 15000 :env env}
                       "op" "item" "list" 
                       "--vault" vault-name
                       "--format=json")]
      
      (if-not (zero? (:exit list-result))
        {:success false
         :error (format "Failed to list 1Password items: %s" (:err list-result))}
        
        (let [all-items (json/parse-string (:out list-result) true)
              total-count (count all-items)
              _ (log/debug "Found items in vault" {:count total-count :vault vault-name})
              
              ;; Process items in batches for parallelism
              batch-size 10
              batches (partition-all batch-size all-items)
              
              ;; Process each batch and collect results
              all-credentials (atom [])
              _ (doseq [[idx batch] (map-indexed vector batches)]
                  (log/progress "Processing 1Password batch" {:current (inc idx) 
                                                              :total (count batches)
                                                              :size (count batch)})
                  (let [batch-results (process-items-batch batch env batch-size)]
                    (swap! all-credentials concat batch-results)))
              
              credentials @all-credentials
              
              ;; Build the cache map, handling duplicates
              email-map (reduce
                        (fn [acc cred]
                          (let [email (str/lower-case (:username cred))
                                existing (get acc email)]
                            (if (or (nil? existing)
                                    ;; Keep the more recent one
                                    (pos? (compare (:updated_at cred) 
                                                  (:updated_at existing))))
                              (assoc acc email cred)
                              acc)))
                        {}
                        credentials)
              
              ;; Count duplicates
              email-counts (frequencies (map #(str/lower-case (:username %)) credentials))
              duplicates (count (filter #(> (val %) 1) email-counts))]
          
          ;; Update the cache
          (reset! credentials-cache email-map)
          (reset! cache-loaded? true)
          
          (log/info "1Password credential pre-loading complete" 
                   {:total-items total-count
                    :credentials-loaded (count email-map)
                    :duplicates duplicates})
          
          {:success true
           :total-items total-count
           :credentials-loaded (count email-map)
           :duplicates duplicates})))
    
    (catch Exception e
      (log/error "Error pre-loading 1Password credentials" e {})
      {:success false :error (.getMessage e)})))

(defn ensure-cache-loaded!
  "Ensure the cache is loaded. If not, load it.
   Returns true if cache is available, false otherwise."
  []
  (if @cache-loaded?
    true
    (let [result (preload-all-credentials!)]
      (:success result))))

(defn fetch-credentials-for-email
  "Fetch credentials for a specific email from the pre-loaded cache.
   
   If cache is not loaded, attempts to load it first.
   
   Args:
     email - The exact email to search for
   
   Returns:
     {:success true/false
      :email <email>
      :password <password>
      :item-used <title>
      :cached true
      :error <message if not found>}"
  [email]
  (try
    ;; Ensure cache is loaded
    (when-not (ensure-cache-loaded!)
      (throw (Exception. "Failed to load 1Password cache")))
    
    (let [email-lower (str/lower-case email)
          cached-cred (get @credentials-cache email-lower)]
      
      (if cached-cred
        (do
          (log/debug "Found credentials in cache" {:email email :item (:title cached-cred)})
          {:success true
           :email (:username cached-cred)
           :password (:password cached-cred)
           :item-used (:title cached-cred)
           :cached true})
        
        (do
          (log/warn "No credentials found in cache" {:email email})
          {:success false
           :error (format "No 1Password entry found with username '%s'. Please ensure the item exists in the 'Customer Support (Site Registrations)' vault with the correct username field." 
                         email)
           :cached true})))
    
    (catch Exception e
      (log/error e "Error fetching credentials for email:" email)
      {:success false :error (.getMessage e)})))

(defn fetch-credentials-for-tenant
  "Fetch credentials for a tenant by constructing the expected email.
   
   This is a compatibility wrapper that constructs the email from tenant name.
   
   Args:
     tenant - The tenant name (e.g., 'boartlongyearapac')
     email-template - Email template with %s placeholder (default: 'customersolutions+%s@jesi.io')
   
   Returns:
     Same as fetch-credentials-for-email"
  ([tenant]
   (fetch-credentials-for-tenant tenant "customersolutions+%s@jesi.io"))
  ([tenant email-template]
   (let [email (format email-template tenant)]
     (fetch-credentials-for-email email))))

(defn get-tenant-credentials
  "Get complete authentication credentials for a tenant.
   
   This is the main entry point for getting tenant credentials.
   It handles the full flow including email template formatting.
   
   Args:
     tenant - Tenant name (e.g., 'elecnor')
     email-template - Email template with %s placeholder (default: 'customersolutions+%s@jesi.io')
   
   Returns:
     Same as fetch-credentials-for-email"
  ([tenant]
   (get-tenant-credentials tenant "customersolutions+%s@jesi.io"))
  ([tenant email-template]
   (fetch-credentials-for-tenant tenant email-template)))

(defn clear-credential-cache
  "Clear the credential cache. Useful for testing or forcing a reload."
  []
  (reset! credentials-cache {})
  (reset! cache-loaded? false)
  (log/info "Credential cache cleared"))

(defn get-cache-stats
  "Get statistics about the credential cache."
  []
  {:loaded? @cache-loaded?
   :cached-credentials (count @credentials-cache)
   :sample-emails (take 10 (keys @credentials-cache))})

(defn reload-credentials!
  "Force a reload of all credentials from 1Password.
   Clears the existing cache and fetches everything again."
  []
  (clear-credential-cache)
  (preload-all-credentials!))

(comment
  ;; Check if 1Password CLI is available
  (check-op-availability)
  
  ;; Pre-load all credentials (do this once at startup)
  (preload-all-credentials!)
  
  ;; Get credentials by email (uses cache)
  (fetch-credentials-for-email "customersolutions+boartlongyearapac@jesi.io")
  
  ;; Get credentials for a tenant (backward compatibility)
  (get-tenant-credentials "qbirt")
  
  ;; Cache management
  (get-cache-stats)
  (reload-credentials!)
  )