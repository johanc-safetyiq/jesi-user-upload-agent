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
        ;; Pass the token as environment variable to the subprocess
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

(defn fetch-credentials-for-tenant
  "Fetch credentials for a specific tenant from 1Password.
   
   Since 1Password CLI doesn't support searching by username field content,
   we try multiple strategies:
   1. Try exact match with tenant name
   2. Try common variations (uppercase, with spaces)
   3. List all items and check username fields for email match
   
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
                 env (merge (into {} (System/getenv)) {"OP_SERVICE_ACCOUNT_TOKEN" token})
                 vault-name "Customer Support (Site Registrations)"
                 expected-email (format email-template tenant)
                 
                 ;; Helper function to try getting an item by name
                 try-get-item (fn [item-name]
                               (try
                                 (let [result (process/shell 
                                            {:out :string :err :string :timeout 5000 :env env}
                                            "op" "item" "get" item-name
                                            "--vault" vault-name
                                            "--format" "json"
                                            "--reveal")]
                                   (when (zero? (:exit result))
                                     (let [item (json/parse-string (:out result) true)
                                           email (some->> item :fields 
                                                         (some #(when (or (= "username" (:label %))
                                                                         (= "username" (:id %))) 
                                                                 (:value %))))
                                           password (some->> item :fields 
                                                            (some #(when (or (= "password" (:label %))
                                                                           (= "password" (:id %))) 
                                                                    (:value %))))]
                                       (when (and email password 
                                                 (= (str/lower-case email) (str/lower-case expected-email)))
                                         {:email email :password password :item-name item-name}))))
                                 (catch Exception e
                                   ;; Item not found, return nil
                                   nil)))
                 
                 ;; Try common variations of the tenant name
                 variations [(str/lower-case tenant)  ; lowercase
                           (str/upper-case tenant)  ; uppercase (like SQM)
                           tenant                    ; as-is
                           ;; Try with spaces for compound names
                           (str/replace tenant #"([a-z])([A-Z])" "$1 $2") ; camelCase to spaces
                           ;; Try the email itself as the item name
                           expected-email
                           ;; Try without domain
                           (str "customersolutions+" tenant)
                           ;; Try with hyphens
                           (str/replace tenant #"_" "-")
                           (str/replace tenant #"-" "_")]
                 
                 ;; Try each variation
                 direct-result (some try-get-item variations)]
             
             (if direct-result
               ;; Found via direct lookup
               (let [credentials {:email (:email direct-result) 
                                 :password (:password direct-result)}]
                 (swap! credentials-cache assoc tenant credentials)
                 (log/info "Successfully fetched credentials for tenant via direct lookup" 
                          {:tenant tenant :item-name (:item-name direct-result)})
                 {:success true 
                  :email (:email direct-result)
                  :password (:password direct-result)
                  :cached false
                  :item-used (:item-name direct-result)})
               
               ;; Direct lookup failed, need to search all items
               ;; This is slower but handles cases where item titles don't match tenant names
               (do
                 (log/warn "Direct lookup failed for all variations" 
                          {:tenant tenant 
                           :expected-email expected-email
                           :tried-variations (take 5 variations)})
                 
                 ;; Try using op's search functionality to find items with the email
                 ;; We'll search for items in the vault and filter by those containing the email
                 (log/info "Searching 1Password vault for items with email" {:email expected-email})
                 (let [;; List all items in the vault first
                       list-result (process/shell 
                                  {:out :string :err :string :timeout default-timeout-ms :env env}
                                  "op" "item" "list"
                                  "--vault" vault-name
                                  "--format" "json")
                       ;; Try searching by the email without the domain
                       search-term (str "customersolutions+" tenant)]
                   
                   (if (zero? (:exit list-result))
                     (let [all-items (json/parse-string (:out list-result) true)
                           ;; Filter items that might contain our email in title or other fields
                           ;; This is a heuristic to reduce the number of items we need to fetch
                           potential-items (filter (fn [item]
                                                    (or (str/includes? (str/lower-case (str (:title item))) 
                                                                      (str/lower-case tenant))
                                                        (str/includes? (str/lower-case (str (:title item)))
                                                                      (str/lower-case search-term))
                                                        (str/includes? (str/lower-case (str (:title item)))
                                                                      expected-email)))
                                                  all-items)
                           ;; If no filtered items, check ALL items (fallback)
                           items-to-check (if (empty? potential-items) all-items potential-items)
                           matching-items (atom [])]
                       
                       (log/debug "Checking items for credentials" {:total (count all-items) 
                                                                    :filtered (count items-to-check)})
                       
                       ;; Check each potential item
                       ;; Reduce to 5 items for much better performance
                       (doseq [item (take 5 items-to-check)]
                         (try
                           (let [get-result (process/shell 
                                          {:out :string :err :string :timeout 2000 :env env}
                                          "op" "item" "get" (:id item)
                                          "--vault" vault-name
                                          "--format" "json"  
                                          "--reveal")]
                             (when (zero? (:exit get-result))
                               (let [item-data (json/parse-string (:out get-result) true)
                                     username (some->> item-data :fields 
                                                      (some #(when (or (= "username" (:label %))
                                                                      (= "username" (:id %))) 
                                                              (:value %))))
                                     password (some->> item-data :fields 
                                                      (some #(when (or (= "password" (:label %))
                                                                      (= "password" (:id %))) 
                                                              (:value %))))]
                                 (when (and username password
                                           (= (str/lower-case username) (str/lower-case expected-email)))
                                   (swap! matching-items conj (merge item
                                                                     {:username username
                                                                      :password password}))))))
                           (catch Exception e
                             ;; Skip items that can't be retrieved
                             nil)))
                       
                       (let [matches @matching-items]
                         (if (empty? matches)
                           {:success false
                            :error (format "No 1Password entry found for tenant '%s' (email: %s). Searched %d items. Please ensure the item exists in the 'Customer Support (Site Registrations)' vault." 
                                          tenant 
                                          expected-email
                                          (count items-to-check))}
                           
                           ;; Found matching items - use the most recent one if multiple
                           (let [selected-item (if (> (count matches) 1)
                                              (do
                                                (log/warn "Multiple 1Password items found for tenant" 
                                                         {:tenant tenant 
                                                          :email expected-email
                                                          :count (count matches)
                                                          :items (map :title matches)})
                                                ;; Sort by updated_at and take the most recent
                                                (first (sort-by :updated_at #(compare %2 %1) matches)))
                                              (first matches))]
                             
                             (if (and (:username selected-item) (:password selected-item))
                               (let [credentials {:email (:username selected-item) 
                                                 :password (:password selected-item)}]
                                 (swap! credentials-cache assoc tenant credentials)
                                 (log/info "Successfully fetched credentials for tenant via search" 
                                          {:tenant tenant 
                                           :item-title (:title selected-item)
                                           :multiple-items (> (count matches) 1)})
                                 {:success true 
                                  :email (:username selected-item)
                                  :password (:password selected-item)
                                  :cached false
                                  :multiple-items-found (> (count matches) 1)
                                  :item-used (:title selected-item)})
                               {:success false
                                :error "Invalid credential format from 1Password"})))))
                     
                     {:success false
                      :error (format "Failed to list 1Password items: %s" (:err list-result))}))
                 
                 #_(let [list-result (process/shell 
                                  {:out :string :err :string :timeout default-timeout-ms :env env}
                                  "op" "item" "list"
                                  "--vault" vault-name
                                  "--format" "json")]
                   
                   (if (zero? (:exit list-result))
                     (let [items (json/parse-string (:out list-result) true)
                           ;; Check each item's username field
                           matching-items (atom [])]
                       
                       (doseq [item items]
                         (try
                           (let [get-result (process/shell 
                                          {:out :string :err :string :timeout 5000 :env env}
                                          "op" "item" "get" (:id item)
                                          "--vault" vault-name
                                          "--format" "json")]
                             (when (zero? (:exit get-result))
                               (let [item-data (json/parse-string (:out get-result) true)
                                     username (some->> item-data :fields 
                                                      (some #(when (= "username" (:label %)) (:value %))))]
                                 (when (and username
                                           (= (str/lower-case username) (str/lower-case expected-email)))
                                   (swap! matching-items conj item)))))
                           (catch Exception e
                             ;; Skip items that can't be retrieved
                             nil)))
                       
                       (let [matches @matching-items]
                         (if (empty? matches)
                           {:success false
                            :error (format "No 1Password entry found for email '%s'" expected-email)}
                           
                           ;; Found matching items - use the most recent one if multiple
                           (let [selected-item (if (> (count matches) 1)
                                              (do
                                                (log/warn "Multiple 1Password items found for tenant" 
                                                         {:tenant tenant 
                                                          :email expected-email
                                                          :count (count matches)
                                                          :items (map :title matches)})
                                                ;; Sort by updated time and take the most recent
                                                (first (sort-by :updated_at #(compare %2 %1) matches)))
                                              (first matches))
                                 
                                 ;; Get full credentials for the selected item
                                 item-result (process/shell 
                                            {:out :string :err :string :timeout default-timeout-ms :env env}
                                            "op" "item" "get" (:id selected-item)
                                            "--vault" vault-name
                                            "--format" "json"
                                            "--reveal")]
                             
                             (if (zero? (:exit item-result))
                               (let [item (json/parse-string (:out item-result) true)
                                     email (some->> item :fields 
                                                   (some #(when (= "username" (:label %)) (:value %))))
                                     password (some->> item :fields 
                                                      (some #(when (= "password" (:label %)) (:value %))))]
                                 
                                 (if (and email password (not (str/blank? email)) (not (str/blank? password)))
                                   (let [credentials {:email email :password password}]
                                     (swap! credentials-cache assoc tenant credentials)
                                     (log/info "Successfully fetched credentials for tenant via search" 
                                              {:tenant tenant 
                                               :item-title (:title selected-item)
                                               :multiple-items (> (count matches) 1)})
                                     {:success true 
                                      :email email 
                                      :password password 
                                      :cached false
                                      :multiple-items-found (> (count matches) 1)
                                      :item-used (:title selected-item)})
                                   {:success false
                                    :error "Invalid credential format from 1Password"}))
                               {:success false
                                :error (format "Failed to get item details: %s" (:err item-result))}))))
                     
                     {:success false
                      :error (format "Failed to list 1Password items: %s" (:err list-result))})))))))))
     
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