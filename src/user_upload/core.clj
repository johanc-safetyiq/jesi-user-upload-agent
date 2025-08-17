(ns user_upload.core
  "Main entry point for the user upload agent system.
   
   This module provides the CLI interface and coordinates the complete
   workflow for processing user upload requests from Jira tickets."
  (:require [user_upload.log :as log]
            [user_upload.config :as config]
            [user_upload.workflow.processor :as processor]
            [user_upload.auth.onepassword :as op]
            [clojure.tools.cli :as cli]
            [clojure.string :as str])
  (:gen-class))

(def cli-options
  "Command line options for the user upload agent."
  [["-h" "--help" "Show help message"]
   ["-v" "--verbose" "Enable verbose logging"]
   ["-d" "--dry-run" "Perform a dry run without making changes"]
   ["-c" "--config CONFIG" "Configuration file path (default: resources/config.edn)"]
   ["-o" "--once" "Run once and exit (default behavior)"
    :default true]
   ["-w" "--watch" "Watch mode - continuously poll for new tickets"
    :default false]
   ["-s" "--single-ticket" "Process only the first ticket found (useful for testing)"
    :default false]
   ["-t" "--ticket KEY" "Process a specific ticket by key (e.g., JESI-5928)"
    :default nil]
   ["-i" "--interval SECONDS" "Polling interval in seconds for watch mode"
    :default 300
    :parse-fn #(Integer/parseInt %)
    :validate [#(> % 0) "Interval must be positive"]]])

(defn usage
  "Generate usage message."
  [options-summary]
  (->> ["User Upload Agent - Automated processing of Jira user upload requests"
        ""
        "Usage: user_upload-agent [options]"
        ""
        "Options:"
        options-summary
        ""
        "Examples:"
        "  user_upload-agent --once          # Process all tickets once and exit"
        "  user_upload-agent --single-ticket # Process only first ticket and exit"
        "  user_upload-agent --ticket JESI-5928  # Process specific ticket"
        "  user_upload-agent --watch         # Continuously watch for tickets"
        "  user_upload-agent --dry-run       # Show what would be done"
        "  user_upload-agent --verbose       # Enable detailed logging"
        ""]
       (str/join \newline)))

(defn validate-prerequisites
  "Validate that all required tools and credentials are available.
   
   Returns:
     Map with keys:
       :valid - Boolean indicating if all prerequisites are met
       :errors - List of error messages for missing prerequisites"
  []
  (let [errors (atom [])]
    
    ;; Check configuration
    (try
      (config/config)
      (catch Exception e
        (swap! errors conj (str "Configuration error: " (.getMessage e)))))
    
    ;; Check 1Password CLI
    (let [op-check (op/check-op-availability)]
      (when-not (:available op-check)
        (swap! errors conj (str "1Password CLI not available: " (:error op-check))))
      (when-not (:authenticated op-check)
        (swap! errors conj "1Password CLI not authenticated. Run 'op signin' first.")))
    
    {:valid (empty? @errors)
     :errors @errors}))

(defn run-once
  "Run the processor once and return results.
   
   Args:
     options - Parsed command line options
   
   Returns:
     Exit code (0 for success, 1 for failure)"
  [options]
  (log/info "Running user upload agent (single execution)")
  
  ;; Validate prerequisites
  (let [prereq-check (validate-prerequisites)]
    (if-not (:valid prereq-check)
      (do
        (doseq [error (:errors prereq-check)]
          (log/error error)
          (log/error "Prerequisite check failed" {:error error}))
        1) ; Exit code 1 for failure
      
      ;; Prerequisites OK, preload 1Password credentials then run processor
      (do
        (log/info "Pre-loading 1Password credentials" {:note "This may take a few minutes"})
        (let [preload-result (op/preload-all-credentials!)]
          (if (:success preload-result)
            (do
              (log/info "1Password credentials loaded successfully" 
                       {:credentials (:credentials-loaded preload-result)
                        :duplicates (:duplicates preload-result)})
)
            (do
              (log/error "Failed to preload 1Password credentials" {:error (:error preload-result)})
              (log/warn "Failed to preload 1Password credentials" 
                       {:error (:error preload-result)
                        :note "Lookups may fail"}))))
        
        (try
        (let [result (processor/run-once {:single-ticket (:single-ticket options)
                                          :ticket (:ticket options)})]
          (log/info "Processing complete" {:summary (:summary result)})
          (println (:summary result))
          
          (if (:success result)
            (do
              (println "✓ Processing completed successfully")
              0) ; Exit code 0 for success
            (do
              (println (str "⚠ Partial success: " (:summary result)))
              0))) ; Still exit 0 if some tickets were processed
        
        (catch Exception e
          (log/error e "Unexpected error during processing")
          (log/error "Unexpected error in run-once" e)
          1)))))) ; Exit code 1 for unexpected errors

(defn run-watch-mode
  "Run in watch mode, continuously polling for tickets.
   
   Args:
     options - Parsed command line options with :interval
   
   Returns:
     Exit code (0 for success, 1 for failure)"
  [options]
  (let [interval-seconds (:interval options)]
    (log/info "Starting watch mode" {:interval-seconds interval-seconds})
    
    ;; Validate prerequisites once
    (let [prereq-check (validate-prerequisites)]
      (if-not (:valid prereq-check)
        (do
          (doseq [error (:errors prereq-check)]
            (log/error error)
            (log/error "Prerequisite check failed" {:error error}))
          1) ; Exit code 1 for failure
        
        ;; Prerequisites OK, preload 1Password credentials then start polling loop
        (do
          (log/info "Pre-loading 1Password credentials" {:note "This may take a few minutes"})
          (log/info "Pre-loading 1Password credentials for watch mode")
          (let [preload-result (op/preload-all-credentials!)]
            (if (:success preload-result)
              (do
                (log/info "1Password credentials loaded successfully" 
                         {:credentials (:credentials-loaded preload-result)
                          :duplicates (:duplicates preload-result)})
                (println (format "Loaded %d credentials from 1Password" 
                                (:credentials-loaded preload-result))))
              (do
                (log/error "Failed to preload 1Password credentials" {:error (:error preload-result)})
                (println "Warning: Failed to preload 1Password credentials. Lookups may fail.")
                (println "Error:" (:error preload-result)))))
          
          (try
          (loop [iteration 1]
            (log/info "Watch mode iteration" {:iteration iteration})
            (log/debug "Watch mode iteration" {:iteration iteration
                                               :timestamp (str (java.time.LocalDateTime/now))})
            
            (try
              (let [result (processor/run-once {:single-ticket (:single-ticket options)})]
                (println (:summary result))
                (when (:error result)
                  (log/error "Watch iteration error" {:error (:error result)})))
              
              (catch Exception e
                (log/error e "Error in watch mode iteration" {:iteration iteration})
                (log/error "Error in watch iteration" e {:iteration iteration})))
            
            ;; Wait for next iteration
            (log/debug "Waiting for next iteration" {:wait-seconds interval-seconds})
            (Thread/sleep (* interval-seconds 1000))
            
            (recur (inc iteration)))
          
          (catch InterruptedException e
            (log/info "Watch mode interrupted")
            (log/info "Watch mode stopped")
            0) ; Normal exit
          
          (catch Exception e
            (log/error e "Unexpected error in watch mode")
            (log/error "Unexpected error in watch mode" e)
            1))))))) ; Exit code 1 for unexpected errors

(defn hello-world
  "A simple hello world function for testing basic functionality."
  []
  (log/info "Hello from user_upload agent!")
  "Hello, World!")

(defn -main
  "Main entry point for the user upload agent."
  [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    
    ;; Handle parsing errors
    (when errors
      (doseq [error errors]
        (log/error "Configuration error" {:error error}))
      (println (usage summary))
      (System/exit 1))
    
    ;; Handle help
    (when (:help options)
      (println (usage summary))
      (System/exit 0))
    
    ;; Configure logging level
    (when (:verbose options)
      (log/info "Verbose logging enabled"))
    
    ;; Show configuration
    (log/info "Starting user upload agent" {:options options :args arguments})
    (println "User Upload Agent Starting...")
    
    (when (:dry-run options)
      (log/info "Running in DRY RUN mode" {:note "No changes will be made"})
      (log/info "Dry run mode enabled"))
    
    ;; Run the appropriate mode
    (let [exit-code (cond
                      (:watch options)
                      (run-watch-mode options)
                      
                      (:once options)
                      (run-once options)
                      
                      :else
                      (run-once options))]
      
      (log/info "User upload agent exiting" {:exit-code exit-code})
      (System/exit exit-code))))