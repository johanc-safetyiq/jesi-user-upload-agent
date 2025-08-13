(ns user-upload.api.client
  "Backend API client for authentication and CRUD operations."
  (:require
    [clj-http.client :as http]
    [cheshire.core :as json]
    [clojure.string :as str]
    [user-upload.config :as config]
    [user-upload.log :as log]))

;; Token storage (in-memory for now)
(def ^:private auth-token (atom nil))

;; Configuration and URL helpers

(defn- base-api-url
  "Get base URL for v1 API (Java backend)."
  []
  (let [url (get-in (config/load-config) [:api :base-url])]
    (when (str/blank? url)
      (throw (ex-info "Missing BASE_API_URL configuration" {:url url})))
    url))

(defn- base-clj-api-url
  "Get base URL for v2 API (Clojure backend)."
  []
  (let [url (get-in (config/load-config) [:api :base-clj-url])]
    (when (str/blank? url)
      (throw (ex-info "Missing BASE_CLJ_API_URL configuration" {:url url})))
    url))

(defn- auth-headers
  "Generate authentication headers with stored token."
  []
  (if-let [token @auth-token]
    {"Authorization" token
     "Accept" "application/json"}
    (throw (ex-info "No authentication token available. Please login first." {}))))

;; Core request helper

(defn- make-request
  "Make an authenticated request to backend API with error handling."
  [method base-url endpoint & [options]]
  (let [url (str base-url endpoint)
        base-headers (if (:skip-auth options)
                       {"Accept" "application/json"}
                       (auth-headers))
        ;; When using :json-params, don't set Content-Type header
        headers (if (:json-params options)
                  (dissoc base-headers "Content-Type")
                  base-headers)
        request-options (-> options
                            (dissoc :skip-auth)
                            (merge {:headers headers
                                    :throw-exceptions false
                                    :as :json
                                    :coerce :always}))]
    (log/info "Backend API request" {:method method :endpoint endpoint :base-url base-url})
    (try
      (let [response (case method
                       :get (http/get url request-options)
                       :post (http/post url request-options)
                       :put (http/put url request-options)
                       :delete (http/delete url request-options))]
        (if (< (:status response) 400)
          (do
            (log/info "Backend API success" {:status (:status response) :endpoint endpoint})
            response)
          (do
            (log/error "Backend API error" {:status (:status response) :endpoint endpoint :body (:body response)})
            (throw (ex-info "Backend API request failed"
                            {:status (:status response)
                             :endpoint endpoint
                             :body (:body response)})))))
      (catch Exception e
        (log/error "Backend API exception" {:endpoint endpoint :error (.getMessage e)})
        (throw e)))))

;; Authentication

(defn login
  "Authenticate with the backend API and store the token.
   Returns authentication result with token information."
  [email password]
  (log/info "Attempting backend API login" {:email email})
  (try
    (let [credentials {:email email :password password}
          response (make-request :post (base-api-url) "/passwords/authenticate"
                                 {:json-params credentials
                                  :skip-auth true})]
      (if-let [token (get-in response [:body :token])]
        (do
          (reset! auth-token token)
          (log/info "Backend API login successful" {:email email})
          {:success true
           :token "***stored***"
           :message "Login successful"})
        (do
          (log/error "Backend API login failed - no token in response" {:response (:body response)})
          {:success false
           :error "No token in response"
           :message "Login failed - invalid response format"})))
    (catch Exception e
      (log/error "Backend API login failed" {:email email :error (.getMessage e)})
      {:success false
       :error (.getMessage e)
       :message (format "Login failed: %s" (.getMessage e))})))

(defn login-with-config
  "Login using credentials from configuration."
  []
  (let [config (config/load-config)
        email (:auth-email config)
        password (:auth-password config)]
    (when (or (str/blank? email) (str/blank? password))
      (throw (ex-info "Missing authentication credentials in configuration" 
                      {:email email :password (when password "***set***")})))
    (login email password)))

(defn logout
  "Clear the stored authentication token."
  []
  (reset! auth-token nil)
  (log/info "Logged out from backend API")
  {:success true :message "Logged out successfully"})

(defn authenticated?
  "Check if we have a valid authentication token."
  []
  (not (nil? @auth-token)))

;; Fetch operations (v1 and v2)

(defn get-profile
  "Get user profile information (v1 API)."
  []
  (log/info "Fetching user profile")
  (let [response (make-request :get (base-api-url) "/profiles")]
    (:body response)))

(defn get-roles
  "Get all available roles (v1 API)."
  []
  (log/info "Fetching roles")
  (let [response (make-request :get (base-api-url) "/roles")]
    (:body response)))

(defn search-users
  "Search for users (v2 API).
   Returns a map with :users key containing user array."
  []
  (log/info "Searching users")
  (let [response (make-request :post (base-clj-api-url) "/users/search"
                               {:json-params {}})]
    (:body response)))

(defn search-teams
  "Search for teams (v2 API).
   Returns a map with :teams key containing team array."
  []
  (log/info "Searching teams")
  (let [response (make-request :post (base-clj-api-url) "/teams/search"
                               {:json-params {}})]
    (:body response)))

;; Create operations (v1)

(defn create-user
  "Create a new user (v1 API).
   
   User data should include:
   - :firstName (required)
   - :lastName (required) 
   - :email (required)
   - :title (optional)
   - :mobileNumbers (array of {:number :isActive})
   - :teamIds (array of team IDs)
   - :defaultTeam (optional, defaults to first teamId)
   - :roleId (required)"
  [user-data]
  (log/info "Creating user" {:email (:email user-data)})
  (when (some str/blank? [(:firstName user-data) (:lastName user-data) (:email user-data)])
    (throw (ex-info "Missing required user fields" {:user-data user-data})))
  (when (str/blank? (:roleId user-data))
    (throw (ex-info "Missing required roleId" {:user-data user-data})))
  (when (empty? (:teamIds user-data))
    (throw (ex-info "Missing required teamIds" {:user-data user-data})))
  
  (let [response (make-request :post (base-api-url) "/users"
                               {:json-params user-data})]
    (:body response)))

(defn create-team
  "Create a new team (v1 API).
   
   Team data should include:
   - :name (required)
   - :members (array of user IDs)
   - :escalationLevels (array of {:minutes :escalationContacts})"
  [team-data]
  (log/info "Creating team" {:name (:name team-data)})
  (when (str/blank? (:name team-data))
    (throw (ex-info "Missing required team name" {:team-data team-data})))
  (when (empty? (:members team-data))
    (throw (ex-info "Missing required team members" {:team-data team-data})))
  (when (empty? (:escalationLevels team-data))
    (throw (ex-info "Missing required escalation levels" {:team-data team-data})))
  
  (let [response (make-request :post (base-api-url) "/teams"
                               {:json-params team-data})]
    (:body response)))

;; Helper functions for common operations

(defn get-all-data
  "Fetch all basic data needed for user/team operations.
   Returns a map with :profile, :roles, :users, :teams keys."
  []
  (log/info "Fetching all backend data")
  (try
    {:profile (get-profile)
     :roles (get-roles)
     :users (:users (search-users))
     :teams (:teams (search-teams))}
    (catch Exception e
      (log/error "Failed to fetch all backend data" {:error (.getMessage e)})
      (throw e))))

(defn find-user-by-email
  "Find a user by email address."
  [email]
  (log/debug "Finding user by email" {:email email})
  (let [users (:users (search-users))]
    (first (filter #(= (:email %) email) users))))

(defn find-team-by-name
  "Find a team by name."
  [team-name]
  (log/debug "Finding team by name" {:name team-name})
  (let [teams (:teams (search-teams))]
    (first (filter #(= (:name %) team-name) teams))))

(defn find-role-by-name
  "Find a role by name."
  [role-name]
  (log/debug "Finding role by name" {:name role-name})
  (let [roles (get-roles)]
    (first (filter #(= (:name %) role-name) roles))))

;; Mock response helpers (for testing when backend is unavailable)

(defn- mock-login-response
  []
  {:token "mock-token-12345"})

(defn- mock-users-response
  []
  {:users [{:id "user-1" :firstName "John" :lastName "Doe" :email "john.doe@example.com"}
           {:id "user-2" :firstName "Jane" :lastName "Smith" :email "jane.smith@example.com"}]})

(defn- mock-teams-response
  []
  {:teams [{:id "team-1" :name "Engineering"}
           {:id "team-2" :name "Operations"}]})

(defn- mock-roles-response
  []
  [{:id "role-1" :name "Administrator"}
   {:id "role-2" :name "User"}])

(defn- mock-profile-response
  []
  {:id "profile-1" :company {:id "company-1" :name "Test Company"}})

(defn enable-mock-mode!
  "Enable mock mode for testing when backend is unavailable.
   WARNING: This replaces actual API calls with mock responses."
  []
  (log/warn "Enabling mock mode - API calls will return fake data")
  
  ;; Override functions with mock implementations
  (alter-var-root #'login 
    (constantly (fn [email password] 
                  (reset! auth-token "mock-token-12345")
                  {:success true :token "***mock***" :message "Mock login successful"})))
  
  (alter-var-root #'get-profile (constantly mock-profile-response))
  (alter-var-root #'get-roles (constantly mock-roles-response))
  (alter-var-root #'search-users (constantly mock-users-response))
  (alter-var-root #'search-teams (constantly mock-teams-response))
  
  (alter-var-root #'create-user 
    (constantly (fn [user-data] 
                  (assoc user-data :id (str "mock-user-" (System/currentTimeMillis))))))
  
  (alter-var-root #'create-team
    (constantly (fn [team-data]
                  (assoc team-data :id (str "mock-team-" (System/currentTimeMillis))))))
  
  {:success true :message "Mock mode enabled"})