(ns user_upload.config
  "Configuration management using aero and environ."
  (:require [aero.core :as aero]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn load-env-file
  "Load environment variables from .env file and return as a map."
  []
  (let [env-file (io/file ".env")]
    (if (.exists env-file)
      (do
        (log/debug "Loading .env file")
        (into {}
              (for [line (line-seq (io/reader env-file))
                    :when (not (or (str/blank? line) (str/starts-with? line "#")))
                    :let [;; Handle both "export KEY=value" and "KEY=value" formats
                          [_ k v] (or (re-matches #"^export\s+([A-Z_]+)=(.*)$" line)
                                     (re-matches #"^([A-Z_]+)=(.*)$" line))]
                    :when k]
                [k v])))
      {})))

;; Store loaded env vars globally so aero reader can access them
(def ^:private env-vars (atom {}))

;; Override aero's env reader to check our .env file first
(defmethod aero/reader 'env
  [opts tag value]
  (or (get @env-vars value)
      (System/getenv value)))

(defn load-config
  "Load configuration from resources/config.edn with environment variable resolution."
  []
  ;; Load .env file first if it exists and store in atom
  (reset! env-vars (load-env-file))
  (try
    (if-let [config-resource (io/resource "config.edn")]
      (let [config (aero/read-config config-resource)]
        (log/debug "Configuration loaded successfully")
        config)
      (throw (ex-info "Configuration file not found: resources/config.edn" {})))
    (catch Exception e
      (log/error e "Failed to load configuration")
      (throw e))))

(defn validate-required-config
  "Validate that all required configuration keys are present and non-empty."
  [config]
  (let [required-keys [[:jira :email]
                       [:jira :api-token] 
                       [:jira :domain]
                       [:api :base-url]
                       [:api :base-clj-url]
                       [:ai :anthropic-api-key]]
        ;; Auth credentials are now fetched per-tenant from 1Password
        missing-keys (filter #(let [value (get-in config %)]
                                (or (nil? value) (and (string? value) (empty? value))))
                             required-keys)]
    (when (seq missing-keys)
      (let [missing-str (mapv #(str/join "." (map name %)) missing-keys)]
        (throw (ex-info 
                (str "Missing required configuration keys: " missing-str
                     ". Please check your .env file and ensure all required variables are set.")
                {:missing-keys missing-keys}))))
    (log/debug "Configuration validation passed")
    config))

(defn get-config
  "Load and validate configuration. Returns the validated config map."
  []
  (-> (load-config)
      (validate-required-config)))

;; Global configuration atom for caching
(defonce config-cache (atom nil))

(defn config
  "Get the cached configuration, loading it if not already cached."
  []
  (or @config-cache
      (let [_ (log/debug "Loading configuration for the first time")
            loaded-config (get-config)]
        (reset! config-cache loaded-config)
        (log/debug "Configuration cached for future use")
        loaded-config)))

(defn reset-config!
  "Reset the configuration cache. Useful for testing or config reload."
  []
  (reset! config-cache nil))